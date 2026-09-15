# Audit — S12 SavedScreen / S14 LanguagePickerSheet / S15 AccountDeletedView / S17 VerifyEmailView

**This was dispatched as a BUILD session; all four screens were already built by prior sessions.** Per the "if substantially built, audit rather than rebuild" clause, this is an audit. No code written. Baseline: `./gradlew assembleDebug` → **BUILD SUCCESSFUL**. §0 index had all four NOT_STARTED (stale). All 15 strings across the four screens carry de/en/fr/nl in the catalog.

## S12 · SavedScreen — `features/saved/SavedScreen.kt` (built, 165 lines)

| Row | Verdict | Detail |
|-----|---------|--------|
| 1 Header | **DIVERGENCE** | iOS: "SAVED FARMS" eyebrow **+ `xmark`(13) `#6B7280` in 32/#F3F4F6 circle → dismiss** (`SavedScreen.swift:23-37`). Android (`:73-78`): **Kicker only, NO close button** — `SavedScreen(onOpenFarm)` has no `onClose`. Sibling `WhatsNewSheet` DOES render a close (`WhatsNewSheet.kt:125`), so this is an inconsistency, not a shell-wide drag-only pattern. Also header pad iOS t16/b8 vs Android v12. |
| 2a Guest | CONFIRM (minor) | 🤍(54) + "Keep your favourites" display(24) + subtitle geist(15) inkMuted + "Sign in" PrimaryButton → requestAuth — matches. Nits: iOS button pad h60 (Android h20); iOS fires `Haptics.tap()` on Sign in, Android does not; iOS Spacer/Spacer pushes content above center, Android `Arrangement.Center`. |
| 2b/savedRow | **DIVERGENCE** | iOS row uses `tapCard(excludeTopTrailing: 44)` (scroll-aware, carves out the X corner) + `tapCard` on the X (`:126-129`). Android (`:129,142`) uses plain `clickable` for both. `tapCard` is a reuse target (exists, scroll-aware) and iOS uses it here — should be reused. Circle 30/farmGreenMap, number geist(13,.bold) white — match. |
| emptyRow | **DIVERGENCE** | iOS: **dashed** circle `StrokeStyle(lineWidth:1.5, dash:[3])`, colour `#E5E7EB` (`:147`). Android (`:155`): **solid** `border(1.5, inkMuted@0.3)` — wrong stroke style AND wrong colour. Expressible via `drawBehind`+`dashPathEffect` (S9 DashedNote already does exactly this) → a fix, not a note. |
| divider | CONFIRM | leading 62 both. |
| 3 Carousel | CONFIRM (verify) | iOS `TripRecommendations` (C1); Android `RecommendationCarousel` (`features/discover/`). Confirm it is the C1 port. |

**SF:** `xmark`→`Close` (already in §7a).

## S14 · LanguagePickerSheet — `features/settings/SettingsScreen.kt:279` (built as AlertDialog)

| Row | Verdict | Detail |
|-----|---------|--------|
| 1 Header | **DIVERGENCE** | iOS is a **detented sheet** `[.medium,.large]` with a "LANGUAGE" eyebrow + `xmark` close (`SettingsSheet.swift:369-393`). Android is a Material **`AlertDialog`** with a plain `title("Language")` and a "Cancel" text button — a different construct, no eyebrow, no close-circle. ModalBottomSheet is available (paywall/claim/AddFarm use it), so the sheet IS expressible — not a missing-API divergence. |
| 2 Rows ×5 | **DIVERGENCE (minor)** | iOS: flag **(22)** + name geist(16,.medium) + **`checkmark`(14,.bold)** farmGreen when current, inside a `.card(4)`, divider leading 50. Android: flag **(18)** + name geist(16) + **`CheckCircle`(18)** (different glyph) + no card + no dividers. Endonyms/System-default label present. |
| 3 Restart prompt | **N/A — CORRECT** | Manifest note sanctions skipping it ("per-app locale applies live"). Android uses `Activity.recreate()` (live apply) — the correct platform behavior; iOS's `exit(0)` restart is genuinely N/A on Android. No divergence. |

**Verdict:** functionally present but structurally an AlertDialog, not the specced sheet. Biggest finding of the batch. **SF:** `checkmark`→`Check` (in §7a); the Android `CheckCircle` is a divergent glyph choice vs iOS bare `checkmark` — flag.

## S15 · AccountDeletedView — `features/settings/SettingsScreen.kt:396` (built)

| Row | Verdict | Detail |
|-----|---------|--------|
| 1 Seal | CONFIRM | `checkmark.circle.fill`(56)→`CheckCircle`(56) farmGreen. |
| 2 Title | CONFIRM | "Your account was deleted" display(24) ink centered. |
| 3 Body | CONFIRM | account_deleted_body geist(15) inkMuted centered. |
| 4 Store reminder | CONFIRM | remindStore → account_deleted_store_reminder_arg in creamCard RR14 (iOS RR16 — 14 vs 16, minor). Source mapping correctly platform-inverted: default→Google Play on Android (iOS default→App Store). |
| 5 "Done" | **DIVERGENCE (missing element)** | iOS has **"Done" → onDone** (white on farmGreenMap RR16, pinned bottom via Spacer, `:AccountDeletedView`). Android `AccountDeletedScreen(state)` takes **no onDone and renders NO button** — the terminal screen has no acknowledge action; the user can only drag the sheet away. Layout also `Arrangement.Center` vs iOS Spacer/content/Spacer/button. |

**SF:** `checkmark.circle.fill`→`CheckCircle` (add to §7a).

## S17 · VerifyEmailView — `features/auth/AuthSheet.kt:409` (built)

| Row | Verdict | Detail |
|-----|---------|--------|
| 1 📬 | CONFIRM | 📬(46). |
| 2 Title | CONFIRM | "Check your inbox" display(26). |
| 3 Body | CONFIRM | verification_link_sent(email) geist(15) inkMuted centered. |
| 4 "Log in" | CONFIRM | PrimaryButton(log_in) → onDone (resets to logIn). |

**Verdict: faithful 1:1 match.** Only nit: pad iOS h24/v40 vs Android h24/t30/b36. No divergence.

## Summary
- **S17**: clean 1:1 — no fixes.
- **S15**: missing the **Done button** (row 5) — the only functional gap; a real fix (add `onDone` + button).
- **S12**: three divergences — missing close button (row 1), `clickable` vs `tapCard` (row savedRow), solid-vs-dashed empty circle + wrong colour (emptyRow).
- **S14**: structural — AlertDialog vs the specced detented sheet + eyebrow/card; restart-skip is correctly N/A. Rebuild as ModalBottomSheet to match, or the manifest should record AlertDialog as an accepted UI substitution (it is expressible, so not a two-status note either way — a design choice to ratify).
- No new PORT_NOTES entries warranted (every gap is expressible — none is a missing-API ACCEPTED_DIVERGENCE). Nothing device-only here except pixel-level padding.
- Rows set to IN_PROGRESS (built, with divergences); none VERIFIED.
