import SwiftUI

/// Discover — inspiration, not utility. For now: the farms with a story and a
/// gallery, then the recommendation carousel. Seasons, "just arrived" and
/// "this weekend" land here in a later phase.
struct DiscoverScreen: View {
    @Environment(FarmsStore.self) private var farms
    @Environment(\.shell) private var shell

    private var featured: [FarmPin] { Array(farms.featuredFarms.prefix(10)) }

    var body: some View {
        VStack(spacing: 0) {
            ScreenHeader(String(localized: "Discover"))
                .padding(.top, Space.s2)
            ScrollView(showsIndicators: false) {
                LazyVStack(alignment: .leading, spacing: Space.s2) {
                    SectionHeader(title: String(localized: "Farms with a story"))
                        .padding(.top, 0)
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
    }
}
