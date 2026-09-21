import Foundation

// Every product event the app sends to PostHog, with its property keys and the
// closed set of values those properties may take.
//
// This is THE place event names are spelled. Never a string literal at a call
// site: `Observability.capture` only accepts an `AnalyticsEvent`, so a typo cannot
// quietly create a fourteenth event. The Android twin is
// android/.../core/AnalyticsEvent.kt, kept in the same order, and
// scripts/analytics-parity.sh diffs the two — that diff is the acceptance test.
//
// The web fires the same names (P0-2), so PostHog can follow one person from a
// web signup to an app purchase.
//
// Privacy: no email, name, coordinates or search text goes into any property.
// osm_id identifies a farm, not a person.

/// Event names, as PostHog shows them.
enum AnalyticsEvent: String {
    case appOpened = "app_opened"
    case onboardingStepCompleted = "onboarding_step_completed"
    case onboardingSkipped = "onboarding_skipped"
    case signupCompleted = "signup_completed"
    case farmOpened = "farm_opened"
    case farmSaved = "farm_saved"
    case filtersOpened = "filters_opened"
    case proFilterTapped = "pro_filter_tapped"
    case paywallViewed = "paywall_viewed"
    case planTapped = "plan_tapped"
    case purchaseCompleted = "purchase_completed"
    case purchaseFailed = "purchase_failed"
    case restoreTapped = "restore_tapped"
    // The four outcome events — did the farm open lead to a real intent? They fire
    // on the TAP, not a result: the OS is already taking the user to Maps, the
    // dialler or a browser and this screen is about to go, so the capture is queued
    // before the hand-off. No membership check on any of them — a free visitor
    // driving out to a farm shop is the product working, and gating them would
    // measure a different question.
    case farmDirections = "farm_directions"
    case farmCalled = "farm_called"
    case farmWebsite = "farm_website"
    case routePlanned = "route_planned"
}

/// Property keys.
enum AnalyticsProp {
    static let step = "step"
    static let method = "method"
    static let osmId = "osm_id"
    static let source = "source"
    static let filter = "filter"
    static let trigger = "trigger"
    static let plan = "plan"
    static let store = "store"
    static let reason = "reason"
    /// `route_planned`: how many farms the corridor found, and the radius it used.
    static let count = "count"
    static let radiusKm = "radius_km"
    // On every event, added by Observability.capture.
    static let isMember = "is_member"
    static let platform = "platform"
    static let appVersion = "app_version"
}

/// Closed value sets. A property that takes one of these is passed the rawValue,
/// never free text — free text that varies by language would split one thing
/// into four buckets that never add up.
enum AnalyticsValue {
    /// `source` on farm_opened: where the farm card was opened from. This is the
    /// property that says whether the map or the feed sells memberships.
    enum Source: String {
        case mapPin = "map_pin"
        case whatsNew = "whats_new"
        case discover = "discover"
        case trips = "trips"
        case saved = "saved"
        case search = "search"
        case deepLink = "deep_link"
    }

    /// `trigger` on paywall_viewed: what put the purchase sheet on screen.
    /// P1-7 (phase 3) adds the rest when the paywall gets one decision point.
    enum Trigger: String {
        case filterRow = "filter_row"
        case farmDetail = "farm_detail"
        case shoppingSample = "shopping_sample"
        case routePreview = "route_preview"
    }

    /// `filter` on pro_filter_tapped for the three time filters. The two axis
    /// groups pass their FarmAxis id through as-is.
    enum Filter {
        static let openNow = "open_now"
        static let openSaturday = "open_saturday"
        static let openSunday = "open_sunday"
    }

    /// `plan` on plan_tapped and purchase_completed.
    enum Plan: String {
        case yearly = "yearly"
        case lifetime = "lifetime"
    }

    /// `store` on purchase_completed.
    enum Store: String {
        case appStore = "app_store"
        case playStore = "play_store"
    }

    /// `reason` on purchase_failed. A short code, never the store's localized
    /// message.
    enum Reason: String {
        case cancelled = "cancelled"
        case network = "network"
        case productUnavailable = "product_unavailable"
        case storeError = "store_error"
    }

    /// `method` on signup_completed. Email is the only way in today.
    enum Method: String {
        case email = "email"
    }
}
