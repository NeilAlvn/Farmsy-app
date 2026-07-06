import Foundation
import Supabase
import Observation

enum AuthError: LocalizedError {
    case invalidCredentials
    case emailTaken
    case throttled
    case server(String)

    var errorDescription: String? {
        switch self {
        case .invalidCredentials: "Invalid email or password."
        case .emailTaken: "An account with this email already exists."
        case .throttled: "Too many attempts. Please wait a few minutes and try again."
        case .server(let msg): msg
        }
    }
}

/// Auth + subscription state. Sign-in and sign-up go through the farmsy.app
/// API (same throttling and custom verification emails as the website); the
/// returned tokens then hydrate the Supabase client session on-device.
@MainActor
@Observable
final class SessionStore {
    private(set) var session: Session?
    private(set) var profile: Profile?
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

    func bootstrap() async {
        session = try? await supabase.auth.session
        if session != nil { await refreshProfile() }
        isBootstrapped = true

        Task { [weak self] in
            for await state in supabase.auth.authStateChanges {
                guard let self else { return }
                self.session = state.session
                if state.session == nil {
                    self.profile = nil
                } else if [.signedIn, .tokenRefreshed, .initialSession].contains(state.event) {
                    await self.refreshProfile()
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
            profile = try decoder.decode(Profile.self, from: data)
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
        case 401: throw AuthError.invalidCredentials
        case 429: throw AuthError.throttled
        default: throw AuthError.server(serverMessage(from: data))
        }
    }

    func signUp(email: String, password: String, refCode: String?) async throws {
        var body = ["email": email, "password": password]
        if let refCode, !refCode.isEmpty { body["refCode"] = refCode }

        let (data, status) = try await postJSON(path: "auth/signup", body: body)
        switch status {
        case 200, 201:
            try await logIn(email: email, password: password)
        case 409: throw AuthError.emailTaken
        case 429: throw AuthError.throttled
        default: throw AuthError.server(serverMessage(from: data))
        }
    }

    func signOut() async {
        try? await supabase.auth.signOut()
        session = nil
        profile = nil
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
