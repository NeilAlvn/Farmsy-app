import SwiftUI

/// The map is the app. There is no feed, list or discovery tab — everything is a
/// layer over the map (MOBILE-SPEC-MAP §0). The farm card is a bottom sheet at
/// three heights with the map usable behind it; Saved and Settings live behind an
/// account button in the header; posts and featured farms live in the What's New
/// sheet, opened from a floating button on the map.
struct MainView: View {
    @Environment(SessionStore.self) private var session

    /// The open farm. A bound value (not a `.sheet(item:)`) so tapping another pin
    /// swaps the card's contents in place rather than dismissing and re-presenting.
    @State private var selectedPin: FarmPin?
    @State private var showAuth = false
    @State private var accountRoute: AccountRoute?
    @State private var showWhatsNew = false
    @State private var showTrips = false
    @State private var showSurvey = false
    @State private var tripDetent: PresentationDetent = .fraction(0.92)
    /// The card opens at half and can be dragged to peek or full.
    @State private var farmDetent: PresentationDetent = .fraction(0.55)
    /// A pin the map should fly to (set when opening from the What's New sheet).
    @State private var flyTarget: FarmPin?

    enum AccountRoute: Identifiable {
        case saved, settings
        var id: Int { hashValue }
    }

    var body: some View {
        MapScreen(onOpenFarm: { openFarm($0) }, focusPin: flyTarget)
        .background(Color.cream.ignoresSafeArea())
        .ignoresSafeArea(.keyboard)
        .tint(.farmGreen)
        .environment(\.requestAuth, { showAuth = true })
        .overlay(alignment: .bottom) {
            bottomPanel
                .padding(.horizontal, 14)
                .padding(.bottom, 6)
        }
        // Survey entry point (button + sheet). Extracted into one modifier so the
        // already-large body stays within the type-checker's budget. Aviah's spec
        // delegates placement on a phone explicitly ("the button placement is
        // yours"); a small round button at bottom-trailing, above the panel, mirrors
        // the web's bottom-right corner without colliding with the map controls. The
        // server gate hides it after answering. (Auto-open timing is a deliberate
        // deferral — see PORT_NOTES.)
        .modifier(SurveyEntry(isPresented: $showSurvey))
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
                .presentationCornerRadius(28)
            }
        }
        .sheet(isPresented: $showWhatsNew) {
            WhatsNewSheet(onOpenFarm: { pin in
                flyTarget = pin          // fly the map straight to it
                showWhatsNew = false
                openFarm(pin)
            })
            .presentationDetents([.fraction(0.55), .fraction(0.92)])
            .presentationBackgroundInteraction(.enabled(upThrough: .fraction(0.55)))
            .presentationDragIndicator(.visible)
                .presentationCornerRadius(28)
        }
        .sheet(item: $accountRoute) { route in
            Group {
                switch route {
                case .saved:    SavedScreen { openFarm($0) }
                case .settings: SettingsSheet()
                }
            }
            .presentationDetents([.fraction(0.55), .fraction(0.92)])
            .presentationBackgroundInteraction(.enabled(upThrough: .fraction(0.55)))
            .presentationDragIndicator(.visible)
                .presentationCornerRadius(28)
        }
        .sheet(isPresented: $showTrips) {
            TripsView(onOpenFarm: { openFarm($0) }, selectedOsmId: selectedPin?.osmId, detent: $tripDetent)
                .presentationDetents([.fraction(0.5), .fraction(0.92)], selection: $tripDetent)
                .presentationBackgroundInteraction(.enabled(upThrough: .fraction(0.5)))
                .presentationDragIndicator(.visible)
                .presentationCornerRadius(28)
        }
        .sheet(isPresented: $showAuth) { AuthView() }
    }

    /// Farm cards open for everyone, signed out included (MOBILE-SPEC-MAP §0) —
    /// the card shows the free content and locks the paid fields inside. Actions
    /// that need an account (save, subscribe) prompt for one from within the card.
    private func openFarm(_ pin: FarmPin) {
        farmDetent = .fraction(0.55)   // always open at half
        flyTarget = pin                // fly the map to the farm, wherever it was opened from
        selectedPin = pin
    }

    // MARK: - Bottom floating panel

    /// A floating pill over the map, the width of the search bar: Discover (the
    /// What's New sheet), Save, Trips and Settings.
    private var bottomPanel: some View {
        HStack(spacing: 4) {
            panelItem(icon: "newspaper", label: String(localized: "Discover")) { showWhatsNew = true }
            panelItem(icon: "heart", label: String(localized: "Saved")) { requireAuth { accountRoute = .saved } }
            panelItem(icon: "map", label: String(localized: "Trips")) { requireAuth { showTrips = true } }
            panelItem(icon: "gearshape", label: String(localized: "Settings")) { requireAuth { accountRoute = .settings } }
        }
        .padding(6)
        .background(.white, in: Capsule())
        .shadow(color: .black.opacity(0.14), radius: 12, y: 3)
    }

    private func panelItem(icon: String, label: String, action: @escaping () -> Void) -> some View {
        Button {
            Haptics.tap()
            action()
        } label: {
            VStack(spacing: 3) {
                Image(systemName: icon).font(.system(size: 17, weight: .semibold))
                Text(label).font(.geist(10, .semibold))
            }
            .foregroundStyle(Color.farmGreenMap)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 8)
        }
        .buttonStyle(.plain)
    }

    private func requireAuth(_ action: @escaping () -> Void) {
        if session.isAuthenticated { action() } else { showAuth = true }
    }
}

/// The survey's floating entry button + arrow + sheet, as one modifier so MainView's
/// body stays small. The button never goes away (except for admins) and changes what
/// it opens: the seven questions while unanswered, the feedback box once answered
/// (Aviah's spec). A hand-drawn arrow bounces above it while unanswered. It also
/// auto-opens once on cold launch after the map settles — once ever per signed-in
/// account, once a day for signed-out visitors.
private struct SurveyEntry: ViewModifier {
    @Binding var isPresented: Bool
    @Environment(SessionStore.self) private var session

    /// The gate result (nil until loaded). Drives everything: admin → no button;
    /// answered → the button opens feedback and the arrow is gone; not-answered →
    /// questions + arrow.
    @State private var gate: SurveyGate?
    @State private var mode: SurveyView.Mode = .questions
    /// Cold-launch auto-open is attempted exactly once (this modifier appears once at
    /// app start; resume does not recreate it, so a plain `.task` never re-fires).
    @State private var didAutoOpen = false

    /// Admin gets NO entry point at all. While the gate is still loading we fail open
    /// (show the button) — better to offer the survey than wrongly withhold it.
    private var hideButton: Bool {
        #if DEBUG
        false   // Debug keeps the button for everyone so the survey stays testable.
        #else
        gate?.isAdmin == true
        #endif
    }

    /// The arrow points only while there is an unanswered survey to point at.
    private var showArrow: Bool {
        guard let g = gate else { return false }
        return !g.isAdmin && !g.answered
    }

    func body(content: Content) -> some View {
        content
            .overlay(alignment: .bottomTrailing) {
                if !hideButton {
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
                    // 120 clears the bottom pill (spans ~safe-bottom+6 to +67) by ~50pt.
                    // The overlay is inset by the safe area, so it clears the home
                    // indicator; button + pill ignore Dynamic Type, so the gap holds.
                    .padding(.bottom, 120)
                }
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
            // Refresh the gate on sign-in / sign-out (token change) for button + arrow.
            .task(id: session.session?.accessToken) {
                gate = await SurveyAPI.gate(accessToken: session.session?.accessToken)
            }
            // Cold-launch auto-open — runs once when the map first appears (not on
            // resume). The 1.4s delay lets the map settle (and the session bootstrap)
            // before asking, so it reads as a question rather than part of the loading.
            .task {
                guard !didAutoOpen else { return }
                didAutoOpen = true
                try? await Task.sleep(for: .seconds(1.4))
                let g = await SurveyAPI.gate(accessToken: session.session?.accessToken)
                gate = g
                guard !g.isAdmin, !g.answered else { return }
                let account = session.isAuthenticated ? session.email : nil
                guard SurveyAutoOpen.canAutoOpen(account: account) else { return }
                SurveyAutoOpen.recordAutoOpen(account: account)
                mode = .questions
                isPresented = true
            }
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

/// A small hand-drawn-style arrow that points down at the survey button while the
/// survey is unanswered, bouncing gently. Drawn as a Path (not an SF Symbol) for the
/// slightly uneven, pointed-by-a-person feel Aviah asked for. Hidden from VoiceOver
/// (it says nothing the button's label doesn't) and it holds still under Reduce Motion.
/// NOTE: an approximation of the web's bespoke SVG, not a pixel match — flagged to the
/// thread; swap for the exact asset if shared.
private struct SurveyArrow: View {
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var drop = false

    var body: some View {
        HandArrowShape()
            .stroke(Color.farmGreen, style: StrokeStyle(lineWidth: 2.5, lineCap: .round, lineJoin: .round))
            .frame(width: 22, height: 34)
            .offset(y: drop ? 9 : 0)   // ~9px fall-and-settle
            .animation(reduceMotion ? nil
                       : .easeInOut(duration: 0.9).repeatForever(autoreverses: true), value: drop)
            .onAppear { if !reduceMotion { drop = true } }
    }
}

private struct HandArrowShape: Shape {
    func path(in rect: CGRect) -> Path {
        var p = Path()
        let x = rect.midX
        // A slightly wavy shaft — the unevenness is deliberate.
        p.move(to: CGPoint(x: x - 2.5, y: rect.minY))
        p.addCurve(
            to: CGPoint(x: x + 1.5, y: rect.maxY - 9),
            control1: CGPoint(x: x + 5, y: rect.height * 0.34),
            control2: CGPoint(x: x - 4, y: rect.height * 0.68)
        )
        // Arrowhead.
        p.move(to: CGPoint(x: x - 6, y: rect.maxY - 12))
        p.addLine(to: CGPoint(x: x + 1.5, y: rect.maxY))
        p.addLine(to: CGPoint(x: x + 8, y: rect.maxY - 13))
        return p
    }
}
