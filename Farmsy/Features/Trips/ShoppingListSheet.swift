import SwiftUI
import CoreLocation

/// "I need eggs, milk and potatoes — where do I drive?"
///
/// The map answers where a farm is. This answers which farms to visit for a
/// list, and in what order. The result is ordinary trip stops, so the route
/// line, the totals, the corridor list and the Google Maps hand-off all pick it
/// up unchanged.
///
/// A picker rather than a text field. The words that mean a product are served
/// (GET /api/shopping/items) because guessing them locally is what sends
/// somebody asking for lamb to a beef farm; picking from the list means the
/// terms are the same ones the website matches on.
///
/// The matching itself runs on pins already in memory, so an empty result means
/// "no farm near you says it sells this", not "we could not look".
struct ShoppingListSheet: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(FarmsStore.self) private var farms
    @Environment(TripStore.self) private var trip

    /// The starting point to plan from — the trip's origin, or the device.
    let origin: CLLocationCoordinate2D

    @State private var catalogue = ShoppingItems.shared
    @State private var plan: ShoppingPlanner.Plan?
    @State private var isPlanning = false

    private var picked: [ShoppingItem] {
        trip.wantedProducts.compactMap { catalogue.item(id: $0) }
    }

    var body: some View {
        VStack(spacing: 0) {
            header
            ScrollView(showsIndicators: false) {
                VStack(alignment: .leading, spacing: 16) {
                    if catalogue.items.isEmpty {
                        loading
                    } else {
                        picker
                        if let plan { result(plan) }
                    }
                }
                .padding(.horizontal, 16)
                .padding(.top, 14)
                .padding(.bottom, 20)
            }
            actions
                .padding(.horizontal, 16)
                .padding(.bottom, 14)
        }
        .background(Color.cream.ignoresSafeArea())
        .task { await catalogue.loadIfNeeded() }
        // The produce text is what every match is made against — without it the
        // sheet would honestly report that nothing is sold anywhere.
        .task { await farms.loadFlagsIfNeeded() }
    }

    // MARK: - Pieces

    private var header: some View {
        HStack {
            VStack(alignment: .leading, spacing: 2) {
                Text("Shopping list").font(.ui(18, .bold)).foregroundStyle(Color.ink)
                Text("We pick the farms, you drive once")
                    .font(.ui(12)).foregroundStyle(Color.inkMuted)
            }
            Spacer()
            Button { Haptics.tap(); dismiss() } label: {
                Image(systemName: "xmark")
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundStyle(Color(hex: 0x6B7280))
                    .frame(width: 32, height: 32)
                    .background(Color(hex: 0xF3F4F6), in: Circle())
            }
            .buttonStyle(.plain)
        }
        .padding(.horizontal, 16)
        .padding(.top, 16)
    }

    @ViewBuilder
    private var loading: some View {
        if catalogue.loadFailed {
            Text("The product list couldn't be loaded. Check your connection and try again.")
                .font(.ui(14)).foregroundStyle(Color.inkMuted)
        } else {
            ProgressView().tint(Color.farmGreen).frame(maxWidth: .infinity).padding(.vertical, 24)
        }
    }

    private var picker: some View {
        FlowRow(spacing: 8) {
            ForEach(catalogue.items) { item in
                let on = trip.wantedProducts.contains(item.id)
                Button {
                    Haptics.tap()
                    trip.toggleProduct(item.id)
                    plan = nil          // the answer belonged to the old list
                } label: {
                    HStack(spacing: 6) {
                        if on {
                            Image(systemName: "checkmark").font(.system(size: 10, weight: .bold))
                        }
                        Text(item.label).font(.ui(14, .medium))
                    }
                    .foregroundStyle(on ? .white : Color.ink)
                    .padding(.vertical, 8).padding(.horizontal, 12)
                    .background(on ? Color.farmGreenMap : .white, in: Capsule())
                    .overlay(Capsule().stroke(on ? Color.clear : Color.hairline, lineWidth: 1))
                }
                .buttonStyle(.plain)
            }
        }
    }

    @ViewBuilder
    private func result(_ plan: ShoppingPlanner.Plan) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            if plan.isEmpty {
                Text("No farm within 25 km lists any of this.")
                    .font(.ui(14)).foregroundStyle(Color.inkMuted)
            } else {
                ForEach(Array(plan.picks.enumerated()), id: \.element.osmId) { i, pick in
                    HStack(alignment: .top, spacing: 12) {
                        Text(verbatim: "\(i + 1)")
                            .font(.ui(13, .bold)).foregroundStyle(.white)
                            .frame(width: 24, height: 24)
                            .background(Color.farmGreenMap, in: Circle())
                        VStack(alignment: .leading, spacing: 2) {
                            Text(farms.pin(forOsmId: pick.osmId)?.name ?? pick.osmId)
                                .font(.ui(15, .semibold)).foregroundStyle(Color.ink).lineLimit(1)
                            Text(labels(pick.covers).joined(separator: " · "))
                                .font(.ui(13)).foregroundStyle(Color.inkMuted).lineLimit(2)
                        }
                        Spacer(minLength: 0)
                    }
                }
            }
            // Said out loud rather than quietly dropped: a list that half worked
            // is only useful if you know which half.
            if !plan.missing.isEmpty {
                Text("Not found nearby: \(labels(plan.missing).joined(separator: ", "))")
                    .font(.ui(13, .medium)).foregroundStyle(Color.inkMuted)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .card()
    }

    private var actions: some View {
        VStack(spacing: 10) {
            Button {
                Haptics.tap()
                if let plan, !plan.isEmpty { addToTrip(plan) } else { buildPlan() }
            } label: {
                HStack(spacing: 8) {
                    if isPlanning {
                        ProgressView().tint(.white)
                    } else {
                        Image(systemName: plan?.isEmpty == false ? "plus" : "sparkles")
                            .font(.system(size: 13, weight: .semibold))
                    }
                    // Two Text views rather than a ternary inside one: each
                    // literal is then its own catalog key, which a ternary of
                    // interpolated literals is not guaranteed to be.
                    if let plan, !plan.isEmpty {
                        Text("Add \(plan.picks.count) stops to my trip").font(.ui(14, .semibold))
                    } else {
                        Text("Plan my trip").font(.ui(14, .semibold))
                    }
                }
                .foregroundStyle(.white).frame(maxWidth: .infinity).padding(.vertical, 14)
                .background(Color.farmGreenMap.opacity(canPlan ? 1 : 0.5),
                            in: RoundedRectangle(cornerRadius: 16))
            }
            .buttonStyle(.plain)
            .disabled(!canPlan)

            if !trip.wantedProducts.isEmpty {
                Button("Clear list") { Haptics.tap(); trip.clearProducts(); plan = nil }
                    .font(.ui(13, .medium)).foregroundStyle(Color.inkMuted)
                    .buttonStyle(.plain)
            }
        }
    }

    private var canPlan: Bool { !trip.wantedProducts.isEmpty && !isPlanning }

    // MARK: - Actions

    /// Ids back to the words on the chips, so the answer is read in the same
    /// language it was asked in.
    private func labels(_ ids: [String]) -> [String] {
        ids.map { catalogue.item(id: $0)?.label ?? $0 }
    }

    private func buildPlan() {
        isPlanning = true
        // The flags payload may still be arriving on a cold open; waiting for it
        // is the difference between "nothing sells eggs" and "we have not looked".
        Task {
            await farms.loadFlagsIfNeeded()
            await catalogue.loadIfNeeded()
            let candidates = farms.pins.map {
                ShoppingPlanner.Candidate(osmId: $0.osmId,
                                          coord: $0.coordinate,
                                          sells: farms.produceByOsm[$0.osmId] ?? "")
            }
            plan = ShoppingPlanner.plan(wanted: picked, farms: candidates, origin: origin)
            isPlanning = false
        }
    }

    private func addToTrip(_ plan: ShoppingPlanner.Plan) {
        trip.addStops(plan.picks.map(\.osmId))
        trip.requestFit()
        Haptics.success()
        dismiss()
    }
}

// MARK: - Flow layout

/// Chips that wrap. `Layout` rather than a stack of rows so the wrapping is the
/// real width, not a guess at how many fit.
struct FlowRow: Layout {
    var spacing: CGFloat = 8

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let maxWidth = proposal.width ?? .infinity
        var x: CGFloat = 0, y: CGFloat = 0, lineHeight: CGFloat = 0
        for view in subviews {
            let size = view.sizeThatFits(.unspecified)
            if x > 0 && x + size.width > maxWidth {
                x = 0; y += lineHeight + spacing; lineHeight = 0
            }
            x += size.width + spacing
            lineHeight = max(lineHeight, size.height)
        }
        return CGSize(width: maxWidth == .infinity ? x : maxWidth, height: y + lineHeight)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        var x = bounds.minX, y = bounds.minY, lineHeight: CGFloat = 0
        for view in subviews {
            let size = view.sizeThatFits(.unspecified)
            if x > bounds.minX && x + size.width > bounds.maxX {
                x = bounds.minX; y += lineHeight + spacing; lineHeight = 0
            }
            view.place(at: CGPoint(x: x, y: y), proposal: ProposedViewSize(size))
            x += size.width + spacing
            lineHeight = max(lineHeight, size.height)
        }
    }
}
