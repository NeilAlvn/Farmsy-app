import SwiftUI

/// Community — "Near you": what people posted at farms, newest first. Reports
/// and confirmations join this feed in a later phase.
struct CommunityScreen: View {
    @Environment(FarmsStore.self) private var farms
    @Environment(\.shell) private var shell

    @State private var pings: [Ping] = []
    @State private var loading = true
    @State private var lightbox: LightboxSource?

    var body: some View {
        VStack(spacing: 0) {
            ScreenHeader(String(localized: "Community"))
                .padding(.top, Space.s2)
            ScrollView(showsIndicators: false) {
                LazyVStack(alignment: .leading, spacing: Space.s2) {
                    SectionHeader(title: String(localized: "Near you"))
                        .padding(.top, 0)
                    if loading {
                        ForEach(0..<3, id: \.self) { _ in
                            SkeletonBox(cornerRadius: Radius.card).frame(height: 150)
                        }
                    } else if pings.isEmpty {
                        EmptyState(icon: "bubble.left.and.bubble.right",
                                   title: String(localized: "Nothing posted yet"),
                                   text: String(localized: "Visit a farm and tell people what you found."),
                                   action: (String(localized: "Open the map"), { shell.showTab(.map) }))
                    } else {
                        ForEach(pings) { ping in
                            PingCard(ping: ping,
                                     farmName: farms.pin(forOsmId: ping.farmOsmId)?.name,
                                     onOpenFarm: {
                                         if let pin = farms.pin(forOsmId: ping.farmOsmId) { shell.openFarm(pin) }
                                     },
                                     onOpenImage: { idx in
                                         var t = Transaction(); t.disablesAnimations = true
                                         withTransaction(t) {
                                             lightbox = LightboxSource(
                                                 images: ping.images, startIndex: idx,
                                                 eyebrow: String(localized: "From a post"),
                                                 title: ping.authorName,
                                                 subtitle: farms.pin(forOsmId: ping.farmOsmId)?.name,
                                                 postText: ping.body)
                                         }
                                     })
                        }
                    }
                }
                .padding(.horizontal, Space.s4)
                .padding(.bottom, TabBarInset.content)
            }
            .refreshable { await load() }
        }
        .background(Color.cream.ignoresSafeArea())
        .fullScreenCover(item: $lightbox) { src in
            ImageLightbox(source: src) {
                var t = Transaction(); t.disablesAnimations = true
                withTransaction(t) { lightbox = nil }
            }
            .presentationBackground(.clear)
        }
        .task { await load() }
    }

    private func load() async {
        let rows: [Ping] = (try? await supabase
            .from("farm_pings")
            .select("id, farm_osm_id, author_name, body, like_count, created_at, farm_ping_images(url, sort_order)")
            .eq("status", value: "visible")
            .order("created_at", ascending: false)
            .limit(30)
            .execute()
            .value) ?? []
        pings = rows
        loading = false
    }
}
