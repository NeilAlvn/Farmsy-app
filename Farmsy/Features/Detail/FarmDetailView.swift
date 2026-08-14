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
    @Environment(\.dismiss) private var dismiss

    @State private var detail: FarmDetail?
    @State private var teaser: FarmTeaser?
    @State private var isLoading = true
    @State private var isLocked = false
    @State private var showClaim = false
    @State private var showPaywall = false
    @State private var lightbox: LightboxSource?

    var body: some View {
        // The card is open to everyone — paid fields are locked *inside* it. The
        // footer is pinned outside the scroll; everything else scrolls.
        VStack(spacing: 0) {
            ScrollView(showsIndicators: false) {
                VStack(alignment: .leading, spacing: 16) {
                    header
                    photoStrip
                        .padding(.horizontal, 14)

                    if isLoading && detail == nil && teaser == nil {
                        cardSkeleton
                            .padding(.horizontal, 14)
                    } else if isLocked {
                        lockedSections
                            .padding(.horizontal, 14)
                    } else {
                        detailSections
                            .padding(.horizontal, 14)
                    }
                }
                .padding(.top, 6)
                .padding(.bottom, 20)
            }
            footer
        }
        .background(Color.cream.ignoresSafeArea())
        .sheet(isPresented: $showClaim) { ClaimFarmView(pin: pin) }
        .sheet(isPresented: $showPaywall) {
            LockedAccessView(pin: pin, onClaim: { showPaywall = false; showClaim = true }) {
                await reload()
            }
        }
        .fullScreenCover(item: $lightbox) { src in
            ImageLightbox(source: src) { lightbox = nil }
                .presentationBackground(.clear)
        }
        .task { await reload() }
        // Open the farm the moment access is granted, however long that takes —
        // the grant lands seconds after the purchase call via RevenueCat's webhook.
        .onChange(of: session.profile?.hasFullAccess ?? false) { _, granted in
            if granted && isLocked {
                showPaywall = false
                Task { await reload() }
            }
        }
    }

    private func reload() async {
        isLoading = true
        defer { isLoading = false }

        await session.refreshProfile()
        guard let token = session.session?.accessToken else {
            isLocked = true
            await loadTeaser()
            return
        }
        // The farmsy.app API is the source of truth for access — always ask
        // it, and only its explicit 401/403 means "no subscription".
        do {
            detail = try await FarmDetailAPI.fetch(osmId: pin.osmId, accessToken: token)
            isLocked = false
        } catch FarmDetailError.locked {
            isLocked = true
            await loadTeaser()
        } catch {
            // Transient failure: keep the member view if the profile says the
            // account has access, otherwise fall back to the open/locked card.
            if session.hasFullAccess {
                isLocked = false
            } else {
                isLocked = true
                await loadTeaser()
            }
        }
    }

    /// The public description opener, shown to non-members above the locked block.
    /// Fetched once and cached in @State so a re-check after purchase doesn't refetch.
    private func loadTeaser() async {
        if teaser == nil { teaser = await FarmDetailAPI.teaser(osmId: pin.osmId) }
    }

    // MARK: - Header (name, actions, rating, address, badges)

    private var header: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(alignment: .top, spacing: 10) {
                Text(pin.name)
                    .font(.geist(19, .bold))
                    .foregroundStyle(Color.ink)
                    .fixedSize(horizontal: false, vertical: true)
                Spacer(minLength: 8)
                HStack(spacing: 6) {
                    headerCircle(favorites.isSaved(pin.osmId) ? "heart.fill" : "heart",
                                 tint: favorites.isSaved(pin.osmId) ? Color.warnRed : Color(hex: 0x6B7280),
                                 action: saveTapped)
                    if let shareURL {
                        ShareLink(item: shareURL) {
                            headerCircleLabel("square.and.arrow.up", tint: Color(hex: 0x6B7280))
                        }
                    }
                    headerCircle("xmark", tint: Color(hex: 0x6B7280)) { dismiss() }
                }
            }

            ratingRow

            if let address = detail?.address, !address.isEmpty {
                HStack(alignment: .top, spacing: 6) {
                    Image(systemName: "mappin.and.ellipse")
                        .font(.system(size: 12))
                        .foregroundStyle(Color.inkMuted)
                    Text([address, detail?.postalCode ?? pin.postalCode, pin.city]
                        .compactMap(\.self).joined(separator: ", "))
                        .font(.geist(13))
                        .foregroundStyle(Color.inkMuted)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }

            badgeRow
        }
        .padding(.horizontal, 14)
        .padding(.top, 8)
    }

    private var ratingRow: some View {
        Button {
            if isLocked { Haptics.tap(); showPaywall = true }
        } label: {
            HStack(spacing: 6) {
                if let rating = pin.avgRating {
                    Image(systemName: "star.fill").font(.system(size: 13)).foregroundStyle(Color.star)
                    Text(String(format: "%.1f", rating)).font(.geist(14, .semibold)).foregroundStyle(Color.ink)
                    Text("(\(pin.reviewCount))").font(.geist(12)).foregroundStyle(Color.inkMuted)
                } else {
                    Text("No reviews yet").font(.geist(13)).foregroundStyle(Color.inkMuted)
                }
            }
        }
        .buttonStyle(.plain)
    }

    /// Category chips (in their colours), then verified, then open-now — one line
    /// that scrolls horizontally rather than wrapping (MOBILE-SPEC-MAP §3).
    private var badgeRow: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 6) {
                ForEach(pin.categories.prefix(4)) { cat in
                    Text("\(cat.emoji) \(cat.label)")
                        .font(.geist(11, .semibold))
                        .foregroundStyle(.white)
                        .padding(.vertical, 4).padding(.horizontal, 10)
                        .background(cat.color, in: Capsule())
                }
                if pin.isVerified {
                    HStack(spacing: 4) {
                        Image(systemName: "checkmark.seal.fill").font(.system(size: 10))
                        Text("Verified").font(.geist(11, .semibold))
                    }
                    .foregroundStyle(Color.farmGreenMap)
                    .padding(.vertical, 4).padding(.horizontal, 10)
                    .overlay(Capsule().stroke(Color.farmGreenMap.opacity(0.4), lineWidth: 1))
                }
                if FarmFilters.isOpenToday(pin.openingHours) {
                    HStack(spacing: 5) {
                        Circle().fill(Color(hex: 0x10B981)).frame(width: 6, height: 6)
                        Text("Open now").font(.geist(11, .semibold))
                    }
                    .foregroundStyle(Color(hex: 0x047857))
                    .padding(.vertical, 4).padding(.horizontal, 10)
                    .background(Color(hex: 0xECFDF5), in: Capsule())
                }
            }
        }
    }

    private func headerCircleLabel(_ icon: String, tint: Color) -> some View {
        Image(systemName: icon)
            .font(.system(size: 14, weight: .semibold))
            .foregroundStyle(tint)
            .frame(width: 32, height: 32)
            .background(Color(hex: 0xF3F4F6), in: Circle())
    }

    private func headerCircle(_ icon: String, tint: Color, action: @escaping () -> Void) -> some View {
        Button {
            Haptics.tap()
            action()
        } label: { headerCircleLabel(icon, tint: tint) }
        .buttonStyle(.plain)
    }

    // MARK: - Photo strip (cover 160 + two 72 thumbnails + "+N")

    private var photoStrip: some View {
        let imgs = (detail?.images.isEmpty == false ? detail!.images : [pin.image].compactMap(\.self))
        return Group {
            if imgs.isEmpty {
                LinearGradient(colors: [Color.farmGreen.opacity(0.85), Color.farmGreenDeep],
                               startPoint: .topLeading, endPoint: .bottomTrailing)
                    .frame(height: 160)
                    .overlay(Text(pin.primaryCategory.emoji).font(.geist(56)))
                    .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
            } else {
                HStack(spacing: 8) {
                    photoTile(imgs[0], radius: 16) { openLightbox(imgs, 0) }
                        .frame(maxWidth: .infinity)
                        .frame(height: 160)
                    if imgs.count > 1 {
                        VStack(spacing: 8) {
                            photoTile(imgs[1], radius: 12) { openLightbox(imgs, 1) }
                                .frame(height: 72)
                            if imgs.count > 2 {
                                photoTile(imgs[2], radius: 12,
                                          plusN: imgs.count > 3 ? imgs.count - 3 : nil) {
                                    openLightbox(imgs, imgs.count > 3 ? 3 : 2)
                                }
                                .frame(height: 72)
                            }
                        }
                        .frame(width: 80)
                    }
                }
            }
        }
    }

    private func photoTile(_ url: String, radius: CGFloat, plusN: Int? = nil, action: @escaping () -> Void) -> some View {
        Button(action: { Haptics.tap(); action() }) {
            ZStack {
                Color(hex: 0xF3F4F6)
                AsyncImage(url: URL(string: url)) { phase in
                    if case .success(let img) = phase { img.resizable().scaledToFill() }
                    else { Color(hex: 0xF3F4F6) }
                }
                if let plusN {
                    Color.black.opacity(0.6)
                    Text("+\(plusN)").font(.geist(14, .bold)).foregroundStyle(.white)
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .clipped()
            .clipShape(RoundedRectangle(cornerRadius: radius, style: .continuous))
        }
        .buttonStyle(.plain)
    }

    // MARK: - Footer (pinned, does not scroll)

    private var footer: some View {
        HStack(spacing: 8) {
            footerButton(icon: isLocked ? "lock.fill" : "location.fill",
                         label: String(localized: "Directions"), filled: false) {
                if isLocked { showPaywall = true } else { openDirections() }
            }
            if let phone = detail?.phone,
               let url = URL(string: "tel:\(phone.filter { !$0.isWhitespace })") {
                footerButton(icon: "phone.fill", label: String(localized: "Call"), filled: true) {
                    UIApplication.shared.open(url)
                }
            } else if let site = detail?.website,
                      let url = URL(string: site.hasPrefix("http") ? site : "https://\(site)") {
                footerButton(icon: "globe", label: String(localized: "Website"), filled: true) {
                    UIApplication.shared.open(url)
                }
            }
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 12)
        .background(
            Color.cream
                .overlay(alignment: .top) { Rectangle().fill(Color.hairline).frame(height: 1) }
                .ignoresSafeArea(edges: .bottom)
        )
    }

    private func footerButton(icon: String, label: String, filled: Bool, action: @escaping () -> Void) -> some View {
        Button(action: { Haptics.tap(); action() }) {
            HStack(spacing: 7) {
                Image(systemName: icon).font(.system(size: 14, weight: .semibold))
                Text(label).font(.geist(14, .semibold))
            }
            .foregroundStyle(filled ? .white : Color.ink)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 12)
            .background(
                RoundedRectangle(cornerRadius: 16, style: .continuous)
                    .fill(filled ? Color.farmGreenMap : Color.clear)
                    .stroke(filled ? Color.clear : Color.hairline, lineWidth: 1)
            )
        }
        .buttonStyle(.plain)
    }

    private var cardSkeleton: some View {
        VStack(alignment: .leading, spacing: 12) {
            RoundedRectangle(cornerRadius: 6).fill(Color(hex: 0xECEBE8)).frame(height: 12).frame(maxWidth: .infinity)
            RoundedRectangle(cornerRadius: 6).fill(Color(hex: 0xECEBE8)).frame(height: 12).padding(.trailing, 60)
            RoundedRectangle(cornerRadius: 6).fill(Color(hex: 0xECEBE8)).frame(height: 12).padding(.trailing, 140)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.top, 8)
    }

    // MARK: - Actions

    private var shareURL: URL? {
        let encoded = pin.osmId.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? pin.osmId
        return URL(string: "https://www.farmsy.app/map?id=\(encoded)")
    }

    private func saveTapped() {
        if isLocked { showPaywall = true; return }
        guard let userId = session.session?.user.id else { return }
        Task { await favorites.toggle(pin.osmId, userId: userId) }
    }

    private func openDirections() {
        let item = MKMapItem(placemark: MKPlacemark(coordinate: pin.coordinate))
        item.name = pin.name
        item.openInMaps()
    }

    private func openLightbox(_ imgs: [String], _ start: Int) {
        lightbox = LightboxSource(images: imgs, startIndex: start,
                                  eyebrow: String(localized: "Farm photo"), title: pin.name)
    }

    // MARK: - Locked content (non-member: teaser + one membership block)

    @ViewBuilder
    private var lockedSections: some View {
        VStack(alignment: .leading, spacing: 16) {
            // Opening hours are free — show them on the open card, not behind the lock.
            if let hours = pin.openingHours, !hours.isEmpty {
                VStack(spacing: 0) {
                    InfoRow(icon: "clock", label: String(localized: "Opening hours"), value: hours)
                }
                .card(padding: 6)
            }

            if let teaser {
                VStack(alignment: .leading, spacing: 8) {
                    (Text(teaser.text) + Text(teaser.truncated ? " …" : ""))
                        .font(.geist(16))
                        .foregroundStyle(Color.ink)
                        .lineSpacing(3)
                    if teaser.truncated {
                        Button {
                            Haptics.tap()
                            showPaywall = true
                        } label: {
                            Text("View more")
                                .font(.geist(15, .semibold))
                                .foregroundStyle(Color.farmGreen)
                        }
                        .buttonStyle(.plain)
                    }
                }
            }

            lockedBlock

            claimLink
                .padding(.top, 6)
        }
    }

    /// The single membership ask, inside the card, matching the web: empty grey
    /// bars behind a blur (the paid values are never sent, so there is nothing
    /// real to reveal — the blur is texture, not a cover), a lock in a soft disc,
    /// one line naming what is behind it, and a soft-green button to the purchase.
    private var lockedBlock: some View {
        ZStack {
            lockedBarsBackground
                .blur(radius: 7)
                .allowsHitTesting(false)

            VStack(spacing: 12) {
                Image(systemName: "lock.fill")
                    .font(.system(size: 20))
                    .foregroundStyle(Color.farmGreen)
                    .frame(width: 52, height: 52)
                    .background(Color.farmGreen.opacity(0.10), in: Circle())
                Text("Farm details are for members")
                    .font(.geist(17, .bold))
                    .foregroundStyle(Color.ink)
                    .multilineTextAlignment(.center)
                Text("Address, phone, website and what this farm sells.")
                    .font(.geist(14))
                    .foregroundStyle(Color.inkMuted)
                    .multilineTextAlignment(.center)
                    .lineSpacing(2)
                Button {
                    Haptics.tap()
                    showPaywall = true
                } label: {
                    HStack(spacing: 8) {
                        Text("See full details")
                            .font(.geist(15, .semibold))
                        Image(systemName: "arrow.right")
                            .font(.system(size: 13, weight: .semibold))
                    }
                    .foregroundStyle(.white)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 14)
                    .background(Color.farmGreenMap, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
                }
                .buttonStyle(.plain)
                .padding(.top, 4)
            }
        }
        .padding(20)
        .frame(maxWidth: .infinity)
        .background(Color.creamCard, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .stroke(Color.hairline, lineWidth: 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
    }

    /// Faux content behind the lock: uneven grey bars, so the blurred area reads
    /// as "there is more here" rather than as an empty panel.
    private var lockedBarsBackground: some View {
        GeometryReader { geo in
            VStack(alignment: .leading, spacing: 14) {
                ForEach(Array([0.78, 0.95, 0.6, 0.88, 0.5].enumerated()), id: \.offset) { _, fraction in
                    RoundedRectangle(cornerRadius: 6)
                        .fill(Color.inkMuted.opacity(0.14))
                        .frame(width: geo.size.width * fraction, height: 13)
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
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
            .background(Color.farmGreenSoft, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
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
            .background(fill, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
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
    @Environment(SessionStore.self) private var session
    @State private var isChecking = false

    /// Access is granted by the server after RevenueCat's webhook writes
    /// subscription_status — which lands a few seconds *after* the purchase call
    /// returns. Re-checking once, immediately, races the webhook and finds the
    /// profile still 'free'. Poll a few times so the screen unlocks on its own
    /// when the grant lands.
    private func awaitGrant() async {
        isChecking = true
        // Only nudge the profile — FarmDetailView watches it and opens the farm the
        // moment access appears, so this loop doesn't have to win a race against the
        // webhook to be correct. It just saves waiting on the next natural refresh.
        for _ in 0..<12 {
            await session.refreshProfile()
            if session.hasFullAccess { break }
            try? await Task.sleep(nanoseconds: 1_500_000_000)
        }
        isChecking = false
    }

    private let emojiGrid = ["🥬", "🥛", "🧀", "🥚", "🥩", "🐟",
                             "🍯", "🍷", "🧺", "🌱", "🍎", "🥔"]

    /// A returning member whose subscription has lapsed — frame the paywall as a
    /// "welcome back / resubscribe", not a first-time "become a member".
    private var isExpired: Bool {
        let s = session.profile?.subscriptionStatus
        return session.hasFullAccess == false && (s == "canceled" || s == "expired")
    }

    var body: some View {
        ScrollView(showsIndicators: false) {
            VStack(spacing: 24) {
                VStack(spacing: 10) {
                    Kicker(text: isExpired ? String(localized: "Welcome back")
                                           : String(localized: "Members only"))
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
                    Text(isExpired ? "Your membership has expired" : "Unlock every farm")
                        .font(.geist(19, .bold))
                        .foregroundStyle(Color.ink)
                        .multilineTextAlignment(.center)
                    Text(isExpired
                         ? "Resubscribe to reopen opening hours, contact details, photos and more — for \(pin.name) and every other farm on the map."
                         : "Opening hours, contact details, photos and more — for \(pin.name) and every other farm on the map.")
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
                if purchases.isPurchasing || isChecking {
                    ProgressView().tint(Color.farmGreen)
                } else if purchases.productsUnavailable {
                    // Fetched, but the store handed back nothing to sell (products
                    // unavailable / rejected, or a network failure). Show a real
                    // message + retry — never an endless spinner, which reads to a
                    // reviewer as "can't access subscriptions" (guideline 2.1).
                    VStack(spacing: 10) {
                        Text("Memberships can't be loaded right now.")
                            .font(.geist(15, .semibold))
                            .foregroundStyle(Color.ink)
                        Text("This is usually temporary — tap to try again.")
                            .font(.geist(13))
                            .foregroundStyle(Color.inkMuted)
                            .multilineTextAlignment(.center)
                        Button("Try again") {
                            Haptics.tap()
                            Task { await purchases.loadOffering(force: true) }
                        }
                        .font(.geist(14, .semibold))
                        .foregroundStyle(Color.farmGreen)
                        .padding(.top, 2)
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 8)
                } else if purchases.yearlyPrice == nil {
                    // Offering still loading — a spinner, not a half-drawn paywall.
                    ProgressView().tint(Color.farmGreen)
                } else {
                    let uid = session.session?.user.id
                    // With a trial, lead with the free days and put the price it
                    // converts to underneath. Without one (a returning subscriber
                    // isn't eligible, and StoreKit tells us so), just the price — we
                    // never advertise a trial someone won't actually get.
                    let trialDays = purchases.yearlyFreeTrialDays
                    // The two plan cards sit tight together (8pt), then the outer
                    // stack's larger gap separates them from the terms/restore below.
                    VStack(spacing: 8) {
                        PlanButton(
                            label: trialDays.map { String(localized: "\($0) days free") }
                                ?? String(localized: "Yearly"),
                            detail: purchases.yearlyPrice.map { price in
                                trialDays == nil
                                    ? String(localized: "\(price) / year")
                                    : String(localized: "then \(price) / year")
                            },
                            filled: true
                        ) {
                            Task {
                                if await purchases.purchase(purchases.yearlyPackage, userId: uid) {
                                    Haptics.success(); await awaitGrant()
                                }
                            }
                        }
                        if let price = purchases.lifetimePrice {
                            PlanButton(label: String(localized: "Lifetime"),
                                       detail: "\(price) · " + String(localized: "One payment, yours forever"),
                                       filled: false) {
                                Task {
                                    if await purchases.purchase(purchases.lifetimePackage, userId: uid) {
                                        Haptics.success(); await awaitGrant()
                                    }
                                }
                            }
                        }
                    }

                    // The full terms, spelled out before the user can buy: how long
                    // it's free, what it renews at, and how to get out. A trial that
                    // quietly turns into a charge is exactly what guideline 3.1.2
                    // exists to stop — and a rotten way to treat someone besides.
                    if let days = trialDays, let price = purchases.yearlyPrice {
                        Text("Free for \(days) days, then \(price) per year. Cancel anytime in Settings.")
                            .font(.geist(12))
                            .foregroundStyle(Color.inkMuted)
                            .multilineTextAlignment(.center)
                            .padding(.top, 4)
                    }

                    // Restore only. "I subscribed on the web" removed — the app
                    // already re-checks the server on open, so web subscribers get
                    // access without it, and a manual "I paid elsewhere" control
                    // reads as sketchy next to Apple's own purchase flow.
                    Button("Restore purchases") {
                        Haptics.tap()
                        Task { if await purchases.restore() { await awaitGrant() } }
                    }
                    .font(.geist(14, .medium))
                    .foregroundStyle(Color.inkMuted)
                }

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

/// One purchasable plan on the paywall. Two lines — label above, price below —
/// kept short so a large Dynamic Type size shrinks rather than reflows. Filled is
/// the primary (yearly); outlined is the secondary (lifetime).
struct PlanButton: View {
    let label: String
    let detail: String?
    let filled: Bool
    let action: () -> Void

    var body: some View {
        Button {
            Haptics.tap()
            action()
        } label: {
            VStack(spacing: 2) {
                Text(detail == nil ? String(localized: "Become a member") : label)
                    .font(.geist(17, .semibold))
                    .minimumScaleFactor(0.7)
                    .lineLimit(1)
                if let detail {
                    Text(detail)
                        .font(.geist(14))
                        .minimumScaleFactor(0.7)
                        .lineLimit(1)
                        .foregroundStyle(filled ? Color.white.opacity(0.9) : Color.ink)
                }
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 16)
            .background(
                RoundedRectangle(cornerRadius: 16, style: .continuous)
                    .fill(filled ? Color.farmGreen : Color.white)
                    .stroke(filled ? Color.clear : Color.farmGreen.opacity(0.45), lineWidth: 1.5)
            )
            .foregroundStyle(filled ? Color.white : Color.farmGreen)
        }
        .buttonStyle(.plain)
    }
}
