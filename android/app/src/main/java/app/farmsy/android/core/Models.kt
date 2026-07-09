package app.farmsy.android.core

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
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

    companion object {
        fun fromRaw(value: String): FarmCategory? =
            entries.firstOrNull { it.raw == value.lowercase() }

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
    val lat: Double,
    val lng: Double,
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
) {
    val categories: List<FarmCategory>
        get() {
            val cats = farmType.mapNotNull { FarmCategory.fromRaw(it) }
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
) {
    /// Same rule as the web's isPaid(): active/trialing always pass, and a
    /// canceled plan keeps access until the already-paid period runs out.
    val hasFullAccess: Boolean
        get() = when (subscriptionStatus) {
            "active", "trialing" -> true
            "canceled" -> parsePostgresDate(subscriptionEndDate)?.isAfter(OffsetDateTime.now()) ?: false
            else -> false
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
