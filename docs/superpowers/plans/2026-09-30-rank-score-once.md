# Map Hang Fix — Score Once, Cache Regexes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stop the "App Hanging for at least 2000 ms" wave Sentry reports on iOS 1.3 (37) after an AI smart search, without changing which farms rank where.

**Architecture:** Two hot-path fixes, no new subsystems. (1) `FarmsStore.rankForIntent` scores every farm once and sorts by that number, instead of scoring both sides of every comparison inside the sort (≈2·n·log₂n scorings, each running the opening-hours parser). (2) `FarmFilters` compiles its three regular expressions once as `static let NSRegularExpression` instead of recompiling them on every call. Android gets the same one-line sort change for parity; its regexes are already cached.

**Tech Stack:** Swift 5 / SwiftUI (iOS 17+), Swift Testing (`@Test`, `#expect`) in `FarmsyTests`; Kotlin / JUnit in `android/app/src/test`.

**Spec:** Sentry triage of 2026-09-30 (this session). Issues in scope, all release 1.3 (37), all main-thread, non-fatal: FARMY-IOS-14, 15, 10, 12, 17, Z, 16, 18, 1D, 1C, 1A, 19, 1E, 1F, 13, 11, 1B. Reference stack (FARMY-IOS-14):

```
FarmFilters.closedDays (FarmFilters.swift:188)          ← regex compile per call
FarmFilters.isOpenOnDay (FarmFilters.swift:167)
FarmFilters.isOpenToday (FarmFilters.swift:81)
FarmsStore.rankForIntent (FarmsStore.swift:564)          ← score() inside sort comparator
closure in FarmsStore.rankForIntent (FarmsStore.swift:571)
MutableCollection.sort
FarmsStore.filtered.getter (FarmsStore.swift:500)
MapScreen.visiblePins.getter (MapScreen.swift:98)
MapScreen.body
```

Breadcrumb 2 s before every hang: `POST https://www.farmsy.app/api/search/smart [200]`.

## Global Constraints

- Ranking order must not change. Same weights, same signals, same tie behaviour (stable: ties keep server order).
- Opening-hours parsing must not change. The existing `FarmsyTests/OpeningHoursTests.swift` (260 lines) is the contract; it must stay green untouched.
- Regex options must survive the move to `NSRegularExpression`: `offPattern` is **case-insensitive**; the other two are not.
- iOS and Android in the same PR. Never push `main`; Neil reviews and merges. `gh` is not installed on this Mac: push the branch, Luuk opens the PR.
- Branch `perf/rank-score-once`, worktree `Farmsy-app-sentry-hang` (already created from `origin/main` at `0645130`).
- Run every command from the worktree root: `/Users/luuksmits/Documents/Farmsy App Code Repo/Farmsy-app-sentry-hang`.

## File map

| File | Change |
|---|---|
| `Farmsy/Core/FarmFilters.swift` | Three `static let` regexes + two helpers; six call sites switched |
| `FarmsyTests/OpeningHoursTests.swift` | One regression test for case-insensitive "closed" markers |
| `Farmsy/Core/FarmsStore.swift` | `rankForIntent` becomes `nonisolated static`, scores once; one caller updated |
| `FarmsyTests/RankForIntentTests.swift` | New: ordering, stability, completeness |
| `android/app/src/main/java/app/farmsy/android/core/FarmsStore.kt` | `sortedByDescending { score(it) }` → score once |

## Test commands

iOS unit tests (simulator must exist; `iPhone 17 Pro` is installed on this Mac):

```bash
xcodebuild test -quiet -scheme Farmsy -destination 'platform=iOS Simulator,name=iPhone 17 Pro' -only-testing:FarmsyTests 2>&1 | tail -40
```

Single suite:

```bash
xcodebuild test -quiet -scheme Farmsy -destination 'platform=iOS Simulator,name=iPhone 17 Pro' -only-testing:FarmsyTests/OpeningHoursTests 2>&1 | tail -40
```

Android unit tests:

```bash
cd android && chmod +x gradlew && ./gradlew :app:testDebugUnitTest --no-daemon 2>&1 | tail -30
```

---

### Task 1: Compile the opening-hours regexes once

**Files:**
- Modify: `Farmsy/Core/FarmFilters.swift:53` (pattern), `:100-105` (`dayPartOf`), `:112-130` (`windowsOf`), `:148`, `:171`, `:188-191`, `:287` (call sites)
- Test: `FarmsyTests/OpeningHoursTests.swift`

**Interfaces:**
- Consumes: nothing new.
- Produces: `private static func isOff(_ s: String) -> Bool` and `private static func stripOff(_ s: String) -> String` inside `enum FarmFilters`. Public API of `FarmFilters` unchanged.

- [ ] **Step 1: Add the regression test** (guards the one thing the refactor can silently break: case-insensitivity of the "closed" marker)

Append inside `struct OpeningHoursTests`, before the final `}` of the struct:

```swift
    // MARK: - Regex caching (Sentry hang, 1.3 (37))

    /// The "closed" marker is matched case-insensitively. This is the option that
    /// moving from `range(of:options:)` to a cached `NSRegularExpression` could
    /// drop without any other test noticing.
    @Test("closed markers match in any case, in all four languages")
    func offMarkersAnyCase() {
        for off in ["Su off", "Su OFF", "zondag Gesloten", "sonntag GESCHLOSSEN", "dimanche Fermé", "Su CLOSED"] {
            #expect(FarmFilters.isOpenOnDay("Mo-Su 09:00-17:00; \(off)", dayMon: 6) == false, "\(off)")
        }
        // The same string still opens on Monday: the closed rule removes one day, not all.
        #expect(FarmFilters.isOpenOnDay("Mo-Su 09:00-17:00; Su OFF", dayMon: 0))
    }
```

- [ ] **Step 2: Run the suite; expected PASS** (this is a refactor: the test is the contract, it must pass before and after)

```bash
xcodebuild test -quiet -scheme Farmsy -destination 'platform=iOS Simulator,name=iPhone 17 Pro' -only-testing:FarmsyTests/OpeningHoursTests 2>&1 | tail -40
```

Expected: `** TEST SUCCEEDED **`. If `offMarkersAnyCase` fails here, stop: the assumption about current behaviour is wrong; report it.

- [ ] **Step 3: Replace the pattern string with three compiled regexes and two helpers**

In `Farmsy/Core/FarmFilters.swift`, replace line 53:

```swift
    private static let offPattern = "\\b(off|gesloten|geschlossen|ferm[eé]|closed)\\b"
```

with:

```swift
    /// Compiled once. These used to be pattern strings handed to
    /// `range(of:options:.regularExpression)` on every call, which compiles the
    /// regex each time. `rankForIntent` calls into here for every farm, and the
    /// map re-evaluates on every SwiftUI body pass, so this was tens of thousands
    /// of regex compiles per render on the main thread — the "App Hanging" wave
    /// Sentry reported on 1.3 (37).
    private static let offRegex = try! NSRegularExpression(
        pattern: "\\b(off|gesloten|geschlossen|ferm[eé]|closed)\\b", options: .caseInsensitive)
    /// First HH:MM token, with the whitespace before it.
    private static let timeTokenRegex = try! NSRegularExpression(pattern: "\\s+\\d{1,2}:\\d{2}")
    /// One HH:MM-HH:MM window; any of the three dashes (see `dashes`).
    private static let windowRegex = try! NSRegularExpression(
        pattern: "(\\d{1,2}):(\\d{2})\\s*[-\u{2013}\u{2014}]\\s*(\\d{1,2}):(\\d{2})")

    private static func fullRange(_ s: String) -> NSRange { NSRange(s.startIndex..., in: s) }

    /// True when the segment carries a "closed" marker.
    private static func isOff(_ s: String) -> Bool {
        offRegex.firstMatch(in: s, range: fullRange(s)) != nil
    }

    /// The segment with its "closed" marker removed (not trimmed).
    private static func stripOff(_ s: String) -> String {
        offRegex.stringByReplacingMatches(in: s, range: fullRange(s), withTemplate: "")
    }
```

- [ ] **Step 4: Switch `dayPartOf` and `windowsOf` to the compiled regexes**

Replace the body of `dayPartOf` (lines ~101-105):

```swift
    private static func dayPartOf(_ s: String) -> String {
        let dayPart: String
        if let m = timeTokenRegex.firstMatch(in: s, range: fullRange(s)),
           let r = Range(m.range, in: s) {
            dayPart = String(s[s.startIndex..<r.lowerBound])
        } else {
            dayPart = s
        }
        return dayPart.trimmingCharacters(in: .whitespaces)
    }
```

In `windowsOf`, delete the two-line `guard let re = try? NSRegularExpression(...) else { return [] }` and change the loop head from `for m in re.matches(in: segment, range: ...)` to:

```swift
        for m in windowRegex.matches(in: segment, range: NSRange(location: 0, length: ns.length)) {
```

Keep the comment above it and everything else in `windowsOf` as is.

- [ ] **Step 5: Switch the four `offPattern` call sites**

Each of these lines (in `isOpenNow` ~148, `isOpenOnDay` ~171, `windowsOnDay` ~287):

```swift
            if s.range(of: offPattern, options: [.regularExpression, .caseInsensitive]) != nil { continue }
```

becomes:

```swift
            if isOff(s) { continue }
```

In `closedDays` (~188-191), replace:

```swift
            if s.range(of: offPattern, options: [.regularExpression, .caseInsensitive]) == nil { continue }
            let dayPart = s.replacingOccurrences(
                of: offPattern, with: "", options: [.regularExpression, .caseInsensitive]
            ).trimmingCharacters(in: .whitespaces)
```

with:

```swift
            if !isOff(s) { continue }
            let dayPart = stripOff(s).trimmingCharacters(in: .whitespaces)
```

Then confirm nothing references the old name:

```bash
grep -n "offPattern\|regularExpression" Farmsy/Core/FarmFilters.swift
```

Expected: no output.

- [ ] **Step 6: Run the whole opening-hours suite; expected PASS**

```bash
xcodebuild test -quiet -scheme Farmsy -destination 'platform=iOS Simulator,name=iPhone 17 Pro' -only-testing:FarmsyTests/OpeningHoursTests 2>&1 | tail -40
```

Expected: `** TEST SUCCEEDED **`, including `offMarkersAnyCase`. `FarmStatusTests` and `TripRouteLockTests` also exercise `FarmFilters`; run the full `FarmsyTests` bundle once here too.

- [ ] **Step 7: Commit**

```bash
git add Farmsy/Core/FarmFilters.swift FarmsyTests/OpeningHoursTests.swift
git commit -m "perf(iOS): compile the opening-hours regexes once

FarmFilters handed pattern strings to range(of:options:.regularExpression)
on every call, compiling the regex each time. rankForIntent runs the parser
for every farm on every map render, so this was tens of thousands of
compiles per pass on the main thread. Three static NSRegularExpressions
now; behaviour unchanged, OpeningHoursTests green, plus one test that the
closed marker stays case-insensitive.

Sentry: FARMY-IOS-10 (NSRegularExpression.init), part of the 1.3 (37) hang wave.

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 2: Score each farm once in `rankForIntent`

**Files:**
- Modify: `Farmsy/Core/FarmsStore.swift:500` (caller), `:552-572` (`rankForIntent`)
- Create: `FarmsyTests/RankForIntentTests.swift`

**Interfaces:**
- Consumes: `FarmFilters.isOpenToday(_:)`, `SearchRanking` (`Farmsy/Core/SmartSearchAPI.swift:51`, memberwise defaults, `.default`), `FarmPin` memberwise init (`Farmsy/Core/Models.swift:92`; the `Decodable` init lives in an extension, so the memberwise init is still synthesised).
- Produces: `nonisolated static func rankForIntent(_ list: [FarmPin], origin: CLLocationCoordinate2D?, ranking: SearchRanking?) -> [FarmPin]` on `FarmsStore`, internal (testable via `@testable import Farmsy`).

- [ ] **Step 1: Write the failing test**

Create `FarmsyTests/RankForIntentTests.swift`:

```swift
import Foundation
import CoreLocation
import Testing
@testable import Farmsy

/// `rankForIntent` orders AI-search results by the server's weights. It used to
/// score both sides of every comparison inside `sorted`, so a 1,000-farm result
/// ran the opening-hours parser ~20,000 times per map render — Sentry's
/// "App Hanging" wave on 1.3 (37). It now scores each farm once. These tests pin
/// the order so that change cannot move anything.
struct RankForIntentTests {

    static func pin(_ id: String, lat: Double = 52.0, lng: Double = 5.0, hours: String? = nil,
                    image: String? = nil, rating: Double? = nil, reviews: Int = 0,
                    verified: Bool = false) -> FarmPin {
        FarmPin(id: id, osmId: id, name: id, lat: lat, lng: lng, address: nil, city: nil,
                postalCode: nil, country: nil, phone: nil, website: nil, openingHours: hours,
                image: image, primaryTag: nil, farmType: [], avgRating: rating,
                reviewCount: reviews, hasDescription: false, isVerified: verified)
    }

    @Test("signals rank a verified, photographed, reviewed farm above a bare one")
    func signalsOrder() {
        let plain = Self.pin("plain")
        let good = Self.pin("good", image: "x.jpg", rating: 4.5, reviews: 30, verified: true)
        let out = FarmsStore.rankForIntent([plain, good], origin: nil, ranking: nil)
        #expect(out.map(\.id) == ["good", "plain"])
    }

    @Test("distance dominates when there is an origin")
    func distanceDominates() {
        let origin = CLLocationCoordinate2D(latitude: 52.0, longitude: 5.0)
        // ~111 km north: distance score 0. Its signals total 15+10+9+7.5 = 41.5.
        let far = Self.pin("far", lat: 53.0, lng: 5.0, image: "x.jpg", rating: 4.5, reviews: 30, verified: true)
        // ~140 m away: distance score ≈ 99.9, no signals.
        let near = Self.pin("near", lat: 52.001, lng: 5.001)
        let out = FarmsStore.rankForIntent([far, near], origin: origin, ranking: nil)
        #expect(out.map(\.id) == ["near", "far"])
    }

    @Test("open today counts, with the server's weight")
    func openTodayWeight() {
        let open = Self.pin("open", hours: "24/7")
        let shut = Self.pin("shut", hours: nil, image: "x.jpg")   // hasPhoto = 10 < openToday = 20
        #expect(FarmsStore.rankForIntent([shut, open], origin: nil, ranking: nil).map(\.id) == ["open", "shut"])
        var w = SearchRanking.default
        w.openToday = 5                                            // now photo (10) wins
        #expect(FarmsStore.rankForIntent([shut, open], origin: nil, ranking: w).map(\.id) == ["shut", "open"])
    }

    @Test("ties keep incoming order, and every farm survives")
    func stableAndComplete() {
        let list = (0..<200).map { Self.pin("p\($0)") }
        #expect(FarmsStore.rankForIntent(list, origin: nil, ranking: nil).map(\.id) == list.map(\.id))
    }

    @Test("an unknown ranking version falls back to the defaults")
    func unknownVersionFallsBack() {
        var w = SearchRanking.default
        w.version = 99
        w.openToday = 0
        let open = Self.pin("open", hours: "24/7")
        let shut = Self.pin("shut", image: "x.jpg")
        // Defaults apply (openToday 20 > hasPhoto 10), not the version-99 weights.
        #expect(FarmsStore.rankForIntent([shut, open], origin: nil, ranking: w).map(\.id) == ["open", "shut"])
    }
}
```

- [ ] **Step 2: Run the new suite; expected FAIL to compile**

`FarmsyTests` is a file-system-synchronised group in `project.pbxproj` (verified: 5 `PBXFileSystemSynchronizedRootGroup` entries), so a new file in the folder joins the target automatically. No project edit needed.

```bash
xcodebuild test -quiet -scheme Farmsy -destination 'platform=iOS Simulator,name=iPhone 17 Pro' -only-testing:FarmsyTests/RankForIntentTests 2>&1 | tail -40
```

Expected: build error `type 'FarmsStore' has no member 'rankForIntent'` (it is a private instance method today).

- [ ] **Step 3: Rewrite `rankForIntent` as a static function that scores once**

In `Farmsy/Core/FarmsStore.swift`, replace the whole function (from the `/// Rank AI-search results by usefulness` doc comment through its closing `}`) with:

```swift
    /// Rank AI-search results by usefulness — the web's signals: distance
    /// (dominant when there's an origin), then open today, verified, has a photo,
    /// rating, review count. One function so it can be swapped for a server-side
    /// order if the endpoint ever returns one (asked Aviah; matches her signal
    /// list until then). Higher score first; ties keep their incoming order.
    ///
    /// Scores each farm ONCE, then sorts by that number. The comparator used to
    /// call `score` on both sides of every comparison — about 2·n·log₂n scorings,
    /// each running the opening-hours parser — on the main thread, on every map
    /// body pass. That was the "App Hanging" wave on 1.3 (37)
    /// (FARMY-IOS-14/15/12/17/Z/16/18 and friends).
    ///
    /// `nonisolated static`: it reads nothing from the store, and the tests call
    /// it without hopping to the main actor.
    nonisolated static func rankForIntent(
        _ list: [FarmPin], origin: CLLocationCoordinate2D?, ranking: SearchRanking?
    ) -> [FarmPin] {
        // Weights come from the server (`ranking` on the response) so every client
        // ranks identically; fall back to the defaults if the field is absent or a
        // future `version` we don't recognise. Distance never leaves the device.
        let r = ranking ?? .default
        let w = (r.version == SearchRanking.default.version) ? r : .default
        let zeroM = max(1, w.distanceZeroKm * 1000)
        let originLoc = origin.map { CLLocation(latitude: $0.latitude, longitude: $0.longitude) }
        func score(_ p: FarmPin) -> Double {
            var s = 0.0
            if let originLoc, let d = p.distance(from: originLoc) {
                s += max(0, 1 - d / zeroM) * w.distanceWeight
            }
            if FarmFilters.isOpenToday(p.openingHours) { s += w.openToday }
            if p.isVerified { s += w.verified }
            if p.image != nil { s += w.hasPhoto }
            s += (p.avgRating ?? 0) * w.ratingFactor
            s += min(Double(p.reviewCount), w.reviewCap) * w.reviewEach
            return s
        }
        return list.map { ($0, score($0)) }.sorted { $0.1 > $1.1 }.map(\.0)
    }
```

- [ ] **Step 4: Update the one caller**

At `Farmsy/Core/FarmsStore.swift:500`, change:

```swift
            return rankForIntent(result, origin: aiCenter)
```

to:

```swift
            return Self.rankForIntent(result, origin: aiCenter, ranking: aiIntent?.ranking)
```

Confirm it is the only caller:

```bash
grep -rn "rankForIntent" Farmsy/
```

Expected: the definition and this one call.

- [ ] **Step 5: Run the new suite and the full bundle; expected PASS**

```bash
xcodebuild test -quiet -scheme Farmsy -destination 'platform=iOS Simulator,name=iPhone 17 Pro' -only-testing:FarmsyTests 2>&1 | tail -40
```

Expected: `** TEST SUCCEEDED **`, five `RankForIntentTests` pass.

- [ ] **Step 6: Commit**

```bash
git add Farmsy/Core/FarmsStore.swift FarmsyTests/RankForIntentTests.swift
git commit -m "perf(iOS): score each farm once in rankForIntent

The sort comparator called score() on both sides of every comparison,
so a 1,000-farm AI result ran the opening-hours parser ~20,000 times per
map render, on the main thread. Score once per farm, then sort by the
number. Same weights, same order, stable ties. rankForIntent is now a
nonisolated static so it can be tested without the store.

Sentry: the App Hanging wave on 1.3 (37) — FARMY-IOS-14, 15, 12, 17, Z,
16, 18, 1D, 1C, 1A, 19, 1E, 1F, 13, 11, 1B.

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 3: Android parity — score once

**Files:**
- Modify: `android/app/src/main/java/app/farmsy/android/core/FarmsStore.kt:485`

**Interfaces:**
- Consumes: existing private `rankForIntent(list, origin, ranking)` and its local `score`.
- Produces: nothing new. Android's `FarmFilters.kt` already caches its regexes (`offRegex`, `timeToken`, `windowRegex` at lines 61-69), so only the sort changes.

- [ ] **Step 1: Change the sort**

At line 485, replace:

```kotlin
        return list.sortedByDescending { score(it) }
```

with:

```kotlin
        // Score once per farm. sortedByDescending { score(it) } calls the selector
        // on both sides of every comparison (see iOS rankForIntent, Sentry 1.3 (37)).
        return list.map { it to score(it) }.sortedByDescending { it.second }.map { it.first }
```

`sortedByDescending` is stable in Kotlin, so ties keep server order, same as iOS.

- [ ] **Step 2: Run the Android unit tests; expected PASS**

```bash
cd android && chmod +x gradlew && ./gradlew :app:testDebugUnitTest --no-daemon 2>&1 | tail -30
```

Expected: `BUILD SUCCESSFUL`. If the Android toolchain is not set up on this Mac (no `ANDROID_HOME`), run `./gradlew :app:compileDebugKotlin --no-daemon` at minimum; if that is also unavailable, say so in the PR description and let CI's `assembleDebug` job (compile-check.yml) cover it.

- [ ] **Step 3: Commit**

```bash
git add android/app/src/main/java/app/farmsy/android/core/FarmsStore.kt
git commit -m "perf(Android): score each farm once in rankForIntent

Same change as iOS: the sort selector ran per comparison, so the
opening-hours parser ran ~2·n·log n times per ranking. Score once, then
sort. Order unchanged, ties stable.

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 4: Push, PR, Sentry follow-up

**Files:** none.

- [ ] **Step 1: Push the branch** (never `main`)

```bash
git push -u origin perf/rank-score-once
```

- [ ] **Step 2: Luuk opens the PR** (`gh` not installed). Title and body:

Title: `perf: score each farm once in rankForIntent, compile opening-hours regexes once`

Body:

```
Fixes the "App Hanging for at least 2000 ms" wave Sentry reports on iOS 1.3 (37):
FARMY-IOS-14, 15, 10, 12, 17, Z, 16, 18, 1D, 1C, 1A, 19, 1E, 1F, 13, 11, 1B
(~130 events, ~60 users in 20 h, all main thread, all after an AI smart search).

Root cause: `rankForIntent` sorted with a comparator that called `score()` on
both sides of every comparison, and `score()` runs the opening-hours parser,
which recompiled its regexes on every call. 1,000 farms ≈ 20,000 scorings ≈
100k+ regex compiles per map render, on the main thread.

- iOS `FarmsStore.rankForIntent`: score once per farm, then sort. Now
  `nonisolated static`, with `RankForIntentTests` pinning the order.
- iOS `FarmFilters`: three `static let NSRegularExpression`; `OpeningHoursTests`
  green untouched, plus one test that the closed marker stays case-insensitive.
- Android `FarmsStore.rankForIntent`: same score-once sort. Regexes were
  already cached there.

No ranking or parsing behaviour change.

🤖 Generated with [Claude Code](https://claude.com/claude-code)
```

- [ ] **Step 3: After Neil merges and the next build ships**, in Sentry select the issues above and choose **Resolve → in the next release**. Leave FARMY-IOS-J (Supabase `get_farms_pins` HTTP 500) and FARMY-IOS-1 (WatchdogTermination) open; they are unrelated.

- [ ] **Step 4: Remove the worktree once merged**

```bash
git -C "/Users/luuksmits/Documents/Farmsy App Code Repo/Farmsy-app" worktree remove ../Farmsy-app-sentry-hang
```

---

## Self-review

- **Coverage:** hang stack has two hot spots (comparator scoring, regex compile); Task 2 and Task 1 remove them. Android parity: Task 3. Sentry close-out: Task 4.
- **Placeholders:** none; every code step is the literal diff.
- **Names:** `isOff` / `stripOff` / `fullRange` / `offRegex` / `timeTokenRegex` / `windowRegex` used consistently in Task 1; `FarmsStore.rankForIntent(_:origin:ranking:)` matches between Task 2 implementation, caller and tests.
- **Out of scope, deliberately:** `FarmsStore.filtered` is still a computed property re-evaluated on every `MapScreen` body pass. After this PR that costs one parser run per farm per render, not 20,000; if Sentry still shows hangs on the next release, memoise `filtered` behind the filter inputs. Not done now because it changes `@Observable` invalidation and needs its own device pass.
