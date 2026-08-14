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
    @State private var teaser: FarmTeaser?
    @State private var isLoading = true
    @State private var isLocked = false
    @State private var showClaim = false
    @State private var showPaywall = false

    var body: some View {
        // The card is open to everyone now — the paid fields are locked *inside* it
        // rather than in front of it, matching the web. A non-member still sees the
        // photos, name, rating and the opening of the story; the membership prompt is
        // a block within the page and a sheet, not a wall that replaces it.
        content
            .sheet(isPresented: $showClaim) { ClaimFarmView(pin: pin) }
            .sheet(isPresented: $showPaywall) {
                LockedAccessView(pin: pin, onClaim: { showPaywall = false; showClaim = true }) {
                    await reload()
                }
            }
            .background(Color.cream.ignoresSafeArea())
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button {
                        Haptics.tap()
                        // Saving a farm is a member feature — send a non-member to the
                        // paywall rather than silently doing nothing.
                        if isLocked { showPaywall = true; return }
                        guard let userId = session.session?.user.id else { return }
                        Task { await favorites.toggle(pin.osmId, userId: userId) }
                    } label: {
                        Image(systemName: favorites.isSaved(pin.osmId) ? "heart.fill" : "heart")
                            .foregroundStyle(favorites.isSaved(pin.osmId) ? Color.warnRed : Color.ink)
                    }
                }
            }
            .task { await reload() }
            // Open the farm the moment access is granted, however long that takes.
            //
            // The grant arrives from the server via RevenueCat's webhook some seconds
            // after the purchase call returns, and polling for a fixed budget is a losing
            // game: if the webhook is slower than the budget, the buyer is left sitting on
            // the very paywall they just paid to leave. Watching the profile instead means
            // the screen unlocks itself whenever the grant lands — on time or late.
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
                                    .foregroundStyle(Color.star)
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

                if isLoading && detail == nil && teaser == nil {
                    HStack {
                        Spacer()
                        ProgressView("Loading details…")
                        Spacer()
                    }
                    .padding(.vertical, 30)
                } else if isLocked {
                    lockedSections
                        .padding(.horizontal, 20)
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
            // Directions is a member feature — the maps route carries the exact
            // coordinates, which is the address in another form. Locked → paywall.
            ActionButton(icon: isLocked ? "lock.fill" : "arrow.triangle.turn.up.right.diamond.fill",
                         label: String(localized: "Directions"), fill: .farmGreen) {
                if isLocked {
                    showPaywall = true
                } else {
                    let item = MKMapItem(placemark: MKPlacemark(coordinate: pin.coordinate))
                    item.name = pin.name
                    item.openInMaps()
                }
            }
        }
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

    /// The single membership ask, inside the card: a lock, one line naming what
    /// is behind it, and a soft-green button to the purchase sheet.
    private var lockedBlock: some View {
        VStack(spacing: 12) {
            Image(systemName: "lock.fill")
                .font(.system(size: 26))
                .foregroundStyle(Color.farmGreenMap)
            Text("Farm details are for members")
                .font(.geist(17, .bold))
                .foregroundStyle(Color.ink)
                .multilineTextAlignment(.center)
            Text("Address, phone, website, directions and the full story — for \(pin.name) and every other farm on the map.")
                .font(.geist(14))
                .foregroundStyle(Color.inkMuted)
                .multilineTextAlignment(.center)
                .lineSpacing(2)
            Button {
                Haptics.tap()
                showPaywall = true
            } label: {
                Text("See membership")
                    .font(.geist(15, .semibold))
                    .foregroundStyle(.white)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 13)
                    .background(Color.farmGreenMap, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
            }
            .buttonStyle(.plain)
            .padding(.top, 2)
        }
        .frame(maxWidth: .infinity)
        .card(padding: 20)
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
