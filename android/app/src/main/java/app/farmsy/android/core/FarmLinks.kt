package app.farmsy.android.core

import java.net.URLDecoder

/// A farm page address: /boerderijwinkels/<place>/<name>.
data class FarmPathRef(val place: String, val name: String)

/// What the Play Store says brought this install (spec A4). `farm` is an osm_id.
data class InstallReferrer(val campaign: String?, val medium: String?, val farm: String?)

/// Farm page links (F3) and the Play Install Referrer. Mirrors iOS FarmLink.swift;
/// the same segment rule as the web's isSegment.
object FarmLinks {
    val HOSTS = setOf("farmsy.app", "www.farmsy.app")

    /// Dutch has no prefix; /nl/... is a redirect on the site and never claimed.
    private val PREFIXED_LOCALES = setOf("en", "fr", "de", "es")
    private val SEGMENT = Regex("[a-z0-9]+(-[a-z0-9]+)*") // matches() = whole string, so no trailing-newline slack

    private fun isSegment(s: String) = s.length <= 80 && SEGMENT.matches(s)

    fun parsePath(path: String?): FarmPathRef? {
        var parts = path.orEmpty().split('/').filter { it.isNotEmpty() }
        if (parts.isNotEmpty() && parts[0] in PREFIXED_LOCALES) parts = parts.drop(1)
        if (parts.size != 3 || parts[0] != "boerderijwinkels" || !isSegment(parts[1]) || !isSegment(parts[2])) return null
        return FarmPathRef(parts[1], parts[2])
    }

    /// `utm_source=web&utm_medium=farm_page&utm_campaign=app_funnel&farm=node%2F123`.
    /// Play hands it over already decoded once; each value is decoded once more.
    /// A value that will not decode, or is blank or absurdly long, counts as absent.
    fun parseReferrer(raw: String?): InstallReferrer {
        val params = raw.orEmpty().split('&').mapNotNull { pair ->
            val i = pair.indexOf('=')
            if (i <= 0) null
            else pair.substring(0, i) to runCatching { URLDecoder.decode(pair.substring(i + 1), "UTF-8") }.getOrNull()
        }.toMap()
        fun value(key: String) = params[key]?.trim()?.takeIf { it.isNotEmpty() && it.length <= 200 }
        return InstallReferrer(campaign = value("utm_campaign"), medium = value("utm_medium"), farm = value("farm"))
    }
}
