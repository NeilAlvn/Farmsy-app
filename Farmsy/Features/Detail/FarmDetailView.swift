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
    @Environment(FarmsStore.self) private var farms
    @Environment(TripStore.self) private var trip
    @Environment(\.requestAuth) private var requestAuth
    @Environment(\.dismiss) private var dismiss

    @State private var detail: FarmDetail?
    @State private var isLoading = true
    /// A transient fetch failure — never a lock (details are public; this is a network
    /// failure). Shows an error + retry.
    @State private var loadFailed = false
    @State private var showClaim = false
    @State private var showSignIn = false
    @State private var lightbox: LightboxSource?
    /// Public gallery photos, so multiple images show even for non-members (the
    /// members' `detail.images` needs a subscription).
    @State private var galleryImages: [String] = []
    @State private var galleryLoaded = false

    var body: some View {
        // The card is open to everyone — paid fields are locked *inside* it. The
        // footer is pinned outside the scroll; everything else scrolls.
        VStack(spacing: 0) {
            pinnedHeader
            ScrollView(showsIndicators: false) {
                VStack(alignment: .leading, spacing: 16) {
                    subHeader
                    tripButton
                        .padding(.horizontal, 14)
                    photoStrip
                        .padding(.horizontal, 14)

                    if isLoading && detail == nil {
                        cardSkeleton
                            .padding(.horizontal, 14)
                    } else if loadFailed {
                        loadErrorView
                            .padding(.horizontal, 14)
                    } else {
                        // Details are public now — no signed-out lock. Everyone sees them.
                        detailSections
                            .padding(.horizontal, 14)
                    }
                }
                .padding(.top, 12)
                .padding(.bottom, 20)
            }
            footer
        }
        .background(Color.cream.ignoresSafeArea())
        // Claiming now happens on the web (`/claim/<osm_id>`, no account needed);
        // the native ClaimFarmView + /api/farms/claim stay live but unused for now.
        .sheet(isPresented: $showClaim) {
            if let url = claimURL { SafariView(url: url).ignoresSafeArea() }
        }
        .sheet(isPresented: $showSignIn) { AuthView() }
        .fullScreenCover(item: $lightbox) { src in
            ImageLightbox(source: src) { closeLightbox() }
                .presentationBackground(.clear)
        }
        .task { await reload() }
        .task { await loadGallery() }
        // Re-fetch when the account changes (sign in / out) — a signed-in account may
        // see a fuller payload than an anonymous one, so the screen fills in on sign-in.
        .onChange(of: session.session?.user.id) { _, _ in
            Task { await reload() }
        }
    }

    private func reload() async {
        isLoading = true
        loadFailed = false
        defer { isLoading = false }

        await session.refreshProfile()
        // Details are PUBLIC now — the farmsy.app route dropped the check (12 Sep), so
        // everyone, signed in or not, gets them. Send the token if we have one (a
        // signed-in account may get a richer payload), but never require it and never
        // gate the screen behind sign-in. Save + trips still prompt on tap; details do not.
        do {
            detail = try await FarmDetailAPI.fetch(osmId: pin.osmId, accessToken: session.session?.accessToken)
        } catch {
            // A signed-in transient failure. NEVER a lock — every signed-in account
            // is entitled to the details, so a network blip must not read as "you
            // can't have this" (which sends a free user to a paywall they're past).
            // Show error + retry regardless of subscription status.
            loadFailed = true
        }
    }

    /// The public description opener, shown to non-members above the locked block.
    /// Fetched once and cached in @State so a re-check after purchase doesn't refetch.
    /// The farm's gallery photos. `farm_images` is NOT anon-readable (RLS blocks
    /// it — confirmed with Aviah), so the public gallery comes from the flags
    /// endpoint's `g` array instead, which the store caches. This is what lets a
    /// non-member see the multi-photo strip.
    private func loadGallery() async {
        await farms.loadGalleriesIfNeeded()
        galleryImages = farms.galleries[pin.osmId] ?? []
        galleryLoaded = true
    }

    /// True once we have the photos to show (members' payload, or the public
    /// gallery has finished loading) — until then the strip shows a skeleton so
    /// images don't pop in one at a time.
    private var photosReady: Bool {
        (detail?.images.isEmpty == false) || galleryLoaded
    }

    // MARK: - Header

    /// Name + save/share/close, aligned on one row and pinned above the scroll —
    /// like the What's New sheet, they don't move when the card scrolls.
    private var pinnedHeader: some View {
        HStack(alignment: .center, spacing: 10) {
            Text(pin.name)
                .font(.geist(19, .bold))
                .foregroundStyle(Color.ink)
                .lineLimit(1)
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
        .padding(.horizontal, 14)
        .padding(.top, 18)
        .padding(.bottom, 4)
    }

    /// Rating, location and the badge row — these scroll with the rest.
    private var subHeader: some View {
        VStack(alignment: .leading, spacing: 8) {
            ratingRow

            // Public location line — just the town and country (NL / BE), shown to
            // everyone. The full street address stays in the members' details list.
            if let locationLine {
                HStack(spacing: 6) {
                    Image(systemName: "mappin.and.ellipse")
                        .font(.system(size: 12))
                        .foregroundStyle(Color.inkMuted)
                    Text(locationLine)
                        .font(.geist(14))
                        .foregroundStyle(Color.inkMuted)
                }
            }

            badgeRow
        }
        .padding(.horizontal, 14)
    }

    /// "Aardenburg, NL" — town plus a two-letter country. Farms are NL & BE.
    private var locationLine: String? {
        let country = pin.country.map { c -> String in
            let u = c.uppercased()
            if u.hasPrefix("NE") || u == "NL" { return "NL" }
            if u.hasPrefix("BE") { return "BE" }
            return c
        }
        return [pin.city, country].compactMap(\.self).joined(separator: ", ").nilIfEmpty
    }

    private var ratingRow: some View {
        // Just the rating (or "No reviews yet") — shown to everyone. Reviews are public
        // details now, so there is nothing to gate behind sign-in here.
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

    // MARK: - Trip button (free — add to / remove from the trip)

    @ViewBuilder
    private var tripButton: some View {
        if isLoading && detail == nil {
            SkeletonBox(cornerRadius: 16).frame(height: 44)
        } else {
            // Trips are free now — no Pro-locked prompt. Everyone sees add/remove;
            // a signed-out tap routes to sign-in. (Dropped the old `isLocked` dashed
            // "Plan a trip with Farmsy Pro" prompt + its shut lock.)
            let inTrip = trip.contains(pin.osmId)
            Button {
                Haptics.tap()
                if isSignedIn { trip.toggle(pin.osmId) } else { showSignIn = true }
            } label: {
                HStack(spacing: 8) {
                    Image(systemName: inTrip ? "checkmark" : "plus").font(.system(size: 13, weight: .semibold))
                    Text(inTrip ? "In your trip" : "Add to trip").font(.geist(14, .semibold))
                }
                .foregroundStyle(inTrip ? .white : Color.farmGreen)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 12)
                .background(
                    RoundedRectangle(cornerRadius: 16, style: .continuous)
                        .fill(inTrip ? Color.farmGreenMap : Color.clear)
                        .strokeBorder(Color.farmGreen, lineWidth: inTrip ? 0 : 1.5)
                )
            }
            .buttonStyle(.plain)
        }
    }

    // MARK: - Photo strip (cover 160 + two 72 thumbnails + "+N")

    /// Photos to show: the members' payload if we have it, else the public
    /// gallery, else just the cover — merged with the cover and de-duplicated so
    /// a farm with a cover plus gallery shows them all.
    private var stripImages: [String] {
        var source = detail?.images.isEmpty == false ? detail!.images : galleryImages
        if let cover = pin.image, !source.contains(cover) { source.insert(cover, at: 0) }
        if source.isEmpty, let cover = pin.image { source = [cover] }
        var seen = Set<String>()
        return source.filter { seen.insert($0).inserted }
    }

    /// A 3-slot skeleton matching the strip layout, shown while photos load so a
    /// farm doesn't first show one image and then pop the rest in.
    private var photoStripSkeleton: some View {
        HStack(spacing: 8) {
            SkeletonBox(cornerRadius: 16)
                .frame(maxWidth: .infinity)
                .frame(height: 160)
            VStack(spacing: 8) {
                SkeletonBox().frame(height: 76)
                SkeletonBox().frame(height: 76)
            }
            .frame(width: 84)
        }
    }

    private var photoStrip: some View {
        let imgs = stripImages
        return Group {
            if !photosReady {
                photoStripSkeleton
            } else if imgs.isEmpty {
                LinearGradient(colors: [Color.farmGreen.opacity(0.85), Color.farmGreenDeep],
                               startPoint: .topLeading, endPoint: .bottomTrailing)
                    .frame(height: 160)
                    .overlay(Text(pin.primaryCategory.emoji).font(.geist(56)))
                    .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
            } else if imgs.count == 1 {
                // One photo — no rail, just the cover.
                photoTile(imgs[0], radius: 16) { openLightbox(imgs, 0) }
                    .frame(maxWidth: .infinity)
                    .frame(height: 160)
            } else {
                HStack(spacing: 8) {
                    photoTile(imgs[0], radius: 16) { openLightbox(imgs, 0) }
                        .frame(maxWidth: .infinity)
                        .frame(height: 160)
                    VStack(spacing: 8) {
                        photoTile(imgs[1], radius: 12) { openLightbox(imgs, 1) }
                            .frame(height: 76)
                        if imgs.count > 2 {
                            photoTile(imgs[2], radius: 12,
                                      plusN: imgs.count > 3 ? imgs.count - 3 : nil) {
                                openLightbox(imgs, imgs.count > 3 ? 3 : 2)
                            }
                            .frame(height: 76)
                        } else {
                            // Exactly two photos — the bottom slot is a light-grey
                            // placeholder so the rail keeps its shape.
                            RoundedRectangle(cornerRadius: 12, style: .continuous)
                                .fill(Color(hex: 0xF3F4F6))
                                .frame(height: 76)
                        }
                    }
                    .frame(width: 84)
                }
            }
        }
    }

    private func photoTile(_ url: String, radius: CGFloat, plusN: Int? = nil, action: @escaping () -> Void) -> some View {
        // A base rectangle carries the size; the image is an overlay that fills
        // and is clipped — so the photo can never push the tile past its bounds.
        RoundedRectangle(cornerRadius: radius, style: .continuous)
            .fill(Color(hex: 0xF3F4F6))
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .overlay(
                AsyncImage(url: URL(string: url)) { phase in
                    if case .success(let img) = phase { img.resizable().scaledToFill() }
                    else { SkeletonBox(cornerRadius: radius) }
                }
            )
            .overlay {
                if let plusN {
                    ZStack {
                        Color.black.opacity(0.6)
                        Text("+\(plusN)").font(.geist(14, .bold)).foregroundStyle(.white)
                    }
                }
            }
            .clipShape(RoundedRectangle(cornerRadius: radius, style: .continuous))
            .tapCard(action)
    }

    // MARK: - Footer (pinned, does not scroll)

    private var footer: some View {
        HStack(spacing: 8) {
            // Directions is public — just opens Maps, signed in or not.
            footerButton(icon: "location.fill",
                         label: String(localized: "Directions"), filled: false) {
                // Fire before the hand-off — the OS is about to take the screen to Maps.
                Observability.capture(.farmDirections, [AnalyticsProp.osmId: pin.osmId])
                openDirections()
            }
            if let phone = detail?.phone,
               let url = URL(string: "tel:\(phone.filter { !$0.isWhitespace })") {
                footerButton(icon: "phone.fill", label: String(localized: "Call"), filled: true) {
                    Observability.capture(.farmCalled, [AnalyticsProp.osmId: pin.osmId])
                    UIApplication.shared.open(url)
                }
            } else if let site = detail?.website,
                      let url = URL(string: site.hasPrefix("http") ? site : "https://\(site)") {
                footerButton(icon: "globe", label: String(localized: "Website"), filled: true) {
                    Observability.capture(.farmWebsite, [AnalyticsProp.osmId: pin.osmId])
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

    /// Signed-in transient-failure state — a message + Retry, never a lock. Shown
    /// when a signed-in account (free or paid) hits a network failure fetching the
    /// details; a lock here would misread a network error as a permission wall.
    private var loadErrorView: some View {
        VStack(spacing: 10) {
            Text("Couldn't load this farm. Check your connection and try again.")
                .font(.geist(14, .medium))
                .foregroundStyle(Color.inkMuted)
                .multilineTextAlignment(.center)
            Button("Try again") {
                Haptics.tap()
                Task { await reload() }
            }
            .font(.geist(15, .semibold))
            .foregroundStyle(Color.farmGreenMap)
        }
        .frame(maxWidth: .infinity)
        .padding(.top, 8)
    }

    // MARK: - Actions

    private var shareURL: URL? {
        let encoded = pin.osmId.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? pin.osmId
        return URL(string: "https://www.farmsy.app/map?id=\(encoded)")
    }

    private func saveTapped() {
        // Saving needs a session. Signed out → sign-in.
        guard let userId = session.session?.user.id else { showSignIn = true; return }
        Task { await favorites.toggle(pin.osmId, userId: userId) }
    }

    private var isSignedIn: Bool { session.session?.user.id != nil }

    /// The web claim page for this farm. The route is a catch-all, so the osm_id
    /// works raw or percent-encoded — encoding keeps the URL string valid.
    private var claimURL: URL? {
        let encoded = pin.osmId.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? pin.osmId
        return URL(string: "https://www.farmsy.app/claim/\(encoded)")
    }

    private func openDirections() {
        let item = MKMapItem(placemark: MKPlacemark(coordinate: pin.coordinate))
        item.name = pin.name
        item.openInMaps()
    }

    private func openLightbox(_ imgs: [String], _ start: Int) {
        // Present without the sheet's slide-up so the viewer pops in from the
        // centre (its own scale + fade) rather than sliding from the bottom.
        var t = Transaction(); t.disablesAnimations = true
        withTransaction(t) {
            lightbox = LightboxSource(images: imgs, startIndex: start,
                                      eyebrow: String(localized: "Farm photo"), title: pin.name)
        }
    }

    private func closeLightbox() {
        var t = Transaction(); t.disablesAnimations = true
        withTransaction(t) { lightbox = nil }
    }

    // MARK: - Detail content

    /// The description, then all the detail sections in the web's order (what
    /// people are saying → details → what's new → reviews → claim → report).
    /// Public now — shown to everyone, signed in or not (the wall is gone). Luuk's
    /// #27 added the "was it open?" FarmStatusSection here; with the wall removed it
    /// lives in the public content rather than behind a sign-up gate.
    @ViewBuilder
    private var detailSections: some View {
        VStack(alignment: .leading, spacing: 20) {
            if let description = detail?.description, !description.isEmpty {
                ExpandableText(text: description)
            }
            // Reading is public, so this sits outside the member sections: a
            // farm three people found shut this week is exactly what somebody
            // deciding whether to drive needs to know, account or not.
            FarmStatusSection(osmId: pin.osmId, onNeedsSignIn: { showSignIn = true })
            FarmMemberSections(pin: pin, detail: detail, onClaim: { showClaim = true })
        }
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
    /// A tappable link row draws its value in green with a chevron.
    var isLink: Bool = false

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            Image(systemName: icon)
                .font(.system(size: 16))
                .foregroundStyle(Color.farmGreenMap)
                .frame(width: 26)
            VStack(alignment: .leading, spacing: 2) {
                Text(label)
                    .font(.geist(13, .semibold))
                    .foregroundStyle(Color.inkMuted)
                Text(value)
                    .font(.geist(15))
                    .foregroundStyle(isLink ? Color.farmGreenMap : Color.ink)
                    .lineLimit(isLink ? 1 : nil)
            }
            Spacer()
            if isLink {
                Image(systemName: "arrow.up.right")
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundStyle(Color.inkMuted)
            }
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
                    DisplayTitle(String(localized: "Unlock every farm's *full story*"), size: 32)
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
        .onAppear {
            Observability.capture(.paywallViewed,
                                  [AnalyticsProp.trigger: AnalyticsValue.Trigger.farmDetail.rawValue])
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
                    .fill(filled ? Color.farmGreenMap : Color.white)
                    .stroke(filled ? Color.clear : Color.farmGreen.opacity(0.45), lineWidth: 1.5)
            )
            .foregroundStyle(filled ? Color.white : Color.farmGreen)
        }
        .buttonStyle(.plain)
    }
}

extension String {
    var nilIfEmpty: String? { isEmpty ? nil : self }
}

/// A description that clamps to N lines with "… View more" fading in at the end
/// of the last visible line, and expands *inline* to the whole text when tapped —
/// no modal. Truncation is measured with the real font so the control only shows
/// when the text genuinely doesn't fit, and the overlay sits exactly on the last
/// clamped line (the Text's own bottom edge, nothing hidden inflating its frame).
struct ExpandableText: View {
    let text: String
    var lineLimit: Int = 3

    @State private var expanded = false
    @State private var width: CGFloat = 0

    private let font = UIFont(name: "Geist-Regular", size: 15) ?? .systemFont(ofSize: 15)
    private let moreLabel = "  … View more"
    private let lessLabel = "  View less"

    var body: some View {
        content
            .font(.geist(15))
            .lineSpacing(3)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(
                GeometryReader { geo in
                    Color.clear
                        .onAppear { width = geo.size.width }
                        .onChange(of: geo.size.width) { _, w in width = w }
                }
            )
            .tapCard { withAnimation(.easeOut(duration: 0.2)) { expanded.toggle() } }
    }

    /// The description with "… View more" / "View less" appended *inline* at the
    /// exact end of the visible text — the string is truncated to fit the clamp so
    /// the control always lands at the end of the last line, never mid-paragraph.
    private var content: Text {
        guard width > 0 else {
            return Text(text).foregroundColor(Color.ink)
        }
        if expanded {
            return Text(text).foregroundColor(Color.ink)
                + Text(lessLabel).foregroundColor(Color.farmGreen).bold()
        }
        let fit = truncatedToFit()
        guard fit.truncated else {
            return Text(text).foregroundColor(Color.ink)
        }
        return Text(fit.text).foregroundColor(Color.ink)
            + Text(moreLabel).foregroundColor(Color.farmGreen).bold()
    }

    private func height(of s: String) -> CGFloat {
        (s as NSString).boundingRect(
            with: CGSize(width: width, height: .greatestFiniteMagnitude),
            options: [.usesLineFragmentOrigin, .usesFontLeading],
            attributes: [.font: font], context: nil
        ).height
    }

    /// The longest prefix of the text such that prefix + "… View more" still fits
    /// in `lineLimit` lines. Binary search over character count.
    private func truncatedToFit() -> (text: String, truncated: Bool) {
        let maxHeight = font.lineHeight * CGFloat(lineLimit) + 1
        if height(of: text) <= maxHeight { return (text, false) }

        let chars = Array(text)
        var lo = 0, hi = chars.count, best = 0
        while lo <= hi {
            let mid = (lo + hi) / 2
            let candidate = String(chars[0..<mid]).trimmingCharacters(in: .whitespacesAndNewlines) + moreLabel
            if height(of: candidate) <= maxHeight { best = mid; lo = mid + 1 } else { hi = mid - 1 }
        }
        return (String(chars[0..<best]).trimmingCharacters(in: .whitespacesAndNewlines), true)
    }
}
