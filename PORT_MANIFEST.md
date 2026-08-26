# Farmsy iOS → Android Port Manifest

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
| S2  | OnboardingView (container) | Features/Onboarding/OnboardingView.swift | Root (first run) | NOT_STARTED |
| S2.1| Onboarding · Welcome step | ″ | Slide track page 0 | NOT_STARTED |
| S2.2| Onboarding · Personalize step | ″ | Slide track page 1 | NOT_STARTED |
| S2.3| Onboarding · Location step | ″ | Slide track page 2 | NOT_STARTED |
| S2.4| Onboarding · Details step | ″ | Slide track page 3 | NOT_STARTED |
| S2.5| Onboarding · Nearby step | ″ | Slide track page 4 | NOT_STARTED |
| S2.6| Onboarding · Notify step | ″ | Slide track page 5 | NOT_STARTED |
| S2.7| Onboarding · Done step | ″ | Slide track page 6 | NOT_STARTED |
| S3  | MainView (map-first shell) | Features/Main/MainView.swift | Root (returning/authed) | NOT_STARTED |
| S4  | MapScreen | Features/Map/MapScreen.swift | Base layer of MainView | NOT_STARTED |
| S5  | FilterSheet | Features/Map/MapScreen.swift | `.sheet` [medium,large] from search bar | NOT_STARTED |
| S6  | WhatsNewSheet | Features/Map/WhatsNewSheet.swift | `.sheet` [0.55,0.92] from bottom panel | NOT_STARTED |
| S7  | FarmDetailView | Features/Detail/FarmDetailView.swift | `.sheet` [180,0.55,large] on pin tap | NOT_STARTED |
| S8  | LockedAccessView (paywall) | Features/Detail/FarmDetailView.swift | `.sheet` from detail gate | NOT_STARTED |
| S9  | FarmMemberSections | Features/Detail/FarmMemberSections.swift | Embedded in FarmDetailView (member) | NOT_STARTED |
| S10 | ImageLightbox | Features/Detail/ImageLightbox.swift | `.fullScreenCover`, clear bg | NOT_STARTED |
| S11 | DiscoverFeedView | Features/Discover/DiscoverFeedView.swift | (feed; folded into WhatsNew on phone) | NOT_STARTED |
| S12 | SavedScreen | Features/Saved/SavedScreen.swift | `.sheet` [0.55,0.92] from bottom panel | NOT_STARTED |
| S13 | SettingsSheet | Features/Settings/SettingsSheet.swift | `.sheet` [0.55,0.92] from bottom panel | NOT_STARTED |
| S14 | LanguagePickerSheet | Features/Settings/SettingsSheet.swift | `.sheet` [medium,large] from Settings | NOT_STARTED |
| S15 | AccountDeletedView | Features/Settings/SettingsSheet.swift | `.fullScreenCover` terminal | NOT_STARTED |
| S16 | AuthView | Features/Auth/AuthView.swift | `.sheet` (requestAuth hook) | NOT_STARTED |
| S17 | VerifyEmailView | Features/Auth/AuthView.swift | In-place swap after signup | NOT_STARTED |
| S18 | TripsView | Features/Trips/TripsView.swift | `.sheet` [0.5,0.92] from bottom panel | NOT_STARTED |
| S19 | PlaceSearchSheet | Features/Trips/PlaceSearch.swift | `.sheet` [large] from Trips origin | NOT_STARTED |
| S20 | AddFarmView | Features/Submit/AddFarmView.swift | NavigationStack sheet (web now) | NOT_STARTED |
| S21 | ClaimFarmView | Features/Claim/ClaimFarmView.swift | NavigationStack sheet (web now) | NOT_STARTED |
| C1  | TripRecommendations (carousel) | Features/Trips/TripsView.swift | Embedded (Saved/WhatsNew/Trips) | IN_PROGRESS |
| C2  | DiscoverFeedCard | Features/Discover/DiscoverFeedView.swift | Embedded row | NOT_STARTED |
| C3  | PingCard | Features/Discover/DiscoverFeedView.swift | Embedded row | IN_PROGRESS |
| C4  | MultiImageFarmCard | Features/Map/WhatsNewSheet.swift | Embedded row | IN_PROGRESS |
| C5  | FixedImageRow | Features/Discover/DiscoverFeedView.swift | Embedded photo row | NOT_STARTED |
| C6  | FarmCard | Features/Map/MapScreen.swift | Bottom card (legacy, unused by MainView) | NOT_STARTED |
| C7  | ExpandableText | Features/Detail/FarmDetailView.swift | Inline clamped text | IN_PROGRESS |
| C8  | PostComposer / ReviewComposer | Features/Detail/FarmMemberSections.swift | Embedded forms | NOT_STARTED |

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
| `LightboxSource` (struct, Identifiable) | images[], startIndex, eyebrow, title, subtitle?, postText? | Drives ImageLightbox fullScreenCover | NOT_STARTED |
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
**Presents:** root (first run). **Reads:** FarmsStore, LocationManager. **Writes:** on finish sets `farms.selectedCategories`, and (if applyPrefs) `filterVerified/OpenToday/HasPhotos/Zelfpluk`; RootView sets `didFinishOnboarding`. **Steps enum:** welcome, personalize, location, details, nearby, notify, done.

**Container layout:**
| Order | Element | Spec | Status |
|-------|---------|------|--------|
| 1 | Full-bleed bg | welcome → `Image("WelcomeFarmShop")` scaledToFill + gradient overlay (black .72 bottom → .30 → .55 top, bottom→top); else `cream`; animated easeInOut 0.45 on step==welcome | NOT_STARTED |
| 2 | Header slot | reserved; opacity 1 when step ∉ {welcome,done}; pad h20 t8 | NOT_STARTED |
| 2a | Back button | `chevron.left` system(15,.semibold) farmGreen, 44×44, `creamCard` circle; opacity/disabled when !canGoBack | NOT_STARTED |
| 2b | ProgressBar | Capsule track `farmGreen@22%`, fill `farmGreen` width max(12, w·fraction), height 5, spring(0.5); `fraction = step.rawValue / (count−2)` | NOT_STARTED |
| 3 | Sliding track | GeometryReader HStack of 7 step pages each `frame(w,h)`; `.offset(x: −step·w)`; spring(0.5 bounce 0.14) on step; clipped | NOT_STARTED |

**Transitions:** spring slide between steps. **advance/goBack** with `Haptics.tap()`. **Sheets:** `showLogin`→AuthView; `showPlaceSearch`→PlaceSearchSheet [large]. **Edge:** focusCoord = chosenCoord ?? GPS.

#### S2.1 · Welcome step
| Order | Element | Spec | Status |
|-------|---------|------|--------|
| 1 | Spacer | flexible top | NOT_STARTED |
| 2 | Logo | `Image("FarmsyLogo")` 74×74, pad 16, `cream` circle, shadow black .25 r10 y4 | NOT_STARTED |
| 3 | "Farmsy" | `displayItalic(52,.medium)` white | NOT_STARTED |
| 4 | Tagline | "Local food, close to you." `display(26,.medium)` white, centered | NOT_STARTED |
| 5 | Body | "Find farm shops, pick-your-own farms and honest food straight from the people who grow it." `geist(16)` white@90%, centered, pad h12 | NOT_STARTED |
| 6 | Spacer | flexible | NOT_STARTED |
| 7 | "Log in / Sign up" | `geist(18,.semibold)` farmGreen on **white** fill, radius 16, vpad 17 → `onLogin` (showLogin) | NOT_STARTED |
| 8 | "Skip for now" | `geist(17,.semibold)` white on white@14% fill, white@35% 1px stroke, radius 16, vpad 15 → `onSkip` (advance) | NOT_STARTED |
| — | Container pad | h24, bottom 48 | NOT_STARTED |

#### S2.2 · Personalize step (multi-select)
Options: produce, dairy, cheese, eggs, honey, meat, fish, wine (8). 2-col LazyVGrid spacing 12.
| Order | Element | Spec | Status |
|-------|---------|------|--------|
| 1 | Kicker | "Personalize" | NOT_STARTED |
| 2 | DisplayTitle | "What are you *looking* for?" size 32 | NOT_STARTED |
| 3 | Sub | "Pick a few — or none. You can change this anytime." `geist(15)` inkMuted centered | NOT_STARTED |
| 4 | CategoryTile ×8 | HStack: emoji `geist(24)` + label `geist(16,.semibold)` (farmGreen when on else ink), lineLimit 1 minScale 0.8, Spacer, `checkmark.circle.fill`(18) farmGreen when on; pad v16 h14; bg radius16 fill `farmGreenSoft` when on else `creamCard`, stroke farmGreen(1.5) when on else hairline(1). Toggle set membership | NOT_STARTED |
| 5 | Button | `selected.isEmpty ? "Skip" : "Continue"` PrimaryButtonStyle → advance | NOT_STARTED |

#### S2.3 · Location step
| Order | Element | Spec | Status |
|-------|---------|------|--------|
| 1 | Kicker | "Location" | NOT_STARTED |
| 2 | DisplayTitle | "Where are you *exploring* today?" size 30 | NOT_STARTED |
| 3 | **RadarPulse** | 3 fixed rings (stroke farmGreen@22, w 58+i·44); 2 staggered pulses (stroke farmGreenMap animate 0.55→0, size 50→150, easeOut 2.4 repeatForever delay i·1.2); center pin `farmGreenMap` 48 circle + `location.fill`(19) white, shadow; height 170 | NOT_STARTED |
| 4 | Row: Use my location | `rowLabel` filled: icon `location.fill` white in `farmGreenMap` 44 circle; title `geist(17,.semibold)`; chevron.right inkMuted; `creamCard` radius16 hairline → `onUseLocation` (locationManager.request) | NOT_STARTED |
| 5 | Row: Search a town instead | same, not filled: icon `magnifyingglass` farmGreen in `farmGreenSoft` circle → `onSearch` (showPlaceSearch) | NOT_STARTED |
| 6 | Resolved label | if resolved: `checkmark.circle.fill` farmGreen + text `geist(15,.semibold)` ink; pad top 20 | NOT_STARTED |
| 7 | Continue | PrimaryButtonStyle → advance | NOT_STARTED |

#### S2.4 · Details step (optional prefs)
| Order | Element | Spec | Status |
|-------|---------|------|--------|
| 1 | Kicker | "Optional" | NOT_STARTED |
| 2 | DisplayTitle | "Anything else we should *know*?" size 30 | NOT_STARTED |
| 3 | Sub | "Fine-tune what shows up. All optional." `geist(15)` inkMuted centered | NOT_STARTED |
| 4 | PrefRow ×4 | 🕒 "Open today"/"Only farms open right now"; 🧺 "Pick-your-own"/"Zelfpluk farms you can visit"; ✅ "Verified farms"/"Confirmed, up-to-date listings"; 📷 "Has photos"/"See the place before you go". Row: emoji `geist(22)` + title `geist(16,.semibold)` + subtitle `geist(13)` inkMuted; trailing **toggle** (track 46×28 `farmGreenMap` on / `#E5E4DF` off, white knob 22 offset ±9, spring 0.25); `creamCard` radius16 hairline | NOT_STARTED |
| 5 | "Show me farms" | PrimaryButtonStyle → applyPrefs=true, advance | NOT_STARTED |
| 6 | "I'll explore on my own" | `geist(16,.semibold)` inkMuted → applyPrefs=false, advance | NOT_STARTED |

#### S2.5 · Nearby step (farm shelf)
Pool: `nearbyWithImages(coord,100km)` else `feedPicks(nil,30)`. Split described(teaser)/plain, prefix 6+8, shuffled, prefix 10. Fetch batch teasers for shown.
| Order | Element | Spec | Status |
|-------|---------|------|--------|
| 1 | Kicker | "Great choice" | NOT_STARTED |
| 2 | DisplayTitle | "Here are farms *near* you" size 30 | NOT_STARTED |
| 3 | Headline | built? (coord? "N farms within 100 km of {label\|you}" : "Popular farm shops") : "Finding farms near you…" — `geist(15,.semibold)` inkMuted centered | NOT_STARTED |
| 4 | Loading | 3× SkeletonBox h180 while !built | NOT_STARTED |
| 5 | MultiImageFarmCard ×≤10 | see C4 | NOT_STARTED |
| 6 | "See all on map" | PrimaryButtonStyle → advance | NOT_STARTED |

#### S2.6 · Notify step
| Order | Element | Spec | Status |
|-------|---------|------|--------|
| 1 | Spacer | top | NOT_STARTED |
| 2 | Kicker | "Stay in the loop" | NOT_STARTED |
| 3 | DisplayTitle | "Know when new farms appear *near you*" size 28 | NOT_STARTED |
| 4 | Spacer | 60 | NOT_STARTED |
| 5 | **RingingBell(76)** | see §1.3 | NOT_STARTED |
| 6 | Spacer | flexible | NOT_STARTED |
| 7 | "Turn on notifications" | PrimaryButtonStyle → request UN authorization [alert,badge,sound] → advance | NOT_STARTED |
| 8 | "Maybe later" | `geist(16,.semibold)` inkMuted → advance | NOT_STARTED |
| 9 | Footnote | "New farm shops join Farmsy every week." `geist(15).italic()` inkMuted | NOT_STARTED |

#### S2.7 · Done step
| Order | Element | Spec | Status |
|-------|---------|------|--------|
| 1 | Spacer | top | NOT_STARTED |
| 2 | Seal | `checkmark.seal.fill`(68) farmGreen, pad bottom 22 | NOT_STARTED |
| 3 | Kicker | "Ready" | NOT_STARTED |
| 4 | DisplayTitle | "You're all *set*" size 34 | NOT_STARTED |
| 5 | DoneBullet ×3 | 🗺️ "Browse farm shops on the map"; ❤️ "Save the ones you want to visit"; 🧭 "Plan a trip across several farms". Row: emoji `geist(22)` in 44 `farmGreenSoft` circle + text `geist(16,.medium)` ink; pad top 30 | NOT_STARTED |
| 6 | Spacer | flexible | NOT_STARTED |
| 7 | "Start exploring" | PrimaryButtonStyle → finish() | NOT_STARTED |

---

### S3 · MainView (map-first shell) — `Features/Main/MainView.swift`
**PORT STATUS: IN_PROGRESS** — `android/…/features/main/MainScreen.kt`. MainScreen owns the single shared map; farm detail + secondary screens present as **detented `BottomSheetScaffold` sheets OVER the live map** — the RootNav push overlay + `openPin` were deleted. **This session:** expanded detent now **declared** (`fillMaxHeight(0.92f)`, not emergent); **background interaction scoped** — enabled at the partial detent, blocked when Expanded via a touch-consuming body overlay (Auth stays modal in RootNav); `focusPin` fly + highlighted pin via MapScreen. **Background interaction: VERIFIED** — offset-gated blocker (`sheetState.requireOffset()`) engages mid-drag above the partial detent, a faithful reproduction of iOS `upThrough: partial` (no built-in modifier, but fully expressible with primitives → not a divergence). Detent/shadow/icon PORT NOTEs → `PORT_NOTES.md`. **OPEN rows (DEFERRED — do not count toward completion):** farm-card **180 mini-detent → OPEN, DEFERRED (fix: `AnchoredDraggable` 3-anchor sheet)**; pill **shadow colour → OPEN, DEFERRED (fix: `Modifier.shadow` spot/ambient colour)**. **Discover routing: RESOLVED** — the Discover pill now routes to **WhatsNewSheet (S6)**, matching iOS (was DiscoverFeedScreen/S11). (S11 DiscoverFeedScreen remains in the tree, no longer wired to the pill.)

**"The map is the app."** No tab bar. **Reads:** SessionStore. **Writes:** selectedPin, showAuth, accountRoute, showWhatsNew, showTrips, flyTarget, detents. Installs `\.requestAuth = { showAuth = true }`. `.tint(farmGreen)`, `.ignoresSafeArea(.keyboard)`, bg cream.

| Order | Element | Spec | Status |
|-------|---------|------|--------|
| 1 | MapScreen (base) | `MapScreen(onOpenFarm:, focusPin: flyTarget)` full-bleed | NOT_STARTED |
| 2 | Bottom floating panel | overlay(.bottom) pad h14 b6: HStack(4) of 4 panelItems in **white Capsule**, pad 6, shadow black .14 r12 y3 | IN_PROGRESS — `MainScreen.kt` pill built (Capsule, pad 6, `shadowElevation=12.dp`). **Shadow colour OPEN — DEFERRED (fix: `Modifier.shadow` spot/ambient colour); `shadowElevation` can't set colour/offset.** |
| 2a | panelItem Discover | `newspaper` system(17,.semibold) + "Discover" `geist(10,.semibold)`, farmGreenMap, vpad 8 → showWhatsNew | NOT_STARTED |
| 2b | panelItem Saved | `heart` + "Saved" → requireAuth → accountRoute=.saved | NOT_STARTED |
| 2c | panelItem Trips | `map` + "Trips" → requireAuth → showTrips | NOT_STARTED |
| 2d | panelItem Settings | `gearshape` + "Settings" → requireAuth → accountRoute=.settings | NOT_STARTED |

**Sheets & detents (all cornerRadius 28, drag indicator visible):**
- Farm card: `.sheet(selectedPin != nil)` → FarmDetailView `.id(osmId)` detents `[.height(180), .fraction(0.55), .large]` sel `$farmDetent`; bg interaction up through 0.55; content scrolls.
- WhatsNew: detents `[0.55, 0.92]`, bg interaction up through 0.55.
- accountRoute (.saved/.settings): detents `[0.55, 0.92]`.
- Trips: detents `[0.5, 0.92]` sel `$tripDetent`, bg interaction up through 0.5.
- Auth: `.sheet(showAuth)`.

**openFarm(pin):** farmDetent=.55, flyTarget=pin, selectedPin=pin (swaps in place, flies map). **requireAuth:** authed → action else showAuth.

---

### S4 · MapScreen — `Features/Map/MapScreen.swift`
**PORT STATUS: IN_PROGRESS** (trip route + focusPin rows) — `android/…/features/map/MapScreen.kt`. Added the `focusPin` param (fly + highlighted pin), TripStore reads, the trip route/stops on this single shared map, and the fit-to-trip effect. **Route widths: VERIFIED** (density-exact 8:5). **focusPin fly: OPEN — DEFERRED** (fix: `Projection.visibleRegion` + `newLatLngZoom` for iOS's exact span logic; current far-out case uses a `zoom<11→12` heuristic). Highlight bitmap static (no spring) — PORT NOTE. → `PORT_NOTES.md`.

**Reads:** FarmsStore (pins, filtered, aiIntent, aiCenter, searchText, loadError), LocationManager, TripStore (tracedLine, stopIds, fitToken). **Writes:** searchText, showFilters, aiSearching, camera, visibleRegion; calls applyAISearch/clearAISearch. **Camera init:** center (51.8, 4.7) span 3.4.

**Top overlay (VStack spacing 8, pad h14 t6):**
| Order | Element | Spec | Status |
|-------|---------|------|--------|
| 1 | searchRow (Capsule) | HStack(10): leading = spinner (aiSearching) OR `sparkles`(aiIntent, farmGreenMap) OR `magnifyingglass`(inkMuted) system(15); TextField "Search or ask — e.g. cheese near you" submitLabel .search onSubmit runSmartSearch; **filter button INSIDE** = `line.3.horizontal.decrease.circle{.fill}`(21) farmGreenMap → showFilters. Pad v13 h18, **white Capsule**, shadow black .12 r8 y2 | NOT_STARTED |
| 2 | Locate button (beside) | `CircleMapButton("location.fill", size 44)` → locateNearMe | NOT_STARTED |
| 3 | AI summary bar | if aiIntent≠nil: white radius18, shadow; `sparkles`(13) farmGreenMap + summary `geist(13)` ink + `xmark.circle.fill`(16) inkMuted → clearAISearch; below: horiz scroll of chips `geist(11,.semibold)` farmGreenDeep on `farmGreenMap@12%` Capsule | NOT_STARTED |

**NO category pill. NO farms-count chip.** (Neither exists on iOS.)

**Map (`mapCard`):** `Map(position:$camera)` mapStyle standard excludingAll, ignoresSafeArea.
| Element | Spec | Status |
|---------|------|--------|
| UserAnnotation | device location dot | NOT_STARTED |
| Clusters | grid-clustered; `isCluster` → ClusterBubble (tap zoomInto); else → FarmPinView (tap onOpenFarm) | NOT_STARTED |
| Trip route | if tracedLine≥2: white casing (MapPolyline w8) aboveLabels + `routeColor #2563EB` line (solid w5 onRoads / dashed w4 [2,4]) aboveLabels | IN_PROGRESS — `MapScreen.kt` white casing Polyline zIndex1 + blue #2563EB zIndex2, dashed `Dash(22)/Gap(18)` when !onRoads; reads `trip.tracedLine()`. Origin = START of the line (azure marker **deleted**, matches iOS). **Widths: VERIFIED** — `routeCasingPx`/`routeLinePx` = `8.dp.toPx()`/`5.dp.toPx()` (exact physical size, 8:5 preserved at every density; not a divergence). **aboveLabels: ACCEPTED_DIVERGENCE** — no overlay/label z-order API on Google Maps (PORT NOTE). |
| Trip stops | numbered `TripStopMarker` above route (tap onOpenFarm) | IN_PROGRESS — `MapScreen.kt` `tripStopBitmap(i+1)` markers zIndex3, tap→onOpenFarm; excluded from clustering; no origin pin |
| Tap gesture | simultaneous TapGesture → dismissKeyboard | NOT_STARTED |
| onMapCameraChange(.onEnd) | set visibleRegion + dismissKeyboard | NOT_STARTED |
| Center overlay | loadError → text + "Retry" button (farmGreenMap); else pins empty → ProgressView(large) farmGreenMap | NOT_STARTED |

**Clustering algorithm:** grid `gridCellsAcross=10`; below span `declusterSpan=0.06` every farm individual; cell → cluster only if `count≥10` (2–9 drawn individually); bucket centroid positions marker; trip stops excluded from clustering. **zoomInto:** span/3.2 easeInOut 0.4.

**Sub-components:**
| Component | Spec | Status |
|-----------|------|--------|
| FarmPinView | `drop.fill` rotated 180° (38, or 50 highlighted → farmGreenDeep else category.color) + white circle 22/28 + emoji 12/15, offset y −5/−7, spring on highlight | IN_PROGRESS — parameterised `pinBitmap(drop, whiteCircle, emoji, …)`: normal `pinBitmap(38,22,12,cat.color)`, highlighted `pinBitmap(50,28,15,farmGreenDeep)` — **drop size now the literal manifest value (38/50)**. Highlighted drawn for `focusPin` on top, **suppressed when the farm is a trip stop** (one marker, no duplicate). PORT NOTE: static bitmaps (no spring); SF pt→px via fixed factors |
| ClusterBubble | count text `geist(15/13,.bold)` white in `farmGreenMap` circle (38/46/54 by <10/<100/else), white 2.5 stroke, shadow; "999+" cap | NOT_STARTED |
| TripStopMarker | number `geist(14,.bold)` white in `farmGreen` 34 circle, white 2.5 stroke | NOT_STARTED |
| CircleMapButton(Label) | `.white@94%` circle (44), glyph `#4B5563` system(size·0.42,.semibold), white@60 stroke, shadow black .22 r10 y3 | NOT_STARTED |
| FilterChip (defined, unused by MainView flow) | icon+title pill, farmGreenMap fill when on | N/A |
| FarmCard (legacy, unused by MainView) | bottom card — MainView uses FarmDetailView instead | N/A |

**Actions:** runSmartSearch (≥2 chars, parse, applyAISearch), flyToAIPlace (span from radiusKm), flyToFocus (keep zoom, shift center south delta·0.28), locateNearMe (span 0.5), fitToTrip (frame route, center shifted north latPad·0.55). **Haptics** on pin/cluster/search/locate.

---

### S5 · FilterSheet — `Features/Map/MapScreen.swift`
**Presents:** `.sheet` [medium,large] from search bar filter icon. **Reads/Writes:** FarmsStore filters. Loads `loadFlagsIfNeeded` on task. Bg cream. One unified list, no card, `.tapCard` rows.

| Order | Element | Spec | Status |
|-------|---------|------|--------|
| 1 | Header | "Filters" `geist(18,.bold)` ink + `xmark`(14) in `#F3F4F6` 32 circle → dismiss; pad h16 t12 b6 | NOT_STARTED |
| 2 | Row "All categories" | emoji 🍽️ in inkMuted circle, label, trailing = pins.count in `#F3F4F6` Capsule, checkmark when `!anyFilterOn` → clearAllFilters | NOT_STARTED |
| 3 | divider | hairline 1px, vpad 4 | NOT_STARTED |
| 4 | Category rows ×10 | emoji in `cat.color` circle (34), label `geist(15)` ink, checkmark(14) farmGreenMap when selected; toggle selectedCategories | NOT_STARTED |
| 5 | divider | — | NOT_STARTED |
| 6 | Quick filter rows ×5 | icon in `#F3F4F6` circle: `checkmark.seal` "Verified farm shops"; `bolt` "Open 24/7 (automaat)"; `clock` "Open today"; `leaf` "Pick your own"; `camera` "Has photos". Toggle respective flag | NOT_STARTED |
| 7 | Section header "TYPE OF PLACE" | `geist(11,.semibold)` kerning 1.1 inkMuted uppercased | NOT_STARTED |
| 8 | placeType rows ×6 | FarmAxis.placeTypes (icon+label), toggle selectedPlaceTypes | NOT_STARTED |
| 9 | Section header "HOW IT'S GROWN" | same style | NOT_STARTED |
| 10 | method rows ×4 | FarmAxis.methods, toggle selectedMethods | NOT_STARTED |

**Row structure:** 34 circle (emoji→tint fill / icon→`#F3F4F6`) + label lineLimit1 + optional trailing Capsule + checkmark; pad h16 v11, `.tapCard`.

---

### S6 · WhatsNewSheet — `Features/Map/WhatsNewSheet.swift`
**PORT STATUS: IN_PROGRESS** — `android/…/features/whatsnew/WhatsNewSheet.kt`. Built this session: header (eyebrow + circular close), posts (loading skeletons / dashed-empty / PingCard list via `FarmContentApi.feedPosts`), "FEATURED FARMS" (skeletons until `galleriesLoaded`, then MultiImageFarmCard over `farms.featuredFarms.take(10)`), recommendation carousel. Wired: **MainScreen Discover pill → WhatsNewSheet** (was DiscoverFeedScreen), as a detented sheet over the shared map (existing sheet system). **Stubbed:** photo-tap → opens farm instead of S10 lightbox (PORT NOTE; S10 unbuilt). **DEFERRED:** dashed empty-posts border (solid hairline for now; API: `drawBehind`+`dashPathEffect`).

**Presents:** `.sheet` [0.55,0.92] from Discover panel item. **Reads:** FarmsStore (featuredFarms, galleries, featuredTeasers). **Writes:** pings, lightbox. Opening a farm flies map + closes. Bg cream. Loads pings + `loadGalleriesIfNeeded` on task.

| Order | Element | Spec | Status |
|-------|---------|------|--------|
| 1 | Header | "WHAT'S NEW" `geist(11,.semibold)` kerning1.2 inkMuted + `xmark`(13) in `#F3F4F6` 32 circle → dismiss; pad h16 t16 b8 | NOT_STARTED |
| 2 | Pings loading | 2× SkeletonBox h150 while loadingPings | NOT_STARTED |
| 3 | Pings empty | `emptyPosts`: "No posts yet — check back soon." `geist(12)` inkMuted, dashed `#E5E7EB` radius16 | NOT_STARTED |
| 4 | PingCard ×N | see C3; onOpenFarm flies+opens; onOpenImage → LightboxSource (eyebrow "From a post", title author, subtitle farm, postText body) | NOT_STARTED |
| 5 | "FEATURED FARMS" header | `geist(11,.semibold)` kerning1.2 inkMuted, pad top 4 | NOT_STARTED |
| 6 | Featured loading | 3× SkeletonBox h180 while `!galleriesLoaded` | NOT_STARTED |
| 7 | MultiImageFarmCard ×≤10 | see C4 (featuredFarms prefix 10) | NOT_STARTED |
| 8 | TripRecommendations | see C1 | NOT_STARTED |
| — | Lightbox | `.fullScreenCover(item:$lightbox)` → ImageLightbox, clear bg | NOT_STARTED |

---

### S7 · FarmDetailView — `Features/Detail/FarmDetailView.swift`
**Presents:** `.sheet` [180,0.55,large] on pin tap (card open to everyone; paid fields locked *inside*). **Reads:** SessionStore, FavoritesStore, FarmsStore, TripStore, requestAuth. **Writes:** detail, teaser, isLoading, isLocked, showClaim/Paywall/SignIn, lightbox, galleryImages. Bg cream. Header pinned, footer pinned, middle scrolls.

**pinnedHeader** (pad h14 t18 b4):
| Order | Element | Spec | Status |
|-------|---------|------|--------|
| 1 | Farm name | `geist(19,.bold)` ink lineLimit1 | NOT_STARTED |
| 2 | Save circle | `heart{.fill}` (warnRed saved / `#6B7280`) 32 in `#F3F4F6` circle → saveTapped (paywall if locked) | NOT_STARTED |
| 3 | Share | ShareLink `square.and.arrow.up` → `farmsy.app/map?id=` | NOT_STARTED |
| 4 | Close | `xmark` `#6B7280` 32 circle → dismiss | NOT_STARTED |

**subHeader** (scrolls, pad h14):
| Order | Element | Spec | Status |
|-------|---------|------|--------|
| 5 | ratingRow | `star.fill`(13) star + `%.1f`(14,.semibold) ink + "(N)"(12) inkMuted, OR "No reviews yet"; tap → paywall if locked | NOT_STARTED |
| 6 | Location line | `mappin.and.ellipse`(12) inkMuted + "{city}, {NL\|BE}" `geist(14)` inkMuted | NOT_STARTED |
| 7 | badgeRow | horiz scroll: category chips (emoji+label `geist(11,.semibold)` white on `cat.color` Capsule, prefix4) + Verified (`checkmark.seal.fill` farmGreenMap outline capsule) + Open now (`#10B981` dot + text `#047857` on `#ECFDF5`) | NOT_STARTED |
| 8 | tripButton | loading→SkeletonBox h44; locked→dashed `#9CA3AF` "Plan a trip with Farmsy Pro" `lock.fill` → gateLocked; member→"Add to trip"/"In your trip" (`plus`/`checkmark`, farmGreenMap filled when in), toggle | NOT_STARTED |
| 9 | photoStrip | !photosReady→skeleton (1 big 160 + 2×76); empty→gradient tile + emoji(56); 1 photo→single 160; ≥2→big 160 + rail(76,76 with "+N" overlay on 3rd) | NOT_STARTED |

**Body (member vs locked):**
| State | Content | Status |
|-------|---------|--------|
| Loading | cardSkeleton (3 grey bars) | NOT_STARTED |
| Locked | lockedSections: teaser (`geist(15)` + "… View more" farmGreen if truncated → gateLocked) + **lockedBlock** | NOT_STARTED |
| Member | detailSections: ExpandableText(description) + FarmMemberSections (S9) | NOT_STARTED |

**lockedBlock** (the "blur" — S7's signature): ZStack: `lockedBarsBackground` (grey `inkMuted@14%` bars top [.78,.95,.6,.88] + Spacer60 + bottom [.7,.9,.5]) **`.blur(radius:7).opacity(0.6)`** + centered VStack: `lock.fill`(20) farmGreen in 48 `farmGreen@10%` circle, "Farm details are for members" `geist(16,.bold)`, "Address, phone, website and what this farm sells." `geist(14)` inkMuted, "See full details →" white on `farmGreenMap` radius16 → gateLocked. minHeight 300.

**footer** (pinned, pad h14 v12, cream + top hairline):
| Element | Spec | Status |
|---------|------|--------|
| Directions | `location.fill`/`lock.fill` outline → openDirections (MKMapItem) / gateLocked | NOT_STARTED |
| Call/Website | `phone.fill`/`globe` filled farmGreenMap → tel:/https: | NOT_STARTED |

**Sheets:** showClaim→SafariView(`/claim/<osmId>`); showSignIn→AuthView; showPaywall→LockedAccessView; lightbox→ImageLightbox (clear bg, pops from center via disabled transaction). **onChange hasFullAccess:** granted+locked → dismiss paywall + reload. **Reused:** ActionButton, InfoRow, SocialChip, ExpandableText (C7).

---

### S8 · LockedAccessView (paywall) — `Features/Detail/FarmDetailView.swift`
**Presents:** `.sheet` from detail gate. **Reads:** FarmsStore, PurchaseStore, SessionStore. **Writes:** isChecking. Loads offering on task. ScrollView.

| Order | Element | Spec | Status |
|-------|---------|------|--------|
| 1 | Kicker | isExpired ? "Welcome back" : "Members only" | NOT_STARTED |
| 2 | DisplayTitle | "Unlock every farm's *full story*" size 32 | NOT_STARTED |
| 3 | Stat row | 3 StatTiles: pins.count+ "farm shops" · "10" "categories" · "NL + BE" "coverage"; dividers h40; `.card(14)` | NOT_STARTED |
| 4 | Emoji grid | 6-col LazyVGrid of 12 emoji `geist(28)` | NOT_STARTED |
| 5 | Lock card | `lock.fill`(34) farmGreen + "Unlock every farm"/"Your membership has expired" `geist(19,.bold)` + body `geist(15)` inkMuted; `.card(22)` | NOT_STARTED |
| 6 | Error | purchaseError `geist(14,.medium)` warnRed | NOT_STARTED |
| 7 | Purchase area | isPurchasing/checking→Spinner; productsUnavailable→"Memberships can't be loaded" + "Try again"(force load); yearlyPrice nil→Spinner; else PlanButtons | NOT_STARTED |
| 7a | Yearly PlanButton | filled; label = "{N} days free" (if trial) else "Yearly"; detail = "{price} / year" or "then {price} / year"; → purchase yearly + awaitGrant | NOT_STARTED |
| 7b | Lifetime PlanButton | outlined; "Lifetime" / "{price} · One payment, yours forever"; → purchase lifetime | NOT_STARTED |
| 8 | Trial terms | if trial: "Free for {days} days, then {price} per year. Cancel anytime in Settings." `geist(12)` inkMuted | NOT_STARTED |
| 9 | Restore | "Restore purchases" `geist(14,.medium)` inkMuted → restore + awaitGrant | NOT_STARTED |
| 10 | Claim | `checkmark.seal` + "Is {name} yours? Claim it" inkMuted → onClaim | NOT_STARTED |

**PlanButton:** VStack label (`geist(17,.semibold)` minScale0.7) + detail (`geist(14)`); filled `farmGreenMap` white / outlined white `farmGreen@45` stroke; radius16 vpad16. **awaitGrant:** poll refreshProfile ×12 @1.5s until hasFullAccess. **isExpired:** canceled/expired past end.

---

### S9 · FarmMemberSections — `Features/Detail/FarmMemberSections.swift`
**Embedded** in FarmDetailView (member only). **Reads:** SessionStore. **Writes:** reviews, posts, likedIds, lightbox. Loads reviews + posts + likedIds on task. Order = web's: what people are saying → details → what's new → reviews → claim → report.

| Order | Element | Spec | Status |
|-------|---------|------|--------|
| 1 | reviewsSummary | header "What people are saying" `geist(17,.bold)`; empty→dashedNote "Be the first to review this farm."; else `star.fill`(15) star + `%.1f`(16,.bold) + "· N reviews"(14) inkMuted | NOT_STARTED |
| 2 | detailsList | header "Details"; InfoRow ×N (icon farmGreenMap + label `geist(13,.semibold)` inkMuted + value `geist(15)`; link rows green + `arrow.up.right`); `.card(6)`. Rows: Hours(clock, formatHours), Address(mappin), Phone(phone, tel:), Website(globe), Email(envelope, mailto:), Facebook/Instagram(link), Organic(leaf "Yes 🌱"), Produce(basket) | NOT_STARTED |
| 3 | whatsNew | header "What's new"; **PostComposer** (C8); empty→dashedNote "Nothing posted here today."; else FarmPostRow ×N | NOT_STARTED |
| 4 | reviewsSection | header "Reviews"; **ReviewComposer** (C8); empty→"No reviews yet. Be the first!"; else ReviewRow ×N | NOT_STARTED |
| 5 | claimBlock | "Is this your farm?" `geist(16,.bold)` + "Claim this farm" (`shield` white on farmGreenMap radius16) → onClaim + fine print | NOT_STARTED |
| 6 | reportLink | `flag`(13) + "Report incorrect info" `geist(13,.medium)` inkMuted → farmsy.app/messages | NOT_STARTED |

**FarmPostRow:** initials avatar (36, `farmGreen@12%`) + author `geist(14,.semibold)`; body `geist(14)`; FixedImageRow(images prefix3, h100, onTap→lightbox); like button (`heart{.fill}` + count, farmGreen when liked, optimistic) + report (`flag` "Report"/"Reported", once). `creamCard` radius16 hairline. **ReviewRow:** name + 5 stars(11) star; body; card. **openLightbox:** eyebrow "From a post", title author, subtitle farm.

**ReviewComposer (C8):** "Leave a review"; 5 tappable stars(22) star; TextField "Share your experience… (optional)" lines2–4 white radius14; "Submit"(80w) farmGreenMap (0.4 when rating 0) → submitReview (upsert). Prefill from existing. Bg `#F7F6F2`. **PostComposer (C8):** TextField "What's new at {name}?" lines2–5; photo previews (56, xmark remove); PhotosPicker `photo.badge.plus` "Photo" (max3); char counter (280−count); "Post"(80w) `paperplane.fill` farmGreenMap (0.4 when empty) → createPost (JPEG transcode 0.8 ≤4.5MB); "Posted to your farm on the map." Bg white radius16 hairline.

---

### S10 · ImageLightbox — `Features/Detail/ImageLightbox.swift`
**Presents:** `.fullScreenCover(item:)` clear bg; pops from center (scale+fade, not slide). **Reads:** LightboxSource. **Writes:** index, shown. Fixed frame (doesn't resize to photo).

| Order | Element | Spec | Status |
|-------|---------|------|--------|
| 1 | Veil | `Color.white@55%` + `.ultraThinMaterial` blur, ignoresSafeArea, opacity 0→1; contentShape Rectangle → tap closes | NOT_STARTED |
| 2 | panel | frame min(w−32,440)×min(h−64,620), opacity 0→1, scale 0.94→1, zIndex1; white radius24 + hairline + shadow black .18 r30 y12 | NOT_STARTED |
| 2a | header | eyebrow `geist(11,.semibold)` kerning1.1 inkMuted uppercased; title `geist(14,.semibold)` ink; subtitle `geist(12)` farmGreenMap; counter "n / N" `geist(13,.semibold)` inkMuted (if many); close `xmark`(14) `#6B7280` 36 circle | NOT_STARTED |
| 2b | postText | if present: `geist(14)` ink lineLimit3, pad | NOT_STARTED |
| 2c | picture | base `#F3F4F6` radius16 + AsyncImage overlay scaledToFill clipShape + **contentShape** (fixes overflow hit-test); left/right arrows (`chevron.left/right`(18) `#374151` in white@90 40 circle) when many; pad h16 b16 | NOT_STARTED |

**Animations:** appear easeOut 0.22; close easeIn 0.15 then onClose. **step(±1):** wraps both ends, easeOut 0.24. **Empty/error:** AsyncImage failure → `photo`(40) inkMuted; loading → ProgressView.

---

### S11 · DiscoverFeedView — `Features/Discover/DiscoverFeedView.swift`
**Note:** On phone the map's Discover panel opens WhatsNewSheet; this standalone feed view exists and shares components. **Reads:** FarmsStore, LocationManager, SessionStore, requestAuth. **Writes:** feed, pings, showAddFarm. Pull-to-refresh reshuffles + reloads pings.

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
**Presents:** `.sheet` [0.55,0.92] from Saved panel item. **Reads:** FarmsStore, FavoritesStore, LocationManager, SessionStore, requestAuth. `savedPins` = saved ∩ pins, sorted by distance. Bg cream.

| Order | Element | Spec | Status |
|-------|---------|------|--------|
| 1 | Header | "SAVED FARMS" `geist(11,.semibold)` kerning1.2 inkMuted + `xmark`(13) `#6B7280` 32 `#F3F4F6` circle → dismiss; pad h16 t16 b8 | NOT_STARTED |
| 2a | Guest state | 🤍(54) + "Keep your favourites" `display(24)` + "Sign in to save farms and find them here on every device." `geist(15)` inkMuted centered + "Sign in" PrimaryButtonStyle → requestAuth | NOT_STARTED |
| 2b | Signed-in list | Numbered slot list, `slots = max(8, count)`, in `creamCard` radius16 + hairline card | NOT_STARTED |
| — savedRow | index `geist(13,.bold)` white in `farmGreenMap` 30 circle + name `geist(15,.semibold)` + city `geist(12)` inkMuted + `xmark`(13) inkMuted 32 (`.tapCard` remove); row `.tapCard(excludeTopTrailing 44)` → onOpenFarm; pad h12 v11 | NOT_STARTED |
| — emptyRow | index inkMuted@60 in dashed `#E5E7EB` circle + "Tap a heart to save a farm" `geist(15)` inkMuted + "—" | NOT_STARTED |
| — divider | between rows, leading 62 | NOT_STARTED |
| 3 | Carousel | TripRecommendations (C1) below list | NOT_STARTED |

---

### S13 · SettingsSheet — `Features/Settings/SettingsSheet.swift`
**Presents:** `.sheet` [0.55,0.92] from Settings panel item. **Reads:** SessionStore, LanguageManager, requestAuth. **Writes:** showLanguage/SignOutConfirm/DeleteInfo, isDeleting, deleteFailed, deletedState. Refresh profile on task. Bg cream.

| Order | Element | Spec | Status |
|-------|---------|------|--------|
| 1 | Header | "SETTINGS" `geist(11,.semibold)` kerning1.2 inkMuted + `xmark` `#6B7280` 32 circle → dismiss; pad h16 t16 b10 | NOT_STARTED |
| 2a | Account card (authed) | `FarmsyLogo`(42) + email `geist(15,.semibold)` lineLimit1 + plan "{Plan} plan"/"Farmsy account" `geist(13)` inkMuted + **subscription badge** (Member/Trial/Canceled/Expired/Free w/ color) `geist(12,.bold)` white Capsule; `.card()` | NOT_STARTED |
| 2b | Guest card | `FarmsyLogo`(42) + "You're browsing as a guest" + "Sign in to save farms and see details" + "Sign in" (white on farmGreenMap Capsule) → requestAuth | NOT_STARTED |
| 3 | MembershipSection (authed) | "Membership" label + card: lifetime → "You have Lifetime access" / "One payment, never expires. Nothing to manage."; hasAccess → "Your membership is ending"/"Your trial is active"/"You're on the Yearly plan" + subtitle (charge/cancel date) + "Manage subscription" (farmGreen, → billingURL by source); expired → "Your membership has expired" + "Renew…"; else → "You don't have a membership yet" + "Unlock full details for every farm." | NOT_STARTED |
| 4 | Prefs card (`.card(4)`) | SettingsRow ×: Language(globe `#3F5E3A`, value = System/lang name → showLanguage); Notifications(bell.fill `#F5B301` → app settings); Refer friends(gift.fill `#EC4899`, authed → web invite); Contact us(envelope.fill `#38BDF8` → web) | NOT_STARTED |
| 5 | Legal card | Privacy Policy(hand.raised.fill `#8B5CF6`); Terms of Service(doc.text.fill `#64748B`) | NOT_STARTED |
| 6 | Account card (authed) | Sign out(rectangle.portrait.and.arrow.right `#3F5E3A` → confirm); Delete account(trash.fill `#DC2626`, warnRed → alert) | NOT_STARTED |
| 7 | Version | "Farmsy for iOS {version}" `geist(12)` inkMuted@70 | NOT_STARTED |

**SettingsRow:** icon(15,.semibold) tint in 34 tint@14% circle + label `geist(16,.medium)` + optional value `geist(14,.medium)` inkMuted + `chevron.right`(13) inkMuted@50; pad v14 h14; Haptics.tap. **Dialogs:** sign-out confirmationDialog; delete alert (Apple 5.1.1v — in-app) → deleteAccount → deletedState or deleteFailed alert; isDeleting overlay (dim + spinner); deletedState → AccountDeletedView fullScreenCover.

---

### S14 · LanguagePickerSheet — `Features/Settings/SettingsSheet.swift`
**Presents:** `.sheet` [medium,large] from Settings Language row. **Reads/Writes:** LanguageManager. Bg cream.

| Order | Element | Spec | Status |
|-------|---------|------|--------|
| 1 | Header | "LANGUAGE" `geist(11,.semibold)` kerning1.2 inkMuted + `xmark` 32 circle → dismiss | NOT_STARTED |
| 2 | Language rows ×5 | System 🌐 / English 🇬🇧 / Nederlands 🇳🇱 / Français 🇫🇷 / Deutsch 🇩🇪 (endonyms); flag(22) + name `geist(16,.medium)` ink + `checkmark`(14,.bold) farmGreen when current; pad v14 h14; `.card(4)`; divider leading 50 | NOT_STARTED |
| 3 | Restart prompt (if changed) | "Restart Farmsy to apply your new language." (in the *newly chosen* language) `geist(13)` inkMuted + "Restart now" (`arrow.clockwise`) white on farmGreenMap radius14 → `exit(0)`; bg `#F3F6F2` radius16 | NOT_STARTED |

**Android note:** per-app locale applies live (no restart) — Android should skip the restart prompt.

---

### S15 · AccountDeletedView — `Features/Settings/SettingsSheet.swift`
**Presents:** `.fullScreenCover` terminal after delete. Bg cream, pad 28.

| Order | Element | Spec | Status |
|-------|---------|------|--------|
| 1 | Seal | `checkmark.circle.fill`(56) farmGreen | NOT_STARTED |
| 2 | Title | "Your account was deleted" `display(24)` ink centered | NOT_STARTED |
| 3 | Body | "Your profile, saved farms and subscription data have been removed. Thanks for trying Farmsy." `geist(15)` inkMuted | NOT_STARTED |
| 4 | Store reminder (if remindStore) | "Your membership was bought through {Google Play\|our website\|the App Store}. Cancel it there so you aren't charged again." `geist(14,.medium)` in `creamCard` radius16 | NOT_STARTED |
| 5 | "Done" | white on farmGreenMap radius16 → onDone (dismiss, back to guest) | NOT_STARTED |

---

### S16 · AuthView — `Features/Auth/AuthView.swift`
**Presents:** `.sheet` via requestAuth. **Default mode = `.logIn`** (not signup). **Reads:** SessionStore, pendingRefCode. **Writes:** mode, step, all fields, isWorking, errorMessage, verifySentTo. ScrollView, bg cream. Auto-dismiss on `session.isAuthenticated`.

| Order | Element | Spec | Status |
|-------|---------|------|--------|
| 1 | Logo header | `FarmsyLogo`(54) + "Farmsy" `display(30)` + "Local farms, fresh finds" `geist(13)` inkMuted; pad t34 b30 | NOT_STARTED |
| 2 | Title | signUp "Create your free account" / logIn "Welcome back" `display(28)` | NOT_STARTED |
| 3 | Subtitle | signUp "See every farm on the map in seconds." / logIn "Log in to pick up where you left off." `geist(15)` inkMuted | NOT_STARTED |
| 4 | Fields — step1/login | Email (placeholder "you@email.com", email kbd, no autocap); Password (placeholder "At least 8 characters", secure, reveal eye); if signUp: Confirm password ("Repeat password", secure) | NOT_STARTED |
| 4b | Fields — step2 (signup) | First+Last name (HStack); **DOBField** (wheel picker, ≤ today−16); Street (optional); City+Postal (HStack, optional); Country (required); Referral code (optional, uppercased) | NOT_STARTED |
| 5 | Error | errorMessage `geist(14,.medium)` warnRed | NOT_STARTED |
| 6 | Submit | isWorking→Spinner; else logIn "Log in" / step1 "Continue" / step2 "Create account"; PrimaryButtonStyle, disabled when !canSubmit (opacity 0.55) | NOT_STARTED |
| 7 | Back (signup step2) | "Back" `geist(15)` inkMuted → step1, spring 0.3 | NOT_STARTED |
| 8 | Mode toggle | "Already have an account?"/"New to Farmsy?" inkMuted + "Log in"/"Create one" farmGreen semibold → toggle mode, spring 0.35 | NOT_STARTED |

**AuthField:** label `geist(14,.semibold)` farmGreen + field (`geist(17)`) with placeholder; secure→SecureField + reveal eye (`eye`/`eye.slash`); bg white@60 radius16 + inkMuted@25 stroke. **Validation:** credentialsOK (email has @, pw≥8 signup / non-empty login, confirm==pw); detailsOK (name+country). **Transitions:** step change spring 0.3 (fields slide); mode change spring 0.35. **submit:** signup step1 just advances; else API call → verifySentTo (signup) or setSession (login). **DOBField:** compact DatePicker, range ...today−16, white radius16. **Haptics:** tap on toggles, success on completion, warning on error.

---

### S17 · VerifyEmailView — `Features/Auth/AuthView.swift`
**Presents:** in-place swap after successful signup (no session yet).

| Order | Element | Spec | Status |
|-------|---------|------|--------|
| 1 | 📬 | system(46) | NOT_STARTED |
| 2 | Title | "Check your inbox" `display(26)` | NOT_STARTED |
| 3 | Body | "We've sent a verification link to {email}. Click it to activate your account, then log in." `geist(15)` inkMuted centered | NOT_STARTED |
| 4 | "Log in" | PrimaryButtonStyle → onDone (reset to logIn, step1) | NOT_STARTED |

---

### S18 · TripsView — `Features/Trips/TripsView.swift`
**PORT STATUS: IN_PROGRESS** — `android/…/features/trips/TripsScreen.kt`. Deleted the private 280dp `TripMap` (+`numberedPin`); TripsScreen is sheet content over the ONE shared map (route/stops render there via MapScreen + `trip.fitToken`). Presented as a `BottomSheetScaffold` [0.5,0.92] sheet. **This session:** the `collapsed` state now **actually tucks the overview list** (`if (!collapsed)` around the Trip-overview Column) + `KeyboardArrowUp` dragUpHint — reached by **dragging** the sheet down (collapsed = PartiallyExpanded). Deleted the 4 dead vals (`routeLine`/`traceProgress`/`fitToken`/`traced`) + 14 dead map imports left by the map removal. PORT NOTE (detent/collapsed) → `PORT_NOTES.md`. **Still open:** origin town-search (S19 PlaceSearchSheet unported).

**Presents:** `.sheet` [0.5,0.92] sel `$detent`. Two tabs (Plan / My trips). **Reads:** FarmsStore, LocationManager, SessionStore, TripStore. **Writes:** tab, naming, tripName, armedDelete, reorderNote, showOriginSearch. Bg cream. `collapsed` = detent==0.5. Refresh route + loadTrips on task; refreshRoute on stopIds change.

**Header + tabs:**
| Order | Element | Spec | Status |
|-------|---------|------|--------|
| 1 | Header | "TRIP PLANNER" `geist(11,.semibold)` kerning1.2 inkMuted + `xmark` `#6B7280` 32 `#F3F4F6` circle → dismiss | NOT_STARTED |
| 2 | Tabs | "Plan a trip" / "My trips": `geist(15,.bold)`, selected white on `farmGreenMap` radius14, unselected white + hairline stroke; Haptics | NOT_STARTED |

**Plan tab:**
| Order | Element | Spec | Status |
|-------|---------|------|--------|
| 3 | Origin bar | `magnifyingglass`(15) inkMuted + label/"Choose a starting point" `geist(15)` + (`xmark.circle.fill` clear OR `location.circle` farmGreenMap); white Capsule + hairline → showOriginSearch | NOT_STARTED |
| 4 | Collapsed | dragUpHint (`chevron.up`(13) inkMuted@80) only | NOT_STARTED |
| 5 | Trip overview | "Trip overview" `geist(16,.bold)`; Divider; scroll of rows `max(stops, 5)`; white radius16 hairline; grows to fill | NOT_STARTED |
| — filledRow | index `geist(12,.bold)` white in `farmGreenMap` 28 circle + name `geist(15,.semibold)` + legLabel `geist(12)` inkMuted + checkmark if selected + `xmark` remove; selected bg `farmGreenMap@8%`; tap → dismiss+openFarm | NOT_STARTED |
| — emptyRow | dashed index circle + "Pick a farm on the map" `geist(15)` inkMuted + "—" | NOT_STARTED |
| 6 | reorderNote | if present `geist(12)` farmGreenMap | NOT_STARTED |
| 7 | "Best order" (≥3 stops) | `geist(14,.semibold)` farmGreen → optimise + note "Reordered · about N km shorter"/"Already the shortest order" | NOT_STARTED |
| 8 | modeSelector | 3 buttons car/bike/walk: icon+label `geist(13,.semibold)`, selected white on `farmGreenMap` radius12, else white+hairline → setMode + refreshRoute | NOT_STARTED |
| 9 | totalsBar | `point.topleft…curvepath`(15) farmGreenMap + text ("Add farms to see…" / "Finding the road…" / "~N km · Xh Ym{( est.)}") + mode icon; bg `#F3F6F2` radius16 | NOT_STARTED |
| 10 | Actions | "Save trip"(bookmark, outline, disabled empty)→naming; "Show route"(location.north.fill, filled, disabled !canRoute)→refreshRoute+fit+dismiss; "Open in Google Maps"(arrow.up.forward.square, outline)→maps URL | NOT_STARTED |

**My trips tab:**
| State | Content | Status |
|-------|---------|--------|
| Signed out | gate: `lock.fill`(34) + "Sign in to view your trips" | NOT_STARTED |
| Not Pro | gate: "Saved trips are a Farmsy Pro feature." | NOT_STARTED |
| Member | Draft banner ("No trip in progress"/"A draft with N stops is waiting", dashed → Plan); collapsed→dragUpHint+carousel; else My Trips list (`max(count,6)`) + carousel | NOT_STARTED |
| — savedRow | index in farmGreenMap circle + name `geist(15,.bold)` + "N farms · {date}" `geist(12)`; `xmark`/`trash.fill` armed-delete (2-tap); tap → openTrip + Plan + detent 0.5 | NOT_STARTED |
| — savedEmptyRow | dashed index + "Plan a trip to fill this" + `plus` farmGreenMap → Plan | NOT_STARTED |

**Alerts:** naming ("Name your trip" TextField "My weekend trip" → save). **Sheet:** showOriginSearch → PlaceSearchSheet [large]. **save():** save + Haptics.success + clear + clearOrigin + tab=.mine. **openGoogleMaps:** origin+dest+waypoints URL with travelmode. **TripRecommendations (C1):** cardHeight 156, 2-per-page pager.

---

### S19 · PlaceSearchSheet — `Features/Trips/PlaceSearch.swift`
**Presents:** `.sheet` [large] from Trips origin / Onboarding location. **Reads/Writes:** PlaceSearch (MKLocalSearchCompleter, biased NL/BE). Bg cream, autofocus.

| Order | Element | Spec | Status |
|-------|---------|------|--------|
| 1 | Header | "STARTING POINT" `geist(11,.semibold)` kerning1.2 inkMuted + `xmark` 32 circle → dismiss | NOT_STARTED |
| 2 | Search bar | `magnifyingglass`(15) inkMuted + TextField "Search a town, address or postcode" `geist(15)` + `xmark.circle.fill` clear; white Capsule + hairline | NOT_STARTED |
| 3 | Use my location row | `location.fill`(15) farmGreenMap in 34 `farmGreenMap@12%` circle + "Use my location" `geist(15,.semibold)` → onLocate + dismiss | NOT_STARTED |
| 4 | Result rows | `mappin.circle`(16) inkMuted + title `geist(15)` + subtitle `geist(12)` inkMuted → resolve → onPick + dismiss; divider leading 62 | NOT_STARTED |
| — | scroll | scrollDismissesKeyboard immediately | NOT_STARTED |

**Android:** replace MKLocalSearchCompleter with Google Places Autocomplete (region-biased NL/BE).

---

### S20 · AddFarmView — `Features/Submit/AddFarmView.swift`
**Note:** iOS now routes "add farm" to the **web form** via SafariView; this native form stays live but unused. **Reads:** SessionStore, LocationManager. **Writes:** form, selectedCategories, photos, pinCoordinate, mapCamera, isSubmitting, errorMessage, isDone. NavigationStack, title "Add a farm shop" inline, Cancel toolbar.

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
**Note:** iOS now routes claim to **web** (`/claim/<osmId>`) via SafariView; native form stays live but unused. **Reads:** SessionStore. **Writes:** fullName, email, phone, method, kvkNumber, message, isSubmitting, errorMessage, isDone. NavigationStack, title "Claim this farm" inline, Cancel toolbar. Prefills email from session.

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
| Header | "RECOMMENDATIONS NEAR YOU"/"RECOMMENDATION" `geist(11,.semibold)` kerning1.2 inkMuted | NOT_STARTED |
| Loading | 2× SkeletonBox radius16 h=cardHeight | IN_PROGRESS — `discover/RecommendationCarousel.kt` `!built` → 2× `SkeletonBox(cornerRadius=16, weight1·height cardHeight)` |
| recCard | cover photo (fill, radius16, `#EDE7DD` base) + bottom gradient scrim + name `geist(14,.bold)` white + city `geist(12,.medium)` white@85 + category tags (`geist(9,.bold)` white on white@22 Capsule, prefix2) + save heart(13) top-right (warnRed saved) + hairline stroke; tap → onOpenFarm | NOT_STARTED |
| pager | `< • • • >` chevrons(13,.bold) (disabled at ends) + dots (farmGreenMap current / inkMuted@30) | NOT_STARTED |
| Empty | built && shown empty → render nothing | IN_PROGRESS — RecommendationCarousel `if (built && shown.isEmpty()) return` |
| select()/build | loadGalleriesIfNeeded → guard pins → select (exclude planned+stops+favs; nearbyWithImages 100km else photo'd·shuffled; described+plain take6+take6 shuffled take6) | IN_PROGRESS — `RecommendationCarousel.kt` LaunchedEffect now calls `farms.loadGalleriesIfNeeded()` **before** the pins guard + select, matching iOS `load()` order |

### C2 · DiscoverFeedCard — `Features/Discover/DiscoverFeedView.swift`
Featured photo tile h210 radius16. Photo fill (or category tile) + bottom gradient (black .80→.22→clear) + name `geist(16,.bold)` white lineLimit2 + city/distance/rating row (white) + category chips(prefix2, `geist(10,.bold)` ink on white@95 Capsule) + "TOP PICK" badge top-left (verified, `geist(9,.bold)` farmGreen on white@92) + save heart top-right (34, warnRed saved) + shadow; tap → onOpen. **Status:** NOT_STARTED

### C3 · PingCard — `Features/Discover/DiscoverFeedView.swift`
Post card `creamCard` radius16 hairline. Two sibling tap regions (header+text → openFarm; photos → openImage). Initials avatar(40, farmGreen@12) + author `geist(14,.semibold)` + farmName `geist(12,.medium)` farmGreenMap + timeAgo `geist(11)` inkMuted; body `geist(14)` lineLimit3; FixedImageRow(prefix3, h100); like row (`heart`(12) + count) inkMuted → openFarm. **Status:** IN_PROGRESS — `whatsnew/WhatsNewSheet.kt PingCard`; timeAgo matches iOS — only `time_just_now` is localized (en/nl/fr/de); the unit letters `m`/`h`/`d` are the default-`values` fallback in every locale (invented nl `u` / fr `j` / de `t` + French `many` class removed, see PORT NOTES); tap regions use `tapCard`; photo tap stubbed → openFarm (S10 unbuilt)

### C4 · MultiImageFarmCard — `Features/Map/WhatsNewSheet.swift`
Featured farm as post. `creamCard` radius16 hairline. Round profile (first photo, 40) + name `geist(14,.bold)` + city `geist(12)` inkMuted + save heart top-right(17); teaser `geist(13)` lineLimit3; FixedImageRow(prefix3, h96); `tapCard(excludeTopTrailing 52)` → onOpen. **Status:** IN_PROGRESS — `whatsnew/WhatsNewSheet.kt MultiImageFarmCard`; teaser is a **static** 3-line clamp (`buildAnnotatedString`, `maxLines=3`, ellipsis) with a decorative farmGreen/bold "  … View more" appended only when `teaser.length > 140` — matching iOS (non-interactive; the card's `tapCard` opens the farm). C7 ExpandableText is **not** used here (reserved for S7). Card + heart use `tapCard`.

### C5 · FixedImageRow — `Features/Discover/DiscoverFeedView.swift`
Equal fixed tiles (base `#F3F4F6` radius10 + AsyncImage fill clipped). Single photo padded to half-width (clear filler). Optional per-index tap. Per-tile `SkeletonBox` while an image loads (`SubcomposeAsyncImage`). **Status:** IN_PROGRESS — `whatsnew/WhatsNewSheet.kt FixedImageRow`

### C6 · FarmCard — `Features/Map/MapScreen.swift`
Legacy bottom card (name+distance+chevron, address, category emojis+rating+heart, white radius16). **MainView uses FarmDetailView instead — N/A for port unless a compact card is wanted.** **Status:** N/A

### C7 · ExpandableText — `Features/Detail/FarmDetailView.swift`
Description clamped to 3 lines; "… View more" (farmGreen bold) at end of last visible line; expands inline to full + "View less"; `.tapCard` toggle. **Status:** IN_PROGRESS — shared `ui/theme/Components.kt ExpandableText` (truncation via `TextLayoutResult.hasVisualOverflow` + `getLineEnd`, not the binary-search font measurement — DEFERRED for exact-fit, see PORT NOTES "C7 ExpandableText — clamp/truncation measurement"; `tapCard` toggle). Built once here; **S7 reuses this component.** C4 does **not** use it (C4 is a static clamp — see C4 row). `ClampedDescription` not ported (S11 concern).

### C8 · PostComposer / ReviewComposer — `Features/Detail/FarmMemberSections.swift`
Covered under S9. **Status:** NOT_STARTED

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

