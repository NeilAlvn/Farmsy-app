# PORT NOTES

Places where the Android port diverges from iOS. Each note names the iOS construct,
the Android substitute, and where it lives. **Every entry carries one of two
statuses:**

- **ACCEPTED_DIVERGENCE** — the platform *cannot* express the iOS behavior. Names the
  specific missing API. The manifest row is closed as an accepted divergence.
- **DEFERRED** — the behavior *is* expressible, but not built (or built differently
  than ideal). Names the API that would do it. **The manifest row stays OPEN and does
  not count toward completion** until implemented.

---

## Farm-card 180px mini-detent (S3 · MainScreen)

**Status: DEFERRED** (API: `AnchoredDraggable` with three anchors). iOS's farm card has
three stops `[.height(180), .fraction(0.55), .large]` (`MainView.swift:49`).
`BottomSheetScaffold` exposes exactly one intermediate stop (`sheetPeekHeight` =
PartiallyExpanded) plus Expanded, so it expresses the 0.55 partial OR the 180 mini-detent,
not both. Reproducing all three stops needs a custom `AnchoredDraggable` sheet. The farm
card uses `[0.55, 0.92]`; the 180 mini-detent is dropped. Manifest S3 farm-card detent row
stays OPEN. File: `features/main/MainScreen.kt`.

## Highlighted pin — static bitmap, no spring (S4 · MapScreen)

**Status: DEFERRED** (API: an animated `Marker` icon — swap `BitmapDescriptor`s across an
`Animatable`/`animateFloatAsState` scale, or draw the highlighted pin as a Compose overlay
positioned via `Projection.toScreenLocation` and spring its scale). iOS pops the
highlighted farm pin with a spring scale; Android renders a single static highlight bitmap
(`highlightedPinBitmap`). Expressible (per-scale bitmap regeneration is coarse but works; a
Compose-overlay marker is smooth), unbuilt → DEFERRED. Manifest S4 highlight row stays OPEN.
File: `features/map/MapScreen.kt` `highlightedPinBitmap`.

## Route aboveLabels ordering — ACCEPTED_DIVERGENCE (S4 · MapScreen)

**Status: ACCEPTED_DIVERGENCE** — missing API: no public overlay/label z-ordering on `GoogleMap`/`Polyline`; overlays always render beneath base-map labels, and `zIndex` orders overlays only among themselves.

**iOS:** the route polylines use `.mapOverlayLevel(level: .aboveLabels)` so the line
draws over the base map's place/road labels.

**Android:** Google Maps (and its Compose wrapper) render **all** overlays —
polylines included — in a layer **beneath** the base map's labels; there is **no
public API** to raise an overlay above the label layer. `Polyline.zIndex` only orders
overlays relative to one another (casing vs line vs markers), not relative to base-map
labels. So `aboveLabels` **cannot be expressed** on Google Maps; the route renders
correctly among the overlays but map labels can draw over it. File:
`features/map/MapScreen.kt` (route block, inline PORT NOTE).

## ImageLightbox veil backdrop blur (S10 · ImageLightbox)

**Status: DEFERRED** (API: `WindowManager.LayoutParams.FLAG_BLUR_BEHIND` +
`WindowManager.LayoutParams.blurBehindRadius`, API **31+**). Manifest S10 Veil row stays
OPEN for the blur.

**iOS:** the veil is `Color.white.opacity(0.55).background(.ultraThinMaterial)`
(`ImageLightbox.swift:42-43`) — a soft blur of the map/sheet behind, not just a white
wash.

**Android:** the white@55% wash is exact. The **blur behind** the Dialog window is
expressible on API 31+ via `FLAG_BLUR_BEHIND` + `blurBehindRadius` on the dialog's
`window`, but it (a) has no equivalent below API 31 (minSdk is 26) and (b) also depends
on the device/OS "allow blur" setting being on, so it can silently no-op even on 31+.
Expressible-with-caveats but unbuilt → DEFERRED, not accepted. File:
`features/detail/ImageLightbox.kt` `ImageLightbox`.

## Drop-shadow radius + y-offset (S10 panel & arrows; also S3 pill)

**Status: DEFERRED** (API: a custom shadow layer — `graphicsLayer`/`RenderNode`
`setShadowColor` with an explicit translation, or a `drawBehind` blurred shape — since
`Modifier.shadow` exposes `elevation`/`shape`/`ambientColor`/`spotColor` but **no blur
radius or offset**). Manifest S10 panel/arrow shadow details stay OPEN. (The S3 pill's
exact shadow was built this way — see `MainScreen.kt` `capsuleShadow`, a `drawBehind` +
`BlurMaskFilter` that carries colour + blur radius + a real y-offset; the same technique
would close S10's panel/arrow shadows, it is simply not built here since S10 is out of
this session's scope.)

**iOS:** panel `shadow(color: .black.opacity(0.18), radius: 30, y: 12)`
(`ImageLightbox.swift:95`); arrows `shadow(color: .black.opacity(0.15), radius: 6, y: 2)`
(`:194`).

**Android:** the shadow **colour** is matched (`Modifier.shadow(spotColor/ambientColor =
black@.18 / black@.15)`), and `elevation` approximates the blur (panel 20.dp, arrow
4.dp). The exact **radius** and **downward y-offset** are not settable on
`Modifier.shadow` — the spot-shadow direction is derived from the system light source,
not a parameter. Reproducing radius 30 / y 12 exactly needs a custom shadow layer.
Expressible, unbuilt → DEFERRED. File: `features/detail/ImageLightbox.kt` `Panel`/`Arrow`.
(Same root cause as the S3 pill-shadow DEFERRED.)

## lockedBlock faux-bars blur on API < 31 (S7 · FarmDetailView, Pass 2)

**Status: DEFERRED** (API: `android.renderscript.ScriptIntrinsicBlur` — available API
17–30, deprecated at 31 — blurs a `Bitmap`; the faux bars are static grey rectangles, so
they can be rasterised to a bitmap, blurred, and drawn, or pre-rendered as a blurred
drawable). `Modifier.blur` (the `RenderEffect` path used on API 31+) is 31-only, but it is
**not** the only way to express this blur — so the platform CAN do it pre-31 and this is a
DEFERRED (unbuilt), not an ACCEPTED_DIVERGENCE. Manifest §S7 lockedBlock row stays OPEN
for the <31 blur. (Distinct from the S10 veil note, also DEFERRED, which blurs *behind* a
Dialog window via `FLAG_BLUR_BEHIND`.)

**iOS:** `lockedBarsBackground.blur(radius: 7).opacity(0.6)` (`FarmDetailView.swift:566`)
— the faux grey bars behind the membership ask are softly blurred so they read as
"there is more here," never as real content.

**Android:** `Modifier.blur(7.dp).alpha(0.6f)` is applied and renders correctly on
API 31+. On API 26–30 `Modifier.blur` is a documented no-op, so the faux bars render
*sharp* at 0.6 alpha instead of blurred. The bars are fake, empty placeholders
(`inkMuted@0.14`), so even unblurred they stay faint behind the centred ask — graceful
degradation, not a broken state. The <31 blur is expressible via `ScriptIntrinsicBlur`
(above) but unbuilt → DEFERRED, row OPEN.
File: `features/detail/FarmDetailScreen7.kt` `LockedBlock`.

## Web claim (and other SafariView pages) use a plain Intent, not an in-app browser (S9 · S7)

**Status: DEFERRED** — API that would do it: `androidx.browser.customtabs.CustomTabsIntent`
(Chrome Custom Tabs), which requires adding the `androidx.browser:browser` dependency
(NOT currently in `app/build.gradle.kts`). Manifest §Frameworks (:238) already tracks
"SafariServices (SFSafariViewController) → Chrome Custom Tabs" as NOT_STARTED; this
formalizes it.

**iOS:** the claim link opens `https://www.farmsy.app/claim/<encoded osmId>` in a
`SafariView` (SFSafariViewController) — an **in-app** browser overlay; the user taps
Done to return (`FarmDetailView.swift:63,498-500`). The submit/claim web pages use the
same wrapper. (Note: iOS's *other* outbound links — detailsList website/email/phone,
reportLink — use `UIApplication.shared.open`, which is **external**; those map exactly
to Android `Intent.ACTION_VIEW` and are NOT deferred.)

**Android:** `openClaim` (and the reportLink) use `Intent.ACTION_VIEW` — the external
browser, consistent with every other web link in the app (Settings terms/privacy,
Trips map links). This is dependency-free and functional. The only gap vs iOS is
in-app (Custom Tab) vs external browser for the *claim* URL specifically. Expressible
with `androidx.browser`, unbuilt (a build-config decision left to the user) → DEFERRED.
Files: `features/detail/FarmDetailScreen7.kt` `openClaim`, `features/detail/FarmMemberSections.kt` reportLink.


## AuthField autofill hints (S16 · AuthView)

**Status: DEFERRED** (API: `Modifier.semantics { contentType = ContentType.EmailAddress
/ .Password / .NewPassword / .PersonName… }`, Compose Foundation **1.8+**; the current
`compose-bom:2025.01.01` ships Compose 1.7.x, where this clean per-field autofill API is
not available — only the older experimental `LocalAutofill` + `AutofillNode` wiring is,
which is verbose and per-field). Manifest S16 rows 4/4b stay OPEN for autofill hints.

**iOS:** each AuthField sets `.textContentType(...)` — `.emailAddress`, `.password` /
`.newPassword`, `.givenName`/`.familyName`, `.fullStreetAddress`, `.addressCity`,
`.postalCode`, `.countryName` (`AuthView.swift:124-164`) — so the keyboard/password manager
offers the right autofill.

**Android:** the fields carry keyboard type + capitalization + secure entry (all matched
this session), but **no autofill content hints**. Expressible cleanly only after a Compose
1.8 bump (`ContentType` semantics), or messily now via the experimental Autofill API →
DEFERRED, not built. File: `features/auth/AuthSheet.kt` `AuthField`.

## Place search provider — Photon, not iOS's MKLocalSearchCompleter (S19 · PlaceSearchSheet)

**Status: DEFERRED** (API: **Google Places Autocomplete (New)** — `com.google.android.libraries.places` with session tokens, or the Places REST endpoint — which is the closest match to iOS `MKLocalSearchCompleter`'s type-ahead + quality; it needs "Places API (New)" enabled on the Cloud project + billing). Manifest S19 stays OPEN for exact-iOS-parity provider.

**iOS:** `MKLocalSearchCompleter` — free, on-device, type-ahead, NL/BE-biased, with Apple's address/typo quality and a completer→`MKLocalSearch` resolve step (`PlaceSearch.swift`).

**Android:** there is no free on-device autocomplete equivalent. The chosen provider is **Photon** (OSM-based, `photon.komoot.io/api`) — a plain Ktor GET, no key, no dependency, coordinates in the same response. Evaluated live (NL+BE towns rank #1, typos and `'s-Gravenhage`→Den Haag resolve, postcodes need the proximity bias that is applied). What is given up vs Google Places / iOS: no SLA (public instance, "extensive usage will be throttled", no published limit, no availability guarantee), and weaker fuzzy-matching than Apple/Google on very messy input. Photon is self-hostable (Apache-2.0) if the public instance throttles — the client code would not change. Google Places would restore exact parity but reintroduces the billing/Cloud-config Photon was chosen to avoid → DEFERRED, not built. Nominatim is **not** an option: its usage policy prohibits client-side autocomplete outright. File: `features/trips/PlaceSearchSheet.kt` `photonSearch`.

