# Audit — farm detail stack (S7 / S8 / S9 / C8; C7+S10 integration only)

Read-only. Build: `./gradlew clean assembleDebug` → **BUILD SUCCESSFUL** (23s, real gradle log; libs stripped warning only). Tree builds → not INCOMPLETE.

## Hard checks

### 1. Claim routing — WEB confirmed, encoding DISPUTED
- S7 paywall `onClaim` → `showPaywall=false; openClaim(context,pin)` (`FarmDetailScreen7.kt:386`). ✓ web.
- S9 claimBlock `onClaim()` (`FarmMemberSections.kt:216`) is threaded from `DetailSections(onClaim = { openClaim(context, pin) })` (`FarmDetailScreen7.kt:351`). ✓ web.
- No native ClaimSheet path survives: `ClaimSheet` has ZERO live callers (only its own def, `features/claim/ClaimSheet.kt:53`). ✓
- **URL encoding does NOT match iOS exactly.** iOS `claimURL` uses `addingPercentEncoding(.urlPathAllowed)` → **preserves `/`** on the catch-all route (`FarmDetailView.swift:499`, e.g. `/claim/way/12345`). Android `openClaim` uses `Uri.encode(pin.osmId)` → **encodes `/`→%2F** (`FarmDetailScreen7.kt:824`, e.g. `/claim/way%2F12345`). ~35% of osm_ids contain a slash (per `FarmDetailApi.kt:28`). iOS's own comment claims the catch-all "works raw or percent-encoded", so functional equivalence is plausible but **web-gated** (depends on the Next.js catch-all decoding `%2F`). The code-level encoding divergence is confirmed. To match iOS exactly: `Uri.encode(pin.osmId, "/")`.
- Note: the *fetch* endpoint (`FarmDetailApi.encodeOsmId`) correctly encodes slashes on BOTH platforms (single dynamic segment) — that one matches. Only the catch-all *claim* differs.

### 2. Sign-in gate — faithful adaptation
iOS `gateLocked`: signed-in→`showPaywall`, else→`showSignIn` presented as a **local** `.sheet(AuthView)` "from within this view" (`FarmDetailView.swift:490-495,66`). Android (`FarmDetailScreen7.kt:198-200`): signed-in→`showPaywall`, else→global `requestAuth()` (`LocalRequestAuth` → RootNav's single `AuthSheet`). Behaviorally equivalent (sign-in appears over the farm). Platform **can** express a local sheet, so ACCEPTED_DIVERGENCE would be wrong; this is a deliberate architecture choice, already annotated in the manifest §S7 P3 row. **Verdict: faithful, no PORT_NOTES two-status note warranted.** Also verified: ratingRow + save-heart when locked go straight to `showPaywall` (not the sign-in gate) on BOTH platforms — matches iOS exactly.

### 3. Paywall presentation — acceptable match
iOS `.sheet(isPresented:$showPaywall){ LockedAccessView }`, **no `presentationDetents`** → default large/page sheet (`FarmDetailView.swift:67`). Android `ModalBottomSheet(skipPartiallyExpanded=true, cream)` (`FarmDetailScreen7.kt:379-382`) → full-height bottom sheet. Both full-height modal, swipe-to-dismiss; platform-idiomatic. **Verdict: faithful (exact visual device-gated).**

### 4. Optimistic like + save — ordering matches iOS
- Like (`FarmMemberSections.kt:176-178`): `wasLiked = contains` → update local set → `launch{ toggleLike(currentlyLiked=wasLiked) }`. iOS `like()` identical, and **neither rolls back** on failure. ✓ ordering + no-rollback both match.
- Save: S7 delegates to `FavoritesStore.toggle` (`:224`). Store does update-local-first → network → **roll back on failure** (`FavoritesStore.kt:38-43`), matching iOS `toggle` (`FavoritesStore.swift:34-45`) exactly, rollback included. ✓
- `displayCount = liked && likeCount==0 ? 1 : likeCount` (`:351`) matches iOS `:339`. ✓

### 5. C8 transcode — matches iOS exactly
Android (`Composers.kt:169-185`): `PickMultipleVisualMedia(3)` → `uris.take(3)` → `decodeStream` → `compress(JPEG, 80)` → `if (jpeg.size <= 4_500_000) out.add` else **drop**. iOS (`FarmMemberSections.swift:504-513`): `items.prefix(3)` → `jpegData(0.8)` → `count <= 4_500_000` in an `if let` chain (fails ⇒ **dropped**). Decode→q0.8/80→keep-else-drop; no downscale, no retry, no reject-all. ✓ CONFIRMED.

### 6. reviews_count plural (values/-only) — faithful
iOS builds `"· \(count) \(count == 1 ? "review" : "reviews")"` — inline English literals, **not in Localizable.xcstrings** (an iOS i18n gap). Android `plurals reviews_count` defined only in `values/` (`FarmMemberSections.kt:135`), so nl/fr/de fall back to English → same rendered output as iOS. ✓ CONFIRMED faithful (not a defect to fix — matches source).

## Separate checks

### 1. Unmanifested Compose elements — NONE
Every composable maps to a manifest row/spec: private helpers (SubmitPill, ComposerField, DashedNote, SectionHeader, StatTile/Divider, PlanButton, HeaderCircle, CardSkeleton, TripButton, PhotoStrip/Tile, LockedBody/Block/Bars, FarmDetailFooter/Button, DetailSections, InfoRow, FarmPostRow, ReviewRow) all implement specced rows. No standalone user-visible element without a row.

### 2. PORT_NOTES touching this stack — re-judged
- C7 clamp (`:141`) DEFERRED, names TextMeasurer/Paragraph — CONFORMS (C7's own row; integration only).
- S10 veil blur (`:178`) DEFERRED, names FLAG_BLUR_BEHIND — CONFORMS (S10's row).
- S10 drop-shadow (`:195`) DEFERRED, names custom shadow layer — CONFORMS (S10's row).
- Web claim (`:232`) DEFERRED, names `androidx.browser.customtabs.CustomTabsIntent` — CONFORMS.
- **lockedBlock faux-bars blur <31 (`:214`) ACCEPTED_DIVERGENCE — QUESTIONABLE / likely mis-classified.** It claims "API 26–30 has no equivalent" — true for `Modifier.blur`/RenderEffect specifically, but **RenderScript `ScriptIntrinsicBlur` (API 17–30) is an alternative API** that blurs a bitmap, and the faux bars are static grey rects (trivially rasterizable), or could be a pre-blurred drawable. Per the two-status rule "ACCEPTED_DIVERGENCE must not be used where an alternative API exists," this should be **DEFERRED (naming ScriptIntrinsicBlur), or the note must explicitly argue why RenderScript is non-viable** (deprecated/perf/rasterization). This is the exact failure mode the audit brief called out. FLAG.
- Third-status "RESOLVED" notes (tapCard `:131`, C4 `:156`, C3 `:167`) violate the two-status rule but belong to Components/S6 rows, not this stack (tapCard is only *consumed* here). Out-of-scope; flag to their owners.

### 3. Notes granting VERIFIED — none in-stack
Two notes grant VERIFIED (`:44` S3 background-interaction, `:94` S4 route-width) — both S3/S4, **outside this stack**. A note cannot promote a row regardless. No S7/S8/S9/C8 note grants VERIFIED.

### 4. SF→Material substitutions — recommend consolidation
~20 across the stack, scattered as per-row/per-comment annotations: S7 checkmark.seal.fill→Verified, checkmark/plus→Check/Add, arrow.right→ArrowForward, location.fill→NearMe; S8 checkmark.seal→Verified; S9 shield→Shield, flag→Flag, heart(.fill)→Favorite/FavoriteBorder, star(.fill)→Star/StarBorder, clock→Schedule, mappin.and.ellipse→Place, phone→Phone, globe→Public, envelope→Email, link→Link, leaf→Eco, basket→ShoppingBasket, arrow.up.right→NorthEast; C8 photo.badge.plus→AddPhotoAlternate, paperplane.fill→Send, xmark.circle.fill→Close-in-circle.
**Recommendation:** SF Symbols have no Android equivalent — this is ONE systematic mapping, not N divergences. Consolidate into a single reference mapping table (Material Icons are an equivalent API, so it's a design-mapping table, not a two-status note). Keep the handful with real shape-divergence risk individually **device-gated**: `checkmark.seal→Verified`, `location.fill→NearMe` (nav-arrow vs pin — meaning shift), `photo.badge.plus→AddPhotoAlternate`.

## Minor / device-gated nits
- InfoRow icon column: iOS `.frame(width: 26)` (`FarmDetailView.swift:669`) vs Android `size(16)` + `spacedBy(12)` (`FarmMemberSections.kt:319`) — value text hangs ~10dp left of iOS. Device-gated visual, not a row defect.
- `FarmMemberSections.kt:319` has a dead `.padding(top = 0.dp)` (no-op modifier) — cosmetic, harmless.
- Sign-in / paywall / lightbox pop-in animations, sheet drag behavior, blur-on-31+ — all **device-gated**.

## Overall
Every deliberate architectural choice from the four build sessions verifies against iOS EXCEPT the claim URL slash-encoding (§1, DISPUTED) and the lockedBlock ACCEPTED_DIVERGENCE classification (§2, re-judge). All six hard checks otherwise pass. Rows stay IN_PROGRESS (device-gated items named); none promotable to VERIFIED by this read-only pass.
