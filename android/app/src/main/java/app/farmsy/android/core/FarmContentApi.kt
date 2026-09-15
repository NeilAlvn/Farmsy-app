package app.farmsy.android.core

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/// Reviews, posts and the composer — read/written straight from Supabase via RLS
/// (the app can't call the web's Server Actions). Mirrors iOS FarmContentAPI.
/// Contracts (confirmed with Aviah): `reviews` is public-read, insert/update your
/// own row (unique per user+farm); `farm_pings` is public-read, any signed-in
/// member can post to any farm, photos go to the `ping-images` bucket under the
/// uploader's user id.
object FarmContentApi {

    // MARK: - Reviews

    suspend fun reviews(osmId: String): List<Review> = runCatching {
        supabase.from("reviews")
            .select(Columns.raw("id, user_id, reviewer_name, rating, body, created_at")) {
                filter { eq("farm_osm_id", osmId) }
                order("created_at", Order.DESCENDING)
            }
            .decodeList<Review>()
    }.getOrDefault(emptyList())

    /// Insert or update the caller's own review (unique on user_id+farm_osm_id).
    suspend fun submitReview(
        osmId: String, userId: String, reviewerName: String, rating: Int, body: String?,
    ) {
        supabase.from("reviews").upsert(
            buildJsonObject {
                put("farm_osm_id", JsonPrimitive(osmId))
                put("user_id", JsonPrimitive(userId))
                put("reviewer_name", JsonPrimitive(reviewerName))
                put("rating", JsonPrimitive(rating))
                put("body", body?.let { JsonPrimitive(it) } ?: JsonPrimitive(null as String?))
            },
        ) { onConflict = "user_id,farm_osm_id" }
    }

    // MARK: - Posts feed (all farms) — the What's New / Discover cross-farm feed

    /// The latest visible posts across every farm — status=visible, created_at desc,
    /// limit 30, no farm filter. Backs the What's New sheet (S6) and the Discover
    /// feed's posts section.
    ///
    /// PORT NOTE (structural deviation): iOS calls this query **inline inside the
    /// views** (WhatsNewSheet.loadPings / DiscoverFeedView.loadPings), not through a
    /// shared API layer. Putting it in FarmContentApi centralises the one query both
    /// screens use — a deliberate structural improvement over the iOS duplication.
    /// `sinceIso` keeps only posts created at or after that ISO-8601 instant
    /// (Discover's "last thirty days").
    suspend fun feedPosts(limit: Long = 30, sinceIso: String? = null): List<Ping> = runCatching {
        supabase.from("farm_pings")
            .select(
                Columns.raw(
                    "id, farm_osm_id, author_name, body, like_count, created_at, farm_ping_images(url, sort_order)"
                )
            ) {
                filter { eq("status", "visible"); sinceIso?.let { gte("created_at", it) } }
                order("created_at", Order.DESCENDING)
                limit(limit)
            }
            .decodeList<Ping>()
    }.getOrDefault(emptyList())

    // MARK: - Posts for one farm

    suspend fun posts(osmId: String): List<Ping> = runCatching {
        supabase.from("farm_pings")
            .select(
                Columns.raw(
                    "id, farm_osm_id, author_name, body, like_count, created_at, farm_ping_images(url, sort_order)"
                )
            ) {
                filter { eq("farm_osm_id", osmId); eq("status", "visible") }
                order("created_at", Order.DESCENDING)
            }
            .decodeList<Ping>()
    }.getOrDefault(emptyList())

    // MARK: - Likes & reports

    @Serializable
    private data class LikeRow(@kotlinx.serialization.SerialName("ping_id") val pingId: String)

    /// The ping ids the viewer has liked.
    suspend fun likedPingIds(userId: String): Set<String> = runCatching {
        supabase.from("farm_ping_likes")
            .select(Columns.raw("ping_id")) { filter { eq("user_id", userId) } }
            .decodeList<LikeRow>()
            .map { it.pingId }
            .toSet()
    }.getOrDefault(emptySet())

    suspend fun toggleLike(pingId: String, userId: String, currentlyLiked: Boolean) {
        runCatching {
            if (currentlyLiked) {
                supabase.from("farm_ping_likes").delete {
                    filter { eq("ping_id", pingId); eq("user_id", userId) }
                }
            } else {
                supabase.from("farm_ping_likes").insert(
                    buildJsonObject {
                        put("ping_id", JsonPrimitive(pingId)); put("user_id", JsonPrimitive(userId))
                    }
                )
            }
        }
    }

    /// Report a post — insert only, one per person per ping (unique constraint).
    suspend fun reportPing(pingId: String, userId: String) {
        runCatching {
            supabase.from("farm_ping_reports").insert(
                buildJsonObject {
                    put("ping_id", JsonPrimitive(pingId)); put("user_id", JsonPrimitive(userId))
                }
            )
        }
    }

    // MARK: - Composer

    @Serializable
    private data class InsertedId(val id: String)

    /// Post to a farm: upload the photos to storage first (bucket `ping-images`,
    /// path `${userId}/${ts}-${i}.jpg` — the policy requires the user id as the
    /// first segment), then insert the ping and its image rows. Mirrors iOS.
    ///
    /// PORT NOTE: iOS uses the Supabase Storage SDK; the Android build does not
    /// depend on storage-kt, so the upload goes through the Storage REST endpoint
    /// (`POST /storage/v1/object/ping-images/<path>`) with the caller's access
    /// token via the shared ktor client — same bucket, path, and public-URL shape.
    suspend fun createPost(
        osmId: String, userId: String, authorName: String, body: String, photos: List<ByteArray>,
    ) {
        val urls = mutableListOf<String>()
        val ts = System.currentTimeMillis() / 1000
        val token = supabase.auth.currentAccessTokenOrNull()
        for ((i, data) in photos.take(3).withIndex()) {
            val path = "$userId/$ts-$i.jpg"
            val ok = runCatching {
                val resp = httpClient.post(
                    "${Backend.SUPABASE_URL}/storage/v1/object/ping-images/$path"
                ) {
                    header("apikey", Backend.SUPABASE_ANON_KEY)
                    token?.let { header(HttpHeaders.Authorization, "Bearer $it") }
                    contentType(ContentType.Image.JPEG)
                    setBody(data)
                }
                resp.status.isSuccess()
            }.getOrDefault(false)
            if (ok) {
                urls.add("${Backend.SUPABASE_URL}/storage/v1/object/public/ping-images/$path")
            }
        }

        // Posts don't expire — but expires_at is NOT NULL and RLS still compares
        // against it, so set it 100 years out.
        val farOut = OffsetDateTime.now(ZoneOffset.UTC).plusYears(100)
            .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

        val inserted = supabase.from("farm_pings").insert(
            buildJsonObject {
                put("farm_osm_id", JsonPrimitive(osmId))
                put("user_id", JsonPrimitive(userId))
                put("author_name", JsonPrimitive(authorName))
                put("body", JsonPrimitive(body))
                put("expires_at", JsonPrimitive(farOut))
            }
        ) { select(Columns.raw("id")) }.decodeList<InsertedId>()

        val pingId = inserted.firstOrNull()?.id ?: return
        if (urls.isEmpty()) return

        val rows = urls.mapIndexed { i, url ->
            buildJsonObject {
                put("ping_id", JsonPrimitive(pingId))
                put("url", JsonPrimitive(url))
                put("sort_order", JsonPrimitive(i))
            }
        }
        runCatching { supabase.from("farm_ping_images").insert(rows) }
    }
}
