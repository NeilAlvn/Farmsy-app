# Conversion Fixes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the PostHog funnel trustworthy, let signed-out people see the paywall, put a Plus lock on every farm card, and seed a shopping list during onboarding — iOS and Android together.

**Architecture:** No new subsystems. Four stacked branches in one worktree, one PR each, each PR carrying both platforms. Analytics constants stay the single source of event names on each platform (`AnalyticsEvent.swift` / `AnalyticsEvent.kt`), kept identical by `scripts/analytics-parity.sh`. Shopping-list writes go through `TripStore.toggleProduct`, which now carries a `source` and emits the event, so no call site can forget. The auth gate moves from `shell.openPlus` into the Plus sheet's plan buttons.

**Tech Stack:** SwiftUI (iOS 17+), Swift Testing (`@Test`, `#expect`) in `FarmsyTests`; Jetpack Compose + JUnit4 in `android/app/src/test`; PostHog EU project 222496; RevenueCat.

**Spec:** `docs/superpowers/specs/2026-09-30-conversion-fixes-design.md` (committed `7d5479d`). Read it first; it holds the numbers and the owner decisions.

## Global Constraints

- Every PR: iOS and Android. Never push `main`. Luuk opens PRs (no `gh` on this Mac), Neil merges.
- Worktree: `/Users/luuksmits/Documents/Farmsy App Code Repo/Farmsy-app-conversion`. Run every command from there. Branches, stacked in this order, each created from the previous one's head:
  1. `conv/analytics-identity` (exists, holds the spec)
  2. `conv/paywall-before-auth`
  3. `conv/farm-detail-lock`
  4. `conv/onboarding-basket`
- Event names, property keys and closed values are snake_case, spelled once per platform, identical across platforms. `scripts/analytics-parity.sh` must exit 0 after every task that touches them.
- No free text, no user-typed text in any analytics property. A custom shopping item reports `item = "custom"`.
- Ranking, parsing and purchase semantics do not change. `PurchaseStore.purchase` must be unreachable without a Supabase user id.
- New user-facing strings ship in en, nl, fr, de on both platforms (iOS `Farmsy/Localizable.xcstrings`, Android `values*/strings_extra.xml`).
- Tap tests on the simulator are part of the deliverable where the plan says so; reviewers cannot see dead taps.
- Commit messages end with `Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>`.

## Test commands

iOS unit tests (simulator `iPhone 17 Pro` exists; Bash timeout 600000 ms):

```bash
xcodebuild test -quiet -scheme Farmsy -destination 'platform=iOS Simulator,name=iPhone 17 Pro' -only-testing:FarmsyTests 2>&1 | tail -40
```

Android unit tests (JDK: `export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home`, SDK `export ANDROID_HOME=$HOME/Library/Android/sdk`; `~/.gradle/gradle.properties` already overrides the repo's Intel JDK path):

```bash
cd android && ./gradlew :app:testDebugUnitTest --no-daemon 2>&1 | tail -30
```

Parity:

```bash
scripts/analytics-parity.sh
```

Simulator launch with a clean onboarding and a demo session:

```bash
xcrun simctl launch booted app.farmsy.ios --reset-onboarding
```

## File map

| Task | iOS | Android |
|---|---|---|
| 1 | `Farmsy/Core/AnalyticsEvent.swift`, `Farmsy/Core/SessionStore.swift`, `Farmsy/Features/Main/AppShell.swift`, `FarmsyTests/AnalyticsEventTests.swift` (new) | `core/AnalyticsEvent.kt`, `core/SessionStore.kt`, `features/main/MainScreen.kt` |
| 2 | `Farmsy/Core/TripStore.swift` + every `toggleProduct` caller, `FarmsyTests/TripStoreTests.swift` (new) | `core/TripStore.kt` + every caller |
| 3 | `Farmsy/Features/Main/AppShell.swift`, `Farmsy/Features/Map/ProUpsellSheet.swift`, `Farmsy/Core/PurchaseStore.swift` | `features/main/MainScreen.kt`, `features/map/ProUpsellSheet.kt`, `core/PurchaseStore.kt` |
| 4 | `Farmsy/Features/Detail/FarmStatusSection.swift`, `Farmsy/Core/FarmStatus.swift`, `Farmsy/Localizable.xcstrings`, `FarmsyTests/FarmStatusTests.swift` | `features/detail/FarmStatusSection.kt`, `core/FarmStatus.kt`, `res/values*/strings_extra.xml`, `test/.../FarmStatusTest.kt` |
| 5 | `Farmsy/Features/Onboarding/OnboardingView.swift`, `Farmsy/Core/ShoppingList.swift`, `Farmsy/Localizable.xcstrings`, `FarmsyTests/BasketSeedTests.swift` (new) | `features/onboarding/OnboardingScreen.kt`, `core/ShoppingList.kt`, `res/values*/strings_extra.xml`, `test/.../BasketSeedTest.kt` (new) |

Android paths are under `android/app/src/main/java/app/farmsy/android/` unless stated.

---

### Task 1: Stop resetting identity on launch; add `tab_viewed` and `auth_prompted`

Branch: `conv/analytics-identity` (already checked out).

**Files:**
- Modify: `Farmsy/Core/AnalyticsEvent.swift`, `android/.../core/AnalyticsEvent.kt`
- Modify: `Farmsy/Core/SessionStore.swift:128-135`, `android/.../core/SessionStore.kt:121-129`
- Modify: `Farmsy/Features/Main/AppShell.swift` (`actions`, `body`, `requireAuth`), `android/.../features/main/MainScreen.kt` (`requireAuth`, `shell`, tab effect)
- Create: `FarmsyTests/AnalyticsEventTests.swift`

**Interfaces:**
- Produces (iOS): `AnalyticsEvent.tabViewed`, `.shoppingItemAdded`, `.authPrompted`; `AnalyticsProp.tab`, `.item`; `AnalyticsValue.Trigger.trips`, `.restore`; `enum AnalyticsValue.ListSource: String { picker, typed, farmDetail = "farm_detail", onboarding }`; `AnalyticsEvent: CaseIterable`.
- Produces (Android): `AnalyticsEvent.TAB_VIEWED`, `SHOPPING_ITEM_ADDED`, `AUTH_PROMPTED`; `AnalyticsProp.TAB`, `ITEM`; `Trigger.TRIPS`, `RESTORE`; `enum class ListSource(val key) { PICKER, TYPED, FARM_DETAIL, ONBOARDING }`.
- Produces (iOS): `AppShell.requireAuth(_ trigger: AnalyticsValue.Trigger, _ action:)`. Android: `requireAuth(trigger: AnalyticsValue.Trigger, then: () -> Unit)`.

- [ ] **Step 1: Write the failing test**

Create `FarmsyTests/AnalyticsEventTests.swift`:

```swift
import Foundation
import Testing
@testable import Farmsy

/// One typo in an event name splits a funnel into two bars that never add up.
/// The names are spelled once, here, and this pins the spelling rule.
struct AnalyticsEventTests {

    static let snake = try! NSRegularExpression(pattern: "^[a-z][a-z0-9_]*$")
    static func isSnake(_ s: String) -> Bool {
        snake.firstMatch(in: s, range: NSRange(s.startIndex..., in: s)) != nil
    }

    @Test("every event name is snake_case and unique")
    func eventNames() {
        let names = AnalyticsEvent.allCases.map(\.rawValue)
        #expect(Set(names).count == names.count)
        for n in names { #expect(Self.isSnake(n), "\(n)") }
    }

    @Test("the three new events exist under the agreed names")
    func newEvents() {
        #expect(AnalyticsEvent.tabViewed.rawValue == "tab_viewed")
        #expect(AnalyticsEvent.shoppingItemAdded.rawValue == "shopping_item_added")
        #expect(AnalyticsEvent.authPrompted.rawValue == "auth_prompted")
        #expect(AnalyticsValue.ListSource.farmDetail.rawValue == "farm_detail")
        #expect(AnalyticsValue.Trigger.trips.rawValue == "trips")
        #expect(AnalyticsValue.Trigger.restore.rawValue == "restore")
    }
}
```

- [ ] **Step 2: Run it; expected FAIL to compile**

```bash
xcodebuild test -quiet -scheme Farmsy -destination 'platform=iOS Simulator,name=iPhone 17 Pro' -only-testing:FarmsyTests/AnalyticsEventTests 2>&1 | tail -20
```

Expected: `type 'AnalyticsEvent' has no member 'allCases'` (or `tabViewed`).

- [ ] **Step 3: Add the constants (iOS)**

In `Farmsy/Core/AnalyticsEvent.swift`:

Change `enum AnalyticsEvent: String {` to `enum AnalyticsEvent: String, CaseIterable {`.

After `case routePlanned = "route_planned"` add:

```swift
    /// Which of the five tabs is on screen. Once per selection change and once
    /// for the initial tab — never per re-render.
    case tabViewed = "tab_viewed"
    /// An item joined `wantedProducts`. Fired by TripStore itself, so no call
    /// site can forget; `source` says which surface added it.
    case shoppingItemAdded = "shopping_item_added"
    /// The sign-in sheet opened because a signed-out person asked for something
    /// that needs an account. `trigger` names what they asked for.
    case authPrompted = "auth_prompted"
```

In `enum AnalyticsProp`, after `static let radiusKm = "radius_km"` add:

```swift
    /// `tab_viewed`: the AppTab rawValue.
    static let tab = "tab"
    /// `shopping_item_added`: the item id, or "custom" for anything typed.
    static let item = "item"
```

In `enum AnalyticsValue.Trigger`, after `case profile = "profile"` add:

```swift
        /// `auth_prompted` only: the trip planner and "Restore purchases".
        case trips = "trips"
        case restore = "restore"
```

After the `Trigger` enum add:

```swift
    /// `source` on shopping_item_added: which surface put the item on the list.
    enum ListSource: String {
        case picker = "picker"
        case typed = "typed"
        case farmDetail = "farm_detail"
        case onboarding = "onboarding"
    }
```

- [ ] **Step 4: Add the constants (Android), same order**

In `android/.../core/AnalyticsEvent.kt`:

After `ROUTE_PLANNED("route_planned"),` add:

```kotlin
    /// Which of the five tabs is on screen. Once per selection change and once
    /// for the initial tab — never per recomposition.
    TAB_VIEWED("tab_viewed"),
    /// An item joined `wantedProducts`. Fired by TripStore itself, so no call
    /// site can forget; `source` says which surface added it.
    SHOPPING_ITEM_ADDED("shopping_item_added"),
    /// The sign-in sheet opened because a signed-out person asked for something
    /// that needs an account. `trigger` names what they asked for.
    AUTH_PROMPTED("auth_prompted"),
```

In `object AnalyticsProp` after `RADIUS_KM`:

```kotlin
    /// `tab_viewed`: the AppTab name, lowercased.
    const val TAB = "tab"
    /// `shopping_item_added`: the item id, or "custom" for anything typed.
    const val ITEM = "item"
```

In `enum class Trigger` after `PROFILE("profile"),`:

```kotlin
        /// `auth_prompted` only: the trip planner and "Restore purchases".
        TRIPS("trips"),
        RESTORE("restore"),
```

After the `Trigger` enum:

```kotlin
    /// `source` on shopping_item_added: which surface put the item on the list.
    enum class ListSource(val key: String) {
        PICKER("picker"),
        TYPED("typed"),
        FARM_DETAIL("farm_detail"),
        ONBOARDING("onboarding"),
    }
```

- [ ] **Step 5: Parity must pass**

```bash
scripts/analytics-parity.sh
```

Expected: `analytics parity OK: N names identical on iOS and Android`. If it fails, the diff shows which side is out of order; fix the order, not the names.

- [ ] **Step 6: Reset identity only on a real sign-out (iOS)**

In `Farmsy/Core/SessionStore.swift`, the `else` branch of the `authStateChanges` loop currently reads:

```swift
                } else {
                    self.profile = nil
                    await PurchaseStore.signOut()
                    Observability.reset()
                }
```

Replace with:

```swift
                } else {
                    self.profile = nil
                    // Only a real sign-out resets identity. The stream's first
                    // emission on a cold start is `.initialSession` with a nil
                    // session for anyone signed out, and resetting there minted a
                    // fresh PostHog anonymous id on every launch — after
                    // `app_opened` had already fired on the old one. Every launch
                    // looked like a new person and Funnel A never joined.
                    if state.event == .signedOut {
                        await PurchaseStore.signOut()
                        Observability.reset()
                    }
                }
```

- [ ] **Step 7: Same on Android**

In `android/.../core/SessionStore.kt`, the `is SessionStatus.NotAuthenticated ->` branch currently reads:

```kotlin
                    is SessionStatus.NotAuthenticated -> {
                        _session.value = null
                        _profile.value = null
                        _isBootstrapped.value = true
                        PurchaseStore.signOut()
                        Observability.reset()
                        fireAppOpened()
                    }
```

Replace with:

```kotlin
                    is SessionStatus.NotAuthenticated -> {
                        _session.value = null
                        _profile.value = null
                        _isBootstrapped.value = true
                        // Only a real sign-out resets identity (see iOS SessionStore):
                        // this status also arrives on every cold start for a
                        // signed-out person, and resetting there made each launch a
                        // brand-new PostHog person.
                        if (status.isSignOut) {
                            PurchaseStore.signOut()
                            Observability.reset()
                        }
                        fireAppOpened()
                    }
```

`SessionStatus.NotAuthenticated` carries `isSignOut` in supabase-kt 3.0.3 (the BOM in `android/app/build.gradle.kts:108`).

- [ ] **Step 8: `tab_viewed` and `auth_prompted` (iOS)**

In `Farmsy/Features/Main/AppShell.swift`:

Change `requireAuth` (near the bottom of `AppShell`) to:

```swift
    /// Runs `action` signed in; otherwise opens the sign-in sheet and says why.
    private func requireAuth(_ trigger: AnalyticsValue.Trigger, _ action: @escaping () -> Void) {
        if session.isAuthenticated {
            action()
        } else {
            Observability.capture(.authPrompted, [AnalyticsProp.trigger: trigger.rawValue])
            showAuth = true
        }
    }
```

In `actions`, change the two callers:

```swift
            openTrips: { requireAuth(.trips) { showTrips = true } },
            openPlus: { trigger in requireAuth(trigger) { plusTrigger = trigger; showPlus = true } },
```

(Task 3 removes the gate from `openPlus`; leave it here so this task stays a pure analytics change.)

In `body`, directly after `.modifier(SurveyEntry(...))`, add:

```swift
        // One event per tab selection, and one for the tab the shell opens on.
        .onChange(of: tab, initial: true) { _, t in
            Observability.capture(.tabViewed, [AnalyticsProp.tab: t.rawValue])
        }
```

- [ ] **Step 9: Same on Android**

In `android/.../features/main/MainScreen.kt`:

Replace `requireAuth`:

```kotlin
    /// Runs `then` signed in; otherwise opens the sign-in sheet and says why.
    fun requireAuth(trigger: AnalyticsValue.Trigger, then: () -> Unit) {
        if (session.isAuthenticated) then()
        else {
            Observability.capture(AnalyticsEvent.AUTH_PROMPTED, mapOf(AnalyticsProp.TRIGGER to trigger.key))
            requestAuth()
        }
    }
```

In `shell`:

```kotlin
            openTrips = { requireAuth(AnalyticsValue.Trigger.TRIPS) { route = SheetRoute.TRIPS } },
            openPlus = { trigger -> requireAuth(trigger) { plusTrigger = trigger; showPlus = true } },
```

Directly after `var tab by rememberSaveable { mutableStateOf(AppTab.HOME) }` add:

```kotlin
    // One event per tab selection, and one for the tab the shell opens on.
    LaunchedEffect(tab) {
        Observability.capture(AnalyticsEvent.TAB_VIEWED, mapOf(AnalyticsProp.TAB to tab.name.lowercase()))
    }
```

- [ ] **Step 10: Run tests, both platforms**

```bash
xcodebuild test -quiet -scheme Farmsy -destination 'platform=iOS Simulator,name=iPhone 17 Pro' -only-testing:FarmsyTests 2>&1 | tail -20
cd android && ./gradlew :app:testDebugUnitTest --no-daemon 2>&1 | tail -15; cd ..
scripts/analytics-parity.sh
```

Expected: iOS bundle green including the two new tests; Android `BUILD SUCCESSFUL`; parity OK.

- [ ] **Step 11: Identity check on the simulator**

Build and launch the app twice, signed out, and in PostHog → Activity → Live events confirm both launches' `app_opened` share one `distinct_id` and that a `tab_viewed` with `tab = home` follows each. Paste the id prefix (first 8 chars) into the report. If PostHog live events cannot be reached from this session, say so in the report; the controller runs the check.

- [ ] **Step 12: Commit**

```bash
git add Farmsy/Core/AnalyticsEvent.swift Farmsy/Core/SessionStore.swift Farmsy/Features/Main/AppShell.swift FarmsyTests/AnalyticsEventTests.swift android/app/src/main/java/app/farmsy/android/core/AnalyticsEvent.kt android/app/src/main/java/app/farmsy/android/core/SessionStore.kt android/app/src/main/java/app/farmsy/android/features/main/MainScreen.kt
git commit -m "analytics: keep one identity per install; add tab_viewed and auth_prompted

The auth stream's first emission on a cold start is a nil session for
anyone signed out, and the else-branch reset PostHog's anonymous id
there — so every launch was a new person, app_opened was orphaned on
iOS, and retention read 0.9% against RevenueCat's ~15%. Reset only on
a real sign-out now, both platforms.

tab_viewed (once per selection) and auth_prompted (when a signed-out
tap opens the sign-in sheet, with the trigger) so the funnel can see
which surfaces people reach and where the account wall stands.

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 2: `shopping_item_added` from `TripStore.toggleProduct`

Branch: `conv/analytics-identity` (continues).

**Files:**
- Modify: `Farmsy/Core/TripStore.swift:323-327` and every caller (`Farmsy/Features/Shopping/ShoppingScreen.swift:108,168,180,493`, `Farmsy/Features/Trips/ShoppingListSheet.swift:106`, `Farmsy/Features/Detail/FarmProductsSection.swift` two sites)
- Modify: `android/.../core/TripStore.kt:293-297` and every caller (`features/shopping/ShoppingScreen.kt:238,276,373,377,397`, `features/trips/ShoppingListSheet.kt:175`, `features/detail/FarmProductsSection.kt:123,128`)
- Create: `FarmsyTests/TripStoreTests.swift`

**Interfaces:**
- Consumes: `AnalyticsEvent.shoppingItemAdded`, `AnalyticsProp.item`, `AnalyticsValue.ListSource` from Task 1.
- Produces (iOS): `TripStore.toggleProduct(_ id: String, source: AnalyticsValue.ListSource)`. Android: `TripStore.toggleProduct(id: String, source: AnalyticsValue.ListSource)`. The old single-argument form no longer exists, so the compiler lists every caller.

- [ ] **Step 1: Write the failing test**

Create `FarmsyTests/TripStoreTests.swift`:

```swift
import Foundation
import Testing
@testable import Farmsy

/// The shopping list is the one thing onboarding, the farm card and the picker
/// all write to. Order and idempotence are what the planner relies on.
@MainActor
struct TripStoreTests {

    @Test("toggle adds in pick order, toggling again removes, source is required")
    func toggleRoundTrip() {
        let trip = TripStore()
        trip.clearProducts()
        trip.toggleProduct("eggs", source: .picker)
        trip.toggleProduct("cheese", source: .farmDetail)
        #expect(trip.wantedProducts == ["eggs", "cheese"])
        trip.toggleProduct("eggs", source: .picker)
        #expect(trip.wantedProducts == ["cheese"])
        trip.clearProducts()
    }
}
```

- [ ] **Step 2: Run it; expected FAIL to compile**

```bash
xcodebuild test -quiet -scheme Farmsy -destination 'platform=iOS Simulator,name=iPhone 17 Pro' -only-testing:FarmsyTests/TripStoreTests 2>&1 | tail -20
```

Expected: `extra argument 'source' in call`.

- [ ] **Step 3: Change `toggleProduct` (iOS)**

In `Farmsy/Core/TripStore.swift` replace:

```swift
    func toggleProduct(_ id: String) {
        if let i = wantedProducts.firstIndex(of: id) { wantedProducts.remove(at: i) }
        else { wantedProducts.append(id) }
        persistWanted()
    }
```

with:

```swift
    /// On or off. `source` names the surface that added it; the event fires here,
    /// on the add branch only, so no call site can forget it. A custom item's id
    /// carries what the person typed, and typed text never goes to analytics.
    func toggleProduct(_ id: String, source: AnalyticsValue.ListSource) {
        if let i = wantedProducts.firstIndex(of: id) {
            wantedProducts.remove(at: i)
        } else {
            wantedProducts.append(id)
            let reported = id.hasPrefix(ShoppingItem.customPrefix) ? "custom" : id
            Observability.capture(.shoppingItemAdded,
                                  [AnalyticsProp.item: reported, AnalyticsProp.source: source.rawValue])
        }
        persistWanted()
    }
```

- [ ] **Step 4: Update every iOS caller**

Build once to get the list (`xcodebuild build -quiet -scheme Farmsy -destination 'generic/platform=iOS Simulator' 2>&1 | grep error:`). Sources:

| File:line | source |
|---|---|
| `ShoppingScreen.swift:108` (remove button on the list) | `.picker` |
| `ShoppingScreen.swift:168` (picker chips) | `.picker` |
| `ShoppingScreen.swift:180` (`addTyped`) | `.typed` |
| `ShoppingScreen.swift:493` (re-add a previous list) | `.picker` |
| `Trips/ShoppingListSheet.swift:106` | `.picker` |
| `Detail/FarmProductsSection.swift` ("Add all to shopping" loop and the chip) | `.farmDetail` |

Each becomes `trip.toggleProduct(<id>, source: .<value>)`. The build must be clean afterwards.

- [ ] **Step 5: Same on Android**

In `android/.../core/TripStore.kt` replace:

```kotlin
    fun toggleProduct(id: String) {
        val cur = _wantedProducts.value
        _wantedProducts.value = if (id in cur) cur - id else cur + id
        persistWanted()
    }
```

with:

```kotlin
    /// On or off. `source` names the surface that added it; the event fires here,
    /// on the add branch only, so no call site can forget it. A custom item's id
    /// carries what the person typed, and typed text never goes to analytics.
    fun toggleProduct(id: String, source: AnalyticsValue.ListSource) {
        val cur = _wantedProducts.value
        if (id in cur) {
            _wantedProducts.value = cur - id
        } else {
            _wantedProducts.value = cur + id
            val reported = if (id.startsWith(ShoppingItem.CUSTOM_PREFIX)) "custom" else id
            Observability.capture(
                AnalyticsEvent.SHOPPING_ITEM_ADDED,
                mapOf(AnalyticsProp.ITEM to reported, AnalyticsProp.SOURCE to source.key),
            )
        }
        persistWanted()
    }
```

Callers (`./gradlew :app:compileDebugKotlin --no-daemon` lists them): `ShoppingScreen.kt:238` → `TYPED`; `:276`, `:373`, `:377`, `:397` → `PICKER`; `trips/ShoppingListSheet.kt:175` → `PICKER`; `detail/FarmProductsSection.kt:123`, `:128` → `FARM_DETAIL`. Form: `trip.toggleProduct(id, AnalyticsValue.ListSource.PICKER)`.

- [ ] **Step 6: Run tests, both platforms, and parity**

```bash
xcodebuild test -quiet -scheme Farmsy -destination 'platform=iOS Simulator,name=iPhone 17 Pro' -only-testing:FarmsyTests 2>&1 | tail -20
cd android && ./gradlew :app:testDebugUnitTest --no-daemon 2>&1 | tail -15; cd ..
scripts/analytics-parity.sh
```

Expected: green, green, OK.

- [ ] **Step 7: Commit**

```bash
git add -A Farmsy FarmsyTests android/app/src/main
git commit -m "analytics: shopping_item_added fires from TripStore.toggleProduct

toggleProduct takes the surface that added the item and emits the event
itself on the add branch, so the picker, typed items, the farm card's
product chips and (next) onboarding are all counted the same way and
no call site can forget. Custom items report item = custom, never the
typed text.

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 3: Paywall before the account wall

Branch: `git switch -c conv/paywall-before-auth` from the Task 2 head.

**Files:**
- Modify: `Farmsy/Features/Main/AppShell.swift` (`actions.openPlus`, `showAuthAtRoot`, new `showAuthInPlus`, `plusSheetContent`)
- Modify: `Farmsy/Features/Map/ProUpsellSheet.swift` (new `trigger` param, `pending`, `buy`, `restore` gating)
- Modify: `Farmsy/Core/PurchaseStore.swift:127-160` (`purchase` takes `UUID`)
- Modify: `android/.../features/main/MainScreen.kt` (`openPlus`, `ProUpsellSheet(...)` call), `android/.../features/map/ProUpsellSheet.kt`, `android/.../core/PurchaseStore.kt:165-176`

**Interfaces:**
- Consumes: `AnalyticsEvent.authPrompted`, `Trigger.restore` (Task 1).
- Produces (iOS): `ProUpsellSheet(trigger: AnalyticsValue.Trigger?)`; `PurchaseStore.purchase(_ package: Package?, userId: UUID) async -> Bool`. Android: `ProUpsellSheet(trigger, onDismiss)` unchanged signature; `PurchaseStore.purchase(activity, pkg, userId: String)`.

- [ ] **Step 1: `openPlus` no longer gates (iOS)**

In `AppShell.actions`:

```swift
            openPlus: { trigger in plusTrigger = trigger; showPlus = true },
```

Update the comment on `plusTrigger` (`/// Set only on the path that really presents Plus ...`) to: `/// Set whenever Plus is asked for; signed-out people see the sheet too and are asked to sign in at the plan tap (Task 3, conversion fixes).`

- [ ] **Step 2: Auth presents above Plus (iOS)**

In `AppShell`, add after `showAuthInFarmCard`:

```swift
    /// The plan buttons inside the Plus sheet ask for auth when signed out; the
    /// sheet is up, so only a `.sheet` nested on it can present. Same rule as the
    /// farm card and Profile above.
    private var showAuthInPlus: Binding<Bool> {
        Binding(get: { showAuth && showPlus }, set: { showAuth = $0 })
    }
```

Change `showAuthAtRoot` to exclude Plus:

```swift
        Binding(get: { showAuth && !showTrips && !showProfile && selectedPin == nil && !showPlus }, set: { showAuth = $0 })
```

In `plusSheetContent()`, pass the trigger and nest the auth sheet:

```swift
        ProUpsellSheet(trigger: plusTrigger)
            .presentationDetents([.fraction(0.92)])
            .presentationDragIndicator(.visible)
            .presentationCornerRadius(Radius.sheet)
            .sheet(isPresented: showAuthInPlus) { AuthView() }
            .onAppear {
```

(the `.onAppear` block stays as is).

- [ ] **Step 3: `purchase` requires a user id (iOS)**

In `Farmsy/Core/PurchaseStore.swift`, change the signature and the logIn block:

```swift
    func purchase(_ package: Package?, userId: UUID) async -> Bool {
```

and

```swift
        if Purchases.shared.appUserID != userId.uuidString {
            _ = try? await Purchases.shared.logIn(userId.uuidString)
        }
```

Update the doc comment's last paragraph to: `/// `userId` is the Supabase user id and is required: the sheet asks for sign-in before it gets here, so a purchase can never run under RevenueCat's anonymous id, which the webhook cannot grant.`

- [ ] **Step 4: Gate the plan buttons inside the sheet (iOS)**

In `Farmsy/Features/Map/ProUpsellSheet.swift`:

Add the parameter and state after `var onClose: () -> Void = {}`:

```swift
    /// What asked for the sheet; reported on `auth_prompted` when a signed-out
    /// plan tap opens sign-in.
    var trigger: AnalyticsValue.Trigger? = nil

    @Environment(\.requestAuth) private var requestAuth
    /// The tap that was waiting for sign-in. Consumed the moment the session
    /// appears; overwritten by the next tap; cleared when the sheet closes.
    @State private var pending: PendingAction?

    private enum PendingAction { case buy(Package), restore }
```

Add two functions after `close()`:

```swift
    /// Sign-in first, then the store. Signed in already: straight to the store.
    private func buy(_ package: Package?) {
        guard let uid = session.session?.user.id else {
            if let package { pending = .buy(package) }
            Observability.capture(.authPrompted, [AnalyticsProp.trigger: (trigger ?? .homeRow).rawValue])
            requestAuth()
            return
        }
        Task { if await purchases.purchase(package, userId: uid) { Haptics.success(); await awaitGrant() } }
    }

    private func restore() {
        guard session.session != nil else {
            pending = .restore
            Observability.capture(.authPrompted, [AnalyticsProp.trigger: AnalyticsValue.Trigger.restore.rawValue])
            requestAuth()
            return
        }
        Task { if await purchases.restore() { await awaitGrant() } }
    }
```

Replace the three tap bodies in `purchaseArea`:

- yearly button action: `buy(purchases.yearlyPackage)`
- lifetime button action: `buy(purchases.lifetimePackage)`
- "Restore purchases" action: `Haptics.tap(); restore()`

Delete `let uid = session.session?.user.id` from `purchaseArea` (no longer used).

On the outer `VStack` in `body`, after `.task { await purchases.loadOffering() }`, add:

```swift
        .onChange(of: session.isAuthenticated) { _, authed in
            guard authed, let action = pending else { return }
            pending = nil
            switch action {
            case .buy(let package): buy(package)
            case .restore: restore()
            }
        }
```

Note for the reviewer: `pending` survives an auth sheet dismissed without signing in. It is consumed only if the session appears while this sheet is still up, and the next tap overwrites it. Deliberate: the alternative needs the auth sheet's dismissal plumbed through AppShell for no user-visible gain.

- [ ] **Step 5: Build; fix any remaining `userId:` optional callers**

```bash
xcodebuild build -quiet -scheme Farmsy -destination 'generic/platform=iOS Simulator' 2>&1 | grep -E "error:" ; echo "exit ${PIPESTATUS[0]}"
```

Expected: no `error:` lines. `grep -rn "purchase(" Farmsy --include='*.swift'` shows only `PurchaseStore.purchase` and the two `buy` calls.

- [ ] **Step 6: Android — `openPlus` no longer gates; sheet gates the taps**

`android/.../features/main/MainScreen.kt`, in `shell`:

```kotlin
            openPlus = { trigger -> plusTrigger = trigger; showPlus = true },
```

`android/.../core/PurchaseStore.kt`: `suspend fun purchase(activity: Activity, pkg: Package?, userId: String): Boolean` and the logIn guard becomes `if (enabled && Purchases.sharedInstance.appUserID != userId)`.

`android/.../features/map/ProUpsellSheet.kt`:

After `val sheetState = ...` add:

```kotlin
    val requestAuth = LocalRequestAuth.current
    val currentSession by session.session.collectAsState()
    val userId = currentSession?.user?.id
    // The tap that was waiting for sign-in; consumed when the session appears.
    var pendingPkg by remember { mutableStateOf<Package?>(null) }
    var pendingRestore by remember { mutableStateOf(false) }
```

Delete the later line `val userId = session.session.collectAsState().value?.user?.id`.

Add, after `awaitGrant()`:

```kotlin
    fun buy(pkg: Package?) {
        val uid = userId
        if (uid == null) {
            pendingPkg = pkg
            Observability.capture(AnalyticsEvent.AUTH_PROMPTED, mapOf(AnalyticsProp.TRIGGER to (trigger ?: AnalyticsValue.Trigger.HOME_ROW).key))
            requestAuth()
            return
        }
        val activity = context as? Activity ?: return
        scope.launch { if (purchases.purchase(activity, pkg, uid)) awaitGrant() }
    }

    fun restore() {
        if (userId == null) {
            pendingRestore = true
            Observability.capture(AnalyticsEvent.AUTH_PROMPTED, mapOf(AnalyticsProp.TRIGGER to AnalyticsValue.Trigger.RESTORE.key))
            requestAuth()
            return
        }
        scope.launch { if (purchases.restore()) awaitGrant() }
    }

    LaunchedEffect(userId) {
        if (userId == null) return@LaunchedEffect
        pendingPkg?.let { pendingPkg = null; buy(it) }
        if (pendingRestore) { pendingRestore = false; restore() }
    }
```

Replace the three tap bodies: yearly → `buy(yearlyPkg)`, lifetime → `buy(lifetimePkg)`, restore text `clickable { restore() }`. Remove the now-unused `val activity = context as? Activity ?: return@PlanButton` lines inside those lambdas.

- [ ] **Step 7: Compile Android**

```bash
cd android && ./gradlew :app:compileDebugKotlin --no-daemon 2>&1 | tail -15; cd ..
```

Expected: `BUILD SUCCESSFUL`. Then run the unit tests once.

- [ ] **Step 8: Tap test (iOS simulator)**

```bash
xcrun simctl launch booted app.farmsy.ios --reset-onboarding
```

Skip onboarding. Signed out: Home → "Upgrade to Farmsy Plus" → the Plus sheet appears (not sign-in). Tap the yearly plan → the sign-in sheet appears above Plus. Cancel it → Plus still up. Tap again → sign-in → sign in with a sandbox account → the App Store sheet appears without a further tap. Cancel the store sheet → sheet stays, `purchase_failed` `reason = cancelled` in PostHog. Then open a farm card → its Plus row → repeat the first two taps (checks the farm-card nesting). Write the outcome of each tap in the report. If a tap does nothing, that is a defect in this task; fix it before reporting.

- [ ] **Step 9: Commit**

```bash
git add Farmsy/Features/Main/AppShell.swift Farmsy/Features/Map/ProUpsellSheet.swift Farmsy/Core/PurchaseStore.swift android/app/src/main/java/app/farmsy/android/features/main/MainScreen.kt android/app/src/main/java/app/farmsy/android/features/map/ProUpsellSheet.kt android/app/src/main/java/app/farmsy/android/core/PurchaseStore.kt
git commit -m "plus: show the paywall to everyone; ask for sign-in at the plan tap

openPlus wrapped the Plus sheet in requireAuth, so 87% of users — the
signed-out ones — got a sign-in form instead of the pitch and never saw
what Plus is. The sheet opens for all now; the yearly, lifetime and
restore taps ask for sign-in when there is no session and continue on
their own once it appears. purchase() takes a non-optional user id, so
a purchase can no longer run under RevenueCat's anonymous id.

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 4: Plus lock on every farm card

Branch: `git switch -c conv/farm-detail-lock` from the Task 3 head.

**Files:**
- Modify: `Farmsy/Core/FarmStatus.swift` (add `lockCopy`), `Farmsy/Features/Detail/FarmStatusSection.swift:59-61,80-110`, `Farmsy/Localizable.xcstrings`
- Modify: `FarmsyTests/FarmStatusTests.swift` (append)
- Modify: `android/.../core/FarmStatus.kt` (add `lockCopyRes`), `android/.../features/detail/FarmStatusSection.kt:153-183`, `android/app/src/main/res/values/strings_extra.xml`, `values-nl`, `values-fr`, `values-de`
- Modify: `android/app/src/test/java/app/farmsy/android/core/FarmStatusTest.kt` (append)

**Interfaces:**
- Produces (iOS): `FarmStatus.lockCopy(hasFreshReport: Bool) -> LocalizedStringResource`. Android: `FarmStatus.lockCopyRes(hasFreshReport: Boolean): Int` returning an `R.string` id.

- [ ] **Step 1: Failing tests**

Append to `struct FarmStatusTests` (before its closing brace):

```swift
    // MARK: - The Plus row on the card (conversion fixes, Task 4)

    @Test("the lock row has copy for both a reported and an unreported farm, and they differ")
    func lockCopy() {
        let fresh = String(localized: FarmStatus.lockCopy(hasFreshReport: true))
        let none = String(localized: FarmStatus.lockCopy(hasFreshReport: false))
        #expect(!fresh.isEmpty && !none.isEmpty)
        #expect(fresh != none)
    }
```

Append to `class FarmStatusTest`:

```kotlin
    @Test fun lockCopyDiffersByReports() {
        assertNotEquals(FarmStatus.lockCopyRes(hasFreshReport = true), FarmStatus.lockCopyRes(hasFreshReport = false))
        assertEquals(app.farmsy.android.R.string.status_confirmed_today_plus, FarmStatus.lockCopyRes(hasFreshReport = true))
        assertEquals(app.farmsy.android.R.string.status_lock_no_reports, FarmStatus.lockCopyRes(hasFreshReport = false))
    }
```

(add `import org.junit.Assert.assertNotEquals`).

- [ ] **Step 2: Run both; expected FAIL to compile** (`lockCopy` / `lockCopyRes` undefined).

- [ ] **Step 3: iOS copy selector + strings**

In `Farmsy/Core/FarmStatus.swift`, inside `enum FarmStatus`, add:

```swift
    /// The Plus row under the status headline. With a fresh report the promise
    /// is "see when"; without one it is what Plus will show once there is one.
    /// Never an empty row: on iOS the row used to need a fresh report, which on
    /// most farms meant it never appeared at all.
    static func lockCopy(hasFreshReport: Bool) -> LocalizedStringResource {
        hasFreshReport
            ? "Confirmed today — see when with Plus"
            : "Plus tells you when someone last found it open"
    }
```

In `Farmsy/Localizable.xcstrings`, add an entry for `"Plus tells you when someone last found it open"` in the same shape as the `"Pick a few — or none..."` entry:

- nl: `Plus vertelt je wanneer iemand het laatst open aantrof`
- fr: `Plus vous dit quand quelqu'un l'a trouvé ouvert pour la dernière fois`
- de: `Plus zeigt dir, wann es zuletzt geöffnet vorgefunden wurde`
- en: same as key, state `new`.

`"Confirmed today — see when with Plus"` already exists in the catalog; leave it.

- [ ] **Step 4: iOS row outside the headline**

In `FarmStatusSection.swift`:

In `body`, change the first line inside the `VStack` from `if summary.total > 0 { headline }` to:

```swift
            if summary.total > 0 { headline }
            if !session.hasFullAccess { plusRow }
```

In `headline`, delete the block:

```swift
            if !session.hasFullAccess, freshness != nil {
                Button {
                    shell.openPlus(.farmDetail)
                } label: {
                    Label(String(localized: "Confirmed today — see when with Plus"), systemImage: "lock.fill")
                        .font(.ui(12, .semibold))
                        .foregroundStyle(Color.farmGreen)
                }
                .buttonStyle(.plain)
            }
```

Add after `headline`:

```swift
    /// The one Plus entry on the card. Always present for a non-member.
    private var plusRow: some View {
        Button {
            shell.openPlus(.farmDetail)
        } label: {
            Label(String(localized: FarmStatus.lockCopy(hasFreshReport: freshness != nil)), systemImage: "lock.fill")
                .font(.ui(12, .semibold))
                .foregroundStyle(Color.farmGreen)
        }
        .buttonStyle(.plain)
    }
```

- [ ] **Step 5: Android selector, strings, row**

`android/.../core/FarmStatus.kt`, inside `object FarmStatus`:

```kotlin
    /// The Plus row under the status headline; see iOS FarmStatus.lockCopy.
    fun lockCopyRes(hasFreshReport: Boolean): Int =
        if (hasFreshReport) app.farmsy.android.R.string.status_confirmed_today_plus
        else app.farmsy.android.R.string.status_lock_no_reports
```

Add to `res/values/strings_extra.xml` next to `status_confirmed_today_plus`:

```xml
    <string name="status_lock_no_reports">Plus tells you when someone last found it open</string>
```

`values-nl`: `Plus vertelt je wanneer iemand het laatst open aantrof`; `values-fr`: `Plus vous dit quand quelqu\'un l\'a trouvé ouvert pour la dernière fois`; `values-de`: `Plus zeigt dir, wann es zuletzt geöffnet vorgefunden wurde`.

In `FarmStatusSection.kt`, change `if (!plus && freshness != null) {` to `if (!plus) {` and the row's text to `stringResource(FarmStatus.lockCopyRes(freshness != null))`. Also move the row out of the `else` of `if (plus && freshness != null)` so it renders regardless of `summary.total`: place it directly after that `if/else` block, still inside the `Column`.

- [ ] **Step 6: Tests + parity + simulator**

Run both test suites (green) and `scripts/analytics-parity.sh` (untouched, still OK). Simulator: open any farm signed out → lock row visible under the status buttons; tap → Plus sheet; PostHog live events show `paywall_viewed` with `trigger = farm_detail`.

- [ ] **Step 7: Commit**

```bash
git add Farmsy/Core/FarmStatus.swift Farmsy/Features/Detail/FarmStatusSection.swift Farmsy/Localizable.xcstrings FarmsyTests/FarmStatusTests.swift android/app/src/main/java/app/farmsy/android/core/FarmStatus.kt android/app/src/main/java/app/farmsy/android/features/detail/FarmStatusSection.kt android/app/src/main/res/values/strings_extra.xml android/app/src/main/res/values-nl/strings_extra.xml android/app/src/main/res/values-fr/strings_extra.xml android/app/src/main/res/values-de/strings_extra.xml android/app/src/test/java/app/farmsy/android/core/FarmStatusTest.kt
git commit -m "farm card: the Plus row shows on every farm, not only reported ones

The card is where attention is (1,722 iOS users in 30 days) and its
one Plus lock needed a fresh visitor report to render, which on iOS
meant farm_detail produced zero paywall views. Non-members now always
see the row; the copy says 'see when' with a report and what Plus will
show without one. Both platforms, four languages.

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 5: Onboarding basket step

Branch: `git switch -c conv/onboarding-basket` from the Task 4 head.

**Files:**
- Modify: `Farmsy/Core/ShoppingList.swift` (add `BasketSeed` + fallback dozen), `Farmsy/Features/Onboarding/OnboardingView.swift` (`Step`, state, `stepContent`, new `BasketStep`), `Farmsy/Localizable.xcstrings`
- Create: `FarmsyTests/BasketSeedTests.swift`
- Modify: `android/.../core/ShoppingList.kt` (add `BasketSeed`), `android/.../features/onboarding/OnboardingScreen.kt` (`Step`, state, `when`, new `BasketStep`), `res/values*/strings_extra.xml`
- Create: `android/app/src/test/java/app/farmsy/android/core/BasketSeedTest.kt`

**Interfaces:**
- Consumes: `TripStore.toggleProduct(_:source:)` (Task 2), `ShoppingItems.shared` / `ShoppingItems` catalogue, `Chip`, `FlowRow` (`Farmsy/Features/Trips/ShoppingListSheet.swift:274`, internal), Android `Chip` and `androidx.compose.foundation.layout.FlowRow`.
- Produces (iOS): `enum BasketSeed { static func toAdd(picked: [String], current: [String]) -> [String]; static let fallback: [ShoppingItem] }`. Android: `object BasketSeed { fun toAdd(picked: List<String>, current: List<String>): List<String>; val fallback: List<ShoppingItem> }`.

- [ ] **Step 1: Failing tests**

Create `FarmsyTests/BasketSeedTests.swift`:

```swift
import Foundation
import Testing
@testable import Farmsy

/// The onboarding basket writes into the same list the picker and the farm card
/// use. It must add what was picked, keep the pick order, and never duplicate.
struct BasketSeedTests {

    @Test("adds only what is missing, in pick order")
    func toAdd() {
        #expect(BasketSeed.toAdd(picked: ["eggs", "cheese", "milk"], current: ["cheese"]) == ["eggs", "milk"])
        #expect(BasketSeed.toAdd(picked: [], current: ["cheese"]).isEmpty)
        #expect(BasketSeed.toAdd(picked: ["eggs", "eggs"], current: []) == ["eggs"])
    }

    @Test("the offline fallback is twelve real catalogue ids with labels in both languages")
    func fallback() {
        let ids = BasketSeed.fallback.map(\.id)
        #expect(ids == ["eggs", "cheese", "milk", "potatoes", "vegetables", "fruits",
                        "meat", "honey", "bread", "strawberry", "apples", "butter"])
        for item in BasketSeed.fallback { #expect(!item.nl.isEmpty && !item.en.isEmpty) }
    }
}
```

Create `android/app/src/test/java/app/farmsy/android/core/BasketSeedTest.kt`:

```kotlin
package app.farmsy.android.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/// Mirror of FarmsyTests/BasketSeedTests.swift.
class BasketSeedTest {
    @Test fun addsOnlyMissingInPickOrder() {
        assertEquals(listOf("eggs", "milk"), BasketSeed.toAdd(listOf("eggs", "cheese", "milk"), listOf("cheese")))
        assertTrue(BasketSeed.toAdd(emptyList(), listOf("cheese")).isEmpty())
        assertEquals(listOf("eggs"), BasketSeed.toAdd(listOf("eggs", "eggs"), emptyList()))
    }

    @Test fun fallbackIsTwelveRealIds() {
        assertEquals(
            listOf("eggs", "cheese", "milk", "potatoes", "vegetables", "fruits", "meat", "honey", "bread", "strawberry", "apples", "butter"),
            BasketSeed.fallback.map { it.id },
        )
        BasketSeed.fallback.forEach { assertTrue(it.nl.isNotEmpty() && it.en.isNotEmpty()) }
    }
}
```

- [ ] **Step 2: Run both; expected FAIL** (`BasketSeed` undefined).

- [ ] **Step 3: `BasketSeed` (iOS)**

Append to `Farmsy/Core/ShoppingList.swift`:

```swift
/// The onboarding basket: which picks to add, and what to show when the
/// catalogue has not arrived. Pure, so it is testable without a store.
enum BasketSeed {
    /// Picks not already on the list, de-duplicated, in pick order.
    static func toAdd(picked: [String], current: [String]) -> [String] {
        var seen = Set(current)
        return picked.filter { seen.insert($0).inserted }
    }

    /// Twelve ids that exist on GET /api/shopping/items today, with the labels
    /// the picker would show. Used only while the catalogue is still loading or
    /// failed; when it arrives, its own items replace these.
    static let fallback: [ShoppingItem] = [
        ("eggs", "Eieren", "Eggs"), ("cheese", "Kaas", "Cheese"), ("milk", "Melk", "Milk"),
        ("potatoes", "Aardappelen", "Potatoes"), ("vegetables", "Groenten", "Vegetables"),
        ("fruits", "Fruit", "Fruit"), ("meat", "Vlees", "Meat"), ("honey", "Honing", "Honey"),
        ("bread", "Brood", "Bread"), ("strawberry", "Aardbeien", "Strawberries"),
        ("apples", "Appels", "Apples"), ("butter", "Boter", "Butter"),
    ].map { ShoppingItem(id: $0.0, nl: $0.1, en: $0.2, terms: [], category: nil, image: nil) }
}
```

(`ShoppingItem`'s memberwise init is available: `custom(_:)` in the same file already uses it.)

- [ ] **Step 4: `BasketSeed` (Android)**

Append to `android/.../core/ShoppingList.kt`:

```kotlin
/// The onboarding basket: which picks to add, and what to show when the
/// catalogue has not arrived. Pure, so it is testable without a store.
object BasketSeed {
    /// Picks not already on the list, de-duplicated, in pick order.
    fun toAdd(picked: List<String>, current: List<String>): List<String> {
        val seen = current.toMutableSet()
        return picked.filter { seen.add(it) }
    }

    /// Twelve ids that exist on GET /api/shopping/items today, with the labels
    /// the picker would show. Used only while the catalogue is still loading or
    /// failed; when it arrives, its own items replace these.
    val fallback: List<ShoppingItem> = listOf(
        Triple("eggs", "Eieren", "Eggs"), Triple("cheese", "Kaas", "Cheese"), Triple("milk", "Melk", "Milk"),
        Triple("potatoes", "Aardappelen", "Potatoes"), Triple("vegetables", "Groenten", "Vegetables"),
        Triple("fruits", "Fruit", "Fruit"), Triple("meat", "Vlees", "Meat"), Triple("honey", "Honing", "Honey"),
        Triple("bread", "Brood", "Bread"), Triple("strawberry", "Aardbeien", "Strawberries"),
        Triple("apples", "Appels", "Apples"), Triple("butter", "Boter", "Butter"),
    ).map { ShoppingItem(it.first, it.second, it.third, emptyList(), null, null) }
}
```

(`ShoppingItem.custom` at `ShoppingList.kt:~136` shows the constructor order: id, nl, en, terms, category, image. Match it; if `category` is non-null there, pass `"other"`.)

- [ ] **Step 5: Run the two unit tests; expected PASS.**

- [ ] **Step 6: The step (iOS)**

In `OnboardingView.swift`:

`enum Step`: `case welcome, personalize, basket, location, details, nearby, notify, done`.

Add state after `@State private var selectedCats`:

```swift
    /// Basket picks, in tap order; written to the shopping list on Continue.
    @State private var basketPicks: [String] = []
    @State private var catalogue = ShoppingItems.shared
```

Add `@Environment(TripStore.self) private var trip` next to the other environment values. Check it is injected: `grep -n "environment(trip)" Farmsy/FarmsyApp.swift` must show `.environment(trip)` on the root view that hosts onboarding (`FarmsyApp.swift:23` creates it). If it is only injected below onboarding, add `.environment(trip)` to the root alongside `.environment(farmsStore)`.

In `stepContent(for:)` add a case after `.personalize`:

```swift
        case .basket:
            BasketStep(items: catalogue.items.isEmpty ? BasketSeed.fallback : catalogue.items,
                       picked: $basketPicks,
                       onContinue: { seedBasket(); advance() },
                       onSkip: { skip() })
```

Add after `finish()`:

```swift
    /// Put the basket on the shopping list. Only what is missing, in pick order;
    /// each add reports `shopping_item_added` with source onboarding.
    private func seedBasket() {
        for id in BasketSeed.toAdd(picked: basketPicks, current: trip.wantedProducts) {
            trip.toggleProduct(id, source: .onboarding)
        }
    }
```

On the outer view (where `.sheet(isPresented: $showLogin)` is attached) add `.task { await catalogue.loadIfNeeded() }`.

Add the step view after `PersonalizeStep`:

```swift
// MARK: - Step 3: basket (what do you usually buy)

private struct BasketStep: View {
    let items: [ShoppingItem]
    @Binding var picked: [String]
    var onContinue: () -> Void
    var onSkip: () -> Void

    var body: some View {
        VStack(spacing: 0) {
            VStack(spacing: 10) {
                Kicker(text: String(localized: "Your basket"))
                DisplayTitle(String(localized: "What do you *usually* buy?"), size: 32)
                Text("Pick a few. Farmsy will show which farms nearby have them.")
                    .font(.ui(15))
                    .foregroundStyle(Color.inkMuted)
                    .multilineTextAlignment(.center)
            }
            .padding(.top, 22)
            .padding(.bottom, 22)
            .padding(.horizontal, 20)

            ScrollView(showsIndicators: false) {
                FlowRow(spacing: Space.s2) {
                    ForEach(items) { item in
                        Chip(label: item.label, emoji: item.emoji, selected: picked.contains(item.id)) {
                            Haptics.tap()
                            if let i = picked.firstIndex(of: item.id) { picked.remove(at: i) } else { picked.append(item.id) }
                        }
                    }
                }
                .padding(.horizontal, 20)
                .padding(.bottom, 8)
            }

            Button(picked.isEmpty ? "Skip" : "Continue", action: picked.isEmpty ? onSkip : onContinue)
                .buttonStyle(PrimaryButtonStyle())
                .accessibilityIdentifier("continue-basket")
                .padding(.horizontal, 20)
                .padding(.bottom, 12)
        }
    }
}
```

Strings for `Localizable.xcstrings` (same shape as the personalize entry):

| key | nl | fr | de |
|---|---|---|---|
| `Your basket` | `Je mandje` | `Votre panier` | `Dein Korb` |
| `What do you *usually* buy?` | `Wat koop je *meestal*?` | `Qu'achetez-vous *d'habitude* ?` | `Was kaufst du *meistens*?` |
| `Pick a few. Farmsy will show which farms nearby have them.` | `Kies er een paar. Farmsy laat zien welke boerderijen in de buurt ze hebben.` | `Choisissez-en quelques-uns. Farmsy vous montrera quelles fermes proches les ont.` | `Wähle ein paar. Farmsy zeigt dir, welche Höfe in der Nähe sie haben.` |

`DisplayTitle` renders `*word*` as the accent, as `PersonalizeStep` does with `*looking*`.

- [ ] **Step 7: The step (Android)**

`OnboardingScreen.kt`:

`private enum class Step { WELCOME, PERSONALIZE, BASKET, LOCATION, DETAILS, NEARBY, NOTIFY, DONE }`

State after `selectedCats`:

```kotlin
    var basketPicks by remember { mutableStateOf(listOf<String>()) }
    val trip = LocalTrip.current
    val catalogue by ShoppingItems.items.collectAsState()
    LaunchedEffect(Unit) { ShoppingItems.loadIfNeeded() }
```

Check `LocalTrip` is provided above `OnboardingScreen` (`grep -rn "LocalTrip provides" android/app/src/main/java`). If it is provided only inside `MainScreen`, provide it in `RootNav.kt` where `LocalRequestAuth` is provided, from the same `TripStore` instance MainScreen uses.

In the `when (s)` add after `Step.PERSONALIZE`:

```kotlin
                        Step.BASKET -> BasketStep(
                            items = catalogue.ifEmpty { BasketSeed.fallback },
                            picked = basketPicks,
                            onChange = { basketPicks = it },
                            onContinue = {
                                BasketSeed.toAdd(basketPicks, trip.wantedProducts.value)
                                    .forEach { trip.toggleProduct(it, AnalyticsValue.ListSource.ONBOARDING) }
                                advance()
                            },
                            onSkip = { skip() },
                        )
```

Add after `PersonalizeStep`:

```kotlin
@Composable
private fun BasketStep(
    items: List<ShoppingItem>,
    picked: List<String>,
    onChange: (List<String>) -> Unit,
    onContinue: () -> Unit,
    onSkip: () -> Unit,
) {
    val context = LocalContext.current
    val language = remember { ShoppingItems.language(context) }
    Column(Modifier.fillMaxSize().navigationBarsPadding()) {
        Column(
            Modifier.fillMaxWidth().padding(top = 22.dp, bottom = 22.dp).padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Kicker(stringResource(R.string.ob_basket))
            DisplayTitle(stringResource(R.string.ob_what_do_you), stringResource(R.string.ob_usually), stringResource(R.string.ob_buy_q), 32.sp, Modifier.fillMaxWidth())
            Text(stringResource(R.string.ob_basket_sub), style = geist(15.sp), color = FarmsyColors.inkMuted, textAlign = TextAlign.Center)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s2), verticalArrangement = Arrangement.spacedBy(Space.s2)) {
                items.forEach { item ->
                    val on = item.id in picked
                    Chip(item.label(language), emoji = item.emoji, selected = on) {
                        onChange(if (on) picked - item.id else picked + item.id)
                    }
                }
            }
        }
        PrimaryButton(
            if (picked.isEmpty()) stringResource(R.string.skip) else stringResource(R.string.continue_),
            Modifier.padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 12.dp),
        ) { if (picked.isEmpty()) onSkip() else onContinue() }
    }
}
```

`DisplayTitle` here takes (before, accent, after) like the personalize call; the strings are the three pieces of "What do you *usually* buy?". Add to `strings_extra.xml` (en) and the three translations:

| name | en | nl | fr | de |
|---|---|---|---|---|
| `ob_basket` | Your basket | Je mandje | Votre panier | Dein Korb |
| `ob_what_do_you` | What do you | Wat koop je | Qu\'achetez-vous | Was kaufst du |
| `ob_usually` | usually | meestal | d\'habitude | meistens |
| `ob_buy_q` | buy? | ? | ? | ? |
| `ob_basket_sub` | Pick a few. Farmsy will show which farms nearby have them. | Kies er een paar. Farmsy laat zien welke boerderijen in de buurt ze hebben. | Choisissez-en quelques-uns. Farmsy vous montrera quelles fermes proches les ont. | Wähle ein paar. Farmsy zeigt dir, welche Höfe in der Nähe sie haben. |

Imports needed in `OnboardingScreen.kt` if absent: `androidx.compose.foundation.layout.FlowRow`, `androidx.compose.foundation.verticalScroll`, `androidx.compose.foundation.rememberScrollState`, `app.farmsy.android.core.BasketSeed`, `app.farmsy.android.core.ShoppingItem`, `app.farmsy.android.core.ShoppingItems`, `app.farmsy.android.core.AnalyticsValue`, `app.farmsy.android.LocalTrip`, `app.farmsy.android.ui.theme.Chip`.

- [ ] **Step 8: Tests, parity, simulator**

Both suites green; parity OK. Simulator: `xcrun simctl launch booted app.farmsy.ios --reset-onboarding`, Welcome → Skip → Personalize → Continue → Basket appears with chips (catalogue or fallback), pick three → Continue → finish onboarding → Shopping tab shows the three items and the coverage number with the Plus lock. Progress bar advances one notch for the new step. Record in the report.

- [ ] **Step 9: Commit**

```bash
git add Farmsy/Core/ShoppingList.swift Farmsy/Features/Onboarding/OnboardingView.swift Farmsy/Localizable.xcstrings FarmsyTests/BasketSeedTests.swift android/app/src/main/java/app/farmsy/android/core/ShoppingList.kt android/app/src/main/java/app/farmsy/android/features/onboarding/OnboardingScreen.kt android/app/src/main/res android/app/src/test/java/app/farmsy/android/core/BasketSeedTest.kt
git commit -m "onboarding: a basket step seeds the shopping list

Welcome is skipped by 1,750 of 1,760 and nothing in onboarding gave
anyone a list, so the shopping-sample lock — the strongest Plus trigger
— fired for the 1% who built one by hand. A 'What do you usually buy?'
chip step after personalize writes picks into wantedProducts (source
onboarding); Shopping then opens with a list and a coverage number.
Fallback dozen when the catalogue has not loaded. Both platforms, four
languages.

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 6: Funnel C in PostHog, push, PRs

**Files:** none in the repo.

- [ ] **Step 1: Funnel C on dashboard 938846** (controller, from Luuk's logged-in Chrome tab on `eu.posthog.com`, same method as the two existing insights). POST `/api/projects/222496/insights/` with `X-CSRFToken` from cookie `posthog_csrftoken` and body:

```json
{
  "name": "Funnel C · Shopping → Plus",
  "dashboards": [938846],
  "query": {
    "kind": "InsightVizNode",
    "source": {
      "kind": "FunnelsQuery",
      "dateRange": { "date_from": "-30d" },
      "funnelsFilter": { "funnelWindowInterval": 7, "funnelWindowIntervalUnit": "day" },
      "breakdownFilter": { "breakdown": "platform", "breakdown_type": "event" },
      "series": [
        { "kind": "EventsNode", "event": "app_opened" },
        { "kind": "EventsNode", "event": "tab_viewed", "properties": [{ "key": "tab", "value": "shopping", "operator": "exact", "type": "event" }] },
        { "kind": "EventsNode", "event": "shopping_item_added" },
        { "kind": "EventsNode", "event": "paywall_viewed", "properties": [{ "key": "trigger", "value": "shopping_sample", "operator": "exact", "type": "event" }] },
        { "kind": "EventsNode", "event": "plan_tapped" },
        { "kind": "EventsNode", "event": "purchase_completed" }
      ]
    }
  }
}
```

Expected: 201 with an insight `short_id`; the dashboard shows three tiles. Note the short id in the PR 1 description.

- [ ] **Step 2: Push the four branches** (ask Luuk first, per norms)

```bash
git push -u origin conv/analytics-identity conv/paywall-before-auth conv/farm-detail-lock conv/onboarding-basket
```

- [ ] **Step 3: PRs, stacked** (Luuk opens; bases: PR1 → `main`, PR2 → `conv/analytics-identity`, PR3 → `conv/paywall-before-auth`, PR4 → `conv/farm-detail-lock`). Each body: the matching section of the spec, the test evidence lines from the task reports, the simulator tap results, and the footer `🤖 Generated with [Claude Code](https://claude.com/claude-code)`.

- [ ] **Step 4: After PR 1 ships**, tell Luuk to check Funnel A on dashboard 938846 joins from step 1 within a day of the release reaching users.

---

## Self-review

- **Spec coverage.** §1 identity → T1 steps 6-7; §1 events → T1 (tab_viewed, auth_prompted), T2 (shopping_item_added); §1 PostHog → T6; §2 → T3 incl. non-optional user id and nested auth sheet; §3 → T4 (lock always-on, copy) and T2 (chip events via `toggleProduct(source: .farmDetail)`); §4 → T5 incl. fallback dozen and `wantedProducts` write. Out-of-scope list respected.
- **Placeholders.** None. Translations given. Every code step is a literal diff or a table of call sites the compiler enumerates.
- **Names.** `toggleProduct(_:source:)` in T2 matches T5's `seedBasket` and T4's untouched chips; `ListSource.onboarding` matches both platforms; `lockCopy` / `lockCopyRes` match tests; `BasketSeed.toAdd(picked:current:)` matches tests on both platforms; `ProUpsellSheet(trigger:)` matches `plusSheetContent()`.
- **Known deviation from spec (T3):** `pending` is not cleared when the auth sheet is dismissed without signing in; it is consumed only if a session appears while the sheet is still up. Recorded in the task text for the reviewer.
