# Farmsy Consumer Relaunch — Conversion Pass Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the consumer app ready for relaunch: looking is free, Farmsy doing the work is Plus, every lock shows a free sample, the route planner is findable, lifetime costs €59.99, and the yearly plan starts with a 7-day trial.

**Architecture:** No new subsystems. iOS (SwiftUI) is the source of truth; Android (Compose) is a 1:1 port in the same PR series. Shopping matching already runs on the device (`ShoppingPlanner.plan`), so the free sample is a presentation change, not an API change. App prices and trial length are read from the stores through RevenueCat, so they change in App Store Connect / Play Console, not in app code. The web (Stripe) holds its own prices and trial logic.

**Tech Stack:** SwiftUI (iOS 17+), Jetpack Compose, RevenueCat, Next.js + Stripe, `node --test` for web unit tests, XCTest / Swift Testing for iOS, JVM tests for Android.

**Spec:** Decisions taken in chat on 2026-09-21 (Luuk), summarised under Global Constraints. Earlier design spec: `docs/superpowers/specs/2026-09-15-five-tab-redesign-design.md`.

## Global Constraints

- Free: map, farm details, contact, directions, seasons, recipes, tips, product pages, **all basic filters and map search**, building a shopping list, the coverage number.
- Plus: which farms cover the list (matching), route planner beyond a preview, alerts, live availability ("Confirmed open today"), weekly digest (later).
- Every lock shows a sample first (a real number or blurred real rows). Never a padlock on an empty screen.
- One Plus sheet everywhere (`shell.openPlus`). Headline idea: "Farmsy finds it, plans it, tells you when it's fresh."
- Prices: yearly €29.99, lifetime **€59.99**. No struck-through anchor price anywhere (EU Omnibus: a "was" price must be real).
- Trial: **7 days**, yearly plan only, first subscription only. Never advertise a trial the person will not get: app copy reads `yearlyFreeTrialDays` from the store; web copy reads the same eligibility function the checkout uses.
- Copy in nl, en, fr, de for every new string, on both platforms.
- Each PR: iOS and Android together, from a feature branch in its **own worktree**. Never push `main`; Neil reviews and merges. `gh` is not installed: push the branch, Luuk opens the PR.
- Deviation from the advice table, deliberate: product search from Home and "Find nearby" on a product page filter the **map**, so they count as basic filters and stay free. Blurring pins on a map would read as a broken map.

## File map

| Area | iOS | Android |
|---|---|---|
| Carousel + idea card | `Farmsy/Features/Discover/SeasonRail.swift` (`CardCarousel`, `IdeaCard`) | `android/app/src/main/java/app/farmsy/android/features/discover/SeasonRail.kt` |
| Shell actions | `Farmsy/Features/Main/AppShell.swift` (`ShellActions.openTrips`, `openPlus`) | `.../features/main/AppShell.kt` |
| Map | `Farmsy/Features/Map/MapScreen.swift` | `.../features/map/MapScreen.kt` |
| Home | `Farmsy/Features/Home/HomeScreen.swift` | `.../features/home/HomeScreen.kt` |
| Shopping | `Farmsy/Features/Shopping/ShoppingScreen.swift`, `Farmsy/Core/ShoppingList.swift` (`ShoppingPlanner`) | `.../features/shopping/ShoppingScreen.kt`, `.../core/ShoppingList.kt` |
| Trips | `Farmsy/Features/Trips/TripsView.swift` | `.../features/trips/TripsScreen.kt` |
| Plus sheet | `Farmsy/Features/Map/ProUpsellSheet.swift`, `Farmsy/App/UI.swift` (`PlusLockCard`) | `.../features/map/ProUpsellSheet.kt` |
| Dead code | `LockedAccessView` in `Farmsy/Features/Detail/FarmDetailView.swift` | `.../features/detail/LockedAccessView.kt` |
| Profile | `Farmsy/Features/Profile/ProfileScreen.swift`, `Farmsy/Core/Push.swift` | `.../features/profile/ProfileScreen.kt`, `.../core/Push.kt` |

Web: `src/app/api/stripe/checkout/route.ts`, new `src/lib/trial.ts` + `src/lib/trial.test.ts`, price strings in `src/app/pricing/PricingContent.tsx`, `src/app/_components/LandingPage.tsx`, `src/app/_components/SubscriptionGateModal.tsx`, `src/app/_components/OfferCountdownBanner.tsx`, `src/app/account/subscription/page.tsx`, `src/app/admin/subscriptions/page.tsx`, `src/lib/stripeWebhook.ts`.

---

### Task 1: Tips and idea cards — a real swipeable card (PR `relaunch/r1-cards`)

**Root cause:** `CardCarousel` pins its height to 380 pt (`SeasonRail.swift`, `.frame(height: 380)`). `IdeaCard` needs about 450 pt (160 image + label + 2-line heading + 4-line body + button + 2 × 20 padding), and more at larger text sizes. The card is centred in the short frame, so the image top and the button are cut off and the rounded corners never show.

**Files:**
- Modify: `Farmsy/Features/Discover/SeasonRail.swift` (`CardCarousel`)
- Modify: `android/.../features/discover/SeasonRail.kt` (`CardCarousel` twin)

- [ ] **Step 1: Replace the fixed-height GeometryReader with content-sized paging and a peek of the next card**

```swift
struct CardCarousel<Item: Identifiable, Content: View>: View {
    let items: [Item]
    @ViewBuilder let content: (Item) -> Content
    @State private var page: Item.ID?

    var body: some View {
        VStack(spacing: Space.s3) {
            ScrollView(.horizontal, showsIndicators: false) {
                // ponytail: HStack, not LazyHStack — a lazy stack sizes to the cards
                // it has realised, so the row jumps in height while swiping. Fine up
                // to a few dozen cards; page the data if a carousel ever grows past that.
                HStack(alignment: .top, spacing: Space.s3) {
                    ForEach(items) { item in
                        content(item)
                            // 32 pt narrower than the screen: the next card peeks in,
                            // which is what tells a thumb this row swipes (Nime "For you").
                            .containerRelativeFrame(.horizontal) { w, _ in w - 32 }
                            .id(item.id)
                    }
                }
                .scrollTargetLayout()
            }
            .scrollTargetBehavior(.viewAligned)
            .scrollPosition(id: $page)
            .scrollClipDisabled()   // keep the card shadow
            if items.count > 1 {
                HStack(spacing: 6) {
                    ForEach(items) { item in
                        Circle().fill(item.id == (page ?? items.first?.id) ? Color.ink : Color.hairline)
                            .frame(width: 6, height: 6)
                    }
                }
                .frame(maxWidth: .infinity)
            }
        }
    }
}
```

In `IdeaCard.body` change the last frame so every card in a row is as tall as the tallest one, and add the Nime card shadow:

```swift
.frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
.background(Color.surface, in: RoundedRectangle(cornerRadius: Radius.card, style: .continuous))
.shadow(color: Color.ink.opacity(0.06), radius: 12, y: 4)
```

Callers that wrapped the carousel in horizontal padding must drop it (the carousel now owns its own side margin through the peek): check `tipsSection` and the season ideas in `DiscoverScreen.swift`, and any use in `HomeScreen.swift`.

- [ ] **Step 2: Android twin.** In `SeasonRail.kt` replace the fixed `Modifier.height(...)` on the pager with `HorizontalPager(contentPadding = PaddingValues(end = 32.dp), pageSpacing = 12.dp, verticalAlignment = Alignment.Top)` and let the page wrap its content height (`Modifier.wrapContentHeight()`); give `IdeaCard` `Modifier.fillMaxWidth()` and the same soft shadow (`shadow(8.dp, RoundedCornerShape(Radius.card))`).

- [ ] **Step 3: Verify on the simulator.** Build, open Discover → Ontdekken. Expected: whole card visible (image top, "Zet op mijn lijst" button, rounded corners), next card peeks on the right, dots follow the swipe. Repeat with Profile → Text size at its largest: nothing clipped. Screenshot both.

- [ ] **Step 4: Commit** — `fix(discover): idea and tip cards size to content and peek the next card`

---

### Task 2: Route planner visible again (PR `relaunch/r2-route-entry`)

Today the only way in is Shopping → "Build my route". Add two entries; both call the existing `shell.openTrips()` (which already asks for sign-in).

**Files:**
- Modify: `Farmsy/Features/Map/MapScreen.swift`, `Farmsy/Features/Home/HomeScreen.swift`, `Farmsy/Localizable.xcstrings`
- Modify: Android twins + `res/values*/strings_l10n.xml`

- [ ] **Step 1: Map — a floating round button** above the locate button, same style as the existing map controls:

```swift
Button { Haptics.tap(); shell.openTrips() } label: {
    Image(systemName: "point.topleft.down.to.point.bottomright.curvepath")
        .font(.system(size: 17, weight: .semibold))
}
.buttonStyle(MapControlButtonStyle())          // the style the locate button uses
.accessibilityLabel(String(localized: "Plan a route"))
```

(If the locate button uses an inline style rather than a named one, copy its modifiers exactly; do not invent a new look.)

- [ ] **Step 2: Home — a card under "Te koop in de buurt":**

```swift
Button { Haptics.tap(); shell.openTrips() } label: {
    HStack(spacing: Space.s4) {
        Image(systemName: "car.fill").font(.system(size: 20, weight: .semibold)).foregroundStyle(Color.farmGreen)
        VStack(alignment: .leading, spacing: 2) {
            Text("Plan a farm route").role(.heading)
            Text("Pick your stops, Farmsy orders them and draws the road.").role(.bodySm, .inkMuted)
        }
        Spacer()
        if !session.hasFullAccess { Badge(text: "PLUS", fill: .vivid, ink: .ink) }
    }
}
.buttonStyle(.plain)
.card()
```

- [ ] **Step 3: Strings** in nl/en/fr/de: "Plan a route" / "Plan een route", "Plan a farm route" / "Plan een boerderijroute", "Pick your stops, Farmsy orders them and draws the road." / "Kies je stops, Farmsy zet ze op volgorde en tekent de weg."
- [ ] **Step 4: Android twin**, same copy keys.
- [ ] **Step 5: Verify**: tap both entries on the simulator; the trip sheet opens from each. Screenshot.
- [ ] **Step 6: Commit** — `feat(route): open the route planner from the map and from Home`

---

### Task 3: Shopping list — sample first, then the lock (PR `relaunch/r3-shopping-sample`)

Free users currently read one sentence and a locked button. New behaviour: the planner runs for everyone; free users see the real coverage number and the real stops, blurred.

**Files:**
- Modify: `Farmsy/Core/ShoppingList.swift` (add `Plan.coveredCount`)
- Modify: `Farmsy/Features/Shopping/ShoppingScreen.swift`
- Test: `FarmsyTests/ShoppingPlannerTests.swift`
- Android twins: `core/ShoppingList.kt`, `features/shopping/ShoppingScreen.kt`, `app/src/test/java/.../ShoppingPlannerTest.kt`

**Interfaces — Produces:** `ShoppingPlanner.Plan.coveredCount: Int` (number of wanted items at least one pick answers).

- [ ] **Step 1: Failing test**

```swift
@Test func coveredCountIsWantedMinusMissing() {
    let plan = ShoppingPlanner.Plan(
        picks: [.init(osmId: "a", covers: ["eggs", "milk"]), .init(osmId: "b", covers: ["milk", "honey"])],
        missing: ["lamb"])
    #expect(plan.coveredCount == 3)
}
```

Run the iOS test target; expected: fails, `coveredCount` does not exist.

- [ ] **Step 2: Implement**

```swift
extension ShoppingPlanner.Plan {
    /// Items at least one stop answers. The number a free user sees.
    var coveredCount: Int { Set(picks.flatMap(\.covers)).count }
}
```

Run the test; expected: passes.

- [ ] **Step 3: Run the planner for everyone.** In `ShoppingScreen`, call `findFarms()` automatically from the existing `.task(id: matchKey)` once `matchingFarms` is known and greater than 0 (members and free alike). Members see `planView` as now.

- [ ] **Step 4: The free sample** replaces the "Farmsy Plus picks the fewest farms…" sentence:

```swift
if let plan, !plan.isEmpty {
    VStack(alignment: .leading, spacing: Space.s3) {
        Text(String(localized: "We found \(plan.coveredCount) of \(picked.count) products at \(plan.picks.count) farms within \(Int(radiusKm)) km."))
            .role(.heading)
        ZStack {
            planView                       // the real rows
                .blur(radius: 7)
                .allowsHitTesting(false)
                .accessibilityHidden(true)
            Button { Haptics.tap(); shell.openPlus() } label: {
                Label(String(localized: "See which farms"), systemImage: "lock.fill")
            }
            .buttonStyle(PillButtonStyle(.primary, size: .small))
        }
    }
}
```

The pinned action bar keeps its behaviour (free → `shell.openPlus()`), label for free users becomes "See which farms · PLUS".

- [ ] **Step 5: Paywall analytics.** Where the sample's button opens Plus, capture `.paywallViewed` with a new trigger value `shopping_sample` (add to `AnalyticsValue.Trigger` on iOS, Android, and web `src/lib/analyticsEvents.ts` so the three vocabularies stay identical; the web test diffs them).
- [ ] **Step 6: Strings** nl/en/fr/de. nl: "We vonden \(n) van je \(m) producten bij \(k) boerderijen binnen \(r) km." / "Bekijk welke boerderijen".
- [ ] **Step 7: Android twin**, including the JVM test for `coveredCount`.
- [ ] **Step 8: Verify** on the simulator without `--demo-plus`: add 4 products, see the number and blurred rows; tap → Plus sheet. With `--demo-plus`: rows sharp, no lock.
- [ ] **Step 9: Commit** — `feat(shopping): free users see real coverage and blurred stops before the Plus sheet`

---

### Task 4: Route preview for free users (same PR as Task 3 or `relaunch/r4-route-preview`)

Server rule already exists (web #36): more than 3 points on `POST /api/route` needs Plus (402). Make the app say so before the server does.

**Files:** `Farmsy/Features/Trips/TripsView.swift`, Android `features/trips/TripsScreen.kt`.

- [ ] **Step 1:** In the trip sheet, when `!session.hasFullAccess` and the trip has 2 or more stops, draw the first stop normally, blur the rest of the stop list (`.blur(radius: 7)`, hit testing off) and show one row: "Your route has \(n) stops. Farmsy Plus orders them and draws the road." with a button "Unlock the route" → `shell.openPlus()` (trigger `route_preview`).
- [ ] **Step 2:** Keep the existing 402 handling as the backstop; do not remove it.
- [ ] **Step 3:** Strings nl/en/fr/de; Android twin; verify free and `--demo-plus`; commit — `feat(route): free preview of the first stop, the rest behind Plus`

---

### Task 5: One Plus sheet, new story, dead paywalls out (PR `relaunch/r5-plus-sheet`)

**Files:**
- Modify: `Farmsy/Features/Map/ProUpsellSheet.swift`, `Farmsy/App/UI.swift` (`PlusLockCard` copy), `Farmsy/Localizable.xcstrings`
- Delete: `LockedAccessView` and the unused farm-detail paywall block in `Farmsy/Features/Detail/FarmDetailView.swift` (lines around 734–775, the `trialDays` plan buttons) — farm details are free, nothing presents it. Confirm with `grep -rn "LockedAccessView" Farmsy` that only its own definition remains before deleting.
- Delete: `android/.../features/detail/LockedAccessView.kt` and its now-unused strings in `res/values*/strings_l10n.xml`
- Modify: `android/.../features/map/ProUpsellSheet.kt`

- [ ] **Step 1: Copy.** Title "Farmsy Plus" (was "Unlock Farmsy Pro"). Subtitle: "Farmsy finds it, plans it, and tells you when it's fresh." / nl "Farmsy vindt het, plant het en vertelt je wanneer het vers is." Feature lines, in this order:
  1. "The farms that cover your shopping list" / "De boerderijen die je boodschappenlijst dekken"
  2. "A route past all of them, in the best order" / "Een route langs allemaal, in de slimste volgorde"
  3. "Alerts when your products arrive nearby" / "Een seintje als jouw producten in de buurt binnenkomen"
  4. "Confirmed open today, by people who were just there" / "Vandaag open, bevestigd door mensen die er net waren"
  Remove the line that sells filters ("filter by the kind of place"): filters are free.
- [ ] **Step 2: Rename "Pro" → "Plus"** in user-facing strings on both platforms (`grep -rn '"[^"]*Pro[^a-z"][^"]*"' Farmsy/Localizable.xcstrings` to list them). Code identifiers (`ProUpsellSheet`, `proFilterTapped`) stay: renaming them breaks the analytics vocabulary shared with web.
- [ ] **Step 3: Remove the stale anchor-price note** in `ProUpsellSheet.swift` (the comment about a struck-through €59.99): lifetime now *is* €59.99.
- [ ] **Step 4: Every Plus entry goes through `shell.openPlus`.** `MapScreen` still has its own `showPro` state for `proRow`; `proLocked` is hard-coded `false`, so delete `proLocked`, the locked branch of `proRow`, and `showPro` if nothing else uses it.
- [ ] **Step 5:** fr/de strings; Android twin; build both; screenshot the sheet in nl and in de at the narrowest device (360 dp on Android).
- [ ] **Step 6: Commit** — `feat(plus): one Plus sheet that sells finding, planning and freshness; remove dead paywalls`

---

### Task 6: Notification row in Profile (PR `relaunch/r6-notify-row`)

Only onboarding asks for notification permission, so everyone upgrading from 1.1 is never asked.

**Files:** `Farmsy/Features/Profile/ProfileScreen.swift`, `Farmsy/Core/Push.swift`; Android `features/profile/ProfileScreen.kt`, `core/Push.kt`.

- [ ] **Step 1:** In Profile, above "Product alerts", add a row "Notifications". State from `UNUserNotificationCenter.current().notificationSettings()`:
  - `.notDetermined` → trailing "Turn on" → call the same request function onboarding uses in `Push.swift`, then register.
  - `.denied` → trailing "Open Settings" → `UIApplication.openSettingsURLString`.
  - authorised → trailing "On", no action.
- [ ] **Step 2:** Android: same three states with `POST_NOTIFICATIONS` (API 33+; below that show "On").
- [ ] **Step 3:** Strings nl/en/fr/de; verify on a simulator with a fresh install that skipped the onboarding prompt; commit — `feat(profile): a row to turn notifications on for people who were never asked`

---

### Task 7: Web — lifetime €59.99 and the 7-day trial (PR `relaunch/r7-price-trial`, repo `Farmsy-web`)

**Files:**
- Create: `src/lib/trial.ts`, `src/lib/trial.test.ts`
- Modify: `src/app/api/stripe/checkout/route.ts` (the block at "The 3-day free trial is gone, and stays gone" and `const totalTrialDays = bonusTrialDays`)
- Modify price strings: `src/app/pricing/PricingContent.tsx`, `src/app/_components/LandingPage.tsx`, `src/app/_components/SubscriptionGateModal.tsx`, `src/app/account/subscription/page.tsx`, `src/app/admin/subscriptions/page.tsx` (`PRICE_LIFETIME`), `src/lib/stripeWebhook.ts` (confirmation email amount)
- Delete: `src/app/_components/OfferCountdownBanner.tsx` and its mount point — the launch offer (€49.99 instead of €59.99) ends with this change

- [ ] **Step 1: Failing test** `src/lib/trial.test.ts`

```ts
import { test } from 'node:test'
import assert from 'node:assert/strict'
import { trialDaysFor, FIRST_TRIAL_DAYS } from './trial.ts'

test('first yearly subscription gets seven days', () => {
  assert.equal(FIRST_TRIAL_DAYS, 7)
  assert.equal(trialDaysFor({ plan: 'yearly', subscription_status: 'free', subscription_plan: null, pending_referral_months: 0 }), 7)
})
test('anyone who ever subscribed gets no first trial', () => {
  for (const s of ['active', 'trialing', 'canceled', 'past_due', 'expired'])
    assert.equal(trialDaysFor({ plan: 'yearly', subscription_status: s, subscription_plan: 'yearly', pending_referral_months: 0 }), 0)
})
test('referral months still count, on top of the first trial', () => {
  assert.equal(trialDaysFor({ plan: 'yearly', subscription_status: 'free', subscription_plan: null, pending_referral_months: 2 }), 67)
  assert.equal(trialDaysFor({ plan: 'yearly', subscription_status: 'canceled', subscription_plan: 'yearly', pending_referral_months: 1 }), 30)
})
test('lifetime never has a trial', () => {
  assert.equal(trialDaysFor({ plan: 'lifetime', subscription_status: 'free', subscription_plan: null, pending_referral_months: 3 }), 0)
})
```

Run `npm run test:unit`; expected: fails, module not found.

- [ ] **Step 2: Implement** `src/lib/trial.ts`

```ts
export const FIRST_TRIAL_DAYS = 7

type TrialInput = {
  plan: 'yearly' | 'lifetime'
  subscription_status: string | null
  subscription_plan: string | null
  pending_referral_months: number | null
}

/** Days of free trial a checkout gets. The first-trial half is for people who
 *  never subscribed; referral months (30 days each) apply to anyone. Shown copy
 *  must call this too, so nobody is promised a trial the checkout will not give. */
export function trialDaysFor(p: TrialInput): number {
  if (p.plan !== 'yearly') return 0
  const neverSubscribed = (p.subscription_status ?? 'free') === 'free' && !p.subscription_plan
  return (neverSubscribed ? FIRST_TRIAL_DAYS : 0) + (p.pending_referral_months ?? 0) * 30
}
```

Run `npm run test:unit`; expected: passes.

- [ ] **Step 3: Wire it in.** In `checkout/route.ts` replace `const totalTrialDays = bonusTrialDays` with `const totalTrialDays = trialDaysFor({ plan: 'yearly', subscription_status: profile?.subscription_status ?? null, subscription_plan: profile?.subscription_plan ?? null, pending_referral_months: pendingReferralMonths })`, and rewrite the comment block: the trial is back, 7 days, first subscription only, decided by Luuk on 2026-09-21.
- [ ] **Step 4: Copy.** Yearly plan buttons on pricing, landing and the gate modal read "Try 7 days free" / "Probeer 7 dagen gratis" with the footnote "then €29.99 per year, cancel anytime" — only when `trialDaysFor(...) > 0` for the viewer (signed-out visitors count as eligible). Add the keys to all five `messages/*.json`.
- [ ] **Step 5: Prices.** Replace every `€49.99` with `€59.99` and remove every struck-through `€59.99` in the files listed above; `PRICE_LIFETIME = 59.99`; webhook email amount `'€59.99'`. `grep -rn '49[.,]99' src messages` must return nothing.
- [ ] **Step 6:** Check `src/app/api/cron/trial-nudge/route.ts` still fits a 7-day trial (it emails before the trial ends; confirm its lead time is shorter than 7 days).
- [ ] **Step 7:** `npm run test:unit && npx tsc --noEmit`; both clean. Commit — `feat(billing): lifetime €59.99, seven-day trial on a first yearly subscription`

---

### Task 8: Store and billing configuration — Luuk (no code)

The apps show whatever the stores return. Do these before the app PRs ship, or the new sheet will still say €49.99 and show no trial.

- [ ] **Stripe:** create a new one-time price of €59.99 on the lifetime product (prices cannot be edited). Put its id in Vercel as `STRIPE_LIFETIME_PRICE_ID` (production), then redeploy. Archive the €49.99 price afterwards.
- [ ] **Vercel:** remove `NEXT_PUBLIC_OFFER_ENDS_AT`.
- [ ] **App Store Connect:** lifetime in-app purchase → price €59.99. Yearly subscription → Introductory offer → Free trial, 1 week, new subscribers, all territories.
- [ ] **Play Console:** lifetime product → €59.99. Yearly base plan → add offer → free trial 7 days, eligibility "new customer acquisition".
- [ ] **RevenueCat:** no change needed if product ids stay the same; open the offering once and confirm both packages still resolve.
- [ ] **Check:** on a sandbox account that never subscribed, the Plus sheet reads "Try free for 7 days" and "€59,99"; on one that did, no trial line.

---

### Task 9: Launch checks (after Neil merges Tasks 1–7)

- [ ] Luuk: review the 73 product profiles (https://claude.ai/artifact/JST9GmQnKpZdD1kzhNmEft) and paste the verdicts; apply `review.status` in `Farmsy-web/content/products/*.json` so product pages stop saying "No profile yet".
- [ ] Run `DEVICE_PASS_REDESIGN.md` on a real iPhone and a real Android phone, plus the new points: tips card not clipped, both route entries, shopping sample (free and Plus), Plus sheet copy and prices, notification row.
- [ ] Confirm with Neil whether APNs/FCM keys are live on Vercel; send one real push.
- [ ] Version bump and store submission: Neil's go, Luuk told first.

## Not in this plan (deliberately)

- Native product-alerts screen (the web page at `farmsy.app/alerts` keeps working from the app).
- Weekly digest, provenance labels, organic verification, farm-to-product rows (C-plan, after launch).
- Ending or time-boxing the lifetime plan: revisit when the farmer portal ships.

## Order and size

| # | PR | Repo | Size |
|---|---|---|---|
| 1 | r1-cards | app | S |
| 2 | r2-route-entry | app | S |
| 3 | r3-shopping-sample (+ Task 4) | app (+1 line web analytics) | M |
| 5 | r5-plus-sheet | app | M |
| 6 | r6-notify-row | app | S |
| 7 | r7-price-trial | web | M |
| 8 | store config | Luuk | 1 hour |

Tasks 1, 2, 6 and 7 are independent and can run in parallel worktrees. Task 5 should follow Task 3 (both touch Plus entry points).
