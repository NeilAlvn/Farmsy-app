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
    var openPlus: () -> Void = {}
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
            openPlus: { requireAuth { showPlus = true } })
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
        .environment(\.requestAuth, { showAuth = true })
        .environment(\.shell, actions)
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
            }
        }
        .sheet(isPresented: $showProfile) {
            ProfileScreen()
                .presentationDetents([.large])
                .presentationDragIndicator(.visible)
                .presentationCornerRadius(Radius.sheet)
        }
        .sheet(isPresented: $showTrips) {
            TripsView(onOpenFarm: { openFarm($0, source: .trips) }, selectedOsmId: selectedPin?.osmId, detent: $tripDetent)
                .presentationDetents([.fraction(0.5), .fraction(0.92)], selection: $tripDetent)
                .presentationBackgroundInteraction(.enabled(upThrough: .fraction(0.5)))
                .presentationDragIndicator(.visible)
                .presentationCornerRadius(Radius.sheet)
        }
        .sheet(isPresented: $showPlus) {
            ProUpsellSheet()
                .presentationDetents([.fraction(0.92)])
                .presentationDragIndicator(.visible)
                .presentationCornerRadius(Radius.sheet)
        }
        .sheet(isPresented: $showAuth) { AuthView() }
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
