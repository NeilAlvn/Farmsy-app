package app.farmsy.android.core

import com.google.android.gms.maps.model.LatLng

/// Handing a planned drive to a maps app, and the one place that URL is built.
///
/// The same rules live in `Farmsy/Core/MapsHandoff.swift` and in
/// `src/lib/mapsHandoff.ts` on the web. Three places, one truth — the same
/// arrangement as [TripEndpoints]. If a rule changes here it changes there, in
/// the same commit.
///
/// R1 gave a trip a destination of its own. This did not follow: the handover
/// built `[originCoord] + stops` and took the last of those as the destination,
/// so a drive planned Utrecht to Groningen with three stops opened as a drive
/// ending at the third farm. [TripStore.destinationCoord] has existed since R1
/// and was simply never read. A stop is somewhere you pass through; the
/// destination is where you stop.
///
/// Two limits shape the rest of this file:
///
///  - `dir/?api=1` takes at most nine waypoints and silently drops the rest. A
///    ten-stop drive would open missing stops with nothing to say so. The older
///    path form (/maps/dir/A/B/C) has no such cap, so it is used once there are
///    too many, rather than quietly losing stops or refusing to hand over.
///  - Everything is coordinates, never place names. A farm called
///    "Chez Lies & Fils" passes through three encoders on the way to a maps app,
///    and a name that resolves to the wrong shop is worse than a pin with no
///    name at all.
object MapsHandoff {

    /// Google's documented ceiling for `waypoints` on dir/?api=1.
    const val API_WAYPOINT_LIMIT = 9

    /// A planned drive, reduced to what a maps URL needs.
    data class Plan(
        /// Where the drive starts. Null falls back to the first stop.
        val origin: LatLng? = null,
        /// The stops, in the order they are driven.
        val stops: List<LatLng> = emptyList(),
        /// Where it ends. Null falls back to the last stop.
        val destination: LatLng? = null,
        /// `driving`, `walking`, `bicycling`. Null leaves the choice to Google,
        /// which is what the web does — it has no mode picker.
        val travelMode: String? = null,
    )

    private fun at(p: LatLng) = "${p.latitude},${p.longitude}"

    /// The Google Maps URL for a planned drive, or "" when there is nothing to
    /// hand over.
    ///
    /// The rules, in the order they are applied:
    ///  - No stops and no destination is nothing to open.
    ///  - One place and no start is a search for it, not a route from nowhere.
    ///  - A destination of its own keeps every stop as a waypoint.
    ///  - No destination means the last stop becomes one, as it did before R1.
    fun googleMapsUrl(plan: Plan): String {
        val stops = plan.stops
        val destination = plan.destination
        if (stops.isEmpty() && destination == null) return ""

        // Where it ends, and which stops are therefore on the way.
        val end = destination ?: stops.last()
        val via = if (destination != null) stops else stops.dropLast(1)

        // Nothing to route between: one place and nowhere to come from. A route
        // from the user's current location is Google's job to offer, not ours
        // to assume.
        if (plan.origin == null && via.isEmpty()) {
            return "https://www.google.com/maps/search/?api=1&query=${at(end)}"
        }

        val start = plan.origin ?: via.first()
        val middle = if (plan.origin != null) via else via.drop(1)
        val mode = plan.travelMode?.let { "&travelmode=$it" }.orEmpty()

        // Past the documented cap, api=1 drops waypoints without saying so. The
        // path form carries them all, so a long drive stays a long drive.
        if (middle.size > API_WAYPOINT_LIMIT) {
            val path = (listOf(start) + middle + listOf(end)).joinToString("/") { at(it) }
            return "https://www.google.com/maps/dir/$path"
        }

        val url = "https://www.google.com/maps/dir/?api=1&origin=${at(start)}&destination=${at(end)}$mode"
        if (middle.isEmpty()) return url

        // The separator is written as %7C rather than run through an encoder.
        // Encoding the joined string would also encode the commas, dots and
        // minus signs — still valid, but a different URL than the web and iOS
        // send for the same drive, and two platforms disagreeing about one trip
        // is a bug found by whoever tries both phones. It also keeps this file
        // free of android.net.Uri, so it can be unit tested without a device.
        return "$url&waypoints=" + middle.joinToString("%7C") { at(it) }
    }
}
