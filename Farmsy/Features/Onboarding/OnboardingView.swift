import SwiftUI
import CoreLocation
import UserNotifications

// MARK: - Flow container

/// Question-per-screen onboarding with the progress header, matching the
/// reference flow but with Farmsy content and real database numbers.
struct OnboardingView: View {
    var onComplete: () -> Void

    @Environment(FarmsStore.self) private var farms
    @Environment(LocationManager.self) private var locationManager

    enum Step: Int, CaseIterable {
        case category, location, finding, counts, value, notify, referral
    }

    @State private var step: Step = .category
    @State private var chosenCategory: FarmCategory?
    @State private var chosenPlace: OnboardingPlace?
    @AppStorage("pendingRefCode") private var pendingRefCode = ""

    var body: some View {
        VStack(spacing: 0) {
            header
                .padding(.horizontal, 20)
                .padding(.top, 8)

            // All steps sit side-by-side on one sliding track. The whole page
            // (chips included) moves as one piece, and going back always
            // slides the right way — no insert/remove transitions involved.
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
        .background(Color.cream.ignoresSafeArea())
    }

    @ViewBuilder
    private func stepContent(for s: Step) -> some View {
        switch s {
        case .category:
            CategoryStep(selected: $chosenCategory) { advance() }
        case .location:
            LocationStep(chosen: $chosenPlace) { advance() }
        case .finding:
            FindingStep(place: chosenPlace, isActive: step == .finding) { advance() }
        case .counts:
            CountsStep(place: chosenPlace, isActive: step == .counts) { advance() }
        case .value:
            ValueStep(isActive: step == .value) { advance() }
        case .notify:
            NotifyStep { advance() }
        case .referral:
            ReferralStep(refCode: $pendingRefCode) {
                Haptics.success()
                onComplete()
            }
        }
    }

    private var canGoBack: Bool { step != .category && step != .finding }

    private var header: some View {
        HStack(spacing: 16) {
            // Slot is always reserved so the progress bar never jumps.
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
            .animation(.easeInOut(duration: 0.25), value: canGoBack)

            ProgressBar(fraction: fraction)
        }
        .frame(height: 48)
    }

    private var fraction: Double {
        Double(step.rawValue + 1) / Double(Step.allCases.count + 1)
    }

    private func advance() {
        Haptics.tap()
        if let next = Step(rawValue: step.rawValue + 1) { step = next }
    }

    private func goBack() {
        // Skip back over the auto-advancing "finding" interstitial.
        let target = step == .counts ? Step.location : Step(rawValue: step.rawValue - 1)
        if let target { step = target }
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

// MARK: - Places

struct OnboardingPlace: Equatable {
    let name: String
    let latitude: Double
    let longitude: Double

    var coordinate: CLLocationCoordinate2D {
        CLLocationCoordinate2D(latitude: latitude, longitude: longitude)
    }

    static let presets: [OnboardingPlace] = [
        .init(name: "Amsterdam", latitude: 52.3676, longitude: 4.9041),
        .init(name: "Rotterdam", latitude: 51.9244, longitude: 4.4777),
        .init(name: "Utrecht", latitude: 52.0907, longitude: 5.1214),
        .init(name: "Den Haag", latitude: 52.0705, longitude: 4.3007),
        .init(name: "Eindhoven", latitude: 51.4416, longitude: 5.4697),
        .init(name: "Antwerpen", latitude: 51.2194, longitude: 4.4025),
        .init(name: "Gent", latitude: 51.0543, longitude: 3.7174),
        .init(name: "Brussel", latitude: 50.8503, longitude: 4.3517),
    ]
}

// MARK: - Step 1: category

private struct CategoryStep: View {
    @Binding var selected: FarmCategory?
    var onContinue: () -> Void

    var body: some View {
        VStack(spacing: 0) {
            Spacer()
            VStack(spacing: 10) {
                Kicker(text: String(localized: "Personalization"))
                DisplayTitle(leading: String(localized: "What are you "),
                             emphasis: String(localized: "looking"),
                             trailing: String(localized: " for?"), size: 32)
            }
            .padding(.bottom, 26)

            ScrollView(showsIndicators: false) {
                VStack(spacing: 12) {
                    RadioRow(emoji: "🍽️", label: String(localized: "Everything local"), isSelected: selected == nil) {
                        selected = nil
                    }
                    ForEach([FarmCategory.produce, .dairy, .cheese, .eggs, .honey, .meat]) { cat in
                        RadioRow(emoji: cat.emoji, label: cat.label, isSelected: selected == cat) {
                            selected = cat
                        }
                    }
                }
                .padding(.horizontal, 20)
            }

            Button("Continue", action: onContinue)
                .buttonStyle(PrimaryButtonStyle())
                .accessibilityIdentifier("continue-category")
                .padding(.horizontal, 20)
                .padding(.bottom, 12)
        }
    }
}

struct RadioRow: View {
    let emoji: String
    let label: String
    let isSelected: Bool
    var onTap: () -> Void

    var body: some View {
        Button {
            Haptics.tap()
            onTap()
        } label: {
            HStack(spacing: 14) {
                ZStack {
                    Circle()
                        .fill(isSelected ? Color.farmGreen : .clear)
                        .stroke(isSelected ? Color.farmGreen : Color.inkMuted.opacity(0.4), lineWidth: 1.5)
                        .frame(width: 26, height: 26)
                    if isSelected {
                        Image(systemName: "checkmark")
                            .font(.system(size: 12, weight: .bold))
                            .foregroundStyle(.white)
                    }
                }
                Text(emoji)
                Text(label)
                    .font(.geist(18, .semibold))
                    .foregroundStyle(isSelected ? Color.farmGreen : Color.ink)
                Spacer()
            }
            .padding(.vertical, 17)
            .padding(.horizontal, 16)
            .background(
                RoundedRectangle(cornerRadius: 16, style: .continuous)
                    .fill(isSelected ? Color.farmGreenSoft : Color.creamCard)
                    .stroke(isSelected ? Color.farmGreen : .clear, lineWidth: 1.5)
            )
        }
        .buttonStyle(.plain)
    }
}

// MARK: - Step 2: location

private struct LocationStep: View {
    @Binding var chosen: OnboardingPlace?
    var onContinue: () -> Void

    @Environment(FarmsStore.self) private var farms
    @Environment(LocationManager.self) private var locationManager
    @State private var query = ""

    var body: some View {
        VStack(spacing: 0) {
            Spacer()
            VStack(spacing: 10) {
                Kicker(text: farms.pins.isEmpty
                       ? String(localized: "Farm shops across NL & BE")
                       : String(localized: "\(farms.pins.count.formatted())+ farm shops (NL & BE)"))
                DisplayTitle(leading: String(localized: "Let's find your "),
                             emphasis: String(localized: "local"),
                             trailing: String(localized: " farms"), size: 32)
            }
            .padding(.bottom, 26)

            HStack(spacing: 12) {
                TextField("City, e.g. Utrecht", text: $query)
                    .textInputAutocapitalization(.words)
                    .autocorrectionDisabled()
                    .padding(.vertical, 16)
                    .padding(.horizontal, 18)
                    .background(Color.creamCard, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
                    .onChange(of: query) { _, text in
                        matchCity(text)
                    }

                Button {
                    Haptics.tap()
                    locationManager.request()
                } label: {
                    Group {
                        if locationManager.isRequesting {
                            ProgressView()
                        } else {
                            Image(systemName: "location.fill")
                                .font(.system(size: 20, weight: .semibold))
                                .foregroundStyle(Color.farmGreen)
                        }
                    }
                    .frame(width: 56, height: 56)
                    .background(
                        RoundedRectangle(cornerRadius: 16, style: .continuous)
                            .fill(Color.creamCard)
                            .stroke(Color.farmGreen, lineWidth: 1.5)
                    )
                }
            }
            .padding(.horizontal, 20)
            .padding(.bottom, 14)

            FlowChips(places: OnboardingPlace.presets, selected: chosen) { place in
                Haptics.tap()
                query = ""
                chosen = place
            }
            .padding(.horizontal, 20)

            Spacer()

            Button("Continue", action: onContinue)
                .buttonStyle(PrimaryButtonStyle())
                .accessibilityIdentifier("continue-location")
                .disabled(chosen == nil)
                .opacity(chosen == nil ? 0.55 : 1)
                .padding(.horizontal, 20)
                .padding(.bottom, 12)
        }
        .onChange(of: locationManager.location) { _, loc in
            if let loc {
                query = ""
                chosen = OnboardingPlace(
                    name: String(localized: "your location"),
                    latitude: loc.coordinate.latitude,
                    longitude: loc.coordinate.longitude
                )
            }
        }
    }

    /// Try to resolve a typed city against real farm data.
    private func matchCity(_ text: String) {
        let name = text.trimmingCharacters(in: .whitespaces)
        guard name.count >= 3 else { return }
        if let preset = OnboardingPlace.presets.first(where: { $0.name.lowercased() == name.lowercased() }) {
            chosen = preset
            return
        }
        if let pin = farms.pins.first(where: { ($0.city ?? "").lowercased() == name.lowercased() }) {
            chosen = OnboardingPlace(name: pin.city ?? name, latitude: pin.lat, longitude: pin.lng)
        }
    }
}

struct FlowChips: View {
    let places: [OnboardingPlace]
    let selected: OnboardingPlace?
    var onTap: (OnboardingPlace) -> Void

    private let columns = [GridItem(.adaptive(minimum: 104), spacing: 10)]

    var body: some View {
        LazyVGrid(columns: columns, spacing: 10) {
            ForEach(places, id: \.name) { place in
                let isOn = selected == place
                Button {
                    onTap(place)
                } label: {
                    Text(place.name)
                        .font(.geist(15, .medium))
                        .foregroundStyle(isOn ? .white : Color.ink)
                        .padding(.vertical, 11)
                        .frame(maxWidth: .infinity)
                        .background(isOn ? Color.farmGreen : Color.creamCard, in: Capsule())
                }
                .buttonStyle(.plain)
            }
        }
    }
}

// MARK: - Step 3: finding interstitial

private struct FindingStep: View {
    let place: OnboardingPlace?
    var isActive: Bool
    var onDone: () -> Void

    @Environment(FarmsStore.self) private var farms
    @State private var started = false

    var body: some View {
        VStack(spacing: 12) {
            Spacer()
            Kicker(text: String(localized: "Farms within 50 km of you"))
            DisplayTitle(leading: String(localized: "Farms "),
                         emphasis: String(localized: "near"),
                         trailing: String(localized: " you"), size: 34)
            Spacer().frame(height: 60)
            ProgressView()
                .controlSize(.large)
                .tint(Color.farmGreen)
            Text("Finding farms near you…")
                .font(.geist(18))
                .foregroundStyle(Color.inkMuted)
                .padding(.top, 10)
            Spacer()
            Spacer()
        }
        .onChange(of: isActive, initial: true) { _, active in
            if active { run() }
        }
    }

    /// Starts only when this page becomes the visible one — every step is
    /// mounted on the sliding track from the start.
    private func run() {
        guard !started else { return }
        started = true
        Task {
            // Let the real load finish, but always hold a beat for the reveal.
            try? await Task.sleep(for: .seconds(1.4))
            while farms.isLoading {
                try? await Task.sleep(for: .milliseconds(200))
            }
            onDone()
        }
    }
}

/// Number that rolls up from zero when its value animates.
struct CountUpText: View, Animatable {
    var value: Double
    var suffix = ""

    var animatableData: Double {
        get { value }
        set { value = newValue }
    }

    var body: some View {
        Text("\(Int(value.rounded()).formatted())\(suffix)")
            .monospacedDigit()
    }
}

// MARK: - Step 4: real category counts

private struct CountsStep: View {
    let place: OnboardingPlace?
    var isActive: Bool
    var onContinue: () -> Void

    @Environment(FarmsStore.self) private var farms
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var revealed = false

    private var counts: [(FarmCategory, Int)] {
        if let place {
            let near = farms.categoryCounts(near: place.coordinate, radiusKm: 50)
            if !near.isEmpty { return near }
        }
        // Fallback: whole dataset.
        var totals: [FarmCategory: Int] = [:]
        for pin in farms.pins {
            for cat in pin.categories { totals[cat, default: 0] += 1 }
        }
        return totals.map { ($0.key, $0.value) }.sorted { $0.1 > $1.1 }
    }

    private let columns = [GridItem(.flexible()), GridItem(.flexible()), GridItem(.flexible())]

    var body: some View {
        VStack(spacing: 0) {
            VStack(spacing: 10) {
                Kicker(text: place.map { String(localized: "Farms within 50 km of \($0.name)") }
                       ?? String(localized: "Farms across NL & BE"))
                DisplayTitle(leading: String(localized: "Farms "),
                             emphasis: String(localized: "near"),
                             trailing: String(localized: " you"), size: 34)
            }
            .padding(.top, 34)
            .padding(.bottom, 22)

            ScrollView(showsIndicators: false) {
                LazyVGrid(columns: columns, spacing: 12) {
                    ForEach(Array(counts.prefix(9).enumerated()), id: \.element.0) { i, entry in
                        let (cat, count) = entry
                        VStack(spacing: 6) {
                            Text(cat.emoji).font(.geist(34))
                            // Numbers roll up from 0 as the tiles pop in.
                            CountUpText(value: revealed ? Double(count) : 0)
                                .font(.geist(26, .bold))
                                .foregroundStyle(Color.farmGreen)
                            Text(cat.label)
                                .font(.geist(14))
                                .foregroundStyle(Color.ink)
                                .lineLimit(1)
                                .minimumScaleFactor(0.7)
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 18)
                        .background(Color.creamCard, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
                        .opacity(revealed ? 1 : 0)
                        .scaleEffect(revealed ? 1 : 0.9)
                        .animation(
                            reduceMotion ? nil
                                : .spring(duration: 0.8, bounce: 0.2).delay(Double(i) * 0.07),
                            value: revealed
                        )
                    }
                }
                .padding(.horizontal, 20)
            }

            Button("Continue", action: onContinue)
                .buttonStyle(PrimaryButtonStyle())
                .accessibilityIdentifier("continue-counts")
                .padding(.horizontal, 20)
                .padding(.bottom, 12)
        }
        .onChange(of: isActive, initial: true) { _, active in
            if active { revealed = true }
        }
    }
}

// MARK: - Step 5: value prop

private struct ValueStep: View {
    var isActive: Bool
    var onContinue: () -> Void

    @Environment(FarmsStore.self) private var farms
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var revealed = false

    private let emojiGrid = ["🥬", "🥛", "🧀", "🥚", "🥩", "🐟",
                             "🍯", "🍷", "🧺", "🌱", "🍎", "🥔",
                             "🍓", "🌷", "🍞", "🫐", "🥕", "🌽"]

    var body: some View {
        VStack(spacing: 0) {
            ScrollView(showsIndicators: false) {
                VStack(spacing: 24) {
                    VStack(spacing: 10) {
                        Kicker(text: String(localized: "Why Farmsy"))
                        DisplayTitle(leading: String(localized: "Real food, straight from the "),
                                     emphasis: String(localized: "farm"),
                                     trailing: "", size: 32)
                    }
                    .padding(.top, 30)

                    HStack(spacing: 0) {
                        // The farm-shop total rolls up from zero on arrival.
                        if farms.pins.isEmpty {
                            StatTile(value: String(localized: "1000s"), caption: String(localized: "farm shops"))
                        } else {
                            VStack(spacing: 3) {
                                CountUpText(value: revealed ? Double(farms.pins.count) : 0, suffix: "+")
                                    .font(.geist(22, .bold))
                                    .foregroundStyle(Color.farmGreen)
                                    .lineLimit(1)
                                    .minimumScaleFactor(0.6)
                                    .animation(reduceMotion ? nil : .easeOut(duration: 1.1), value: revealed)
                                Text("farm shops")
                                    .font(.geist(13))
                                    .foregroundStyle(Color.inkMuted)
                            }
                            .frame(maxWidth: .infinity)
                        }
                        Divider().frame(height: 40)
                        StatTile(value: "10", caption: String(localized: "categories"))
                        Divider().frame(height: 40)
                        StatTile(value: "NL + BE", caption: String(localized: "and growing"))
                    }
                    .card(padding: 14)

                    LazyVGrid(columns: Array(repeating: GridItem(.flexible()), count: 6), spacing: 16) {
                        ForEach(Array(emojiGrid.enumerated()), id: \.element) { i, e in
                            Text(e).font(.geist(30))
                                .opacity(revealed ? 1 : 0)
                                .scaleEffect(revealed ? 1 : 0.4)
                                .animation(
                                    reduceMotion ? nil
                                        : .spring(duration: 0.5, bounce: 0.45).delay(0.15 + Double(i) * 0.03),
                                    value: revealed
                                )
                        }
                    }
                    .padding(.horizontal, 8)

                    VStack(spacing: 14) {
                        Text("“Found a cheese farm ten minutes from home. Fresher than the supermarket, and I know exactly who made it.”")
                            .font(.geist(18, .medium).italic())
                            .foregroundStyle(Color.ink)
                            .multilineTextAlignment(.center)
                        HStack(spacing: 10) {
                            Circle()
                                .fill(Color.farmGreenSoft)
                                .frame(width: 38, height: 38)
                                .overlay(Text("🧀").font(.geist(18)))
                            VStack(alignment: .leading, spacing: 1) {
                                Text("Sanne").font(.geist(15, .semibold)).foregroundStyle(Color.ink)
                                Text("Utrecht").font(.geist(13)).foregroundStyle(Color.inkMuted)
                            }
                        }
                    }
                    .card()
                }
                .padding(.horizontal, 20)
            }

            Button("Continue", action: onContinue)
                .buttonStyle(PrimaryButtonStyle())
                .accessibilityIdentifier("continue-value")
                .padding(.horizontal, 20)
                .padding(.bottom, 12)
        }
        .onChange(of: isActive, initial: true) { _, active in
            if active { revealed = true }
        }
    }
}

struct StatTile: View {
    let value: String
    let caption: String
    var body: some View {
        VStack(spacing: 3) {
            Text(value)
                .font(.geist(22, .bold))
                .foregroundStyle(Color.farmGreen)
                .lineLimit(1)
                .minimumScaleFactor(0.6)
            Text(caption)
                .font(.geist(13))
                .foregroundStyle(Color.inkMuted)
        }
        .frame(maxWidth: .infinity)
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
                             trailing: "", size: 30)
            }
            Spacer().frame(height: 70)
            RingingBell(size: 76)
            Spacer()

            HStack(spacing: 14) {
                Button("No") { onContinue() }
                    .buttonStyle(SecondaryButtonStyle())
                Button("Notify Me") {
                    Task {
                        _ = try? await UNUserNotificationCenter.current()
                            .requestAuthorization(options: [.alert, .badge, .sound])
                        onContinue()
                    }
                }
                .buttonStyle(PrimaryButtonStyle())
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

// MARK: - Step 7: referral

private struct ReferralStep: View {
    @Binding var refCode: String
    var onFinish: () -> Void

    var body: some View {
        VStack(spacing: 0) {
            Spacer()
            VStack(spacing: 10) {
                Kicker(text: String(localized: "One last thing"))
                DisplayTitle(leading: String(localized: "Have a "),
                             emphasis: String(localized: "referral"),
                             trailing: String(localized: " code?"), size: 32)
            }
            Spacer().frame(height: 40)
            Image(systemName: "ticket.fill")
                .font(.system(size: 44))
                .foregroundStyle(Color.farmGreen)
                .frame(width: 92, height: 92)
                .background(Color.farmGreenSoft, in: Circle())
            Spacer().frame(height: 30)

            TextField("Enter code", text: $refCode)
                .textInputAutocapitalization(.characters)
                .autocorrectionDisabled()
                .multilineTextAlignment(.center)
                .font(.geist(19, .semibold))
                .kerning(2)
                .padding(.vertical, 16)
                .background(
                    RoundedRectangle(cornerRadius: 16, style: .continuous)
                        .fill(Color.creamCard)
                        .stroke(Color.inkMuted.opacity(0.25), lineWidth: 1)
                )
                .padding(.horizontal, 40)

            Spacer()

            Button(refCode.isEmpty ? "Continue" : "Apply Code") { onFinish() }
                .buttonStyle(PrimaryButtonStyle())
                .padding(.horizontal, 20)

            Button("Skip") {
                refCode = ""
                onFinish()
            }
            .font(.geist(17))
            .foregroundStyle(Color.inkMuted)
            .padding(.top, 14)
            .padding(.bottom, 12)
        }
    }
}

#Preview {
    OnboardingView {}
        .environment(FarmsStore())
        .environment(LocationManager())
}
