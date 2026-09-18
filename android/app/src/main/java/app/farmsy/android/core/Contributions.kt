package app.farmsy.android.core

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.ShoppingBasket
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.ui.graphics.vector.ImageVector
import app.farmsy.android.R
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Badges and the leaderboard: what a person gave the map, as the server counts
// it (migration 069). The app only reads; award_badges runs server-side after
// every report and post, so nothing here can be earned by tapping.
// The Android twin of iOS Contributions.swift.

/// The eight badges, in display order, with their rule in words.
enum class BadgeKind(val wire: String, @StringRes val titleRes: Int, @StringRes val ruleRes: Int, val icon: ImageVector) {
    FIRST_REPORT("first_report", R.string.badge_first_report, R.string.badge_rule_first_report, Icons.Filled.Flag),
    EARLY_BIRD("early_bird", R.string.badge_early_bird, R.string.badge_rule_early_bird, Icons.Filled.WbSunny),
    CONFIRMER("confirmer", R.string.badge_confirmer, R.string.badge_rule_confirmer, Icons.Filled.Verified),
    EXPLORER("explorer", R.string.badge_explorer, R.string.badge_rule_explorer, Icons.Filled.Map),
    SHELF_SCOUT("shelf_scout", R.string.badge_shelf_scout, R.string.badge_rule_shelf_scout, Icons.Filled.ShoppingBasket),
    PHOTOGRAPHER("photographer", R.string.badge_photographer, R.string.badge_rule_photographer, Icons.Filled.PhotoCamera),
    REGULAR("regular", R.string.badge_regular, R.string.badge_rule_regular, Icons.Filled.CalendarMonth),
    COLLECTOR("collector", R.string.badge_collector, R.string.badge_rule_collector, Icons.Filled.Favorite);

    companion object {
        fun fromWire(s: String): BadgeKind? = entries.firstOrNull { it.wire == s }
    }
}

@Serializable
data class ContributionStats(
    val reports: Int = 0,
    val farms: Int = 0,
    val confirmations: Int = 0,
    @SerialName("early_birds") val earlyBirds: Int = 0,
    @SerialName("shelf_reports") val shelfReports: Int = 0,
    val weeks: Int = 0,
    val posts: Int = 0,
    @SerialName("photo_posts") val photoPosts: Int = 0,
    val saved: Int = 0,
    val badges: Int = 0,
)

@Serializable
data class EarnedBadge(val badge: String, @SerialName("earned_at") val earnedAt: String)

@Serializable
data class LeaderRow(
    @SerialName("user_id") val userId: String,
    val name: String,
    val reports: Int = 0,
    val farms: Int = 0,
    val confirmations: Int = 0,
    val badges: Int = 0,
)

object Contributions {
    private val _stats = MutableStateFlow<ContributionStats?>(null)
    val stats: StateFlow<ContributionStats?> = _stats.asStateFlow()
    private val _earned = MutableStateFlow<List<EarnedBadge>>(emptyList())
    val earned: StateFlow<List<EarnedBadge>> = _earned.asStateFlow()
    /// Badges the last refresh awarded — the profile shows a celebration for
    /// these once, then forgets them.
    private val _justAwarded = MutableStateFlow<List<BadgeKind>>(emptyList())
    val justAwarded: StateFlow<List<BadgeKind>> = _justAwarded.asStateFlow()
    private val _leaderboard = MutableStateFlow<List<LeaderRow>>(emptyList())
    val leaderboard: StateFlow<List<LeaderRow>> = _leaderboard.asStateFlow()
    private val _leaderboardMonth = MutableStateFlow<List<LeaderRow>>(emptyList())
    val leaderboardMonth: StateFlow<List<LeaderRow>> = _leaderboardMonth.asStateFlow()

    fun has(kind: BadgeKind): Boolean = _earned.value.any { it.badge == kind.wire }
    fun clearAwarded() { _justAwarded.value = emptyList() }

    suspend fun refreshMine(token: String) {
        val decoded = runCatching {
            val resp = httpClient.get("${Backend.WEB_API}/profile/contributions") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }
            if (resp.status.value != 200) null
            else lenientJson.decodeFromString<MinePayload>(resp.bodyAsText())
        }.getOrNull() ?: return
        _stats.value = decoded.stats
        _earned.value = decoded.badges
        _justAwarded.value = decoded.awarded.mapNotNull(BadgeKind::fromWire)
    }

    suspend fun refreshLeaderboard() = coroutineScope {
        val all = async { fetchBoard(null) }
        val month = async { fetchBoard("month") }
        _leaderboard.value = all.await()
        _leaderboardMonth.value = month.await()
    }

    private suspend fun fetchBoard(period: String?): List<LeaderRow> = runCatching {
        val url = "${Backend.WEB_API}/community/leaderboard" + (period?.let { "?period=$it" } ?: "")
        val resp = httpClient.get(url)
        if (resp.status.value != 200) emptyList()
        else lenientJson.decodeFromString<BoardPayload>(resp.bodyAsText()).rows
    }.getOrDefault(emptyList())

    @Serializable
    private data class MinePayload(val stats: ContributionStats? = null, val badges: List<EarnedBadge> = emptyList(), val awarded: List<String> = emptyList())

    @Serializable
    private data class BoardPayload(val rows: List<LeaderRow> = emptyList())
}
