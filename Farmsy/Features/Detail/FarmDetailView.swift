import SwiftUI
import MapKit

/// Farm detail. The full payload only exists behind the farmsy.app API's
/// subscription check — without access we show the locked state instead.
/// There is intentionally no purchase button or external link in this app;
/// membership is handled on the Farmsy website.
struct FarmDetailView: View {
    let pin: FarmPin

    @Environment(SessionStore.self) private var session
    @Environment(FavoritesStore.self) private var favorites

    @State private var detail: FarmDetail?
    @State private var isLoading = true
    @State private var isLocked = false
    @State private var showClaim = false

    var body: some View {
        Group {
            if isLocked {
                LockedAccessView(pin: pin, onClaim: { showClaim = true }) {
                    await reload()
                }
            } else {
                content
            }
        }
        .sheet(isPresented: $showClaim) { ClaimFarmView(pin: pin) }
        .background(Color.cream.ignoresSafeArea())
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button {
                    guard let userId = session.session?.user.id else { return }
                    Haptics.tap()
                    Task { await favorites.toggle(pin.osmId, userId: userId) }
                } label: {
                    Image(systemName: favorites.isSaved(pin.osmId) ? "heart.fill" : "heart")
                        .foregroundStyle(favorites.isSaved(pin.osmId) ? Color.warnRed : Color.ink)
                }
            }
        }
        .task { await reload() }
    }

    private func reload() async {
        isLoading = true
        isLocked = false
        defer { isLoading = false }

        await session.refreshProfile()
        guard let token = session.session?.accessToken else {
            isLocked = true
            return
        }
        // The farmsy.app API is the source of truth for access — always ask
        // it, and only its explicit 401/403 means "no subscription".
        do {
            detail = try await FarmDetailAPI.fetch(osmId: pin.osmId, accessToken: token)
        } catch FarmDetailError.locked {
            isLocked = true
        } catch {
            // Transient failure: fall through with pin-level info if the
            // profile says the account has access, otherwise lock.
            if !session.hasFullAccess { isLocked = true }
        }
    }

    // MARK: - Unlocked content

    private var content: some View {
        ScrollView(showsIndicators: false) {
            VStack(alignment: .leading, spacing: 18) {
                gallery

                VStack(alignment: .leading, spacing: 6) {
                    HStack(spacing: 8) {
                        ForEach(pin.categories.prefix(4)) { cat in
                            Text("\(cat.emoji) \(cat.label)")
                                .font(.geist(12, .semibold))
                                .padding(.vertical, 5)
                                .padding(.horizontal, 9)
                                .background(cat.color.opacity(0.14), in: Capsule())
                                .foregroundStyle(Color.ink)
                        }
                    }
                    Text(pin.name)
                        .font(.display(30))
                        .foregroundStyle(Color.ink)
                    HStack(spacing: 6) {
                        if let city = pin.city {
                            Text("\(city)\(pin.country.map { ", \($0)" } ?? "")")
                                .font(.geist(15))
                                .foregroundStyle(Color.inkMuted)
                        }
                        if let rating = pin.avgRating {
                            HStack(spacing: 3) {
                                Image(systemName: "star.fill")
                                    .font(.system(size: 12))
                                    .foregroundStyle(.yellow)
                                Text(String(format: "%.1f (%d)", rating, pin.reviewCount))
                                    .font(.geist(14, .semibold))
                                    .foregroundStyle(Color.ink)
                            }
                        }
                    }
                }
                .padding(.horizontal, 20)

                actionRow
                    .padding(.horizontal, 20)

                if isLoading {
                    HStack {
                        Spacer()
                        ProgressView("Loading details…")
                        Spacer()
                    }
                    .padding(.vertical, 30)
                } else {
                    detailSections
                        .padding(.horizontal, 20)
                }
            }
            .padding(.bottom, 30)
        }
    }

    private var gallery: some View {
        let urls = (detail?.images.isEmpty == false ? detail?.images : nil)
            ?? [detail?.image ?? pin.image].compactMap(\.self)

        return Group {
            if urls.isEmpty {
                LinearGradient(
                    colors: [Color.farmGreen.opacity(0.85), Color.farmGreenDeep],
                    startPoint: .topLeading, endPoint: .bottomTrailing
                )
                .frame(height: 210)
                .overlay(Text(pin.primaryCategory.emoji).font(.geist(64)))
            } else {
                TabView {
                    ForEach(urls, id: \.self) { url in
                        AsyncImage(url: URL(string: url)) { phase in
                            switch phase {
                            case .success(let image):
                                image.resizable().scaledToFill()
                            default:
                                Color.creamCard
                                    .overlay(ProgressView())
                            }
                        }
                    }
                }
                .tabViewStyle(.page)
                .frame(height: 260)
            }
        }
        .clipShape(RoundedRectangle(cornerRadius: 0))
    }

    private var actionRow: some View {
        HStack(spacing: 10) {
            if let phone = detail?.phone ?? pin.phone,
               let url = URL(string: "tel:\(phone.filter { !$0.isWhitespace })") {
                ActionButton(icon: "phone.fill", label: String(localized: "Call"), fill: Color(hex: 0x2563EB)) {
                    UIApplication.shared.open(url)
                }
            }
            if let site = detail?.website ?? pin.website,
               let url = URL(string: site.hasPrefix("http") ? site : "https://\(site)") {
                ActionButton(icon: "globe", label: String(localized: "Web"), fill: Color(hex: 0xF97316)) {
                    UIApplication.shared.open(url)
                }
            }
            ActionButton(icon: "arrow.triangle.turn.up.right.diamond.fill", label: String(localized: "Directions"), fill: .farmGreen) {
                let item = MKMapItem(placemark: MKPlacemark(coordinate: pin.coordinate))
                item.name = pin.name
                item.openInMaps()
            }
        }
    }

    @ViewBuilder
    private var detailSections: some View {
        VStack(alignment: .leading, spacing: 16) {
            if let description = detail?.description, !description.isEmpty {
                Text(description)
                    .font(.geist(16))
                    .foregroundStyle(Color.ink)
                    .lineSpacing(3)
            }

            VStack(spacing: 0) {
                if let hours = detail?.openingHours ?? pin.openingHours {
                    InfoRow(icon: "clock", label: String(localized: "Opening hours"), value: hours)
                }
                if let address = detail?.address ?? pin.address {
                    InfoRow(icon: "mappin.and.ellipse", label: String(localized: "Address"),
                            value: [address, detail?.postalCode ?? pin.postalCode, pin.city]
                                .compactMap(\.self).joined(separator: ", "))
                }
                if let email = detail?.email {
                    InfoRow(icon: "envelope", label: String(localized: "Email"), value: email)
                }
                if let op = detail?.operatorName {
                    InfoRow(icon: "person", label: String(localized: "Run by"), value: op)
                }
                if detail?.organic == true {
                    InfoRow(icon: "leaf", label: String(localized: "Organic"), value: String(localized: "Yes 🌱"))
                }
                if let produce = detail?.produce, !produce.isEmpty {
                    InfoRow(icon: "basket", label: String(localized: "Produce"), value: produce)
                }
            }
            .card(padding: 6)

            if detail?.facebook != nil || detail?.instagram != nil {
                HStack(spacing: 12) {
                    if let fb = detail?.facebook, let url = socialURL(fb, base: "https://facebook.com/") {
                        SocialChip(label: "Facebook") { UIApplication.shared.open(url) }
                    }
                    if let ig = detail?.instagram, let url = socialURL(ig, base: "https://instagram.com/") {
                        SocialChip(label: "Instagram") { UIApplication.shared.open(url) }
                    }
                }
            }

            claimLink
                .padding(.top, 6)
        }
    }

    private var claimLink: some View {
        Button {
            Haptics.tap()
            showClaim = true
        } label: {
            HStack(spacing: 8) {
                Image(systemName: "checkmark.seal")
                    .font(.system(size: 15))
                Text("Is this your farm? Claim it")
                    .font(.geist(14, .semibold))
            }
            .foregroundStyle(Color.farmGreen)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 13)
            .background(Color.farmGreenSoft, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier("claim-farm")
    }

    private func socialURL(_ value: String, base: String) -> URL? {
        if value.hasPrefix("http") { return URL(string: value) }
        return URL(string: base + value.trimmingCharacters(in: CharacterSet(charactersIn: "@/")))
    }
}

struct ActionButton: View {
    let icon: String
    let label: String
    let fill: Color
    var action: () -> Void

    var body: some View {
        Button {
            Haptics.tap()
            action()
        } label: {
            HStack(spacing: 7) {
                Image(systemName: icon).font(.system(size: 14, weight: .semibold))
                Text(label).font(.system(size: 15, weight: .bold))
            }
            .foregroundStyle(.white)
            .padding(.vertical, 13)
            .frame(maxWidth: .infinity)
            .background(fill, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
        }
        .buttonStyle(.plain)
    }
}

struct InfoRow: View {
    let icon: String
    let label: String
    let value: String

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            Image(systemName: icon)
                .font(.system(size: 16))
                .foregroundStyle(Color.farmGreen)
                .frame(width: 26)
            VStack(alignment: .leading, spacing: 2) {
                Text(label)
                    .font(.geist(13, .semibold))
                    .foregroundStyle(Color.inkMuted)
                Text(value)
                    .font(.geist(15))
                    .foregroundStyle(Color.ink)
            }
            Spacer()
        }
        .padding(12)
    }
}

struct SocialChip: View {
    let label: String
    var action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(label)
                .font(.geist(14, .semibold))
                .foregroundStyle(Color.farmGreen)
                .padding(.vertical, 10)
                .padding(.horizontal, 16)
                .background(Color.farmGreenSoft, in: Capsule())
        }
        .buttonStyle(.plain)
    }
}

// MARK: - Locked state (no purchase CTA, no external links — by design)

struct LockedAccessView: View {
    let pin: FarmPin
    var onClaim: () -> Void = {}
    var onRecheck: () async -> Void

    @Environment(FarmsStore.self) private var farms
    @Environment(PurchaseStore.self) private var purchases
    @State private var isChecking = false

    private let emojiGrid = ["🥬", "🥛", "🧀", "🥚", "🥩", "🐟",
                             "🍯", "🍷", "🧺", "🌱", "🍎", "🥔"]

    var body: some View {
        ScrollView(showsIndicators: false) {
            VStack(spacing: 24) {
                VStack(spacing: 10) {
                    Kicker(text: String(localized: "Members only"))
                    DisplayTitle(leading: String(localized: "Unlock every farm's "),
                                 emphasis: String(localized: "full story"),
                                 trailing: "", size: 32)
                }
                .padding(.top, 30)

                HStack(spacing: 0) {
                    StatTile(value: farms.pins.isEmpty ? String(localized: "1000s") : "\(farms.pins.count.formatted())+",
                             caption: String(localized: "farm shops"))
                    Divider().frame(height: 40)
                    StatTile(value: "10", caption: String(localized: "categories"))
                    Divider().frame(height: 40)
                    StatTile(value: "NL + BE", caption: String(localized: "coverage"))
                }
                .card(padding: 14)

                LazyVGrid(columns: Array(repeating: GridItem(.flexible()), count: 6), spacing: 14) {
                    ForEach(emojiGrid, id: \.self) { e in
                        Text(e).font(.geist(28))
                    }
                }
                .padding(.horizontal, 6)

                VStack(spacing: 12) {
                    Image(systemName: "lock.fill")
                        .font(.system(size: 34))
                        .foregroundStyle(Color.farmGreen)
                    Text("Unlock every farm")
                        .font(.geist(19, .bold))
                        .foregroundStyle(Color.ink)
                        .multilineTextAlignment(.center)
                    Text("Opening hours, contact details, photos and more — for \(pin.name) and every other farm on the map.")
                        .font(.geist(15))
                        .foregroundStyle(Color.inkMuted)
                        .multilineTextAlignment(.center)
                        .lineSpacing(2)
                }
                .card(padding: 22)

                if let error = purchases.purchaseError {
                    Text(error)
                        .font(.geist(14, .medium))
                        .foregroundStyle(Color.warnRed)
                        .multilineTextAlignment(.center)
                }

                // Buy. The server grants access (RevenueCat webhook writes
                // subscription_status), so after a purchase we re-ask the API
                // rather than trusting the client.
                Button {
                    Haptics.tap()
                    Task {
                        if await purchases.purchase() {
                            Haptics.success()
                            await onRecheck()
                        }
                    }
                } label: {
                    if purchases.isPurchasing {
                        ProgressView().tint(.white)
                    } else if let price = purchases.displayPrice {
                        Text("Become a member — \(price)/year")
                    } else {
                        Text("Become a member")
                    }
                }
                .buttonStyle(PrimaryButtonStyle())
                .disabled(purchases.isPurchasing)

                HStack(spacing: 18) {
                    // Apple requires a visible restore path.
                    Button("Restore purchases") {
                        Haptics.tap()
                        Task {
                            if await purchases.restore() { await onRecheck() }
                        }
                    }
                    Button("I subscribed on the web") {
                        Haptics.tap()
                        isChecking = true
                        Task {
                            await onRecheck()
                            isChecking = false
                        }
                    }
                }
                .font(.geist(14, .medium))
                .foregroundStyle(Color.inkMuted)
                .disabled(purchases.isPurchasing)

                if isChecking { ProgressView().tint(Color.farmGreen) }

                // Owners can claim without a membership.
                Button {
                    Haptics.tap()
                    onClaim()
                } label: {
                    HStack(spacing: 6) {
                        Image(systemName: "checkmark.seal")
                            .font(.system(size: 13))
                        Text("Is \(pin.name) yours? Claim it")
                            .font(.geist(14, .semibold))
                    }
                    .foregroundStyle(Color.inkMuted)
                }
                .padding(.bottom, 26)
            }
            .padding(.horizontal, 20)
        }
        .task { await purchases.loadOffering() }
    }
}
