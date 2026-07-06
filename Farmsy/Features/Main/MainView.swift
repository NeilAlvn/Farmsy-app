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
    }

    @State private var tab: Tab = .map
    @State private var path: [FarmPin] = []

    var body: some View {
        NavigationStack(path: $path) {
            VStack(spacing: 0) {
                header
                    .padding(.horizontal, 18)
                    .padding(.vertical, 8)

                Group {
                    switch tab {
                    case .map:
                        MapScreen { pin in path.append(pin) }
                    case .discover:
                        DiscoverFeedView { pin in path.append(pin) }
                    case .saved:
                        SavedScreen { pin in path.append(pin) }
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
            .background(Color.cream.ignoresSafeArea())
            .navigationDestination(for: FarmPin.self) { pin in
                FarmDetailView(pin: pin)
            }
        }
        .tint(.farmGreen)
    }

    private var header: some View {
        HStack {
            HStack(spacing: 8) {
                FarmsyMark(size: 34)
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
                        Text(t.rawValue)
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
