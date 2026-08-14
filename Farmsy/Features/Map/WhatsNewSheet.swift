import SwiftUI

/// The phone-only sheet that holds everything the desktop side panel does, in one
/// place (MOBILE-SPEC-MAP §2): the farms' posts, then featured farms. Opened from
/// the map's What's New button; opening a farm from here closes the sheet first.
struct WhatsNewSheet: View {
    var onOpenFarm: (FarmPin) -> Void

    @Environment(\.dismiss) private var dismiss
    @Environment(FarmsStore.self) private var farms

    @State private var pings: [Ping] = []

    /// Verified farms with a photo — the same selection the web shelf uses.
    private var featured: [FarmPin] {
        farms.pins.filter { $0.isVerified && $0.image != nil }
            .sorted { $0.osmId < $1.osmId }
            .prefix(12)
            .map { $0 }
    }

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Text("WHAT'S NEW")
                    .font(.geist(11, .semibold))
                    .kerning(1.2)
                    .foregroundStyle(Color.inkMuted)
                Spacer()
                Button { dismiss() } label: {
                    Image(systemName: "xmark")
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundStyle(Color(hex: 0x6B7280))
                        .frame(width: 32, height: 32)
                        .background(Color(hex: 0xF3F4F6), in: Circle())
                }
                .buttonStyle(.plain)
            }
            .padding(.horizontal, 16)
            .padding(.top, 14)
            .padding(.bottom, 8)

            ScrollView(showsIndicators: false) {
                LazyVStack(spacing: 14) {
                    if pings.isEmpty {
                        emptyPosts
                    } else {
                        ForEach(pings) { ping in
                            PingCard(ping: ping,
                                     farmName: farms.pin(forOsmId: ping.farmOsmId)?.name,
                                     onOpenFarm: {
                                         if let pin = farms.pin(forOsmId: ping.farmOsmId) { onOpenFarm(pin) }
                                     })
                        }
                    }

                    if !featured.isEmpty {
                        Text("FRESH FROM THE FARM")
                            .font(.geist(11, .semibold))
                            .kerning(1.2)
                            .foregroundStyle(Color.inkMuted)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding(.top, 4)

                        ForEach(featured) { pin in
                            DiscoverFeedCard(pin: pin, onOpen: { onOpenFarm(pin) })
                        }
                    }
                }
                .padding(.horizontal, 14)
                .padding(.bottom, 24)
            }
        }
        .background(Color.cream.ignoresSafeArea())
        .task { await loadPings() }
    }

    private var emptyPosts: some View {
        Text("No posts yet — check back soon.")
            .font(.geist(12))
            .foregroundStyle(Color.inkMuted)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 16)
            .overlay(
                RoundedRectangle(cornerRadius: 16, style: .continuous)
                    .strokeBorder(Color(hex: 0xE5E7EB), style: StrokeStyle(lineWidth: 1, dash: [4]))
            )
    }

    private func loadPings() async {
        let rows: [Ping] = (try? await supabase
            .from("farm_pings")
            .select("id, farm_osm_id, author_name, body, like_count, created_at, farm_ping_images(url, sort_order)")
            .eq("status", value: "visible")
            .order("created_at", ascending: false)
            .limit(30)
            .execute()
            .value) ?? []
        pings = rows
    }
}
