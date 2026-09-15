# C-row audit — C1 / C3 / C4 / C7 (read-only)

Build gate: `./gradlew assembleDebug` → **BUILD SUCCESSFUL** (16s, gradle-log verified). Tree builds → not INCOMPLETE-across-board.

Scope: C1, C3, C4, C7. C5 / SkeletonBox / TapCard not re-audited (prior VERIFIED-code, device-gated) — confirmed their source is unchanged where these rows touch them (SkeletonBox reused by C1 cover; tapCard by C3/C4).

## Claimed fixes — verified against current source (claims not credited)

### C1 · RecommendationCarousel.kt — BOTH fixes CONFIRMED
- Cover loading skeleton: `SubcomposeAsyncImage(loading = { SkeletonBox(16) }, error = { SkeletonBox(16) })` (`:239-245`). iOS `TripsView.swift:706-711` — AsyncImage success→scaledToFill, else (loading OR failure)→`SkeletonBox(16)`. Match (both loading + error).
- Category-tag kerning: `letterSpacing = 0.4.sp` (`:303`) vs iOS `.kerning(0.4)` (`TripsView.swift:731`). Match.
- Verdict: the two remaining C1 deltas from the last full audit are resolved; C1 recCard is code-complete. tapCard scroll-behavior + shimmer sweep remain **device-gated** (reused components, out of scope). Not VERIFIED.

### C3 · WhatsNewSheet.kt PingCard — BOTH fixes CONFIRMED (hard check passed)
- Inner author/farm Column `verticalArrangement = Arrangement.spacedBy(1.dp)` (`:256`) vs iOS `VStack(spacing: 1)` (`DiscoverFeedView.swift:334`). Match.
- Photo-tap is a REAL lightbox, not a renamed stub, wired end-to-end:
  - PingCard `FixedImageRow(..., onTap = onOpenImage)` (`:273`) → a tile tap fires `onOpenImage(index)`.
  - `onOpenImage = { idx -> lightbox = LightboxSource(images = ping.images, startIndex = idx, eyebrow = fromAPost, title = ping.authorName, subtitle = farm?.name, postText = ping.body) }` (`:152-162`) — correct `startIndex = idx`, all fields 1:1 with iOS `WhatsNewSheet.swift:59-64`.
  - Presented: `lightbox?.let { ImageLightbox(source = it, onClose = { lightbox = null }) }` (`:193-194`) → real S10 `ImageLightbox` Dialog (`ImageLightbox.kt:101`).
- C4 photos correctly NOT wired: `FixedImageRow(urls = images.take(3), height = 96.dp)` — no `onTap` (`:401`), matching iOS `WhatsNewSheet.swift:202` (no onTap). Confirmed.
- Verdict: the row's only remaining blocker (photo-tap) is resolved and real; C3 code-complete. The lightbox presentation visual is **device-gated**. Not VERIFIED.

### C4 · WhatsNewSheet.kt teaser — line metric CONFIRMED
- `style = geist(13.sp).copy(lineHeight = 18.9.sp, lineHeightStyle = LineHeightStyle(Alignment.Center, Trim.Both))` (`:387-393`). iOS `.geist(13).lineSpacing(2)` (`WhatsNewSheet.swift:192,195`). Geist natural@13 = 16.90 + 2 = 18.9 → correct.
- **Analytic-only; device-checkable** (renders in featured cards with wrapped teasers) but not yet device-verified → not VERIFIED.

### C7 · Components.kt ExpandableText — line metric CONFIRMED; clamp still DEFERRED
- `style: TextStyle = geist(15.sp).copy(lineHeight = 22.5.sp, lineHeightStyle = LineHeightStyle(Center, Trim.Both))` (`:295-301`). iOS `.geist(15).lineSpacing(3)` (`FarmDetailView.swift:983-984`). Natural@15 = 19.50 + 3 = 22.5 → correct.
- Clamp: still `res.hasVisualOverflow` + `res.getLineEnd(lineLimit-1, visibleEnd=true)` then trims (`:326-335`), NOT iOS's char-level binary search (`FarmDetailView.swift` `truncatedToFit`). This is the documented legitimate DEFERRED.
- **Renders nowhere in the running app** (C4 reverted off it; S7 unbuilt) → line metric is analytic-only AND un-renderable now. Not VERIFIED.

## C7 fit for S7? — YES, with two inherited caveats
S7 can consume C7. Line metric now matches iOS (22.5 Trim.Both); labels ("  … View more" / "  View less"), farmGreen-bold, ink default, and tapCard toggle all match. S7 would inherit:
1. **The DEFERRED clamp** (overflow-based, not iOS's char-level binary search): the truncated prefix can differ from iOS by a character or two at the boundary, and in a full-last-line edge case the "… View more" could be ellipsized rather than guaranteed-to-fit. Functional, not broken — the documented DEFERRED.
2. **No expand/collapse animation** — Android toggles `expanded` instantly; iOS wraps it in `withAnimation(.easeOut(0.2))`. Minor divergence, not noted anywhere. Neither is "broken."

## Separate reports
1. **Unmanifested Compose elements in C1/C3/C4/C7:** none. The C3 lightbox wiring (`lightbox` state, `onOpenImage`, `ImageLightbox` presentation) maps to existing S6 rows (row 4 PingCard `onOpenImage → LightboxSource`, and the S6 "Lightbox" row) — not C-rows, but they exist. No new element without a row.
2. **PORT_NOTES two-status (entries touching C-rows):**
   - C7 clamp/truncation (`:139`) — DEFERRED, names `TextMeasurer`/`Paragraph` binary search, row OPEN → **CONFORMS**.
   - tapCard haptic (`:129`) — **"RESOLVED" = third status, non-conforming.**
   - C4 teaser static clamp (`:154`) — **"RESOLVED" = third status.**
   - C3 timeAgo (`:165`) — **"RESOLVED" = third status.**
   - (Also non-C third-statuses: `:17` "mostly implemented", `:76` "DEFERRED → now IMPLEMENTED", `:88` "RESOLVED".)
   - The prior `lineSpacing(N)` DEFERRED note is **deleted** (confirmed absent) — correct, since the metric is now built.
3. **Notes granting a row VERIFIED:** `PORT_NOTES.md:44` ("manifest S3 background-interaction row is VERIFIED") and `:94` ("manifest S4 route-width row is VERIFIED"). Both S-rows; a note cannot promote a row.
4. **§0 vs §8 disagreement:** **C5** — §0 `:51` NOT_STARTED vs §8 `:777` IN_PROGRESS — still unreconciled. C1/C3/C4/C7 agree (both IN_PROGRESS).
