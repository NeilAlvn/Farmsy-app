# PORT NOTES

Places where the Android port diverges from iOS. Each note names the iOS construct,
the Android substitute, and where it lives. **Every entry carries one of two
statuses:**

- **ACCEPTED_DIVERGENCE** — the platform *cannot* express the iOS behavior. Names the
  specific missing API. The manifest row is closed as an accepted divergence.
- **DEFERRED** — the behavior *is* expressible, but not built (or built differently
  than ideal). Names the API that would do it. **The manifest row stays OPEN and does
  not count toward completion** until implemented.

---

## Detented sheets over the live map (S3 · MainScreen)

**Status: mostly implemented** — the 0.55/0.5 partial detents, the declared 0.92
expanded detent, and `upThrough`-partial background interaction (now offset-gated,
below) are built via `BottomSheetScaffold`. The one remaining gap is the farm-card
180px mini-detent → **DEFERRED** (own entry below).

**iOS:** `.sheet(...) { … }.presentationDetents([.fraction(0.55), .large])` with
`.presentationBackgroundInteraction(.enabled(upThrough:))` — the farm card and the
secondary screens present as sheets at partial/full heights *over the live map*,
and the map behind stays interactive up through a detent. The farm card also has a
third mini-detent `.height(180)`.

**Android:** Compose has no detent API. `MainScreen` uses `BottomSheetScaffold` +
`rememberStandardBottomSheetState(Hidden / PartiallyExpanded / Expanded,
skipHiddenState = false)`:
- The scaffold **body is the single shared map**.
- `sheetPeekHeight` = the **partial detent** (screen × 0.55, or × 0.5 for Trips) — a
  declared value.
- **Expanded is now a declared value**, not emergent: the sheet content wrapper is
  `fillMaxHeight(0.92f)`, so `BottomSheetScaffold`'s Expanded state settles at
  exactly 0.92 of the screen (a strip of map stays visible, matching iOS `.large`).
- **Background interaction is scoped (correct port — no re-label needed):** there is
  no *built-in* modifier for iOS `.enabled(upThrough: partial)`, but the behavior is
  **fully expressible** with Compose primitives and is **built faithfully**: a
  touch-consuming blocker (`pointerInput` consuming all events) gated on the **live
  drag offset** (`sheetState.requireOffset()` → block when visible height > partial
  peek). Blocking engages mid-drag the instant the sheet passes the partial detent,
  exactly matching `upThrough: partial` — interactive below, blocked above. A faithful
  reproduction is not a divergence; manifest S3 background-interaction row is VERIFIED.
  Auth (modal) is a `ModalBottomSheet` in RootNav whose scrim handles its own case.
- Closing: dragging the sheet fully down → `SheetValue.Hidden` clears the route +
  focus (returns the map to all-farms and reveals the pill).

**180px farm-card mini-detent — DEFERRED (API: `AnchoredDraggable`).** Manifest S3 farm-card detent row stays OPEN. iOS's farm card
has *three* stops `[.height(180), .fraction(0.55), .large]`. `BottomSheetScaffold`
exposes exactly **one** intermediate stop — `sheetPeekHeight` (the PartiallyExpanded
height) — plus Expanded. So `peekHeight` can express *either* the 180 mini-detent *or*
the 0.55 partial, **not both**: it cannot add a second intermediate stop. Reproducing
all three stops requires a custom `AnchoredDraggable` sheet with three anchors (out of
scope for this change), so the mini-detent is dropped and the farm card uses
`[0.55, 0.92]`.

File: `features/main/MainScreen.kt` (class doc + inline).

## focusPin fly / recenter (S4 · MapScreen)

**Status: DEFERRED** (API: `Projection.visibleRegion` for the exact on-screen span +
`CameraUpdateFactory.newLatLngZoom`). Manifest S4 focusPin-fly row stays OPEN.

The behavior **is expressible** — there is no `MKCoordinateRegion` type, but the same
information (the current on-screen lat/lng span) is available from
`cameraPositionState.projection.visibleRegion`, so iOS's `span * 0.28` south-shift and
"keep the user's zoom" can be reproduced exactly. The current code approximates: the
south-shift reads the last settled `viewport` span (capped 0.15°, fallback 0.12°) —
faithful — **but** the far-out case force-zooms with a `zoom < 11 → 12` heuristic
instead of iOS's exact `span = min(current, 0.15)` logic. Expressible, not yet exact →
DEFERRED. File: `features/map/MapScreen.kt` `LaunchedEffect(focusPin?.osmId)`.

## Collapsed trip detent + dragUpHint (S18 · TripsScreen)

**Status: DEFERRED → now IMPLEMENTED** via `SheetState.currentValue` (the earlier "Compose has no detent value to read" claim was wrong — the value exists and is used). Manifest S18 collapsed row stays OPEN pending on-device verification.

**iOS:** `TripsView` reads `detent == .fraction(0.5)` to decide `collapsed`, tucks
the overview list, and shows a `chevron.up` cue so the header + actions stay on
screen at the small detent.

**Android:** Compose has no detent value to read. `MainScreen` passes
`collapsed = (sheetState.currentValue == PartiallyExpanded)` into `TripsScreen`,
which shows the `KeyboardArrowUp` hint and tucks the overview list.

File: `features/trips/TripsScreen.kt` (`collapsed` param) + `features/main/MainScreen.kt`.

## Trip route widths — RESOLVED, not a divergence (removed)

The former entry claimed `Polyline.width` "has no dp unit" as a missing API. That is
not a real limitation: SwiftUI points ≈ Android dp, and `8.dp.toPx()` / `5.dp.toPx()`
via `LocalDensity` renders the widths at the exact physical size of iOS's 8pt/5pt,
preserving the 8:5 ratio at every density. This is a **correct, complete port** — the
entry has been removed and manifest S4 route-width row is VERIFIED
(`features/map/MapScreen.kt` `routeCasingPx` / `routeLinePx`).

## Route aboveLabels ordering — ACCEPTED_DIVERGENCE (S4 · MapScreen)

**Status: ACCEPTED_DIVERGENCE** — missing API: no public overlay/label z-ordering on `GoogleMap`/`Polyline`; overlays always render beneath base-map labels, and `zIndex` orders overlays only among themselves.

**iOS:** the route polylines use `.mapOverlayLevel(level: .aboveLabels)` so the line
draws over the base map's place/road labels.

**Android:** Google Maps (and its Compose wrapper) render **all** overlays —
polylines included — in a layer **beneath** the base map's labels; there is **no
public API** to raise an overlay above the label layer. `Polyline.zIndex` only orders
overlays relative to one another (casing vs line vs markers), not relative to base-map
labels. So `aboveLabels` **cannot be expressed** on Google Maps; the route renders
correctly among the overlays but map labels can draw over it. File:
`features/map/MapScreen.kt` (route block, inline PORT NOTE).

## Sheet shadow (S3 pill)

**Status: DEFERRED (API: `Modifier.shadow(elevation, shape, ambientColor, spotColor)`)** — spot/ambient colour can match iOS's shadow colour more closely than `Surface.shadowElevation` (y-offset still not directly settable). Manifest S3 pill-shadow detail stays OPEN. Compose `Surface.shadowElevation` is a Material elevation — it cannot reproduce
iOS's exact shadow (`color .14 / radius 12 / y-offset 3`). `12.dp` is the closest
visual match. File: `features/main/MainScreen.kt`.

## Panel-item icon size (S3 pill)

**Status: DEFERRED** — reclassified from ACCEPTED_DIVERGENCE: this is a visual
approximation, not a missing API. Compose icon size is a freely settable `Dp`
(`Modifier.size(...)`), and the iOS point value can even be carried across as `17.dp`.
SF Symbols carry optical sizing/weight that a Material glyph's bounding box doesn't
reproduce 1:1, so `20.dp` is used as the closest visual match — a tuning gap, not an
inexpressible one (API: `Modifier.size` + matched glyph). Manifest S3 pill-icon detail
stays OPEN. iOS pill icons are SF Symbols at `system(17,.semibold)` (Compose default is
24.dp). File: `features/main/MainScreen.kt` `PanelItem`.

## tapCard haptic (App/TapCard.swift · Components.kt) — RESOLVED

**Status: RESOLVED (implemented), not a divergence.** iOS `TapActivate` fires
`Haptics.tap()` (`UIImpactFeedbackGenerator(.light)`) before the action. This is fully
expressible: `Modifier.tapCard` now reads `LocalHapticFeedback` and calls
`performHapticFeedback(HapticFeedbackType.TextHandleMove)` — Compose's light tick, the
closest equivalent to a `.light` impact — before `onTap()`. `tapCard` became a
`@Composable` modifier factory to read the composition-local. No divergence remains.
File: `ui/theme/Components.kt` `Modifier.tapCard`.

## C7 ExpandableText — clamp/truncation measurement (Detail/FarmDetailView.swift · Components.kt)

**Status: DEFERRED** (API: `TextMeasurer` / `Paragraph` character-level binary search).
iOS `ExpandableText` finds the longest prefix that still fits `lineLimit` lines by a
**binary search over character count**, measuring each candidate with
`UIFont(Geist-Regular,15)` + `NSString.boundingRect` against
`font.lineHeight * lineLimit + 1`. The Android component instead detects truncation from
`TextLayoutResult.hasVisualOverflow` and trims back to `getLineEnd(lineLimit-1)`. The
visible result is equivalent (suffix sits at the end of the last visible line), but the
exact prefix can differ by a character or two at the boundary because the mechanisms
differ. The exact iOS approach **is** expressible with `rememberTextMeasurer` +
per-candidate measurement, just not built — so this stays OPEN, not accepted. C7 is
retained as a shared component for S7 FarmDetail (it is no longer used in C4).
File: `ui/theme/Components.kt` `ExpandableText`.

## C4 teaser — static clamp vs interactive C7 (Map/WhatsNewSheet.swift) — RESOLVED

**Status: RESOLVED (reverted to iOS), not a divergence.** A prior pass had C4's
`MultiImageFarmCard` teaser use the interactive C7 `ExpandableText` (tap-to-expand
inline, suffix on visual overflow). iOS C4 does **not**: it renders a **static** 3-line
clamp with a purely decorative `"  … View more"` suffix appended only when
`teaser.count > 140` (the card's own `tapCard` opens the farm; the text is inert). The
Android C4 now matches — a `buildAnnotatedString` with the farmGreen/bold suffix gated on
`teaser.length > 140`, `maxLines = 3`, `TextOverflow.Ellipsis`. No divergence remains.
File: `features/whatsnew/WhatsNewSheet.kt` `MultiImageFarmCard`.

## C3 timeAgo — unit-letter localization (Discover/DiscoverFeedView.swift · strings) — RESOLVED

**Status: RESOLVED (reverted to iOS), not a divergence.** A prior pass localized the
time-unit letters per locale (nl hour `u`, fr day `j`, de day `t`) and added a French
`many` plural class. iOS localizes **only** `just now` (the `Localizable.xcstrings`
catalog carries de/fr/nl for that key); the `%lldm` / `%lldh` / `%lldd` unit strings are
`en`-only, so iOS renders `m` / `h` / `d` in **every** locale. The invented unit plurals
were deleted from `values-nl` / `values-fr` / `values-de` (they now fall back to the
default `values` — English `m/h/d`), and `time_just_now` stays localized in each. Matches
iOS exactly. Files: `res/values*/strings_l10n.xml`.
