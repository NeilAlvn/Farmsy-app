import SwiftUI
import CoreLocation
import UserNotifications

// MARK: - Flow container

/// Seven-screen onboarding rebuilt to the app's design system: a full-bleed
/// welcome photo, a personalization pass, a location pick, optional preferences,
/// a real "farms near you" shelf (featured-card design, photo'd farms within
/// 100 km), a notifications ask, and a wrap-up. No "explore as guest" — the two
/// ways in are logging in or skipping the intro straight to the map.
struct OnboardingView: View {
    var onComplete: () -> Void

    @Environment(FarmsStore.self) private var farms
    @Environment(LocationManager.self) private var locationManager

    /// Sign-in presented over the onboarding. On success the session becomes
    /// authenticated and RootView swaps in the map on its own; on cancel the
    /// user stays right here on the welcome screen.
    @State private var showLogin = false

    enum Step: Int, CaseIterable {
        case welcome, personalize, location, details, nearby, notify, done
    }

    @State private var step: Step = .welcome
    @State private var selectedCats: Set<FarmCategory> = []
    @State private var prefs = QuickPrefs()
    @State private var applyPrefs = false
    @State private var chosenCoord: CLLocationCoordinate2D?
    @State private var chosenLabel: String?
    @State private var showPlaceSearch = false

    /// The point we measure "farms near you" from: an explicit pick, or GPS.
    private var focusCoord: CLLocationCoordinate2D? {
        chosenCoord ?? locationManager.location?.coordinate
    }

    private var showsHeader: Bool { step != .welcome && step != .done }
    private var canGoBack: Bool { step != .welcome && step != .done }

    var body: some View {
        ZStack {
            // Full-bleed background: the welcome photograph, cream everywhere else.
            Group {
                if step == .welcome {
                    Image("WelcomeFarmShop")
                        .resizable()
                        .scaledToFill()
                        .overlay(
                            LinearGradient(
                                colors: [.black.opacity(0.72), .black.opacity(0.30), .black.opacity(0.55)],
                                startPoint: .bottom, endPoint: .top)
                        )
                } else {
                    Color.cream
                }
            }
            .ignoresSafeArea()
            .animation(.easeInOut(duration: 0.45), value: step == .welcome)

            // Safe-area content: a reserved header slot, then the sliding track.
            VStack(spacing: 0) {
                header
                    .padding(.horizontal, 20)
                    .padding(.top, 8)
                    .opacity(showsHeader ? 1 : 0)

                GeometryReader { geo in
                    HStack(spacing: 0) {
                        ForEach(Step.allCases, id: \.rawValue) { s in
                            stepContent(for: s)
                                .frame(width: geo.size.width, height: geo.size.height)
                        }
                    }
                    .offset(x: -CGFloat(step.rawValue) * geo.size.width)
                    .animation(.spring(duration: 0.5, bounce: 0.14), value: step)
                }
                .clipped()
            }
        }
        .sheet(isPresented: $showLogin) { AuthView() }
        .sheet(isPresented: $showPlaceSearch) {
            PlaceSearchSheet(
                onPick: { coord, label in
                    chosenCoord = coord
                    chosenLabel = label
                },
                onLocate: { locationManager.request() })
                .presentationDetents([.large])
        }
    }

    @ViewBuilder
    private func stepContent(for s: Step) -> some View {
        switch s {
        case .welcome:
            WelcomeStep(onLogin: { showLogin = true }, onSkip: { advance() })
        case .personalize:
            PersonalizeStep(selected: $selectedCats) { advance() }
        case .location:
            LocationStep(
                label: chosenLabel,
                onUseLocation: { locationManager.request() },
                onSearch: { showPlaceSearch = true },
                onContinue: { advance() })
        case .details:
            DetailsStep(prefs: $prefs,
                        onShowFarms: { applyPrefs = true; advance() },
                        onSkip: { applyPrefs = false; advance() })
        case .nearby:
            NearbyStep(coord: focusCoord, label: chosenLabel) { advance() }
        case .notify:
            NotifyStep { advance() }
        case .done:
            DoneStep { finish() }
        }
    }

    private var header: some View {
        HStack(spacing: 16) {
            Button {
                Haptics.tap()
                goBack()
            } label: {
                Image(systemName: "chevron.left")
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(Color.farmGreen)
                    .frame(width: 44, height: 44)
                    .background(Color.creamCard, in: Circle())
            }
            .opacity(canGoBack ? 1 : 0)
            .disabled(!canGoBack)

            ProgressBar(fraction: fraction)
        }
        .frame(height: 48)
    }

    /// Progress across the middle steps (welcome and done sit outside the bar).
    private var fraction: Double {
        let mid = max(1, Step.allCases.count - 2)   // personalize … notify
        return Double(step.rawValue) / Double(mid)
    }

    private func advance() {
        Haptics.tap()
        if let next = Step(rawValue: step.rawValue + 1) { step = next }
    }

    private func goBack() {
        if let target = Step(rawValue: step.rawValue - 1) { step = target }
    }

    /// Carry the user's choices into the map, then hand off.
    private func finish() {
        Haptics.success()
        farms.selectedCategories = selectedCats
        if applyPrefs {
            farms.filterVerified  = prefs.verified
            farms.filterOpenToday = prefs.openToday
            farms.filterHasPhotos = prefs.hasPhotos
            farms.filterZelfpluk  = prefs.pickYourOwn
        }
        onComplete()
    }
}

struct ProgressBar: View {
    var fraction: Double
    var body: some View {
        GeometryReader { geo in
            ZStack(alignment: .leading) {
                Capsule().fill(Color.farmGreen.opacity(0.22))
                Capsule()
                    .fill(Color.farmGreen)
                    .frame(width: max(12, geo.size.width * fraction))
            }
        }
        .frame(height: 5)
        .animation(.spring(duration: 0.5), value: fraction)
    }
}

/// The optional preferences captured on the "anything else" screen.
struct QuickPrefs: Equatable {
    var openToday = false
    var pickYourOwn = false
    var verified = false
    var hasPhotos = false
}

// MARK: - Step 1: welcome

private struct WelcomeStep: View {
    var onLogin: () -> Void
    var onSkip: () -> Void

    var body: some View {
        VStack(spacing: 0) {
            Spacer()

            VStack(spacing: 16) {
                VStack(spacing: 14) {
                    Image("FarmsyLogo")
                        .resizable()
                        .scaledToFit()
                        .frame(width: 74, height: 74)
                        .padding(16)
                        .background(Color.cream, in: Circle())
                        .shadow(color: .black.opacity(0.25), radius: 10, y: 4)
                    Text("Farmsy")
                        .font(.displayItalic(52, weight: .medium))
                        .foregroundStyle(.white)
                }
                Text("Local food, close to you.")
                    .font(.display(26, weight: .medium))
                    .foregroundStyle(.white)
                    .multilineTextAlignment(.center)
                Text("Find farm shops, pick-your-own farms and honest food straight from the people who grow it.")
                    .font(.geist(16))
                    .foregroundStyle(.white.opacity(0.9))
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 12)
            }

            Spacer()

            VStack(spacing: 11) {
                Button {
                    Haptics.tap()
                    onLogin()
                } label: {
                    Text("Log in / Sign up")
                        .font(.geist(18, .semibold))
                        .foregroundStyle(Color.farmGreen)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 17)
                        .background(.white, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
                }
                .buttonStyle(.plain)

                Button {
                    Haptics.tap()
                    onSkip()
                } label: {
                    Text("Skip for now")
                        .font(.geist(17, .semibold))
                        .foregroundStyle(.white)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 15)
                        .background(.white.opacity(0.14), in: RoundedRectangle(cornerRadius: 16, style: .continuous))
                        .overlay(
                            RoundedRectangle(cornerRadius: 16, style: .continuous)
                                .stroke(.white.opacity(0.35), lineWidth: 1))
                }
                .buttonStyle(.plain)
            }
            .padding(.horizontal, 24)
            .padding(.bottom, 48)
        }
    }
}

// MARK: - Step 2: personalize (multi-select categories)

private struct PersonalizeStep: View {
    @Binding var selected: Set<FarmCategory>
    var onContinue: () -> Void

    private let options: [FarmCategory] = [.produce, .dairy, .cheese, .eggs, .honey, .meat, .fish, .wine]
    private let columns = [GridItem(.flexible(), spacing: 12), GridItem(.flexible(), spacing: 12)]

    var body: some View {
        VStack(spacing: 0) {
            VStack(spacing: 10) {
                Kicker(text: String(localized: "Personalize"))
                DisplayTitle(leading: String(localized: "What are you "),
                             emphasis: String(localized: "looking"),
                             trailing: String(localized: " for?"), size: 32)
                Text("Pick a few — or none. You can change this anytime.")
                    .font(.geist(15))
                    .foregroundStyle(Color.inkMuted)
                    .multilineTextAlignment(.center)
            }
            .padding(.top, 22)
            .padding(.bottom, 22)
            .padding(.horizontal, 20)

            ScrollView(showsIndicators: false) {
                LazyVGrid(columns: columns, spacing: 12) {
                    ForEach(options) { cat in
                        CategoryTile(cat: cat, isOn: selected.contains(cat)) {
                            Haptics.tap()
                            if selected.contains(cat) { selected.remove(cat) } else { selected.insert(cat) }
                        }
                    }
                }
                .padding(.horizontal, 20)
                .padding(.bottom, 8)
            }

            Button(selected.isEmpty ? "Skip" : "Continue", action: onContinue)
                .buttonStyle(PrimaryButtonStyle())
                .accessibilityIdentifier("continue-personalize")
                .padding(.horizontal, 20)
                .padding(.bottom, 12)
        }
    }
}

private struct CategoryTile: View {
    let cat: FarmCategory
    let isOn: Bool
    var onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            HStack(spacing: 10) {
                Text(cat.emoji).font(.geist(24))
                Text(cat.label)
                    .font(.geist(16, .semibold))
                    .foregroundStyle(isOn ? Color.farmGreen : Color.ink)
                    .lineLimit(1)
                    .minimumScaleFactor(0.8)
                Spacer(minLength: 0)
                if isOn {
                    Image(systemName: "checkmark.circle.fill")
                        .font(.system(size: 18))
                        .foregroundStyle(Color.farmGreen)
                }
            }
            .padding(.vertical, 16)
            .padding(.horizontal, 14)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(
                RoundedRectangle(cornerRadius: 16, style: .continuous)
                    .fill(isOn ? Color.farmGreenSoft : Color.creamCard)
                    .stroke(isOn ? Color.farmGreen : Color.hairline, lineWidth: isOn ? 1.5 : 1))
        }
        .buttonStyle(.plain)
    }
}

/// A location "radar": static range rings, a sweep of expanding pulses, and a
/// pin at the centre — the map-ish visual for the location step.
struct RadarPulse: View {
    @State private var animate = false

    var body: some View {
        ZStack {
            // Fixed range rings.
            ForEach(0..<3, id: \.self) { i in
                Circle()
                    .stroke(Color.farmGreen.opacity(0.22), lineWidth: 1.5)
                    .frame(width: 58 + CGFloat(i) * 44, height: 58 + CGFloat(i) * 44)
            }
            // Two staggered pulses rippling outward.
            ForEach(0..<2, id: \.self) { i in
                Circle()
                    .stroke(Color.farmGreenMap.opacity(animate ? 0 : 0.55), lineWidth: 2)
                    .frame(width: animate ? 150 : 50, height: animate ? 150 : 50)
                    .animation(
                        .easeOut(duration: 2.4).repeatForever(autoreverses: false).delay(Double(i) * 1.2),
                        value: animate)
            }
            // Centre pin.
            Circle()
                .fill(Color.farmGreenMap)
                .frame(width: 48, height: 48)
                .overlay(
                    Image(systemName: "location.fill")
                        .font(.system(size: 19, weight: .semibold))
                        .foregroundStyle(.white))
                .shadow(color: Color.farmGreenMap.opacity(0.35), radius: 8, y: 3)
        }
        .frame(height: 170)
        .onAppear { animate = true }
    }
}

// MARK: - Step 3: location

private struct LocationStep: View {
    let label: String?
    var onUseLocation: () -> Void
    var onSearch: () -> Void
    var onContinue: () -> Void

    @Environment(LocationManager.self) private var locationManager

    private var resolved: String? {
        if let label { return label }
        if locationManager.location != nil { return String(localized: "Your current location") }
        return nil
    }

    var body: some View {
        VStack(spacing: 0) {
            Spacer()

            VStack(spacing: 10) {
                Kicker(text: String(localized: "Location"))
                DisplayTitle(leading: String(localized: "Where are you "),
                             emphasis: String(localized: "exploring"),
                             trailing: String(localized: " today?"), size: 30)
            }
            .padding(.horizontal, 20)

            Spacer().frame(height: 20)

            RadarPulse()

            Spacer().frame(height: 28)

            VStack(spacing: 14) {
                Button {
                    Haptics.tap()
                    onUseLocation()
                } label: {
                    rowLabel(icon: "location.fill",
                             title: String(localized: "Use my location"),
                             filled: true)
                }
                .buttonStyle(.plain)

                Button {
                    Haptics.tap()
                    onSearch()
                } label: {
                    rowLabel(icon: "magnifyingglass",
                             title: String(localized: "Search a town instead"),
                             filled: false)
                }
                .buttonStyle(.plain)
            }
            .padding(.horizontal, 20)

            if let resolved {
                HStack(spacing: 8) {
                    Image(systemName: "checkmark.circle.fill")
                        .foregroundStyle(Color.farmGreen)
                    Text(resolved)
                        .font(.geist(15, .semibold))
                        .foregroundStyle(Color.ink)
                }
                .padding(.top, 20)
            }

            Spacer()

            Button("Continue", action: onContinue)
                .buttonStyle(PrimaryButtonStyle())
                .accessibilityIdentifier("continue-location")
                .padding(.horizontal, 20)
                .padding(.top, 8)
                .padding(.bottom, 12)
        }
    }

    private func rowLabel(icon: String, title: String, filled: Bool) -> some View {
        HStack(spacing: 14) {
            Image(systemName: icon)
                .font(.system(size: 18, weight: .semibold))
                .foregroundStyle(filled ? .white : Color.farmGreen)
                .frame(width: 44, height: 44)
                .background(filled ? Color.farmGreenMap : Color.farmGreenSoft, in: Circle())
            Text(title)
                .font(.geist(17, .semibold))
                .foregroundStyle(Color.ink)
            Spacer()
            Image(systemName: "chevron.right")
                .font(.system(size: 14, weight: .semibold))
                .foregroundStyle(Color.inkMuted)
        }
        .padding(.vertical, 14)
        .padding(.horizontal, 16)
        .background(Color.creamCard, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: 16, style: .continuous).stroke(Color.hairline, lineWidth: 1))
    }
}

// MARK: - Step 4: optional details

private struct DetailsStep: View {
    @Binding var prefs: QuickPrefs
    var onShowFarms: () -> Void
    var onSkip: () -> Void

    var body: some View {
        VStack(spacing: 0) {
            VStack(spacing: 10) {
                Kicker(text: String(localized: "Optional"))
                DisplayTitle(leading: String(localized: "Anything else we should "),
                             emphasis: String(localized: "know?"),
                             trailing: "", size: 30)
                Text("Fine-tune what shows up. All optional.")
                    .font(.geist(15))
                    .foregroundStyle(Color.inkMuted)
                    .multilineTextAlignment(.center)
            }
            .padding(.top, 22)
            .padding(.bottom, 22)
            .padding(.horizontal, 20)

            ScrollView(showsIndicators: false) {
                VStack(spacing: 12) {
                    PrefRow(emoji: "🕒", title: String(localized: "Open today"),
                            subtitle: String(localized: "Only farms open right now"), isOn: $prefs.openToday)
                    PrefRow(emoji: "🧺", title: String(localized: "Pick-your-own"),
                            subtitle: String(localized: "Zelfpluk farms you can visit"), isOn: $prefs.pickYourOwn)
                    PrefRow(emoji: "✅", title: String(localized: "Verified farms"),
                            subtitle: String(localized: "Confirmed, up-to-date listings"), isOn: $prefs.verified)
                    PrefRow(emoji: "📷", title: String(localized: "Has photos"),
                            subtitle: String(localized: "See the place before you go"), isOn: $prefs.hasPhotos)
                }
                .padding(.horizontal, 20)
            }

            VStack(spacing: 16) {
                Button("Show me farms", action: onShowFarms)
                    .buttonStyle(PrimaryButtonStyle())
                Button("I'll explore on my own", action: onSkip)
                    .font(.geist(16, .semibold))
                    .foregroundStyle(Color.inkMuted)
            }
            .padding(.horizontal, 20)
            .padding(.top, 12)
            .padding(.bottom, 14)
        }
    }
}

private struct PrefRow: View {
    let emoji: String
    let title: String
    let subtitle: String
    @Binding var isOn: Bool

    var body: some View {
        Button {
            Haptics.tap()
            isOn.toggle()
        } label: {
            HStack(spacing: 12) {
                Text(emoji).font(.geist(22))
                VStack(alignment: .leading, spacing: 2) {
                    Text(title).font(.geist(16, .semibold)).foregroundStyle(Color.ink)
                    Text(subtitle).font(.geist(13)).foregroundStyle(Color.inkMuted)
                }
                Spacer()
                ZStack {
                    RoundedRectangle(cornerRadius: 20, style: .continuous)
                        .fill(isOn ? Color.farmGreenMap : Color(hex: 0xE5E4DF))
                        .frame(width: 46, height: 28)
                    Circle().fill(.white).frame(width: 22, height: 22)
                        .offset(x: isOn ? 9 : -9)
                        .shadow(color: .black.opacity(0.15), radius: 1, y: 1)
                }
                .animation(.spring(duration: 0.25), value: isOn)
            }
            .padding(.vertical, 14)
            .padding(.horizontal, 14)
            .background(Color.creamCard, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: 16, style: .continuous).stroke(Color.hairline, lineWidth: 1))
        }
        .buttonStyle(.plain)
    }
}

// MARK: - Step 5: farms near you (featured-card shelf)

private struct NearbyStep: View {
    let coord: CLLocationCoordinate2D?
    let label: String?
    var onContinue: () -> Void

    @Environment(FarmsStore.self) private var farms

    /// The shuffled list actually shown — built once (and rebuilt only when the
    /// data or location behind it changes) so the order stays put across renders.
    @State private var shown: [FarmPin] = []
    @State private var built = false
    @State private var nearbyCount = 0
    /// Descriptions fetched for the non-featured nearby farms (featured ones
    /// already have theirs prefetched on the store).
    @State private var extraTeasers: [String: String] = [:]

    /// All photo'd farms within 100 km (nearest first), before mixing.
    private var pool: [FarmPin] {
        if let coord { return farms.nearbyWithImages(near: coord, radiusKm: 100) }
        return farms.feedPicks(near: nil, limit: 30)
    }

    /// Rebuild whenever the pins, the featured galleries, or the location change.
    private var buildKey: String {
        "\(farms.pins.count)-\(farms.galleriesLoaded)-\(coord?.latitude ?? 0)-\(coord?.longitude ?? 0)"
    }

    /// Photos for a card: the featured gallery when we have it, else the cover.
    private func images(for pin: FarmPin) -> [String] {
        if let gallery = farms.galleries[pin.osmId], !gallery.isEmpty { return gallery }
        if let cover = pin.image { return [cover] }
        return []
    }

    /// Split the pool into farms that carry a description (featured, with a
    /// gallery) and the rest, then shuffle them together so the described cards
    /// land at random positions in the list rather than clustering by distance.
    private func build() {
        let base = pool
        nearbyCount = base.count
        let described = base.filter { !(farms.galleries[$0.osmId]?.isEmpty ?? true) }
        let plain = base.filter { farms.galleries[$0.osmId]?.isEmpty ?? true }
        let picked = Array(described.prefix(6)) + Array(plain.prefix(8))
        shown = picked.shuffled()
        built = true
    }

    /// Fetch descriptions for the shown farms that don't already have one — so
    /// the cover-only (non-featured) cards also carry their teaser text. One batch
    /// request (Aviah's `/api/farms/teasers`) rather than a round trip per tile.
    /// We ask for every shown farm rather than trusting `hasDescription` (which
    /// `get_farms_pins()` hardcodes to false); farms with genuinely no description
    /// (≈1 in 5, foursquare imports) come back empty and simply show nothing.
    private func fetchTeasers() async {
        let targets = shown.prefix(10)
            .map(\.osmId)
            .filter { farms.featuredTeasers[$0] == nil && extraTeasers[$0] == nil }
        guard !targets.isEmpty else { return }
        let result = await FarmDetailAPI.teasers(osmIds: targets)
        guard !result.isEmpty else { return }
        extraTeasers.merge(result) { _, new in new }
    }

    private func teaser(for pin: FarmPin) -> String? {
        farms.featuredTeasers[pin.osmId] ?? extraTeasers[pin.osmId]
    }

    private var headline: String {
        guard built else { return String(localized: "Finding farms near you…") }
        guard coord != nil else { return String(localized: "Popular farm shops") }
        if let label {
            return String(localized: "\(nearbyCount) farms within 100 km of \(label)")
        }
        return String(localized: "\(nearbyCount) farms within 100 km of you")
    }

    var body: some View {
        VStack(spacing: 0) {
            VStack(spacing: 10) {
                Kicker(text: String(localized: "Great choice"))
                DisplayTitle(leading: String(localized: "Here are farms "),
                             emphasis: String(localized: "near"),
                             trailing: String(localized: " you"), size: 30)
                Text(headline)
                    .font(.geist(15, .semibold))
                    .foregroundStyle(Color.inkMuted)
                    .multilineTextAlignment(.center)
            }
            .padding(.top, 22)
            .padding(.bottom, 16)
            .padding(.horizontal, 20)

            ScrollView(showsIndicators: false) {
                LazyVStack(spacing: 14) {
                    if !built {
                        // Same skeleton the What's New "Featured farms" shelf uses.
                        ForEach(0..<3, id: \.self) { _ in
                            SkeletonBox(cornerRadius: 16).frame(height: 180)
                        }
                    } else {
                        ForEach(shown.prefix(10)) { pin in
                            MultiImageFarmCard(pin: pin,
                                               images: images(for: pin),
                                               teaser: teaser(for: pin),
                                               onOpen: onContinue)
                        }
                    }
                }
                .padding(.horizontal, 20)
                .padding(.bottom, 8)
            }

            Button("See all on map", action: onContinue)
                .buttonStyle(PrimaryButtonStyle())
                .accessibilityIdentifier("continue-nearby")
                .padding(.horizontal, 20)
                .padding(.top, 14)
                .padding(.bottom, 12)
        }
        .task(id: buildKey) {
            await farms.loadGalleriesIfNeeded()
            if !farms.pins.isEmpty {
                build()
                await fetchTeasers()
            }
        }
    }
}

// MARK: - Step 6: notifications

private struct NotifyStep: View {
    var onContinue: () -> Void

    var body: some View {
        VStack(spacing: 0) {
            Spacer()
            VStack(spacing: 10) {
                Kicker(text: String(localized: "Stay in the loop"))
                DisplayTitle(leading: String(localized: "Know when new farms appear "),
                             emphasis: String(localized: "near you"),
                             trailing: "", size: 28)
            }
            .padding(.horizontal, 20)
            Spacer().frame(height: 60)
            RingingBell(size: 76)
            Spacer()

            VStack(spacing: 16) {
                Button("Turn on notifications") {
                    Task {
                        _ = try? await UNUserNotificationCenter.current()
                            .requestAuthorization(options: [.alert, .badge, .sound])
                        onContinue()
                    }
                }
                .buttonStyle(PrimaryButtonStyle())

                Button("Maybe later") { onContinue() }
                    .font(.geist(16, .semibold))
                    .foregroundStyle(Color.inkMuted)
            }
            .padding(.horizontal, 20)

            Text("New farm shops join Farmsy every week.")
                .font(.geist(15).italic())
                .foregroundStyle(Color.inkMuted)
                .padding(.top, 14)
                .padding(.bottom, 12)
        }
    }
}

// MARK: - Step 7: all set

private struct DoneStep: View {
    var onStart: () -> Void

    var body: some View {
        VStack(spacing: 0) {
            Spacer()

            Image(systemName: "checkmark.seal.fill")
                .font(.system(size: 68))
                .foregroundStyle(Color.farmGreen)
                .padding(.bottom, 22)

            VStack(spacing: 10) {
                Kicker(text: String(localized: "Ready"))
                DisplayTitle(leading: String(localized: "You're all "),
                             emphasis: String(localized: "set"),
                             trailing: "", size: 34)
            }

            VStack(alignment: .leading, spacing: 14) {
                DoneBullet(emoji: "🗺️", text: String(localized: "Browse farm shops on the map"))
                DoneBullet(emoji: "❤️", text: String(localized: "Save the ones you want to visit"))
                DoneBullet(emoji: "🧭", text: String(localized: "Plan a trip across several farms"))
            }
            .fixedSize(horizontal: true, vertical: false)
            .padding(.top, 30)

            Spacer()

            Button("Start exploring", action: onStart)
                .buttonStyle(PrimaryButtonStyle())
                .accessibilityIdentifier("start-exploring")
                .padding(.horizontal, 20)
                .padding(.bottom, 12)
        }
    }
}

private struct DoneBullet: View {
    let emoji: String
    let text: String
    var body: some View {
        HStack(spacing: 14) {
            Text(emoji).font(.geist(22))
                .frame(width: 44, height: 44)
                .background(Color.farmGreenSoft, in: Circle())
            Text(text)
                .font(.geist(16, .medium))
                .foregroundStyle(Color.ink)
                .fixedSize(horizontal: false, vertical: true)
        }
    }
}

#Preview {
    OnboardingView(onComplete: {})
        .environment(FarmsStore())
        .environment(LocationManager())
        .environment(SessionStore())
        .environment(FavoritesStore())
}
