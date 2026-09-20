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

## After the pass

- All green on a device → tell Neil; then (and only then) the version bump + store
  submission, with Luuk told first.
- Anything red → note the platform, tab, and exact gesture; app-repo fixes are Chris's,
  web/data is Aviah's, cross-cutting goes to the vault thread first.
