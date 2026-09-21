import SwiftUI

/// Five tabs, one floating pill: Home · Shopping · Map · Discover · Community.
/// Profile opens from the Home header. Sheets that any tab can raise — the farm
/// card, sign-in, the trip planner, the membership sheet — live here once, and
/// screens reach them through `ShellActions` in the environment.
enum AppTab: String, CaseIterable, Identifiable {
    case home, shopping, map, discover, community
    var id: String { rawValue }

    var title: String {
        switch self {
        case .home: String(localized: "Home")
        case .shopping: String(localized: "Shopping")
        case .map: String(localized: "Map")
        case .discover: String(localized: "Discover")
        case .community: String(localized: "Community")
        }
    }

    /// Farmsy's own glyphs (Assets.xcassets/Tabs): a farmhouse, a basket, a
    /// folded map, a seedling, two people. Outline at rest, solid when selected.
    var icon: String { "tab-" + rawValue }
    var filledIcon: String { icon + "-fill" }
}

/// What a screen can ask the shell to do.
struct ShellActions {
    var openFarm: (FarmPin) -> Void = { _ in }
    var showTab: (AppTab) -> Void = { _ in }
    var openTrips: () -> Void = {}
    var openProfile: () -> Void = {}
    /// Opens the one Plus sheet, saying what asked for it. The sheet reports
    /// `paywall_viewed` with that trigger when it appears, so a call site can
    /// neither forget to report nor report a paywall that never showed.
    var openPlus: (AnalyticsValue.Trigger) -> Void = { _ in }
    /// A product page, by shopping id or seasonal slug.
    var openProduct: (String) -> Void = { _ in }
}

private struct ShellActionsKey: EnvironmentKey {
    static let defaultValue = ShellActions()
}

extension EnvironmentValues {
    var shell: ShellActions {
        get { self[ShellActionsKey.self] }
        set { self[ShellActionsKey.self] = newValue }
    }
}

struct ProductRoute: Identifiable { let slug: String; var id: String { slug } }

struct AppShell: View {
    @Environment(SessionStore.self) private var session
    @Environment(FarmsStore.self) private var farms

    @State private var tab: AppTab = .home
    @State private var push = PushRegistrar.shared
    /// The open farm. A bound value (not a `.sheet(item:)`) so tapping another pin
    /// swaps the card's contents in place rather than dismissing and re-presenting.
    @State private var selectedPin: FarmPin?
    @State private var showAuth = false
    @State private var showProfile = false
    @State private var showTrips = false
    @State private var showPlus = false
    /// What asked for the Plus sheet — read once by the sheet itself, in
    /// `onAppear`, so `paywall_viewed` fires exactly once per presentation.
    /// Set only on the path that really presents Plus: a signed-out tap goes to
    /// the Auth sheet instead and reports nothing.
    @State private var plusTrigger: AnalyticsValue.Trigger?
    @State private var productSlug: ProductRoute?
    @State private var showSurvey = false
    @State private var tripDetent: PresentationDetent = .fraction(0.92)
    /// The card opens at half and can be dragged to peek or full.
    @State private var farmDetent: PresentationDetent = .fraction(0.55)
    /// A pin the map should fly to (set when a farm is opened from another tab).
    @State private var flyTarget: FarmPin?

    private var actions: ShellActions {
        ShellActions(
            openFarm: { openFarm($0, source: .whatsNew) },
            showTab: { tab = $0 },
            openTrips: { requireAuth { showTrips = true } },
            openProfile: { showProfile = true },
            openPlus: { trigger in requireAuth { plusTrigger = trigger; showPlus = true } },
            openProduct: { productSlug = ProductRoute(slug: $0) })
    }

    var body: some View {
        ZStack(alignment: .bottom) {
            // A TabView with its own bar hidden: each tab keeps its scroll position,
            // camera and loaded state across switches, which a `switch` would drop.
            // iOS 26 draws its glass tab bar unless every tab's own content
            // hides it; the modifier on the TabView alone left a blank slab
            // behind our pill.
            TabView(selection: $tab) {
                HomeScreen().tag(AppTab.home).toolbar(.hidden, for: .tabBar)
                ShoppingScreen().tag(AppTab.shopping).toolbar(.hidden, for: .tabBar)
                MapScreen(onOpenFarm: { openFarm($0, source: .mapPin) }, focusPin: flyTarget).tag(AppTab.map).toolbar(.hidden, for: .tabBar)
                DiscoverScreen().tag(AppTab.discover).toolbar(.hidden, for: .tabBar)
                CommunityScreen().tag(AppTab.community).toolbar(.hidden, for: .tabBar)
            }
            .toolbar(.hidden, for: .tabBar)

            FloatingTabBar(selected: $tab)
                .padding(.bottom, Space.s3)
        }
        .background(Color.cream.ignoresSafeArea())
        .ignoresSafeArea(.keyboard)
        .tint(.farmGreen)
        // A notification tap lands here: open the farm it was about.
        .onChange(of: push.pendingOsmId, initial: true) { _, osmId in
            guard let osmId, let pin = farms.pin(forOsmId: osmId) else { return }
            push.pendingOsmId = nil
            openFarm(pin, source: .whatsNew)
        }
        .modifier(SurveyEntry(isPresented: $showSurvey, buttonVisible: tab == .map))
        // The farm card — three resting heights, opening at half, and the map
        // stays interactive behind it up through half.
        .sheet(isPresented: Binding(
            get: { selectedPin != nil },
            set: { if !$0 { selectedPin = nil } }
        )) {
            if let pin = selectedPin {
                FarmDetailView(pin: pin)
                    .id(pin.osmId)   // swap contents when another pin is tapped
                    .presentationDetents([.height(180), .fraction(0.55), .large], selection: $farmDetent)
                    .presentationBackgroundInteraction(.enabled(upThrough: .fraction(0.55)))
                    .presentationContentInteraction(.scrolls)
                    .presentationDragIndicator(.visible)
                    .presentationCornerRadius(Radius.sheet)
                    // Same reason as Profile/Trips below: `FarmStatusSection`'s
                    // "Confirmed today — see when with Plus" row calls
                    // `shell.openPlus(_:)` from inside this sheet, which needs
                    // to present ON TOP of the farm card, not get dropped by
                    // the same one-sheet-per-presenter limit.
                    .sheet(isPresented: showPlusInFarmCard) { plusSheetContent() }
                    // Task 4 fix round 5: the farm card is reachable signed
                    // out, and `openPlus`'s `requireAuth` else-branch sets
                    // `showAuth` (not `showPlus`) for a signed-out tap on that
                    // same "see when with Plus" row — same limit, same fix.
                    // Dismissing this (on sign-in success) returns to the farm
                    // card; re-tapping Plus is on the person, no auto-reopen.
                    .sheet(isPresented: showAuthInFarmCard) { AuthView() }
            }
        }
        .sheet(isPresented: $showProfile) {
            ProfileScreen()
                .presentationDetents([.large])
                .presentationDragIndicator(.visible)
                .presentationCornerRadius(Radius.sheet)
                // Task 4 fix round 3: SwiftUI can only present one sheet per
                // presenter at a time, so a sibling `.sheet(isPresented: $showPlus)`
                // on the root view is silently ignored while this sheet is up.
                // Nesting it here lets it present ON TOP of Profile instead.
                // `showPlusInProfile` is one of three views onto the single
                // `showPlus` (see below); its setter still writes `showPlus`.
                .sheet(isPresented: showPlusInProfile) { plusSheetContent() }
                // Final review #5: Profile's "Sign in" used to `dismiss()` and
                // then ask for Auth, which the root presenter — still animating
                // Profile out — silently dropped. Same nesting as the farm card.
                .sheet(isPresented: showAuthInProfile) { AuthView() }
        }
        .sheet(isPresented: $showTrips) {
            TripsView(onOpenFarm: { openFarm($0, source: .trips) }, selectedOsmId: selectedPin?.osmId, detent: $tripDetent)
                .presentationDetents([.fraction(0.5), .fraction(0.92)], selection: $tripDetent)
                .presentationBackgroundInteraction(.enabled(upThrough: .fraction(0.5)))
                .presentationDragIndicator(.visible)
                .presentationCornerRadius(Radius.sheet)
                // Same reason as Profile above: nested so the route-preview
                // "Unlock the route" / "Show route" taps (Task 4) actually open
                // Plus instead of doing nothing while the trip sheet is up, and
                // so closing/purchasing returns to the same trip, now unlocked.
                .sheet(isPresented: showPlusInTrips) { plusSheetContent() }
        }
        .sheet(item: $productSlug) { r in
            ProductSheet(slug: r.slug)
                .presentationDetents([.large])
                .presentationDragIndicator(.visible)
                .presentationCornerRadius(Radius.sheet)
        }
        // The root presentation — used when none of Trips, Profile or the farm
        // card owns the request (every other Plus entry point — Shopping's
        // pinned bar, Home's lock card, the Map chip — calls `shell.openPlus(_:)`
        // while no AppShell-owned sheet is up, so this is the one they hit).
        .sheet(isPresented: showPlusAtRoot) { plusSheetContent() }
        // Root presentation for Auth, same shape as Plus above: used only when
        // neither the farm card nor Profile — the two surfaces a signed-out
        // `requireAuth`/`requestAuth` can fire from — owns the request.
        .sheet(isPresented: showAuthAtRoot) { AuthView() }
        // Task 4 fix round 4: a sheet's presented content inherits the
        // environment of the view its `.sheet(...)` modifier is attached to —
        // and every `.sheet` above is attached to the chain built so far, so
        // these two have to be the LAST modifiers, after every `.sheet`, or
        // none of the sheets (Trips, Profile, the farm card, ProductSheet,
        // the nested Plus sheets) see them and every `shell.*`/`requestAuth`
        // call from inside one silently reads the environment's default
        // no-op `ShellActions()` / no-op closure instead. This was also why
        // `onOpenFarm` had to be passed to `TripsView` as an explicit
        // parameter rather than read from `\.shell` — the same bug, worked
        // around in one place instead of fixed at the root.
        .environment(\.requestAuth, { showAuth = true })
        .environment(\.shell, actions)
    }

    /// One source of truth, `showPlus` — presented from whichever surface is
    /// frontmost. SwiftUI drops a second `.sheet(isPresented:)` request from a
    /// presenter that already has one up, so the same boolean is exposed as
    /// four bindings (root / inside Trips / inside Profile / inside the farm
    /// card), each true only when that surface should own the presentation,
    /// each writing back to the single `showPlus` on set.
    ///
    /// What is actually true — not "exactly one is ever true", which the old
    /// comment claimed and which a pin tapped behind the half-open trip sheet
    /// breaks: MORE THAN ONE getter may be true at once, and that is safe.
    /// Only a MOUNTED sheet's nested `.sheet` can present anything, so a true
    /// binding inside a sheet that is not on screen does nothing at all; and
    /// the root binding presents only when no AppShell-owned sheet is up. So
    /// the request lands on the surface that is actually in front, and never
    /// twice.
    ///
    /// Do NOT "harden" the farm-card bindings with `!showTrips && !showProfile`
    /// (that was tried and reverted): those flags go stale. Drag the farm card
    /// to its half detent, tap "Plan a route" on the live map behind it, and
    /// `showTrips` is set while the root presenter is busy with the card — so
    /// Trips never mounts and the flag sticks. With the extra clauses the card's
    /// own Plus row then had NO presenter at all: the farm-card binding was
    /// false because of the stale flag, and `showPlusInTrips` lived inside a
    /// `TripsView` that was never built.
    private var showPlusAtRoot: Binding<Bool> {
        Binding(get: { showPlus && !showTrips && !showProfile && selectedPin == nil }, set: { showPlus = $0 })
    }
    private var showPlusInTrips: Binding<Bool> {
        Binding(get: { showPlus && showTrips }, set: { showPlus = $0 })
    }
    private var showPlusInProfile: Binding<Bool> {
        Binding(get: { showPlus && showProfile }, set: { showPlus = $0 })
    }
    private var showPlusInFarmCard: Binding<Bool> {
        Binding(get: { showPlus && selectedPin != nil }, set: { showPlus = $0 })
    }

    /// The same one-source-of-truth split as `showPlus` above, for `showAuth`,
    /// and the same rule: overlapping getters are fine, an unmounted sheet
    /// presents nothing, and the root only fires when nothing else is up.
    /// The farm card and Profile both need the nested form (Profile since final
    /// review #5: its "Sign in" no longer dismisses Profile first). Trips has
    /// none because it cannot be reached signed out, so nothing inside it ever
    /// asks for auth — if that changes, it wants a `showAuthInTrips` exactly
    /// like `showPlusInTrips`, and the root binding here already excludes it.
    private var showAuthAtRoot: Binding<Bool> {
        Binding(get: { showAuth && !showTrips && !showProfile && selectedPin == nil }, set: { showAuth = $0 })
    }
    private var showAuthInProfile: Binding<Bool> {
        Binding(get: { showAuth && showProfile }, set: { showAuth = $0 })
    }
    private var showAuthInFarmCard: Binding<Bool> {
        Binding(get: { showAuth && selectedPin != nil }, set: { showAuth = $0 })
    }

    /// The Plus sheet's one content definition, reused by whichever binding
    /// above is presenting it — never duplicated, so there's still exactly one
    /// `ProUpsellSheet` and no risk of double-firing its analytics.
    ///
    /// It is also the one place `paywall_viewed` is captured. Call sites used to
    /// capture it themselves, which reported a paywall for signed-out taps that
    /// actually got the sign-in sheet, and reported nothing at all for the four
    /// entry points that never had a capture. `onAppear` runs once per
    /// presentation, so one shown paywall is one event.
    private func plusSheetContent() -> some View {
        ProUpsellSheet()
            .presentationDetents([.fraction(0.92)])
            .presentationDragIndicator(.visible)
            .presentationCornerRadius(Radius.sheet)
            .onAppear {
                guard let trigger = plusTrigger else { return }
                Observability.capture(.paywallViewed, [AnalyticsProp.trigger: trigger.rawValue])
            }
    }

    /// Farm cards open for everyone, signed out included. Opening a farm from
    /// another tab switches to the map and flies to it, so "where is it" is
    /// always one tap from "what is it".
    private func openFarm(_ pin: FarmPin, source: AnalyticsValue.Source) {
        farmDetent = .fraction(0.55)   // always open at half
        if tab != .map { tab = .map }
        flyTarget = pin
        selectedPin = pin
        Observability.capture(.farmOpened, [AnalyticsProp.osmId: pin.osmId,
                                            AnalyticsProp.source: source.rawValue])
    }

    private func requireAuth(_ action: @escaping () -> Void) {
        if session.isAuthenticated { action() } else { showAuth = true }
    }
}

/// The survey's floating entry button + arrow + sheet, as one modifier so AppShell's
/// body stays small. The button is shown for EVERY role — signed-out, signed-in and
/// admin alike (Neil: "the survey appears whatever the role"). It changes what it
/// opens: the seven questions while unanswered, the feedback box once answered. A down
/// arrow bounces above it while unanswered. It also auto-opens once on cold launch
/// after the map settles — once ever per signed-in account, once a day for signed-out.
/// (Admin data integrity is kept server-side: `POST /api/survey/respond` rejects an
/// admin submit with `is_admin`, so showing the UI to staff pollutes nothing.)
private struct SurveyEntry: ViewModifier {
    @Binding var isPresented: Bool
    /// The floating button only sits over the map; on scrolling tabs it would
    /// cover content. The cold-launch auto-open does not depend on it.
    var buttonVisible = true
    @Environment(SessionStore.self) private var session

    /// The gate result (nil until loaded). Drives only `answered` now: answered → the
    /// button opens feedback and the arrow is gone; not-answered → questions + arrow.
    /// Role no longer hides anything on the client.
    @State private var gate: SurveyGate?
    @State private var mode: SurveyView.Mode = .questions
    /// Cold-launch auto-open is attempted exactly once (this modifier appears once at
    /// app start; resume does not recreate it, so a plain `.task` never re-fires).
    @State private var didAutoOpen = false

    /// The arrow points only while there is an unanswered survey to point at — role
    /// no longer matters, only `answered`.
    private var showArrow: Bool {
        guard let g = gate else { return false }
        return !g.answered
    }

    func body(content: Content) -> some View {
        content
            .overlay(alignment: .bottomTrailing) {
                if buttonVisible { surveyButton }
            }
            .sheet(isPresented: $isPresented) {
                SurveyView(mode: mode)
                    .presentationDetents([.fraction(0.92)])
                    .presentationDragIndicator(.visible)
                    .presentationCornerRadius(28)
                    // Re-check once the sheet closes (they may have just answered), so
                    // the arrow disappears and the button switches to feedback mode.
                    .onDisappear { Task { gate = await SurveyAPI.gate(accessToken: session.session?.accessToken) } }
            }
            // Refresh the gate on sign-in / sign-out (token change) for the arrow + mode.
            .task(id: session.session?.accessToken) {
                gate = await SurveyAPI.gate(accessToken: session.session?.accessToken)
            }
            // Cold-launch auto-open — runs once when the app first appears (not on
            // resume). The 1.4s delay lets the map settle (and the session bootstrap)
            // before asking, so it reads as a question rather than part of the loading.
            // Fires for any role; only `answered` and the frequency cap stop it.
            .task {
                guard !didAutoOpen else { return }
                didAutoOpen = true
                try? await Task.sleep(for: .seconds(1.4))
                let g = await SurveyAPI.gate(accessToken: session.session?.accessToken)
                gate = g
                guard !g.answered else { return }
                let account = session.isAuthenticated ? session.email : nil
                guard SurveyAutoOpen.canAutoOpen(account: account) else { return }
                SurveyAutoOpen.recordAutoOpen(account: account)
                mode = .questions
                isPresented = true
            }
    }

    private var surveyButton: some View {
                ZStack(alignment: .bottom) {
                    if showArrow {
                        SurveyArrow()
                            .offset(y: -52)   // sit above the 44pt button
                            .accessibilityHidden(true)
                    }
                    Button {
                        Haptics.tap()
                        mode = (gate?.answered == true) ? .feedback : .questions
                        isPresented = true
                    } label: {
                        Image(systemName: "text.bubble.fill")
                            .font(.system(size: 17, weight: .semibold))
                            .foregroundStyle(Color.farmGreenMap)
                            .frame(width: 44, height: 44)
                            .background(Color.white.opacity(0.94), in: Circle())
                            .overlay(Circle().stroke(.white.opacity(0.6), lineWidth: 1))
                            .shadow(color: .black.opacity(0.22), radius: 10, y: 3)
                    }
                    .buttonStyle(.plain)
                }
                .padding(.trailing, 14)
                // Clears the 64pt tab pill and its 12pt gap.
                .padding(.bottom, TabBarInset.content + 12)
    }
}

/// Whether the survey may auto-open, and the record that it did. Signed in it opens
/// once ever per account; signed out, once per calendar day (a phone cold-launches
/// often, so an uncapped "every visit" would harass). Persisted in UserDefaults —
/// per-device is acceptable for signed-out (there is no identity to key on), and the
/// signed-in per-account key survives across devices only as far as the account's
/// server-side `answered` state does, which is the real stop.
enum SurveyAutoOpen {
    private static let store = UserDefaults.standard

    static func canAutoOpen(account: String?) -> Bool {
        if let a = account, !a.isEmpty {
            return !store.bool(forKey: "survey_autoopen_acct_\(a)")
        }
        return store.string(forKey: "survey_autoopen_day") != today()
    }

    static func recordAutoOpen(account: String?) {
        if let a = account, !a.isEmpty {
            store.set(true, forKey: "survey_autoopen_acct_\(a)")
        } else {
            store.set(today(), forKey: "survey_autoopen_day")
        }
    }

    private static func today() -> String {
        let f = DateFormatter()
        f.dateFormat = "yyyy-MM-dd"
        f.locale = Locale(identifier: "en_US_POSIX")
        return f.string(from: Date())
    }
}

/// A regular dark-green down arrow above the survey button, pointing at it — shown
/// only while the survey is unanswered. Per Neil's brief (a standard arrow, not a
/// hand-drawn Path): SF `arrow.down`, `farmGreen` (dark brand green, distinct from the
/// lighter on-map `farmGreenMap`). Bounces ~9px / ~0.9s eased; hidden from VoiceOver;
/// holds still under Reduce Motion. Matches the Android `SurveyArrow`.
private struct SurveyArrow: View {
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var drop = false

    var body: some View {
        Image(systemName: "arrow.down")
            .font(.system(size: 22, weight: .semibold))
            .foregroundStyle(Color.farmGreen)
            .offset(y: drop ? 9 : 0)   // ~9px fall-and-settle
            .animation(reduceMotion ? nil
                       : .easeInOut(duration: 0.9).repeatForever(autoreverses: true), value: drop)
            .onAppear { if !reduceMotion { drop = true } }
    }
}
