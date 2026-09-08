package app.farmsy.android.core

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import app.farmsy.android.R
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

// MARK: Categories — mirrors iOS Models.swift (same taxonomy + pin colors)

enum class FarmCategory(@StringRes val labelRes: Int, val emoji: String, val color: Color) {
    PRODUCE(R.string.farm_produce, "🥬", Color(0xFF10B981)),
    DAIRY(R.string.dairy, "🥛", Color(0xFF38BDF8)),
    CHEESE(R.string.cheese, "🧀", Color(0xFFF97316)),
    EGGS(R.string.eggs, "🥚", Color(0xFFEAB308)),
    MEAT(R.string.meat, "🥩", Color(0xFFEF4444)),
    FISH(R.string.fish, "🐟", Color(0xFF2563EB)),
    HONEY(R.string.honey, "🍯", Color(0xFFD97706)),
    WINE(R.string.wine, "🍷", Color(0xFF7C3AED)),
    MARKETS(R.string.markets, "🧺", Color(0xFF92400E)),
    ORGANIC(R.string.organic, "🌱", Color(0xFF059669));

    val raw: String get() = name.lowercase()

    /// Hue (0-360) for the Google Maps marker, derived from the category color
    /// so map pins carry the same coding as the rest of the app.
    val markerHue: Float
        get() {
            val hsv = FloatArray(3)
            android.graphics.Color.colorToHSV(color.toArgb(), hsv)
            return hsv[0]
        }

    companion object {
        fun fromRaw(value: String): FarmCategory? =
            entries.firstOrNull { it.raw == value.lowercase() }

        /// farm_type values that aren't one of the ten canonical cases but map
        /// onto one — OSM imports carry a few (beef, poultry) that would otherwise
        /// render as nothing. Client-side safety net (mirrors iOS FarmCategory.valueAlias).
        private val valueAlias: Map<String, FarmCategory> = mapOf(
            "beef" to MEAT, "poultry" to MEAT, "chicken" to MEAT, "pork" to MEAT,
            "vegetables" to PRODUCE, "fruit" to PRODUCE, "vegetable" to PRODUCE,
            "wine_cellar" to WINE, "winery" to WINE, "vineyard" to WINE,
            "milk" to DAIRY, "market" to MARKETS,
        )

        /// Resolve a raw farm_type string: direct case, then alias. Null for
        /// genuinely unknown values so they drop rather than render blank.
        fun from(value: String): FarmCategory? {
            val key = value.lowercase().trim()
            return fromRaw(key) ?: valueAlias[key]
        }

        /// Same OSM-tag fallback mapping the web map applies when farm_type is empty.
        val tagToCategory: Map<String, FarmCategory> = mapOf(
            "shop=farm" to PRODUCE, "shop=dairy" to DAIRY, "shop=cheese" to CHEESE,
            "craft=beekeeper" to HONEY, "shop=honey" to HONEY, "vending=eggs" to EGGS,
            "vending=milk" to DAIRY, "tourism=wine_cellar" to WINE, "craft=winery" to WINE,
            "amenity=winery" to WINE, "landuse=vineyard" to WINE, "shop=farm (wine)" to WINE,
            "shop=wine" to WINE, "amenity=marketplace" to MARKETS, "craft=cheesemaker" to CHEESE,
            "shop=butcher (farm)" to MEAT, "shop=bakery (farm)" to PRODUCE, "craft=butcher" to MEAT,
            "shop=butcher (direct_sale)" to MEAT, "shop=poultry" to MEAT, "vending=meat" to MEAT,
            "vending=sausage" to MEAT, "shop=fish" to FISH, "craft=fish_farm" to FISH,
        )
    }
}

/// farm_type arrives as an array, a `{a,b}` postgres literal, a plain string,
/// or null depending on the source row — normalize like the web (and iOS).
object FarmTypeSerializer : KSerializer<List<String>> {
    override val descriptor = ListSerializer(String.serializer()).descriptor

    override fun deserialize(decoder: Decoder): List<String> {
        val input = decoder as? JsonDecoder ?: return emptyList()
        return when (val el = input.decodeJsonElement()) {
            is JsonArray -> el.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.lowercase() }
            is JsonPrimitive -> {
                val s = el.contentOrNull ?: return emptyList()
                when {
                    s.isEmpty() -> emptyList()
                    s.startsWith("{") && s.endsWith("}") ->
                        s.substring(1, s.length - 1).split(',')
                            .map { it.trim(' ', '"').lowercase() }
                            .filter { it.isNotEmpty() }
                    else -> listOf(s.lowercase())
                }
            }
            else -> emptyList()
        }
    }

    override fun serialize(encoder: Encoder, value: List<String>) =
        ListSerializer(String.serializer()).serialize(encoder, value)
}

/// organic column types vary — accept bool or string forms ("yes"/"only"/...).
object FlexibleBoolSerializer : KSerializer<Boolean?> {
    override val descriptor = Boolean.serializer().descriptor

    override fun deserialize(decoder: Decoder): Boolean? {
        val input = decoder as? JsonDecoder ?: return null
        val el = input.decodeJsonElement() as? JsonPrimitive ?: return null
        el.contentOrNull?.let { s ->
            return when (s.lowercase()) {
                "true", "yes", "only", "organic" -> true
                "false", "no" -> false
                else -> null
            }
        }
        return null
    }

    @Suppress("UNCHECKED_CAST")
    override fun serialize(encoder: Encoder, value: Boolean?) =
        (Boolean.serializer() as KSerializer<Boolean?>).serialize(encoder, value)
}

// MARK: Farm pin (public shape returned by the get_farms_pins RPC)

@Serializable
data class FarmPin(
    val id: String,
    @SerialName("osm_id") val osmId: String,
    val name: String,
    override val lat: Double,
    override val lng: Double,
    val address: String? = null,
    val city: String? = null,
    @SerialName("postal_code") val postalCode: String? = null,
    val country: String? = null,
    val phone: String? = null,
    val website: String? = null,
    @SerialName("opening_hours") val openingHours: String? = null,
    val image: String? = null,
    @SerialName("primary_tag") val primaryTag: String? = null,
    @Serializable(with = FarmTypeSerializer::class)
    @SerialName("farm_type") val farmType: List<String> = emptyList(),
    @SerialName("avg_rating") val avgRating: Double? = null,
    @SerialName("review_count") val reviewCount: Int = 0,
    @SerialName("has_description") val hasDescription: Boolean = false,
    @SerialName("is_verified") val isVerified: Boolean = false,
) : Corridor.Point {
    val categories: List<FarmCategory>
        get() {
            // De-dupe: a farm tagged ["meat","beef"] resolves both to MEAT.
            val cats = farmType.mapNotNull { FarmCategory.from(it) }.distinct()
            if (cats.isNotEmpty()) return cats
            return primaryTag?.let { FarmCategory.tagToCategory[it] }?.let { listOf(it) } ?: emptyList()
        }

    val primaryCategory: FarmCategory get() = categories.firstOrNull() ?: FarmCategory.PRODUCE

    /// Meters between this pin and (lat, lng) — haversine, same as CLLocation.
    fun distanceMeters(fromLat: Double, fromLng: Double): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(lat - fromLat)
        val dLng = Math.toRadians(lng - fromLng)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(fromLat)) * cos(Math.toRadians(lat)) *
            sin(dLng / 2) * sin(dLng / 2)
        return r * 2 * atan2(sqrt(a), sqrt(1 - a))
    }
}

// MARK: Farm detail (subscriber-gated payload from GET /api/farm/[osmId])

@Serializable
data class FarmDetail(
    @SerialName("osm_id") val osmId: String,
    val phone: String? = null,
    val website: String? = null,
    val address: String? = null,
    @SerialName("postal_code") val postalCode: String? = null,
    val country: String? = null,
    @SerialName("opening_hours") val openingHours: String? = null,
    val image: String? = null,
    val description: String? = null,
    val email: String? = null,
    val facebook: String? = null,
    val instagram: String? = null,
    @Serializable(with = FlexibleBoolSerializer::class)
    val organic: Boolean? = null,
    val produce: String? = null,
    @SerialName("operator") val operatorName: String? = null,
    val images: List<String> = emptyList(),
)

// MARK: Profile (subscription state via /api/profile/status)

@Serializable
data class Profile(
    @SerialName("subscription_status") val subscriptionStatus: String? = null,
    @SerialName("subscription_plan") val subscriptionPlan: String? = null,
    @SerialName("subscription_end_date") val subscriptionEndDate: String? = null,
    /// Which rail took the money: "google", "apple" or "stripe". Billing lives with
    /// whoever charged the card — neither store lets us cancel on the user's behalf —
    /// so this decides where "manage your subscription" has to send them. Sending a
    /// web subscriber into Google Play to find nothing is the kind of dead end that
    /// reads as hiding the cancel button.
    @SerialName("subscription_source") val subscriptionSource: String? = null,
    /// 'admin', 'farmer' or 'user'. Admins (Neil, Luuk) and farmers who own a farm
    /// get full access with no subscription — the same `hasPaidAccess()` rule the
    /// server applies, mirrored here so the app doesn't need a round trip to know.
    @SerialName("role") val role: String? = null,
    /// The 13 who paid during the original paywalled era — also full access.
    @SerialName("founding_member") val foundingMember: Boolean? = null,
    /// Name for authoring posts/reviews (added to /api/profile/status by Aviah).
    @SerialName("first_name") val firstName: String? = null,
    @SerialName("last_name") val lastName: String? = null,
    /// The onboarding answers (P0-3), or null when never set. Applied to the map
    /// by PreferencesSync; see Preferences.kt for the conflict rule.
    val preferences: Preferences? = null,
) {
    /// Same order as the web's `hasPaidAccess()`: admin → farmer → founding member
    /// → active/trialing → a canceled plan still inside its paid period.
    val hasFullAccess: Boolean
        get() {
            if (role == "admin" || role == "farmer") return true
            if (foundingMember == true) return true
            return when (subscriptionStatus) {
                "active", "trialing" -> true
                "canceled" -> parsePostgresDate(subscriptionEndDate)?.isAfter(OffsetDateTime.now()) ?: false
                else -> false
            }
        }

    companion object {
        /// Postgres timestamptz strings vary in fractional-second precision;
        /// normalize before ISO-8601 parsing (mirrors iOS parsePostgresDate).
        fun parsePostgresDate(raw: String?): OffsetDateTime? {
            if (raw == null) return null
            val noFraction = raw.replace(Regex("\\.\\d+"), "")
            return runCatching {
                OffsetDateTime.parse(noFraction, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
            }.getOrNull() ?: runCatching {
                OffsetDateTime.parse(noFraction + "Z", DateTimeFormatter.ISO_OFFSET_DATE_TIME)
            }.getOrNull()
        }
    }
}

// MARK: Farm post ("What's new" ping, read straight from Supabase) — mirrors iOS Ping

/// A short post a farm published, with up to 3 photos. Read via RLS
/// (status = 'visible'); author_name is denormalised on the row so it survives
/// an account being deleted. like_count is kept by a trigger.
@Serializable
data class Ping(
    val id: String,
    @SerialName("farm_osm_id") val farmOsmId: String,
    @SerialName("author_name") val authorName: String,
    val body: String,
    @SerialName("like_count") val likeCount: Int = 0,
    @SerialName("created_at") val createdAt: String,
    @SerialName("farm_ping_images") private val imageRows: List<PingImageRow> = emptyList(),
) {
    @Serializable
    data class PingImageRow(val url: String, @SerialName("sort_order") val sortOrder: Int = 0)

    /// Photo URLs in sort order.
    val images: List<String> get() = imageRows.sortedBy { it.sortOrder }.map { it.url }

    /// Parsed timestamp, tolerant of the fractional seconds Postgres emits
    /// (reuses Profile.parsePostgresDate).
    val date: OffsetDateTime? get() = Profile.parsePostgresDate(createdAt)
}

// MARK: Review (public read from `reviews`; write is upsert-own) — mirrors iOS Review

/// A farm review. `reviewer_name` is denormalised on the row (use it, don't join).
/// `rating` is 1–5; `body` is optional. One review per user per farm (unique
/// constraint), so writing is an upsert of the caller's own row.
@Serializable
data class Review(
    val id: String,
    @SerialName("user_id") val userId: String? = null,
    @SerialName("reviewer_name") val reviewerName: String = "?",
    val rating: Int = 0,
    val body: String? = null,
    @SerialName("created_at") val createdAt: String = "",
)

// MARK: Farm teaser (public description opener) — mirrors iOS FarmTeaser

/// The first ~200 characters of a farm's description, cut on a word. `truncated`
/// is true when there is more behind the paywall, which drives the "View more".
@Serializable
data class FarmTeaser(val text: String, val truncated: Boolean = false)
