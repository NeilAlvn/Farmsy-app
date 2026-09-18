import SwiftUI
import PhotosUI

/// Profile — opened from the Home header. Identity, membership, what Farmsy
/// knows about you (radius, alerts), preferences, legal, and the way out.
struct ProfileScreen: View {
    @AppStorage("searchRadiusKm") private var radiusKm = 15.0
    @State private var showSurvey = false
    @Environment(SessionStore.self) private var session
    @Environment(LanguageManager.self) private var language
    @Environment(\.dismiss) private var dismiss
    @Environment(\.requestAuth) private var requestAuth
    @Environment(\.shell) private var shell

    @State private var showLanguage = false
    @State private var showAccessibility = false
    @State private var avatar = AvatarStore.shared
    @State private var contributions = Contributions.shared
    @State private var photoItem: PhotosPickerItem?
    @State private var badgeSheet: BadgeKind?
    @State private var showSignOutConfirm = false
    @State private var showDeleteInfo = false
    @State private var isDeleting = false
    @State private var deleteFailed = false
    // Non-nil once the server confirms the erase. Carries whether they still have a
    // subscription to cancel, and which rail actually charged them.
    @State private var deletedState: DeletedState?

    struct DeletedState: Identifiable {
        let id = UUID()
        let remindStore: Bool
        let source: String?
    }

    // Key the badge off *access*, not the raw status word. A "canceled" status whose
    // period has already lapsed still reads "canceled" in the DB, but the user has no
    // access — labelling them Canceled while the section below says "no membership" is
    // a contradiction on one screen. hasFullAccess is the truth both halves share.
    private var subscriptionBadge: (String, Color) {
        guard session.profile?.hasFullAccess == true else {
            // A lapsed sub (canceled/expired past its end date) is not the same as
            // never having subscribed — flag it clearly in red so it (and a reviewer)
            // can't be mistaken for a plain free account.
            switch session.profile?.subscriptionStatus {
            case "canceled", "expired": return (String(localized: "Expired"), .warnRed)
            default:                    return (String(localized: "Free"), .inkMuted)
            }
        }
        switch session.profile?.subscriptionStatus {
        case "trialing": return (String(localized: "Trial"), .farmGreen)
        case "canceled": return (String(localized: "Canceled"), .inkMuted)
        default:         return (String(localized: "Member"), .farmGreen)
        }
    }

    private var initials: String {
        let parts = session.displayName.split(separator: " ").prefix(2).compactMap { $0.first }
        return parts.isEmpty ? "?" : String(parts).uppercased()
    }

    @ViewBuilder
    private var identity: some View {
        if session.isAuthenticated {
            VStack(spacing: Space.s3) {
                PhotosPicker(selection: $photoItem, matching: .images, photoLibrary: .shared()) {
                    ZStack(alignment: .bottomTrailing) {
                        ZStack {
                            Circle().stroke(Color.vivid.opacity(0.9), style: StrokeStyle(lineWidth: 2, dash: [6, 5]))
                                .frame(width: 96, height: 96)
                            Circle().stroke(Color.farmGreen.opacity(0.35), style: StrokeStyle(lineWidth: 2, dash: [4, 6]))
                                .frame(width: 84, height: 84)
                            if let url = avatar.url {
                                AsyncImage(url: url) { phase in
                                    if case .success(let img) = phase { img.resizable().scaledToFill() }
                                    else { Text(initials).font(.ui(26, .bold)).foregroundStyle(Color.farmGreen) }
                                }
                                .frame(width: 72, height: 72)
                                .background(Color.surface)
                                .clipShape(Circle())
                            } else {
                                Text(initials)
                                    .font(.ui(26, .bold))
                                    .foregroundStyle(Color.farmGreen)
                                    .frame(width: 72, height: 72)
                                    .background(Color.surface, in: Circle())
                            }
                        }
                        // The camera badge says "this is tappable" without a label.
                        ZStack {
                            Circle().fill(Color.ink).frame(width: 32, height: 32)
                                .overlay(Circle().stroke(Color.cream, lineWidth: 2))
                            if avatar.busy {
                                ProgressView().tint(.white).controlSize(.small)
                            } else {
                                Image(systemName: "camera.fill").font(.system(size: 13, weight: .bold)).foregroundStyle(.white)
                            }
                        }
                        .offset(x: 4, y: 4)
                    }
                }
                .buttonStyle(.plain)
                .accessibilityLabel(String(localized: "Change photo"))
                .onChange(of: photoItem) { _, item in
                    guard let item, let userId = session.session?.user.id.uuidString.lowercased() else { return }
                    Task {
                        if let data = try? await item.loadTransferable(type: Data.self), let img = UIImage(data: data) {
                            if await avatar.upload(img, userId: userId) { Haptics.success() } else { Haptics.warning() }
                        }
                        photoItem = nil
                    }
                }
                HStack(spacing: Space.s2) {
                    Text(session.displayName).role(.heading)
                    Badge(text: subscriptionBadge.0, fill: subscriptionBadge.1)
                }
                if !session.email.isEmpty { Text(session.email).role(.caption, .inkMuted) }
            }
            .frame(maxWidth: .infinity)
        } else {
            VStack(spacing: Space.s3) {
                Image(systemName: "person.crop.circle")
                    .font(.system(size: 56, weight: .regular))
                    .foregroundStyle(Color.farmGreen)
                Text("You're browsing as a guest").role(.heading)
                Text("Sign in to follow farms, plan trips and keep your list on every device.")
                    .role(.bodySm, .inkMuted)
                    .multilineTextAlignment(.center)
                Button(String(localized: "Sign in")) { Haptics.tap(); dismiss(); requestAuth() }
                    .buttonStyle(PillButtonStyle(.primary, size: .medium))
            }
            .frame(maxWidth: .infinity)
        }
    }

    /// Three numbers and the eight circles. Locked ones are tappable too: the
    /// rule is the invitation.
    private var contributionsCard: some View {
        let stats = contributions.stats
        return VStack(alignment: .leading, spacing: Space.s4) {
            Text(String(localized: "Your contributions")).role(.heading)
            HStack(spacing: 0) {
                stat(stats.map { "\($0.reports)" } ?? "—", String(localized: "reports"), "flag.fill")
                Divider().frame(height: 36)
                stat(stats.map { "\($0.farms)" } ?? "—", String(localized: "farms"), "map.fill")
                Divider().frame(height: 36)
                stat(stats.map { "\($0.badges)" } ?? "—", String(localized: "badges"), "medal.fill")
            }
            Divider().overlay(Color.hairline)
            LazyVGrid(columns: Array(repeating: GridItem(.flexible()), count: 4), spacing: Space.s4) {
                ForEach(BadgeKind.allCases) { kind in
                    let earned = contributions.has(kind)
                    Button { Haptics.tap(); badgeSheet = kind } label: {
                        VStack(spacing: 6) {
                            Image(systemName: kind.icon)
                                .font(.system(size: 18, weight: .semibold))
                                .foregroundStyle(earned ? Color.ink : Color.inkFaint)
                                .frame(width: 44, height: 44)
                                .background(earned ? Color.vivid : Color.creamFill, in: Circle())
                            Text(kind.title).role(.caption, earned ? .ink : .inkFaint)
                                .multilineTextAlignment(.center).lineLimit(2)
                        }
                        .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.plain)
                }
            }
        }
        .card()
    }

    private func stat(_ value: String, _ label: String, _ icon: String) -> some View {
        VStack(spacing: 4) {
            Image(systemName: icon).font(.system(size: 16, weight: .semibold)).foregroundStyle(Color.farmGreen)
            Text(value).role(.heading)
            Text(label).role(.caption, .inkMuted)
        }
        .frame(maxWidth: .infinity)
    }

    var body: some View {
        VStack(spacing: 0) {
            ScreenHeader(String(localized: "Profile"), compact: true) {
                IconButton("xmark", label: String(localized: "Close"), small: true) { dismiss() }
            }
            .padding(.top, Space.s2)

            ScrollView(showsIndicators: false) {
                VStack(spacing: Space.s8) {
                    identity

                    if session.isAuthenticated {
                        MembershipSection()
                        contributionsCard
                    }

                    RowGroup(title: String(localized: "Farmsy")) {
                        Menu {
                            ForEach(HomeScreen.radiusChoices, id: \.self) { km in
                                Button("\(Int(km)) km") { radiusKm = km }
                            }
                        } label: {
                            Row(icon: "location", title: String(localized: "Search radius"),
                                value: "\(Int(radiusKm)) km", chevron: false) {}
                        }
                        Row(icon: "bell", title: String(localized: "Product alerts"),
                            subtitle: session.hasFullAccess ? nil : String(localized: "Farmsy Plus")) {
                            if session.hasFullAccess, let url = URL(string: "https://www.farmsy.app/alerts") {
                                UIApplication.shared.open(url)
                            } else {
                                dismiss(); shell.openPlus()
                            }
                        }
                    }

                    RowGroup(title: String(localized: "Preferences")) {
                        Row(icon: "globe", title: String(localized: "Language"),
                            value: language.current == .system ? String(localized: "System") : language.current.name) {
                            showLanguage = true
                        }
                        Row(icon: "app.badge", title: String(localized: "Notifications")) {
                            if let url = URL(string: UIApplication.openSettingsURLString) { UIApplication.shared.open(url) }
                        }
                        Row(icon: "figure.walk", title: String(localized: "Accessibility")) { showAccessibility = true }
                    }

                    RowGroup(title: String(localized: "Community")) {
                        Row(icon: "text.bubble", title: String(localized: "Give feedback")) { showSurvey = true }
                        if session.isAuthenticated {
                            Row(icon: "gift", title: String(localized: "Refer friends")) {
                                if let url = URL(string: "https://www.farmsy.app/invite") { UIApplication.shared.open(url) }
                            }
                        }
                        Row(icon: "envelope", title: String(localized: "Contact us")) {
                            if let url = URL(string: "https://www.farmsy.app/messages") { UIApplication.shared.open(url) }
                        }
                    }

                    RowGroup(title: String(localized: "Legal")) {
                        Row(icon: "hand.raised", title: String(localized: "Privacy Policy")) {
                            if let url = URL(string: "https://farmsy.app/privacy") { UIApplication.shared.open(url) }
                        }
                        Row(icon: "doc.text", title: String(localized: "Terms of Service")) {
                            if let url = URL(string: "https://farmsy.app/terms") { UIApplication.shared.open(url) }
                        }
                    }

                    if session.isAuthenticated {
                        VStack(spacing: Space.s3) {
                            Button(String(localized: "Sign out")) { Haptics.tap(); showSignOutConfirm = true }
                                .buttonStyle(PillButtonStyle(.ghost, size: .medium, block: true))
                            Button(String(localized: "Delete account")) { showDeleteInfo = true }
                                .buttonStyle(PillButtonStyle(.text, size: .small))
                                .foregroundStyle(Color.critical)
                        }
                    }

                    VStack(spacing: 2) {
                        Text("Farmsy for iOS \(Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "")")
                        // OpenStreetMap attribution (P0-5): the farm records carry OSM ids,
                        // so the ODbL credit has to be reachable in the app.
                        Text("Place data © OpenStreetMap contributors")
                    }
                    .role(.caption, .inkFaint)
                    .frame(maxWidth: .infinity)
                }
                .padding(.horizontal, Space.s4)
                .padding(.top, Space.s4)
                .padding(.bottom, Space.s8)
            }
        }
        .background(Color.cream.ignoresSafeArea())
        .task { await session.refreshProfile() }
        .task(id: session.session?.user.id) {
            guard let uid = session.session?.user.id.uuidString.lowercased(), let token = session.session?.accessToken else { return }
            await avatar.load(userId: uid)
            await contributions.refreshMine(token: token)
        }
        .sheet(isPresented: $showAccessibility) {
            AccessibilitySheet()
                .presentationDetents([.medium, .large])
                .presentationDragIndicator(.visible)
                .presentationCornerRadius(Radius.sheet)
        }
        .sheet(item: $badgeSheet) { kind in
            BadgeSheet(kind: kind, earned: contributions.has(kind))
                .presentationDetents([.medium])
                .presentationDragIndicator(.visible)
                .presentationCornerRadius(Radius.sheet)
        }
        .sheet(isPresented: $showSurvey) {
            SurveyView(mode: .feedback)
                .presentationDetents([.fraction(0.92)])
                .presentationDragIndicator(.visible)
                .presentationCornerRadius(Radius.sheet)
        }
        .sheet(isPresented: $showLanguage) {
            LanguagePickerSheet()
                .presentationDetents([.medium, .large])
                .presentationDragIndicator(.visible)
        }
        .confirmationDialog("Sign out of Farmsy?", isPresented: $showSignOutConfirm, titleVisibility: .visible) {
            Button("Sign out", role: .destructive) {
                Task {
                    await session.signOut()
                    dismiss()
                }
            }
        }
        // Apple 5.1.1(v): deletion is initiated and completed inside the app.
        .alert("Delete account", isPresented: $showDeleteInfo) {
            Button("Delete account", role: .destructive) {
                isDeleting = true
                Task {
                    let result = await session.deleteAccount()
                    isDeleting = false
                    if result.ok {
                        deletedState = DeletedState(
                            remindStore: result.storeSubscriptionReminder,
                            source: result.subscriptionSource
                        )
                    } else {
                        deleteFailed = true
                    }
                }
            }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("This permanently removes your profile, saved farms and subscription data. This can't be undone.")
        }
        .alert("Couldn't delete account", isPresented: $deleteFailed) {
            Button("OK", role: .cancel) {}
        } message: {
            Text("Please check your connection and try again.")
        }
        // Cover the sheet while the request is in flight so nothing else is tappable.
        .overlay {
            if isDeleting {
                ZStack {
                    Color.black.opacity(0.15).ignoresSafeArea()
                    ProgressView().tint(Color.farmGreen)
                        .padding(24)
                        .background(Color.cream, in: RoundedRectangle(cornerRadius: 16))
                }
            }
        }
        // Terminal confirmation once the account is gone. Dismissing returns the
        // user to the app as a guest.
        .fullScreenCover(item: $deletedState) { state in
            AccountDeletedView(remindStore: state.remindStore, source: state.source) {
                deletedState = nil
                dismiss()
            }
        }
    }
}

/// Shown after the server confirms the account is erased. The person is already
/// signed out — this is a plain acknowledgement, plus the store-cancellation nudge
/// when they still have a live subscription the app can't cancel for them.
private struct AccountDeletedView: View {
    let remindStore: Bool
    let source: String?
    let onDone: () -> Void

    /// Name the rail that actually charged them, not the phone they're holding — an
    /// iOS user who subscribed on Android has to cancel in Google Play, and pointing
    /// them at the App Store would leave the billing running.
    private var storeName: String {
        switch source {
        case "google": return String(localized: "Google Play")
        case "stripe": return String(localized: "our website")
        default:       return String(localized: "the App Store")
        }
    }

    var body: some View {
        VStack(spacing: 18) {
            Spacer()
            Image(systemName: "checkmark.circle.fill")
                .font(.system(size: 56))
                .foregroundStyle(Color.farmGreen)
            Text("Your account was deleted")
                .font(.ui(24, .bold))
                .foregroundStyle(Color.ink)
                .multilineTextAlignment(.center)
            Text("Your profile, saved farms and subscription data have been removed. Thanks for trying Farmsy.")
                .font(.ui(15))
                .foregroundStyle(Color.inkMuted)
                .multilineTextAlignment(.center)
            if remindStore {
                Text("Your membership was bought through \(storeName). Cancel it there so you aren't charged again.")
                    .font(.ui(14, .medium))
                    .foregroundStyle(Color.ink)
                    .multilineTextAlignment(.center)
                    .padding(16)
                    .background(Color.creamCard, in: RoundedRectangle(cornerRadius: 16))
            }
            Spacer()
            Button(action: onDone) {
                Text("Done")
                    .font(.ui(16, .semibold))
                    .foregroundStyle(.white)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 15)
                    .background(Color.farmGreenMap, in: RoundedRectangle(cornerRadius: 16))
            }
        }
        .padding(28)
        .background(Color.cream.ignoresSafeArea())
    }
}

struct SettingsRow: View {
    let icon: String
    let tintBg: UInt32
    let label: LocalizedStringKey
    var tint: Color = .ink
    /// Optional trailing value shown before the chevron (e.g. the current language).
    var value: String? = nil
    var action: () -> Void

    var body: some View {
        Button {
            Haptics.tap()
            action()
        } label: {
            HStack(spacing: 14) {
                Image(systemName: icon)
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(Color(hex: tintBg))
                    .frame(width: 34, height: 34)
                    .background(Color(hex: tintBg).opacity(0.14), in: Circle())
                Text(label)
                    .font(.ui(16, .medium))
                    .foregroundStyle(tint)
                Spacer()
                if let value {
                    Text(value)
                        .font(.ui(14, .medium))
                        .foregroundStyle(Color.inkMuted)
                        .lineLimit(1)
                }
                Image(systemName: "chevron.right")
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(Color.inkMuted.opacity(0.5))
            }
            .padding(.vertical, 14)
            .padding(.horizontal, 14)
        }
        .buttonStyle(.plain)
    }
}

/// The in-app language chooser. Lists every language Farmsy is translated into,
/// each named in itself, with the active one ticked. Picking one applies at once.
struct LanguagePickerSheet: View {
    @Environment(LanguageManager.self) private var language
    @Environment(\.dismiss) private var dismiss
    @State private var changed = false

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Text("LANGUAGE")
                    .font(.ui(11, .semibold))
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
            .padding(.bottom, 10)

            ScrollView(showsIndicators: false) {
                VStack(spacing: 0) {
                    ForEach(Array(LanguageManager.Lang.allCases.enumerated()), id: \.element.id) { i, lang in
                        Button {
                            language.set(lang)
                            changed = true
                        } label: {
                            HStack(spacing: 14) {
                                Text(lang.flag).font(.system(size: 22))
                                Text(lang.name)
                                    .font(.ui(16, .medium))
                                    .foregroundStyle(Color.ink)
                                Spacer()
                                if language.current == lang {
                                    Image(systemName: "checkmark")
                                        .font(.system(size: 14, weight: .bold))
                                        .foregroundStyle(Color.farmGreen)
                                }
                            }
                            .padding(.vertical, 14)
                            .padding(.horizontal, 14)
                            .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                        if i < LanguageManager.Lang.allCases.count - 1 {
                            Divider().padding(.leading, 50)
                        }
                    }
                }
                .card(padding: 4)
                .padding(.horizontal, 20)

                // The switch only fully lands on relaunch (see LanguageManager), so
                // offer a restart — with the prompt and button already in the
                // language the user just picked, not the one they're leaving.
                if changed {
                    VStack(spacing: 12) {
                        Text(language.localized("Restart Farmsy to apply your new language.", in: language.current))
                            .font(.ui(13)).foregroundStyle(Color.inkMuted)
                            .multilineTextAlignment(.center)
                        Button {
                            // No API relaunches an iOS app; terminating drops the user
                            // to the home screen and reopening comes up in the new
                            // language (persisted via AppleLanguages).
                            exit(0)
                        } label: {
                            HStack(spacing: 8) {
                                Image(systemName: "arrow.clockwise")
                                Text(language.localized("Restart now", in: language.current))
                            }
                            .font(.ui(16, .semibold))
                            .foregroundStyle(.white)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 14)
                            .background(Color.farmGreenMap, in: RoundedRectangle(cornerRadius: 14))
                        }
                        .buttonStyle(.plain)
                    }
                    .padding(16)
                    .frame(maxWidth: .infinity)
                    .background(Color(hex: 0xF3F6F2), in: RoundedRectangle(cornerRadius: 16))
                    .padding(.horizontal, 20)
                    .padding(.top, 12)
                }
                Spacer(minLength: 24)
            }
        }
        .background(Color.cream.ignoresSafeArea())
    }
}

/// Membership status, and where to go to change it.
///
/// Two rules shape this. **Lifetime is the top of the ladder** — someone who paid
/// once, forever, must never see an upsell, because there is nothing above it and
/// dangling an offer would be dishonest. And **billing lives with whoever took the
/// money**: Apple, Play and Stripe are three separate contracts, and no store lets
/// us cancel on a user's behalf. So "manage" has to send them to the rail that
/// actually charged them — pointing a web subscriber at the App Store, where they'd
/// find nothing, reads as hiding the cancel button.
struct MembershipSection: View {
    @Environment(SessionStore.self) private var session

    private var plan: String? { session.profile?.subscriptionPlan }
    private var status: String? { session.profile?.subscriptionStatus }
    private var hasAccess: Bool { session.profile?.hasFullAccess ?? false }
    private var isLifetime: Bool { plan == "lifetime" && hasAccess }
    private var isTrialing: Bool { status == "trialing" }
    /// Cancelled but still inside the period they already paid for — they keep access
    /// to the end, and deserve to be told when that is rather than sold a renewal.
    private var isCanceled: Bool { status == "canceled" }
    /// Had a membership that has now lapsed (canceled/expired past its end date). They
    /// no longer have access, but "you don't have a membership yet" is wrong for them —
    /// they had one, it ended. Distinguish so the copy (and a reviewer) read it right.
    private var isExpired: Bool { !hasAccess && (status == "canceled" || status == "expired") }

    private var billingURL: URL? {
        switch session.profile?.subscriptionSource {
        case "stripe": URL(string: "https://www.farmsy.app/account/subscription")
        case "google": URL(string: "https://play.google.com/store/account/subscriptions")
        default:       URL(string: "https://apps.apple.com/account/subscriptions")
        }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text("Membership")
                .font(.ui(13, .semibold))
                .foregroundStyle(Color.inkMuted)
                .padding(.leading, 4)

            VStack(alignment: .leading, spacing: 4) {
                if isLifetime {
                    // Nothing to sell, and nothing to cancel — but say so plainly. An
                    // empty section reads as broken; a clear statement reads as meant.
                    Text("You have Lifetime access")
                        .font(.ui(16, .bold))
                        .foregroundStyle(Color.ink)
                    Text("One payment, never expires. Nothing to manage.")
                        .font(.ui(14))
                        .foregroundStyle(Color.inkMuted)
                } else if hasAccess {
                    Text(isCanceled ? "Your membership is ending"
                         : isTrialing ? "Your trial is active"
                         : "You're on the Yearly plan")
                        .font(.ui(16, .bold))
                        .foregroundStyle(Color.ink)

                    // During a trial, say when the charge lands. A free trial that
                    // quietly becomes a bill is exactly what guideline 3.1.2 exists to
                    // stop, and the date is the whole point of the disclosure.
                    Text(subtitle)
                        .font(.ui(14))
                        .foregroundStyle(Color.inkMuted)

                    // No "Upgrade to Lifetime": the server now refuses to sell a second
                    // subscription to someone who already has one (it used to happily
                    // charge them twice), so the button could only ever 409 on a paying
                    // customer. A real upgrade must cancel the running subscription
                    // first — that's a feature, not a button.
                    Button("Manage subscription") {
                        Haptics.tap()
                        if let url = billingURL { UIApplication.shared.open(url) }
                    }
                    .font(.ui(14, .semibold))
                    .foregroundStyle(Color.farmGreen)
                    .padding(.top, 10)
                } else if isExpired {
                    Text("Your membership has expired")
                        .font(.ui(16, .bold))
                        .foregroundStyle(Color.ink)
                    Text("Renew to unlock full details for every farm again.")
                        .font(.ui(14))
                        .foregroundStyle(Color.inkMuted)
                } else {
                    Text("You don't have a membership yet")
                        .font(.ui(16, .bold))
                        .foregroundStyle(Color.ink)
                    Text("Unlock full details for every farm.")
                        .font(.ui(14))
                        .foregroundStyle(Color.inkMuted)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .card()
        }
    }

    /// Say the true thing about what happens next.
    ///
    /// A cancelled plan was being told it "renews yearly" — flatly false, and it
    /// buried the one fact the person actually needs: the day their access stops. A
    /// trial was told nothing about the charge that's coming. Both are the same
    /// failure — describing the happy path to someone who isn't on it.
    private var subtitle: String {
        let ends = session.profile?.subscriptionEndDate?.formatted(date: .long, time: .omitted)
        if isCanceled {
            if let ends { return String(localized: "Cancelled · you keep access until \(ends)") }
            return String(localized: "Cancelled · won't renew")
        }
        if isTrialing {
            if let ends { return String(localized: "Free trial · you'll be charged on \(ends)") }
            return String(localized: "Free trial · renews automatically after it ends")
        }
        return String(localized: "Renews yearly · manage where you subscribed")
    }
}