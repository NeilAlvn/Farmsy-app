# Farmsy iOS → Android Port Manifest

## Five-tab redesign (Sept 2026) — new surfaces and their Android files

The map-with-sheets shell became five tabs with a floating pill; Fraunces and Geist
became Plus Jakarta Sans (weight by file). The sections below this one predate
the redesign and still describe the old shell/typography where they mention it.

| iOS | Android |
|-----|---------|
| `App/Theme.swift` tokens (colours, `Space`, `Radius`, `TabBarInset`, `TextRole`) | `ui/theme/Theme.kt`, `ui/theme/Type.kt` (`ui()`, `role(TextRole)`; `geist()`/`display()` kept as aliases) |
| `App/UI.swift` primitive kit | `ui/theme/Components.kt` (`ScreenHeader`, `SectionHeader`, `PillButton`, `IconButton`, `Chip`, `Badge`, `RowGroup`/`ListRow`, `SearchField`, `EmptyState`, `AtmosphereBand`, `Wordmark`, `PlusLockCard`); `FloatingTabBar` in `features/main/MainScreen.kt` |
| `Features/Main/AppShell.swift` (`AppTab`, `ShellActions`) | `features/main/MainScreen.kt` (`AppTab`, `ShellActions`, `LocalShell`; pager keeps every tab alive) |
| `Features/Home/HomeScreen.swift` | `features/home/HomeScreen.kt` |
| `Features/Shopping/ShoppingScreen.swift` | `features/shopping/ShoppingScreen.kt` |
| `Features/Profile/ProfileScreen.swift` (replaces SettingsSheet) | `features/profile/ProfileScreen.kt` (replaces `features/settings/SettingsScreen.kt`) |
| `Features/Discover/DiscoverScreen.swift` | `features/discover/DiscoverScreen.kt` |
| `Features/Community/CommunityScreen.swift` | `features/community/CommunityScreen.kt` |
| `PingCard.swift` / `FeaturedFarmCard.swift` | `features/whatsnew/FeedCards.kt` (was `WhatsNewSheet.kt`; the sheet itself is gone) |
| `Core/FarmsStore.productsNearby` / `showProduct`; `ShoppingItem.emoji`, `ProductNearby` | `core/FarmsStore.kt`, `core/ShoppingList.kt`; search radius in `core/Preferences.kt` (`SearchRadius`) |
| `FarmsyTests/ProductsNearbyTests.swift` | `app/src/test/.../core/ProductsNearbyTest.kt` |
| Deleted: `SavedScreen`, `WhatsNewSheet`, `DiscoverFeedView` | Deleted: `features/saved/SavedScreen.kt`, `WhatsNewSheet` composable, `features/discover/DiscoverFeedScreen.kt` |

Map: the "Farmsy Pro" filter group is now "When and what kind" and free on both;
the map opens on the user's first fix unless a product/AI search is pending.
Plus upsell: the four feature lines sell matching, routing, live availability and
alerts (nl/fr/de fall back to English, as on iOS).

Exhaustive audit of the **iOS** app (`Farmsy/`, SwiftUI, 39 files, ~10,098 LOC) for a 1:1 Android (Jetpack Compose) port. Every screen, element, model, endpoint, token and dependency is enumerated with a **Status** column. Nothing here is summarized as "similar to above" — each item is listed.

**Status legend:** `NOT_STARTED` · `IN_PROGRESS` · `DONE` · `N/A` (platform-specific, no Android analog). Every row below is `NOT_STARTED` unless a port already exists — this manifest is the source of truth; treat all as `NOT_STARTED` for planning.

**Conventions:**
- Colors are sRGB hex from `Farmsy/App/Theme.swift` (measured from the web `DESIGN-SYSTEM.md`, oklch→sRGB — not eyeballed).
- Fonts: **Fraunces** (serif display) via `display()`/`displayItalic()`; **Geist** (UI/body) via `geist()`; **GeistMono** via `geistMono()`. Weights map to named TTFs.
- "Spacing/pad" are SwiftUI points (≈ Android dp).
- SF Symbols are named; the Android port must substitute Material or vector equivalents (tracked per element).

---

## 0. Master Screen Index

| # | Screen / View | Source file | Presentation | Status |
|---|---------------|-------------|--------------|--------|
| S1  | SplashView | Features/Splash/SplashView.swift | Root, fades to Main/Onboarding | NOT_STARTED |
| S2  | OnboardingView (container) | Features/Onboarding/OnboardingView.swift | Root (first run) | IN_PROGRESS |
| S2.1| Onboarding · Welcome step | ″ | Slide track page 0 | IN_PROGRESS |
| S2.2| Onboarding · Personalize step | ″ | Slide track page 1 | IN_PROGRESS |
| S2.3| Onboarding · Location step | ″ | Slide track page 2 | IN_PROGRESS |
| S2.4| Onboarding · Details step | ″ | Slide track page 3 | IN_PROGRESS |
| S2.5| Onboarding · Nearby step | ″ | Slide track page 4 | IN_PROGRESS |
| S2.6| Onboarding · Notify step | ″ | Slide track page 5 | IN_PROGRESS |
| S2.7| Onboarding · Done step | ″ | Slide track page 6 | IN_PROGRESS |
| S3  | MainView (map-first shell) | Features/Main/MainView.swift | Root (returning/authed) | IN_PROGRESS |
| S4  | MapScreen | Features/Map/MapScreen.swift | Base layer of MainView | IN_PROGRESS |
| S5  | FilterSheet | Features/Map/MapScreen.swift | `.sheet` [medium,large] from search bar | IN_PROGRESS |
| S6  | WhatsNewSheet | Features/Map/WhatsNewSheet.swift | `.sheet` [0.55,0.92] from bottom panel | IN_PROGRESS |
| S7  | FarmDetailView | Features/Detail/FarmDetailView.swift | `.sheet` [180,0.55,large] on pin tap | IN_PROGRESS |
| S8  | LockedAccessView (paywall) | Features/Detail/FarmDetailView.swift | `.sheet` from detail gate | IN_PROGRESS |
| S9  | FarmMemberSections | Features/Detail/FarmMemberSections.swift | Embedded in FarmDetailView (member) | IN_PROGRESS |
| S10 | ImageLightbox | Features/Detail/ImageLightbox.swift | `.fullScreenCover`, clear bg | IN_PROGRESS |
| S11 | DiscoverFeedView | Features/Discover/DiscoverFeedView.swift | (feed; folded into WhatsNew on phone) | N/A |
| S12 | SavedScreen | Features/Saved/SavedScreen.swift | `.sheet` [0.55,0.92] from bottom panel | IN_PROGRESS |
| S13 | SettingsSheet | Features/Settings/SettingsSheet.swift | `.sheet` [0.55,0.92] from bottom panel | IN_PROGRESS |
| S14 | LanguagePickerSheet | Features/Settings/SettingsSheet.swift | `.sheet` [medium,large] from Settings | IN_PROGRESS |
| S15 | AccountDeletedView | Features/Settings/SettingsSheet.swift | `.fullScreenCover` terminal | IN_PROGRESS |
| S16 | AuthView | Features/Auth/AuthView.swift | `.sheet` (requestAuth hook) | IN_PROGRESS |
| S17 | VerifyEmailView | Features/Auth/AuthView.swift | In-place swap after signup | IN_PROGRESS |
| S18 | TripsView | Features/Trips/TripsView.swift | `.sheet` [0.5,0.92] from bottom panel | IN_PROGRESS |
| S19 | PlaceSearchSheet | Features/Trips/PlaceSearch.swift | `.sheet` [large] from Trips origin | IN_PROGRESS |
| S20 | AddFarmView | Features/Submit/AddFarmView.swift | NavigationStack sheet (web now) | N/A |
| S21 | ClaimFarmView | Features/Claim/ClaimFarmView.swift | NavigationStack sheet (web now) | N/A |
| C1  | TripRecommendations (carousel) | Features/Trips/TripsView.swift | Embedded (Saved/WhatsNew/Trips) | IN_PROGRESS |
| C2  | DiscoverFeedCard | Features/Discover/DiscoverFeedView.swift | Embedded row | NOT_STARTED |
| C3  | PingCard | Features/Discover/DiscoverFeedView.swift | Embedded row | IN_PROGRESS |
| C4  | MultiImageFarmCard | Features/Map/WhatsNewSheet.swift | Embedded row | IN_PROGRESS |
| C5  | FixedImageRow | Features/Discover/DiscoverFeedView.swift | Embedded photo row | IN_PROGRESS |
| C6  | FarmCard | Features/Map/MapScreen.swift | Bottom card (legacy, unused by MainView) | NOT_STARTED |
| C7  | ExpandableText | Features/Detail/FarmDetailView.swift | Inline clamped text | IN_PROGRESS |
| C8  | PostComposer / ReviewComposer | Features/Detail/FarmMemberSections.swift | Embedded forms | IN_PROGRESS |

**Screen/view count: 21 top-level screens + 8 reusable component-views = 29 addressable surfaces.**

---

## 1. Design Tokens (`Farmsy/App/Theme.swift`)

### 1.1 Colors — `extension Color` (init `Color(hex: UInt32)`, sRGB)

| Token | Hex | Role / usage | Status |
|-------|-----|--------------|--------|
| `farmGreen` | `#234725` | Primary brand green — surfaces away from the map, kickers, primary text accents | NOT_STARTED |
| `farmGreenDeep` | `#18321A` | Darker green — gradients, highlighted pin, AI chip text | NOT_STARTED |
| `farmGreenMap` | `#4E7F54` | On-map / soft green — every control that sits *on* the map (search icons, filter, locate, clusters, route casing under blue, primary buttons via PrimaryButtonStyle) | NOT_STARTED |
| `farmGreenSoft` | `#234725` @ 10% | Primary tint fill (banners, chips, soft backgrounds) | NOT_STARTED |
| `cream` | `#FCFAF6` | App background (warm off-white) | NOT_STARTED |
| `creamCard` | `#FDFCF9` | Card fill (a hair lighter than ground) | NOT_STARTED |
| `creamFill` | `#F3EAD9` | Marketing blocks / input fields on white | NOT_STARTED |
| `ink` | `#15110D` | Primary foreground (warm near-black) | NOT_STARTED |
| `inkMuted` | `#68625E` | Secondary/muted foreground (warm grey) | NOT_STARTED |
| `hairline` | `#E1DDD8` | Border/divider 1px | NOT_STARTED |
| `star` | `#FBBF24` | Rating star amber (never brand) | NOT_STARTED |
| `warnRed` | `#BA2B28` | Destructive / error | NOT_STARTED |

**Ad-hoc hex used inline (not tokens — port as constants):** `#6B7280` (circle-button glyph grey), `#F3F4F6` (circle-button bg / photo tile bg), `#4B5563` (map circle btn tint), `#374151` (lightbox arrow), `#E5E4DF` (secondary button / toggle track off), `#E5E7EB` (dashed empty-slot stroke), `#9CA3AF` (dashed trip prompt), `#10B981`/`#047857`/`#ECFDF5` (open-now badge), `#F5B301` (bell yellow / notifications icon), `#EF4444` (bell badge red), `#EC4899` (refer icon), `#38BDF8` (contact icon), `#8B5CF6` (privacy icon), `#64748B` (terms icon), `#3F5E3A` (language/sign-out icon bg), `#DC2626` (delete icon), `#F7F6F2`/`#F3F6F2` (composer / restart-note bg), `#EDE7DD` (rec card placeholder), `#ECEBE8` (skeleton base).

### 1.2 Typography

| Helper | Face | Weights → PostScript name | Sizes seen | Status |
|--------|------|---------------------------|-----------|--------|
| `display(size, weight=.bold)` | Fraunces | regular→Fraunces-Regular, medium→Fraunces-Medium, semibold→Fraunces-SemiBold, else→Fraunces-Bold | 19,22,24,26,28,30,32,34,52,60 | NOT_STARTED |
| `displayItalic(size, weight=.regular)` | Fraunces Italic | medium→Fraunces-MediumItalic, else→Fraunces-Italic | 52,60 (splash/welcome), used inside DisplayTitle emphasis | NOT_STARTED |
| `geist(size, weight=.regular)` | Geist | medium→Geist-Medium, semibold→Geist-SemiBold, bold/heavy/black→Geist-Bold, else→Geist-Regular | 9,10,11,12,13,14,15,16,17,18,19,22,24,28,44,46,54,56 | NOT_STARTED |
| `geistMono(size, weight=.regular)` | GeistMono | medium→GeistMono-Medium, else→GeistMono-Regular | (defined; sparse use) | NOT_STARTED |

Bundled fonts live in `Farmsy/Fonts/`. Android must ship the same Fraunces + Geist + GeistMono TTFs and map weights identically.

### 1.3 Shared component styles

| Component | Spec | Status |
|-----------|------|--------|
| `DisplayTitle(_ marked, size)` | Serif headline; one *asterisk-wrapped* word rendered italic (Fraunces-MediumItalic), rest Fraunces-Medium; `.foregroundStyle(ink)`, `.multilineTextAlignment(.center)`. Legacy 3-part init `(leading,emphasis,trailing)` for dynamic emphasis (farm names). | NOT_STARTED |
| `Kicker(text)` | `geist(14,.semibold)`, `.uppercased()`, kerning 1.6, `farmGreen` | NOT_STARTED |
| `PrimaryButtonStyle` | fill `farmGreenMap` (overridable), `geist(18,.semibold)`, white, `.frame(maxWidth:.infinity)`, vertical pad 17, radius 16 continuous, pressed `scaleEffect 0.97` spring(0.25) | NOT_STARTED |
| `SecondaryButtonStyle` | fill `#E5E4DF`, `geist(18,.semibold)`, ink, Capsule, vertical 17, pressed 0.97 | NOT_STARTED |
| `CardBackground` / `.card(padding=16)` | fill `creamCard`, radius 16 continuous, `hairline` 1px stroke, no shadow at rest | NOT_STARTED |
| `StatTile(value,caption)` | value `geist(22,.bold)` farmGreen (minScale 0.6, 1 line); caption `geist(13)` inkMuted; centered, maxWidth infinity | NOT_STARTED |
| `RingingBell(size=76)` | `bell.fill` `#F5B301`, swings ±14° (anchor top, easeInOut 0.4 repeatForever autoreverse); red `#EF4444` badge (0.3·size) scaling 0.9↔1.15 (easeInOut 0.8 repeatForever); badge offset x=+0.06·size y=−0.04·size | NOT_STARTED |
| `SkeletonBox(cornerRadius=12)` | base `#ECEBE8`, white-band gradient (0.55) width 0.6·w sweeping x −1→1 over 1.4s linear repeatForever | IN_PROGRESS — `whatsnew/WhatsNewSheet.kt SkeletonBox`; band = **0.6·measured width** (BoxWithConstraints), sweep x=phase·1.4·w, 1.4s LinearEasing |
| `Haptics` | `tap`=UIImpactFeedback(.light); `success`=UINotification(.success); `warning`=UINotification(.warning) | NOT_STARTED |
| `TapCard` / `.tapCard(excludeTopTrailing:)` | Tap-that-isn't-a-scroll; fires `Haptics.tap()` (`.light`) before the action; `excludeTopTrailing` carves a square out of top-right for a corner control | IN_PROGRESS — `ui/theme/Components.kt Modifier.tapCard` (`@Composable` factory; `detectTapGestures`; scroll cancels the tap via parent consume; excludeTopTrailing corner check). Haptic now fired via `LocalHapticFeedback` → `performHapticFeedback(TextHandleMove)` (light tick ≈ iOS `.light`) |
| `SafariView` | SFSafariViewController wrapper, tint `farmGreen`, dismiss `.close` — for web claim/submit pages | N/A (Android: Custom Tab) |

### 1.4 Spacing / radii scale (observed)

Corner radii: **12** (small tiles/fields), **14** (chips/small buttons), **16** (cards/buttons/photo tiles — the dominant radius), **18** (AI summary bar), **22/24** (paywall card / lightbox panel), Capsule (pills, filter chips, badges). Horizontal screen padding: **14** (map overlays, feed), **16** (sheet headers), **18** (form screens), **20** (settings/onboarding), **24** (auth). Card internal padding: **12/14/16**. Standard vertical rhythm: **8/10/12/14/16/20/22** gaps.

---

## 2. Data Models (`Farmsy/Core/Models.swift`, `SmartSearchAPI.swift`)

| Model | Fields (wire keys) | Notes / decoding quirks | Status |
|-------|--------------------|-------------------------|--------|
| `FarmCategory` (enum) | produce, dairy, cheese, eggs, meat, fish, honey, wine, markets, organic | Each has `emoji`, `label` (localized), `color` (hex). `tagToCategory` (24 OSM tag→cat), `valueAlias` (beef/poultry/chicken/pork→meat, vegetables/fruit→produce, etc.), `from(raw)` = case→alias→nil | NOT_STARTED |
| `FarmPin` (Decodable, Hashable) | id, osm_id, name, lat, lng, address, city, postal_code, country, phone, website, opening_hours, image, primary_tag, farm_type[], avg_rating, review_count, has_description, is_verified | `farm_type` normalizer: accepts `[String]`, `{a,b}` pg-literal, plain string, or null. `categories` = de-dup resolve of farm_type → else tagToCategory[primary_tag]. `primaryCategory` = first ?? produce. `distance(from:)` haversine via CLLocation | NOT_STARTED |
| `FarmDetail` (Decodable) | osm_id, phone, website, address, postal_code, country, opening_hours, image, description, email, facebook, instagram, organic, produce, operator, images[] | Subscriber-gated payload. `organic` accepts bool or "yes/true/only/organic" string | NOT_STARTED |
| `Ping` (Decodable, Identifiable) | id, farm_osm_id, author_name, body, like_count, created_at, farm_ping_images(url,sort_order) | Images sorted by sort_order. `date` parses ISO w/ + w/o fractional seconds. Author denormalized (survives account delete) | NOT_STARTED |
| `Review` (Decodable, Identifiable) | id, user_id, reviewer_name, rating(1–5), body?, created_at | reviewer_name denormalized; one per user+farm (upsert) | NOT_STARTED |
| `FarmTeaser` (Decodable) | text, truncated | First ~200 chars of description (public opener) | NOT_STARTED |
| `Profile` (Decodable) | subscription_status, subscription_plan, subscription_end_date(Date), subscription_source, role, founding_member, first_name, last_name | `hasFullAccess`: admin\|farmer → true; founding_member → true; active/trialing → true; canceled → end>now; else false | NOT_STARTED |
| `SmartSearchIntent` (Decodable, Equatable) | products[], categories[], locationTypes[], methods[], openNow, automaat, zelfpluk, verified, place?, center{lat,lng}?, nearMe, radiusKm?, summary?, ranking? | `isEmpty` = all fields empty/false. Fails to empty, never error | NOT_STARTED |
| `SearchRanking` (Decodable, Equatable) | version=1, distanceZeroKm=100, distanceWeight=100, openToday=20, verified=15, hasPhoto=10, ratingFactor=2, reviewEach=0.25, reviewCap=20 | `.default`; unknown version → fall back to default weights | NOT_STARTED |
| `SignUpDetails` (struct) | email, password, firstName, lastName, dob(ISO yyyy-MM-dd, 16+), streetAddress, city, postalCode, country, refCode? | Mirrors 2-step web form | NOT_STARTED |
| `SubmissionAPI.FarmSubmission` (struct) | name, city, description, farmType[], address, postalCode, country="Netherlands", phone, website, email, openingHours, lat?, lng?, imageData[] | Multipart submit | NOT_STARTED |
| `SubmissionAPI.FarmClaim` (Encodable) | farmOsmId, farmName, fullName, email, phone, verificationMethod(email\|kvk), kvkNumber?, message? | JSON submit | NOT_STARTED |
| `FarmAxisValue` (struct) | id, label, icon(SF) | Two axes below | NOT_STARTED |
| `FarmAxis.placeTypes` | shop→storefront, vending-machine→cabinet, stall→basket, milk-tap→drop, self-picking→hand.raised, self-picking-unstaffed→hand.raised.slash | `location_types` | NOT_STARTED |
| `FarmAxis.methods` | biodynamic→moon.stars, regenerative→arrow.3.trianglepath, grass-fed→leaf, sustainable→globe.europe.africa | `methods` — **organic deliberately excluded** (stays a category) | NOT_STARTED |
| `LightboxSource` (struct, Identifiable) | images[], startIndex, eyebrow, title, subtitle?, postText? | Drives ImageLightbox fullScreenCover | IN_PROGRESS — `features/detail/ImageLightbox.kt` `data class LightboxSource(images, startIndex=0, eyebrow, title, subtitle?=null, postText?=null)`, co-located with the view as on iOS |
| `MapCluster` (struct, Identifiable) | id, coordinate, pins[]; `isCluster`=pins>1; `representative`=pins[0] | Grid-cluster bucket | NOT_STARTED |
| `SavedTrip` (Decodable) | id, name, updated_at?, stopCount (from trip_farms(count)) | From `trips` join | NOT_STARTED |
| `TravelMode` (enum) | car, bike, walk | label, icon(car.fill/bicycle/figure.walk), googleMode(driving/bicycling/walking), kmh(55/15/4.8), minutes(km) | NOT_STARTED |
| `QuickPrefs` (struct) | openToday, pickYourOwn, verified, hasPhotos | Onboarding details toggles | NOT_STARTED |

---

## 3. Networking Layer

**Base:** `Backend.supabaseURL` = `https://lxkyypmzxfkzddraxtat.supabase.co`; `Backend.webAPI` = `https://www.farmsy.app/api`; anon key ships in app. Secrets (RevenueCat public, Sentry DSN, PostHog) from `Secrets.plist` (gitignored; missing key disables feature).

| API / call | Method · path | Request | Response / behavior | Errors | Status |
|------------|---------------|---------|---------------------|--------|--------|
| `get_farms_pins` (Supabase RPC) | RPC, paginated `range(from,to)` size 1000 | — | `[FarmPin]`; loops pages until <1000 | sets `loadError` | NOT_STARTED |
| Flags | GET `/api/farms/flags` | — | `[{o,g[],p,l[],m[]}]` → galleries (g≥2), produceByOsm(p), locationTypesByOsm(l), methodsByOsm(m) | silent | NOT_STARTED |
| Farm detail | GET `/api/farm/[osmId]` (Bearer) | osm_id **%2F-encoded** (slashes preserved) | `FarmDetail` | 401/403→locked, 404→notFound, else other | NOT_STARTED |
| Teaser | GET `/api/farm/[osmId]/teaser` | — | `FarmTeaser` (public opener) | nil on any failure | NOT_STARTED |
| Teasers (batch) | POST `/api/farms/teasers` | `{osmIds:[…≤60]}` | `{teasers:{id:{text,truncated}}}` → drop empties | `[:]` | NOT_STARTED |
| Smart search | POST `/api/search/smart` | `{query}`, 6s timeout | `SmartSearchIntent` | nil (→ keyword fallback) | NOT_STARTED |
| Profile status | GET `/api/profile/status` (Bearer, refreshed token) | — | `Profile` (custom pg-date decode) | keep last on fail | NOT_STARTED |
| Login | POST `/api/auth/login` | `{email,password}` | `{session:{access_token,refresh_token}}` → `supabase.auth.setSession` | 401 invalid, 403 email_not_verified, 429 throttled | NOT_STARTED |
| Signup | POST `/api/auth/signup` | SignUpDetails (+ refCode uppercased) | 200/201 (no session — verify email) | 409 taken, 429 throttled, 400 missing_fields/invalid_dob/missing_credentials | NOT_STARTED |
| Delete account | POST `/api/account/delete` (Bearer) | — | `{ok,storeSubscriptionReminder,subscription_source}` then signOut | ok:false | NOT_STARTED |
| Reviews read | Supabase `.from(reviews)` eq farm_osm_id order created_at desc | — | `[Review]` | `[]` | NOT_STARTED |
| Review upsert | Supabase upsert `reviews` onConflict user_id,farm_osm_id | Payload | — | throws | NOT_STARTED |
| Posts read | Supabase `.from(farm_pings)` eq osm_id, status=visible, order desc (+images join) | — | `[Ping]` | `[]` | NOT_STARTED |
| Liked ids | Supabase `.from(farm_ping_likes)` eq user_id | — | `Set<ping_id>` | `[]` | NOT_STARTED |
| Toggle like | Supabase insert/delete `farm_ping_likes` | ping_id,user_id | — | best-effort | NOT_STARTED |
| Report ping | Supabase insert `farm_ping_reports` (unique/person) | ping_id,user_id | — | best-effort | NOT_STARTED |
| Create post | Storage upload → `ping-images/${userId}/${ts}-${i}.jpg` (JPEG), then insert `farm_pings` (+`expires_at` 100yr) → `farm_ping_images` | body, photos≤3 | pingId | throws | NOT_STARTED |
| Feed pings (all-farms) | Supabase `.from(farm_pings)` status=visible order created_at desc limit 30, **no farm filter** | — | `[Ping]` | `[]` | IN_PROGRESS — `FarmContentApi.feedPosts(limit=30)`; PORT NOTE: iOS calls this inline in the views (WhatsNewSheet/DiscoverFeedView), Android centralises it in the API layer |
| Flags (galleries `g`) | GET `/api/farms/flags` → keep `g` when ≥2 photos → `galleries[o]=g` | — | gallery map | silent | IN_PROGRESS — `FarmsStore.loadFlagsIfNeeded` now captures `g` (≥2) into `_galleries` (was discarded) |
| Featured teasers prefetch | `FarmDetailApi.teasers` batched (≤60/call) over all gallery ids | — | `{osmId:text}` | `[:]` | IN_PROGRESS — `FarmsStore.loadGalleriesIfNeeded` chunks ids into 60s (PORT NOTE: iOS uses per-id `teaser()` TaskGroup) |
| Favorites read | Supabase `.from(favorites)` eq user_id | — | `Set<farm_osm_id>` | leave set | NOT_STARTED |
| Favorite toggle | Supabase insert/delete `favorites` (optimistic + rollback) | user_id,farm_osm_id | — | rollback | NOT_STARTED |
| Route | POST `/api/route` | `{coordinates:[[lng,lat]…]}` (2–50) | `{coordinates[[lng,lat]],distance,duration,segments}` | nil → straight-line fallback | NOT_STARTED |
| Trips read | Supabase `.from(trips)` select `id,name,updated_at,trip_farms(count)` eq user_id order updated_at desc limit 20 | — | `[SavedTrip]` | `[]` | NOT_STARTED |
| Planned farm ids | Supabase `.from(trip_farms)` in trip_id | — | `Set<farm_osm_id>` | `[]` | NOT_STARTED |
| Trip save | Supabase insert/update `trips` + delete/insert `trip_farms` (caches name/coords/city/image) | — | tripId (rollback on stop failure) | rollback | NOT_STARTED |
| Trip open | Supabase `.from(trip_farms)` eq trip_id order sort_order | — | stopIds | `[]` | NOT_STARTED |
| Trip delete | Supabase delete `trips` eq id (optimistic) | — | — | best-effort | NOT_STARTED |
| Submit farm | POST `/api/farms/submit` (Bearer, multipart) | fields + images≤5 | — | code unauthenticated/no_subscription/invalid/failed | NOT_STARTED |
| Submit claim | POST `/api/farms/claim` (Bearer, JSON) | FarmClaim | — | same code taxonomy | NOT_STARTED |

---

## 4. Persistence

| Store | Key / table | What | Android equivalent | Status |
|-------|-------------|------|--------------------|--------|
| UserDefaults | `didFinishOnboarding` (Bool) | Onboarding gate | SharedPreferences | NOT_STARTED |
| UserDefaults | `pendingRefCode` + `pendingRefCodeAt` | Referral code (7-day TTL) captured from `/join?ref=` universal link | SharedPreferences + deep link | NOT_STARTED |
| UserDefaults | `app_language` + `AppleLanguages` | In-app language override (applies on relaunch) | SharedPreferences + per-app locale / `attachBaseContext` | NOT_STARTED |
| UserDefaults (TripStore) | `dlb_pending_trip`(stops), `dlb_trip_origin`(+`.label`), `dlb_trip_owner`, `dlb_trip_mode` | Local trip draft + owner reconcile | SharedPreferences | NOT_STARTED |
| Supabase auth | Keychain session | Access/refresh tokens | supabase-kt session store | NOT_STARTED |
| Supabase tables | favorites, trips, trip_farms, reviews, farm_pings, farm_ping_images, farm_ping_likes, farm_ping_reports, profiles (via API) | Sync w/ web | supabase-kt postgrest | NOT_STARTED |
| Supabase Storage | `ping-images` bucket, path `${userId}/…` | Post photos (JPEG ≤4.5MB) | supabase-kt storage | NOT_STARTED |

---

## 5. Auth Flow

**Entry:** browsing is fully open; sign-in is requested lazily via the `\.requestAuth` environment hook (MainView installs the real presenter). States:

1. **Bootstrap** (`SessionStore.bootstrap`): read `supabase.auth.session`; if present → `refreshProfile()`. Subscribe to `authStateChanges` → on session: `PurchaseStore.identify` + `Observability.identify` + refresh; on nil: clear profile, `PurchaseStore.signOut`, `Observability.reset`.
2. **Log in** (2-field): POST login → `setSession(access,refresh)` → refresh. 403 `email_not_verified` is surfaced distinctly (never as bad password).
3. **Sign up** (2-step: credentials then name+DOB+address; only name+country required per Apple 5.1.1(v)): POST signup returns 200 **without** a session → show **VerifyEmailView** ("check your inbox"). Referral code consumed only on success.
4. **Full access** (`Profile.hasFullAccess`): admin/farmer/founding always; active/trialing; canceled within paid period.
5. **Sign out**: `supabase.auth.signOut` + clear.
6. **Delete account** (Apple 5.1.1(v)): POST delete → signOut → **AccountDeletedView** with store-cancel reminder naming the actual billing rail (google/stripe/apple).

Foreground refresh: `scenePhase → .active` re-checks profile (catches store purchases whose webhook lands seconds later).

---

## 6. Stores / State (`@Observable`, injected via `.environment`)

| Store | Key state | Key methods | Status |
|-------|-----------|-------------|--------|
| `SessionStore` | session, profile, isBootstrapped, isDemoSession; `isAuthenticated`, `hasFullAccess`, `email`, `displayName` | bootstrap, refreshProfile, logIn, signUp, signOut, deleteAccount | NOT_STARTED |
| `FarmsStore` | pins, isLoading, loadError, searchText, selectedCategories, filterVerified/OpenToday/Automaat/Zelfpluk/HasPhotos, selectedPlaceTypes/Methods, aiIntent, aiCenter, aiPlaceToken, galleries, featuredOrder, featuredTeasers, produceByOsm, locationTypesByOsm, methodsByOsm, flagsLoaded, galleriesLoaded | loadIfNeeded, loadFlagsIfNeeded, loadGalleriesIfNeeded, `filtered` (AI branch + manual branch + rank), applyAISearch, clearAISearch, clearAllFilters, rankForIntent, sortedByDistance, categoryCounts, feedPicks, nearbyWithImages, pin(forOsmId) | IN_PROGRESS (featured/gallery machinery, this session) |
| `FarmsStore` **featured/gallery rows** (S6 dependency, previously omitted) | **state:** `galleries` (g≥2), `galleriesLoaded`, `featuredOrder` (frozen once-shuffled: described-first then rest, each shuffled), `featuredTeasers` | **methods:** `loadGalleriesIfNeeded()` (loadFlags → batch-teaser prefetch → freeze order), computed `featuredFarms` (order→pins). Freeze on first load; nothing invalidates it for the session (iOS parity) | IN_PROGRESS — `FarmsStore.kt` `_galleries`/`galleriesLoaded`/`featuredOrder`/`featuredTeasers`/`loadGalleriesIfNeeded`/`featuredFarms` |
| `FarmsStore` **filter/helper rows** (previously omitted / diverged) | `selectedCategories: Set<FarmCategory>` (**multi-select**, was single `selectedCategory?`), `anyQuickFilterOn()`, `nearbyWithImages(lat,lng,radiusKm=100)` | `filtered()` combines categories as "farm in ANY selected" (iOS parity); `applyAISearch` now `clearAllFilters()` | IN_PROGRESS — `FarmsStore.kt`. **S5 work (not mechanical):** FilterSheet must bind category rows to `selectedCategories` (add/remove) — no category rows exist there currently. |
| `FavoritesStore` | osmIds:Set | load, isSaved, toggle (optimistic), clear | NOT_STARTED |
| `TripStore` | stopIds, originCoord/Label, editingTripId, routeLine, distanceMeters, durationSeconds, isRouting, onRoads, mode, traceProgress(+tracedLine), savedTrips, plannedFarmIds, fitToken | contains, toggle, remove, move, clear, setOrigin, clearOrigin, reconcileOwner, optimise (NN+2-opt), refreshRoute, loadTrips, save, openTrip, deleteTrip, setMode | NOT_STARTED |
| `PurchaseStore` (RevenueCat) | offering, isPurchasing, purchaseError, offeringFailed, didLoadOffering; `yearlyPackage/Price`, `lifetimePackage/Price`, `yearlyFreeTrialDays`, `productsUnavailable`, `displayPrice` | configure, identify, signOut, loadOffering, purchase, restore | IN_PROGRESS — `didLoadOffering`/`productsUnavailable`/`displayPrice` added this session (iOS semantics: didLoad true on any finished attempt incl. disabled; productsUnavailable = didLoad && yearly==null) |
| `LocationManager` (CLLocationManager) | location, status, isRequesting | request | NOT_STARTED |
| `LanguageManager` | current(Lang), launchLocale | set, localized(key,in) | NOT_STARTED |
| `Observability` | — | start, identify, reset, capture (Sentry + PostHog) | NOT_STARTED |
| `PlaceSearch` (MKLocalSearchCompleter) | query, results | resolve(completion) → coord+label | NOT_STARTED |

---

## 7. Third-party dependencies → Android equivalents

| iOS dependency | Purpose | Android equivalent | Status |
|----------------|---------|--------------------|--------|
| Supabase Swift SDK | Auth session, Postgrest, Storage, RPC | `supabase-kt` (auth, postgrest, storage) | NOT_STARTED |
| RevenueCat (Purchases) | IAP yearly/lifetime, entitlements, restore | RevenueCat Android (`Purchases`) | NOT_STARTED |
| MapKit (Map, MKLocalSearchCompleter, MKDirections proxy) | Map render, clustering, place autocomplete | Google Maps Compose + Places SDK autocomplete | NOT_STARTED |
| CoreLocation (CLLocationManager) | GPS | FusedLocationProvider / LocationHelper | NOT_STARTED |
| PhotosUI (PhotosPicker) | Photo pick for posts/submit | Photo Picker (`ActivityResultContracts.PickMultipleVisualMedia`) | NOT_STARTED |
| Sentry | Crash reporting | sentry-android | NOT_STARTED |
| PostHog | Product analytics (shared project key + user id) | posthog-android | NOT_STARTED |
| SafariServices (SFSafariViewController) | In-app web (claim/submit) | Chrome Custom Tabs | NOT_STARTED |
| StoreKit (via RevenueCat introductoryDiscount) | Free-trial eligibility | Play Billing via RevenueCat | NOT_STARTED |
| SwiftUI AsyncImage | Remote images | Coil `AsyncImage` | NOT_STARTED |
| ShareLink | Share sheet | `Intent.ACTION_SEND` | NOT_STARTED |
| Fonts: Fraunces, Geist, GeistMono (bundled TTF) | Type | Same TTFs in `res/font` | NOT_STARTED |

---

## 7a. SF Symbols → Material Icons (reference mapping)

SF Symbols do not exist on Android; Material Icons are the equivalent API, so this is a
**design mapping, not a two-status divergence** (no PORT_NOTES entry). The per-row `(SF
substitution)` annotations point here. **Device-gated** rows are where the Material glyph's
shape differs enough from the SF symbol that a visual check is warranted; the rest are
close matches.

| iOS SF Symbol | Material (Icons.*) | Used by | Visual |
|---------------|--------------------|---------|--------|
| heart / heart.fill | Filled.FavoriteBorder / Favorite | S7 save, S9 like | match |
| square.and.arrow.up | Filled.Share | S7 share | **device-gated** (box-arrow vs share-nodes) |
| xmark | Filled.Close | S7/S10/S18 close | match |
| star / star.fill | Filled.StarBorder / Star | S7·S9·C8 ratings | match |
| mappin.and.ellipse | Filled.Place | S7 location, S9 address | close |
| checkmark.seal{.fill} | Filled/Outlined.Verified | S7 badge, S8·S9 claim | **device-gated** (seal vs check-badge) |
| location.fill | Filled.NearMe | S7 directions | **device-gated** (pin vs nav-arrow) |
| phone{.fill} | Filled.Phone | S7 call, S9 phone | match |
| globe | Filled.Public | S7 website, S9 | match |
| lock.fill | Filled.Lock | S7 footer/tripButton · S8 | match |
| lucide Unlock (web) / open padlock | Filled.LockOpen | S7 sign-up-wall block | 1:1 — sign-up wall, not paywall: an OPEN lock. iOS shipped the same open padlock in build 19 (`lock.open.fill`), so this now matches iOS directly. |
| plus / checkmark | Filled.Add / Check | S7 trip toggle | match |
| arrow.right | AutoMirrored.Filled.ArrowForward | S7 locked CTA | match |
| clock | Filled.Schedule | S9 hours | match |
| envelope | Filled.Email | S9 email | close |
| link | Filled.Link | S9 fb/ig | match |
| leaf | Filled.Eco | S9 organic | close |
| basket | Filled.ShoppingBasket | S9 produce | close |
| arrow.up.right | Filled.NorthEast | S9 link rows | match |
| shield | Outlined.Shield | S9 claim | match |
| flag | Outlined.Flag | S9·S6 report | match |
| photo.badge.plus | Filled.AddPhotoAlternate | C8 photo picker | **device-gated** |
| paperplane.fill | AutoMirrored.Filled.Send | C8 post | match |
| xmark.circle.fill | Filled.Close in black@50% circle | C8 remove photo | close (two-tone) |
| checkmark.circle.fill | Filled.CheckCircle | S15 seal | match |
| checkmark | Filled.Check | S14 selected lang | match |
| globe (Language row) | Filled.Language | S13 Language row | close |
| bell.fill | Filled.Notifications | S13 Notifications | match |
| gift.fill | Filled.CardGiftcard | S13 Refer friends | close |
| envelope.fill | Filled.Email | S13 Contact us | match |
| hand.raised**.fill** | Filled.PrivacyTip | S13 Privacy Policy | **device-gated** (raised-hand vs shield-i). NOTE: distinct from the outline `hand.raised`→FrontHand below (S5) — different SF symbol (fill vs outline), different context |
| doc.text.fill | Filled.Article | S13 Terms | close |
| rectangle.portrait.and.arrow.right | AutoMirrored.Filled.Logout | S13 Sign out | close |
| trash.fill | Filled.Delete | S13 Delete account | match |
| bolt | Filled.Bolt | S5 Open 24/7 (automaat) | match |
| camera | Filled.PhotoCamera | S5 Has photos | match |
| storefront | Filled.Storefront | S5 Farm shop (axis) | match |
| cabinet | Filled.Inventory2 | S5 Vending machine (axis) | **device-gated** (cabinet vs box — no Material vending/cabinet glyph) |
| drop | Filled.WaterDrop | S5 Milk tap (axis) | match |
| hand.raised (outline) | Filled.FrontHand | S5 Self-picking (axis) | close. NOTE: the filled `hand.raised.fill`→PrivacyTip above (S13 privacy) is a different SF symbol in a different context — both are correct, not a duplicate |
| hand.raised.slash | Filled.DoNotTouch | S5 Self-picking unstaffed (axis) | close |
| moon.stars | Filled.NightsStay | S5 Biodynamic (axis) | **device-gated** (moon+stars vs moon) |
| arrow.3.trianglepath | Filled.Recycling | S5 Regenerative (axis) | **device-gated** (triangle-loop vs recycling) |
| globe.europe.africa | Filled.Public | S5 Sustainable (axis) | close |
| eye | Filled.Visibility | S16 password reveal | match |
| eye.slash | Filled.VisibilityOff | S16 password reveal | match |
| location.fill (in-circle) | Filled.LocationOn | S19 use-my-location | close (pin-fill vs pin-outline; distinct from S7 directions→NearMe) |
| mappin.circle | Filled.Place | S19 result rows | close (mappin-in-circle vs plain pin) |
| chevron.left / chevron.right | Filled.KeyboardArrowLeft / Right | S10 arrows | match |
| photo | Filled.Photo | S10 error | match |
| magnifyingglass | Filled.Search | S4 search | match |
| sparkles | Filled.AutoAwesome | S4 AI | close |
| line.3.horizontal.decrease.circle | Filled.FilterList / Tune | S4 filter | **device-gated** |
| newspaper | (S3 Discover pill) | S3 | verify glyph |
| gearshape | (S3 Settings pill) | S3 | verify glyph |
| (survey entry) text.bubble.fill | AutoMirrored.Filled.Chat | Survey button (map) | close |
| (survey arrow) — regular down arrow | Filled.ArrowDownward | Survey pointer, above the button | product-sourced (a standard down arrow, farmGreen; the button-pointer that shows while unanswered). NOTE: not an SF port — the pointer is a product element both platforms build from spec |

**Follow-ups for the author:** rows marked *verify glyph* (S3 pill `newspaper`/`map`/
`gearshape`) are not yet annotated in code — confirm the chosen Material glyph and add to
this table. Keep this table the single source; drop the scattered `(SF substitution)` code
comments to a bare `// SF→Material (see manifest §7a)` at your convenience.

---

## 8. Screens — element-by-element

### S1 · SplashView — `Features/Splash/SplashView.swift`
**Presents:** root; on finish (`onFinished`) RootView fades to Main/Onboarding. **Reads:** nothing (self-timed). **Writes:** nothing. **Bg:** `cream` ignoresSafeArea, centered group offset y −14 (optical), whole group scaleEffect settle 1.06→1.

**PORT STATUS: DONE** — `android/…/features/splash/SplashScreen.kt`. All 17 rows verified DONE (checklist below). Fixed vs prior port: added logo blur-in (`:102`), letter rotation anchor-bottom via graphicsLayer (`:119`), settle `EaseOut` (`:77`), tap haptic (`:82`); aligned mark opacity to the markIn spring (`:62`). PORT NOTEs at `:80` (haptic constant), `:118` (rotation anchor).

| Order | Element | Spec | Status |
|-------|---------|------|--------|
| 1 | Brand mark image | `Image("FarmsyLogo")` scaledToFit height 116; markIn: scale 0.55→1 (spring 0.65 bounce 0.3), opacity 0→1, blur 5→0 | DONE `:96,99–102` |
| 2 | Spacer | 16 | DONE `:104` |
| 3 | Wordmark "Farmsy" | Per-letter HStack(spacing 0); each `displayItalic(60,.medium)` ink; lettersIn cascade: opacity 0→1, offset y 38→0, blur 7→0, rotation 7°→0 (anchor bottom), spring(0.6 bounce 0.32) staggered delay i·0.075 | DONE `:108–121,66–67` |
| 4 | Spacer | 16 | DONE `:126` |
| 5 | Underline | Capsule `farmGreen`, width 0→132, height 4, opacity 0→1, spring(0.55 bounce 0.25) delay 0.85 | DONE `:129–131,71–72` |

**Sequence:** markIn spring; lettersIn true; underline delay 0.85; settle delay 0.2 easeOut 0.7; hold 2.3s (7s if `--hold-splash`) → `Haptics.tap()` → onFinished. **Status: DONE** `:60–83` (PORT NOTE: `--hold-splash` debug flag is iOS-only; N/A on Android). **Animations:** all above. **Empty/error:** none.

---

### S2 · OnboardingView (container) — `Features/Onboarding/OnboardingView.swift`
**PORT STATUS: IN_PROGRESS (assessment session — no build).** `features/onboarding/OnboardingScreen.kt` (660 lines) is **substantially built and faithful**: all 7 steps (`Step` enum welcome/personalize/location/details/nearby/notify/done — 1:1), container (full-bleed welcome bg + gradient, header slot, `ProgressBar` `fraction = index/(count−2)`, sliding page track), `Kicker` + `DisplayTitle` (italic emphasis, correct sizes 32/30/30/28/34), `RadarPulse`, `RingingBell`, custom 46×28 toggle (`PrefRow`). **Write-back matches iOS:** on finish sets `farms.selectedCategories` (multi-select `Set<FarmCategory>` — same shape S5 binds), and if `applyPrefs` sets `filterVerified/OpenToday/HasPhotos/Zelfpluk`; RootNav sets `didFinishOnboarding` (`RootNav.kt:73`). Wired: RootNav Splash→onboarding(first run)→Main; welcome "Log in" → the app-wide `AuthSheet` (S16). **BOTH DIVERGENCES NOW FIXED.** (1) ~~**S2.3 location "Search a town instead"**~~ **FIXED (Pass B, this session)** — the inline `TownPicker` (8 hardcoded chips) is replaced by **S19 `PlaceSearchSheet` (Photon, reused as-is)**: LocationStep's search row → `onSearch` → `showPlaceSearch` → sheet; onPick sets `chosenLabel`, onLocate → GPS request (iOS onboarding pattern, `OnboardingView.swift:84-106`). Any town/postcode now works. `TownPicker`/`presetTowns` are dead code (left in place, reported). **Coord threaded (this session):** added `chosenCoord` state; `onPick` stores both `chosenCoord` + `chosenLabel`; `NearbyStep(coord = chosenCoord ?: loc, …)` — iOS `focusCoord = chosenCoord ?? loc` precedence (a picked town wins over GPS; picking doesn't clear the GPS fix, just overrides). `NearbyStep` now takes the coord param and drops its internal `loc` read, so a town picked without GPS centres on `nearbyWithImages(coord,100)` instead of falling to "Popular farms". Pass-A pipeline (split/6+8/shuffle/10/teasers/C4) untouched — only the coordinate source changed; headline gate moved `loc==null`→`coord==null`. (2) ~~**S2.5 nearby**~~ **FIXED (Pass A, this session)** — `NearbyStep` now ports iOS's pipeline 1:1: pool = `nearbyWithImages(loc,100) ?: feedPicks(null,30)`; split described(has-gallery)/plain → `take(6)+take(8)` → **shuffle** → `take(10)`; **batch `FarmDetailApi.teasers`** for cover-only cards; `images` = gallery-else-cover; `teaser` = featuredTeasers ?? extraTeasers; headline count = **pool `base.size`** (was `shown.size`); loading = 3× `SkeletonBox`; rendered as **C4 `MultiImageFarmCard`** (reused as-is). Keyed on pins/galleriesLoaded/coord (iOS buildKey); calls `loadGalleriesIfNeeded()`. Core deps all present (no patch). **Dead code:** the old `NearbyCard` composable (Pass A) + `TownPicker`/`presetTowns` (Pass B) now have zero callers (left in place, not deleted). **Remaining gap:** the picked-town coordinate isn't threaded to `NearbyStep` (see S2.3 note above) — an S2.5 change, out of scope. **All 7 steps built + faithful; device-gated.** Not VERIFIED. **Reads:** FarmsStore, LocationManager. **Writes:** on finish sets `farms.selectedCategories`, and (if applyPrefs) `filterVerified/OpenToday/HasPhotos/Zelfpluk`; RootView sets `didFinishOnboarding`. **Steps enum:** welcome, personalize, location, details, nearby, notify, done.

**Container layout:**
| Order | Element | Spec | Status |
|-------|---------|------|--------|
| 1 | Full-bleed bg | welcome → `Image("WelcomeFarmShop")` scaledToFill + gradient overlay (black .72 bottom → .30 → .55 top, bottom→top); else `cream`; animated easeInOut 0.45 on step==welcome | IN_PROGRESS |
| 2 | Header slot | reserved; opacity 1 when step ∉ {welcome,done}; pad h20 t8 | IN_PROGRESS |
| 2a | Back button | `chevron.left` system(15,.semibold) farmGreen, 44×44, `creamCard` circle; opacity/disabled when !canGoBack | IN_PROGRESS |
| 2b | ProgressBar | Capsule track `farmGreen@22%`, fill `farmGreen` width max(12, w·fraction), height 5, spring(0.5); `fraction = step.rawValue / (count−2)` | IN_PROGRESS |
| 3 | Sliding track | GeometryReader HStack of 7 step pages each `frame(w,h)`; `.offset(x: −step·w)`; spring(0.5 bounce 0.14) on step; clipped | IN_PROGRESS |

**Transitions:** spring slide between steps. **advance/goBack** with `Haptics.tap()`. **Sheets:** `showLogin`→AuthView; `showPlaceSearch`→PlaceSearchSheet [large]. **Edge:** focusCoord = chosenCoord ?? GPS.

#### S2.1 · Welcome step
| Order | Element | Spec | Status |
|-------|---------|------|--------|
| 1 | Spacer | flexible top | IN_PROGRESS |
| 2 | Logo | `Image("FarmsyLogo")` 74×74, pad 16, `cream` circle, shadow black .25 r10 y4 | IN_PROGRESS |
| 3 | "Farmsy" | `displayItalic(52,.medium)` white | IN_PROGRESS |
| 4 | Tagline | "Local food, close to you." `display(26,.medium)` white, centered | IN_PROGRESS |
| 5 | Body | "Find farm shops, pick-your-own farms and honest food straight from the people who grow it." `geist(16)` white@90%, centered, pad h12 | IN_PROGRESS |
| 6 | Spacer | flexible | IN_PROGRESS |
| 7 | "Log in / Sign up" | `geist(18,.semibold)` farmGreen on **white** fill, radius 16, vpad 17 → `onLogin` (showLogin) | IN_PROGRESS |
| 8 | "Skip for now" | `geist(17,.semibold)` white on white@14% fill, white@35% 1px stroke, radius 16, vpad 15 → `onSkip` (advance) | IN_PROGRESS |
| — | Container pad | h24, bottom 48 | IN_PROGRESS |

#### S2.2 · Personalize step (multi-select)
Options: produce, dairy, cheese, eggs, honey, meat, fish, wine (8). 2-col LazyVGrid spacing 12.
| Order | Element | Spec | Status |
|-------|---------|------|--------|
| 1 | Kicker | "Personalize" | IN_PROGRESS |
| 2 | DisplayTitle | "What are you *looking* for?" size 32 | IN_PROGRESS |
| 3 | Sub | "Pick a few — or none. You can change this anytime." `geist(15)` inkMuted centered | IN_PROGRESS |
| 4 | CategoryTile ×8 | HStack: emoji `geist(24)` + label `geist(16,.semibold)` (farmGreen when on else ink), lineLimit 1 minScale 0.8, Spacer, `checkmark.circle.fill`(18) farmGreen when on; pad v16 h14; bg radius16 fill `farmGreenSoft` when on else `creamCard`, stroke farmGreen(1.5) when on else hairline(1). Toggle set membership | IN_PROGRESS |
| 5 | Button | `selected.isEmpty ? "Skip" : "Continue"` PrimaryButtonStyle → advance | IN_PROGRESS |

#### S2.3 · Location step
| Order | Element | Spec | Status |
|-------|---------|------|--------|
| 1 | Kicker | "Location" | IN_PROGRESS |
| 2 | DisplayTitle | "Where are you *exploring* today?" size 30 | IN_PROGRESS |
| 3 | **RadarPulse** | 3 fixed rings (stroke farmGreen@22, w 58+i·44); 2 staggered pulses (stroke farmGreenMap animate 0.55→0, size 50→150, easeOut 2.4 repeatForever delay i·1.2); center pin `farmGreenMap` 48 circle + `location.fill`(19) white, shadow; height 170 | IN_PROGRESS |
| 4 | Row: Use my location | `rowLabel` filled: icon `location.fill` white in `farmGreenMap` 44 circle; title `geist(17,.semibold)`; chevron.right inkMuted; `creamCard` radius16 hairline → `onUseLocation` (locationManager.request) | IN_PROGRESS |
| 5 | Row: Search a town instead | same, not filled: icon `magnifyingglass` farmGreen in `farmGreenSoft` circle → `onSearch` (showPlaceSearch) | IN_PROGRESS (Pass B) — `OnboardingScreen.kt` LocationStep row → `onSearch` → `showPlaceSearch` → **S19 `PlaceSearchSheet` (Photon, reused as-is)**. Replaced the inline `TownPicker` (8 hardcoded chips). onPick sets `chosenLabel`; onLocate → `locationHelper.request()`. **`TownPicker`/`presetTowns` now dead code (left in place).** SF: `magnifyingglass`→Search (§7a) |
| 6 | Resolved label | if resolved: `checkmark.circle.fill` farmGreen + text `geist(15,.semibold)` ink; pad top 20 | IN_PROGRESS |
| 7 | Continue | PrimaryButtonStyle → advance | IN_PROGRESS |

#### S2.4 · Details step (optional prefs)
| Order | Element | Spec | Status |
|-------|---------|------|--------|
| 1 | Kicker | "Optional" | IN_PROGRESS |
| 2 | DisplayTitle | "Anything else we should *know*?" size 30 | IN_PROGRESS |
| 3 | Sub | "Fine-tune what shows up. All optional." `geist(15)` inkMuted centered | IN_PROGRESS |
| 4 | PrefRow ×4 | 🕒 "Open today"/"Only farms open right now"; 🧺 "Pick-your-own"/"Zelfpluk farms you can visit"; ✅ "Verified farms"/"Confirmed, up-to-date listings"; 📷 "Has photos"/"See the place before you go". Row: emoji `geist(22)` + title `geist(16,.semibold)` + subtitle `geist(13)` inkMuted; trailing **toggle** (track 46×28 `farmGreenMap` on / `#E5E4DF` off, white knob 22 offset ±9, spring 0.25); `creamCard` radius16 hairline | IN_PROGRESS |
| 5 | "Show me farms" | PrimaryButtonStyle → applyPrefs=true, advance | IN_PROGRESS |
| 6 | "I'll explore on my own" | `geist(16,.semibold)` inkMuted → applyPrefs=false, advance | IN_PROGRESS |

#### S2.5 · Nearby step (farm shelf)
Pool: `nearbyWithImages(coord,100km)` else `feedPicks(nil,30)`. Split described(teaser)/plain, prefix 6+8, shuffled, prefix 10. Fetch batch teasers for shown.
| Order | Element | Spec | Status |
|-------|---------|------|--------|
| 1 | Kicker | "Great choice" | IN_PROGRESS |
| 2 | DisplayTitle | "Here are farms *near* you" size 30 | IN_PROGRESS |
| 3 | Headline | built? (coord? "N farms within 100 km of {label\|you}" : "Popular farm shops") : "Finding farms near you…" — `geist(15,.semibold)` inkMuted centered | IN_PROGRESS |
| 4 | Loading | 3× SkeletonBox h180 while !built | IN_PROGRESS |
| 5 | MultiImageFarmCard ×≤10 | see C4 | IN_PROGRESS — `OnboardingScreen.kt` NearbyStep; renders C4 `MultiImageFarmCard` (reused) for `shown.take(10)` (Pass A). **Coord threaded (this session):** `NearbyStep(coord = chosenCoord ?: loc)` — a town picked in S2.3 now centres the pool via `nearbyWithImages(coord,100)`, iOS `focusCoord` precedence (pick wins over GPS) |
| 6 | "See all on map" | PrimaryButtonStyle → advance | IN_PROGRESS |

#### S2.6 · Notify step
| Order | Element | Spec | Status |
|-------|---------|------|--------|
| 1 | Spacer | top | IN_PROGRESS |
| 2 | Kicker | "Stay in the loop" | IN_PROGRESS |
| 3 | DisplayTitle | "Know when new farms appear *near you*" size 28 | IN_PROGRESS |
| 4 | Spacer | 60 | IN_PROGRESS |
| 5 | **RingingBell(76)** | see §1.3 | IN_PROGRESS |
| 6 | Spacer | flexible | IN_PROGRESS |
| 7 | "Turn on notifications" | PrimaryButtonStyle → request UN authorization [alert,badge,sound] → advance | IN_PROGRESS |
| 8 | "Maybe later" | `geist(16,.semibold)` inkMuted → advance | IN_PROGRESS |
| 9 | Footnote | "New farm shops join Farmsy every week." `geist(15).italic()` inkMuted | IN_PROGRESS |

#### S2.7 · Done step
| Order | Element | Spec | Status |
|-------|---------|------|--------|
| 1 | Spacer | top | IN_PROGRESS |
| 2 | Seal | `checkmark.seal.fill`(68) farmGreen, pad bottom 22 | IN_PROGRESS |
| 3 | Kicker | "Ready" | IN_PROGRESS |
| 4 | DisplayTitle | "You're all *set*" size 34 | IN_PROGRESS |
| 5 | DoneBullet ×3 | 🗺️ "Browse farm shops on the map"; ❤️ "Save the ones you want to visit"; 🧭 "Plan a trip across several farms". Row: emoji `geist(22)` in 44 `farmGreenSoft` circle + text `geist(16,.medium)` ink; pad top 30 | IN_PROGRESS |
| 6 | Spacer | flexible | IN_PROGRESS |
| 7 | "Start exploring" | PrimaryButtonStyle → finish() | IN_PROGRESS |

---

### S3 · MainView (map-first shell) — `Features/Main/MainView.swift`
**PORT STATUS: IN_PROGRESS** — `android/…/features/main/MainScreen.kt`. MainScreen owns the single shared map; farm detail + secondary screens present as **detented `BottomSheetScaffold` sheets OVER the live map** — the RootNav push overlay + `openPin` were deleted. **This session:** expanded detent now **declared** (`fillMaxHeight(0.92f)`, not emergent); **background interaction scoped** — enabled at the partial detent, blocked when Expanded via a touch-consuming body overlay (Auth stays modal in RootNav); `focusPin` fly + highlighted pin via MapScreen. **Background interaction: IN_PROGRESS — device-gated** (gesture: slow-drag the sheet up past the partial detent, then try to pan/tap the map behind — must be inert above the detent, live below). Mechanism confirmed by source: offset-gated blocker (`sheetState.requireOffset()` in a `derivedStateOf`) flips mid-drag above the partial detent, a faithful reproduction of iOS `upThrough: partial`. (A note cannot promote a row — the prior self-granted VERIFIED was removed from PORT_NOTES.) **Expanded detent per route (this session): FARM = `.large` via `expandedFraction` = `(screen − statusBar)/screen` clamped [0.92, 0.96] (inset-driven, matching iOS `.large` for the FARM card `MainView.swift:49`); Discover/Saved/Settings/Trips stay `0.92` (exact match to iOS `.fraction(0.92)`). FARM no longer settles ~1% short — it rests just below the status bar like iOS `.large`. Device-gated for the exact FARM top-gap.** The FARM card still lacks the `.height(180)` mini-detent (see OPEN below). Detent PORT NOTE → `PORT_NOTES.md`. **Pill shadow + icon size: BUILT this session** (exact `capsuleShadow`; icon 17.dp — notes deleted). **OPEN row (DEFERRED — does not count toward completion):** farm-card **180 mini-detent → OPEN, DEFERRED (fix: `AnchoredDraggable` 3-anchor sheet).** STOP-and-reported this session: the 3rd anchor for the FARM route alone requires replacing the single shared `BottomSheetScaffold` spine that all five routes sit on — its own session, not a contained change. **Discover routing: RESOLVED** — the Discover pill now routes to **WhatsNewSheet (S6)**, matching iOS (was DiscoverFeedScreen/S11). (S11 DiscoverFeedScreen remains in the tree, no longer wired to the pill.)

**"The map is the app."** No tab bar. **Reads:** SessionStore. **Writes:** selectedPin, showAuth, accountRoute, showWhatsNew, showTrips, flyTarget, detents. Installs `\.requestAuth = { showAuth = true }`. `.tint(farmGreen)`, `.ignoresSafeArea(.keyboard)`, bg cream.

| Order | Element | Spec | Status |
|-------|---------|------|--------|
| 1 | MapScreen (base) | `MapScreen(onOpenFarm:, focusPin: flyTarget)` full-bleed | IN_PROGRESS |
| 2 | Bottom floating panel | overlay(.bottom) pad h14 b6: HStack(4) of 4 panelItems in **white Capsule**, pad 6, shadow black .14 r12 y3 | IN_PROGRESS — `MainScreen.kt` pill built (Capsule, pad 6). **Shadow now EXACT (this session):** `capsuleShadow` modifier = `drawBehind` + `BlurMaskFilter(12.dp, NORMAL)` at colour black@.14 drawn at y-offset 3.dp — carries all three iOS params (colour + blur radius + downward offset) that `shadowElevation` couldn't. Note deleted; row carries the outcome. Device-gated only for the blur-sigma↔radius match. |
| 2a | panelItem Discover | `newspaper` system(17,.semibold) + "Discover" `geist(10,.semibold)`, farmGreenMap, vpad 8 → showWhatsNew | IN_PROGRESS |
| 2b | panelItem Saved | `heart` + "Saved" → requireAuth → accountRoute=.saved | IN_PROGRESS |
| 2c | panelItem Trips | `map` + "Trips" → requireAuth → showTrips | IN_PROGRESS |
| 2d | panelItem Settings | `gearshape` + "Settings" → requireAuth → accountRoute=.settings | IN_PROGRESS |

**Sheets & detents (all cornerRadius 28, drag indicator visible):**
- Farm card: `.sheet(selectedPin != nil)` → FarmDetailView `.id(osmId)` detents `[.height(180), .fraction(0.55), .large]` sel `$farmDetent`; bg interaction up through 0.55; content scrolls.
- WhatsNew: detents `[0.55, 0.92]`, bg interaction up through 0.55.
- accountRoute (.saved/.settings): detents `[0.55, 0.92]`.
- Trips: detents `[0.5, 0.92]` sel `$tripDetent`, bg interaction up through 0.5.
- Auth: `.sheet(showAuth)`.

**openFarm(pin):** farmDetent=.55, flyTarget=pin, selectedPin=pin (swaps in place, flies map). **requireAuth:** authed → action else showAuth.

---

### S4 · MapScreen — `Features/Map/MapScreen.swift`
**PORT STATUS: IN_PROGRESS** (trip route + focusPin rows) — `android/…/features/map/MapScreen.kt`. Added the `focusPin` param (fly + highlighted pin), TripStore reads, the trip route/stops on this single shared map, and the fit-to-trip effect. **Route widths: IN_PROGRESS — device-gated** (render check at device density; values confirmed by source: `8.dp.toPx()`/`5.dp.toPx()`, density-exact 8:5. Prior self-granted VERIFIED removed from PORT_NOTES). **focusPin fly: BUILT this session** — ported iOS `flyToFocus` (`MapScreen.swift:327`) exactly as a single code path: `delta = min(currentSpan, 0.15)`, centre shifted south by `delta*0.28`, shown span = `delta`, expressed as `newLatLngBounds` of a `delta`-sized box (the Google-Maps analog of iOS setting an `MKCoordinateRegion` — both fit-a-region with the same aspect adjustment). Retired the old `zoom<11→12`/fixed-0.045 heuristic. Note deleted; row carries the outcome. **Highlight pin static (no spring) — OPEN, DEFERRED** (fix: animated `Marker` icon via a per-scale bitmap swap, or a Compose-overlay marker springing its scale; iOS springs the highlighted pin). Kept DEFERRED: expressible, but the clean build (a Compose-overlay marker positioned via `Projection.toScreenLocation`) restructures how markers render — not contained to this pass. → `PORT_NOTES.md`.

**Reads:** FarmsStore (pins, filtered, aiIntent, aiCenter, searchText, loadError), LocationManager, TripStore (tracedLine, stopIds, fitToken). **Writes:** searchText, showFilters, aiSearching, camera, visibleRegion; calls applyAISearch/clearAISearch. **Camera init:** center (51.8, 4.7) span 3.4.

**Top overlay (VStack spacing 8, pad h14 t6):**
| Order | Element | Spec | Status |
|-------|---------|------|--------|
| 1 | searchRow (Capsule) | HStack(10): leading = spinner (aiSearching) OR `sparkles`(aiIntent, farmGreenMap) OR `magnifyingglass`(inkMuted) system(15); TextField "Search or ask — e.g. cheese near you" submitLabel .search onSubmit runSmartSearch; **filter button INSIDE** = `line.3.horizontal.decrease.circle{.fill}`(21) farmGreenMap → showFilters. Pad v13 h18, **white Capsule**, shadow black .12 r8 y2 | IN_PROGRESS |
| 2 | Locate button (beside) | `CircleMapButton("location.fill", size 44)` → locateNearMe | IN_PROGRESS |
| 3 | AI summary bar | if aiIntent≠nil: white radius18, shadow; `sparkles`(13) farmGreenMap + summary `geist(13)` ink + `xmark.circle.fill`(16) inkMuted → clearAISearch; below: horiz scroll of chips `geist(11,.semibold)` farmGreenDeep on `farmGreenMap@12%` Capsule | IN_PROGRESS |

**NO category pill. NO farms-count chip.** (Neither exists on iOS.)

**Map (`mapCard`):** `Map(position:$camera)` mapStyle standard excludingAll, ignoresSafeArea.
| Element | Spec | Status |
|---------|------|--------|
| UserAnnotation | device location dot | IN_PROGRESS |
| Clusters | grid-clustered; `isCluster` → ClusterBubble (tap zoomInto); else → FarmPinView (tap onOpenFarm) | IN_PROGRESS |
| Trip route | if tracedLine≥2: white casing (MapPolyline w8) aboveLabels + `routeColor #2563EB` line (solid w5 onRoads / dashed w4 [2,4]) aboveLabels | IN_PROGRESS — `MapScreen.kt` white casing Polyline zIndex1 + blue #2563EB zIndex2, dashed `Dash(22)/Gap(18)` when !onRoads; reads `trip.tracedLine()`. Origin = START of the line (azure marker **deleted**, matches iOS). **Widths: IN_PROGRESS — device-gated** — `routeCasingPx`/`routeLinePx` = `8.dp.toPx()`/`5.dp.toPx()` (exact physical size, 8:5 preserved at every density; render check device-gated). **aboveLabels: ACCEPTED_DIVERGENCE** — no overlay/label z-order API on Google Maps (PORT NOTE). |
| Trip stops | numbered `TripStopMarker` above route (tap onOpenFarm) | IN_PROGRESS — `MapScreen.kt` `tripStopBitmap(i+1)` markers zIndex3, tap→onOpenFarm; excluded from clustering; no origin pin |
| Tap gesture | simultaneous TapGesture → dismissKeyboard | IN_PROGRESS |
| onMapCameraChange(.onEnd) | set visibleRegion + dismissKeyboard | IN_PROGRESS |
| Center overlay | loadError → text + "Retry" button (farmGreenMap); else pins empty → ProgressView(large) farmGreenMap | IN_PROGRESS |

**Clustering algorithm:** grid `gridCellsAcross=10`; below span `declusterSpan=0.06` every farm individual; cell → cluster only if `count≥10` (2–9 drawn individually); bucket centroid positions marker; trip stops excluded from clustering. **zoomInto:** span/3.2 easeInOut 0.4.

**Sub-components:**
| Component | Spec | Status |
|-----------|------|--------|
| FarmPinView | `drop.fill` rotated 180° (38, or 50 highlighted → farmGreenDeep else category.color) + white circle 22/28 + emoji 12/15, offset y −5/−7, spring on highlight | IN_PROGRESS — parameterised `pinBitmap(drop, whiteCircle, emoji, …)`: normal `pinBitmap(38,22,12,cat.color)`, highlighted `pinBitmap(50,28,15,farmGreenDeep)` — **drop size now the literal manifest value (38/50)**. Highlighted drawn for `focusPin` on top, **suppressed when the farm is a trip stop** (one marker, no duplicate). PORT NOTE: static bitmaps (no spring); SF pt→px via fixed factors |
| ClusterBubble | count text `geist(15/13,.bold)` white in `farmGreenMap` circle (38/46/54 by <10/<100/else), white 2.5 stroke, shadow; "999+" cap | IN_PROGRESS |
| TripStopMarker | number `geist(14,.bold)` white in `farmGreen` 34 circle, white 2.5 stroke | IN_PROGRESS |
| CircleMapButton(Label) | `.white@94%` circle (44), glyph `#4B5563` system(size·0.42,.semibold), white@60 stroke, shadow black .22 r10 y3 | IN_PROGRESS |
| FilterChip (defined, unused by MainView flow) | icon+title pill, farmGreenMap fill when on | N/A |
| FarmCard (legacy, unused by MainView) | bottom card — MainView uses FarmDetailView instead | N/A |

**Actions:** runSmartSearch (≥2 chars, parse, applyAISearch), flyToAIPlace (span from radiusKm), flyToFocus (keep zoom, shift center south delta·0.28), locateNearMe (span 0.5), fitToTrip (frame route, center shifted north latPad·0.55). **Haptics** on pin/cluster/search/locate.

---

### S5 · FilterSheet — `Features/Map/MapScreen.swift`
**PORT STATUS: IN_PROGRESS** — `features/map/MapScreen.kt` `FilterSheet` (**REBUILT this session**). Was a chip layout (`FlowRow` of quick/axis chips) with **NO category selection**; now iOS's unified **`.tapCard` row list** (`MapScreen.swift:690`): "Filters" header + xmark → dismiss; **"All categories" row** (🍽️ inkMuted circle + pins.count Capsule + checkmark when `!anyFilterOn()` → `clearAllFilters()`); **10 category rows** (emoji in `cat.color` 34-circle → toggle `selectedCategories` with iOS add/remove: contains→remove else add); **5 quick-filter rows** (Verified/Automaat/Open today/Pick-your-own/Has photos, icon in #F3F4F6 circle); "TYPE OF PLACE" + 6 placeType rows; "HOW IT'S GROWN" + 4 method rows. Shared `FilterRow`/`FilterDivider`/`FilterSectionHeader` (iOS `row()`/`divider`/`sectionHeader`). Deleted the old `FilterChip`/`FilterGroupHeader`. Strings reused (`filter_*` already hold iOS's full labels). **Core gap handled without patching:** Android `FarmAxisValue` has **no `icon` field** (iOS has one); axis-row icons are mapped id→ImageVector at the UI layer (`axisIcon()`), core untouched. **SF→Material subs → §7a.** Not VERIFIED — device-gated: row taps + multi-select behavior.
**Presents:** `.sheet` [medium,large] from search bar filter icon. **Reads/Writes:** FarmsStore filters. Loads `loadFlagsIfNeeded` on task. Bg cream. One unified list, no card, `.tapCard` rows.

| Order | Element | Spec | Status |
|-------|---------|------|--------|
| 1 | Header | "Filters" `geist(18,.bold)` ink + `xmark`(14) in `#F3F4F6` 32 circle → dismiss; pad h16 t12 b6 | IN_PROGRESS |
| 2 | Row "All categories" | emoji 🍽️ in inkMuted circle, label, trailing = pins.count in `#F3F4F6` Capsule, checkmark when `!anyFilterOn` → clearAllFilters | IN_PROGRESS |
| 3 | divider | hairline 1px, vpad 4 | IN_PROGRESS |
| 4 | Category rows ×10 | emoji in `cat.color` circle (34), label `geist(15)` ink, checkmark(14) farmGreenMap when selected; toggle selectedCategories | IN_PROGRESS |
| 5 | divider | — | IN_PROGRESS |
| 6 | Quick filter rows ×5 | icon in `#F3F4F6` circle: `checkmark.seal` "Verified farm shops"; `bolt` "Open 24/7 (automaat)"; `clock` "Open today"; `leaf` "Pick your own"; `camera` "Has photos". Toggle respective flag | IN_PROGRESS |
| 7 | Section header "TYPE OF PLACE" | `geist(11,.semibold)` kerning 1.1 inkMuted uppercased | IN_PROGRESS |
| 8 | placeType rows ×6 | FarmAxis.placeTypes (icon+label), toggle selectedPlaceTypes | IN_PROGRESS |
| 9 | Section header "HOW IT'S GROWN" | same style | IN_PROGRESS |
| 10 | method rows ×4 | FarmAxis.methods, toggle selectedMethods | IN_PROGRESS |

**Row structure:** 34 circle (emoji→tint fill / icon→`#F3F4F6`) + label lineLimit1 + optional trailing Capsule + checkmark; pad h16 v11, `.tapCard`.

---

### S6 · WhatsNewSheet — `Features/Map/WhatsNewSheet.swift`
**PORT STATUS: IN_PROGRESS** — `android/…/features/whatsnew/WhatsNewSheet.kt`. Built this session: header (eyebrow + circular close), posts (loading skeletons / dashed-empty / PingCard list via `FarmContentApi.feedPosts`), "FEATURED FARMS" (skeletons until `galleriesLoaded`, then MultiImageFarmCard over `farms.featuredFarms.take(10)`), recommendation carousel. Wired: **MainScreen Discover pill → WhatsNewSheet** (was DiscoverFeedScreen), as a detented sheet over the shared map (existing sheet system). **S10 lightbox now wired:** a PingCard photo tap opens `ImageLightbox` via a `lightbox: LightboxSource?` state (eyebrow `lightbox_from_a_post`, title author, subtitle farm, postText body) — 1:1 with iOS `WhatsNewSheet.swift:59-64`. C4 featured-card photos are **not** tappable (iOS `FixedImageRow` with no `onTap`). **DEFERRED:** dashed empty-posts border (solid hairline for now; API: `drawBehind`+`dashPathEffect`).

**Presents:** `.sheet` [0.55,0.92] from Discover panel item. **Reads:** FarmsStore (featuredFarms, galleries, featuredTeasers). **Writes:** pings, lightbox. Opening a farm flies map + closes. Bg cream. Loads pings + `loadGalleriesIfNeeded` on task.

| Order | Element | Spec | Status |
|-------|---------|------|--------|
| 1 | Header | "WHAT'S NEW" `geist(11,.semibold)` kerning1.2 inkMuted + `xmark`(13) in `#F3F4F6` 32 circle → dismiss; pad h16 t16 b8 | NOT_STARTED |
| 2 | Pings loading | 2× SkeletonBox h150 while loadingPings | NOT_STARTED |
| 3 | Pings empty | `emptyPosts`: "No posts yet — check back soon." `geist(12)` inkMuted, dashed `#E5E7EB` radius16 | NOT_STARTED |
| 4 | PingCard ×N | see C3; onOpenFarm flies+opens; onOpenImage → LightboxSource (eyebrow "From a post", title author, subtitle farm, postText body) | IN_PROGRESS — `WhatsNewSheet.kt`; `onOpenImage = { idx -> lightbox = LightboxSource(ping.images, idx, eyebrow=`lightbox_from_a_post`, title=ping.authorName, subtitle=farm?.name, postText=ping.body) }` — 1:1 with iOS `WhatsNewSheet.swift:59-64`. String `lightbox_from_a_post` = "From a post"/"Uit een bericht"/"D'une publication"/"Aus einem Beitrag" (verified from iOS `Localizable.xcstrings`) |
| 5 | "FEATURED FARMS" header | `geist(11,.semibold)` kerning1.2 inkMuted, pad top 4 | NOT_STARTED |
| 6 | Featured loading | 3× SkeletonBox h180 while `!galleriesLoaded` | NOT_STARTED |
| 7 | MultiImageFarmCard ×≤10 | see C4 (featuredFarms prefix 10) | NOT_STARTED |
| 8 | TripRecommendations | see C1 | NOT_STARTED |
| — | Lightbox | `.fullScreenCover(item:$lightbox)` → ImageLightbox, clear bg | IN_PROGRESS — `WhatsNewSheet.kt`; `var lightbox by remember` state, presented as `lightbox?.let { ImageLightbox(source=it, onClose={lightbox=null}) }` after the Column (S10 is a `Dialog`, no layout impact). Only PingCard photos open it — C4 photos are not tappable on iOS |

---

### S7 · FarmDetailView — `Features/Detail/FarmDetailView.swift`
**PORT STATUS: IN_PROGRESS (all 3 passes built; FARM route now LIVE on `FarmDetailScreen7`).** The old `FarmDetailScreen.kt` has been DELETED and `MainScreen.kt:170` swapped. Rows remain IN_PROGRESS (never self-VERIFIED — a separate audit promotes). **`DetailSections` now renders C7 ExpandableText + S9 `FarmMemberSections` (built); the S9 anchor is consumed. C8 composers built and consumed by S9.** Historical note (pre-swap): the FARM route used to point at a pre-manifest OLD design — — `features/detail/FarmDetailScreen.kt` (single scrolling Column + `HorizontalPager` gallery/dots + `display(30.sp)` name + `cat.color@14%` chips). It is being replaced by the new manifest design, built **alongside** in a NEW file `features/detail/FarmDetailScreen7.kt` (route stays on the old one until the final pass). **Swap plan:** at Pass 3, change `MainScreen.kt:170` → `FarmDetailScreen7`, then delete the old file — **but only after S8 `LockedAccessView` is ported** (the old file holds the live paywall; **S8 is a known S7 dependency**). **Pass split:** P1 scaffold + rows 1-6 + footer + Loading (this session); P2 rows 7-9 + Locked body (lockedSections + lockedBlock blur) + loadTeaser/loadGallery; P3 Member body (detailSections = C7 `ExpandableText` + **S9 anchor** `FarmMemberSections`) + sheets + swap + delete. **S9 anchor** = inside `detailSections` after the description ExpandableText (iOS `:630-637`). A temporary `@Preview` (`FarmDetailScreen7DevPreview`) lives at the bottom of the new file, deleted at the swap. **C7 (this session): both gaps closed** — its clamp is now iOS's char-level binary search (`rememberTextMeasurer`), and expand/collapse animates `easeOut 0.2` (`animateContentSize` + `tween(200, EaseOut)`). See C7 row.
**Presents:** `.sheet` [180,0.55,large] on pin tap (card open to everyone; paid fields locked *inside*). **Reads:** SessionStore, FavoritesStore, FarmsStore, TripStore, requestAuth. **Writes:** detail, teaser, isLoading, isLocked, showClaim/Paywall/SignIn, lightbox, galleryImages. Bg cream. Header pinned, footer pinned, middle scrolls.

**pinnedHeader** (pad h14 t18 b4):
| Order | Element | Spec | Status |
|-------|---------|------|--------|
| 1 | Farm name | `geist(19,.bold)` ink lineLimit1 | IN_PROGRESS — `FarmDetailScreen7.kt` pinnedHeader; `geist(19,.bold)` ink maxLines1 ellipsis |
| 2 | Save circle | `heart{.fill}` (warnRed saved / `#6B7280`) 32 in `#F3F4F6` circle → saveTapped (paywall if locked) | IN_PROGRESS — `FarmDetailScreen7.kt` HeaderCircle; Favorite/FavoriteBorder(14) in 32 #F3F4F6, optimistic `favorites.toggle`. **UN-GATED (1:1 with iOS build 19):** saving is free now — the tap was `if (isLocked) showPaywall` (a feature→paywall path), now `if (signed out) requestAuth() else toggle`. Signed-out → sign-in; signed-in free → saves like a member. iOS shipped the identical un-gating in build 19 (`saveTapped` → `showSignIn`), so this is an ordinary 1:1 row. |
| 3 | Share | ShareLink `square.and.arrow.up` → `farmsy.app/map?id=` | IN_PROGRESS — `FarmDetailScreen7.kt`; `Icons.Share`(14) → `ACTION_SEND` `https://www.farmsy.app/map?id=<osmId>` |
| 4 | Close | `xmark` `#6B7280` 32 circle → dismiss | IN_PROGRESS — `FarmDetailScreen7.kt`; `Icons.Close`(14) #6B7280 in 32 #F3F4F6 → onBack |

**subHeader** (scrolls, pad h14):
| Order | Element | Spec | Status |
|-------|---------|------|--------|
| 5 | ratingRow | `star.fill`(13) star + `%.1f`(14,.semibold) ink + "(N)"(12) inkMuted, OR "No reviews yet"; tap → paywall if locked | IN_PROGRESS — `FarmDetailScreen7.kt`; `Star`(13) star + `%.1f` + `(reviewCount)` else `no_reviews_yet`; tap→paywall when locked. String `no_reviews_yet` added (nl/fr/de localized) |
| 6 | Location line | `mappin.and.ellipse`(12) inkMuted + "{city}, {NL\|BE}" `geist(14)` inkMuted | IN_PROGRESS — `FarmDetailScreen7.kt`; `Place`(12) inkMuted + `locationLineOf` (city, country→NL/BE) `geist(14)` inkMuted |
| 7 | badgeRow | horiz scroll: category chips (emoji+label `geist(11,.semibold)` white on `cat.color` Capsule, prefix4) + Verified (`checkmark.seal.fill` farmGreenMap outline capsule) + Open now (`#10B981` dot + text `#047857` on `#ECFDF5`) | IN_PROGRESS (P2) — `FarmDetailScreen7.kt` badgeRow; `horizontalScroll` Row spacedBy6; chips `${emoji} ${label}` `geist(11,.semibold)` white on `cat.color` CircleShape (take 4); Verified border-only `farmGreenMap@0.4` capsule; Open now `#10B981` 6dp dot + `#047857` on `#ECFDF5`. Strings `verified`/`open_now` added (nl/fr/de). **`checkmark.seal.fill` → Material `Verified`(10) (SF substitution)** |
| 8 | tripButton | loading→SkeletonBox h44; locked→dashed `#9CA3AF` "Plan a trip with Farmsy Pro" `lock.fill` → gateLocked; member→"Add to trip"/"In your trip" (`plus`/`checkmark`, farmGreenMap filled when in), toggle | IN_PROGRESS (P2) — `FarmDetailScreen7.kt` TripButton; loading (`isLoading && detail==null && teaser==null`)→`SkeletonBox(16)` h44; locked→`drawBehind` dashed 1.5dp `#9CA3AF` (dash 5/5) white fill + `Lock`(13) + `plan_trip_pro` `#6B7280` → gateLocked; member→`inTrip` filled `farmGreenMap`/white else `farmGreen` 1.5 border, `Check`/`Add`(13) + `in_your_trip`/`add_to_trip`, `trip.toggle`. Both branches light haptic (iOS `Haptics.tap()`). Strings `in_your_trip` added, `add_to_trip` reused. **`plus`/`checkmark` → Material `Add`/`Check`(13) (SF substitution).** **UN-GATED (1:1 with iOS build 19):** trips are free now — the `isLocked ->` dashed "Plan a trip with Farmsy Pro" prompt + its shut `Lock` were **removed** (the padlock disappears). Everyone sees add/remove; a signed-out tap → `requestAuth()` (sign-in). iOS shipped the same tripButton collapse in build 19 (dropped the `else if isLocked` Pro prompt), so this is an ordinary 1:1 row. `plan_trip_pro` string **deleted this session** (0 refs, all locales). |
| 9 | photoStrip | !photosReady→skeleton (1 big 160 + 2×76); empty→gradient tile + emoji(56); 1 photo→single 160; ≥2→big 160 + rail(76,76 with "+N" overlay on 3rd) | IN_PROGRESS (P2) — `FarmDetailScreen7.kt` PhotoStrip; `!photosReady`→`SkeletonBox(16)` big weight1 h160 + rail (84 wide) 2× `SkeletonBox` h76; empty→`Brush.linearGradient(farmGreen@0.85→farmGreenDeep)` RR16 h160 + `primaryCategory.emoji` `geist(56)`; 1→`PhotoTile` r16 h160; ≥2→big `PhotoTile` r16 weight1 h160 + rail `PhotoTile` r12 h76 + third `PhotoTile` r12 `+N` (>3) / `#F3F4F6` placeholder if exactly 2. `PhotoTile` = `#F3F4F6` base + `SubcomposeAsyncImage` Crop (loading/error→`SkeletonBox`) clipped + optional black@0.6 `+N` `geist(14,.bold)` + `tapCard`. Taps open **S10 ImageLightbox** (`LightboxSource` eyebrow `lightbox_farm_photo`, title `pin.name`); string added (nl/fr/de). `stripImagesOf`: members' images else gallery, cover-first, de-duped |

**Body (member vs locked):**
| State | Content | Status |
|-------|---------|--------|
| Loading | cardSkeleton (3 grey bars) | IN_PROGRESS — `FarmDetailScreen7.kt` CardSkeleton; 3× `#ECEBE8` bars h12 r6 (trailing 0/60/140), pad top 8 |
| Locked | lockedSections: teaser (`geist(15)` + "… View more" farmGreen if truncated → gateLocked) + **lockedBlock** | IN_PROGRESS (P2) — `FarmDetailScreen7.kt` LockedBody; teaser present→`Text(teaser.text + " …" if truncated)` `geist(15)` lineHeight 22.5 Trim.Both ink, + `view_more` `geist(14,.semibold)` farmGreen (if truncated) → gateLocked (NOT the inline-expand C7 — iOS uses a plain clamp + a paywall button here). String `view_more` added (nl/fr/de). Body gate fixed to `isLoading && detail==null && teaser==null` (iOS `:47`) |
| Member | detailSections: ExpandableText(description) + FarmMemberSections (S9) | IN_PROGRESS (P3) — `FarmDetailScreen7.kt` `DetailSections`; Column spacedBy20: `detail.description` (non-empty) → **C7 `ExpandableText`** (consumed), then the **S9 anchor** (a marked comment — `FarmMemberSections` stays NOT_STARTED, NOT stubbed). C7's two former gaps are now closed this session (char-level binary-search clamp; easeOut(0.2) expand/collapse) — see C7 row |
| Load error | **Android-only; no iOS counterpart.** signed-in transient fetch failure → error + Retry, never a lock (sign-up-wall contract, Aviah 2026-08-30) | IN_PROGRESS — `FarmDetailScreen7.kt` `LoadError`; `something_went_wrong_please_try_again` `geist(14,.medium)` inkMuted center + `retry` `geist(15,.semibold)` farmGreenMap → `reload()`. Reuses the app's MapScreen error/retry shape. `reload()` `:183` was `if(hasFullAccess) isLocked=false else isLocked=true` → now `loadFailed=true` for any signed-in transient failure (the paid distinction is moot; the branch is only reachable signed-in). iOS `reload()` has no such branch — it locks on any non-member failure |

**lockedBlock** (the "blur" — S7's signature): ZStack: `lockedBarsBackground` (grey `inkMuted@14%` bars top [.78,.95,.6,.88] + Spacer60 + bottom [.7,.9,.5]) **`.blur(radius:7).opacity(0.6)`** + centered VStack: `lock.fill`(20) farmGreen in 48 `farmGreen@10%` circle, "Farm details are for members" `geist(16,.bold)`, "Address, phone, website and what this farm sells." `geist(14)` inkMuted, "See full details →" white on `farmGreenMap` radius16 → gateLocked. minHeight 300.
**IN_PROGRESS (P2)** — `FarmDetailScreen7.kt` LockedBlock + LockedBarsBackground + Bars: `Box heightIn(min=300)` center; bars `BoxWithConstraints` width-fractions (top 4 / weighted spacer min60 / bottom 3), `inkMuted@0.14` h13 r6, `.blur(7.dp).alpha(0.6)`; ask `Lock`(20) farmGreen in 48 `farmGreenSoft` circle + `farm_details_members` `geist(16,.bold)` ink center + `farm_details_members_sub` `geist(14)` lineHeight 20.2 Trim.Both inkMuted center + button (`see_full_details` `geist(15,.semibold)` white + `ArrowForward`(13) on `farmGreenMap` RR16 pad v14 → gateLocked, light haptic). Strings `farm_details_members`/`_sub`/`see_full_details` added (nl/fr/de). **DISPUTED:** manifest says string `"See full details →"` (en-only); iOS actually renders `Text("See full details")` (localized nl/fr/de) **+ a separate `arrow.right` icon** → used the localized `see_full_details` + `ArrowForward` icon, not the en-only "→" string. **`arrow.right` → Material AutoMirrored `ArrowForward`(13) (SF substitution).** **`.blur(7)` → PORT NOTE (DEFERRED for <31; corrected from a stale "ACCEPTED_DIVERGENCE" annotation — PORT_NOTES already classifies it DEFERRED because `ScriptIntrinsicBlur` (API 17–30) can blur the static faux-bar bitmap, so an alternative exists):** Compose `Modifier.blur` needs RenderEffect (API 31+); on API 26–30 it is a no-op and the faint bars render sharp at 0.6 alpha (built via the real API on 31+). **Reachability (this session):** the lockedBlock is still REACHABLE — it renders on the S7 signed-OUT path (`reload()` → no token → `isLocked=true` → `LockedBody` → `LockedBlock`, `FarmDetailScreen7.kt:176/367/707`), which is independent of S8 `LockedAccessView`. The "membership-entry-only, unreachable" status applies to S8/`LockedAccessView`, NOT to this S7 sign-up-wall block. So the <31 blur gap is live, not moot → stays DEFERRED, OPEN (not built here — `ScriptIntrinsicBlur` is deprecated/heavy and the faux bars degrade gracefully).
**CONTRACT CHANGE — SIGN-UP WALL (now 1:1 with iOS build 19). Original source of truth for this row was `farmsy-web` `origin/main` `messages/*.json` `map.lockedTitle/lockedBody/lockedCta`, ported ahead of iOS; iOS shipped the same copy + open padlock in build 19, so this is no longer "iOS behind" — it is an ordinary 1:1 row.** The web dropped the paid check on `GET /api/farm/[osmId]` + `/api/farms/contact` (403 gone; 401 = no session); details are now a sign-up wall, not a paywall. Applied (Android) in the earlier session: (a) copy → new keys `signup_wall_title` "See this farm, free" / `signup_wall_body` "Address, phone, opening times and what they sell. One free account opens every farm on the map." / `signup_wall_cta` "Create a free account" (nl/fr/de added from web; **ES informational — Android has no values-es**). Old `farm_details_members`/`_sub`/`see_full_details` keys now **unused** (0 code refs; left in place). (b) **padlock → OPEN** (`lock.fill`→Material `LockOpen`; web uses lucide `Unlock` — a shut lock beside "free" reads as a catch). iOS shipped the same open padlock (`lock.open.fill`) in build 19, so this is now direct iOS parity, not a web-only divergence.

**footer** (pinned, pad h14 v12, cream + top hairline):
| Element | Spec | Status |
|---------|------|--------|
| Directions | `location.fill`/`lock.fill` outline → openDirections (MKMapItem) / gateLocked | IN_PROGRESS — `FarmDetailScreen7.kt` FarmDetailFooter; **always `NearMe`(14)** (the shut `Lock` variant removed — directions are free-with-account) → `geo:` intent when signed in, else `gateLocked → requestAuth()` (sign-in). **UN-GATED (1:1 with iOS build 19 — iOS footer is now always `location.fill`, no lock variant).** **NearMe = Material stand-in for SF `location.fill`** |
| Call/Website | `phone.fill`/`globe` filled farmGreenMap → tel:/https: | IN_PROGRESS — `FarmDetailScreen7.kt`; `Phone`/`Public`(14) filled farmGreenMap → `tel:` / `https:` |

**Sheets:** showClaim→SafariView(`/claim/<osmId>`); showSignIn→AuthView; showPaywall→LockedAccessView; lightbox→ImageLightbox (clear bg, pops from center via disabled transaction). **onChange hasFullAccess:** granted+locked → dismiss paywall + reload. **Reused:** ActionButton, InfoRow, SocialChip, ExpandableText (C7).
**IN_PROGRESS (P3)** — `FarmDetailScreen7.kt`: **showPaywall** → S8 `LockedAccessView` in a `ModalBottomSheet(skipPartiallyExpanded, cream)` over the detented sheet (iOS `.sheet`); onClaim → `showPaywall=false; openClaim(context,pin)`; onRecheck → `reload()`. **claim → WEB** (`openClaim`): `Intent.ACTION_VIEW https://www.farmsy.app/claim/<encoded osmId>` — matching iOS (`FarmDetailView.swift:498-500` builds that URL, presented via SafariView). **Corrected S9 session**: was `showClaim → native ClaimSheet` (Pass 3); the `showClaim` state + `ClaimSheet` reference are removed, both the paywall onClaim and the S9 claimBlock now use the web route. **DISPUTED (mechanism): iOS SafariView = SFSafariViewController (in-app browser); the manifest planned Chrome Custom Tabs (:106/:238), which needs the `androidx.browser` dependency (NOT present → a build-config decision). Used plain `ACTION_VIEW` instead — consistent with every other web link in the app (Settings/Trips) and dependency-free. Custom Tabs remains the closer analog if the user adds `androidx.browser`.** **signed-out gate** → global `requestAuth()` (`LocalRequestAuth`, RootNav → one app-wide `AuthSheet`). **DISPUTED (architecture): iOS uses a local `.sheet(showSignIn){AuthView()}`; Android's app-wide pattern is the global `LocalRequestAuth`. `showSignIn` state removed; routed to `requestAuth()`.** **onChange hasFullAccess** built. lightbox → S10 (P2). **Route swapped: `MainScreen.kt:170` → `FarmDetailScreen7`; old `FarmDetailScreen.kt` DELETED; temp dev preview removed.** **S21 note: the native `ClaimSheet` (`features/claim/ClaimSheet.kt`) now has ZERO live callers** (only its own definition) — the user may mark S21 N/A.

---

### S8 · LockedAccessView (paywall) — `Features/Detail/FarmDetailView.swift`
**PORT STATUS: IN_PROGRESS — MEMBERSHIP-ENTRY-ONLY; currently unreachable on Android (1:1 with iOS build 19).** After the un-gating, `LockedAccessView` must not be reached from any FEATURE path — and isn't: every `showPaywall = true` setter was removed (save/rating/directions/tripButton now route to `requestAuth()` sign-in, not the paywall). iOS build 19 did the same (its `LockedAccessView` is likewise dormant — no feature path sets `showPaywall`), so this matches iOS, not just the web. The `showPaywall` state + `ModalBottomSheet{ LockedAccessView }` presentation remain as **dormant scaffolding** for a future membership entry point. **Android currently has NO in-app membership-purchase entry** — Settings' membership section manages an existing subscription via the **web billing URL** (`manage_subscription`), not this composable — so `LockedAccessView` has zero reachable callers today. Kept per intent (a future Pro-filters upsell would set `showPaywall = true` to re-wire it). The component + its S8 rows below are unchanged.
**Wired (dormant): presented from `FarmDetailScreen7`'s `showPaywall` in a `ModalBottomSheet`** (onClaim → web claim route `openClaim`, onRecheck → reload). (History: the old `FarmDetailScreen.kt` held a file-private `LockedAccessView` of an **outdated design** — no stat row, no emoji grid, no expired variants, no claim icon, older strings; that file is now deleted.) Reused shared components: `Kicker`, `DisplayTitle`, `card()`, `FitText` (all match current iOS). **NOT reused: shared `PlanCard`** (Components.kt) — it is the OLDER shape (radius22 + `farmGreen`); current iOS PlanButton is radius16 + `farmGreenMap`, so S8 has its own faithful private `PlanButton`.
**Presents:** `.sheet` from detail gate. **Reads:** FarmsStore, PurchaseStore, SessionStore. **Writes:** isChecking. Loads offering on task. ScrollView.

| Order | Element | Spec | Status |
|-------|---------|------|--------|
| 1 | Kicker | isExpired ? "Welcome back" : "Members only" | IN_PROGRESS — `LockedAccessView.kt`; `Kicker(welcome_back / members_only)` per `isExpired`. Strings reused (all 4 locales) |
| 2 | DisplayTitle | "Unlock every farm's *full story*" size 32 | IN_PROGRESS — `LockedAccessView.kt`; `DisplayTitle(unlock_every_farm_s + full_story, 32.sp)`. Strings reused |
| 3 | Stat row | 3 StatTiles: pins.count+ "farm shops" · "10" "categories" · "NL + BE" "coverage"; dividers h40; `.card(14)` | IN_PROGRESS — `LockedAccessView.kt`; Row `.card(14)` of 3 `StatTile` (value `geist(22,.bold)` farmGreen via `FitText`, caption `geist(13)` inkMuted) + `StatDivider` (1×40 hairline). Value = `stat_thousands` when `pins` empty else `"%,d+".format(pins.size)`. Captions `farm_shops`(reused)/`categories`/`coverage` (new, 4 locales). **`StatTile` is S8-only in iOS → built as a private S8 helper, NOT a core component** |
| 4 | Emoji grid | 6-col LazyVGrid of 12 emoji `geist(28)` | IN_PROGRESS — `LockedAccessView.kt`; 12 emoji `geist(28)` as 2 Rows of 6 (weight1 each) inside the outer verticalScroll. **DISPUTED (layout, not visual): a `LazyVGrid` nested in a scroll can't measure; iOS's `LazyVGrid` of a fixed 12 is a static 6-col grid, so a chunked static Row grid is the correct Compose equivalent — same rendered result, no nested-scroll crash** |
| 5 | Lock card | `lock.fill`(34) farmGreen + "Unlock every farm"/"Your membership has expired" `geist(19,.bold)` + body `geist(15)` inkMuted; `.card(22)` | IN_PROGRESS — `LockedAccessView.kt` `.card(22)`; `Lock`(34) farmGreen + title `unlock_every_farm`/`membership_expired_title` `geist(19,.bold)` ink center + body `lock_body_normal`/`lock_body_expired` (arg `pin.name`) `geist(15)` lineHeight 21.5 Trim.Both inkMuted center. Title + both bodies NEW (4 locales) |
| 6 | Error | purchaseError `geist(14,.medium)` warnRed | IN_PROGRESS — `LockedAccessView.kt`; `purchaseError?.let { Text(geist(14,.medium) warnRed center) }` |
| 7 | Purchase area | isPurchasing/checking→Spinner; productsUnavailable→"Memberships can't be loaded" + "Try again"(force load); yearlyPrice nil→Spinner; else PlanButtons | IN_PROGRESS — `LockedAccessView.kt`; `isPurchasing\|\|isChecking`→`CircularProgressIndicator` farmGreen; `productsUnavailable` (=`didLoadOffering && yearly==null`)→`memberships_cant_load`(new) + `memberships_load_retry`(new) + `try_again`(reused, force-load); `yearlyPrice==null`→spinner; else PlanButtons |
| 7a | Yearly PlanButton | filled; label = "{N} days free" (if trial) else "Yearly"; detail = "{price} / year" or "then {price} / year"; → purchase yearly + awaitGrant | IN_PROGRESS — `LockedAccessView.kt` `PlanButton(filled=true)`; label `free_trial_days_arg`/`plan_yearly`, detail `then_price_per_year_arg`/`price_per_year_arg`; → `purchases.purchase(activity, yearlyPkg, userId)` then `successHaptic()` + `awaitGrant`. Strings reused. **Success haptic:** iOS `Haptics.success()` → `View.performHapticFeedback(HapticFeedbackConstants.CONFIRM)` on API 30+, light tick below (Compose `HapticFeedbackType` has no success constant; the platform View does — same route SplashScreen uses). Built, not deferred |
| 7b | Lifetime PlanButton | outlined; "Lifetime" / "{price} · One payment, yours forever"; → purchase lifetime | IN_PROGRESS — `LockedAccessView.kt` `PlanButton(filled=false)`; `plan_lifetime` + `"{price} · " + one_payment_yours_forever`; → purchase lifetimePkg then `successHaptic()` + `awaitGrant`. Strings reused. Same success haptic as 7a |
| 8 | Trial terms | if trial: "Free for {days} days, then {price} per year. Cancel anytime in Settings." `geist(12)` inkMuted | IN_PROGRESS — `LockedAccessView.kt`; if `trialDays!=null && yearlyPrice!=null` → `trial_terms_arg` `geist(12)` inkMuted center. **DISPUTED: string ends "Cancel anytime in Google Play." not iOS's "…in Settings." — the reused Android `trial_terms_arg` is deliberately platform-correct (Play manages Android subs); kept as-is** |
| 9 | Restore | "Restore purchases" `geist(14,.medium)` inkMuted → restore + awaitGrant | IN_PROGRESS — `LockedAccessView.kt`; `restore_purchases` `geist(14,.medium)` inkMuted no-ripple tap → `purchases.restore()` then `awaitGrant`. String reused |
| 10 | Claim | `checkmark.seal` + "Is {name} yours? Claim it" inkMuted → onClaim | IN_PROGRESS — `LockedAccessView.kt`; Row `Verified`(13, Outlined) inkMuted + `is_arg_yours_claim_it`(reused, arg `pin.name`) `geist(14,.semibold)` inkMuted → onClaim, pad bottom 26. **`checkmark.seal` → Material Outlined `Verified`(13) (SF substitution)** |

**PlanButton:** VStack label (`geist(17,.semibold)` minScale0.7) + detail (`geist(14)`); filled `farmGreenMap` white / outlined white `farmGreen@45` stroke; radius16 vpad16. **awaitGrant:** poll refreshProfile ×12 @1.5s until hasFullAccess. **isExpired:** canceled/expired past end. **Android PlanButton:** private S8 helper (Components.kt `PlanCard` is the older radius22/farmGreen shape, left for the old file); label falls back to `become_a_member` when `detail==null`, both lines `FitText` (iOS `minimumScaleFactor(0.7)`), light tap haptic on press.

---

### S9 · FarmMemberSections — `Features/Detail/FarmMemberSections.swift`
**PORT STATUS: IN_PROGRESS** — built in `features/detail/FarmMemberSections.kt` (public `FarmMemberSections(pin, detail, onClaim)`), rendered into `FarmDetailScreen7`'s `DetailSections` after the C7 ExpandableText (S9 anchor consumed). Consumes the C8 composers (PostComposer in whatsNew, ReviewComposer in reviewsSection) — not rebuilt. Loaders: reviews on one `LaunchedEffect(osmId)`, posts+likedIds on `LaunchedEffect(osmId, uid)` (iOS two `.task`s). **Reads:** SessionStore. **Writes:** reviews, posts, likedIds, lightbox. Strings added (all nl/fr/de localized): `section_people_saying`, `reviews_be_first`, `details`, `hours`, `organic_yes`, `section_whats_new`, `posts_empty_today`, `reviews`, `reviews_none_be_first`, `claim_is_your_farm`, `claim_fine_print`, `report_incorrect_info`, `post_report`, `post_reported`; **reused** `address`/`phone`/`website`/`email`/`produce`/`organic`/`lightbox_from_a_post`/`claim_this_farm`.

| Order | Element | Spec | Status |
|-------|---------|------|--------|
| 1 | reviewsSummary | header "What people are saying" `geist(17,.bold)`; empty→dashedNote "Be the first to review this farm."; else `star.fill`(15) star + `%.1f`(16,.bold) + "· N reviews"(14) inkMuted | IN_PROGRESS — `FarmMemberSections.kt`; `SectionHeader(section_people_saying)`; empty→`DashedNote(reviews_be_first)`; else `Star`(15) star + `%.1f`(16,.bold) ink + `reviews_count` plural (14) inkMuted. avg = mean of ratings. **DISPUTED (string): iOS hardcodes "review"/"reviews" as inline English literals (NOT in the catalog) — an iOS i18n gap; built as an Android `plurals reviews_count` in `values/` ONLY, so nl/fr/de fall back to English, matching iOS exactly.** |
| 2 | detailsList | header "Details"; InfoRow ×N (icon farmGreenMap + label `geist(13,.semibold)` inkMuted + value `geist(15)`; link rows green + `arrow.up.right`); `.card(6)`. Rows: Hours(clock, formatHours), Address(mappin), Phone(phone, tel:), Website(globe), Email(envelope, mailto:), Facebook/Instagram(link), Organic(leaf "Yes 🌱"), Produce(basket) | IN_PROGRESS — `FarmMemberSections.kt` `InfoRow` + `detailRowsOf`; creamCard RR16 pad6; link rows tap → `Intent.ACTION_VIEW` (iOS `UIApplication.open`), value farmGreenMap maxLines1 + `NorthEast`(12). `formatHours` splits `;`/newline. `webURL` = https/base logic. **SF substitutions:** clock→`Schedule`, mappin.and.ellipse→`Place`, phone→`Phone`, globe→`Public`, envelope→`Email`, link→`Link`, leaf→`Eco`, basket→`ShoppingBasket`, arrow.up.right→`NorthEast`. |
| 3 | whatsNew | header "What's new"; **PostComposer** (C8); empty→dashedNote "Nothing posted here today."; else FarmPostRow ×N | IN_PROGRESS — `FarmMemberSections.kt`; `SectionHeader(section_whats_new)` + `PostComposer(pin){reload()}`; empty→`DashedNote(posts_empty_today)`; else `FarmPostRow` ×N |
| 4 | reviewsSection | header "Reviews"; **ReviewComposer** (C8); empty→"No reviews yet. Be the first!"; else ReviewRow ×N | IN_PROGRESS — `FarmMemberSections.kt`; `SectionHeader(reviews)` + `ReviewComposer(pin, myReview){reload()}` (myReview = own row by uid); empty→`reviews_none_be_first` `geist(13)` inkMuted; else `ReviewRow` ×N |
| 5 | claimBlock | "Is this your farm?" `geist(16,.bold)` + "Claim this farm" (`shield` white on farmGreenMap radius16) → onClaim + fine print | IN_PROGRESS — `FarmMemberSections.kt`; `claim_is_your_farm` `geist(16,.bold)` + button (`Shield` outlined + `claim_this_farm` white on farmGreenMap RR16 v14) → onClaim (**web claim route**) + `claim_fine_print` `geist(12)` inkMuted center. **`shield` → Material Outlined `Shield`(15) (SF substitution).** |
| 6 | reportLink | `flag`(13) + "Report incorrect info" `geist(13,.medium)` inkMuted → farmsy.app/messages | IN_PROGRESS — `FarmMemberSections.kt`; `Flag`(13) outlined + `report_incorrect_info` `geist(13,.medium)` inkMuted → `Intent.ACTION_VIEW https://www.farmsy.app/messages`. **`flag` → Material Outlined `Flag` (SF substitution).** |

**FarmPostRow:** initials avatar (36, `farmGreen@12%`) + author `geist(14,.semibold)`; body `geist(14)`; FixedImageRow(images prefix3, h100, onTap→lightbox); like button (`heart{.fill}` + count, farmGreen when liked, optimistic) + report (`flag` "Report"/"Reported", once). `creamCard` radius16 hairline. **ReviewRow:** name + 5 stars(11) star; body; card. **openLightbox:** eyebrow "From a post", title author, subtitle farm.
**IN_PROGRESS** — `FarmMemberSections.kt` `FarmPostRow`: initials (2 caps, `?` fallback) in 36 `farmGreen@12%` circle + author `geist(14,.semibold)`; body `geist(14)`; **reused C5 `FixedImageRow`**(take3, h100, onTap→`openLightbox`); like Row (`Favorite`/`FavoriteBorder`(13) + `displayCount` when >0, farmGreen when liked, **optimistic** likedIds toggle then `toggleLike`) + report Row (`Flag`(12) + `post_report`/`post_reported`, local `reported` guard once → `reportPing` + success haptic); creamCard RR16 + hairline. **`heart.fill`/`heart` → `Favorite`/`FavoriteBorder`; `flag` → Outlined `Flag` (SF substitutions).** `ReviewRow`: name `geist(14,.semibold)` + 5 `Star`/`StarBorder`(11) star; body `geist(14)`; creamCard RR16 + hairline. `openLightbox` → S10 `ImageLightbox` (`LightboxSource` eyebrow `lightbox_from_a_post`, title author, subtitle pin.name) — **reused, not rebuilt**.

**ReviewComposer (C8):** "Leave a review"; 5 tappable stars(22) star; TextField "Share your experience… (optional)" lines2–4 white radius14; "Submit"(80w) farmGreenMap (0.4 when rating 0) → submitReview (upsert). Prefill from existing. Bg `#F7F6F2`. **PostComposer (C8):** TextField "What's new at {name}?" lines2–5; photo previews (56, xmark remove); PhotosPicker `photo.badge.plus` "Photo" (max3); char counter (280−count); "Post"(80w) `paperplane.fill` farmGreenMap (0.4 when empty) → createPost (JPEG transcode 0.8 ≤4.5MB); "Posted to your farm on the map." Bg white radius16 hairline.

**C8 · ReviewComposer + PostComposer — IN_PROGRESS.** Built standalone in `features/detail/Composers.kt` (public composables `ReviewComposer(pin, existing, onPosted)` / `PostComposer(pin, onPosted)`) for S9 to consume; **S9 stays NOT_STARTED — these are the components, not their wiring, so nothing references them yet.** iOS makes them `private struct`s inside FarmMemberSections; on Android they are public in this file. Strings added (all nl/fr/de localized): `review_leave`, `review_placeholder`, `review_submit`, `composer_photo`, `composer_post`, `composer_posted_note`, `composer_whats_new_at` (arg `%1$s` = pin.name).
- **ReviewComposer** (iOS `FarmMemberSections.swift:378`): Column `#F7F6F2` RR16 pad14 spacedBy10 → `review_leave` `geist(14,.semibold)` ink; Row spacedBy6 of 5 stars(22) `star` colour, tap→light-haptic + set rating; `ComposerField` (BasicTextField) `review_placeholder` `geist(15)` minLines2 maxLines4, white RR14 + hairline pad12; trailing `SubmitPill` (80w, farmGreenMap / 0.4 when rating 0, RR14 vpad11, spinner when posting) → `FarmContentApi.submitReview(osmId, uid, displayName, rating, body=reviewText.ifEmpty{null})` + success haptic + `onPosted()`, disabled when rating 0 / posting. Prefill from `existing` via `LaunchedEffect` (iOS onAppear). **`star.fill`/`star` → Material `Star`/`StarBorder`(22) (SF substitution).**
- **PostComposer** (iOS `:439`): Column white RR16 + hairline pad14 spacedBy10 → `ComposerField` `composer_whats_new_at`(pin.name) minLines2 maxLines5 (plain, no bg); photo previews Row (56 RR10 Crop, `Close`-in-black@50%-circle(18) top-end to remove); Row → picker button (`AddPhotoAlternate`(15) + `composer_photo` `geist(14,.medium)` ink) + counter `${280 - text.length}` `geist(13)` inkMuted + Spacer + `SubmitPill` (`Send`(13) + `composer_post`, active when `canPost`) → `FarmContentApi.createPost(osmId, uid, displayName, body=text.trim(), photos)` + success haptic + clear/`onPosted()`, warning haptic on failure; footer `composer_posted_note` `geist(11)` inkMuted. `canPost = text.trim().isNotEmpty() && length ≤ 280`. **SF substitutions:** `photo.badge.plus`→`AddPhotoAlternate`(15), `paperplane.fill`→AutoMirrored `Send`(13), `xmark.circle.fill`→`Close`(12) on a black@50% circle.
  - **Photo picker:** `ActivityResultContracts.PickMultipleVisualMedia(3)` (Android Photo Picker) — the exact 1:1 for iOS `PhotosPicker(maxSelectionCount:3, matching:.images)`. **Needs NO runtime permission and NO new dependency** (androidx.activity, already present; same construct `AddFarmSheet.kt:79` uses). No build-config change.
  - **JPEG transcode:** decode → `Bitmap.compress(JPEG, 80, …)` (= iOS quality 0.8) → **if ≤ 4.5MB keep, else DROP that photo** (skip it, keep the rest). Matches iOS exactly (iOS `:509-513`: the `if let jpeg…, jpeg.count <= 4_500_000` simply fails and the photo is not appended — **no downscale, no reject-all, no retry at lower quality**). NOTE: the existing `AddFarmSheet` transcode has **no** 4.5MB gate — this composer adds it per iOS, and does not reuse AddFarmSheet's helper.
  - **Haptics:** success → `View.performHapticFeedback(CONFIRM)` (API 30+, else VIRTUAL_KEY); warning → `REJECT` (API 30+, else light tick) — the S8-established route (Compose `HapticFeedbackType` has no success/warning constant).
- **Reused:** none of SkeletonBox/tapCard/ExpandableText — iOS doesn't use them in either composer, so neither does this (per instruction). `SubmitPill`/`ComposerField` are C8-local private helpers (iOS inlines both).

---

### S10 · ImageLightbox — `Features/Detail/ImageLightbox.swift`
**PORT STATUS: IN_PROGRESS** — `features/detail/ImageLightbox.kt` (this session). **Compose construct: `Dialog(usePlatformDefaultWidth=false, decorFitsSystemWindows=false)`** — the clear-bg, floats-above-everything equivalent of iOS `.fullScreenCover`; owns its own window so it needs NO MainScreen sheet scaffold (per instruction). Dialog dim cleared to 0 + transparent bg so only the white veil shows. `LightboxSource` data class co-located (row §3 below). OPEN/DEFERRED: veil blur, panel+arrow shadow r/offset (see PORT NOTES). **Wired into S6** — a PingCard photo tap presents it (`WhatsNewSheet.kt`); S7 FarmDetail will present it too when built.
**Presents:** `.fullScreenCover(item:)` clear bg; pops from center (scale+fade, not slide). **Reads:** LightboxSource. **Writes:** index, shown. Fixed frame (doesn't resize to photo).

| Order | Element | Spec | Status |
|-------|---------|------|--------|
| 1 | Veil | `Color.white@55%` + `.ultraThinMaterial` blur, ignoresSafeArea, opacity 0→1; contentShape Rectangle → tap closes | IN_PROGRESS — `features/detail/ImageLightbox.kt`; white@55% veil in a `Dialog(usePlatformDefaultWidth=false)`, dim cleared, `graphicsLayer` alpha 0→1, no-ripple `detectTapGestures` tap→close. **Blur → OPEN, DEFERRED** (`FLAG_BLUR_BEHIND`/`blurBehindRadius` 31+, see PORT NOTES) |
| 2 | panel | frame min(w−32,440)×min(h−64,620), opacity 0→1, scale 0.94→1, zIndex1; white radius24 + hairline + shadow black .18 r30 y12 | IN_PROGRESS — `Panel`; `BoxWithConstraints` size `minOf(maxW−32,440)×minOf(maxH−64,620)`, alpha 0→1 + scale 0.94→1 via graphicsLayer, zIndex1, white RR24 + hairline. Shadow **colour** matched (spot/ambient black@.18); **exact r30/y12 → OPEN, DEFERRED** (Modifier.shadow has no radius/offset, see PORT NOTES) |
| 2a | header | eyebrow `geist(11,.semibold)` kerning1.1 inkMuted uppercased; title `geist(14,.semibold)` ink; subtitle `geist(12)` farmGreenMap; counter "n / N" `geist(13,.semibold)` inkMuted (if many); close `xmark`(14) `#6B7280` 36 circle **[iOS close bg `#F3F4F6`; close fires `Haptics.tap()` — both were omitted from this row]** | IN_PROGRESS — `Header`; eyebrow `letterSpacing=1.1.sp` uppercased; title/subtitle/counter per spec; close `Icons.Close`(14) `#6B7280` in 36 `#F3F4F6` circle, fires `TextHandleMove` haptic (iOS `Haptics.tap()`, `:125`) |
| 2b | postText | if present: `geist(14)` ink lineLimit3, pad **[iOS also `.lineSpacing(2)` (`:83`) — omitted from this row]** | IN_PROGRESS — `Panel`; `geist(14)` ink maxLines3 pad h16 b12, `lineHeight=20.2sp` + `LineHeightStyle(Center,Both)` for iOS `.lineSpacing(2)` |
| 2c | picture | base `#F3F4F6` radius16 + AsyncImage overlay scaledToFill clipShape + **contentShape** (fixes overflow hit-test); left/right arrows (`chevron.left/right`(18) `#374151` in white@90 40 circle) when many; pad h16 b16 **[iOS arrows also have `shadow(black.15 r6 y2)` (`:194`) + fire `Haptics.tap()` (`:186`) — omitted from this row]** | IN_PROGRESS — `Picture`; base `#F3F4F6` RR16 + `SubcomposeAsyncImage` Crop, `Crossfade` on index (easeOut 240). Arrows `KeyboardArrowLeft/Right`(18) `#374151` in white@90 40 circle + shadow(spot black@.15)+haptic. **contentShape N/A on Compose** (layout bounds gate hit-testing — no overflow-hit bug to fix). Arrow-shadow exact r6/y2 shares the shadow DEFERRED |

**Animations:** appear easeOut 0.22; close easeIn 0.15 then onClose. **step(±1):** wraps both ends, easeOut 0.24. **Empty/error:** AsyncImage failure → `photo`(40) inkMuted; loading → ProgressView. **Android:** `animateFloatAsState` alpha/scale (220 EaseOut / 150 EaseIn); close = `shown=false` then `delay(150)`→onClose; step wraps `(i+d+n)%n`, `Crossfade` 240 EaseOut; loading→`CircularProgressIndicator`, error→`Icons.Photo`(40) inkMuted.

---

### S11 · DiscoverFeedView — `Features/Discover/DiscoverFeedView.swift`
**PORT STATUS: N/A.** iOS **never reaches `DiscoverFeedView`** — zero references anywhere in the iOS codebase; the Discover pill routes to `WhatsNewSheet` (S6) on both platforms. The Android `DiscoverFeedScreen.kt` was likewise unwired (zero callers) and was **DELETED this cleanup session** (its `RecommendationCarousel` (C1) lives separately in `discover/` and survives, still used by S12/S18/S6). No Android surface to build → N/A.
**Note (historical):** On phone the map's Discover panel opens WhatsNewSheet; this standalone feed view exists and shares components. **Reads:** FarmsStore, LocationManager, SessionStore, requestAuth. **Writes:** feed, pings, showAddFarm. Pull-to-refresh reshuffles + reloads pings.

| Order | Element | Spec | Status |
|-------|---------|------|--------|
| 1 | header | Kicker "Discover" + DisplayTitle "Farms worth a *detour*" size 30 | NOT_STARTED |
| 2 | whatsNewSection | if pings: "WHAT'S NEW" header + PingCard ×prefix8 | NOT_STARTED |
| 3 | addFarmBanner | `plus.circle.fill`(26) farmGreen + "Know a farm shop we're missing?" `geist(15,.bold)` + "Add it to the map for everyone" `geist(13)` inkMuted + chevron; `farmGreenSoft` radius16 → web submit | NOT_STARTED |
| 4 | DiscoverFeedCard ×N | see C2 | NOT_STARTED |
| 5 | emptyState | if feed empty & pins present: 🧺(44) + "Nothing to discover right now" `geist(16,.medium)` inkMuted | NOT_STARTED |
| 6 | loading | farms.isLoading → ProgressView("Loading farms…") | NOT_STARTED |

---

### S12 · SavedScreen — `Features/Saved/SavedScreen.swift`
**PORT STATUS: IN_PROGRESS** — `features/saved/SavedScreen.kt` (audit divergences fixed this session). **Fixed:** (1) row 1 now has the `xmark` close button (32/#F3F4F6 circle, light haptic → `onClose`, threaded from `MainScreen` as `{ route = null }` like WhatsNewSheet); (2) savedRow now uses `tapCard(excludeTopTrailing = 44.dp)` + `tapCard` on the X (was plain `clickable`); (3) emptyRow circle now dashed `#E5E7EB` via `drawBehind` `dashPathEffect([3,3])` 1.5dp (was solid `inkMuted@0.3`); (4) Sign-in fires a light haptic; guest button pad h60 (was h20, matches iOS). Not VERIFIED — device-gated: scroll-aware tap cancel, close dismiss.
**Presents:** `.sheet` [0.55,0.92] from Saved panel item. **Reads:** FarmsStore, FavoritesStore, LocationManager, SessionStore, requestAuth. `savedPins` = saved ∩ pins, sorted by distance. Bg cream.

| Order | Element | Spec | Status |
|-------|---------|------|--------|
| 1 | Header | "SAVED FARMS" `geist(11,.semibold)` kerning1.2 inkMuted + `xmark`(13) `#6B7280` 32 `#F3F4F6` circle → dismiss; pad h16 t16 b8 | IN_PROGRESS |
| 2a | Guest state | 🤍(54) + "Keep your favourites" `display(24)` + "Sign in to save farms and find them here on every device." `geist(15)` inkMuted centered + "Sign in" PrimaryButtonStyle → requestAuth | IN_PROGRESS |
| 2b | Signed-in list | Numbered slot list, `slots = max(8, count)`, in `creamCard` radius16 + hairline card | IN_PROGRESS |
| — savedRow | index `geist(13,.bold)` white in `farmGreenMap` 30 circle + name `geist(15,.semibold)` + city `geist(12)` inkMuted + `xmark`(13) inkMuted 32 (`.tapCard` remove); row `.tapCard(excludeTopTrailing 44)` → onOpenFarm; pad h12 v11 | IN_PROGRESS |
| — emptyRow | index inkMuted@60 in dashed `#E5E7EB` circle + "Tap a heart to save a farm" `geist(15)` inkMuted + "—" | IN_PROGRESS |
| — divider | between rows, leading 62 | IN_PROGRESS |
| 3 | Carousel | TripRecommendations (C1) below list | IN_PROGRESS |

---

### S13 · SettingsSheet — `Features/Settings/SettingsSheet.swift`
**PORT STATUS: IN_PROGRESS** — `features/settings/SettingsScreen.kt` (built by a prior session; audited + `onClose` fix this session). Account/guest card, MembershipSection, prefs (Language/Notifications/Refer/Contact), legal (Privacy/Terms), account (Sign out/Delete), version — all present and faithful. **Fixed this session:** (1) **row 1 header** was a `display(26)` centred "Settings" title with **no close** — now the "SETTINGS" eyebrow (geist(11,.semibold) kerning1.2 inkMuted) + `xmark` close (32/#F3F4F6, haptic) → new `onClose`, matching iOS + the sibling sheets; (2) **`onClose` threaded** through `SettingsScreen(onClose)` and the `MainScreen` call (`{ route = null }`), so **S15's Done now dismisses the sheet** (clear `deletedState` + `onClose`, matching iOS `dismiss()`), and **sign-out dismisses** after `signOut()` (iOS `SettingsSheet.swift:218`). **Version string FIXED this session:** row 7 was `"Farmsy for Android ${BuildConfig.VERSION_NAME}"` (a hardcoded Kotlin literal); now `stringResource(R.string.farmsy_for_android_arg, BuildConfig.VERSION_NAME)`, a localized `%1$s` format mirroring iOS's localized `Farmsy for iOS %@`. `farmsy_for_android_arg` added across all four locales (en/nl/fr/de). **SF subs (row 4-6):** globe→Language, bell.fill→Notifications, gift.fill→CardGiftcard, envelope.fill→Email, hand.raised.fill→PrivacyTip, doc.text.fill→Article, rectangle.portrait.and.arrow.right→(sign-out glyph), trash.fill→Delete — record in §7a. Not VERIFIED — device-gated: close/sign-out/delete dismiss.
**Presents:** `.sheet` [0.55,0.92] from Settings panel item. **Reads:** SessionStore, LanguageManager, requestAuth. **Writes:** showLanguage/SignOutConfirm/DeleteInfo, isDeleting, deleteFailed, deletedState. Refresh profile on task. Bg cream.

| Order | Element | Spec | Status |
|-------|---------|------|--------|
| 1 | Header | "SETTINGS" `geist(11,.semibold)` kerning1.2 inkMuted + `xmark` `#6B7280` 32 circle → dismiss; pad h16 t16 b10 | IN_PROGRESS |
| 2a | Account card (authed) | `FarmsyLogo`(42) + email `geist(15,.semibold)` lineLimit1 + plan "{Plan} plan"/"Farmsy account" `geist(13)` inkMuted + **subscription badge** (Member/Trial/Canceled/Expired/Free w/ color) `geist(12,.bold)` white Capsule; `.card()` | IN_PROGRESS |
| 2b | Guest card | `FarmsyLogo`(42) + "You're browsing as a guest" + "Sign in to save farms and see details" + "Sign in" (white on farmGreenMap Capsule) → requestAuth | IN_PROGRESS |
| 3 | MembershipSection (authed) | "Membership" label + card: lifetime → "You have Lifetime access" / "One payment, never expires. Nothing to manage."; hasAccess → "Your membership is ending"/"Your trial is active"/"You're on the Yearly plan" + subtitle (charge/cancel date) + "Manage subscription" (farmGreen, → billingURL by source); expired → "Your membership has expired" + "Renew…"; else → "You don't have a membership yet" + "Unlock full details for every farm." | IN_PROGRESS |
| 4 | Prefs card (`.card(4)`) | SettingsRow ×: Language(globe `#3F5E3A`, value = System/lang name → showLanguage); Notifications(bell.fill `#F5B301` → app settings); Refer friends(gift.fill `#EC4899`, authed → web invite); Contact us(envelope.fill `#38BDF8` → web) | IN_PROGRESS |
| 5 | Legal card | Privacy Policy(hand.raised.fill `#8B5CF6`); Terms of Service(doc.text.fill `#64748B`) | IN_PROGRESS |
| 6 | Account card (authed) | Sign out(rectangle.portrait.and.arrow.right `#3F5E3A` → confirm); Delete account(trash.fill `#DC2626`, warnRed → alert) | IN_PROGRESS |
| 7 | Version | "Farmsy for iOS {version}" `geist(12)` inkMuted@70 | IN_PROGRESS — `SettingsScreen.kt`; localized `farmsy_for_android_arg` (%1$s) ×4 locales, iOS-parity format (was a hardcoded literal) |

**SettingsRow:** icon(15,.semibold) tint in 34 tint@14% circle + label `geist(16,.medium)` + optional value `geist(14,.medium)` inkMuted + `chevron.right`(13) inkMuted@50; pad v14 h14; Haptics.tap. **Dialogs:** sign-out confirmationDialog; delete alert (Apple 5.1.1v — in-app) → deleteAccount → deletedState or deleteFailed alert; isDeleting overlay (dim + spinner); deletedState → AccountDeletedView fullScreenCover.

---

### S14 · LanguagePickerSheet — `Features/Settings/SettingsSheet.swift`
**PORT STATUS: IN_PROGRESS** — `features/settings/SettingsScreen.kt` `LanguageSheet` (REBUILT this session). Was a Material `AlertDialog`; now a **`ModalBottomSheet`** matching iOS: "LANGUAGE" eyebrow (geist(11,.semibold) kerning1.2 inkMuted) + `xmark` close (32/#F3F4F6, haptic), a `.card(4)` list of rows — flag(22) + endonym `geist(16,.medium)` + `checkmark`(14) farmGreen when current (`checkmark`→Material `Check`, §7a) — divider leading 50. **Row 3 restart prompt = N/A (correct):** Android applies the locale live via `Activity.recreate()`; iOS's `exit(0)` restart is genuinely N/A, so it is omitted. Not VERIFIED — device-gated: sheet present/dismiss + recreate() locale swap.
**Presents:** `.sheet` [medium,large] from Settings Language row. **Reads/Writes:** LanguageManager. Bg cream.

| Order | Element | Spec | Status |
|-------|---------|------|--------|
| 1 | Header | "LANGUAGE" `geist(11,.semibold)` kerning1.2 inkMuted + `xmark` 32 circle → dismiss | IN_PROGRESS |
| 2 | Language rows ×5 | System 🌐 / English 🇬🇧 / Nederlands 🇳🇱 / Français 🇫🇷 / Deutsch 🇩🇪 (endonyms); flag(22) + name `geist(16,.medium)` ink + `checkmark`(14,.bold) farmGreen when current; pad v14 h14; `.card(4)`; divider leading 50 | IN_PROGRESS |
| 3 | Restart prompt (if changed) | "Restart Farmsy to apply your new language." (in the *newly chosen* language) `geist(13)` inkMuted + "Restart now" (`arrow.clockwise`) white on farmGreenMap radius14 → `exit(0)`; bg `#F3F6F2` radius16 | IN_PROGRESS |

**Android note:** per-app locale applies live (no restart) — Android should skip the restart prompt.

---

### S15 · AccountDeletedView — `Features/Settings/SettingsSheet.swift`
**PORT STATUS: IN_PROGRESS** — `features/settings/SettingsScreen.kt` `AccountDeletedScreen` (audit fix this session). Seal / title / body / store-reminder match (store source correctly platform-inverted: default→Google Play). **Fixed:** row 5 **"Done" button added** — `AccountDeletedScreen(state, onDone)`, "Done" `geist(16,.semibold)` white on farmGreenMap RR16 vpad15 fillMaxWidth + light haptic → `onDone` (clears `deletedState`, back to the now-guest settings; iOS also `dismiss()`es the sheet — Android `SettingsScreen()` has no dismiss handler, so it returns to guest settings instead). Layout now Spacer/content(spacedBy18)/Spacer/Done (iOS-matching, was `Arrangement.Center`); store-reminder card RR14→RR16. Not VERIFIED — device-gated: the delete→confirm flow.
**Presents:** `.fullScreenCover` terminal after delete. Bg cream, pad 28.

| Order | Element | Spec | Status |
|-------|---------|------|--------|
| 1 | Seal | `checkmark.circle.fill`(56) farmGreen | IN_PROGRESS |
| 2 | Title | "Your account was deleted" `display(24)` ink centered | IN_PROGRESS |
| 3 | Body | "Your profile, saved farms and subscription data have been removed. Thanks for trying Farmsy." `geist(15)` inkMuted | IN_PROGRESS |
| 4 | Store reminder (if remindStore) | "Your membership was bought through {Google Play\|our website\|the App Store}. Cancel it there so you aren't charged again." `geist(14,.medium)` in `creamCard` radius16 | IN_PROGRESS |
| 5 | "Done" | white on farmGreenMap radius16 → onDone (dismiss, back to guest) | IN_PROGRESS |

---

### S16 · AuthView — `Features/Auth/AuthView.swift`
**PORT STATUS: IN_PROGRESS** — `features/auth/AuthSheet.kt` (built by a prior session; audited + field fixes this session). Rows 1-3 (logo/title/subtitle), 5 (error), 6 (submit: log_in/continue_label/create_account + spinner + disabled), 7 (Back), 8 (mode toggle), and validation (credentialsOk email@+pw≥8+confirm; detailsOk name+country) all faithful to iOS. **Single auth surface confirmed:** RootNav provides `LocalRequestAuth → showAuth → AuthSheet` (app-wide), and `OnboardingScreen.kt:221` presents the **same `AuthSheet`** composable via its own `showLogin` — both reach one sheet. **Fixed this session (rows 4/4b + AuthField):** (1) **password placeholder** was `if (isSignUp) … else ""` → **empty in log-in mode** (the reported "no placeholder" bug); now always `auth_pw_placeholder` (iOS is unconditional). (2) **AuthField rebuilt** from Material `OutlinedTextField` → iOS's plain field: `BasicTextField` + decorationBox placeholder in a white@60 + `inkMuted@25` stroke RR16 box (pad v15 h16), text `geist(17)`, green label above, reveal eye — matching `AuthView.swift:366` (Material chrome was a structural divergence). (3) **Capitalization/autocorrect per field** (iOS parity): email `None`+no-autocorrect, secure fields `None`+no-autocorrect, referral `Characters`+no-autocorrect, others default. Email placeholder stays the literal `you@email.com` (catalog key is **en-only** — identical in every locale, so not a divergence). **eye/eye.slash → §7a.** **Autofill hints (textContentType): DEFERRED** — Compose 1.7 (BOM 2025.01.01) lacks the clean `Modifier.semantics { contentType }` API (1.8+); see PORT_NOTES. Not VERIFIED — device-gated: keyboard/autocap behavior, autofill, secure toggle.
**Presents:** `.sheet` via requestAuth. **Default mode = `.logIn`** (not signup). **Reads:** SessionStore, pendingRefCode. **Writes:** mode, step, all fields, isWorking, errorMessage, verifySentTo. ScrollView, bg cream. Auto-dismiss on `session.isAuthenticated`.

| Order | Element | Spec | Status |
|-------|---------|------|--------|
| 1 | Logo header | `FarmsyLogo`(54) + "Farmsy" `display(30)` + "Local farms, fresh finds" `geist(13)` inkMuted; pad t34 b30 | IN_PROGRESS |
| 2 | Title | signUp "Create your free account" / logIn "Welcome back" `display(28)` | IN_PROGRESS |
| 3 | Subtitle | signUp "See every farm on the map in seconds." / logIn "Log in to pick up where you left off." `geist(15)` inkMuted | IN_PROGRESS |
| 4 | Fields — step1/login | Email (placeholder "you@email.com", email kbd, no autocap); Password (placeholder "At least 8 characters", secure, reveal eye); if signUp: Confirm password ("Repeat password", secure) | IN_PROGRESS |
| 4b | Fields — step2 (signup) | First+Last name (HStack); **DOBField** (wheel picker, ≤ today−16); Street (optional); City+Postal (HStack, optional); Country (required); Referral code (optional, uppercased) | IN_PROGRESS |
| 5 | Error | errorMessage `geist(14,.medium)` warnRed | IN_PROGRESS |
| 6 | Submit | isWorking→Spinner; else logIn "Log in" / step1 "Continue" / step2 "Create account"; PrimaryButtonStyle, disabled when !canSubmit (opacity 0.55) | IN_PROGRESS |
| 7 | Back (signup step2) | "Back" `geist(15)` inkMuted → step1, spring 0.3 | IN_PROGRESS |
| 8 | Mode toggle | "Already have an account?"/"New to Farmsy?" inkMuted + "Log in"/"Create one" farmGreen semibold → toggle mode, spring 0.35 | IN_PROGRESS |

**AuthField:** label `geist(14,.semibold)` farmGreen + field (`geist(17)`) with placeholder; secure→SecureField + reveal eye (`eye`/`eye.slash`); bg white@60 radius16 + inkMuted@25 stroke. **Validation:** credentialsOK (email has @, pw≥8 signup / non-empty login, confirm==pw); detailsOK (name+country). **Transitions:** step change spring 0.3 (fields slide); mode change spring 0.35. **submit:** signup step1 just advances; else API call → verifySentTo (signup) or setSession (login). **DOBField:** compact DatePicker, range ...today−16, white radius16. **Haptics:** tap on toggles, success on completion, warning on error.

---

### S17 · VerifyEmailView — `Features/Auth/AuthView.swift`
**PORT STATUS: IN_PROGRESS** — `features/auth/AuthSheet.kt:409` `VerifyEmailNotice` (built by a prior session; audited). **Faithful 1:1** — 📬(46), "Check your inbox" display(26), verification_link_sent(email) geist(15) inkMuted centered, "Log in" PrimaryButton → onDone (resets to logIn). Only nit: pad h24/t30/b36 vs iOS h24/v40. No divergence. Not VERIFIED (device-gated: the emailed-link round-trip).
**Presents:** in-place swap after successful signup (no session yet).

| Order | Element | Spec | Status |
|-------|---------|------|--------|
| 1 | 📬 | system(46) | IN_PROGRESS |
| 2 | Title | "Check your inbox" `display(26)` | IN_PROGRESS |
| 3 | Body | "We've sent a verification link to {email}. Click it to activate your account, then log in." `geist(15)` inkMuted centered | IN_PROGRESS |
| 4 | "Log in" | PrimaryButtonStyle → onDone (reset to logIn, step1) | IN_PROGRESS |

---

### S18 · TripsView — `Features/Trips/TripsView.swift`
**PORT STATUS: IN_PROGRESS** — `android/…/features/trips/TripsScreen.kt`. Deleted the private 280dp `TripMap` (+`numberedPin`); TripsScreen is sheet content over the ONE shared map (route/stops render there via MapScreen + `trip.fitToken`). Presented as a `BottomSheetScaffold` [0.5,0.92] sheet. **This session:** the `collapsed` state now **actually tucks the overview list** (`if (!collapsed)` around the Trip-overview Column) + `KeyboardArrowUp` dragUpHint — reached by **dragging** the sheet down (collapsed = PartiallyExpanded). Deleted the 4 dead vals (`routeLine`/`traceProgress`/`fitToken`/`traced`) + 14 dead map imports left by the map removal. PORT NOTE (detent/collapsed) → `PORT_NOTES.md`. **Origin town-search: DONE** — S19 `PlaceSearchSheet` (Photon) is built and wired to the Trips OriginRow.

**Presents:** `.sheet` [0.5,0.92] sel `$detent`. Two tabs (Plan / My trips). **Reads:** FarmsStore, LocationManager, SessionStore, TripStore. **Writes:** tab, naming, tripName, armedDelete, reorderNote, showOriginSearch. Bg cream. `collapsed` = detent==0.5. Refresh route + loadTrips on task; refreshRoute on stopIds change.

**Header + tabs:**
| Order | Element | Spec | Status |
|-------|---------|------|--------|
| 1 | Header | "TRIP PLANNER" `geist(11,.semibold)` kerning1.2 inkMuted + `xmark` `#6B7280` 32 `#F3F4F6` circle → dismiss | IN_PROGRESS |
| 2 | Tabs | "Plan a trip" / "My trips": `geist(15,.bold)`, selected white on `farmGreenMap` radius14, unselected white + hairline stroke; Haptics | IN_PROGRESS |

**Plan tab:**
| Order | Element | Spec | Status |
|-------|---------|------|--------|
| 3 | Origin bar | `magnifyingglass`(15) inkMuted + label/"Choose a starting point" `geist(15)` + (`xmark.circle.fill` clear OR `location.circle` farmGreenMap); white Capsule + hairline → showOriginSearch | IN_PROGRESS — **visual fixed this session** — `TripsScreen.kt` OriginRow: leading `Search`(18) inkMuted (was `MyLocation` farmGreen), empty label `choose_a_starting_point` (was `start_from_my_location`), empty-state trailing `MyLocation`(20) farmGreenMap (iOS decorative `location.circle`), filled trailing `Close` clear. The S19 session had changed the tap behaviour but left the MyLocation/"Start from my location" visual; now iOS-parity. `start_from_my_location` string **deleted** (its only ref); `choose_a_starting_point` added ×4 locales. SF `magnifyingglass`→Search (§7a) |
| 4 | Collapsed | dragUpHint (`chevron.up`(13) inkMuted@80) only | IN_PROGRESS |
| 5 | Trip overview | "Trip overview" `geist(16,.bold)`; Divider; scroll of rows `max(stops, 5)`; white radius16 hairline; grows to fill | IN_PROGRESS |
| — filledRow | index `geist(12,.bold)` white in `farmGreenMap` 28 circle + name `geist(15,.semibold)` + legLabel `geist(12)` inkMuted + checkmark if selected + `xmark` remove; selected bg `farmGreenMap@8%`; tap → dismiss+openFarm | IN_PROGRESS |
| — emptyRow | dashed index circle + "Pick a farm on the map" `geist(15)` inkMuted + "—" | IN_PROGRESS |
| 6 | reorderNote | if present `geist(12)` farmGreenMap | IN_PROGRESS |
| 7 | "Best order" (≥3 stops) | `geist(14,.semibold)` farmGreen → optimise + note "Reordered · about N km shorter"/"Already the shortest order" | IN_PROGRESS |
| 8 | modeSelector | 3 buttons car/bike/walk: icon+label `geist(13,.semibold)`, selected white on `farmGreenMap` radius12, else white+hairline → setMode + refreshRoute | IN_PROGRESS |
| 9 | totalsBar | `point.topleft…curvepath`(15) farmGreenMap + text ("Add farms to see…" / "Finding the road…" / "~N km · Xh Ym{( est.)}") + mode icon; bg `#F3F6F2` radius16 | IN_PROGRESS |
| 10 | Actions | "Save trip"(bookmark, outline, disabled empty)→naming; "Show route"(location.north.fill, filled, disabled !canRoute)→refreshRoute+fit+dismiss; "Open in Google Maps"(arrow.up.forward.square, outline)→maps URL | IN_PROGRESS |

**My trips tab:**
| State | Content | Status |
|-------|---------|--------|
| Signed out | gate: `lock.fill`(34) + "Sign in to view your trips" | IN_PROGRESS |
| Not Pro | gate: "Saved trips are a Farmsy Pro feature." | **UN-GATED (1:1 with iOS build 19)** — `TripsScreen.kt` MyTripsTab: the `if (!hasFullAccess) Gate(saved_trips_pro)` was **removed**; My-trips is free for any signed-in user. The signed-OUT gate above (`if (!isAuthenticated) Gate(sign_in_to_view_trips)`) is kept. `hasFullAccess` param dropped from MyTripsTab. iOS shipped the same removal in build 19 (TripsView My-trips gate dropped), so this is an ordinary 1:1 row. `saved_trips_pro` string **deleted this session** (0 refs, all locales). |
| Member | Draft banner ("No trip in progress"/"A draft with N stops is waiting", dashed → Plan); collapsed→dragUpHint+carousel; else My Trips list (`max(count,6)`) + carousel | IN_PROGRESS |
| — savedRow | index in farmGreenMap circle + name `geist(15,.bold)` + "N farms · {date}" `geist(12)`; `xmark`/`trash.fill` armed-delete (2-tap); tap → openTrip + Plan + detent 0.5 | IN_PROGRESS |
| — savedEmptyRow | dashed index + "Plan a trip to fill this" + `plus` farmGreenMap → Plan | IN_PROGRESS |

**Alerts:** naming ("Name your trip" TextField "My weekend trip" → save). **Sheet:** showOriginSearch → PlaceSearchSheet [large]. **save():** save + Haptics.success + clear + clearOrigin + tab=.mine. **openGoogleMaps:** origin+dest+waypoints URL with travelmode. **TripRecommendations (C1):** cardHeight 156, 2-per-page pager.

---

### S19 · PlaceSearchSheet — `Features/Trips/PlaceSearch.swift`
**PORT STATUS: IN_PROGRESS** — `features/trips/PlaceSearchSheet.kt` (new). **Provider differs by decision: Photon** (`photon.komoot.io/api`), not iOS's `MKLocalSearchCompleter` — a plain Ktor `httpClient.get` through the shared client + kotlinx.serialization decode of GeoJSON; **no dependency, no key**. Layout/behaviour 1:1 with iOS (`PlaceSearch.swift`). NL/BE **proximity bias** (`lat=51.8&lon=4.7&location_bias_scale=0.6`) + client-side `countrycode ∈ {NL,BE}` filter (Photon has no country param); input **debounced 300ms**; identifiable **User-Agent + contact** sent per usage policy. Coordinates arrive in the same response → **no completer→resolve two-step** (simpler than iOS). Field map: `name`→title, `city/state/postcode`→subtitle, `geometry.coordinates`→`TripStore.setOrigin(LatLng,label)`. **Wired: Trips origin only** — `TripsScreen` OriginRow tap → `showOriginSearch` → sheet (iOS OriginBar pattern); GPS moved into the sheet's `onLocate` (`useMyLocation()`). S2 onboarding (Pass B) NOT wired, per scope. Strings `starting_point`/`search_town_address_postcode` added (nl/fr/de/en); `use_my_location` reused; `osm_attribution` (Android-only, en). **Google Places Autocomplete (New) = exact-iOS-parity path → DEFERRED (PORT_NOTES); Places SDK removed from build.gradle.** Added a row: **OSM/ODbL attribution footer** (Android-only, no iOS counterpart — MapKit carries its own). Not VERIFIED — device-gated: live query, keyboard, proximity bias.
**Presents:** `.sheet` [large] from Trips origin / Onboarding location. **Reads/Writes:** PlaceSearch (MKLocalSearchCompleter, biased NL/BE). Bg cream, autofocus.

| Order | Element | Spec | Status |
|-------|---------|------|--------|
| 1 | Header | "STARTING POINT" `geist(11,.semibold)` kerning1.2 inkMuted + `xmark` 32 circle → dismiss | IN_PROGRESS |
| 2 | Search bar | `magnifyingglass`(15) inkMuted + TextField "Search a town, address or postcode" `geist(15)` + `xmark.circle.fill` clear; white Capsule + hairline | IN_PROGRESS |
| 3 | Use my location row | `location.fill`(15) farmGreenMap in 34 `farmGreenMap@12%` circle + "Use my location" `geist(15,.semibold)` → onLocate + dismiss | IN_PROGRESS |
| 4 | Result rows | `mappin.circle`(16) inkMuted + title `geist(15)` + subtitle `geist(12)` inkMuted → resolve → onPick + dismiss; divider leading 62 | IN_PROGRESS |
| — | scroll | scrollDismissesKeyboard immediately | IN_PROGRESS — `PlaceSearchSheet.kt`; scroll column clears focus/keyboard on pick |
| 5 | OSM attribution footer | `osm_attribution` "Place data © OpenStreetMap contributors" `geist(11)` inkMuted@70; below results | IN_PROGRESS — **added this session; no iOS counterpart** (iOS MapKit carries its own attribution). ODbL notice for OSM-derived Photon data |

**Android:** provider is **Photon** (OSM), not MKLocalSearchCompleter — decided after evaluation (Nominatim prohibits client autocomplete; Google Places is the exact-parity DEFERRED). **SF→Material (see §7a):** `magnifyingglass`→Search, `location.fill`→LocationOn, `mappin.circle`→Place, `xmark.circle.fill`→Close-in-circle, `xmark`→Close.

---

### S20 · AddFarmView — `Features/Submit/AddFarmView.swift`
**PORT STATUS: N/A.** iOS routes "add farm" to the **web form** (SafariView `/farmers/submit`); the native `AddFarmView` stays live-but-unused on iOS. On Android the only caller of the native `AddFarmSheet` was `DiscoverFeedScreen.kt:101` — **that file was deleted this session (S11 N/A)**, so `AddFarmSheet` is now unreachable. Matching iOS's unused-native posture, S20 is not ported → N/A. **The `features/submit/AddFarmSheet.kt` file was LEFT IN PLACE this session** (task scoped the deletion to the *wiring*, not the sheet) — it's now dead code, reported for a future cleanup or an explicit N/A delete.
**Note (historical):** iOS now routes "add farm" to the **web form** via SafariView; this native form stays live but unused. **Reads:** SessionStore, LocationManager. **Writes:** form, selectedCategories, photos, pinCoordinate, mapCamera, isSubmitting, errorMessage, isDone. NavigationStack, title "Add a farm shop" inline, Cancel toolbar.

| Order | Element | Spec | Status |
|-------|---------|------|--------|
| 1 | DisplayTitle | "Put a farm *on the map*" size 28 | NOT_STARTED |
| 2 | Intro | "Know a farm shop that isn't on Farmsy yet?…" `geist(14)` inkMuted | NOT_STARTED |
| 3 | membersNote (if !hasFullAccess) | `lock.fill` farmGreen + "Adding farm shops is a member feature…" `farmGreenSoft` radius16 | NOT_STARTED |
| 4 | Section "The essentials" | Farm name*, City*, Country segmented (Netherlands/Belgium) | NOT_STARTED |
| 5 | Section "What do they sell?" | CategoryPickChips (adaptive 108 grid; on → farmGreen white, off → cream ink) | NOT_STARTED |
| 6 | Section "Tell us more" | Description TextEditor(90) + Street/Postal/Phone/Website/Email/Opening hours labeled fields | NOT_STARTED |
| 7 | Section "Where is it?" | MapReader tap-to-drop-pin (`mappin.circle.fill`(34) farmGreen) + "Use my location" | NOT_STARTED |
| 8 | Section "Photos (up to 5)" | PhotosPicker "Choose/Change photos" (capsule, farmGreen 1.5 stroke) + horiz 84 previews | NOT_STARTED |
| 9 | Error + Submit | errorMessage warnRed; "Submit farm shop" PrimaryButtonStyle (disabled unless name+city) | NOT_STARTED |
| — doneState | `checkmark.seal.fill`(56) farmGreen + DisplayTitle "Thanks — it's *in review*" size 28 + body + "Done" | NOT_STARTED |

**section():** title `display(19,.semibold)` + content, white radius16 pad16. **labeledField:** label `geist(13,.semibold)` inkMuted + TextField cream radius12. **loadPhotos:** scale ≤1600px, JPEG 0.8. **submit:** farmType from categories, lat/lng from pin → submitFarm → isDone spring.

---

### S21 · ClaimFarmView — `Features/Claim/ClaimFarmView.swift`
**PORT STATUS: N/A** — claim is routed to the WEB on both platforms. iOS opens `/claim/<osmId>` via SafariView; the native `ClaimFarmView` stays live-but-unused (`FarmDetailView.swift:62`). On Android, claim opens `https://www.farmsy.app/claim/<osmId>` via `Intent.ACTION_VIEW` (`FarmDetailScreen7.kt` `openClaim`), and the native `features/claim/ClaimSheet.kt` has **zero live callers** (verified — only its own definition). The native form is not ported (matches iOS's unused-native posture). The rows below are retained for reference only.
**Note (historical):** iOS routes claim to **web** (`/claim/<osmId>`) via SafariView; native form stays live but unused. **Reads:** SessionStore. **Writes:** fullName, email, phone, method, kvkNumber, message, isSubmitting, errorMessage, isDone. NavigationStack, title "Claim this farm" inline, Cancel toolbar. Prefills email from session.

| Order | Element | Spec | Status |
|-------|---------|------|--------|
| 1 | Kicker | "For farm owners" | NOT_STARTED |
| 2 | DisplayTitle | "Is {name} yours?" (3-part) size 26 | NOT_STARTED |
| 3 | Intro | "Claim it to keep your details up to date…" `geist(14)` inkMuted | NOT_STARTED |
| 4 | Contact card | Full name*, Email*, Phone* fields; white radius16 pad16 | NOT_STARTED |
| 5 | Verify card | "How should we verify you?" `display(19,.semibold)`; segmented (Business email / KVK number); kvk → KVK number field, else → hint; "Anything we should know?" TextEditor(80) | NOT_STARTED |
| 6 | Error + Submit | errorMessage warnRed; "Send claim" PrimaryButtonStyle (disabled unless name+email+phone[+kvk]) | NOT_STARTED |
| — doneState | `checkmark.seal.fill`(56) + DisplayTitle "Claim *sent*" size 30 + "We'll be in touch at {email}…" + "Done" | NOT_STARTED |

**field():** label `geist(13,.semibold)` inkMuted + TextField cream radius12. **submit:** FarmClaim → submitClaim → isDone spring.

---

## 9. Reusable component-views

### C1 · TripRecommendations — `Features/Trips/TripsView.swift`
Two-cards-per-page horizontal pager (TabView `.page`). **Reads:** FarmsStore, LocationManager, FavoritesStore, SessionStore, TripStore. `anchor` = GPS ?? draft origin ?? stops centroid ?? planned centroid. `select()`: nearbyWithImages(anchor,100km) else random photo'd; minus planned+stops+favorites; described+plain shuffled prefix 6. Pages of 2.
| Element | Spec | Status |
|---------|------|--------|
| Header | "RECOMMENDATIONS NEAR YOU"/"RECOMMENDATION" `geist(11,.semibold)` kerning1.2 inkMuted | IN_PROGRESS — `discover/RecommendationCarousel.kt` header now sets `letterSpacing = 1.2.sp` (iOS `.kerning(1.2)`) |
| Loading | 2× SkeletonBox radius16 h=cardHeight | IN_PROGRESS — `discover/RecommendationCarousel.kt` `!built` → 2× `SkeletonBox(cornerRadius=16, weight1·height cardHeight)` |
| recCard | cover photo (fill, radius16, `#EDE7DD` base) + bottom gradient scrim + name `geist(14,.bold)` white + city `geist(12,.medium)` white@85 + category tags (`geist(9,.bold)` white on white@22 Capsule, prefix2) + save heart(13) top-right (warnRed saved) + hairline stroke; tap → onOpenFarm | IN_PROGRESS — `RecommendationCarousel.kt RecCard`; this session: image = gallery-first-else-cover (was `pin.image`); card uses `tapCard(excludeTopTrailing=44)` (was `clickable`); save heart size 13 (was 15) + silent no-op when signed-out (removed the non-iOS `requestAuth()`); scrim gradient center→bottom (was top→bottom); 1dp hairline stroke overlay added (iOS `:742`); no-image fallback now Material `Eco` leaf glyph size 28 inkMuted@0.5 (iOS SF `leaf`, `:713`); cover now `SubcomposeAsyncImage` with `SkeletonBox(16)` loading+error (iOS `:706-711`); category-tag `letterSpacing = 0.4.sp` (iOS `.kerning(0.4)`, `:731`) |
| pager | `< • • • >` chevrons(13,.bold) (disabled at ends) + dots (farmGreenMap current / inkMuted@30) | IN_PROGRESS — `RecommendationCarousel.kt`; chevrons now size 13 (was 22), active dot `farmGreenMap` (was `farmGreen`). Glyph is Material `KeyboardArrowLeft/Right` (vs iOS SF `chevron`) |
| Empty | built && shown empty → render nothing | IN_PROGRESS — RecommendationCarousel `if (built && shown.isEmpty()) return` |
| select()/build | loadGalleriesIfNeeded → guard pins → select (exclude planned+stops+favs; nearbyWithImages 100km else photo'd·shuffled; described+plain take6+take6 shuffled take6) | IN_PROGRESS — `RecommendationCarousel.kt` LaunchedEffect now calls `farms.loadGalleriesIfNeeded()` **before** the pins guard + select, matching iOS `load()` order |

### C2 · DiscoverFeedCard — `Features/Discover/DiscoverFeedView.swift`
Featured photo tile h210 radius16. Photo fill (or category tile) + bottom gradient (black .80→.22→clear) + name `geist(16,.bold)` white lineLimit2 + city/distance/rating row (white) + category chips(prefix2, `geist(10,.bold)` ink on white@95 Capsule) + "TOP PICK" badge top-left (verified, `geist(9,.bold)` farmGreen on white@92) + save heart top-right (34, warnRed saved) + shadow; tap → onOpen. **Status:** NOT_STARTED

### C3 · PingCard — `Features/Discover/DiscoverFeedView.swift`
Post card `creamCard` radius16 hairline. Two sibling tap regions (header+text → openFarm; photos → openImage). Initials avatar(40, farmGreen@12) + author `geist(14,.semibold)` + farmName `geist(12,.medium)` farmGreenMap + timeAgo `geist(11)` inkMuted; body `geist(14)` lineLimit3; FixedImageRow(prefix3, h100); like row (`heart`(12) + count) inkMuted → openFarm. **Status:** IN_PROGRESS — `whatsnew/WhatsNewSheet.kt PingCard`; timeAgo matches iOS — only `time_just_now` is localized (en/nl/fr/de); the unit letters `m`/`h`/`d` are the default-`values` fallback in every locale (invented nl `u` / fr `j` / de `t` + French `many` class removed, see PORT NOTES); tap regions use `tapCard`; author/farm inner spacing = `spacedBy(1.dp)` (iOS VStack spacing 1, `:334`); photo tap stubbed → openFarm (S10 unbuilt)

### C4 · MultiImageFarmCard — `Features/Map/WhatsNewSheet.swift`
Featured farm as post. `creamCard` radius16 hairline. Round profile (first photo, 40) + name `geist(14,.bold)` + city `geist(12)` inkMuted + save heart top-right(17); teaser `geist(13)` lineLimit3; FixedImageRow(prefix3, h96); `tapCard(excludeTopTrailing 52)` → onOpen. **Status:** IN_PROGRESS — `whatsnew/WhatsNewSheet.kt MultiImageFarmCard`; teaser is a **static** 3-line clamp (`buildAnnotatedString`, `maxLines=3`, ellipsis) with a decorative farmGreen/bold "  … View more" appended only when `teaser.length > 140` — matching iOS (non-interactive; the card's `tapCard` opens the farm). C7 ExpandableText is **not** used here (reserved for S7). Card + heart use `tapCard`. Name/city inner spacing = `spacedBy(1.dp)` (iOS VStack spacing 1, `:164`). Teaser `.lineSpacing(2)` now matched (iOS `:195`): `lineHeight = 18.9.sp` (Geist natural 16.90 + 2) + `LineHeightStyle(Center, Trim.Both)` — exact, `includeFontPadding=false` confirmed. (DEFERRED note deleted.)

### C5 · FixedImageRow — `Features/Discover/DiscoverFeedView.swift`
Equal fixed tiles (base `#F3F4F6` radius10 + AsyncImage fill clipped). Single photo padded to half-width (clear filler). Optional per-index tap. Per-tile `SkeletonBox` while an image loads (`SubcomposeAsyncImage`). **Status:** IN_PROGRESS — `whatsnew/WhatsNewSheet.kt FixedImageRow`; this session: per-tile tap now uses `tapCard` (was `clickable`) — scroll-aware + haptic, matching iOS `OptionalTap`→`tapCard` (`DiscoverFeedView.swift:431-435`)

### C6 · FarmCard — `Features/Map/MapScreen.swift`
Legacy bottom card (name+distance+chevron, address, category emojis+rating+heart, white radius16). **MainView uses FarmDetailView instead — N/A for port unless a compact card is wanted.** **Status:** N/A

### C7 · ExpandableText — `Features/Detail/FarmDetailView.swift`
Description clamped to 3 lines; "… View more" (farmGreen bold) at end of last visible line; expands inline to full + "View less"; `.tapCard` toggle. **Status:** IN_PROGRESS — shared `ui/theme/Components.kt ExpandableText` (truncation via `TextLayoutResult.hasVisualOverflow` + `getLineEnd`, not the binary-search font measurement — DEFERRED for exact-fit, see PORT NOTES "C7 ExpandableText — clamp/truncation measurement"; `tapCard` toggle). Built once here; **S7 reuses this component.** C4 does **not** use it (C4 is a static clamp — see C4 row). `ClampedDescription` not ported (S11 concern). `.lineSpacing(3)` now matched (iOS `FarmDetailView.swift:984`): style default `lineHeight = 22.5.sp` (Geist natural 19.50 + 3) + `LineHeightStyle(Center, Trim.Both)` — exact, `includeFontPadding=false` confirmed. (DEFERRED note deleted.) Clamp mechanism remains DEFERRED (untouched).

### C8 · PostComposer / ReviewComposer — `Features/Detail/FarmMemberSections.swift`
Covered under S9. **Status:** IN_PROGRESS — built in `features/detail/Composers.kt` (`PostComposer`/`ReviewComposer`), consumed by S9; see C8 block below.

---

## 11. Survey — `features/survey/SurveyScreen.kt` + `core/SurveyApi.kt` (PRODUCT-SOURCED, NOT A PORT)

**This is not an iOS→Android port row.** The "why-farm" survey is a product addition Neil asked for on both apps; **iOS and Android build from the same source — Aviah's spec in the vault thread and her server API — with no platform as source of truth for the other.** iOS shipped first (TestFlight builds 20–23) but is a peer, not the reference; where the two differ, the thread wins (see the feedback-celebration DISPUTED in this session's PORT_NOTES).

**Status: IN_PROGRESS (both platforms).** No VERIFIED.

- **API** (`SurveyApi.kt`, mirrors iOS `SurveyAPI.swift`): `GET /api/survey/questions?locale=` (localized, cached 1h), `GET /api/survey/respond` (gate → `{known,answered,isAdmin}`), `POST /api/survey/respond` (`source="android"` / `"ios"`), feedback → `POST /api/contact topic=feedback`.
- **Screen** (`SurveyScreen.kt`): 7 questions on one scroll; `one`→radio / `many`→checkbox (Q4 caps at 2, swap-oldest) / `text`→field; signed-out name/email; "N to go" counter; **two modes** — QUESTIONS and FEEDBACK. After answering (or an already-answered tap) the button opens FEEDBACK ("What could be better?"). Copy is Aviah's verbatim (thread); **en-only** (the new survey's chrome is not in `messages/*.json` yet — DEFERRED, both platforms).
- **Entry point** (`MainScreen.kt`): floating button, bottom-trailing above the pill; **admin → no button** (gate at map level; DEBUG shows for all, Release hides admin); **failed gate ≠ hide** (fail-open + error/retry, never a silent vanish); **cold-launch auto-open** 1.4s after the map settles, once-ever signed-in / once-a-day signed-out (`SurveyAutoOpen`, SharedPreferences), never on resume; a **down-arrow pointer** (farmGreen, bounces, still under reduce-motion, a11y-hidden) shows only while unanswered.
- **Reconciled (Aviah later-5):** both prior divergences settled and both platforms now agree. **Arrow →** standard `ArrowDownward` above the button, `farmGreen`, bouncing — Aviah confirmed her hand-drawn thread spec was stale (described a web version Neil had already rejected); web itself is the standard arrow. iOS corrected off the build-23 hand-drawn Path. **Celebration → KEPT** on both: after answering, a standalone "Thank you, we have your answers." screen that STAYS until closed; the feedback box is a separate screen on reopen (sequence: answer → thank-you → close → reopen → feedback). This was iOS build 23's behaviour and was correct; the web was silently swapping straight to feedback (a bug Aviah fixed at `4719fe0`). Android gained the celebration to match.

---

## 12. Pro filters — `core/FarmFilters.kt` + `FarmsStore.kt` + `features/map/FilterSheet` + `ProUpsellSheet` (PRODUCT-SOURCED)

**Product-sourced, both platforms build from Aviah's closed five-group set (later-3/7/8), not iOS→Android.** Paid on both, no exception (Aviah later-8: Play IAP works and carries most revenue).

**Status: IN_PROGRESS (both platforms).** No VERIFIED.

- **Parser** (`FarmFilters`): `isOpenToday` re-synced to the **Amsterdam** wall clock (was device — the free/paid disagreement for users outside CET, web `a46ab4b`); added `isOpenNow` (Amsterdam minute, `windowsOf`, `00:00`=end-of-day, multi-windows) and `isOpenOnDay(dayMon)`. Both platforms; tested via a frozen-clock harness (Sun 23:00 Amsterdam / Tokyo device) — **18/18 each**, incl. timezone independence.
- **State** (`FarmsStore`): `filterOpenNow`/`OpenSaturday`/`OpenSunday` wired into `filtered()` (Sat=5, Sun=6), `anyProFilterOn`, cleared by `clearAllFilters`.
- **FilterSheet**: FARMSY PRO section — three time filters; **Type-of-place + How-it's-grown moved out of the free rail into Pro**. Non-members see all five **dimmed + a lock** (shown, not hidden); a locked tap opens the upsell (signed out → sign-in first) instead of toggling. `proLocked = !hasFullAccess`, unconditional (no Android exception, no flag).
- **ProUpsellSheet** (iOS `Features/Map/ProUpsellSheet.swift`, Android `features/map/ProUpsellSheet.kt`): farm-free membership panel (Aviah option 2, matches web `SubscriptionGateModal`) — title + four `account.gateFeature` lines + the **same native RevenueCat/Play plan buttons** LockedAccessView uses (Android reuses `PlanButton`, made internal). **Native purchase, NOT a web URL** (Aviah later-8: the Settings web URL only *manages* a sub; buying is native — where the 19 Play / 13 Apple purchases came through).
- **iOS shipped** TestFlight build 25. **Android committed** (Play release blocked on Console access). Chrome/labels: `farmsy_pro`/`pro_open_now`/`_saturday`/`_sunday` added ×4 locales (Android); iOS en `String(localized:)`.
- **DEFERRED (both):** per-value counts on the locked rows (Aviah's "let people see how many farms it finds" — sustainable 5, unstaffed 9). Shown-dimmed works without them; the live counts (esp. `isOpenNow` over all pins) are a refinement, unbuilt.

---

## 10. Totals

- **iOS source:** 39 Swift files, ~10,098 LOC.
- **Top-level screens/views:** 21 (S1–S21).
- **Onboarding sub-steps enumerated:** 7 (S2.1–S2.7).
- **Reusable component-views:** 8 (C1–C8).
- **Total addressable UI surfaces:** **29** (21 screens + 8 components), **36** counting onboarding steps individually.
- **Section-8 screen rows (elements enumerated):** every element of all 29 surfaces is listed above.
- **Infrastructure rows:** design tokens (12 color + 4 type + 10 component-style), 21 data models, 27 network calls, 7 persistence stores, 9 state stores, 12 dependencies.
- **Every row Status = NOT_STARTED** (this is a fresh port manifest).

