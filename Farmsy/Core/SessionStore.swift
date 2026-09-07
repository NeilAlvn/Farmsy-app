import Foundation
import Supabase
import Observation

enum AuthError: LocalizedError {
    case invalidCredentials
    case emailTaken
    case throttled
    /// The account exists and the password is correct — the emailed link just
    /// hasn't been clicked yet. Never surface this as a credentials failure, or
    /// we send people off to reset a password that works perfectly well.
    case emailNotVerified
    case missingFields([String])
    case invalidDOB
    case server(String)

    var errorDescription: String? {
        switch self {
        case .invalidCredentials: String(localized: "Invalid email or password.")
        case .emailTaken: String(localized: "An account with this email already exists.")
        case .throttled: String(localized: "Too many attempts. Please wait a few minutes and try again.")
        case .emailNotVerified:
            String(localized: "Please verify your email first — check your inbox for the link.")
        case .missingFields: String(localized: "Please fill in all fields.")
        case .invalidDOB:
            String(localized: "Enter a valid date of birth. You must be at least 16.")
        case .server(let msg): msg
        }
    }
}

/// Everything `POST /api/auth/signup` requires. Mirrors the web's two-step form:
/// credentials, then personal details + address. `refCode` is the only optional.
struct SignUpDetails {
    var email: String
    var password: String
    var firstName: String
    var lastName: String
    var dob: String            // ISO yyyy-MM-dd, a real date, 16+
    var streetAddress: String
    var city: String
    var postalCode: String
    var country: String
    var refCode: String?
}

/// Auth + subscription state. Sign-in and sign-up go through the farmsy.app
/// API (same throttling and custom verification emails as the website); the
/// returned tokens then hydrate the Supabase client session on-device.
@MainActor
@Observable
final class SessionStore {
    private(set) var session: Session?
    private(set) var profile: Profile?
    /// Fires whenever a fresh profile is decoded from /profile/status.
    /// PreferencesSync installs it to apply the server's preferences.
    @ObservationIgnored var onProfileLoaded: ((Profile) -> Void)?
    private(set) var isBootstrapped = false

    /// Debug-only: lets the screenshot UI tour walk the signed-in screens
    /// without a real account. Never true in release builds.
    var isDemoSession = false

    var isAuthenticated: Bool {
        #if DEBUG
        if isDemoSession { return true }
        #endif
        return session != nil
    }
    var hasFullAccess: Bool { profile?.hasFullAccess ?? false }
    var email: String {
        #if DEBUG
        if isDemoSession { return "preview@farmsy.app" }
        #endif
        return session?.user.email ?? ""
    }

    /// Author name for posts/reviews, composed the way the web does: full name
    /// from the profile, else the email prefix, else "Someone".
    var displayName: String {
        let full = [profile?.firstName, profile?.lastName]
            .compactMap { $0?.trimmingCharacters(in: .whitespaces) }
            .filter { !$0.isEmpty }
            .joined(separator: " ")
        if !full.isEmpty { return full }
        let prefix = email.components(separatedBy: "@").first ?? ""
        return prefix.isEmpty ? "Someone" : prefix
    }

    func bootstrap() async {
        session = try? await supabase.auth.session
        if session != nil { await refreshProfile() }
        isBootstrapped = true

        Task { [weak self] in
            for await state in supabase.auth.authStateChanges {
                guard let self else { return }
                self.session = state.session
                if let session = state.session {
                    // RevenueCat must know the Supabase user id before any
                    // purchase, or its webhook can't find the profile to grant.
                    await PurchaseStore.identify(userId: session.user.id)
                    Observability.identify(userId: session.user.id.uuidString)
                    if [.signedIn, .tokenRefreshed, .initialSession].contains(state.event) {
                        await self.refreshProfile()
                    }
                } else {
                    self.profile = nil
                    await PurchaseStore.signOut()
                    Observability.reset()
                }
            }
        }
    }

    /// Subscription status comes from the farmsy.app API (service-role read on
    /// the server) rather than a direct `profiles` select — the table's RLS
    /// policies aren't a dependency of the app this way.
    func refreshProfile() async {
        // Ask the client for the *current* session so an expired access token
        // is refreshed first — a stale cached token would 401 and silently
        // leave us looking un-subscribed. Fall back to the cached token if the
        // refresh path is unavailable.
        let token = (try? await supabase.auth.session.accessToken) ?? session?.accessToken
        guard let token else { return }
        var request = URLRequest(url: Backend.webAPI.appending(path: "profile/status"))
        request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        do {
            let (data, response) = try await URLSession.shared.data(for: request)
            guard (response as? HTTPURLResponse)?.statusCode == 200 else { return }
            let decoder = JSONDecoder()
            decoder.dateDecodingStrategy = .custom { decoder in
                let raw = try decoder.singleValueContainer().decode(String.self)
                guard let date = Self.parsePostgresDate(raw) else {
                    throw DecodingError.dataCorrupted(.init(
                        codingPath: decoder.codingPath,
                        debugDescription: "Unrecognized date: \(raw)"
                    ))
                }
                return date
            }
            let loaded = try decoder.decode(Profile.self, from: data)
            profile = loaded
            onProfileLoaded?(loaded)
        } catch {
            // Keep the last known profile on transient failures.
        }
    }

    /// Postgres timestamptz strings vary in fractional-second precision
    /// (none, millis, or micros); normalize before ISO-8601 parsing.
    private static func parsePostgresDate(_ raw: String) -> Date? {
        let iso = ISO8601DateFormatter()
        iso.formatOptions = [.withInternetDateTime]
        let noFraction = raw.replacingOccurrences(
            of: #"\.\d+"#, with: "", options: .regularExpression
        )
        return iso.date(from: noFraction)
            ?? iso.date(from: noFraction + "Z")
    }

    func logIn(email: String, password: String) async throws {
        struct LoginResponse: Decodable {
            struct Tokens: Decodable {
                let accessToken: String
                let refreshToken: String
                enum CodingKeys: String, CodingKey {
                    case accessToken = "access_token", refreshToken = "refresh_token"
                }
            }
            let session: Tokens
        }

        let (data, status) = try await postJSON(
            path: "auth/login",
            body: ["email": email, "password": password]
        )
        switch status {
        case 200:
            let tokens = try JSONDecoder().decode(LoginResponse.self, from: data).session
            session = try await supabase.auth.setSession(
                accessToken: tokens.accessToken,
                refreshToken: tokens.refreshToken
            )
            await refreshProfile()
        // The email-verified gate lives in the API now. This is *not* a bad
        // password, and must not be reported as one.
        case 403:
            if errorCode(from: data) == "email_not_verified" {
                throw AuthError.emailNotVerified
            }
            throw AuthError.server(serverMessage(from: data))
        case 401: throw AuthError.invalidCredentials
        case 429: throw AuthError.throttled
        default: throw AuthError.server(serverMessage(from: data))
        }
    }

    /// Creates the account. Deliberately does **not** log in afterwards: signup
    /// returns 200 but the account cannot authenticate until the emailed link is
    /// clicked, so an auto-login would immediately 403 and read as "signup failed".
    /// The caller shows a "check your inbox" screen instead — same as the web.
    func signUp(_ details: SignUpDetails) async throws {
        var body = [
            "email": details.email,
            "password": details.password,
            "firstName": details.firstName,
            "lastName": details.lastName,
            "dob": details.dob,
            "streetAddress": details.streetAddress,
            "city": details.city,
            "postalCode": details.postalCode,
            "country": details.country,
        ]
        // Uppercased, matching the web's cookie contract.
        if let refCode = details.refCode?.trimmingCharacters(in: .whitespaces), !refCode.isEmpty {
            body["refCode"] = refCode.uppercased()
        }

        let (data, status) = try await postJSON(path: "auth/signup", body: body)
        switch status {
        case 200, 201: return       // no session yet; caller shows "verify your email"
        case 409: throw AuthError.emailTaken
        case 429: throw AuthError.throttled
        case 400:
            switch errorCode(from: data) {
            case "missing_fields": throw AuthError.missingFields(missingFields(from: data))
            case "invalid_dob": throw AuthError.invalidDOB
            case "missing_credentials": throw AuthError.invalidCredentials
            default: throw AuthError.server(serverMessage(from: data))
            }
        default: throw AuthError.server(serverMessage(from: data))
        }
    }

    /// Branch on `code`, never the human-readable message.
    private func errorCode(from data: Data) -> String? {
        struct Body: Decodable { let code: String? }
        return try? JSONDecoder().decode(Body.self, from: data).code
    }

    private func missingFields(from data: Data) -> [String] {
        struct Body: Decodable { let fields: [String]? }
        return (try? JSONDecoder().decode(Body.self, from: data).fields) as? [String] ?? []
    }

    func signOut() async {
        try? await supabase.auth.signOut()
        session = nil
        profile = nil
    }

    struct DeleteResult {
        let ok: Bool
        let storeSubscriptionReminder: Bool
        var subscriptionSource: String? = nil
    }

    /// In-app account deletion — required by App Review guideline 5.1.1(v) for any
    /// app that supports account creation. The server actually erases the account;
    /// we only drop the local session once it confirms. The server can't cancel a
    /// store subscription (the stores own those contracts), so it returns
    /// `storeSubscriptionReminder` when the person still has a live sub to cancel
    /// themselves, and the UI surfaces that on the way out.
    ///
    /// `subscriptionSource` names the rail that actually charged them, which isn't
    /// always this platform: someone can subscribe on Android, install the iOS app,
    /// and delete from there — telling them to cancel in the App Store would send
    /// them somewhere with nothing to cancel while Play kept billing.
    func deleteAccount() async -> DeleteResult {
        let token = (try? await supabase.auth.session.accessToken) ?? session?.accessToken
        guard let token else { return DeleteResult(ok: false, storeSubscriptionReminder: false) }
        var request = URLRequest(url: Backend.webAPI.appending(path: "account/delete"))
        request.httpMethod = "POST"
        request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        struct DeleteBody: Decodable {
            let ok: Bool?
            let storeSubscriptionReminder: Bool?
            let subscriptionSource: String?
            enum CodingKeys: String, CodingKey {
                case ok, storeSubscriptionReminder
                case subscriptionSource = "subscription_source"
            }
        }
        do {
            let (data, response) = try await URLSession.shared.data(for: request)
            guard let code = (response as? HTTPURLResponse)?.statusCode, (200...299).contains(code) else {
                return DeleteResult(ok: false, storeSubscriptionReminder: false)
            }
            let body = try? JSONDecoder().decode(DeleteBody.self, from: data)
            await signOut()
            return DeleteResult(
                ok: true,
                storeSubscriptionReminder: body?.storeSubscriptionReminder ?? false,
                subscriptionSource: body?.subscriptionSource
            )
        } catch {
            return DeleteResult(ok: false, storeSubscriptionReminder: false)
        }
    }

    // MARK: - Helpers

    private func postJSON(path: String, body: [String: String]) async throws -> (Data, Int) {
        var request = URLRequest(url: Backend.webAPI.appending(path: path))
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try JSONEncoder().encode(body)
        let (data, response) = try await URLSession.shared.data(for: request)
        return (data, (response as? HTTPURLResponse)?.statusCode ?? 0)
    }

    private func serverMessage(from data: Data) -> String {
        struct ErrorBody: Decodable { let error: String? }
        return (try? JSONDecoder().decode(ErrorBody.self, from: data))?.error
            ?? "Something went wrong. Please try again."
    }
}
