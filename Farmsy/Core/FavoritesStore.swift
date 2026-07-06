import Foundation
import Supabase
import Observation

/// Saved farms — same `favorites(user_id, farm_osm_id)` table the website
/// uses, so hearts stay in sync across web and app.
@MainActor
@Observable
final class FavoritesStore {
    private(set) var osmIds: Set<String> = []

    private struct Row: Decodable {
        let farmOsmId: String
        enum CodingKeys: String, CodingKey { case farmOsmId = "farm_osm_id" }
    }

    func load(userId: UUID) async {
        do {
            let rows: [Row] = try await supabase
                .from("favorites")
                .select("farm_osm_id")
                .eq("user_id", value: userId)
                .execute()
                .value
            osmIds = Set(rows.map(\.farmOsmId))
        } catch {
            // Non-fatal: leave the current set untouched.
        }
    }

    func isSaved(_ osmId: String) -> Bool { osmIds.contains(osmId) }

    func toggle(_ osmId: String, userId: UUID) async {
        if osmIds.contains(osmId) {
            osmIds.remove(osmId)
            do {
                try await supabase
                    .from("favorites")
                    .delete()
                    .eq("user_id", value: userId)
                    .eq("farm_osm_id", value: osmId)
                    .execute()
            } catch {
                osmIds.insert(osmId) // roll back the optimistic update
            }
        } else {
            osmIds.insert(osmId)
            do {
                try await supabase
                    .from("favorites")
                    .insert(["user_id": userId.uuidString, "farm_osm_id": osmId])
                    .execute()
            } catch {
                osmIds.remove(osmId)
            }
        }
    }

    func clear() { osmIds = [] }
}
