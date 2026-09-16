# Five-tab redesign — design spec (2026-09-15)

Full plan with phases: https://claude.ai/artifact/7hBZAwxprXBJZWJ4L631Pj

## Decisions
- Tabs: Home · Shopping · Map · Discover · Community. Profile opens from the Home header (Nime pattern). The map-first floating pill, and the Saved / Trips / Settings sheets it opened, go away.
- Layout and primitives copied from Nime (`nime-app/src/theme/base.ts`, `src/components/ui/*`): type scale, spacing (base 4), radii (pill 999 / card 20 / tile 16 / input 14 / sheet 28), three elevation levels, 44pt targets, floating 64pt tab pill without labels, two-mode header, row-card lists.
- Colours stay Farmsy: accent #234725, canvas #FCFAF6, tile #F3EAD9, ink #15110D. New fill-only vivid green #9BE15D. Text-safe semantics positive #137A4A / warning #9A5B00 / critical #BA2B28; fill-only vivid #9BE15D / #FF8A00 / #FF2D46. Primary button fill is ink.
- Typography: Plus Jakarta Sans (Regular/Medium/SemiBold/Bold, weight by file). Fraunces and Geist removed.
- Icons stay SF Symbols (iOS) / Material (Android).
- Entitlement: farm details are already free (web `features.ts` register). Time and axis filters become free. Plus = shopping-list farm matching, route builder, live availability freshness, product/season alerts.
- Phases 0–2 (foundations, Home + Profile, Shopping) ship on one branch `redesign/five-tabs` as one PR. Later phases are separate PRs. iOS first, Android port after each PR.

## Phase 0 — foundations
- `Theme.swift`: tokens above; `Font.ui(size, weight)` and `Font.role(.heading)`; `.geist(` call sites renamed to `.ui(`; `DisplayTitle` keeps its API, renders bold Jakarta without the italic word.
- `Farmsy/App/UI.swift`: FloatingTabBar, ScreenHeader, Chip, Badge, PillButton style, IconButton, RowGroup/Row, SearchField, EmptyState, AtmosphereBand, PlusLockCard.
- `AppShell` replaces `MainView`: five tabs, shared farm sheet, auth sheet, trips sheet.
- `FilterSheet`: Pro lock removed.

## Phase 1 — Home + Profile
- HomeScreen: atmosphere band (profile · wordmark · saved), greeting card with search + "Open now near me", Available near you (from pins + flags produce within radius), This week (seasons endpoint, falls back to nothing while it does not exist), Your farms, For you (alerts; locked card for free).
- ProfileScreen: identity block, groups (Account, Farmsy, Contributions, Preferences, Community, Legal), sign out, delete, version. Reuses SettingsSheet pieces.

## Phase 2 — Shopping
- ShoppingScreen: list (ShoppingItems + TripStore.wantedProducts), history with repeat, Find farms (Plus; ShoppingPlanner on device), Build my route (Plus; trip stops + TripsView).
