package app.farmsy.android.core

// Every product event the app sends to PostHog, with its property keys and the
// closed set of values those properties may take.
//
// This is THE place event names are spelled. Never a string literal at a call
// site: `Observability.capture` only accepts an `AnalyticsEvent`, so a typo cannot
// quietly create a fourteenth event. The iOS twin is Farmsy/Core/AnalyticsEvent.swift,
// kept in the same order, and scripts/analytics-parity.sh diffs the two — that
// diff is the acceptance test.
//
// The web fires the same names (P0-2), so PostHog can follow one person from a
// web signup to an app purchase.
//
// Privacy: no email, name, coordinates or search text goes into any property.
// osm_id identifies a farm, not a person.

/// Event names, as PostHog shows them.
enum class AnalyticsEvent(val key: String) {
    APP_OPENED("app_opened"),
    ONBOARDING_STEP_COMPLETED("onboarding_step_completed"),
    ONBOARDING_SKIPPED("onboarding_skipped"),
    SIGNUP_COMPLETED("signup_completed"),
    FARM_OPENED("farm_opened"),
    FARM_SAVED("farm_saved"),
    FILTERS_OPENED("filters_opened"),
    PRO_FILTER_TAPPED("pro_filter_tapped"),
    PAYWALL_VIEWED("paywall_viewed"),
    PLAN_TAPPED("plan_tapped"),
    PURCHASE_COMPLETED("purchase_completed"),
    PURCHASE_FAILED("purchase_failed"),
    RESTORE_TAPPED("restore_tapped"),
}

/// Property keys.
object AnalyticsProp {
    const val STEP = "step"
    const val METHOD = "method"
    const val OSM_ID = "osm_id"
    const val SOURCE = "source"
    const val FILTER = "filter"
    const val TRIGGER = "trigger"
    const val PLAN = "plan"
    const val STORE = "store"
    const val REASON = "reason"
    // On every event, added by Observability.capture.
    const val IS_MEMBER = "is_member"
    const val PLATFORM = "platform"
    const val APP_VERSION = "app_version"
}

/// Closed value sets. A property that takes one of these is passed the key,
/// never free text — free text that varies by language would split one thing
/// into four buckets that never add up.
object AnalyticsValue {
    /// `source` on farm_opened: where the farm card was opened from. This is the
    /// property that says whether the map or the feed sells memberships.
    enum class Source(val key: String) {
        MAP_PIN("map_pin"),
        WHATS_NEW("whats_new"),
        DISCOVER("discover"),
        TRIPS("trips"),
        SAVED("saved"),
        SEARCH("search"),
        DEEP_LINK("deep_link"),
    }

    /// `trigger` on paywall_viewed: what put the purchase sheet on screen.
    /// P1-7 (phase 3) adds the rest when the paywall gets one decision point.
    enum class Trigger(val key: String) {
        FILTER_ROW("filter_row"),
        FARM_DETAIL("farm_detail"),
    }

    /// `filter` on pro_filter_tapped for the three time filters. The two axis
    /// groups pass their FarmAxis id through as-is.
    object Filter {
        const val OPEN_NOW = "open_now"
        const val OPEN_SATURDAY = "open_saturday"
        const val OPEN_SUNDAY = "open_sunday"
    }

    /// `plan` on plan_tapped and purchase_completed.
    enum class Plan(val key: String) {
        YEARLY("yearly"),
        LIFETIME("lifetime"),
    }

    /// `store` on purchase_completed.
    enum class Store(val key: String) {
        APP_STORE("app_store"),
        PLAY_STORE("play_store"),
    }

    /// `reason` on purchase_failed. A short code, never the store's localized
    /// message.
    enum class Reason(val key: String) {
        CANCELLED("cancelled"),
        NETWORK("network"),
        PRODUCT_UNAVAILABLE("product_unavailable"),
        STORE_ERROR("store_error"),
    }

    /// `method` on signup_completed. Email is the only way in today.
    enum class Method(val key: String) {
        EMAIL("email"),
    }
}
