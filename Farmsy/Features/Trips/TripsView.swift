import SwiftUI
import CoreLocation
import MapKit

/// The trip planner (Aviah's spec + Neil's screenshots): two tabs — Plan (the
/// local draft) and My trips (saved trips from the DB). Stops come from "Add to
/// trip" on farm cards; the road route + totals come from POST /api/route; the
/// route is drawn on the map. My Trips is free (sign-in only).
struct TripsView: View {
    var onOpenFarm: (FarmPin) -> Void
    /// The farm currently selected/open on the map — its stop row is marked.
    var selectedOsmId: String? = nil
    /// The sheet's height — dropped to half when a saved trip is opened so the
    /// route is visible on the map above the sheet.
    @Binding var detent: PresentationDetent

    @Environment(\.dismiss) private var dismiss
    @Environment(FarmsStore.self) private var farms
    @Environment(LocationManager.self) private var locationManager
    @Environment(SessionStore.self) private var session
    @Environment(TripStore.self) private var trip
    @Environment(\.shell) private var shell

    @State private var tab: Tab = .plan
    @State private var naming = false
    @State private var tripName = ""
    @State private var armedDelete: String?
    @State private var reorderNote: String?
    @State private var showOriginSearch = false
    @State private var showShoppingList = false
    /// R5 corridor chips read the same served catalogue as the shopping list
    /// (GET /api/shopping/items) — one runtime source, no bundled table.
    @State private var catalogue = ShoppingItems.shared

    enum Tab { case plan, mine }

    private var stops: [FarmPin] { trip.stopIds.compactMap { farms.pin(forOsmId: $0) } }
    /// A route needs at least two points — either a starting point + one farm, or
    /// two farms.
    private var canRoute: Bool { (trip.originCoord != nil && stops.count >= 1) || stops.count >= 2 }
    /// Task 4: looking is free, ordering stops and drawing the road is Plus. A
    /// single stop is a plain directions request either way, so it stays free.
    private var isLocked: Bool { TripStore.isRouteLocked(hasFullAccess: session.hasFullAccess, stopCount: stops.count) }
    private var pinIndex: [String: FarmPin] {
        Dictionary(farms.pins.map { ($0.osmId, $0) }, uniquingKeysWith: { a, _ in a })
    }
    private let SLOTS = 8
    /// Visible rows before the plan list scrolls internally.
    private let PLAN_SLOTS = 5
    private let ROW_HEIGHT: CGFloat = 52
    private let MINE_SLOTS = 6
    /// Recommendation carousel: two cover-photo cards per page.
    private let REC_CARD_HEIGHT: CGFloat = 156

    var body: some View {
        VStack(spacing: 0) {
            header
            tabs.padding(.horizontal, 14).padding(.top, 4)
            // The middle flexes inside a ScrollView so it can never push the header
            // off the top or the actions off the bottom when the sheet is short;
            // the actions stay pinned below it.
            if tab == .plan {
                planTab
                planActions
                    .padding(.horizontal, 14)
                    .padding(.top, 14)
                    .padding(.bottom, 12)
                    .background(Color.cream)
            } else {
                mineTab
            }
        }
        .background(Color.cream.ignoresSafeArea())
        .task { await trip.refreshRoute(pins: pinIndex, locked: isLocked) }
        .task { if let uid { await trip.loadTrips(userId: uid) } }
        .task { await catalogue.loadIfNeeded() }
        .onChange(of: trip.stopIds) { _, _ in Task { await trip.refreshRoute(pins: pinIndex, locked: isLocked) } }
        // Fix round 1 #1: buying Plus mid-session flips `isLocked` without the
        // stops changing, so the route fetch above never re-fires on its own —
        // the sheet would stay unblurred but with a nil/never-fetched route.
        // Re-key on the lock flag too.
        .onChange(of: isLocked) { _, _ in Task { await trip.refreshRoute(pins: pinIndex, locked: isLocked) } }
        .alert("Name your trip", isPresented: $naming) {
            TextField("My weekend trip", text: $tripName)
            Button("Save") { Task { await save() } }
            Button("Cancel", role: .cancel) {}
        }
        .sheet(isPresented: $showOriginSearch) {
            PlaceSearchSheet(
                onPick: { coord, label in
                    trip.setOrigin(coord, label: label)   // flies via fitToken
                    Task { await trip.refreshRoute(pins: pinIndex, locked: isLocked) }
                    withAnimation { detent = .fraction(0.5) }
                },
                onLocate: { Task { await locate() } })
            .presentationDetents([.large])
            .presentationDragIndicator(.visible)
        }
        .sheet(isPresented: $showShoppingList) {
            // Plans from the trip's starting point, falling back to where the
            // phone is — a list is worth nothing without somewhere to drive from.
            if let from = trip.originCoord ?? locationManager.location?.coordinate {
                ShoppingListSheet(origin: from)
                    .presentationDetents([.large])
                    .presentationDragIndicator(.visible)
            } else {
                needsOrigin
            }
        }
    }

    /// No starting point and no location permission: say which, and offer the
    /// one control that fixes it.
    private var needsOrigin: some View {
        VStack(spacing: 14) {
            Image(systemName: "location.slash").font(.system(size: 28)).foregroundStyle(Color.inkMuted)
            Text("Choose a starting point first")
                .font(.ui(16, .bold)).foregroundStyle(Color.ink)
            Text("A shopping trip is planned from somewhere — pick a starting point, or let the app use your location.")
                .font(.ui(14)).foregroundStyle(Color.inkMuted)
                .multilineTextAlignment(.center)
            Button("Choose a starting point") {
                Haptics.tap(); showShoppingList = false; showOriginSearch = true
            }
            .font(.ui(14, .semibold)).foregroundStyle(Color.farmGreen)
            .buttonStyle(.plain)
        }
        .padding(28)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color.cream.ignoresSafeArea())
        .presentationDetents([.medium])
    }

    private var uid: String? { session.session?.user.id.uuidString.lowercased() }

    /// The sheet dragged down to its small detent — hide the list so the header
    /// (TRIP PLANNER + close) and the actions stay on screen.
    private var collapsed: Bool { detent == .fraction(0.5) }

    /// A quiet "drag up" cue (arrow only) for the collapsed state.
    private var dragUpHint: some View {
        Image(systemName: "chevron.up")
            .font(.system(size: 13, weight: .bold))
            .foregroundStyle(Color.inkMuted.opacity(0.8))
    }

    // MARK: - Header + tabs

    private var header: some View {
        HStack {
            Text("TRIP PLANNER")
                .font(.ui(11, .semibold)).kerning(1.2).foregroundStyle(Color.inkMuted)
            Spacer()
            circleButton("xmark") { dismiss() }
        }
        .padding(.horizontal, 16).padding(.top, 16).padding(.bottom, 8)
    }

    private var tabs: some View {
        HStack(spacing: 10) {
            tabButton("Plan a trip", .plan)
            tabButton("My trips", .mine)
        }
    }

    private func tabButton(_ title: LocalizedStringKey, _ t: Tab) -> some View {
        Button { Haptics.tap(); tab = t } label: {
            Text(title).font(.ui(15, .bold))
                .foregroundStyle(tab == t ? .white : Color.ink)
                .frame(maxWidth: .infinity).padding(.vertical, 13)
                .background(tab == t ? Color.farmGreenMap : .white, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                .overlay(RoundedRectangle(cornerRadius: 14).stroke(tab == t ? Color.clear : Color.hairline, lineWidth: 1))
        }
        .buttonStyle(.plain)
    }

    private func circleButton(_ icon: String, _ action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: icon).font(.system(size: 13, weight: .semibold))
                .foregroundStyle(Color(hex: 0x6B7280)).frame(width: 32, height: 32)
                .background(Color(hex: 0xF3F4F6), in: Circle())
        }.buttonStyle(.plain)
    }

    // MARK: - When the drive is (R7)

    /// The day the planner is working against.
    ///
    /// The picker always shows a day, but the trip only *has* one once somebody
    /// chooses it: `trip.tripDate` stays nil until then, and a trip saved with
    /// nil reopens against the planner's default rather than claiming it was
    /// planned for a day in the past.
    private var chosenDay: Binding<Date> {
        Binding(
            get: { TripEndpoints.day(from: trip.tripDate)
                    ?? TripEndpoints.day(from: TripEndpoints.nextSaturday())
                    ?? Date() },
            set: { trip.setWhen(date: TripEndpoints.dayString($0), departMinutes: trip.departMinutes) }
        )
    }

    /// A weekday and date, in the reader's own language.
    private var dayLabel: String {
        let day = TripEndpoints.day(from: trip.tripDate) ?? Date()
        let f = DateFormatter()
        f.calendar = TripEndpoints.calendar
        f.timeZone = TripEndpoints.calendar.timeZone
        f.setLocalizedDateFormatFromTemplate("EEEEd MMM")
        return f.string(from: day)
    }

    /// R7 · which day this drive is for. A farm open on the Saturday you saved
    /// and shut on the one you are planning is the single most useful thing the
    /// planner can tell someone, and it cannot say it without a day.
    private var dayRow: some View {
        HStack(spacing: 10) {
            Image(systemName: "calendar").font(.system(size: 15)).foregroundStyle(Color.inkMuted)

            Text(trip.tripDate == nil ? String(localized: "Choose a day") : dayLabel)
                .font(.ui(15))
                .foregroundStyle(trip.tripDate == nil ? Color.inkMuted : Color.ink)
                .lineLimit(1)

            Spacer()

            if trip.tripDate != nil {
                Image(systemName: "xmark.circle.fill").font(.system(size: 16))
                    .foregroundStyle(Color.inkMuted)
                    .onTapGesture { Haptics.tap(); trip.clearWhen() }
                    .accessibilityLabel(String(localized: "Clear the day"))
            }

            // The system picker rather than a hand-rolled calendar: it already
            // knows the reader's language, their first day of the week and how
            // they expect a date to be typed.
            //
            // No upper bound and a lower bound of today — a trip planned for
            // last Tuesday is a trip nobody is going to drive, and the whole
            // point of the day is what it says about opening hours ahead.
            DatePicker("", selection: chosenDay, in: Date()..., displayedComponents: .date)
                .labelsHidden()
                .datePickerStyle(.compact)
                .accessibilityLabel(String(localized: "Day of the trip"))
        }
        .padding(.vertical, 8).padding(.horizontal, 16)
        .background(.white, in: Capsule())
        .overlay(Capsule().stroke(Color.hairline, lineWidth: 1))
    }

    // MARK: - Plan tab

    private var planTab: some View {
        VStack(spacing: 14) {
            // Starting point — a search bar that opens live place suggestions.
            Button { Haptics.tap(); showOriginSearch = true } label: {
                HStack(spacing: 10) {
                    Image(systemName: "magnifyingglass").font(.system(size: 15)).foregroundStyle(Color.inkMuted)
                    Text(trip.originLabel?.isEmpty == false ? trip.originLabel! : String(localized: "Choose a starting point"))
                        .font(.ui(15))
                        .foregroundStyle(trip.originLabel?.isEmpty == false ? Color.ink : Color.inkMuted)
                        .lineLimit(1)
                    Spacer()
                    if trip.originLabel?.isEmpty == false {
                        Image(systemName: "xmark.circle.fill").font(.system(size: 16)).foregroundStyle(Color.inkMuted)
                            .onTapGesture { Haptics.tap(); trip.clearOrigin() }
                    } else {
                        Image(systemName: "location.circle").font(.system(size: 20)).foregroundStyle(Color.farmGreenMap)
                    }
                }
                .padding(.vertical, 14).padding(.horizontal, 16)
                .background(.white, in: Capsule())
                .overlay(Capsule().stroke(Color.hairline, lineWidth: 1))
            }
            .buttonStyle(.plain)

            dayRow

            // Fill the trip from a shopping list rather than pin by pin. Sits
            // under the starting point because it needs one, and because that is
            // the order the trip is built in.
            if !collapsed {
                Button {
                    Haptics.tap()
                    showShoppingList = true
                } label: {
                    HStack(spacing: 8) {
                        Image(systemName: "list.bullet").font(.system(size: 13, weight: .semibold))
                        Text("Shop from a list").font(.ui(14, .semibold))
                        if !trip.wantedProducts.isEmpty {
                            // verbatim: a bare count is a number, not a phrase to translate.
                            Text(verbatim: "\(trip.wantedProducts.count)")
                                .font(.ui(12, .bold)).foregroundStyle(.white)
                                .padding(.horizontal, 7).padding(.vertical, 2)
                                .background(Color.farmGreenMap, in: Capsule())
                        }
                        Spacer()
                        Image(systemName: "chevron.right").font(.system(size: 12, weight: .semibold))
                            .foregroundStyle(Color.inkMuted)
                    }
                    .foregroundStyle(Color.ink)
                    .padding(.vertical, 12).padding(.horizontal, 16)
                    .background(.white, in: RoundedRectangle(cornerRadius: 16))
                    .overlay(RoundedRectangle(cornerRadius: 16).stroke(Color.hairline, lineWidth: 1))
                }
                .buttonStyle(.plain)
            }

            if collapsed {
                // On the small detent the list is tucked away entirely to keep the
                // header (TRIP PLANNER + close) and the actions on screen. The hint
                // is an overlay, so it cues "drag up" without taking layout height.
                Spacer(minLength: 0).overlay { dragUpHint }
            } else {
                // Trip overview — grows to fill so its bottom sits one 14pt margin
                // above the mode line; its list scrolls internally when the stops
                // outgrow the box.
                let rows = max(stops.count, PLAN_SLOTS)
                VStack(alignment: .leading, spacing: 0) {
                    Text("Trip overview").font(.ui(16, .bold)).foregroundStyle(Color.ink)
                        .padding(14)
                    Divider()
                    ScrollView(showsIndicators: true) {
                        VStack(spacing: 0) {
                            if isLocked {
                                // Task 4: the first stop is a real, interactive row like
                                // any other. Farmsy ordering the rest and drawing the
                                // road between them is the Plus work, so those rows —
                                // real, not placeholders — are blurred behind one unlock
                                // row rather than hidden outright (never a padlock on an
                                // empty screen).
                                filledRow(i: 0, pin: stops[0])
                                if rows > 1 { Divider().padding(.leading, 60) }
                                lockedStopsBlock(rows: rows)
                            } else {
                                ForEach(0..<rows, id: \.self) { i in
                                    if i < stops.count { filledRow(i: i, pin: stops[i]) } else { emptyRow(i: i) }
                                    if i < rows - 1 { Divider().padding(.leading, 60) }
                                }
                            }
                        }
                        // R4 · farms on the way. Shown once there's a road to measure
                        // against. Fed the FILTERED pin set (farms.filtered) so it never
                        // offers a farm the map is hiding; adding a farm re-routes, so it
                        // re-positions against the new road for free.
                        if trip.routeLine.count >= 2 {
                            Divider()
                            // R6 · the corridor answers "open when you pass" for the day
                            // picked in `dayRow` above (Luuk's R7); departure defaults to
                            // 10:00. resolvedDayMon/resolvedDepartMinutes bridge his model.
                            RouteCorridorView(
                                road: trip.routeLine,
                                tripKm: trip.distanceMeters.map { $0 / 1000 },
                                filteredFarms: farms.filtered,
                                stopIds: Set(trip.stopIds),
                                dayMon: trip.resolvedDayMon,
                                departMinutes: trip.resolvedDepartMinutes,
                                durationSeconds: trip.durationSeconds,
                                produceByOsm: farms.produceByOsm,
                                chips: catalogue.items,
                                selectedProducts: trip.selectedProducts,
                                onToggleProduct: { trip.toggleCorridorProduct($0) },
                                onOpenFarm: { pin in onOpenFarm(pin) },
                                // toggle changes stopIds, and .onChange(of: trip.stopIds)
                                // above re-routes — so the farm re-sorts against the new
                                // road without an explicit refresh here.
                                onAddStop: { pin in trip.toggle(pin.osmId) }
                            )
                            .padding(14)
                        }
                    }
                }
                .frame(maxHeight: .infinity)
                .background(.white, in: RoundedRectangle(cornerRadius: 16))
                .overlay(RoundedRectangle(cornerRadius: 16).stroke(Color.hairline, lineWidth: 1))
            }

            if !collapsed, let reorderNote {
                Text(reorderNote).font(.ui(12)).foregroundStyle(Color.farmGreenMap)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }

            // "Best order" only when expanded — at the small detent every point of
            // height matters for keeping the header and actions on screen.
            // Fix round 1 #3: reordering IS the paid work, so a locked trip opens
            // Plus instead of running it for free.
            if !collapsed, stops.count >= 3 {
                Button { if isLocked { openPlusFromSample() } else { reorder() } } label: {
                    Text("Best order").font(.ui(14, .semibold)).foregroundStyle(Color.farmGreen)
                }.buttonStyle(.plain)
            }
        }
        .padding(.horizontal, 14)
        .padding(.top, 14)
        // No bottom padding here — the gap to the mode line is owned entirely by
        // planActions' top padding so it matches the 14pt rhythm everywhere else.
        .padding(.bottom, 0)
    }

    /// Pinned below the scroll: travel mode, the live totals, and the trip
    /// actions — the controls Luuk flagged as getting buried under the stop list.
    private var planActions: some View {
        VStack(spacing: 10) {
            modeSelector
            // Task 4: the totals are the full ordered route's answer — Plus
            // work — so the line is left out entirely for a locked trip rather
            // than shown with a blurred/fake distance.
            if !isLocked { totalsBar }
            HStack(spacing: 10) {
                outlineButton("Save trip", icon: "bookmark") {
                    if isLocked { openPlusFromSample(); return }
                    naming = true; tripName = ""
                }
                .disabled(stops.isEmpty)
                filledButton("Show route", icon: "location.north.fill") {
                    if isLocked { openPlusFromSample(); return }
                    Task {
                        await trip.refreshRoute(pins: pinIndex, locked: isLocked)
                        // Fly the map to frame the whole route once it's computed,
                        // so the user sees where the trip actually goes.
                        trip.requestFit()
                    }
                    dismiss()
                }
                .disabled(!canRoute)
            }
            outlineButton("Open in Google Maps", icon: "arrow.up.forward.square") {
                if isLocked { openPlusFromSample(); return }
                openGoogleMaps()
            }
            .disabled(stops.isEmpty)
        }
    }

    private var modeSelector: some View {
        HStack(spacing: 8) {
            ForEach(TravelMode.allCases, id: \.self) { m in
                Button {
                    Haptics.tap()
                    trip.setMode(m)
                    Task { await trip.refreshRoute(pins: pinIndex, locked: isLocked) }
                } label: {
                    HStack(spacing: 6) {
                        Image(systemName: m.icon).font(.system(size: 13, weight: .semibold))
                        Text(m.label).font(.ui(13, .semibold))
                    }
                    .foregroundStyle(trip.mode == m ? .white : Color.ink)
                    .frame(maxWidth: .infinity).padding(.vertical, 9)
                    .background(trip.mode == m ? Color.farmGreenMap : .white,
                                in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                    .overlay(RoundedRectangle(cornerRadius: 12)
                        .stroke(trip.mode == m ? Color.clear : Color.hairline, lineWidth: 1))
                }
                .buttonStyle(.plain)
            }
        }
    }


    private func filledRow(i: Int, pin: FarmPin) -> some View {
        let selected = pin.osmId == selectedOsmId
        return HStack(spacing: 12) {
            Text("\(i + 1)").font(.ui(12, .bold)).foregroundStyle(.white)
                .frame(width: 28, height: 28).background(Color.farmGreenMap, in: Circle())
            VStack(alignment: .leading, spacing: 1) {
                Text(pin.name).font(.ui(15, .semibold)).foregroundStyle(Color.ink).lineLimit(1)
                Text(legLabel(i)).font(.ui(12)).foregroundStyle(Color.inkMuted)
            }
            Spacer()
            // A check marks the farm currently selected/open on the map.
            if selected {
                Image(systemName: "checkmark.circle.fill")
                    .font(.system(size: 15)).foregroundStyle(Color.farmGreenMap)
            }
            Button { Haptics.tap(); trip.remove(pin.osmId) } label: {
                Image(systemName: "xmark").font(.system(size: 12, weight: .semibold)).foregroundStyle(Color.inkMuted)
            }.buttonStyle(.plain)
        }
        .padding(.horizontal, 14).padding(.vertical, 11)
        .background(selected ? Color.farmGreenMap.opacity(0.08) : .clear)
        .contentShape(Rectangle())
        .onTapGesture { dismiss(); onOpenFarm(pin) }
    }

    private func emptyRow(i: Int) -> some View {
        HStack(spacing: 12) {
            Text("\(i + 1)").font(.ui(12, .bold)).foregroundStyle(Color.inkMuted.opacity(0.6))
                .frame(width: 28, height: 28)
                .overlay(Circle().strokeBorder(Color(hex: 0xE5E7EB), style: StrokeStyle(lineWidth: 1.5, dash: [3])))
            VStack(alignment: .leading, spacing: 2) {
                Text("Pick a farm on the map").font(.ui(15)).foregroundStyle(Color.inkMuted)
                Text("—").font(.ui(12)).foregroundStyle(Color.inkMuted.opacity(0.5))
            }
            Spacer()
        }
        .padding(.horizontal, 14).padding(.vertical, 11)
    }

    /// Task 4's free sample: every row from the second stop on, drawn for real
    /// and blurred — never a padlock on an empty screen — with one unlock row
    /// underneath. The whole blurred block is also a tap target, so both paths
    /// go through the same `openPlusFromSample()` as the button.
    private func lockedStopsBlock(rows: Int) -> some View {
        // Fix round 1 #4: the sentence + button used to sit below the blurred
        // rows, inside the stop list's own scroll area — invisible until it
        // was scrolled. Overlay them centred on the blur instead (a ZStack,
        // not a sibling below it), with a floor height that fits the sentence
        // (up to 3 lines at nl/de length) plus the button, so both are visible
        // without scrolling.
        ZStack {
            VStack(spacing: 0) {
                ForEach(1..<rows, id: \.self) { i in
                    if i < stops.count { filledRow(i: i, pin: stops[i]) } else { emptyRow(i: i) }
                    if i < rows - 1 { Divider().padding(.leading, 60) }
                }
            }
            .blur(radius: 7)
            .allowsHitTesting(false)
            .accessibilityHidden(true)
            .contentShape(Rectangle())
            .onTapGesture { openPlusFromSample() }
            .accessibilityLabel(String(localized: "Unlock the route"))
            .accessibilityAddTraits(.isButton)

            // The real, readable control: a soft surface-coloured backing
            // keeps the sentence legible over the blur; only the stop rows
            // above are hidden from accessibility, so this text and button
            // read normally.
            VStack(spacing: 8) {
                Text(String(localized: "Your route has \(stops.count) stops. Farmsy Plus orders them and draws the road."))
                    .font(.ui(13)).foregroundStyle(Color.ink)
                    .multilineTextAlignment(.center)
                Button(String(localized: "Unlock the route")) { openPlusFromSample() }
                    .font(.ui(14, .semibold)).foregroundStyle(.white)
                    .padding(.vertical, 10).padding(.horizontal, 20)
                    .background(Color.farmGreenMap, in: Capsule())
                    .buttonStyle(.plain)
            }
            .padding(16)
            .background(Color.surface.opacity(0.6), in: RoundedRectangle(cornerRadius: 16, style: .continuous))
            .padding(.horizontal, 24)
        }
        .frame(minHeight: 168)
    }

    /// The one place free users open Plus from the route preview — the unlock
    /// button, the blurred stop block, and the locked Save/Show route/Maps
    /// actions all call this, so `paywall_viewed` only ever fires from one spot.
    private func openPlusFromSample() {
        Haptics.tap()
        Observability.capture(.paywallViewed,
                              [AnalyticsProp.trigger: AnalyticsValue.Trigger.routePreview.rawValue])
        shell.openPlus()
    }

    private var totalsBar: some View {
        HStack(spacing: 10) {
            Image(systemName: "point.topleft.down.to.point.bottomright.curvepath")
                .font(.system(size: 15)).foregroundStyle(Color.farmGreenMap)
            if !canRoute {
                Text("Add farms to see time and distance").font(.ui(14)).foregroundStyle(Color.inkMuted)
            } else if trip.isRouting {
                Text("Finding the road…").font(.ui(14)).foregroundStyle(Color.inkMuted)
            } else {
                Text(totalsText).font(.ui(14, .semibold)).foregroundStyle(Color.ink)
            }
            Spacer()
            Image(systemName: trip.mode.icon).font(.system(size: 15)).foregroundStyle(Color.inkMuted.opacity(0.5))
        }
        .padding(14)
        .background(Color(hex: 0xF3F6F2), in: RoundedRectangle(cornerRadius: 16))
    }

    private var totalsText: String {
        let km = (trip.distanceMeters ?? 0) / 1000
        let mins = Int((trip.durationSeconds ?? 0) / 60)
        let time = mins >= 60 ? "\(mins / 60) h \(mins % 60)" : "\(mins) min"
        return String(format: "~%.0f km · %@%@", km, time, trip.onRoads ? "" : " (est.)")
    }

    /// The drive to stop i from the previous stop (or the start).
    private func legLabel(_ i: Int) -> String {
        let coords = ([trip.originCoord].compactMap { $0 }) + stops.map(\.coordinate)
        let idx = trip.originCoord != nil ? i + 1 : i
        guard idx >= 1, idx < coords.count else { return "Start" }
        let km = TripGeometry.haversineKm(coords[idx - 1], coords[idx])
        return String(format: "~%.0f km · %d min", km, trip.mode.minutes(km: km))
    }

    // MARK: - My trips tab

    @ViewBuilder
    private var mineTab: some View {
        if !session.isAuthenticated {
            gate(String(localized: "Sign in to view your trips"))
        } else {
            // My Trips is free now (was Pro). Any signed-in account sees their saved
            // trips; the `hasFullAccess` gate was removed with the un-gating. The
            // signed-out gate above stays.
            // Only the numbered list scrolls (its own box). On the small detent the
            // list is tucked away behind a hint (like the trip overview); fully open,
            // the box grows to fill so the recommendations sit at the frame's end.
            VStack(spacing: 14) {
                // Draft banner.
                Text(trip.stopIds.isEmpty ? String(localized: "No trip in progress")
                     : String(localized: "A draft with \(trip.stopIds.count) stops is waiting"))
                    .font(.ui(14)).foregroundStyle(Color.inkMuted)
                    .frame(maxWidth: .infinity).padding(.vertical, 14)
                    .overlay(RoundedRectangle(cornerRadius: 16).strokeBorder(Color(hex: 0xE5E7EB), style: StrokeStyle(lineWidth: 1, dash: [4])))
                    .contentShape(Rectangle())
                    .onTapGesture { if !trip.stopIds.isEmpty { tab = .plan } }

                if collapsed {
                    // Small detent: hide the list so the header stays visible. The
                    // drag-up hint sits between the draft and the carousel as a fixed
                    // element (not a flexing spacer) so it stays put during the drag.
                    // Negative padding trims the parent VStack's 14pt spacing; the
                    // bottom is pulled in further so the arrow sits close to the
                    // carousel below it.
                    dragUpHint
                        .frame(maxWidth: .infinity)
                        .padding(.top, -6)
                        .padding(.bottom, -12)
                    TripRecommendations(cardHeight: REC_CARD_HEIGHT,
                                        onOpenFarm: { pin in dismiss(); onOpenFarm(pin) })
                } else {
                    let rows = max(trip.savedTrips.count, MINE_SLOTS)
                    VStack(alignment: .leading, spacing: 0) {
                        HStack {
                            Text("My Trips").font(.ui(16, .bold)).foregroundStyle(Color.ink)
                            Spacer()
                            Text("\(trip.savedTrips.count) saved").font(.ui(13)).foregroundStyle(Color.inkMuted)
                        }.padding(14)
                        Divider()
                        ScrollView(showsIndicators: true) {
                            VStack(spacing: 0) {
                                ForEach(0..<rows, id: \.self) { i in
                                    if i < trip.savedTrips.count { savedRow(i: i, t: trip.savedTrips[i]) }
                                    else { savedEmptyRow(i: i) }
                                    if i < rows - 1 { Divider().padding(.leading, 60) }
                                }
                            }
                        }
                    }
                    .background(.white, in: RoundedRectangle(cornerRadius: 16))
                    .overlay(RoundedRectangle(cornerRadius: 16).stroke(Color.hairline, lineWidth: 1))
                    // Grow to fill so the carousel below lands at the frame's end.
                    .frame(maxHeight: .infinity)

                    // Discovery carousel, pinned at the bottom.
                    TripRecommendations(cardHeight: REC_CARD_HEIGHT,
                                        onOpenFarm: { pin in dismiss(); onOpenFarm(pin) })
                }
            }
            .padding(14)
        }
    }


    private func savedRow(i: Int, t: SavedTrip) -> some View {
        HStack(spacing: 12) {
            Text("\(i + 1)").font(.ui(12, .bold)).foregroundStyle(.white)
                .frame(width: 28, height: 28).background(Color.farmGreenMap, in: Circle())
            VStack(alignment: .leading, spacing: 1) {
                Text(t.name).font(.ui(15, .bold)).foregroundStyle(Color.ink).lineLimit(1)
                Text("\(t.stopCount) \(t.stopCount == 1 ? String(localized: "farm") : String(localized: "farms"))\(dateLabel(t.updatedAt))")
                    .font(.ui(12)).foregroundStyle(Color.inkMuted)
            }
            Spacer()
            Button {
                if armedDelete == t.id { Task { if let uid { await trip.deleteTrip(t.id, userId: uid) } } }
                else { armedDelete = t.id }
            } label: {
                Image(systemName: armedDelete == t.id ? "trash.fill" : "xmark")
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(armedDelete == t.id ? Color.warnRed : Color.inkMuted)
            }.buttonStyle(.plain)
        }
        .padding(.horizontal, 14).padding(.vertical, 11)
        .contentShape(Rectangle())
        .onTapGesture {
            Task { await trip.openTrip(t.id) }
            tab = .plan
            withAnimation { detent = .fraction(0.5) }
        }
    }

    private func savedEmptyRow(i: Int) -> some View {
        HStack(spacing: 12) {
            Text("\(i + 1)").font(.ui(12, .bold)).foregroundStyle(Color.inkMuted.opacity(0.6))
                .frame(width: 28, height: 28)
                .overlay(Circle().strokeBorder(Color(hex: 0xE5E7EB), style: StrokeStyle(lineWidth: 1.5, dash: [3])))
            Text("Plan a trip to fill this").font(.ui(15)).foregroundStyle(Color.inkMuted)
            Spacer()
            Image(systemName: "plus").font(.system(size: 15, weight: .semibold)).foregroundStyle(Color.farmGreenMap)
        }
        .padding(.horizontal, 14).padding(.vertical, 11)
        .contentShape(Rectangle())
        .onTapGesture { tab = .plan }
    }

    private func dateLabel(_ iso: String?) -> String {
        guard let iso, let d = ISO8601DateFormatter().date(from: iso) else { return "" }
        let f = DateFormatter(); f.dateFormat = "MMM d"
        return " · " + f.string(from: d)
    }

    private func gate(_ text: String) -> some View {
        VStack(spacing: 12) {
            Spacer()
            Image(systemName: "lock.fill").font(.system(size: 34)).foregroundStyle(Color.farmGreenMap)
            Text(text).font(.ui(15)).foregroundStyle(Color.inkMuted).multilineTextAlignment(.center)
            Spacer(); Spacer()
        }
        .frame(maxWidth: .infinity, minHeight: 260).padding(.horizontal, 40)
    }

    // MARK: - Shared

    private func outlineButton(_ title: LocalizedStringKey, icon: String, _ action: @escaping () -> Void) -> some View {
        Button { Haptics.tap(); action() } label: {
            HStack(spacing: 8) {
                Image(systemName: icon).font(.system(size: 13, weight: .semibold))
                Text(title).font(.ui(14, .semibold))
            }
            .foregroundStyle(Color.ink).frame(maxWidth: .infinity).padding(.vertical, 14)
            .background(.white, in: RoundedRectangle(cornerRadius: 16))
            .overlay(RoundedRectangle(cornerRadius: 16).stroke(Color.hairline, lineWidth: 1))
        }.buttonStyle(.plain)
    }

    private func filledButton(_ title: LocalizedStringKey, icon: String, _ action: @escaping () -> Void) -> some View {
        Button { Haptics.tap(); action() } label: {
            HStack(spacing: 8) {
                Image(systemName: icon).font(.system(size: 13, weight: .semibold))
                Text(title).font(.ui(14, .semibold))
            }
            .foregroundStyle(.white).frame(maxWidth: .infinity).padding(.vertical, 14)
            .background(Color.farmGreenMap.opacity(canRoute ? 1 : 0.5), in: RoundedRectangle(cornerRadius: 16))
        }.buttonStyle(.plain)
    }

    // MARK: - Actions

    private func locate() async {
        locationManager.request()
        guard let loc = locationManager.location else { return }
        let label = (try? await CLGeocoder().reverseGeocodeLocation(
            CLLocation(latitude: loc.coordinate.latitude, longitude: loc.coordinate.longitude)).first?.locality)
            ?? String(format: "%.3f, %.3f", loc.coordinate.latitude, loc.coordinate.longitude)
        trip.setOrigin(loc.coordinate, label: label ?? "Here")
        await trip.refreshRoute(pins: pinIndex, locked: isLocked)
        withAnimation { detent = .fraction(0.5) }
    }

    private func reorder() {
        Haptics.tap()
        let saved = trip.optimise(pins: pinIndex)
        reorderNote = saved >= 0.5 ? String(localized: "Reordered · about \(Int(saved)) km shorter")
                                    : String(localized: "Already the shortest order")
        Task { await trip.refreshRoute(pins: pinIndex, locked: isLocked) }
    }

    private func save() async {
        guard let uid, !tripName.trimmingCharacters(in: .whitespaces).isEmpty else { return }
        await trip.save(name: tripName.trimmingCharacters(in: .whitespaces), userId: uid, pins: pinIndex)
        Haptics.success()
        // Start fresh: wipe the draft stops and the starting point so the Plan tab
        // is empty for the next trip.
        trip.clear()
        trip.clearOrigin()
        tab = .mine
    }

    /// Hand the planned drive to Google Maps.
    ///
    /// The endpoints are the same ones a save writes, so what opens in Maps is
    /// what reopening the trip would draw. Before R8 this built its own URL from
    /// the origin and the stop list alone, which meant a drive planned towards a
    /// destination opened as a drive ending at the last farm — the place the
    /// user said they were going was simply absent. The rules now live in
    /// `MapsHandoff`, alongside the web's `mapsHandoff.ts`.
    private func openGoogleMaps() {
        let url = MapsHandoff.googleMapsURL(.init(
            origin: trip.originCoord,
            stops: stops.map(\.coordinate),
            destination: trip.destinationCoord,
            travelMode: trip.mode.googleMode
        ))
        if let url { UIApplication.shared.open(url) }
    }
}

// MARK: - Recommendations near you (bottom of My Trips)

/// "Recommendations near you" — Aviah's spec. Built off the same inputs as the
/// onboarding shelf (nearby photo'd farms + batch teasers), with the *selection*
/// isolated in `select()` so a real recommendation source can replace it without
/// touching the view. Anchors on the user's location, falling back to a trip they
/// care about; excludes anything already planned or hearted; renders nothing when
/// there is nothing sensible nearby.
struct TripRecommendations: View {
    var cardHeight: CGFloat = 150
    var onOpenFarm: (FarmPin) -> Void

    @Environment(FarmsStore.self) private var farms
    @Environment(LocationManager.self) private var locationManager
    @Environment(FavoritesStore.self) private var favorites
    @Environment(SessionStore.self) private var session
    @Environment(TripStore.self) private var trip

    @State private var shown: [FarmPin] = []
    @State private var built = false
    @State private var page = 0

    /// User's location first; then a trip they care about — the draft's origin,
    /// its stops, or (for someone with saved trips but no draft and no location)
    /// the centroid of every farm they've already planned. Only nil when there's
    /// genuinely nothing to anchor on, and then the section renders nothing.
    private var anchor: CLLocationCoordinate2D? {
        if let loc = locationManager.location { return loc.coordinate }
        if let origin = trip.originCoord { return origin }
        if let c = centroid(of: trip.stopIds) { return c }
        return centroid(of: trip.plannedFarmIds)
    }

    private func centroid<S: Sequence>(of ids: S) -> CLLocationCoordinate2D? where S.Element == String {
        let coords = ids.compactMap { farms.pin(forOsmId: $0)?.coordinate }
        guard !coords.isEmpty else { return nil }
        let lat = coords.map(\.latitude).reduce(0, +) / Double(coords.count)
        let lng = coords.map(\.longitude).reduce(0, +) / Double(coords.count)
        return CLLocationCoordinate2D(latitude: lat, longitude: lng)
    }

    /// Whether we have a real location/trip anchor. Drives the header wording
    /// ("near you" vs a plain "recommendation") and which pool we pick from.
    private var hasAnchor: Bool { anchor != nil }

    /// The one place "which farms are recommended" lives. With an anchor: nearby
    /// photo'd farms. Without one (no location, no trip yet): random photo'd farms
    /// so the shelf still has something. Either way, minus anything already planned
    /// or hearted, described ones mixed among the rest. Swap this when a real
    /// recommendation source exists.
    private func select() -> [FarmPin] {
        let excluded = trip.plannedFarmIds
            .union(trip.stopIds)
            .union(favorites.osmIds)
        let pool: [FarmPin]
        if let anchor {
            pool = farms.nearbyWithImages(near: anchor, radiusKm: 100)
                .filter { !excluded.contains($0.osmId) }
        } else {
            pool = farms.pins
                .filter { $0.image != nil && !excluded.contains($0.osmId) }
                .shuffled()
        }
        let described = pool.filter { !(farms.galleries[$0.osmId]?.isEmpty ?? true) }
        let plain = pool.filter { farms.galleries[$0.osmId]?.isEmpty ?? true }
        let picked = Array(described.prefix(6)) + Array(plain.prefix(6))
        return Array(picked.shuffled().prefix(6))
    }

    private func images(for pin: FarmPin) -> [String] {
        if let gallery = farms.galleries[pin.osmId], !gallery.isEmpty { return gallery }
        if let cover = pin.image { return [cover] }
        return []
    }

    private var buildKey: String {
        "\(farms.pins.count)-\(farms.galleriesLoaded)-\(trip.plannedFarmIds.count)-\(anchor?.latitude ?? 0)-\(anchor?.longitude ?? 0)"
    }

    private func load() async {
        await farms.loadGalleriesIfNeeded()
        guard !farms.pins.isEmpty else { return }
        shown = select()
        page = 0
        built = true
    }

    /// The recommendations split into pages of two — the carousel shows one page
    /// (two cards side by side) at a time.
    private var pages: [[FarmPin]] {
        stride(from: 0, to: shown.count, by: 2).map {
            Array(shown[$0..<min($0 + 2, shown.count)])
        }
    }

    var body: some View {
        Group {
            // Nothing sensible to show → render nothing (no empty state).
            if built && shown.isEmpty {
                EmptyView()
            } else {
                VStack(alignment: .leading, spacing: 10) {
                    Text(hasAnchor ? "RECOMMENDATIONS NEAR YOU" : "RECOMMENDATION")
                        .font(.ui(11, .semibold)).kerning(1.2)
                        .foregroundStyle(Color.inkMuted)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.top, 6)

                    if !built {
                        HStack(spacing: 12) {
                            SkeletonBox(cornerRadius: 16).frame(height: cardHeight)
                            SkeletonBox(cornerRadius: 16).frame(height: cardHeight)
                        }
                    } else {
                        // A horizontal pager: two cards per page, no vertical scroll.
                        TabView(selection: $page) {
                            ForEach(Array(pages.enumerated()), id: \.offset) { idx, pair in
                                HStack(spacing: 12) {
                                    ForEach(pair) { pin in recCard(pin) }
                                    if pair.count == 1 { Color.clear.frame(maxWidth: .infinity) }
                                }
                                .padding(.horizontal, 1)
                                .tag(idx)
                            }
                        }
                        .tabViewStyle(.page(indexDisplayMode: .never))
                        .frame(height: cardHeight)

                        if pages.count > 1 { pager }
                    }
                }
            }
        }
        .task(id: buildKey) { await load() }
    }

    /// `< • • • >` — arrows step a page, dots mark the current one.
    private var pager: some View {
        HStack(spacing: 14) {
            Button { withAnimation { page = max(0, page - 1) } } label: {
                Image(systemName: "chevron.left").font(.system(size: 13, weight: .bold))
                    .foregroundStyle(page == 0 ? Color.inkMuted.opacity(0.35) : Color.ink)
            }.buttonStyle(.plain).disabled(page == 0)

            HStack(spacing: 6) {
                ForEach(0..<pages.count, id: \.self) { i in
                    Circle()
                        .fill(i == page ? Color.farmGreenMap : Color.inkMuted.opacity(0.3))
                        .frame(width: 7, height: 7)
                }
            }

            Button { withAnimation { page = min(pages.count - 1, page + 1) } } label: {
                Image(systemName: "chevron.right").font(.system(size: 13, weight: .bold))
                    .foregroundStyle(page >= pages.count - 1 ? Color.inkMuted.opacity(0.35) : Color.ink)
            }.buttonStyle(.plain).disabled(page >= pages.count - 1)
        }
        .frame(maxWidth: .infinity)
        .padding(.top, 2)
    }

    /// A recommended farm as a cover-photo card — the "fresh from the farm" look:
    /// full-bleed photo, a save heart, category tags, and the name + city over a
    /// legibility scrim.
    private func recCard(_ pin: FarmPin) -> some View {
        let saved = favorites.isSaved(pin.osmId)
        let tags = Array(pin.categories.prefix(2))
        return ZStack(alignment: .bottomLeading) {
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .fill(Color(hex: 0xEDE7DD))
                .overlay {
                    if let img = images(for: pin).first, let url = URL(string: img) {
                        AsyncImage(url: url) { phase in
                            if case .success(let image) = phase {
                                image.resizable().scaledToFill()
                            } else { SkeletonBox(cornerRadius: 16) }
                        }
                    } else {
                        Image(systemName: "leaf").font(.system(size: 28)).foregroundStyle(Color.inkMuted.opacity(0.5))
                    }
                }
                .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))

            LinearGradient(colors: [.black.opacity(0.0), .black.opacity(0.65)],
                           startPoint: .center, endPoint: .bottom)
                .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))

            VStack(alignment: .leading, spacing: 4) {
                Text(pin.name).font(.ui(14, .bold)).foregroundStyle(.white).lineLimit(2)
                if let city = pin.city {
                    Text(city).font(.ui(12, .medium)).foregroundStyle(.white.opacity(0.85)).lineLimit(1)
                }
                if !tags.isEmpty {
                    HStack(spacing: 5) {
                        ForEach(tags) { cat in
                            Text(cat.label.uppercased())
                                .font(.ui(9, .bold)).kerning(0.4)
                                .foregroundStyle(.white)
                                .padding(.vertical, 3).padding(.horizontal, 7)
                                .background(.white.opacity(0.22), in: Capsule())
                        }
                    }
                }
            }
            .padding(12)
        }
        .frame(height: cardHeight)
        .overlay(RoundedRectangle(cornerRadius: 16, style: .continuous).stroke(Color.hairline, lineWidth: 1))
        .overlay(alignment: .topTrailing) {
            Button {
                guard let uid = session.session?.user.id else { return }
                Task { await favorites.toggle(pin.osmId, userId: uid) }
            } label: {
                Image(systemName: saved ? "heart.fill" : "heart")
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(saved ? Color.warnRed : Color.ink)
                    .frame(width: 30, height: 30)
                    .background(.white.opacity(0.9), in: Circle())
                    .contentShape(Circle())
            }
            .buttonStyle(.plain)
            .padding(8)
        }
        .contentShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
        .tapCard(excludeTopTrailing: 44) { onOpenFarm(pin) }
    }
}
