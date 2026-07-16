import SwiftUI

struct SettingsSheet: View {
    @Environment(SessionStore.self) private var session
    @Environment(\.dismiss) private var dismiss
    @Environment(\.requestAuth) private var requestAuth

    @State private var showSignOutConfirm = false
    @State private var showDeleteInfo = false
    @State private var isDeleting = false
    @State private var deleteFailed = false
    // Non-nil once the server confirms the erase; the Bool is whether they still
    // have a store subscription to cancel themselves.
    @State private var deletedReminder: Bool?

    // Key the badge off *access*, not the raw status word. A "canceled" status whose
    // period has already lapsed still reads "canceled" in the DB, but the user has no
    // access — labelling them Canceled while the section below says "no membership" is
    // a contradiction on one screen. hasFullAccess is the truth both halves share.
    private var subscriptionBadge: (String, Color) {
        guard session.profile?.hasFullAccess == true else {
            return (String(localized: "Free"), .inkMuted)
        }
        switch session.profile?.subscriptionStatus {
        case "trialing": return (String(localized: "Trial"), .farmGreen)
        case "canceled": return (String(localized: "Canceled"), .inkMuted)
        default:         return (String(localized: "Member"), .farmGreen)
        }
    }

    var body: some View {
        VStack(spacing: 0) {
            Text("Settings")
                .font(.display(26))
                .foregroundStyle(Color.ink)
                .padding(.top, 26)
                .padding(.bottom, 18)

            ScrollView(showsIndicators: false) {
                VStack(spacing: 14) {
                    // Account
                    if session.isAuthenticated {
                        HStack(spacing: 12) {
                            Image("FarmsyLogo")
                                .resizable()
                                .scaledToFit()
                                .frame(height: 42)
                            VStack(alignment: .leading, spacing: 2) {
                                Text(session.email.isEmpty ? String(localized: "Signed in") : session.email)
                                    .font(.geist(15, .semibold))
                                    .foregroundStyle(Color.ink)
                                    .lineLimit(1)
                                // Only name a plan while it still grants access — a
                                // lapsed cancellation keeps subscription_plan in the DB.
                                Text((session.profile?.hasFullAccess == true ? session.profile?.subscriptionPlan : nil).map { String(localized: "\($0.capitalized) plan") } ?? String(localized: "Farmsy account"))
                                    .font(.geist(13))
                                    .foregroundStyle(Color.inkMuted)
                            }
                            Spacer()
                            Text(subscriptionBadge.0)
                                .font(.geist(12, .bold))
                                .foregroundStyle(.white)
                                .padding(.vertical, 5)
                                .padding(.horizontal, 10)
                                .background(subscriptionBadge.1, in: Capsule())
                        }
                        .card()
                    } else {
                        // Guest: invite to sign in instead of account info.
                        HStack(spacing: 12) {
                            Image("FarmsyLogo")
                                .resizable()
                                .scaledToFit()
                                .frame(height: 42)
                            VStack(alignment: .leading, spacing: 2) {
                                Text("You're browsing as a guest")
                                    .font(.geist(15, .semibold))
                                    .foregroundStyle(Color.ink)
                                Text("Sign in to save farms and see details")
                                    .font(.geist(13))
                                    .foregroundStyle(Color.inkMuted)
                            }
                            Spacer()
                            Button("Sign in") {
                                Haptics.tap()
                                requestAuth()
                            }
                            .font(.geist(13, .bold))
                            .foregroundStyle(.white)
                            .padding(.vertical, 7)
                            .padding(.horizontal, 12)
                            .background(Color.farmGreen, in: Capsule())
                            .buttonStyle(.plain)
                        }
                        .card()
                    }

                    // Membership. Signed-in users only — a guest has no subscription
                    // to manage, and the paywall is where they'd start one.
                    if session.isAuthenticated {
                        MembershipSection()
                    }

                    VStack(spacing: 0) {
                        SettingsRow(icon: "bell.fill", tintBg: 0xF5B301, label: "Notifications") {
                            if let url = URL(string: UIApplication.openSettingsURLString) {
                                UIApplication.shared.open(url)
                            }
                        }
                        // Refer friends, signed-in only — a guest has no code to
                        // share. Opens the web invite page rather than duplicating the
                        // referral dashboard natively; the signup-side code capture,
                        // the part that actually earns referrals, stays in the app.
                        if session.isAuthenticated {
                            Divider().padding(.leading, 62)
                            SettingsRow(icon: "gift.fill", tintBg: 0xEC4899, label: "Refer friends") {
                                if let url = URL(string: "https://www.farmsy.app/invite") {
                                    UIApplication.shared.open(url)
                                }
                            }
                        }
                        Divider().padding(.leading, 62)
                        SettingsRow(icon: "envelope.fill", tintBg: 0x38BDF8, label: "Contact us") {
                            if let url = URL(string: "https://www.farmsy.app/messages") {
                                UIApplication.shared.open(url)
                            }
                        }
                    }
                    .card(padding: 4)

                    // Legal
                    VStack(spacing: 0) {
                        SettingsRow(icon: "hand.raised.fill", tintBg: 0x8B5CF6, label: "Privacy Policy") {
                            if let url = URL(string: "https://farmsy.app/privacy") {
                                UIApplication.shared.open(url)
                            }
                        }
                        Divider().padding(.leading, 62)
                        SettingsRow(icon: "doc.text.fill", tintBg: 0x64748B, label: "Terms of Service") {
                            if let url = URL(string: "https://farmsy.app/terms") {
                                UIApplication.shared.open(url)
                            }
                        }
                    }
                    .card(padding: 4)

                    if session.isAuthenticated {
                        VStack(spacing: 0) {
                            SettingsRow(icon: "rectangle.portrait.and.arrow.right", tintBg: 0x3F5E3A, label: "Sign out") {
                                showSignOutConfirm = true
                            }
                            Divider().padding(.leading, 62)
                            SettingsRow(icon: "trash.fill", tintBg: 0xDC2626, label: "Delete account", tint: .warnRed) {
                                showDeleteInfo = true
                            }
                        }
                        .card(padding: 4)
                    }

                    Text("Farmsy for iOS \(Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "")")
                        .font(.geist(12))
                        .foregroundStyle(Color.inkMuted.opacity(0.7))
                        .padding(.top, 8)
                }
                .padding(.horizontal, 20)
                .padding(.bottom, 24)
            }
        }
        .background(Color.cream.ignoresSafeArea())
        .task { await session.refreshProfile() }
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
                        deletedReminder = result.storeSubscriptionReminder
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
        .fullScreenCover(isPresented: Binding(
            get: { deletedReminder != nil },
            set: { if !$0 { deletedReminder = nil } }
        )) {
            AccountDeletedView(remindStore: deletedReminder ?? false) {
                deletedReminder = nil
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
    let onDone: () -> Void

    var body: some View {
        VStack(spacing: 18) {
            Spacer()
            Image(systemName: "checkmark.circle.fill")
                .font(.system(size: 56))
                .foregroundStyle(Color.farmGreen)
            Text("Your account was deleted")
                .font(.display(24))
                .foregroundStyle(Color.ink)
                .multilineTextAlignment(.center)
            Text("Your profile, saved farms and subscription data have been removed. Thanks for trying Farmsy.")
                .font(.geist(15))
                .foregroundStyle(Color.inkMuted)
                .multilineTextAlignment(.center)
            if remindStore {
                Text("Your membership was bought through the App Store. Cancel it in your Apple subscriptions so you aren't charged again.")
                    .font(.geist(14, .medium))
                    .foregroundStyle(Color.ink)
                    .multilineTextAlignment(.center)
                    .padding(16)
                    .background(Color.creamCard, in: RoundedRectangle(cornerRadius: 14))
            }
            Spacer()
            Button(action: onDone) {
                Text("Done")
                    .font(.geist(16, .semibold))
                    .foregroundStyle(.white)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 15)
                    .background(Color.farmGreen, in: RoundedRectangle(cornerRadius: 14))
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
                    .font(.geist(16, .medium))
                    .foregroundStyle(tint)
                Spacer()
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
                .font(.geist(13, .semibold))
                .foregroundStyle(Color.inkMuted)
                .padding(.leading, 4)

            VStack(alignment: .leading, spacing: 4) {
                if isLifetime {
                    // Nothing to sell, and nothing to cancel — but say so plainly. An
                    // empty section reads as broken; a clear statement reads as meant.
                    Text("You have Lifetime access")
                        .font(.geist(16, .bold))
                        .foregroundStyle(Color.ink)
                    Text("One payment, never expires. Nothing to manage.")
                        .font(.geist(14))
                        .foregroundStyle(Color.inkMuted)
                } else if hasAccess {
                    Text(isCanceled ? "Your membership is ending"
                         : isTrialing ? "Your trial is active"
                         : "You're on the Yearly plan")
                        .font(.geist(16, .bold))
                        .foregroundStyle(Color.ink)

                    // During a trial, say when the charge lands. A free trial that
                    // quietly becomes a bill is exactly what guideline 3.1.2 exists to
                    // stop, and the date is the whole point of the disclosure.
                    Text(subtitle)
                        .font(.geist(14))
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
                    .font(.geist(14, .semibold))
                    .foregroundStyle(Color.farmGreen)
                    .padding(.top, 10)
                } else {
                    Text("You don't have a membership yet")
                        .font(.geist(16, .bold))
                        .foregroundStyle(Color.ink)
                    Text("Unlock full details for every farm.")
                        .font(.geist(14))
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