import Foundation

enum SubmissionError: LocalizedError {
    case notSignedIn
    case membersOnly
    case server(String)

    var errorDescription: String? {
        switch self {
        case .notSignedIn: String(localized: "Your session expired — please sign in again.")
        case .membersOnly: String(localized: "A Farmsy membership is required to add a farm shop.")
        case .server(let msg): msg
        }
    }
}

/// Farm submissions and ownership claims go through the farmsy.app API —
/// the same service-role write path the website uses. The app never writes
/// these tables directly.
enum SubmissionAPI {

    struct FarmSubmission {
        var name = ""
        var city = ""
        var description = ""
        var farmType: [String] = []
        var address = ""
        var postalCode = ""
        var country = "Netherlands"
        var phone = ""
        var website = ""
        var email = ""
        var openingHours = ""
        var lat: Double?
        var lng: Double?
        var imageData: [Data] = []
    }

    static func submitFarm(_ s: FarmSubmission, accessToken: String) async throws {
        let boundary = "farmsy-\(UUID().uuidString)"
        var request = URLRequest(url: Backend.webAPI.appending(path: "farms/submit"))
        request.httpMethod = "POST"
        request.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
        request.setValue("multipart/form-data; boundary=\(boundary)",
                         forHTTPHeaderField: "Content-Type")

        var body = Data()
        func field(_ name: String, _ value: String) {
            guard !value.isEmpty else { return }
            body.append(Data("--\(boundary)\r\n".utf8))
            body.append(Data("Content-Disposition: form-data; name=\"\(name)\"\r\n\r\n".utf8))
            body.append(Data("\(value)\r\n".utf8))
        }
        field("name", s.name)
        field("city", s.city)
        field("description", s.description)
        field("farm_type", s.farmType.joined(separator: ","))
        field("address", s.address)
        field("postal_code", s.postalCode)
        field("country", s.country)
        field("phone", s.phone)
        field("website", s.website)
        field("email", s.email)
        field("opening_hours", s.openingHours)
        if let lat = s.lat { field("lat", String(lat)) }
        if let lng = s.lng { field("lng", String(lng)) }
        for (i, data) in s.imageData.prefix(5).enumerated() {
            body.append(Data("--\(boundary)\r\n".utf8))
            body.append(Data(
                "Content-Disposition: form-data; name=\"images\"; filename=\"photo-\(i).jpg\"\r\n".utf8
            ))
            body.append(Data("Content-Type: image/jpeg\r\n\r\n".utf8))
            body.append(data)
            body.append(Data("\r\n".utf8))
        }
        body.append(Data("--\(boundary)--\r\n".utf8))
        request.httpBody = body

        try await send(request)
    }

    struct FarmClaim: Encodable {
        let farmOsmId: String
        let farmName: String
        let fullName: String
        let email: String
        let phone: String
        let verificationMethod: String   // "email" | "kvk"
        let kvkNumber: String?
        let message: String?
    }

    static func submitClaim(_ claim: FarmClaim, accessToken: String) async throws {
        var request = URLRequest(url: Backend.webAPI.appending(path: "farms/claim"))
        request.httpMethod = "POST"
        request.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try JSONEncoder().encode(claim)
        try await send(request)
    }

    private static func send(_ request: URLRequest) async throws {
        let (data, response) = try await URLSession.shared.data(for: request)
        let status = (response as? HTTPURLResponse)?.statusCode ?? 0
        switch status {
        case 200, 201:
            return
        case 401:
            throw SubmissionError.notSignedIn
        case 403:
            throw SubmissionError.membersOnly
        default:
            struct ErrorBody: Decodable { let error: String? }
            let msg = (try? JSONDecoder().decode(ErrorBody.self, from: data))?.error
            throw SubmissionError.server(msg ?? "Something went wrong. Please try again.")
        }
    }
}
