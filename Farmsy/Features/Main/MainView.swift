import SwiftUI

/// The map is the app. There is no feed, list or discovery tab — everything is a
/// layer over the map (MOBILE-SPEC-MAP §0). The farm card is a bottom sheet at
/// three heights with the map usable behind it; Saved and Settings live behind an
/// account button in the header; posts and featured farms live in the What's New
/// sheet, opened from a floating button on the map.
struct MainView: View {
    @Environment(SessionStore.self) private var session

    /// The open farm. A bound value (not a `.sheet(item:)`) so tapping another pin
    /// swaps the card's contents in place rather than dismissing and re-presenting.
    @State private var selectedPin: FarmPin?
    @State private var showAuth = false
    @State private var accountRoute: AccountRoute?
    @State private var showWhatsNew = false
    @State private var showTrips = false
    /// The card opens at half and can be dragged to peek or full.
    @State private var farmDetent: PresentationDetent = .fraction(0.55)
    /// A pin the map should fly to (set when opening from the What's New sheet).
    @State private var flyTarget: FarmPin?

    enum AccountRoute: Identifiable {
        case saved, settings
        var id: Int { hashValue }
    }

    var body: some View {
        MapScreen(onOpenFarm: { openFarm($0) }, focusPin: flyTarget)
        .background(Color.cream.ignoresSafeArea())
        .ignoresSafeArea(.keyboard)
        .tint(.farmGreen)
        .environment(\.requestAuth, { showAuth = true })
        .overlay(alignment: .bottom) {
            bottomPanel
                .padding(.horizontal, 14)
                .padding(.bottom, 6)
        }
        // The farm card — three resting heights, opening at half, and the map
        // stays interactive behind it up through half.
        .sheet(isPresented: Binding(
            get: { selectedPin != nil },
            set: { if !$0 { selectedPin = nil } }
        )) {
            if let pin = selectedPin {
                FarmDetailView(pin: pin)
                    .id(pin.osmId)   // swap contents when another pin is tapped
                    .presentationDetents([.height(180), .fraction(0.55), .large], selection: $farmDetent)
                    .presentationBackgroundInteraction(.enabled(upThrough: .fraction(0.55)))
                    .presentationContentInteraction(.scrolls)
                    .presentationDragIndicator(.visible)
            }
        }
        .sheet(isPresented: $showWhatsNew) {
            WhatsNewSheet(onOpenFarm: { pin in
                flyTarget = pin          // fly the map straight to it
                showWhatsNew = false
                openFarm(pin)
            })
            .presentationDetents([.fraction(0.55), .fraction(0.92)])
            .presentationBackgroundInteraction(.enabled(upThrough: .fraction(0.55)))
            .presentationDragIndicator(.visible)
        }
        .sheet(item: $accountRoute) { route in
            switch route {
            case .saved:    SavedScreen { openFarm($0) }
            case .settings: SettingsSheet()
            }
        }
        .sheet(isPresented: $showTrips) {
            TripsView(onOpenFarm: { openFarm($0) })
        }
        .sheet(isPresented: $showAuth) { AuthView() }
    }

    /// Farm cards open for everyone, signed out included (MOBILE-SPEC-MAP §0) —
    /// the card shows the free content and locks the paid fields inside. Actions
    /// that need an account (save, subscribe) prompt for one from within the card.
    private func openFarm(_ pin: FarmPin) {
        farmDetent = .fraction(0.55)   // always open at half
        selectedPin = pin
    }

    // MARK: - Bottom floating panel

    /// A floating pill over the map, the width of the search bar: Discover (the
    /// What's New sheet), Save, Trips and Settings.
    private var bottomPanel: some View {
        HStack(spacing: 4) {
            panelItem(icon: "newspaper", label: String(localized: "Discover")) { showWhatsNew = true }
            panelItem(icon: "heart", label: String(localized: "Saved")) { requireAuth { accountRoute = .saved } }
            panelItem(icon: "map", label: String(localized: "Trips")) { requireAuth { showTrips = true } }
            panelItem(icon: "gearshape", label: String(localized: "Settings")) { requireAuth { accountRoute = .settings } }
        }
        .padding(6)
        .background(.white, in: Capsule())
        .shadow(color: .black.opacity(0.14), radius: 12, y: 3)
    }

    private func panelItem(icon: String, label: String, action: @escaping () -> Void) -> some View {
        Button {
            Haptics.tap()
            action()
        } label: {
            VStack(spacing: 3) {
                Image(systemName: icon).font(.system(size: 17, weight: .semibold))
                Text(label).font(.geist(10, .semibold))
            }
            .foregroundStyle(Color.farmGreenMap)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 8)
        }
        .buttonStyle(.plain)
    }

    private func requireAuth(_ action: @escaping () -> Void) {
        if session.isAuthenticated { action() } else { showAuth = true }
    }
}

/// The trip planner — a client-side itinerary. Stops come from "Add to trip" on
/// farm cards; the road route + totals come from POST /api/route (TripStore). The
/// route line is drawn on the map. Pro-gated at the farm card, so anyone reaching
/// this already has stops.
struct TripsView: View {
    var onOpenFarm: (FarmPin) -> Void

    @Environment(\.dismiss) private var dismiss
    @Environment(FarmsStore.self) private var farms
    @Environment(LocationManager.self) private var locationManager
    @Environment(TripStore.self) private var trip

    private var stops: [FarmPin] { trip.stopIds.compactMap { farms.pin(forOsmId: $0) } }
    private var pinIndex: [String: FarmPin] {
        Dictionary(uniqueKeysWithValues: farms.pins.map { ($0.osmId, $0) })
    }

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Text("TRIPS").font(.geist(11, .semibold)).kerning(1.2).foregroundStyle(Color.inkMuted)
                Spacer()
                if !stops.isEmpty {
                    Button { Haptics.tap(); trip.clear() } label: {
                        Text("Clear").font(.geist(14, .semibold)).foregroundStyle(Color.farmGreenMap)
                    }.buttonStyle(.plain)
                }
                Button { dismiss() } label: {
                    Image(systemName: "xmark").font(.system(size: 13, weight: .semibold))
                        .foregroundStyle(Color(hex: 0x6B7280)).frame(width: 32, height: 32)
                        .background(Color(hex: 0xF3F4F6), in: Circle())
                }.buttonStyle(.plain)
            }
            .padding(.horizontal, 16).padding(.top, 16).padding(.bottom, 8)

            if stops.isEmpty {
                VStack(spacing: 10) {
                    Spacer()
                    Image(systemName: "map").font(.system(size: 40)).foregroundStyle(Color.farmGreenMap)
                    Text("No stops yet").font(.geist(18, .bold)).foregroundStyle(Color.ink)
                    Text("Open a farm and tap “Add to trip” to build a route.")
                        .font(.geist(14)).foregroundStyle(Color.inkMuted).multilineTextAlignment(.center)
                    Spacer(); Spacer()
                }
                .frame(maxWidth: .infinity).padding(.horizontal, 40)
            } else {
                if trip.distanceMeters != nil || trip.isRouting {
                    routeSummary.padding(.horizontal, 16).padding(.bottom, 8)
                }
                List {
                    ForEach(Array(stops.enumerated()), id: \.element.osmId) { i, pin in
                        stopRow(i: i, pin: pin)
                    }
                    .onMove { trip.move(from: $0, to: $1) }
                    .onDelete { idx in idx.map { stops[$0].osmId }.forEach(trip.remove) }
                }
                .listStyle(.plain)
                .environment(\.editMode, .constant(.active))

                if stops.count >= 2 {
                    Button {
                        Haptics.tap()
                        trip.optimize(pins: pinIndex, from: locationManager.location)
                        Task { await trip.refreshRoute(pins: pinIndex) }
                    } label: {
                        Text("Best order").font(.geist(15, .semibold)).foregroundStyle(Color.farmGreen)
                            .frame(maxWidth: .infinity).padding(.vertical, 14)
                            .background(Color.farmGreenSoft, in: RoundedRectangle(cornerRadius: 16))
                    }
                    .buttonStyle(.plain).padding(16)
                }
            }
        }
        .background(Color.cream.ignoresSafeArea())
        .task { await trip.refreshRoute(pins: pinIndex) }
        .onChange(of: trip.stopIds) { _, _ in
            Task { await trip.refreshRoute(pins: pinIndex) }
        }
    }

    private var routeSummary: some View {
        HStack(spacing: 16) {
            if trip.isRouting {
                ProgressView().tint(Color.farmGreenMap)
            } else {
                if let d = trip.distanceMeters {
                    label("figure.walk", String(format: "%.0f km", d / 1000))
                }
                if let s = trip.durationSeconds {
                    label("clock", "\(Int(s / 60)) min")
                }
            }
            Spacer()
        }
        .padding(12)
        .background(Color.creamCard, in: RoundedRectangle(cornerRadius: 16))
        .overlay(RoundedRectangle(cornerRadius: 16).stroke(Color.hairline, lineWidth: 1))
    }

    private func label(_ icon: String, _ text: String) -> some View {
        HStack(spacing: 6) {
            Image(systemName: icon).font(.system(size: 13)).foregroundStyle(Color.farmGreenMap)
            Text(text).font(.geist(14, .semibold)).foregroundStyle(Color.ink)
        }
    }

    private func stopRow(i: Int, pin: FarmPin) -> some View {
        HStack(spacing: 12) {
            Text("\(i + 1)").font(.geist(12, .bold)).foregroundStyle(.white)
                .frame(width: 26, height: 26).background(Color.farmGreenMap, in: Circle())
            VStack(alignment: .leading, spacing: 1) {
                Text(pin.name).font(.geist(15, .semibold)).foregroundStyle(Color.ink).lineLimit(1)
                if let city = pin.city { Text(city).font(.geist(12)).foregroundStyle(Color.inkMuted) }
            }
            Spacer()
        }
        .contentShape(Rectangle())
        .onTapGesture { dismiss(); onOpenFarm(pin) }
    }
}
