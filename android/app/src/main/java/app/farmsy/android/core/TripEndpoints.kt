package app.farmsy.android.core

import com.google.android.gms.maps.model.LatLng
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.ResolverStyle
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive

/// Where a saved drive starts and ends (R1), and the one place that shape is
/// enforced on its way to and from `trips`.
///
/// The bug this closes: a trip is a name and a list of farms, so save() never
/// carried the ends. setOrigin() writes the start to SharedPreferences and no
/// further. Save a drive from Utrecht to Groningen, reopen it tomorrow, and it
/// starts at the first farm instead of at your house. Nothing errors; the trip
/// just quietly means something else than what was saved.
///
/// The same rules live in `Farmsy/Core/TripEndpoints.swift`, in
/// `src/lib/tripEndpoints.ts` on the web, and again as CHECK constraints in
/// migration 058. Four places, one truth — the same shape as the T-1 access
/// matrix. If a rule changes here it changes there, in the same commit.
///
/// Three rules that are easy to get wrong:
///  - **A coordinate is a pair or it is nothing.** Half a point survives a write
///    and then fails at the far end of a route calculation, where the cause is
///    no longer visible.
///  - **Null is legal, everywhere.** Every trip saved before R1 has no ends, and
///    a trip that is only a list of farms stays a valid trip. Reading one back
///    must produce a usable plan, not an error.
///  - **The radius travels with the pair.** A corridor only means something at
///    the width it was chosen at: five kilometres around a city drive and five
///    around a cross-country one are different offers. Reopening at the default
///    width would quietly show a different set of farms than the one saved.

/// Somewhere someone chose in the place search. Separate from [LatLng] so it can
/// carry the label it was picked by — stored rather than re-geocoded, because
/// Photon will not always give back the same words for the same point and a
/// saved trip that renames its own start is unsettling.
data class TripPlace(
    val lat: Double,
    val lng: Double,
    val label: String,
) {
    val latLng: LatLng get() = LatLng(lat, lng)

    companion object {
        /// A pair that is really on the planet, or null. Rejects the half-pair,
        /// NaN and infinity — all three survive a write and fail elsewhere.
        fun make(lat: Double?, lng: Double?, label: String?): TripPlace? {
            if (lat == null || lng == null) return null
            if (!lat.isFinite() || !lng.isFinite()) return null
            if (lat < -90.0 || lat > 90.0 || lng < -180.0 || lng > 180.0) return null

            // A point with no label is still a point. Falling back to the
            // coordinates is uglier than a place name and better than dropping a
            // start someone chose.
            val text = label?.trim().orEmpty()
            return TripPlace(
                lat = lat,
                lng = lng,
                label = if (text.isEmpty()) String.format("%.4f, %.4f", lat, lng) else text,
            )
        }

        fun make(coord: LatLng?, label: String?): TripPlace? =
            if (coord == null) null else make(coord.latitude, coord.longitude, label)
    }
}

data class TripEndpoints(
    val origin: TripPlace? = null,
    val destination: TripPlace? = null,
    /// Corridor width in km. Null means "never chosen" — the panel suggests one.
    val radiusKm: Double? = null,
    /// The day it was planned for, `yyyy-mm-dd`. Null for anything saved before
    /// R7, and for a trip that is only a list of farms.
    ///
    /// This is what makes "drive this again" able to say what changed. Without
    /// a day to compare against there is nothing to compare: every farm would
    /// report today, and the answer would be a saved route against nothing.
    val date: String? = null,
    /// When it set off, minutes past midnight. Null means never chosen.
    val departMinutes: Int? = null,
) {
    companion object {
        val NONE = TripEndpoints()

        /// The widest corridor the clients offer. Anything past it is a bad
        /// write rather than a preference, so it is dropped instead of clamped:
        /// clamping stores a number nobody chose.
        const val MAX_RADIUS_KM = 100.0

        fun validRadius(km: Double?): Double? =
            if (km != null && km.isFinite() && km > 0.0 && km <= MAX_RADIUS_KM) km else null

        /// A trip is planned against Dutch and Belgian opening hours, so the day
        /// is decided in Amsterdam and not on whatever clock the phone is set
        /// to. A device in Los Angeles would otherwise turn a Saturday drive
        /// into a Friday one at the date boundary.
        val zone: ZoneId = ZoneId.of("Europe/Amsterdam")

        /// `yyyy-mm-dd`, or null. Validated against a calendar and not only a
        /// pattern: `2026-02-31` matches the shape and is not a day. A lenient
        /// parser rolls it into March, and the trip then compares opening hours
        /// against the wrong weekday and reports it with full confidence.
        fun validDate(text: String?): String? {
            val trimmed = text?.trim().orEmpty()
            if (trimmed.length != 10) return null
            return try {
                LocalDate.parse(trimmed, ISO_DAY).toString().takeIf { it == trimmed }
            } catch (_: DateTimeParseException) {
                null
            }
        }

        /// Minutes past midnight inside one day, or null. The bounds are the
        /// same ones migration 059 states as a CHECK — 1440 is tomorrow, not a
        /// time today.
        fun validDepartMinutes(minutes: Int?): Int? =
            if (minutes != null && minutes in 0..1439) minutes else null

        /// A stored day as a [LocalDate], or null if it is not a day.
        fun day(text: String?): LocalDate? =
            validDate(text)?.let { LocalDate.parse(it, ISO_DAY) }

        fun dayString(date: LocalDate): String = date.format(ISO_DAY)

        /// The Saturday coming, today included when today is Saturday.
        ///
        /// Offered by the picker and never stored on its own: a trip saved
        /// before R7 has no day, and inventing one would make it claim it was
        /// planned for a date nobody chose. Mirrors `nextSaturday()` in the
        /// web's MapSearchContext.
        fun nextSaturday(from: LocalDate = LocalDate.now(zone)): String {
            val ahead = (DayOfWeek.SATURDAY.value - from.dayOfWeek.value + 7) % 7
            return dayString(from.plusDays(ahead.toLong()))
        }

        private val ISO_DAY: DateTimeFormatter =
            DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT)
    }
}

/// The `trips` columns from migration 058, exactly as they are stored.
///
/// All nullable: a trip from before R1 decodes to [TripEndpoints.NONE], which is
/// a working plan and not a failure.
@Serializable
data class TripEndpointRow(
    @SerialName("origin_lat") val originLat: Double? = null,
    @SerialName("origin_lng") val originLng: Double? = null,
    @SerialName("origin_label") val originLabel: String? = null,
    @SerialName("destination_lat") val destinationLat: Double? = null,
    @SerialName("destination_lng") val destinationLng: Double? = null,
    @SerialName("destination_label") val destinationLabel: String? = null,
    @SerialName("radius_km") val radiusKm: Double? = null,
    @SerialName("trip_date") val tripDate: String? = null,
    @SerialName("depart_minutes") val departMinutes: Int? = null,
) {
    val endpoints: TripEndpoints
        get() = TripEndpoints(
            origin = TripPlace.make(originLat, originLng, originLabel),
            destination = TripPlace.make(destinationLat, destinationLng, destinationLabel),
            radiusKm = TripEndpoints.validRadius(radiusKm),
            date = TripEndpoints.validDate(tripDate),
            departMinutes = TripEndpoints.validDepartMinutes(departMinutes),
        )

    companion object {
        /// Always all nine, so clearing an end writes NULL rather than leaving
        /// yesterday's value behind. The failure mode of a partial update is a
        /// trip that keeps a start the user deliberately removed.
        ///
        /// A day or a departure that does not survive validation is dropped
        /// here rather than sent. The CHECK in migration 059 would reject the
        /// whole row, so one bad minute would cost the entire save: a trip with
        /// no day still saves, a trip that fails to save is gone.
        fun from(endpoints: TripEndpoints) = TripEndpointRow(
            originLat = endpoints.origin?.lat,
            originLng = endpoints.origin?.lng,
            originLabel = endpoints.origin?.label,
            destinationLat = endpoints.destination?.lat,
            destinationLng = endpoints.destination?.lng,
            destinationLabel = endpoints.destination?.label,
            radiusKm = TripEndpoints.validRadius(endpoints.radiusKm),
            tripDate = TripEndpoints.validDate(endpoints.date),
            departMinutes = TripEndpoints.validDepartMinutes(endpoints.departMinutes),
        )

        /// The columns to select when opening a trip. Here so a new column cannot
        /// be added to the row without the query that reads it.
        const val COLUMNS =
            "origin_lat, origin_lng, origin_label, destination_lat, destination_lng, destination_label, radius_km, trip_date, depart_minutes"
    }
}

/// The nine end columns, written onto a `trips` insert or update.
///
/// Always all nine, including the nulls. A partial update would leave
/// yesterday's start on a trip whose start the user deliberately removed, and
/// that is a worse bug than the one R1 fixes: it is wrong rather than missing.
fun JsonObjectBuilder.putEndpoints(row: TripEndpointRow) {
    fun num(v: Double?) = if (v == null) JsonNull else JsonPrimitive(v)
    fun int(v: Int?) = if (v == null) JsonNull else JsonPrimitive(v)
    fun str(v: String?) = if (v == null) JsonNull else JsonPrimitive(v)

    put("origin_lat", num(row.originLat))
    put("origin_lng", num(row.originLng))
    put("origin_label", str(row.originLabel))
    put("destination_lat", num(row.destinationLat))
    put("destination_lng", num(row.destinationLng))
    put("destination_label", str(row.destinationLabel))
    put("radius_km", num(row.radiusKm))
    put("trip_date", str(row.tripDate))
    put("depart_minutes", int(row.departMinutes))
}
