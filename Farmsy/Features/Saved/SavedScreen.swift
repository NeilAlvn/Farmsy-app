import SwiftUI

/// Saved farms — the same favorites the user hearts on the website.
struct SavedScreen: View {
    var onOpenFarm: (FarmPin) -> Void

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
            } else if savedPins.isEmpty {
                VStack(spacing: 12) {
                    Spacer()
                    Text("🤍").font(.geist(54))
                    Text("No saved farms yet")
                        .font(.display(24))
                        .foregroundStyle(Color.ink)
                    Text("Tap the heart on any farm to keep it here.")
                        .font(.geist(15))
                        .foregroundStyle(Color.inkMuted)
                    Spacer()
                    Spacer()
                }
                .frame(maxWidth: .infinity)
            } else {
                ScrollView(showsIndicators: false) {
                    LazyVStack(spacing: 12) {
                        ForEach(savedPins) { pin in
                            FarmCard(pin: pin, onOpen: { onOpenFarm(pin) })
                        }
                    }
                    .padding(.horizontal, 14)
                    .padding(.top, 6)
                    .padding(.bottom, 16)
                }
            }
        }
    }
}
