import SwiftUI

/// Free sign-up / log-in with Supabase email+password, routed through the
/// farmsy.app auth API (keeps the login throttle and the branded
/// verification email).
struct AuthView: View {
    @Environment(SessionStore.self) private var session

    @AppStorage("pendingRefCode") private var pendingRefCode = ""

    @State private var mode: Mode = .signUp
    @State private var email = ""
    @State private var password = ""
    @State private var confirm = ""
    @State private var isWorking = false
    @State private var errorMessage: String?
    @State private var showVerifyNote = false

    enum Mode { case signUp, logIn }

    private var canSubmit: Bool {
        guard email.contains("@"), password.count >= 8 else { return false }
        if mode == .signUp && confirm != password { return false }
        return !isWorking
    }

    var body: some View {
        ScrollView(showsIndicators: false) {
            VStack(spacing: 0) {
                HStack(spacing: 12) {
                    FarmsyMark(size: 54)
                    VStack(alignment: .leading, spacing: 0) {
                        Text("Farmsy")
                            .font(.display(30))
                            .foregroundStyle(Color.ink)
                        Text("Local farms, fresh finds")
                            .font(.geist(13))
                            .foregroundStyle(Color.inkMuted)
                    }
                }
                .padding(.top, 34)
                .padding(.bottom, 30)

                Text(mode == .signUp ? "Create your free account" : "Welcome back")
                    .font(.display(28))
                    .foregroundStyle(Color.ink)
                    .padding(.bottom, 6)
                Text(mode == .signUp
                     ? "See every farm on the map in seconds."
                     : "Log in to pick up where you left off.")
                    .font(.geist(15))
                    .foregroundStyle(Color.inkMuted)
                    .padding(.bottom, 24)

                VStack(spacing: 14) {
                    AuthField(label: "Email", placeholder: "you@email.com", text: $email)
                        .textContentType(.emailAddress)
                        .keyboardType(.emailAddress)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()

                    AuthField(label: "Password", placeholder: "At least 8 characters",
                              text: $password, isSecure: true)
                        .textContentType(mode == .signUp ? .newPassword : .password)

                    if mode == .signUp {
                        AuthField(label: "Confirm password", placeholder: "Repeat password",
                                  text: $confirm, isSecure: true)
                            .textContentType(.newPassword)
                    }
                }

                if let errorMessage {
                    Text(errorMessage)
                        .font(.geist(14, .medium))
                        .foregroundStyle(Color.warnRed)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.top, 12)
                }

                if showVerifyNote {
                    Text("📬 We've sent a verification link to your email — you can keep exploring in the meantime.")
                        .font(.geist(14))
                        .foregroundStyle(Color.farmGreen)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.top, 12)
                }

                Button {
                    submit()
                } label: {
                    if isWorking {
                        ProgressView().tint(.white)
                    } else {
                        Text(mode == .signUp ? "Create account" : "Log in")
                    }
                }
                .buttonStyle(PrimaryButtonStyle())
                .disabled(!canSubmit)
                .opacity(canSubmit ? 1 : 0.55)
                .padding(.top, 22)

                HStack(spacing: 5) {
                    Text(mode == .signUp ? "Already have an account?" : "New to Farmsy?")
                        .foregroundStyle(Color.inkMuted)
                    Button(mode == .signUp ? "Log in" : "Create one") {
                        Haptics.tap()
                        withAnimation(.spring(duration: 0.35)) {
                            mode = mode == .signUp ? .logIn : .signUp
                            errorMessage = nil
                        }
                    }
                    .fontWeight(.semibold)
                    .foregroundStyle(Color.farmGreen)
                }
                .font(.geist(15))
                .padding(.top, 18)
                .padding(.bottom, 30)
            }
            .padding(.horizontal, 24)
        }
        .scrollBounceBehavior(.basedOnSize)
        .background(Color.cream.ignoresSafeArea())
    }

    private func submit() {
        guard canSubmit else { return }
        isWorking = true
        errorMessage = nil
        Task {
            defer { isWorking = false }
            do {
                if mode == .signUp {
                    try await session.signUp(
                        email: email.trimmingCharacters(in: .whitespaces),
                        password: password,
                        refCode: pendingRefCode.isEmpty ? nil : pendingRefCode
                    )
                    pendingRefCode = ""
                    showVerifyNote = true
                    Haptics.success()
                } else {
                    try await session.logIn(
                        email: email.trimmingCharacters(in: .whitespaces),
                        password: password
                    )
                    Haptics.success()
                }
            } catch {
                Haptics.warning()
                errorMessage = (error as? AuthError)?.errorDescription
                    ?? "Something went wrong. Please try again."
            }
        }
    }
}

struct AuthField: View {
    let label: String
    let placeholder: String
    @Binding var text: String
    var isSecure = false

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(label)
                .font(.geist(14, .semibold))
                .foregroundStyle(Color.farmGreen)
            Group {
                if isSecure {
                    SecureField(placeholder, text: $text)
                } else {
                    TextField(placeholder, text: $text)
                }
            }
            .font(.geist(17))
            .padding(.vertical, 15)
            .padding(.horizontal, 16)
            .background(
                RoundedRectangle(cornerRadius: 14, style: .continuous)
                    .fill(.white.opacity(0.6))
                    .stroke(Color.inkMuted.opacity(0.25), lineWidth: 1)
            )
        }
    }
}

#Preview {
    AuthView().environment(SessionStore())
}
