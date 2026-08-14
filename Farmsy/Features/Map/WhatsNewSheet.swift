import SwiftUI

/// The phone-only sheet that holds everything the desktop side panel does
/// (MOBILE-SPEC-MAP §2): the farms' posts, then a shelf of farms that have
/// several photos and a story. Opened from the map's What's New button; opening
/// a farm from here flies the map to it and closes the sheet.
struct WhatsNewSheet: View {
    var onOpenFarm: (FarmPin) -> Void

    @Environment(\.dismiss) private var dismiss
    @Environment(FarmsStore.self) private var farms

    @State private var pings: [Ping] = []
    @State private var loadingPings = true
    /// osm_id → gallery photos, for farms that published two or more.
    @State private var galleries: [String: [String]] = [:]

    private struct FarmFlag: Decodable { let o: String; let g: [String]? }

    /// Farms with a gallery of two or more photos — the shelf's picks.
    private var multiImageFarms: [FarmPin] {
        farms.pins
            .filter { galleries[$0.osmId] != nil }
            .sorted { $0.osmId < $1.osmId }
            .prefix(10)
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
            .padding(.top, 16)
            .padding(.bottom, 8)

            ScrollView(showsIndicators: false) {
                LazyVStack(spacing: 14) {
                    if loadingPings {
                        ProgressView().tint(Color.farmGreenMap).padding(.vertical, 24)
                    } else if pings.isEmpty {
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

                    if !multiImageFarms.isEmpty {
                        Text("FEATURED FARMS")
                            .font(.geist(11, .semibold))
                            .kerning(1.2)
                            .foregroundStyle(Color.inkMuted)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding(.top, 4)

                        ForEach(multiImageFarms) { pin in
                            MultiImageFarmCard(pin: pin,
                                               images: galleries[pin.osmId] ?? [],
                                               onOpen: { onOpenFarm(pin) })
                        }
                    }
                }
                .padding(.horizontal, 14)
                .padding(.bottom, 24)
            }
        }
        .background(Color.cream.ignoresSafeArea())
        .task { await loadPings() }
        .task { await loadFlags() }
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
        loadingPings = false
    }

    /// Gallery photos per farm, from the public flags endpoint. Only farms with
    /// two or more are kept — those are the ones worth a card.
    private func loadFlags() async {
        let url = Backend.webAPI.appending(path: "farms").appending(path: "flags")
        guard let (data, resp) = try? await URLSession.shared.data(from: url),
              (resp as? HTTPURLResponse)?.statusCode == 200,
              let rows = try? JSONDecoder().decode([FarmFlag].self, from: data)
        else { return }
        var map: [String: [String]] = [:]
        for r in rows where (r.g?.count ?? 0) >= 2 { map[r.o] = r.g }
        galleries = map
    }
}

/// A featured farm in the What's New shelf: its gallery photos across the top,
/// then the name, place and the opening of its description.
struct MultiImageFarmCard: View {
    let pin: FarmPin
    let images: [String]
    var onOpen: () -> Void

    @State private var teaser: String?

    var body: some View {
        Button(action: onOpen) {
            VStack(alignment: .leading, spacing: 8) {
                HStack(spacing: 6) {
                    ForEach(images.prefix(3), id: \.self) { url in
                        AsyncImage(url: URL(string: url)) { phase in
                            if case .success(let img) = phase { img.resizable().scaledToFill() }
                            else { Color(hex: 0xF3F4F6) }
                        }
                        .frame(maxWidth: .infinity)
                        .frame(height: 104)
                        .clipped()
                        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                    }
                }

                Text(pin.name)
                    .font(.geist(15, .bold))
                    .foregroundStyle(Color.ink)
                    .lineLimit(1)
                if let city = pin.city {
                    Text(city)
                        .font(.geist(12))
                        .foregroundStyle(Color.inkMuted)
                }
                if let teaser, !teaser.isEmpty {
                    Text(teaser)
                        .font(.geist(13))
                        .foregroundStyle(Color.inkMuted)
                        .lineLimit(3)
                        .lineSpacing(2)
                }
            }
            .padding(12)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Color.creamCard, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: 16, style: .continuous)
                    .stroke(Color.hairline, lineWidth: 1)
            )
        }
        .buttonStyle(.plain)
        .task {
            if teaser == nil { teaser = await FarmDetailAPI.teaser(osmId: pin.osmId)?.text }
        }
    }
}
