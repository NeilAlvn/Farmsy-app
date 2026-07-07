import SwiftUI

struct SettingsSheet: View {
    @Environment(SessionStore.self) private var session
    @Environment(\.dismiss) private var dismiss
    @Environment(\.requestAuth) private var requestAuth

    @State private var showSignOutConfirm = false
    @State private var showDeleteInfo = false

    private var subscriptionBadge: (String, Color) {
        switch session.profile?.subscriptionStatus {
        case "active":   (String(localized: "Member"), .farmGreen)
        case "trialing": (String(localized: "Trial"), .farmGreen)
        case "canceled": (String(localized: "Canceled"), .inkMuted)
        default:         (String(localized: "Free"), .inkMuted)
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
                                Text(session.profile?.subscriptionPlan.map { String(localized: "\($0.capitalized) plan") } ?? String(localized: "Farmsy account"))
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

                    VStack(spacing: 0) {
                        SettingsRow(icon: "bell.fill", tintBg: 0xF5B301, label: "Notifications") {
                            if let url = URL(string: UIApplication.openSettingsURLString) {
                                UIApplication.shared.open(url)
                            }
                        }
                        Divider().padding(.leading, 62)
                        SettingsRow(icon: "envelope.fill", tintBg: 0x38BDF8, label: "Contact us") {
                            if let url = URL(string: "mailto:hello@farmsy.app") {
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
        .alert("Delete account", isPresented: $showDeleteInfo) {
            // Apple requires deletion to be reachable from inside the app —
            // hand off to the web account page where it's handled.
            Button("Open my account page") {
                if let url = URL(string: "https://www.farmsy.app/profile") {
                    UIApplication.shared.open(url)
                }
            }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("Deleting your account removes your profile, favourites and subscription data. Continue on your Farmsy account page, or write to hello@farmsy.app.")
        }
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
