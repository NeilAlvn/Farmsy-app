# Conversion fixes — design

Date: 2026-09-30. Owner decisions by Luuk in chat, same day. Source of the numbers: PostHog project 222496 (last 30 days) and RevenueCat project Farmsy (`9adc7f29`), read on 2026-09-30.

## Why

4,718 installs in 28 days, €0 revenue, one active trial. The paywall is reached by 1.4% of users. Of those who tap a plan, most cancel at the store sheet. And the analytics that should show this cannot: every anonymous launch gets a new PostHog identity, so retention reads 0.9% when RevenueCat says about 15%, and Funnel A on dashboard 938846 cannot join `app_opened` to the next step.

Four changes, one PR each, iOS and Android together in every PR (repo norm). Branches stack in one worktree, `Farmsy-app-conversion`: `conv/analytics-identity` → `conv/paywall-before-auth` → `conv/farm-detail-lock` → `conv/onboarding-basket`. Luuk opens the PRs, Neil merges. Never push `main`.

## 1. Analytics identity and the missing events

### Problem
`SessionStore` listens to Supabase `authStateChanges`. The first emission on a cold start is `.initialSession`; for a signed-out user its `session` is nil, and the `else` branch runs `Observability.reset()` (iOS `Farmsy/Core/SessionStore.swift:133`, Android `SessionStore.kt:127`). `PostHogSDK.shared.reset()` / `PostHog.reset()` discards the anonymous id and mints a new one. Result: on iOS `app_opened` (fired in `bootstrap()` before the stream starts) lands on id A and everything after lands on id B; on Android `fireAppOpened()` runs after the reset, so every launch is a brand-new person. Both platforms: no retention, no funnel from step 1.

### Change
- Reset only on a real sign-out. iOS: in the `else` branch, call `PurchaseStore.signOut()` and `Observability.reset()` only when `state.event == .signedOut`. Android: same condition on the Supabase-kt auth status (`SessionStatus.NotAuthenticated(isSignOut = true)`); the `fireAppOpened()` call stays where it is.
- Three new events, added to `AnalyticsEvent` on both platforms, same names, same property keys:
  - `tab_viewed` — `tab` ∈ `home|shopping|map|discover|community`. Fired when the selected tab changes and once for the initial tab after the shell appears. Not on every re-render.
  - `shopping_item_added` — `item` (the item id, e.g. `eggs`), `source` ∈ `picker|typed|farm_detail|onboarding|discover`. Fired when an item joins `wantedProducts`, not on removal. `discover` is the Discover tab (product sheet, season card); it was found at implementation time.
  - `auth_prompted` — `trigger` (same values as `paywall_viewed`'s `trigger`, plus `trips` and `restore`). Fired when a signed-out person taps something on the Plus path that needs an account: a plan or Restore inside the Plus sheet, or the trip planner. Other sign-in asks (saving a farm, reporting status, posting) are not counted yet; thread a trigger through the `requestAuth` environment closure if the funnel ever needs them.
- `AnalyticsValue.Trigger` gains `.trips` and `.restore`; a new `AnalyticsValue.ListSource` closed set carries the four `source` values. No free text.

### PostHog
Add a third funnel to dashboard 938846: "Funnel C · Shopping → Plus": `app_opened` → `tab_viewed` (tab = shopping) → `shopping_item_added` → `paywall_viewed` (trigger = shopping_sample) → `plan_tapped` → `purchase_completed`, 7-day window, breakdown `platform`. Created through the project API from Luuk's logged-in browser, same as the existing two. Funnel A is left as is; it becomes readable once the identity fix ships.

### Test
- iOS `FarmsyTests/AnalyticsEventTests.swift` (new): every `AnalyticsEvent` rawValue is snake_case and unique; `Trigger` and `ListSource` rawValues are snake_case. Cheap guard against a typo splitting a funnel.
- The reset condition is exercised by launching the app twice on the simulator with `--reset-onboarding` absent and confirming in PostHog live events that both launches share one distinct id. Recorded in the PR description with the id prefix.

## 2. Show the paywall before asking for an account

### Problem
`shell.openPlus(_:)` wraps the Plus sheet in `requireAuth` (iOS `AppShell.swift:86`, Android `MainScreen.kt:287`). A signed-out person who taps "Upgrade to Farmsy Plus" gets the sign-in sheet and never sees what Plus is. 87% of users are signed out. Separately, `ProUpsellSheet` passes `session.session?.user.id` to `purchase(_:userId:)`; if that were ever nil the purchase would run under an anonymous RevenueCat id and the webhook would answer `ignored: 'unknown app_user_id'`, so the person pays and stays locked.

### Change
- `openPlus` opens the Plus sheet for everyone. `requireAuth` moves to the two plan buttons and "Restore purchases" inside `ProUpsellSheet` / `ProUpsellSheet.kt`.
- On a plan tap while signed out: remember the chosen package (`@State private var pending: Package?` / a `var pending: Package?` in the Compose state), fire `auth_prompted` with the paywall's trigger, and ask for the auth sheet. When `session.isAuthenticated` flips to true and `pending` is set, run the purchase with the now-present user id, then clear `pending`. If the auth sheet is dismissed without signing in, `pending` is cleared and nothing else happens.
- `purchase(_:userId:)` keeps its signature but `userId` becomes non-optional (`UUID`), so the anonymous path cannot be reached by construction. Same on Android.
- Sheet nesting on iOS: the auth sheet must present above the Plus sheet. Add a `showAuthInPlus` binding (`showAuth && showPlus`) attached with `.sheet` to the Plus sheet's content, and exclude `showPlus` from `showAuthAtRoot`. Same one-source-of-truth pattern the Profile and farm-card cases already use (`AppShell.swift:252-259`). Do not add stale sibling flags to the farm-card bindings (see relaunch notes).
- Android: `ProUpsellSheet.kt` receives `requestAuth` and `isAuthenticated`; the pending purchase runs in a `LaunchedEffect(isAuthenticated)`.

### Test
- Simulator, `--reset-onboarding`, signed out: Home → "Upgrade to Farmsy Plus" shows the Plus sheet (not the auth sheet). Tap "Try free for 7 days" → auth sheet appears above it. Sign in with a sandbox account → the App Store sheet appears without another tap. Cancel it → `purchase_failed` reason `cancelled`, sheet stays open. This is a tap test, recorded in the PR description; code review cannot catch dead taps here.
- Unit: none new beyond the `AnalyticsEventTests` guard; the gating is view state.

## 3. A Plus moment on the farm card

### Problem
The farm card is where attention is (1,722 iOS users in 30 days). Its only Plus lock, in `FarmStatusSection`, renders only when `freshness != nil`, i.e. when someone has reported this farm in the last days. On iOS the `farm_detail` trigger produced 0 paywall views in 30 days. The product chips that add items to the shopping list already exist in `FarmProductsSection` but fire no event, so nobody knows whether people use them.

### Change
- `FarmStatusSection` (both platforms): the lock row shows for every non-member, reports or not. Copy depends on data:
  - reports exist: unchanged, "Confirmed today — see when with Plus".
  - no reports: "Plus tells you when someone last found it open" (nl/en/fr/de).
  Trigger stays `.farmDetail`.
- `FarmProductsSection` (both platforms): every chip tap that adds and the "Add all to shopping" button fire `shopping_item_added` with `source = farm_detail`, one event per item added.
- Nothing else on the card changes. Alerts stay web-only for now.

### Test
- `FarmStatusTests` (iOS) / `FarmStatusTest` (Android) gain one case: the lock-row copy selector returns the no-reports string when the summary is empty and the freshness string when it is not.
- Simulator: open any farm signed out → lock row visible under the status headline; tap → Plus sheet with trigger `farm_detail` (check PostHog live events).

## 4. Onboarding: a basket step

### Problem
Welcome is skipped by 1,750 of 1,760 people who see it, and nothing in onboarding gives the person a shopping list. The shopping-sample lock, the strongest trigger there is, therefore fires only for the 1% who build a list by hand.

### Change (Luuk: keep welcome, add a step)
- New step `basket` between `personalize` and `location` on both platforms (`Step` enum: `welcome, personalize, basket, location, details, nearby, notify, done`; the progress fraction divides by `allCases.count - 2` and needs no change). Analytics name `basket`, so `onboarding_step_completed` / `onboarding_skipped` cover it for free.
- Screen: title "What do you usually buy?", subtitle "Pick a few. Farmsy will show which farms nearby have them.", a wrapping chip grid, Continue and Skip. Chips come from `ShoppingItems.shared` (`GET /api/shopping/items`, 29 items today). Load starts when onboarding appears. If the catalogue has not loaded by the time the step shows, the grid shows this fixed dozen, which are all real ids on that endpoint: `eggs, cheese, milk, potatoes, vegetables, fruits, meat, honey, bread, strawberry, apples, butter`. Labels come from the catalogue when present; the fallback uses the localized names already in the picker.
- Continue: for each picked id not already in `trip.wantedProducts`, `trip.toggleProduct(id)` (persists under the existing `wantedKey` in `UserDefaults` / Android `TripStore.readWanted()` storage) and one `shopping_item_added` with `source = onboarding`. Skip: nothing written.
- Nothing is pushed to `shoppingHistory`; that key records built routes, not picks.
- The Shopping tab already renders `wantedProducts` on first open, so a seeded list shows the coverage number and the Plus lock without further work.

### Test
- iOS `OnboardingBasketTests` (new): the seeding function (extracted as `static func seed(_ picked: [String], into trip: TripStore)`) adds only ids not already present and returns the ids it added, in order. Android: same on `TripStore`.
- Simulator, `--reset-onboarding`: pick three chips → Continue → finish onboarding → Shopping tab shows the three items and "N farms cover your list" with the lock.

## Out of scope
- Memoising `FarmsStore.filtered` (separate perf follow-up).
- Native alerts UI.
- Changing prices, trial length or the store sheet.
- Web changes. The webhook's `unknown app_user_id` branch stays as the last line of defence.

## Success measure
30 days after all four ship: Funnel A joins from step 1; paywall_viewed users ≥ 10% of app_opened users (from 1.4%); shopping_item_added users ≥ 30% of onboarding completers; at least one paid conversion per week. If plan_tapped → purchase_completed stays under 20%, the next question is the store sheet (price, trial), not the app.
