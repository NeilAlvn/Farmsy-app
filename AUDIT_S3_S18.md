# Audit — shared-map shell (S3 MainScreen / S4 MapScreen / S6 WhatsNewSheet / S18 TripsScreen)

Read-only. Build: `./gradlew clean assembleDebug` → **BUILD SUCCESSFUL** (13s, real gradle log; lib-strip warning only). Tree builds → not INCOMPLETE.

## Hard checks

### S3 background-interaction — CONFIRMED live-offset (not settle); VERIFIED grant invalid; runtime device-gated
`MainScreen.kt:126-131`: `blockBackground = derivedStateOf { (screenHeightPx - sheetState.requireOffset()) > peekPx + 1f }`. `requireOffset()` is the **live drag offset** (snapshot state); `derivedStateOf` recomputes continuously, so the boolean flips **mid-drag** the instant visible height passes the partial detent — NOT gated on `currentValue`/Expanded settle. The blocker (`MainScreen.kt:195-203`) is a real `pointerInput` loop consuming every pointer change over map+pill — not a stub. **Mechanism is a faithful reproduction of iOS `.enabled(upThrough: .fraction(0.55))` (`MainView.swift:50`).** But the manifest's VERIFIED was self-granted by the authoring session in a PORT NOTE (`PORT_NOTES.md:44`) on source alone — a note cannot promote a row. **Verdict: mechanism CONFIRMED-by-source; actual touch-blocking is DEVICE-GATED** — gesture: slow-drag the sheet up past the partial detent and try to pan/tap the map behind (must be inert above, live below). Not VERIFIED by this pass.

### S3 expanded detent 0.92 vs iOS .large — DISPUTED (FARM diverges)
`MainScreen.kt:167` `fillMaxHeight(0.92f)` — a **uniform** 0.92 expanded anchor for ALL routes. iOS (`MainView.swift:49,62,74,81`) uses `.fraction(0.92)` for Discover/Saved/Settings/Trips (Android **exact match**) but **`.large` for the FARM card** (`[.height(180), .fraction(0.55), .large]`, :49). `.large` ≈ full safe height (~0.93–0.95, ~1% taller than 0.92) → **Android FARM settles slightly lower than iOS `.large`, showing a marginally larger top map strip.** Also, Android FARM lacks iOS's third `.height(180)` mini-detent (manifest already flags this OPEN/DEFERRED via `AnchoredDraggable`). **Verdict: 0.92 does NOT equal iOS `.large` for FARM (near-match, ~1%, device-gated); it is exact for the other four routes.** Gesture: expand the FARM sheet, compare top gap to iOS `.large`.

### S4 route widths — CONFIRMED density-exact 8:5; VERIFIED grant invalid; render device-gated
`MapScreen.kt:270-271` `routeCasingPx = 8.dp.toPx()`, `routeLinePx = 5.dp.toPx()`; applied at `:489-490` as Polyline widths. `.dp.toPx()` converts iOS's 8pt/5pt at the live density → physically-exact 8:5 at every density. **CONFIRMED-by-source.** VERIFIED was self-granted (`PORT_NOTES.md:94`) — invalid. **Verdict: values CONFIRMED exact; rendered stroke DEVICE-GATED** — gesture: show a route, eyeball casing:line at device density. Not VERIFIED by this pass.

### S4 focusPin fly — CONFIRMED heuristic, DEFERRED row OPEN, note conforms
`MapScreen.kt:392-400`: `if (z < 11f) animate(newLatLngZoom(lat-0.045, lng, 12f)) else animate(newLatLng(lat - latSpan*0.28, lng))` — the `zoom<11→12` heuristic, NOT iOS's exact on-screen span logic. Manifest §S4 row = **OPEN, DEFERRED**; `PORT_NOTES.md:62` DEFERRED names `Projection.visibleRegion` + `newLatLngZoom`. **Verdict: conforms — row correctly OPEN, note is a valid two-status DEFERRED.**

### S6 photo-tap — CONFIRMED real lightbox at tapped index (not a stub)
`WhatsNewSheet.kt:152-161`: `onOpenImage = { idx -> lightbox = LightboxSource(images = ping.images, startIndex = idx, eyebrow = fromAPost, title = ping.authorName, subtitle = farm?.name, postText = ping.body) }`; presented at `:193-194` `lightbox?.let { ImageLightbox(source = it, onClose = ...) }`. All six fields + `startIndex = idx` are 1:1 with iOS (`WhatsNewSheet.swift:59-64`). Featured-card photos correctly NOT tappable (iOS FixedImageRow no onTap). **Verdict: CONFIRMED wired, correct index, real S10 ImageLightbox — not a renamed stub. Presentation visual device-gated.**

### S18 collapsed detent — code reads currentValue (settle-based); note self-contradiction
`MainScreen.kt:144` `val collapsed = sheetState.currentValue == SheetValue.PartiallyExpanded` → passed to `TripsScreen(collapsed=)`; `TripsScreen.kt:137,175` gate the overview list + dragUpHint on it. **The code reads `sheetState.currentValue` (a settled detent value).** So `PORT_NOTES.md:76` header ("the value exists and is used") is CORRECT, and the stale `TripsScreen.kt:134` code-comment ("Compose has no detent value") is WRONG. It is **settle-based** (currentValue = settled anchor), which is fine for a collapsed cue and functionally equivalent to iOS's `detent == .fraction(0.5)` binding (also settle-based). **Verdict: code uses currentValue; the :134 comment is stale and should be deleted; runtime tuck/hint is DEVICE-GATED** — gesture: drag the Trips sheet to the 0.5 detent, verify the overview list tucks and the dragUpHint appears.

## Separate checks

### 1. Unmanifested Compose elements — NONE
Every composable maps to a row: MainScreen/PanelItem (S3 pill); pin/cluster/highlight/tripStop bitmaps → FarmPinView/ClusterBubble/TripStopMarker specs (S4); MapScreen searchRow/locate/AI/route/stops (S4); WhatsNewSheet PingCard/MultiImageFarmCard/FixedImageRow/SkeletonBox/EmptyPosts (S6 + C-rows); TripsScreen TabButton/OriginRow/StopRow/ModeButton/MyTripsTab/SavedRow/Gate (S18 rows 1–10 + My-trips states). **FilterSheet/FilterGroupHeader/FilterChip/CategoryMenu live in MapScreen.kt but are S5** (`§0:30`, NOT_STARTED) — out of scope, colocated, NOT unmanifested.

### 2. PORT_NOTES entries touching this cluster — re-judged
- `:60` focusPin (S4) DEFERRED, names `Projection.visibleRegion` — CONFORMS.
- `:97` route aboveLabels (S4) **ACCEPTED_DIVERGENCE — VALID.** iOS renders the route `aboveLabels` while KEEPING road/city labels (`.standard(pointsOfInterest: .excludingAll)` drops only POIs, `MapScreen.swift:418`). Google Maps Android has no API to place a Polyline above the base-map's own text labels; `zIndex` orders overlays only among themselves (code comment `MapScreen.kt:486`). The only "alternative" (`MapProperties.mapStyleOptions` hiding ALL labels) produces a *different* map (no labels) and does not reproduce iOS's look → not a valid alternative. Names a real missing API, none exists → CONFORMS.
- `:112` pill shadow (S3) DEFERRED, names `Modifier.shadow` spot/ambient — CONFORMS.
- `:118` panel-icon size (S3) DEFERRED (reclassified from ACCEPTED_DIVERGENCE) — CONFORMS.
- `:15` "**Status: mostly implemented**" (S3 detented sheets) — **THIRD STATUS, non-conforming.** Not ACCEPTED_DIVERGENCE/DEFERRED.
- `:74/:76` "**DEFERRED → now IMPLEMENTED**" (S18 collapsed) — **THIRD STATUS, non-conforming.** If built + faithful it needs no divergence note; delete it and keep the manifest row IN_PROGRESS (device-gated).
- `:88` "**RESOLVED, not a divergence (removed)**" (S4 widths) — **THIRD STATUS** label (claims removed but the heading persists).
- `:131` tapCard, `:156` C4, `:167` C3 — known **RESOLVED** third-status entries; touch S6/Components (tapCard consumed by PingCard, C4/C3 are WhatsNewSheet/Discover). Flag to their owners.

### 3. Notes granting VERIFIED — both in this cluster, both invalid
- `PORT_NOTES.md:44` grants **S3 background-interaction = VERIFIED** — invalid (note cannot promote; source-only).
- `PORT_NOTES.md:94` grants **S4 route-width = VERIFIED** — invalid (note cannot promote; source-only).
Both should be downgraded; the underlying code is correct but the runtime is device-gated (see hard checks).

### 4. §0 vs §8 status disagreements — all four rows
`§0` index (`:28,29,31,43`) marks **S3, S4, S6, S18 all NOT_STARTED**, while every `§8` detail section is **IN_PROGRESS** (`:361,386,447,678`). Four disagreements. **Additionally**, pervasive intra-§8 staleness: many built sub-rows still read NOT_STARTED — S3 rows 1/2a–2d (pill items), S4 searchRow/locate/AI/UserAnnotation/clusters/tap/camera/centre, all S18 rows 1–10 — despite the code building them. Oldest cluster = heaviest stale-status drift.

## Minor / device-gated
- S4 highlight pin "static (no spring)" is asserted inline in the manifest §S4 with no matching PORT_NOTES two-status entry — verify it carries a conforming note or is accepted.
- Predictive-back sheet dismiss (`MainScreen.kt:150`), sheet corner radius 28 (iOS `presentationCornerRadius(28)`) vs Compose default — device-gated visual.
- `+1f` epsilon in `blockBackground` (guards float jitter at the exact detent) — reasonable, device-gated.

## Overall
All six hard checks verify at the source level; two carry invalid VERIFIED grants (downgrade to device-gated), one is a real divergence (FARM 0.92 vs `.large`), one confirms a stale code-comment (S18 :134). Separately: no unmanifested elements; three third-status notes (:15, :74, :88) + three known (:131/:156/:167); two VERIFIED-granting notes (:44/:94); four §0-vs-§8 disagreements plus broad intra-§8 staleness. No row promotable to VERIFIED by this read-only pass.
