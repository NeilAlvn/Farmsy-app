import Foundation
import Supabase

/// Reviews, posts and the composer — read/written straight from Supabase via RLS
/// (the app can't call the web's Server Actions). Contracts confirmed with Aviah:
/// `reviews` is public-read, insert/update your own row (unique per user+farm);
/// `farm_pings` is public-read, any signed-in member can post to any farm, photos
/// go to the `ping-images` bucket under the uploader's user id.
enum FarmContentAPI {

    // MARK: - Reviews

    static func reviews(osmId: String) async -> [Review] {
        (try? await supabase
            .from("reviews")
            .select("id, user_id, reviewer_name, rating, body, created_at")
            .eq("farm_osm_id", value: osmId)
            .order("created_at", ascending: false)
            .execute()
            .value) ?? []
    }

    /// Insert or update the caller's own review (unique on user_id+farm_osm_id).
    static func submitReview(osmId: String, userId: String, reviewerName: String,
                             rating: Int, body: String?) async throws {
        struct Payload: Encodable {
            let farm_osm_id: String
            let user_id: String
            let reviewer_name: String
            let rating: Int
            let body: String?
        }
        try await supabase
            .from("reviews")
            .upsert(Payload(farm_osm_id: osmId, user_id: userId, reviewer_name: reviewerName,
                            rating: rating, body: body),
                    onConflict: "user_id,farm_osm_id")
            .execute()
    }

    // MARK: - Posts for one farm

    static func posts(osmId: String) async -> [Ping] {
        (try? await supabase
            .from("farm_pings")
            .select("id, farm_osm_id, author_name, body, like_count, created_at, farm_ping_images(url, sort_order)")
            .eq("farm_osm_id", value: osmId)
            .eq("status", value: "visible")
            .order("created_at", ascending: false)
            .execute()
            .value) ?? []
    }

    // MARK: - Composer

    /// Post to a farm: upload the photos to storage first (bucket `ping-images`,
    /// path `${user.id}/${ts}-${i}.jpg` — the policy requires the user id as the
    /// first segment), then insert the ping and its image rows.
    static func createPost(osmId: String, userId: String, authorName: String,
                           body: String, photos: [Data]) async throws {
        var urls: [String] = []
        let ts = Int(Date().timeIntervalSince1970)
        for (i, data) in photos.prefix(3).enumerated() {
            let path = "\(userId)/\(ts)-\(i).jpg"
            _ = try await supabase.storage
                .from("ping-images")
                .upload(path, data: data, options: FileOptions(contentType: "image/jpeg"))
            let url = try supabase.storage.from("ping-images").getPublicURL(path: path)
            urls.append(url.absoluteString)
        }

        struct PingPayload: Encodable {
            let farm_osm_id: String
            let user_id: String
            let author_name: String
            let body: String
            let expires_at: String
        }
        struct InsertedPing: Decodable { let id: String }
        // Posts don't expire — but expires_at is NOT NULL and RLS still compares
        // against it, so set it 100 years out.
        let farOut = ISO8601DateFormatter().string(
            from: Calendar.current.date(byAdding: .year, value: 100, to: Date()) ?? Date())
        let inserted: [InsertedPing] = try await supabase
            .from("farm_pings")
            .insert(PingPayload(farm_osm_id: osmId, user_id: userId, author_name: authorName,
                                body: body, expires_at: farOut))
            .select("id")
            .execute()
            .value
        guard let pingId = inserted.first?.id, !urls.isEmpty else { return }

        struct ImagePayload: Encodable {
            let ping_id: String
            let url: String
            let sort_order: Int
        }
        let rows = urls.enumerated().map { ImagePayload(ping_id: pingId, url: $1, sort_order: $0) }
        try await supabase.from("farm_ping_images").insert(rows).execute()
    }
}
