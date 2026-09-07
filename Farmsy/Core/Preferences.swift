import Foundation

/// The onboarding answers, kept (P0-3).
///
/// Seven screens of questions used to be honoured for one session and then
/// forgotten. These five fields — the chosen categories and the four quick
/// flags — now live in `profiles.preferences` on the server and in UserDefaults
/// on the device, and are applied to `FarmsStore` before the map first renders.
///
/// The wire shape is the web's (src/lib/preferences.ts); snake_case keys.
struct Preferences: Codable, Equatable {
    /// FarmCategory raw values, in the order they were picked. Empty means
    /// "show everything" — never "match nothing".
    var categories: [String] = []
    var openToday = false
    var pickYourOwn = false
    var verified = false
    var hasPhotos = false

    enum CodingKeys: String, CodingKey {
        case categories
        case openToday = "open_today"
        case pickYourOwn = "pick_your_own"
        case verified
        case hasPhotos = "has_photos"
    }

    init(categories: [String] = [], openToday: Bool = false, pickYourOwn: Bool = false,
         verified: Bool = false, hasPhotos: Bool = false) {
        self.categories = categories
        self.openToday = openToday
        self.pickYourOwn = pickYourOwn
        self.verified = verified
        self.hasPhotos = hasPhotos
    }

    /// Tolerant of a row written before a flag existed: a missing key is false,
    /// a missing list is empty.
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        categories  = try c.decodeIfPresent([String].self, forKey: .categories) ?? []
        openToday   = try c.decodeIfPresent(Bool.self, forKey: .openToday) ?? false
        pickYourOwn = try c.decodeIfPresent(Bool.self, forKey: .pickYourOwn) ?? false
        verified    = try c.decodeIfPresent(Bool.self, forKey: .verified) ?? false
        hasPhotos   = try c.decodeIfPresent(Bool.self, forKey: .hasPhotos) ?? false
    }

    /// The categories that still exist. A stored id that no longer exists is
    /// ignored, not an error.
    var knownCategories: Set<FarmCategory> {
        Set(categories.compactMap(FarmCategory.init(rawValue:)))
    }
}

/// Keeps `FarmsStore`'s five preference fields, the device mirror and the
/// server in agreement.
///
/// THE CONFLICT RULE, written down because it will not be obvious in six months:
///   1. Server wins on login. When the profile loads and the server has
///      preferences, they replace whatever the device held.
///   2. Device wins while offline. A change made when the server cannot be
///      reached is kept on the device and marked pending.
///   3. Device writes through on reconnect. The next time the profile loads
///      with a pending change, the device's value is pushed, not overwritten.
///   4. A fresh account (server has nothing) takes the device's answers — that
///      is the onboarding-while-signed-out case.
///   5. Signed-out users keep preferences on the device only.
@MainActor
final class PreferencesSync {
    private static let deviceKey  = "farmsy.preferences"
    private static let pendingKey = "farmsy.preferences.pending"

    private let farms: FarmsStore
    private let session: SessionStore
    private var saveTask: Task<Void, Never>?
    /// What was last applied from the device or the server. A change that only
    /// echoes it (applying server values fires the store's observer too) is
    /// not a user edit and must not be pushed back.
    private var lastApplied: Preferences?

    init(farms: FarmsStore, session: SessionStore) {
        self.farms = farms
        self.session = session
        // Device first, before the first render: a cold start with no network
        // still gets the map it was personalised to.
        if let device = Self.loadDevice() { apply(device) }
        farms.onPreferencesChanged = { [weak self] in self?.changed() }
        session.onProfileLoaded = { [weak self] profile in self?.profileLoaded(profile) }
    }

    // MARK: - Inbound

    /// The profile arrived: login, cold start with a session, or a foreground
    /// refresh. Applies the rule above.
    private func profileLoaded(_ profile: Profile) {
        let current = farms.preferences
        if UserDefaults.standard.bool(forKey: Self.pendingKey) {
            // Rule 3: the device changed something the server never got.
            Task { await push(current) }
            return
        }
        if let server = profile.preferences {
            // Rule 1.
            if server != current { apply(server) }
            Self.saveDevice(server)
        } else if Self.loadDevice() != nil {
            // Rule 4.
            Task { await push(current) }
        }
    }

    private func apply(_ p: Preferences) {
        lastApplied = p
        farms.apply(p)
    }

    // MARK: - Outbound

    /// One of the five fields changed in the store.
    private func changed() {
        let snapshot = farms.preferences
        if snapshot == lastApplied { return }        // an echo, not an edit
        lastApplied = snapshot
        Self.saveDevice(snapshot)                     // device mirror, always
        guard session.isAuthenticated else { return } // rule 5

        saveTask?.cancel()
        saveTask = Task { [weak self] in
            // Coalesce a burst of toggles into one write.
            try? await Task.sleep(for: .seconds(1))
            guard !Task.isCancelled, let self else { return }
            await self.push(self.farms.preferences)
        }
    }

    private func push(_ p: Preferences) async {
        guard session.isAuthenticated else { return }
        let token = (try? await supabase.auth.session.accessToken) ?? session.session?.accessToken
        guard let token else { UserDefaults.standard.set(true, forKey: Self.pendingKey); return }

        var request = URLRequest(url: Backend.webAPI.appending(path: "profile/preferences"))
        request.httpMethod = "PUT"
        request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try? JSONEncoder().encode(p)
        do {
            let (_, response) = try await URLSession.shared.data(for: request)
            let ok = (response as? HTTPURLResponse)?.statusCode == 200
            UserDefaults.standard.set(!ok, forKey: Self.pendingKey)   // rule 2 when !ok
        } catch {
            UserDefaults.standard.set(true, forKey: Self.pendingKey)  // rule 2
        }
    }

    // MARK: - Device mirror

    private static func loadDevice() -> Preferences? {
        guard let data = UserDefaults.standard.data(forKey: deviceKey) else { return nil }
        return try? JSONDecoder().decode(Preferences.self, from: data)
    }

    private static func saveDevice(_ p: Preferences) {
        if let data = try? JSONEncoder().encode(p) {
            UserDefaults.standard.set(data, forKey: deviceKey)
        }
    }
}
