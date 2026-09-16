import SwiftUI

/// Discover — inspiration, not utility. For now: the farms with a story and a
/// gallery, then the recommendation carousel. Seasons, "just arrived" and
/// "this weekend" land here in a later phase.
struct DiscoverScreen: View {
    @Environment(FarmsStore.self) private var farms
    @Environment(LocationManager.self) private var locationManager
    @Environment(\.shell) private var shell
    @AppStorage("searchRadiusKm") private var radiusKm = 15.0
    @State private var seasons = Seasons.shared

    private var featured: [FarmPin] { Array(farms.featuredFarms.prefix(10)) }

    var body: some View {
        VStack(spacing: 0) {
            ScreenHeader(String(localized: "Discover"))
                .padding(.top, Space.s2)
            ScrollView(showsIndicators: false) {
                LazyVStack(alignment: .leading, spacing: Space.s2) {
                    if !seasons.thisMonth.isEmpty {
                        SectionHeader(title: String(localized: "In season near you"))
                            .padding(.top, 0)
                        Text(String(localized: "Grown outdoors around here this month. Tap one to see who sells it."))
                            .role(.bodySm, .inkMuted)
                        FlowRow(spacing: Space.s2) {
                            ForEach(seasons.thisMonth) { item in
                                Chip(label: item.label, emoji: item.emoji,
                                     selected: false,
                                     dot: item.isPeak(month: seasons.month) ? Color.vivid : nil) {
                                    Task {
                                        await farms.showProduct(label: item.label, terms: item.terms,
                                                                userLocation: locationManager.location, radiusKm: radiusKm)
                                        shell.showTab(.map)
                                    }
                                }
                            }
                        }
                        .padding(.bottom, Space.s2)
                    }
                    SectionHeader(title: String(localized: "Farms with a story"))
                        .padding(.top, seasons.thisMonth.isEmpty ? 0 : Space.s4)
                    if !farms.galleriesLoaded {
                        ForEach(0..<3, id: \.self) { _ in
                            SkeletonBox(cornerRadius: Radius.card).frame(height: 180)
                        }
                    } else {
                        ForEach(featured) { pin in
                            MultiImageFarmCard(pin: pin,
                                               images: farms.galleries[pin.osmId] ?? [],
                                               teaser: farms.featuredTeasers[pin.osmId],
                                               onOpen: { shell.openFarm(pin) })
                        }
                    }
                    TripRecommendations(onOpenFarm: shell.openFarm)
                        .padding(.top, Space.s4)
                }
                .padding(.horizontal, Space.s4)
                .padding(.bottom, TabBarInset.content)
            }
        }
        .background(Color.cream.ignoresSafeArea())
        .task { await farms.loadGalleriesIfNeeded() }
        .task { await seasons.loadIfNeeded() }
    }
}
