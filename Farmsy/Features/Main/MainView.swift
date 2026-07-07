import SwiftUI

/// Main shell: brand header up top, content in the middle, and the
/// Map / Saved / Settings menu in a floating bar at the bottom.
struct MainView: View {
    enum Tab: String, CaseIterable {
        case map = "Map"
        case discover = "Discover"
        case saved = "Saved"
        case settings = "Settings"

        var icon: String {
            switch self {
            case .map: "map.fill"
            case .discover: "sparkles"
            case .saved: "heart.fill"
            case .settings: "gearshape.fill"
            }
        }

        /// User-facing name — rawValue stays English for identifiers.
        var label: String {
            switch self {
            case .map: String(localized: "Map")
            case .discover: String(localized: "Discover")
            case .saved: String(localized: "Saved")
            case .settings: String(localized: "Settings")
            }
        }
    }

    @Environment(SessionStore.self) private var session

    @State private var tab: Tab = .map
    @State private var path: [FarmPin] = []
    @State private var showAuth = false

    var body: some View {
        NavigationStack(path: $path) {
            Group {
                if tab == .map {
                    // Map is the hero: full-bleed edge to edge, with the
                    // tab bar floating on top of it.
                    ZStack(alignment: .bottom) {
                        MapScreen { pin in openFarm(pin) }
                        tabBar
                            .padding(.horizontal, 24)
                            .padding(.bottom, 4)
                    }
                } else {
                    VStack(spacing: 0) {
                        header
                            .padding(.horizontal, 18)
                            .padding(.vertical, 8)

                        Group {
                            switch tab {
                            case .map:
                                EmptyView()
                            case .discover:
                                DiscoverFeedView { pin in openFarm(pin) }
                            case .saved:
                                SavedScreen { pin in openFarm(pin) }
                            case .settings:
                                SettingsSheet()
                            }
                        }
                        .frame(maxHeight: .infinity)

                        tabBar
                            .padding(.horizontal, 24)
                            .padding(.top, 8)
                            .padding(.bottom, 4)
                    }
                }
            }
            .background(Color.cream.ignoresSafeArea())
            // No chrome on the root — the empty translucent nav bar would
            // otherwise blur a band across the top of the full-bleed map.
            .toolbar(.hidden, for: .navigationBar)
            .navigationDestination(for: FarmPin.self) { pin in
                FarmDetailView(pin: pin)
            }
        }
        .tint(.farmGreen)
        .environment(\.requestAuth, { showAuth = true })
        .sheet(isPresented: $showAuth) { AuthView() }
    }

    /// Guests can browse the map and feed freely; opening a farm's details
    /// asks for an account first. Once signed in, the detail view's own
    /// subscription gate takes over.
    private func openFarm(_ pin: FarmPin) {
        if session.isAuthenticated {
            path.append(pin)
        } else {
            showAuth = true
        }
    }

    private var header: some View {
        HStack {
            HStack(spacing: 8) {
                Image("FarmsyLogo")
                    .resizable()
                    .scaledToFit()
                    .frame(height: 34)
                Text("Farmsy")
                    .font(.display(22, weight: .semibold))
                    .foregroundStyle(Color.ink)
            }
            Spacer()
        }
    }

    private var tabBar: some View {
        HStack(spacing: 6) {
            ForEach(Tab.allCases, id: \.self) { t in
                Button {
                    Haptics.tap()
                    withAnimation(.spring(duration: 0.3)) { tab = t }
                } label: {
                    VStack(spacing: 3) {
                        Image(systemName: t.icon)
                            .font(.system(size: 17, weight: .semibold))
                        Text(t.label)
                            .font(.geist(11, .semibold))
                    }
                    .foregroundStyle(tab == t ? .white : Color.inkMuted)
                    .padding(.vertical, 9)
                    .frame(maxWidth: .infinity)
                    .background(tab == t ? Color.farmGreen : .clear, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
                }
                .buttonStyle(.plain)
                .accessibilityIdentifier("tab-\(t.rawValue.lowercased())")
            }
        }
        .padding(5)
        .background(.white, in: RoundedRectangle(cornerRadius: 21, style: .continuous))
        .shadow(color: .black.opacity(0.1), radius: 10, y: 3)
    }
}
