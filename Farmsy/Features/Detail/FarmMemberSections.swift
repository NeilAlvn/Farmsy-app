import SwiftUI
import PhotosUI

/// Everything below the description for a *member* (MOBILE-SPEC-MAP §3): what
/// people are saying, the details list is rendered by the caller, then the farm's
/// posts with a composer, the reviews with a form, and the claim + report links.
/// A non-member never sees this — the caller shows the locked block instead.
struct FarmMemberSections: View {
    let pin: FarmPin
    let detail: FarmDetail?
    var onClaim: () -> Void

    @Environment(SessionStore.self) private var session

    @State private var reviews: [Review] = []
    @State private var posts: [Ping] = []
    @State private var likedIds: Set<String> = []
    @State private var lightbox: LightboxSource?

    private var uid: String? { session.session?.user.id.uuidString.lowercased() }

    var body: some View {
        VStack(alignment: .leading, spacing: 22) {
            reviewsSummary
            detailsList
            whatsNew
            reviewsSection
            claimBlock
            reportLink
        }
        .task { reviews = await FarmContentAPI.reviews(osmId: pin.osmId) }
        .task {
            posts = await FarmContentAPI.posts(osmId: pin.osmId)
            if let uid { likedIds = await FarmContentAPI.likedPingIds(userId: uid) }
        }
        .fullScreenCover(item: $lightbox) { src in
            ImageLightbox(source: src) {
                var t = Transaction(); t.disablesAnimations = true
                withTransaction(t) { lightbox = nil }
            }
            .presentationBackground(.clear)
        }
    }

    // MARK: - What people are saying

    private var reviewsSummary: some View {
        VStack(alignment: .leading, spacing: 10) {
            sectionHeader(String(localized: "What people are saying"))
            if reviews.isEmpty {
                dashedNote(String(localized: "Be the first to review this farm."))
            } else {
                HStack(spacing: 8) {
                    Image(systemName: "star.fill").font(.system(size: 15)).foregroundStyle(Color.star)
                    Text(String(format: "%.1f", averageRating))
                        .font(.geist(16, .bold)).foregroundStyle(Color.ink)
                    Text("· \(reviews.count) \(reviews.count == 1 ? "review" : "reviews")")
                        .font(.geist(14)).foregroundStyle(Color.inkMuted)
                }
            }
        }
    }

    private var averageRating: Double {
        guard !reviews.isEmpty else { return 0 }
        return Double(reviews.map(\.rating).reduce(0, +)) / Double(reviews.count)
    }

    // MARK: - Details (members)

    @ViewBuilder
    private var detailsList: some View {
        let rows = detailRows
        if !rows.isEmpty {
            VStack(alignment: .leading, spacing: 10) {
                sectionHeader(String(localized: "Details"))
                VStack(spacing: 0) {
                    ForEach(Array(rows.enumerated()), id: \.offset) { _, row in
                        InfoRow(icon: row.icon, label: row.label, value: row.value)
                    }
                }
                .card(padding: 6)
            }
        }
    }

    private var detailRows: [(icon: String, label: String, value: String)] {
        var out: [(String, String, String)] = []
        if let hours = detail?.openingHours ?? pin.openingHours {
            out.append(("clock", String(localized: "Hours"), hours))
        }
        if let address = detail?.address ?? pin.address {
            out.append(("mappin.and.ellipse", String(localized: "Address"),
                        [address, detail?.postalCode ?? pin.postalCode, pin.city].compactMap(\.self).joined(separator: ", ")))
        }
        if let phone = detail?.phone { out.append(("phone", String(localized: "Phone"), phone)) }
        if let website = detail?.website { out.append(("globe", String(localized: "Website"), website)) }
        if let email = detail?.email { out.append(("envelope", String(localized: "Email"), email)) }
        if detail?.organic == true { out.append(("leaf", String(localized: "Organic"), String(localized: "Yes 🌱"))) }
        if let produce = detail?.produce, !produce.isEmpty { out.append(("basket", String(localized: "Produce"), produce)) }
        return out
    }

    // MARK: - What's new (composer + posts)

    private var whatsNew: some View {
        VStack(alignment: .leading, spacing: 12) {
            sectionHeader(String(localized: "What's new"))
            PostComposer(pin: pin) { await reload() }
            if posts.isEmpty {
                dashedNote(String(localized: "Nothing posted here today."))
            } else {
                ForEach(posts) { ping in
                    FarmPostRow(
                        ping: ping,
                        liked: likedIds.contains(ping.id),
                        onOpenImage: { idx in openLightbox(ping.images, idx, author: ping.authorName) },
                        onLike: { await like(ping) },
                        onReport: { await report(ping) })
                }
            }
        }
    }

    // MARK: - Reviews (form + list)

    private var reviewsSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            sectionHeader(String(localized: "Reviews"))
            ReviewComposer(pin: pin, existing: myReview) { await reload() }
            if reviews.isEmpty {
                Text("No reviews yet. Be the first!")
                    .font(.geist(13)).foregroundStyle(Color.inkMuted)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 8)
            } else {
                ForEach(reviews) { r in ReviewRow(review: r) }
            }
        }
    }

    private var myReview: Review? {
        guard let uid = session.session?.user.id.uuidString.lowercased() else { return nil }
        return reviews.first { $0.userId?.lowercased() == uid }
    }

    // MARK: - Claim + report

    private var claimBlock: some View {
        VStack(spacing: 8) {
            Text("Is this your farm?")
                .font(.geist(16, .bold)).foregroundStyle(Color.ink)
            Button(action: { Haptics.tap(); onClaim() }) {
                HStack(spacing: 8) {
                    Image(systemName: "shield").font(.system(size: 15, weight: .semibold))
                    Text("Claim this farm").font(.geist(15, .semibold))
                }
                .foregroundStyle(.white)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 14)
                .background(Color.farmGreen, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
            }
            .buttonStyle(.plain)
            Text("Claim it to keep its details, photos and opening hours up to date. We check every claim by hand.")
                .font(.geist(12)).foregroundStyle(Color.inkMuted)
                .multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity)
    }

    private var reportLink: some View {
        Button {
            Haptics.tap()
            if let url = URL(string: "https://www.farmsy.app/messages") { UIApplication.shared.open(url) }
        } label: {
            HStack(spacing: 6) {
                Image(systemName: "flag").font(.system(size: 13))
                Text("Report incorrect info").font(.geist(13, .medium))
            }
            .foregroundStyle(Color.inkMuted)
            .frame(maxWidth: .infinity)
        }
        .buttonStyle(.plain)
    }

    // MARK: - Helpers

    private func sectionHeader(_ title: String) -> some View {
        Text(title).font(.geist(17, .bold)).foregroundStyle(Color.ink)
    }

    private func dashedNote(_ text: String) -> some View {
        Text(text)
            .font(.geist(13)).foregroundStyle(Color.inkMuted)
            .frame(maxWidth: .infinity).padding(.vertical, 14)
            .overlay(RoundedRectangle(cornerRadius: 16, style: .continuous)
                .strokeBorder(Color(hex: 0xE5E7EB), style: StrokeStyle(lineWidth: 1, dash: [4])))
    }

    private func openLightbox(_ imgs: [String], _ start: Int, author: String) {
        var t = Transaction(); t.disablesAnimations = true
        withTransaction(t) {
            lightbox = LightboxSource(images: imgs, startIndex: start,
                                      eyebrow: String(localized: "From a post"),
                                      title: author, subtitle: pin.name)
        }
    }

    private func reload() async {
        reviews = await FarmContentAPI.reviews(osmId: pin.osmId)
        posts = await FarmContentAPI.posts(osmId: pin.osmId)
        if let uid { likedIds = await FarmContentAPI.likedPingIds(userId: uid) }
    }

    private func like(_ ping: Ping) async {
        guard let uid else { return }
        let wasLiked = likedIds.contains(ping.id)
        // Optimistic toggle.
        if wasLiked { likedIds.remove(ping.id) } else { likedIds.insert(ping.id) }
        await FarmContentAPI.toggleLike(pingId: ping.id, userId: uid, currentlyLiked: wasLiked)
    }

    private func report(_ ping: Ping) async {
        guard let uid else { return }
        await FarmContentAPI.reportPing(pingId: ping.id, userId: uid)
        Haptics.success()
    }
}

// MARK: - Post row (this farm's own post — no farm name needed)

private struct FarmPostRow: View {
    let ping: Ping
    let liked: Bool
    var onOpenImage: (Int) -> Void
    var onLike: () async -> Void
    var onReport: () async -> Void

    @State private var reported = false

    private var initials: String {
        let s = String(ping.authorName.split(separator: " ").compactMap { $0.first }.prefix(2)).uppercased()
        return s.isEmpty ? "?" : s
    }

    /// The count adjusted for the viewer's own optimistic like.
    private var displayCount: Int {
        let base = ping.likeCount
        return liked && base == 0 ? 1 : base
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 10) {
                Text(initials).font(.geist(13, .bold)).foregroundStyle(Color.farmGreen)
                    .frame(width: 36, height: 36).background(Color.farmGreen.opacity(0.12), in: Circle())
                Text(ping.authorName).font(.geist(14, .semibold)).foregroundStyle(Color.ink)
                Spacer()
            }
            if !ping.body.isEmpty {
                Text(ping.body).font(.geist(14)).foregroundStyle(Color.ink)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
            if !ping.images.isEmpty {
                FixedImageRow(urls: Array(ping.images.prefix(3)), height: 100, onTap: onOpenImage)
            }
            HStack(spacing: 16) {
                Button { Task { await onLike() } } label: {
                    HStack(spacing: 5) {
                        Image(systemName: liked ? "heart.fill" : "heart").font(.system(size: 13))
                        if displayCount > 0 { Text("\(displayCount)").font(.geist(12)) }
                    }
                    .foregroundStyle(liked ? Color.farmGreen : Color.inkMuted)
                }.buttonStyle(.plain)

                Button {
                    guard !reported else { return }
                    reported = true
                    Task { await onReport() }
                } label: {
                    HStack(spacing: 5) {
                        Image(systemName: "flag").font(.system(size: 12))
                        Text(reported ? "Reported" : "Report").font(.geist(12))
                    }
                    .foregroundStyle(Color.inkMuted)
                }.buttonStyle(.plain).disabled(reported)
            }
            .padding(.top, 2)
        }
        .padding(12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.creamCard, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: 16, style: .continuous).stroke(Color.hairline, lineWidth: 1))
    }
}

// MARK: - Review row

private struct ReviewRow: View {
    let review: Review
    var body: some View {
        VStack(alignment: .leading, spacing: 5) {
            HStack(spacing: 8) {
                Text(review.reviewerName).font(.geist(14, .semibold)).foregroundStyle(Color.ink)
                Spacer()
                HStack(spacing: 2) {
                    ForEach(1...5, id: \.self) { i in
                        Image(systemName: i <= review.rating ? "star.fill" : "star")
                            .font(.system(size: 11)).foregroundStyle(Color.star)
                    }
                }
            }
            if let body = review.body, !body.isEmpty {
                Text(body).font(.geist(14)).foregroundStyle(Color.ink)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
        }
        .padding(12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.creamCard, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: 16, style: .continuous).stroke(Color.hairline, lineWidth: 1))
    }
}

// MARK: - Review composer

private struct ReviewComposer: View {
    let pin: FarmPin
    let existing: Review?
    var onPosted: () async -> Void

    @Environment(SessionStore.self) private var session
    @State private var rating = 0
    @State private var reviewText = ""
    @State private var posting = false

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Leave a review").font(.geist(14, .semibold)).foregroundStyle(Color.ink)
            HStack(spacing: 6) {
                ForEach(1...5, id: \.self) { i in
                    Image(systemName: i <= rating ? "star.fill" : "star")
                        .font(.system(size: 22)).foregroundStyle(Color.star)
                        .onTapGesture { Haptics.tap(); rating = i }
                }
            }
            TextField("Share your experience… (optional)", text: $reviewText, axis: .vertical)
                .font(.geist(15)).lineLimit(2...4)
                .padding(12)
                .background(.white, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                .overlay(RoundedRectangle(cornerRadius: 14).stroke(Color.hairline, lineWidth: 1))
            HStack {
                Spacer()
                Button {
                    Task { await submit() }
                } label: {
                    if posting { ProgressView().tint(.white).frame(width: 80) }
                    else { Text("Submit").font(.geist(14, .semibold)).foregroundStyle(.white).frame(width: 80) }
                }
                .padding(.vertical, 10)
                .background(rating > 0 ? Color.farmGreen : Color.farmGreen.opacity(0.4),
                            in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                .disabled(rating == 0 || posting)
                .buttonStyle(.plain)
            }
        }
        .padding(14)
        .background(Color(hex: 0xF7F6F2), in: RoundedRectangle(cornerRadius: 16, style: .continuous))
        .onAppear {
            if let existing { rating = existing.rating; reviewText = existing.body ?? "" }
        }
    }

    private func submit() async {
        guard rating > 0, let uid = session.session?.user.id.uuidString.lowercased() else { return }
        posting = true; defer { posting = false }
        let name = session.email.components(separatedBy: "@").first ?? "Member"
        try? await FarmContentAPI.submitReview(
            osmId: pin.osmId, userId: uid, reviewerName: name,
            rating: rating, body: reviewText.isEmpty ? nil : reviewText)
        Haptics.success()
        await onPosted()
    }
}

// MARK: - Post composer

private struct PostComposer: View {
    let pin: FarmPin
    var onPosted: () async -> Void

    @Environment(SessionStore.self) private var session
    @State private var text = ""
    @State private var picks: [PhotosPickerItem] = []
    @State private var photos: [Data] = []
    @State private var posting = false

    private let limit = 280

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            TextField("What's new at \(pin.name)?", text: $text, axis: .vertical)
                .font(.geist(15)).lineLimit(2...5)
            if !photos.isEmpty {
                HStack(spacing: 6) {
                    ForEach(Array(photos.enumerated()), id: \.offset) { i, data in
                        if let ui = UIImage(data: data) {
                            Image(uiImage: ui).resizable().scaledToFill()
                                .frame(width: 56, height: 56).clipShape(RoundedRectangle(cornerRadius: 10))
                                .overlay(alignment: .topTrailing) {
                                    Button { photos.remove(at: i) } label: {
                                        Image(systemName: "xmark.circle.fill").foregroundStyle(.white, .black.opacity(0.5))
                                    }.buttonStyle(.plain).padding(2)
                                }
                        }
                    }
                }
            }
            HStack {
                PhotosPicker(selection: $picks, maxSelectionCount: 3, matching: .images) {
                    HStack(spacing: 6) {
                        Image(systemName: "photo.badge.plus").font(.system(size: 15))
                        Text("Photo").font(.geist(14, .medium))
                    }.foregroundStyle(Color.ink)
                }
                Text("\(limit - text.count)").font(.geist(13)).foregroundStyle(Color.inkMuted)
                Spacer()
                Button { Task { await post() } } label: {
                    if posting { ProgressView().tint(.white).frame(width: 60) }
                    else {
                        HStack(spacing: 6) {
                            Image(systemName: "paperplane.fill").font(.system(size: 13))
                            Text("Post").font(.geist(14, .semibold))
                        }.foregroundStyle(.white).frame(width: 60)
                    }
                }
                .padding(.vertical, 10)
                .background(canPost ? Color.farmGreen : Color.farmGreen.opacity(0.4),
                            in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                .disabled(!canPost || posting)
                .buttonStyle(.plain)
            }
            Text("Posted to your farm on the map.")
                .font(.geist(11)).foregroundStyle(Color.inkMuted)
        }
        .padding(14)
        .background(.white, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: 16, style: .continuous)
            .strokeBorder(Color.hairline, style: StrokeStyle(lineWidth: 1)))
        .onChange(of: picks) { _, items in
            Task {
                var out: [Data] = []
                for it in items.prefix(3) {
                    if let d = try? await it.loadTransferable(type: Data.self), d.count <= 4_000_000 { out.append(d) }
                }
                photos = out
            }
        }
    }

    private var canPost: Bool {
        !text.trimmingCharacters(in: .whitespaces).isEmpty && text.count <= limit
    }

    private func post() async {
        guard canPost, let uid = session.session?.user.id.uuidString.lowercased() else { return }
        posting = true; defer { posting = false }
        let name = session.email.components(separatedBy: "@").first ?? "Member"
        do {
            try await FarmContentAPI.createPost(
                osmId: pin.osmId, userId: uid, authorName: name,
                body: text.trimmingCharacters(in: .whitespaces), photos: photos)
            text = ""; photos = []; picks = []
            Haptics.success()
            await onPosted()
        } catch {
            Haptics.warning()
        }
    }
}
