import SwiftUI
import Foundation

/// In-app language override.
///
/// iOS picks the app's language from the device settings, so a user whose phone
/// is in English can't get Farmsy in Dutch without changing their whole phone.
/// This lets them choose inside the app.
///
/// **Mechanism: `AppleLanguages` + reopen.** The chosen code is written to the
/// `AppleLanguages` default, which iOS reads at launch to pick the bundle every
/// `Text` and `String(localized:)` lookup resolves against — so the *entire* app
/// comes up in that language, consistently. It applies on the next launch rather
/// than mid-session: a live bundle swap only catches `NSLocalizedString`, not the
/// `String(localized:)` the app is built on, so switching in place would leave
/// half the screen in the old language. Reopening switches everything together.
/// Picking "System" clears the override and follows the device again.
@MainActor
@Observable
final class LanguageManager {
    static let shared = LanguageManager()

    /// The languages Farmsy ships translations for (must match the compiled
    /// `.lproj` set / the project's known regions).
    enum Lang: String, CaseIterable, Identifiable {
        case system = ""
        case en, nl, fr, de

        var id: String { rawValue }

        /// Endonym — each language named in itself, the convention for a language
        /// picker so people recognise their own regardless of the current UI language.
        var name: String {
            switch self {
            case .system: return String(localized: "System default")
            case .en:     return "English"
            case .nl:     return "Nederlands"
            case .fr:     return "Français"
            case .de:     return "Deutsch"
            }
        }

        var flag: String {
            switch self {
            case .system: return "🌐"
            case .en:     return "🇬🇧"
            case .nl:     return "🇳🇱"
            case .fr:     return "🇫🇷"
            case .de:     return "🇩🇪"
            }
        }
    }

    private let key = "app_language"
    /// The active choice (drives the picker's checkmark). Reactive.
    private(set) var current: Lang
    /// Locale for SwiftUI formatting, captured at launch so nothing shifts
    /// mid-session — the language change lands as one consistent switch on reopen.
    let launchLocale: Locale

    private init() {
        let raw = UserDefaults.standard.string(forKey: key) ?? ""
        let lang = Lang(rawValue: raw) ?? .system
        current = lang
        launchLocale = lang == .system ? .autoupdatingCurrent : Locale(identifier: lang.rawValue)
    }

    func set(_ lang: Lang) {
        guard lang != current else { return }
        current = lang
        if lang == .system {
            UserDefaults.standard.removeObject(forKey: key)
            UserDefaults.standard.removeObject(forKey: "AppleLanguages")
        } else {
            UserDefaults.standard.set(lang.rawValue, forKey: key)
            UserDefaults.standard.set([lang.rawValue], forKey: "AppleLanguages")
        }
        Haptics.tap()
    }
}
