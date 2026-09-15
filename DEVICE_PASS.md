# DEVICE_PASS — single batched on-device pass (physical Android)

Read-only checklist. You hold the phone; each check names the manifest row it
settles, the exact gesture, what iOS does (file:line), a PASS, the **most likely
FAILURE to hunt**, and whether a fail demotes a currently-VERIFIED row.

**Run the whole pass twice where marked [both nav]:** once in gesture navigation,
once in 3-button navigation (Settings → System → Navigation). Inset behavior
diverges there.

Ordered so the checks that invalidate the most downstream work come first.

---

## WILL BREAK — highest stakes, hunt these first

### 1. S3 · mid-drag background blocking [both nav] — THE SPINE
- **Row:** S3 background-interaction (currently VERIFIED — see demotion).
- **Gesture:** Open any pill sheet (Discover). It rests at the partial detent
  (~55%). Put a second finger on the visible **map strip above the sheet** and
  try to pan the map — confirm it pans. Now **slowly drag the sheet upward and,
  without releasing, hold it just above the partial-detent line**, and with the
  other finger try to pan the map.
- **iOS:** `.presentationBackgroundInteraction(.enabled(upThrough: .fraction(0.55)))`
  — map interactive up to the partial detent, blocked above it
  (`MainView.swift:50,63,75`; Trips 0.5 at `:82`).
- **PASS:** the instant the sheet crosses the partial line *while you are still
  dragging*, the map stops responding to the other finger. Below the line it pans.
- **FAILURE to hunt:** you can still pan the map while holding the sheet above the
  detent, and blocking only kicks in **after you release and it settles** at
  expanded → the blocker is gated on the settled anchor, not the live drag. Also
  hunt **wrong-offset**: blocking starts noticeably below the detent line, or not
  until far above it. (Code reads `sheetState.requireOffset()` in a
  `derivedStateOf`, `MainScreen.kt:126-129`; a settle-only failure means
  `requireOffset()` isn't updating the block mid-drag as claimed.)
- **[both nav]:** in 3-button the nav bar is taller and the pill sits higher —
  confirm the block threshold still lines up with the visible partial-detent edge,
  not shifted by the nav inset.
- **Demotes:** YES — S3 background-interaction VERIFIED was granted inside a PORT
  NOTE by the author session on source geometry alone. A settle/wrong-offset fail
  demotes it, and every sheet screen (S6/S7/S12/S13/S18) sits on this spine.

### 2. TapCard · scroll-release vs deliberate tap + haptic
- **Row:** TapCard (VERIFIED code, device-gated).
- **Gesture:** In the What's New feed, **fling/scroll the list and let your finger
  lift while it is over a post card** (not the photos — the card body). Then,
  separately, **tap a card while your finger stays still.**
- **iOS:** `TapActivate` drops the tap if the finger moved past `threshold = 12`
  and fires `Haptics.tap()` (`.light`) only on a clean tap (`TapCard.swift:17,36,45`).
- **PASS:** the scroll-and-lift does **nothing** (no open, no haptic); the still
  tap opens the farm **with a light haptic tick**.
- **FAILURE to hunt:** lifting after a scroll **opens the farm** (tap not
  cancelled) — the classic scroll-through misfire. Also feel the **threshold**: a
  *too-large* threshold feels sloppy — you can drag your finger a visible distance
  and it still counts as a tap; a *correct* threshold cancels on any perceptible
  slide, only a near-stationary press fires; a *too-small* threshold feels twitchy
  — legit taps get eaten by micro-movement. And the **haptic**: no tick at all, or
  a heavy buzz instead of a light tick.
- **Approximations (expected, not failures):** Android uses the system touch-slop,
  not iOS's exact 12pt; and `HapticFeedbackType.TextHandleMove`, not a `.light`
  impact. A slightly different slop distance or a marginally different tick is the
  documented divergence — only a *misfire* or *no haptic* is a fail.
- **Demotes:** YES — TapCard VERIFIED (code). A misfire demotes it and casts doubt
  on every C1/C3/C4/C5 tap that rides it.

### 3. C5 · FixedImageRow per-tile tap during scroll
- **Row:** C5 (VERIFIED code, device-gated).
- **Gesture:** In the feed, **scroll with your finger landing/lifting on a photo
  tile inside a PingCard.** Then deliberately **tap a single photo tile, finger
  still.**
- **iOS:** photo tiles use `OptionalTap → tapCard` (`DiscoverFeedView.swift:434`),
  so a scroll that ends on a tile does not fire.
- **PASS:** scroll-lift on a tile just scrolls; a deliberate tile tap fires (opens
  the farm — the S10 lightbox is stubbed, so it currently opens the farm).
- **FAILURE to hunt:** lifting on a tile mid/after scroll **opens** — i.e. the tile
  behaves like a plain `clickable`, firing on any touch-up.
- **Demotes:** YES — C5 VERIFIED (code).

---

## NEEDS TESTING — medium stakes

### 4. S18 · collapsed trip tuck (settles the doc contradiction)
- **Row:** S18 collapsed (OPEN — no demotion; PASS supports promotion).
- **Gesture:** Open Trips. It rests at the small detent (~50%). Note the list.
  Drag the sheet **up** to expand, then drag it back **down** to the small detent.
- **iOS:** `collapsed = (detent == .fraction(0.5))`; at collapsed the overview list
  is tucked and a `chevron.up` cue shows (`TripsView.swift:91,95,164`).
- **PASS:** at the small detent the **Trip-overview list is hidden** and a
  **KeyboardArrowUp** hint shows; expanding brings the list back.
- **FAILURE to hunt:** the list stays visible at the small detent (tuck never
  happens), or no up-hint, or the tuck only toggles after a full expand/collapse
  round-trip lags behind the settle.
- **Settles the note contradiction:** the code reads `sheetState.currentValue ==
  PartiallyExpanded` (`MainScreen.kt:144`) — a **discrete settled detent value**.
  So the PORT_NOTES header is right ("the value exists and is used") and the
  paragraph's "Compose has no detent value to read" is stale/wrong. This is a
  **settle-based** toggle (matches iOS's settled `detent ==`), so do NOT expect it
  to tuck mid-drag — it flips when the sheet *settles* at the small detent.

### 5. C · S6 WhatsNewSheet in the hand [both nav]
- **Row:** S6 (IN_PROGRESS — no demotion).
- **Gesture:** Tap Discover → sheet rises. Scroll through posts → featured cards →
  carousel. Tap a farm card. Then drag the sheet fully down.
- **iOS:** presented `[.fraction(0.55), .fraction(0.92)]` over the live map
  (`MainView.swift:62`); opening a farm flies the map + dismisses.
- **PASS:** sheet settles ~55%, expands to ~92% with a **strip of live map still
  visible** at the top; scroll is smooth; tapping a farm **flies the map and closes
  the sheet**; dragging fully down returns to the all-farms map + pill.
- **FAILURE to hunt:** expanded covers the whole screen (no map strip → 0.92 not
  applied); the **last feed row hides under the gesture/nav bar** (missing
  `navigationBarsPadding`); or opening a farm leaves the sheet up.
- **[both nav]:** check the last-row-clears-nav-bar in both; the map strip height
  at 0.92 differs by the nav inset.

### 6. F · SkeletonBox shimmer sweep
- **Row:** SkeletonBox (VERIFIED code, device-gated).
- **Gesture:** Force a loading state — cold-launch into Discover on a **throttled/
  slow network**, or scroll so images/galleries are still loading; watch the grey
  placeholder blocks (post skeletons, featured skeletons, carousel loading, photo
  tiles mid-load).
- **iOS:** a light band sweeps left→right, `linear(1.4s).repeatForever` over base
  `#ECEBE8` (`Shimmer.swift:25-26`).
- **PASS:** a soft light band sweeps across each grey block, repeating ~1.4s,
  smooth, left-to-right.
- **FAILURE to hunt:** the block is **static grey with no sweep** (animation is a
  silent no-op / not running), or it flickers/jumps, or sweeps the wrong way.
- **Demotes:** YES — SkeletonBox VERIFIED (code); a static block demotes it and
  every skeleton in the app.

### 7. G · C4 teaser line metric — does Trim.Both actually trim?
- **Row:** C4 (IN_PROGRESS — confirms the just-built metric).
- **Gesture:** In featured cards, find a teaser that **wraps to 2 and 3 lines**
  (a 1-line teaser looks identical trimmed or not — you MUST use wrapped text).
- **iOS:** `.lineSpacing(2)` — +2pt between lines, nothing above line 1
  (`WhatsNewSheet.swift:195`).
- **PASS:** even spacing between the wrapped lines, and the **first line sits
  snug to the top of the text block** — the gap above line 1 is just the glyph
  ascent, clearly smaller than the inter-line gaps carry.
- **FAILURE to hunt (silent no-op trim):** there is **extra leading above the
  first line and below the last** (the whole block sits lower / taller, first-line
  top gap matches the inter-line gap). Numerically a 3-line teaser would be ~56.7dp
  (untrimmed N·L) instead of ~54.7dp — you can't eyeball 2dp, so judge by the
  **top gap**: working trim = tight top; no-op = padded top.
- **BLOCKED — C7 not testable:** C7 `ExpandableText` (22.5sp) renders nowhere in
  the current app (C4 reverted off it; S7 unbuilt). Its Trim.Both cannot be
  confirmed this pass — defer to when S7 lands.

### 8. C1 · recommendation carousel (freshly rebuilt)
- **Row:** C1 recCard + pager (IN_PROGRESS).
- **Gesture:** Scroll to the carousel in What's New. Swipe between pages. Tap a
  card body; tap a card's save heart; find a card whose photo is still loading.
- **iOS:** cover = gallery-first-else-cover with a loading `SkeletonBox`
  (`TripsView.swift:706-711`); heart size 13, silent no-op when signed out (`:745`);
  tapCard(excludeTopTrailing 44) (`:770`); hairline stroke (`:742`).
- **PASS:** two cards/page, `< • • •  >` pager steps pages, active dot is the deep
  green; a still-loading cover shows a **shimmer** (not flat grey); tapping the
  card opens the farm but tapping the heart only toggles save; a 1dp hairline rings
  each card.
- **FAILURE to hunt:** cover flashes flat grey then pops (no skeleton); tapping the
  heart *also* opens the farm (corner exclusion wrong); no hairline; signed-out
  heart tap throws up an auth sheet (should be silent).

### 9. S4 · trip route on the shared map
- **Row:** S4 route (route-width VERIFIED in a note; aboveLabels ACCEPTED_DIVERGENCE).
- **Gesture:** Build/open a trip with ≥2 stops so a route draws on the map.
- **iOS:** white casing width 8 + blue `#2563EB` width 5 (on-roads) / width 4
  dashed `[2,4]` (off-roads), both `aboveLabels` (`MapScreen.swift:398-407`).
- **PASS:** a white-cased blue line follows the stops; solid when snapped to roads,
  dashed when straight-line.
- **FAILURE to hunt:** line width ratio looks wrong (casing not visibly wider than
  the line); dashed/solid inverted. **Expected divergence (not a fail):** base-map
  road/place **labels draw over the route** — Google Maps has no overlay-vs-label
  z-order API (ACCEPTED_DIVERGENCE); note it but do not demote for it.
- **Demotes:** the route-**width** VERIFIED (granted in `PORT_NOTES.md`) — demote
  only if the 8:5 width ratio is visibly wrong, not for the labels.

---

## CONFIRM-ONLY — low risk, quick look

### 10. S1 · splash animation
- **Row:** S1 (DONE-claimed by a port session, never audited).
- **Gesture:** Cold-launch the app and watch the splash.
- **iOS:** logo blur-in, letter rotation settling, a tap haptic on hand-off
  (`SplashView.swift`).
- **PASS:** barn/logo animates in (blur→sharp), letters settle, smooth fade into
  the app; one light haptic.
- **FAILURE to hunt:** static logo (no animation), abrupt cut, or a black flash
  before Compose draws.
- **Demotes:** no formal VERIFIED to demote (DONE is a port claim); a fail means S1
  is not actually done.

---

## Notes for the pass
- Anything that only reproduces on a real sheet drag (1, 4, 5) will not show in a
  preview or emulator reliably — this is why it is a physical-device pass.
- If check 1 fails, stop and report before trusting 5/8 — they ride the same sheet.
- Take the [both nav] checks (1, 5) in both navigation modes back-to-back so the
  inset difference is obvious.
