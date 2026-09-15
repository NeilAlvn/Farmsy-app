# Farmsy iOS → Android port — HANDOFF

State snapshot for a fresh session with no prior context. Facts below were read
from `PORT_MANIFEST.md`, `PORT_NOTES.md`, and git on 2026-08-26 — re-read those
files before trusting any status here; this document goes stale, they don't.

---

## 1. What this project is

A disciplined, exhaustive **1:1 port of the iOS app to Android**.

- **iOS (source of truth):** SwiftUI, at `Farmsy/Farmsy/` (`Features/…`, `Core/…`).
- **Android (target):** Jetpack Compose, at `android/` (`app/src/main/java/app/farmsy/android/…`).

The port must match iOS behavior, layout, animation, colours, and copy — not
just "look similar." Where Android genuinely cannot match iOS, that is recorded
as a divergence (see §4), never silently changed.

---

## 2. Working process (read this before touching anything)

- **`PORT_MANIFEST.md` is the spec.** It enumerates every screen (S1–S21) and
  reusable component (C1–C8) plus tokens, fonts, models, API, stores, and an
  element-by-element breakdown (§8). Each row carries a status:
  `NOT_STARTED` / `IN_PROGRESS` / `VERIFIED` / `N/A` (and, for divergences,
  `OPEN` + `DEFERRED` / `ACCEPTED_DIVERGENCE`).
- **iOS is the source of truth.** When manifest and code disagree, the iOS
  source wins and the manifest/port is corrected to it.
- **A port (BUILD) session never marks its own work `VERIFIED`.** It sets
  touched rows to `IN_PROGRESS` and writes PORT NOTEs for anything Compose
  can't express. `VERIFIED` is granted only by a separate **audit** session.
  - Corollary: a row that says **`DONE`** (e.g. S1 Splash) is a *port-session
    claim*, not an audit result. It still needs a fresh-session audit to become
    `VERIFIED`. Treat `DONE` as "built, unaudited."
- **Audits run in fresh sessions**, adversarially: change no code, read the
  *current* source (not prior claims), assume incomplete until proven, and
  report `VERIFIED / INCOMPLETE / MISSING` with `file:line`.
- **Anything in the Android app that has no manifest row is a defect.** New
  Android behavior is not allowed to appear without first existing in the
  manifest (which is derived from iOS). "It compiles and looks fine" is not a
  reason to keep an unmanifested addition.
- **Core is off-limits to feature sessions.** If a feature session finds a core
  gap (a store/model/API it depends on is missing or wrong), it STOPS and
  reports — it does not patch core from a feature session.

---

## 3. Where the port stands (from `PORT_MANIFEST.md`)

Status-token counts across the manifest: **345 NOT_STARTED, 29 IN_PROGRESS,
7 DEFERRED, 5 OPEN, 3 VERIFIED, 1 ACCEPTED_DIVERGENCE, 7 N/A.** Most of the app
is not started yet; a handful of screens and the shared-map spine are in flight.

### Screens (§0 index + §8 detail)
- **S1 SplashScreen — DONE (port-claimed, UNAUDITED).** All 17 rows claimed
  done in `features/splash/SplashScreen.kt`. Needs an audit to reach VERIFIED.
- **S3 MainScreen — IN_PROGRESS.** `features/main/MainScreen.kt`. The single
  shared-map shell: farm detail + secondary screens present as detented
  `BottomSheetScaffold` sheets over the live map. Sub-row **VERIFIED:
  background-interaction** (offset-gated blocker reproduces iOS
  `upThrough: partial`). Discover pill routes to WhatsNewSheet (S6), matching iOS.
- **S4 MapScreen — IN_PROGRESS.** `features/map/MapScreen.kt`. `focusPin` fly +
  highlighted pin, trip route/stops on the shared map, fit-to-trip. Sub-row
  **VERIFIED: route widths** (density-exact 8:5).
- **S6 WhatsNewSheet — IN_PROGRESS.** `features/whatsnew/WhatsNewSheet.kt`.
  Header, posts (skeletons / empty / PingCard list), featured farms, and the
  recommendation carousel. Photo-tap is stubbed to open the farm (S10 lightbox
  unbuilt).
- **S18 TripsScreen — IN_PROGRESS.** `features/trips/TripsScreen.kt`. Sheet
  content over the one shared map; `collapsed` tucks the overview list. Origin
  town-search (S19 PlaceSearchSheet) not ported.
- **Everything else (S2 onboarding, S5 filter sheet, S7 detail, S8 paywall,
  S9 member sections, S10 lightbox, S11 feed, S12 saved, S13–S17, S19–S21) —
  NOT_STARTED.**

### Components (§1.3 / §8 C-rows)
- **IN_PROGRESS:** C1 TripRecommendations (`discover/RecommendationCarousel.kt`),
  C3 PingCard, C4 MultiImageFarmCard, C5 FixedImageRow, C7 ExpandableText
  (`ui/theme/Components.kt`), SkeletonBox, TapCard (`.tapCard`, now with haptic).
- **NOT_STARTED:** C2 DiscoverFeedCard, C8 PostComposer/ReviewComposer, and the
  `Haptics` helper row (note: tapCard already fires a light haptic via
  `LocalHapticFeedback`, but the standalone `Haptics` object row is not started).
- **N/A:** C6 FarmCard (legacy iOS bottom card; MainView uses FarmDetailView).

### Core (§6 stores) — all IN_PROGRESS or NOT_STARTED
- `FarmsStore` IN_PROGRESS (featured/gallery machinery + multi-select
  `selectedCategories` + `nearbyWithImages` landed this session).
- `PurchaseStore` IN_PROGRESS (`didLoadOffering`/`productsUnavailable`/
  `displayPrice`).
- `SessionStore` row still NOT_STARTED in the manifest table though the type
  exists — verify against source.

### VERIFIED, precisely
No whole screen is VERIFIED. The 3 `VERIFIED` tokens are **sub-row**
verifications: S3 background-interaction, and S4 route-widths (referenced twice).
Everything else that's been touched is `IN_PROGRESS` awaiting audit.

### OPEN / DEFERRED rows (do NOT count toward completion)
- S3 farm-card **180 mini-detent** — DEFERRED (fix: `AnchoredDraggable`, 3 anchors).
- S3 **pill shadow colour** — DEFERRED (fix: `Modifier.shadow` spot/ambient colour).
- S4 **focusPin fly** exact span — DEFERRED (fix: `Projection.visibleRegion` +
  `newLatLngZoom`; current far-out case uses a `zoom<11→12` heuristic).
- C7 **exact-fit clamp** — DEFERRED (fix: `TextMeasurer` char-level binary
  search; current uses `TextLayoutResult.hasVisualOverflow` + `getLineEnd`).
- S18 collapsed detent — implemented, row stays OPEN pending on-device check.

### ACCEPTED_DIVERGENCE (genuinely inexpressible)
- **S4 route `aboveLabels`** — Google Maps exposes no overlay-vs-base-label
  z-order API; `zIndex` orders overlays only among themselves. This is the only
  true accepted divergence in the manifest.

---

## 4. The two-status rule for `PORT_NOTES.md`

Every divergence entry carries exactly one of:

- **`ACCEPTED_DIVERGENCE`** — the platform *cannot* express the iOS behavior.
  The entry **must name the specific missing API**. The manifest row is closed
  as an accepted divergence.
- **`DEFERRED`** — the behavior *is* expressible but was not built (or built a
  simpler way). The entry **must name the API that would do it**, and the
  **manifest row stays OPEN** and does not count toward completion until built.

Current `PORT_NOTES.md` entries:
- Detented sheets (S3) — mostly implemented; background-interaction VERIFIED.
- focusPin fly (S4) — DEFERRED.
- Collapsed trip detent (S18) — DEFERRED → implemented; row OPEN pending device.
- Route aboveLabels (S4) — **ACCEPTED_DIVERGENCE** (the one genuine case).
- Sheet shadow (S3 pill) — DEFERRED.
- Panel-item icon size (S3 pill) — DEFERRED (reclassified from ACCEPTED_DIVERGENCE:
  icon size is a settable `Dp`, so it's an approximation, not a missing API).
- Trip route widths — RESOLVED (note removed; row VERIFIED).
- tapCard haptic — RESOLVED.
- C7 clamp/truncation measurement — DEFERRED.
- C4 teaser static clamp vs interactive C7 — RESOLVED.
- C3 timeAgo unit-letter localization — RESOLVED.

> **Caveat / open decision:** several entries use a third, informal status
> **`RESOLVED`** for divergences that were later fixed. Under the strict
> two-status rule, "a fixed divergence is not a divergence" — such entries
> should be **deleted** and the outcome carried by the manifest row (as was done
> for the dashed-border case). Whether to purge the `RESOLVED` entries is an
> **open decision** (see §6).

---

## 5. Branch state & Play

- **`main`** (currently at `a40b1db`) — the **port line**. Carries all port work
  (S1/S3/S4/S6/S18 + components + core), the build toolchain pin, the API-36
  audit fixes, `PORT_MANIFEST.md`, `PORT_NOTES.md`, and the Aviah handoff. This
  is where the port continues. It carries `versionCode = 22`, `targetSdk 36`.
  Pushed to `origin/main` (GitHub `NeilAlvn/Farmsy-app`).

- **`compliance/api36-hotfix`** (at `b6761d3`, **LOCAL — NOT pushed**) — a
  minimal Play-compliance release cut **off `8b0e2e8`** (the published build),
  deliberately **excluding all in-flight port work**. Contains only:
  targetSdk/compileSdk 35→36, `suppressUnsupportedCompileSdk`, versionCode 25,
  and the map-attribution `contentPadding` fix (bottom = system bars +
  bottomInset; start = past the bottom-start CategoryMenu pill so the Google
  logo/"Terms" clears it under edge-to-edge). `PORT_MANIFEST.md`/`PORT_NOTES.md`
  do **not** exist on this branch (it predates them). A signed AAB is built at
  `android/app/build/outputs/bundle/release/app-release.aab` (11 MB, versionCode
  25) — **not uploaded**.

- **Live on Play:** the published baseline is **build 21** (`versionCode 21`,
  `versionName 1.0`, `targetSdk 35`) = commit **`8b0e2e8`**. **versionCodes
  22, 23, and 24 have already been consumed on Play**, so the compliance AAB is
  **versionCode 25** (next unused). The compliance AAB is the intended
  API-36 submission; the port on `main` ships later and separately (and, because
  22 is burned, will need a versionCode **> 25** when it does).

> The map fix differs between branches **on purpose**: on `main`, CategoryMenu
> has no bottom-start call site (moved to a FilterSheet), so its map fix is
> bottom-padding only; on the compliance branch (pre-refactor design)
> CategoryMenu *is* bottom-start, so it also needs the `start` padding. Do not
> reconcile the two by copying one onto the other.

---

## 6. Open threads (in order)

1. **Compliance AAB (build 25) → Play upload.** Ready and signed, not uploaded.
   Before upload, do an **on-device eyes-on check** of the map logo/attribution
   in both gesture-nav and 3-button-nav (the fix is verified by source geometry,
   not a screenshot). Upload is a manual Play Console step.
2. **Deep-link `.well-known` files — WEB side (Aviah).** Referral capture is
   broken on both platforms until `assetlinks.json` and
   `apple-app-site-association` are filled in on both `farmsy.app` and
   `www.farmsy.app`. Full spec + fingerprints in `aviah-task.txt`.
3. **Continue the port on `main`** — next screens: S7 FarmDetail (reuses C7
   ExpandableText), S9 FarmMemberSections, S10 ImageLightbox (unblocks the S6
   photo-tap stub), S13 Settings, S19 PlaceSearch (unblocks Trips origin), plus
   finishing the IN_PROGRESS rows.
4. **Audits owed (fresh sessions):** S1 Splash (DONE-claimed, unaudited), and
   the IN_PROGRESS S3/S4/S6/S18 + C1/C3/C4/C5/C7 rows — none are VERIFIED yet.
5. **Decide the `PORT_NOTES.md` RESOLVED cleanup** (§4 caveat): delete the
   RESOLVED entries and let the manifest rows carry the outcome, or keep them.
6. **`main` versionCode hygiene:** `main` still declares `versionCode = 22`,
   which is burned on Play. Bump it above 25 before cutting any release from `main`.

---

## 7. Toolchain notes (needed to build)

The Android build needs a JDK the default shell can't see out of the box:

- The only JDK on this machine is **Homebrew keg-only `openjdk@17`** at
  `/usr/local/Cellar/openjdk@17/17.0.19/libexec/openjdk.jdk/Contents/Home`.
  Being keg-only, it is **not** on the default `PATH` and `/usr/bin/java` is the
  macOS stub — so a bare `java` / `./gradlew` reports "Unable to locate a Java
  Runtime." This is an env issue, **not** a missing JDK.
- **`~/.zshenv`** exports `JAVA_HOME` (that keg) and puts it on `PATH`. `.zshenv`
  is sourced by *every* zsh invocation (unlike `.zshrc`), so new terminals and
  tool shells can bootstrap the Gradle wrapper.
- **`android/gradle.properties`** pins
  `org.gradle.java.home=/usr/local/Cellar/openjdk@17/17.0.19/libexec/openjdk.jdk/Contents/Home`
  so the Gradle **daemon/toolchain** uses JDK 17 regardless of the launching
  shell. (The wrapper still needs a launchable `java` to *start*, which
  `~/.zshenv` provides.)
- If a build errors on the JDK, export it explicitly for that shell:
  `export JAVA_HOME="/usr/local/Cellar/openjdk@17/17.0.19/libexec/openjdk.jdk/Contents/Home"`.
  Do **not** use the also-installed `openjdk` 26 keg — Gradle 8.11.1 rejects it.
- Release builds also need local-only `android/secrets.properties`
  (`MAPS_API_KEY`, `REVENUECAT_KEY`, …) and `android/keystore.properties`
  (+ `keystore/farmsy-release.jks`); both are gitignored and present on this
  machine. `assembleDebug` works without them; `bundleRelease` needs the keystore.
- No Android Studio is installed (by choice); the SDK is the Homebrew
  `android-commandlinetools` at `/usr/local/share/android-commandlinetools`
  (pointed to by `android/local.properties`), with platform-tools,
  `platforms;android-36`, `build-tools;36.0.0`, licenses accepted.

---

## 8. Key files

| Path | What |
|---|---|
| `PORT_MANIFEST.md` | The spec — every screen/component/token/model/API row + status. |
| `PORT_NOTES.md` | Divergences (two-status rule). |
| `Farmsy/Farmsy/` | iOS source of truth (SwiftUI). |
| `android/app/src/main/java/app/farmsy/android/` | Android target (Compose). |
| `aviah-task.txt` | Handoff channel to Aviah (web); git is the sync. |
| `android/gradle.properties` | JDK pin (`org.gradle.java.home`). |
| `~/.zshenv` | Puts keg-only openjdk@17 on PATH for every shell. |
