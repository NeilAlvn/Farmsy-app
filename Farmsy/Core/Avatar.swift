import Foundation
import Observation
import Supabase
import UIKit

/// The profile picture: read from the own profiles row, uploaded straight to
/// the public `avatars` bucket under the user's id (migration 070), the URL
/// written onto the profile. The same path ping photos take, so the storage
/// policy scopes writes to the caller's folder.
@MainActor
@Observable
final class AvatarStore {
    static let shared = AvatarStore()
    private(set) var url: URL?
    private(set) var busy = false
    private init() {}

    func load(userId: String) async {
        struct Row: Decodable { let avatar_url: String? }
        let row: Row? = try? await supabase.from("profiles").select("avatar_url").eq("id", value: userId).single().execute().value
        url = row?.avatar_url.flatMap(URL.init)
    }

    /// Resizes to 512 on the long side, JPEG at 0.8, uploads, and points the
    /// profile at the new file. Old files are left; a profile picture is a few
    /// tens of kilobytes and a sweep can come later.
    func upload(_ image: UIImage, userId: String) async -> Bool {
        busy = true
        defer { busy = false }
        let scale = min(1, 512 / max(image.size.width, image.size.height))
        let size = CGSize(width: image.size.width * scale, height: image.size.height * scale)
        let resized = UIGraphicsImageRenderer(size: size).image { _ in image.draw(in: CGRect(origin: .zero, size: size)) }
        guard let data = resized.jpegData(compressionQuality: 0.8) else { return false }
        let path = "\(userId)/avatar-\(Int(Date().timeIntervalSince1970)).jpg"
        do {
            try await supabase.storage.from("avatars")
                .upload(path, data: data, options: FileOptions(contentType: "image/jpeg", upsert: true))
            let publicURL = try supabase.storage.from("avatars").getPublicURL(path: path)
            try await supabase.from("profiles").update(["avatar_url": publicURL.absoluteString]).eq("id", value: userId).execute()
            url = publicURL
            return true
        } catch {
            return false
        }
    }
}
