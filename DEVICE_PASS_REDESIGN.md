# DEVICE_PASS_REDESIGN — on-device pass for the redesign (#44–#52)

The whole redesign is on `main` and compiles green on both platforms, but nothing has
been run on a real device. This is the launch gate. Run on **both iOS and Android** —
it's a 1:1 parity build. Each item: **do → expect → watch for**. Ordered highest-risk
first.

Merged is not shipped: passing this does not bump a version or submit anything. The
version bump + store submission waits on Neil's go **and** telling Luuk first (his
explicit ask). Luuk is on holiday from Wednesday and wants launch before then, so this
pass is what decides whether that happens.

---

## ⚠️ Before you start: you need THREE different test accounts (they conflict)

Access is gated by role and subscription state, so **no single account can test every gate** —
and an `admin` / `farmer` / `founding_member` account **never sees a paywall at all** (access is
granted by role before any subscription check — this is correct, not a bug). Neil's own account is
almost certainly `admin`, so it will pass straight through every Plus gate and show no trial line.

To actually exercise the paid surfaces you need:

1. **A fresh account that has NEVER subscribed** → to see the **"7 days free" trial** line on the
   Plus sheet (checks 14, 18). An account that used the old 3-day trial, or ever subscribed, is
   ineligible and correctly shows price-only.
2. **A paid (Plus) account** → to reach the **Profile → Product alerts** row, which opens
   `farmsy.app/alerts` (a free account correctly gets the Plus sheet and never follows the link).
3. **A plain expired/canceled account** — a normal user (NOT admin/farmer/founding) whose sub has
   lapsed → to see the **re-subscribe paywall**. On expiry the status becomes `canceled` with a null
   end date, which correctly removes access.

If a gate "doesn't work," check the account first: an admin/founding account showing no paywall, or
a subscribed account showing no trial, is the gate working — not a bug.

---

## 🔴 P0 — most likely to look broken / block launch

### 1. Product sheet with no profiles (#52 "empty by design")
Open any product's sheet (tap a product from Shopping or Discover).
- **Expect:** a clean **"No profile for this product yet"** state; the recipe-of-the-week
  area simply absent, not a crash or blank.
- **Watch for:** empty section headers (How to choose / Keeping / Preserving with nothing
  under them), a broken "Recipe of the week" card, or a spinner that never resolves.
  `/api/products` returns **empty** right now (all 73 profiles are draft, gated to
  approved-only for safety) — it must read as *intentional*, not half-loaded.

### 2. Community leaderboard is anonymous (GDPR)
Community → Leaderboard.
- **Expect:** entries show **"Farmsy-fan ####"** handles, never real names; your own row
  marked **YOU**.
- **Watch for:** any real first name / last initial showing. The opt-in name toggle isn't
  shipped, so *nobody* should show a real name.

### 3. Report portal round-trip (shelf products actually save)
Community → Reports → "What did you see today?" → pick a farm, mark status, tick a few
shelf items → submit.
- **Expect:** "report sent" confirmation; reopen the farm/feed and the report shows with
  its products.
- **Watch for:** products coming back empty after submit (means the shopping-id
  vocabulary is off — Aviah's explicit flag).

### 4. Five-tab bar (#51 — no system slab)
- **Expect:** Home · Shopping · Map · Discover · Community, original glyphs, **no grey
  system tab-bar slab** behind it; all five switch.
- **Watch for [Android, both nav modes]:** run once in gesture nav and once in 3-button
  (Settings → System → Navigation) — the tab bar sitting under the nav bar or the wrong
  safe-area inset. This is the classic parity break in `DEVICE_PASS.md`.

---

## 🟠 P1 — per-tab function

### 5. Shopping (#47 + #46 images)
- Search a product; **type a custom item** → expect an **"Add "xyz""** row that adds it.
- **Category groups** render (dairy, eggs, vegetables…) — live from `/api/shopping/items`.
- Product **photos** on rows; items with no photo fall back to the emoji, not a blank tile.
- "Find farms for my list" / "Build my route · N stops" → coverage shows **"N of M
  products"**; open a stop's **"Change farm"** sheet.

### 6. Discover (#48)
- **Season rail:** tap through months; **NOW** badge on the current month; "in season ·
  ideas" counts.
- **"What to make with it"** ideas; **"Put it on my list"** adds to Shopping.
- **Grandmother's tips** load (from `/api/tips` — 8 live).
- Farm search: by name/place; "No farm matches → try fewer words" empty state.

### 7. Map (#45)
- **Expect:** every farm its **own pin** (declustered); the **Plus chip** present.
- **Watch for:** pins collapsing back into clusters, or the Plus chip mis-gated.

### 8. Profile (#50)
- **Change photo** → pick an image → uploads and shows (writes `avatar_url`).
- **Your contributions** with badges (**Earned / Locked** states).
- **Accessibility:** Haptics on/off, Reduce motion, Text size — toggle and confirm they
  take effect.

---

## 🟡 P2 — cross-cutting

### 9. Sheet drag / background interaction
The `DEVICE_PASS.md` "spine" check — open a pill sheet, confirm the map behind it stops
responding to a second finger once dragged past the partial detent.

### 10. Push (#44)
On a **fresh install**, onboarding asks notification permission. *Nothing actually sends
yet* (no FCM/APNs keys set), so just confirm the prompt appears and registration doesn't
crash — don't expect a delivered push. Note: upgraders from 1.1 never get asked (only
onboarding requests permission), so first push numbers will understate reach until a
"turn on alerts" row ships in Profile.

### 11. Product photos across Home / Discover / shopping list
Load, or fall back to emoji cleanly — no blank tiles.

---

## 🟢 Relaunch batch (#53–#59) — run these once that batch is in the build

The conversion pass: looking is free, Farmsy-does-the-work is Plus, every lock shows a real
sample. These are on top of the checks above.

### 12. Idea / tip cards no longer clip (#53)
Discover → season ideas, and any product sheet's recipe cards.
- **Expect:** the whole card shows (image top, button, rounded corners); the **next card
  peeks** on the right; page dots follow the swipe.
- **Watch for:** a card cut off top or bottom, or at **largest Text size** (Profile →
  Accessibility) — the old bug was a fixed 380pt frame clipping it.

### 13. Route planner is reachable (#54)
- **Home:** a "**Plan a farm route**" card (PLUS badge if you're not a member) → opens the
  planner.
- **Map:** a floating route button (above the locate button) → opens the planner.
- **Expect:** both open Trips (asks sign-in if needed). **Watch for:** either entry dead, or
  the PLUS badge missing/mis-shown.

### 14. Shopping free sample, then Plus (#55) — the core conversion
Shopping with a few items, **not** signed into Plus.
- **Expect:** the real **coverage sentence** ("N of M products") shows, and the stop rows
  appear **blurred**; tapping the blurred block *or* the pinned bar opens the Plus sheet.
- **Watch for:** a **padlock on an empty screen** (the thing the plan explicitly forbids), the
  sample not blurred, or the tap not opening Plus. As a member: full unblurred rows, no sample.

### 15. Route preview free, full route Plus (#56)
- **Expect:** a non-member sees a **preview** of the route; going beyond the preview opens the
  Plus sheet. Member sees the full planned route.

### 16. One Plus sheet everywhere (#56/#57)
Trigger the paywall from several places (shopping sample, route, a farm, membership row).
- **Expect:** the **same** Plus sheet each time, same headline ("finds it, plans it, tells you
  when it's fresh"). **Watch for:** the old `LockedAccessView` paywall still appearing anywhere
  (it was deleted) — or a farm detail showing a paywall at all (details are free now).

### 17. Notifications row in Profile (#58) — closes the upgrader gap
Profile → **Notifications** row (this is new; supersedes note 10 above).
- **Not yet asked:** shows "**Turn on**" → tap → system permission prompt → row flips to "**On**".
- **Denied:** shows "**Open Settings**" → tap opens iOS/Android Settings; grant there and return →
  row updates to "On" (it refreshes on foreground).
- **Watch for:** the row stuck on "Turn on" after granting, or not reacting to a Settings change.

### 18. Price + trial copy (store config is set; price/trial come from the device, not RevenueCat)
Open the Plus sheet. Prices are live: **€59.99 lifetime + 7-day yearly trial** on both stores.
- **Expect:** lifetime **€59,99**; on an account that has **never subscribed**, a **"Try 7 days
  free"** line on the yearly plan; on one that has, **no** trial line.
- **Watch for:** old numbers (€49.99) or a struck-through "was" price — would mean a stale device
  cache, not a config error. The app reads price via `storeProduct.localizedPriceString` and the
  trial via `storeProduct.introductoryDiscount` (StoreKit / Play Billing) directly — **not** through
  RevenueCat, which holds no price or trial value. So there's nothing to "wait to sync."

> ⚠️ **The trial leg needs a FRESH account that has never subscribed.** The 7-day trial replaced a
> 3-day one, and Apple's intro-offer eligibility is per-subscription-group, per-customer: an Apple
> ID that already used the old 3-day trial is **ineligible** for the 7-day, so StoreKit reports **no
> intro offer** and the sheet correctly shows **price only, no trial line**. On a device this looks
> exactly like "the 7-day trial is broken" — it's the guard working. **Use a brand-new sandbox Apple
> ID** (Neil's own account won't do — it's been testing since July). Same on Play: "New customer
> acquisition / Never had any subscription" excludes any test account that has subscribed.

---

## After the pass

- All green on a device → tell Neil; then (and only then) the version bump + store
  submission, with Luuk told first.
- Anything red → note the platform, tab, and exact gesture; app-repo fixes are Chris's,
  web/data is Aviah's, cross-cutting goes to the vault thread first.
