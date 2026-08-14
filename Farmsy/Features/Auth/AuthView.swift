import SwiftUI

/// Free sign-up / log-in with Supabase email+password, routed through the
/// farmsy.app auth API (keeps the login throttle and the branded
/// verification email).
struct AuthView: View {
    @Environment(SessionStore.self) private var session
    @Environment(\.dismiss) private var dismiss

    @AppStorage("pendingRefCode") private var pendingRefCode = ""

    @State private var mode: Mode = .signUp
    /// Signup is two steps, like the web: credentials, then personal details +
    /// address. Every one of those fields is required by POST /api/auth/signup.
    @State private var step = 1
    @State private var email = ""
    @State private var password = ""
    @State private var confirm = ""
    @State private var firstName = ""
    @State private var lastName = ""
    @State private var dob: Date?
    @State private var street = ""
    @State private var city = ""
    @State private var postalCode = ""
    @State private var country = ""
    @State private var refCode = ""
    @State private var isWorking = false
    @State private var errorMessage: String?
    /// Set once the account exists. Signup no longer logs in — the account can't
    /// authenticate until the emailed link is clicked — so we park here.
    @State private var verifySentTo: String?

    enum Mode { case signUp, logIn }

    /// The API rejects under-16s, so the picker simply cannot offer a younger date.
    private var latestAllowedDOB: Date {
        Calendar.current.date(byAdding: .year, value: -16, to: .now) ?? .now
    }

    private var credentialsOK: Bool {
        guard email.contains("@"), password.count >= 8 else { return false }
        if mode == .signUp && confirm != password { return false }
        return true
    }

    private var detailsOK: Bool {
        // Apple 5.1.1(v): DOB, street, city, and postal code must not be
        // required. Only name + country are required; the rest are optional.
        ![firstName, lastName, country]
            .contains { $0.trimmingCharacters(in: .whitespaces).isEmpty }
    }

    private var canSubmit: Bool {
        if isWorking { return false }
        if mode == .logIn { return credentialsOK }
        return step == 1 ? credentialsOK : detailsOK
    }

    private static let isoDay: DateFormatter = {
        let f = DateFormatter()
        f.calendar = Calendar(identifier: .gregorian)
        f.locale = Locale(identifier: "en_US_POSIX")   // never localise an API date
        f.dateFormat = "yyyy-MM-dd"
        return f
    }()

    var body: some View {
        // Account created: no session exists yet, so show the "check your inbox"
        // state rather than a form that looks like it failed.
        if let verifySentTo {
            VerifyEmailView(email: verifySentTo) {
                self.verifySentTo = nil
                mode = .logIn
                step = 1
            }
        } else {
            form
        }
    }

    private var form: some View {
        ScrollView(showsIndicators: false) {
            VStack(spacing: 0) {
                HStack(spacing: 12) {
                    Image("FarmsyLogo")
                        .resizable()
                        .scaledToFit()
                        .frame(height: 54)
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
                    // Step 1 (and log in): credentials only. Staging the commitment
                    // is what keeps the drop-off down — nobody abandons at "email
                    // and password".
                    if mode == .logIn || step == 1 {
                        AuthField(label: String(localized: "Email"), placeholder: String(localized: "you@email.com"), text: $email)
                            .textContentType(.emailAddress)
                            .keyboardType(.emailAddress)
                            .textInputAutocapitalization(.never)
                            .autocorrectionDisabled()

                        AuthField(label: String(localized: "Password"), placeholder: String(localized: "At least 8 characters"),
                                  text: $password, isSecure: true)
                            .textContentType(mode == .signUp ? .newPassword : .password)

                        if mode == .signUp {
                            AuthField(label: String(localized: "Confirm password"), placeholder: String(localized: "Repeat password"),
                                      text: $confirm, isSecure: true)
                                .textContentType(.newPassword)
                        }
                    } else {
                        // Step 2: name + country are required; DOB and address
                        // are optional (Apple 5.1.1(v)).
                        HStack(spacing: 10) {
                            AuthField(label: String(localized: "First name"), placeholder: "", text: $firstName)
                                .textContentType(.givenName)
                            AuthField(label: String(localized: "Last name"), placeholder: "", text: $lastName)
                                .textContentType(.familyName)
                        }

                        // A wheel, not a text field: if provided the server wants a
                        // real ISO date and 16+, so typed input would only bounce.
                        DOBField(date: $dob, latestAllowed: latestAllowedDOB)

                        AuthField(label: String(localized: "Street address (optional)"), placeholder: "", text: $street)
                            .textContentType(.fullStreetAddress)
                        HStack(spacing: 10) {
                            AuthField(label: String(localized: "City (optional)"), placeholder: "", text: $city)
                                .textContentType(.addressCity)
                            AuthField(label: String(localized: "Postal code (optional)"), placeholder: "", text: $postalCode)
                                .textContentType(.postalCode)
                        }
                        AuthField(label: String(localized: "Country"), placeholder: "", text: $country)
                            .textContentType(.countryName)
                        AuthField(label: String(localized: "Referral code (optional)"), placeholder: "", text: $refCode)
                            .textInputAutocapitalization(.characters)
                            .autocorrectionDisabled()
                    }
                }

                if let errorMessage {
                    Text(errorMessage)
                        .font(.geist(14, .medium))
                        .foregroundStyle(Color.warnRed)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.top, 12)
                }

                Button {
                    submit()
                } label: {
                    if isWorking {
                        ProgressView().tint(.white)
                    } else {
                        Text(mode == .logIn ? "Log in"
                             : (step == 1 ? "Continue" : "Create account"))
                    }
                }
                .buttonStyle(PrimaryButtonStyle())
                .disabled(!canSubmit)
                .opacity(canSubmit ? 1 : 0.55)
                .padding(.top, 22)

                if mode == .signUp && step == 2 {
                    Button("Back") {
                        Haptics.tap()
                        withAnimation(.spring(duration: 0.3)) { step = 1; errorMessage = nil }
                    }
                    .font(.geist(15))
                    .foregroundStyle(Color.inkMuted)
                    .padding(.top, 10)
                }

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
        // Presented as a sheet over guest browsing — offer a way out and
        // step aside on its own once the user is signed in.
        .overlay(alignment: .topTrailing) {
            Button {
                Haptics.tap()
                dismiss()
            } label: {
                Image(systemName: "xmark")
                    .font(.system(size: 13, weight: .bold))
                    .foregroundStyle(Color.inkMuted)
                    .frame(width: 32, height: 32)
                    .background(.white.opacity(0.9), in: Circle())
            }
            .buttonStyle(.plain)
            .padding(.top, 14)
            .padding(.trailing, 16)
            .accessibilityLabel("Close")
        }
        .onChange(of: session.isAuthenticated) { _, authed in
            if authed { dismiss() }
        }
    }

    private func submit() {
        guard canSubmit else { return }
        // Step 1 of signup only advances the form — nothing is sent yet.
        if mode == .signUp && step == 1 {
            Haptics.tap()
            withAnimation(.spring(duration: 0.3)) { step = 2; errorMessage = nil }
            return
        }

        isWorking = true
        errorMessage = nil
        Task {
            defer { isWorking = false }
            do {
                if mode == .signUp {
                    let trimmed = email.trimmingCharacters(in: .whitespaces)
                    let typed = refCode.trimmingCharacters(in: .whitespaces)
                    try await session.signUp(
                        SignUpDetails(
                            email: trimmed,
                            password: password,
                            firstName: firstName.trimmingCharacters(in: .whitespaces),
                            lastName: lastName.trimmingCharacters(in: .whitespaces),
                            dob: dob.map { Self.isoDay.string(from: $0) } ?? "",
                            streetAddress: street.trimmingCharacters(in: .whitespaces),
                            city: city.trimmingCharacters(in: .whitespaces),
                            postalCode: postalCode.trimmingCharacters(in: .whitespaces),
                            country: country.trimmingCharacters(in: .whitespaces),
                            // Whatever they typed wins over a stale captured code.
                            refCode: typed.isEmpty ? (pendingRefCode.isEmpty ? nil : pendingRefCode) : typed
                        )
                    )
                    // Burn the code only once the server has accepted it, so a
                    // failed signup doesn't cost the referrer their credit.
                    pendingRefCode = ""
                    verifySentTo = trimmed
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
                    ?? String(localized: "Something went wrong. Please try again.")
                // A field-level rejection belongs back on the field-level step.
                if case .some(.missingFields) = error as? AuthError { step = 2 }
                if case .some(.invalidDOB) = error as? AuthError { step = 2 }
            }
        }
    }
}

/// Date of birth, via the wheel picker.
///
/// The API wants a real ISO `yyyy-MM-dd` and rejects under-16s, so free text would
/// only bounce back as `invalid_dob` after a round trip. Capping the range at
/// today-minus-16 enforces the age rule before the user can even submit.
struct DOBField: View {
    @Binding var date: Date?
    let latestAllowed: Date

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text("Date of birth (optional)")
                .font(.geist(14, .semibold))
                .foregroundStyle(Color.farmGreen)
            DatePicker(
                "",
                selection: Binding(
                    get: { date ?? latestAllowed },
                    set: { date = $0 }
                ),
                in: ...latestAllowed,
                displayedComponents: .date
            )
            .datePickerStyle(.compact)
            .labelsHidden()
            .padding(.horizontal, 14)
            .padding(.vertical, 10)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(.white, in: RoundedRectangle(cornerRadius: 16))
        }
    }
}

/// Shown after a successful signup. There is no session yet — the account can't
/// authenticate until the emailed link is clicked — so parking the user here is
/// the honest state. Dropping them back onto the form would read as a failure.
struct VerifyEmailView: View {
    let email: String
    let onDone: () -> Void

    var body: some View {
        VStack(spacing: 0) {
            Text("📬").font(.system(size: 46))
            Text("Check your inbox")
                .font(.display(26))
                .foregroundStyle(Color.ink)
                .padding(.top, 14)
            Text("We've sent a verification link to \(email). Click it to activate your account, then log in.")
                .font(.geist(15))
                .foregroundStyle(Color.inkMuted)
                .multilineTextAlignment(.center)
                .padding(.top, 8)
            Button("Log in", action: onDone)
                .buttonStyle(PrimaryButtonStyle())
                .padding(.top, 24)
        }
        .padding(.horizontal, 24)
        .padding(.vertical, 40)
        .frame(maxWidth: .infinity)
        .background(Color.cream.ignoresSafeArea())
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
                RoundedRectangle(cornerRadius: 16, style: .continuous)
                    .fill(.white.opacity(0.6))
                    .stroke(Color.inkMuted.opacity(0.25), lineWidth: 1)
            )
        }
    }
}

#Preview {
    AuthView().environment(SessionStore())
}
