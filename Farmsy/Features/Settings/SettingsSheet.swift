import SwiftUI

struct SettingsSheet: View {
    @Environment(SessionStore.self) private var session
    @Environment(\.dismiss) private var dismiss

    @State private var showSignOutConfirm = false
    @State private var showDeleteInfo = false

    private var subscriptionBadge: (String, Color) {
        switch session.profile?.subscriptionStatus {
        case "active":   ("Member", .farmGreen)
        case "trialing": ("Trial", .farmGreen)
        case "canceled": ("Canceled", .inkMuted)
        default:         ("Free", .inkMuted)
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
                    HStack(spacing: 12) {
                        FarmsyMark(size: 42)
                        VStack(alignment: .leading, spacing: 2) {
                            Text(session.email.isEmpty ? "Signed in" : session.email)
                                .font(.geist(15, .semibold))
                                .foregroundStyle(Color.ink)
                                .lineLimit(1)
                            Text(session.profile?.subscriptionPlan.map { $0.capitalized + " plan" } ?? "Farmsy account")
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

                    Text("Farmsy for iOS — prototype")
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
            Button("OK", role: .cancel) {}
        } message: {
            Text("Account deletion is handled from your Farmsy account on the web, or by writing to hello@farmsy.app.")
        }
    }
}

struct SettingsRow: View {
    let icon: String
    let tintBg: UInt32
    let label: String
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
