import SwiftUI

/// Saved farms — the same favorites the user hearts on the website.
struct SavedScreen: View {
    var onOpenFarm: (FarmPin) -> Void

    @Environment(\.dismiss) private var dismiss
    @Environment(FarmsStore.self) private var farms
    @Environment(FavoritesStore.self) private var favorites
    @Environment(LocationManager.self) private var locationManager
    @Environment(SessionStore.self) private var session
    @Environment(\.requestAuth) private var requestAuth

    private var savedPins: [FarmPin] {
        let saved = farms.pins.filter { favorites.isSaved($0.osmId) }
        return farms.sortedByDistance(saved, from: locationManager.location)
    }

    var body: some View {
        VStack(spacing: 0) {
            // Same header treatment as the What's New sheet: an eyebrow and a
            // circular close.
            HStack {
                Text("SAVED FARMS")
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

            content
        }
        .background(Color.cream.ignoresSafeArea())
    }

    @ViewBuilder
    private var content: some View {
        Group {
            if !session.isAuthenticated {
                VStack(spacing: 12) {
                    Spacer()
                    Text("🤍").font(.geist(54))
                    Text("Keep your favourites")
                        .font(.display(24))
                        .foregroundStyle(Color.ink)
                    Text("Sign in to save farms and find them here on every device.")
                        .font(.geist(15))
                        .foregroundStyle(Color.inkMuted)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 40)
                    Button("Sign in") {
                        Haptics.tap()
                        requestAuth()
                    }
                    .buttonStyle(PrimaryButtonStyle())
                    .padding(.horizontal, 60)
                    .padding(.top, 6)
                    Spacer()
                    Spacer()
                }
                .frame(maxWidth: .infinity)
            } else {
                // A numbered list, like the web: filled green circle + number for a
                // saved farm (with an X to remove), dashed circle + "Tap a heart to
                // save a farm" for the empty slots up to eight.
                let slots = max(8, savedPins.count)
                ScrollView(showsIndicators: false) {
                    VStack(spacing: 0) {
                        ForEach(0..<slots, id: \.self) { i in
                            if i < savedPins.count {
                                savedRow(index: i, pin: savedPins[i])
                            } else {
                                emptyRow(index: i)
                            }
                            if i < slots - 1 {
                                Divider().padding(.leading, 62)
                            }
                        }
                    }
                    .background(Color.creamCard, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
                    .overlay(RoundedRectangle(cornerRadius: 16, style: .continuous).stroke(Color.hairline, lineWidth: 1))
                    .padding(.horizontal, 14)
                    .padding(.top, 4)

                    // Discovery carousel — more farms worth saving.
                    TripRecommendations(onOpenFarm: { pin in dismiss(); onOpenFarm(pin) })
                        .padding(.horizontal, 14)
                        .padding(.top, 6)
                        .padding(.bottom, 16)
                }
            }
        }
    }

    private func savedRow(index: Int, pin: FarmPin) -> some View {
        HStack(spacing: 14) {
            Text("\(index + 1)")
                .font(.geist(13, .bold))
                .foregroundStyle(.white)
                .frame(width: 30, height: 30)
                .background(Color.farmGreenMap, in: Circle())
            VStack(alignment: .leading, spacing: 1) {
                Text(pin.name)
                    .font(.geist(15, .semibold))
                    .foregroundStyle(Color.ink)
                    .lineLimit(1)
                if let city = pin.city {
                    Text(city).font(.geist(12)).foregroundStyle(Color.inkMuted).lineLimit(1)
                }
            }
            Spacer(minLength: 6)
            Image(systemName: "xmark")
                .font(.system(size: 13, weight: .semibold))
                .foregroundStyle(Color.inkMuted)
                .frame(width: 32, height: 32)
                .contentShape(Rectangle())
                .tapCard { remove(pin) }
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 11)
        .contentShape(Rectangle())
        .tapCard(excludeTopTrailing: 44) { onOpenFarm(pin) }
    }

    private func emptyRow(index: Int) -> some View {
        HStack(spacing: 14) {
            Text("\(index + 1)")
                .font(.geist(13, .bold))
                .foregroundStyle(Color.inkMuted.opacity(0.6))
                .frame(width: 30, height: 30)
                .overlay(Circle().strokeBorder(Color(hex: 0xE5E7EB), style: StrokeStyle(lineWidth: 1.5, dash: [3])))
            VStack(alignment: .leading, spacing: 2) {
                Text("Tap a heart to save a farm")
                    .font(.geist(15))
                    .foregroundStyle(Color.inkMuted)
                Text("—").font(.geist(12)).foregroundStyle(Color.inkMuted.opacity(0.5))
            }
            Spacer(minLength: 0)
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 11)
    }

    private func remove(_ pin: FarmPin) {
        guard let userId = session.session?.user.id else { return }
        Task { await favorites.toggle(pin.osmId, userId: userId) }
    }
}
