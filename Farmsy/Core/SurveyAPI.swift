import Foundation

/// The "why-farm" survey — questions come from the server (localized, cached an
/// hour) so a reworded question changes on every phone at once without an app
/// update. See `GET /api/survey/questions`. Three question kinds only: `one`
/// (single-select), `many` (multi-select, optionally capped by `max`), `text`
/// (free text, always `optional`).
enum SurveyKind: String, Decodable { case one, many, text }

struct SurveyOption: Decodable, Identifiable, Hashable {
    let id: String
    let label: String
}

struct SurveyQuestion: Decodable, Identifiable {
    let id: String
    let kind: SurveyKind
    /// Cap on a `many` question (question 4 is 2); nil = unlimited.
    let max: Int?
    /// The two open (`text`) questions are optional; the five choice ones are not.
    let optional: Bool
    let text: String
    let options: [SurveyOption]
}

struct SurveyDefinition: Decodable {
    let surveyKey: String
    let locale: String
    let questions: [SurveyQuestion]
}

/// `GET /api/survey/respond` — has this person answered? Bearer optional.
/// Signed-out/unknown returns all-false. The screen is shown ONLY when the person
/// has not answered and is not an admin (an answer from us is >0.5% of the data).
struct SurveyGate: Decodable {
    let known: Bool
    let answered: Bool
    let isAdmin: Bool
    var shouldShow: Bool { !answered && !isAdmin }
}

enum SurveyError: Error {
    case network
    /// A 400 from the submit route: `incomplete`, `unknown_person`, `is_admin`,
    /// `rate_limited`, `failed`.
    case refused(String)
}

enum SurveyAPI {

    /// One answer in a submit body: `{ options: [...] }` for one/many, `{ text: … }`
    /// for a text question.
    struct Answer: Encodable {
        var options: [String]?
        var text: String?
    }

    /// The `source` the app sends that the web does not — `ios` here (Android sends
    /// `android`), so Aviah can tell whether phone users answer differently.
    static let source = "ios"

    /// The survey's supported locales overlap the app's four (es exists server-side
    /// but the app ships no Spanish), so clamp to what the app actually renders.
    static func clampLocale(_ code: String) -> String {
        ["nl", "fr", "de"].contains(code) ? code : "en"
    }

    static func questions(locale: String) async throws -> SurveyDefinition {
        guard var comps = URLComponents(
            url: Backend.webAPI.appending(path: "survey/questions"),
            resolvingAgainstBaseURL: false
        ) else { throw SurveyError.network }
        comps.queryItems = [URLQueryItem(name: "locale", value: locale)]
        guard let url = comps.url else { throw SurveyError.network }

        let (data, response) = try await URLSession.shared.data(from: url)
        guard (response as? HTTPURLResponse)?.statusCode == 200 else { throw SurveyError.network }
        return try JSONDecoder().decode(SurveyDefinition.self, from: data)
    }

    /// Best-effort gate: any failure is treated as "unknown, not answered, not
    /// admin" so a network blip does not wrongly hide the survey from a real person.
    static func gate(accessToken: String?) async -> SurveyGate {
        var request = URLRequest(url: Backend.webAPI.appending(path: "survey/respond"))
        if let t = accessToken { request.setValue("Bearer \(t)", forHTTPHeaderField: "Authorization") }
        guard let (data, response) = try? await URLSession.shared.data(for: request),
              (response as? HTTPURLResponse)?.statusCode == 200,
              let gate = try? JSONDecoder().decode(SurveyGate.self, from: data)
        else { return SurveyGate(known: false, answered: false, isAdmin: false) }
        return gate
    }

    /// `POST /api/survey/respond`. name/email only travel when signed out (server
    /// has them otherwise). 200 → stored; 400 → `{ ok:false, error }` thrown as
    /// `.refused(error)`.
    static func submit(locale: String, answers: [String: Answer],
                       name: String?, email: String?, accessToken: String?) async throws {
        struct Body: Encodable {
            let locale: String
            let source: String
            let name: String?
            let email: String?
            let answers: [String: Answer]
        }
        let body = Body(
            locale: locale, source: source,
            name: (name?.isEmpty == false) ? name : nil,
            email: (email?.isEmpty == false) ? email : nil,
            answers: answers
        )
        var request = URLRequest(url: Backend.webAPI.appending(path: "survey/respond"))
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        if let t = accessToken { request.setValue("Bearer \(t)", forHTTPHeaderField: "Authorization") }
        request.httpBody = try JSONEncoder().encode(body)

        let (data, response) = try await URLSession.shared.data(for: request)
        let status = (response as? HTTPURLResponse)?.statusCode ?? 0
        if status == 200 { return }
        struct Refusal: Decodable { let error: String? }
        let reason = (try? JSONDecoder().decode(Refusal.self, from: data))?.error ?? "failed"
        throw SurveyError.refused(reason)
    }

    /// The post-answer feedback box → `POST /api/contact` with `topic: "feedback"`.
    /// The contact route requires name+email+message and has NO `subject` field, so
    /// the box's subject is folded into the message as its first line.
    static func sendFeedback(subject: String, message: String,
                             name: String, email: String, accessToken: String?) async throws {
        struct Body: Encodable { let name, email, topic, message, source: String }
        let combined = subject.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            ? message : "\(subject)\n\n\(message)"
        let body = Body(name: name, email: email, topic: "feedback", message: combined, source: source)

        var request = URLRequest(url: Backend.webAPI.appending(path: "contact"))
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        if let t = accessToken { request.setValue("Bearer \(t)", forHTTPHeaderField: "Authorization") }
        request.httpBody = try JSONEncoder().encode(body)

        let (_, response) = try await URLSession.shared.data(for: request)
        guard (response as? HTTPURLResponse)?.statusCode == 200 else { throw SurveyError.network }
    }
}
