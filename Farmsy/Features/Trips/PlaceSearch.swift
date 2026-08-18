import SwiftUI
import MapKit

/// Live place autocomplete, biased to NL/BE. Wraps MKLocalSearchCompleter (a
/// delegate-based API) so a SwiftUI view can observe its suggestions.
@MainActor
final class PlaceSearch: NSObject, ObservableObject, MKLocalSearchCompleterDelegate {
    @Published var query = "" { didSet { completer.queryFragment = query } }
    @Published var results: [MKLocalSearchCompletion] = []

    private let completer = MKLocalSearchCompleter()

    override init() {
        super.init()
        completer.delegate = self
        completer.resultTypes = [.address, .pointOfInterest]
        completer.region = MKCoordinateRegion(
            center: CLLocationCoordinate2D(latitude: 51.8, longitude: 4.7),
            span: MKCoordinateSpan(latitudeDelta: 4, longitudeDelta: 4))
    }

    nonisolated func completerDidUpdateResults(_ completer: MKLocalSearchCompleter) {
        let items = completer.results
        Task { @MainActor in self.results = items }
    }

    /// Resolve a chosen completion to real coordinates + a place label.
    func resolve(_ completion: MKLocalSearchCompletion) async -> (CLLocationCoordinate2D, String)? {
        let request = MKLocalSearch.Request(completion: completion)
        guard let item = try? await MKLocalSearch(request: request).start().mapItems.first else { return nil }
        let label = item.placemark.locality ?? completion.title
        return (item.placemark.coordinate, label)
    }
}

/// A search sheet for the trip's starting point: a search bar, live suggestions,
/// and a "use my location" row. Returns the chosen coordinate + label.
struct PlaceSearchSheet: View {
    var onPick: (CLLocationCoordinate2D, String) -> Void
    var onLocate: () -> Void

    @Environment(\.dismiss) private var dismiss
    @StateObject private var search = PlaceSearch()
    @FocusState private var focused: Bool

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Text("STARTING POINT")
                    .font(.geist(11, .semibold)).kerning(1.2).foregroundStyle(Color.inkMuted)
                Spacer()
                Button { dismiss() } label: {
                    Image(systemName: "xmark").font(.system(size: 13, weight: .semibold))
                        .foregroundStyle(Color(hex: 0x6B7280)).frame(width: 32, height: 32)
                        .background(Color(hex: 0xF3F4F6), in: Circle())
                }.buttonStyle(.plain)
            }
            .padding(.horizontal, 16).padding(.top, 16).padding(.bottom, 10)

            // Search bar.
            HStack(spacing: 10) {
                Image(systemName: "magnifyingglass").font(.system(size: 15)).foregroundStyle(Color.inkMuted)
                TextField("Search a town, address or postcode", text: $search.query)
                    .font(.geist(15)).focused($focused).submitLabel(.search)
                if !search.query.isEmpty {
                    Button { search.query = "" } label: {
                        Image(systemName: "xmark.circle.fill").font(.system(size: 16)).foregroundStyle(Color.inkMuted)
                    }.buttonStyle(.plain)
                }
            }
            .padding(.vertical, 13).padding(.horizontal, 16)
            .background(.white, in: Capsule())
            .overlay(Capsule().stroke(Color.hairline, lineWidth: 1))
            .padding(.horizontal, 14)

            ScrollView(showsIndicators: false) {
                VStack(spacing: 0) {
                    // Use my location, or a result. Scrolling drops the keyboard.
                    EmptyView()
                    Button { onLocate(); dismiss() } label: {
                        HStack(spacing: 12) {
                            Image(systemName: "location.fill").font(.system(size: 15)).foregroundStyle(Color.farmGreenMap)
                                .frame(width: 34, height: 34).background(Color.farmGreenMap.opacity(0.12), in: Circle())
                            Text("Use my location").font(.geist(15, .semibold)).foregroundStyle(Color.ink)
                            Spacer()
                        }
                        .padding(.horizontal, 16).padding(.vertical, 12).contentShape(Rectangle())
                    }.buttonStyle(.plain)
                    Divider().padding(.leading, 62)

                    ForEach(search.results, id: \.self) { r in
                        Button {
                            Task {
                                if let (coord, label) = await search.resolve(r) { onPick(coord, label); dismiss() }
                            }
                        } label: {
                            HStack(spacing: 12) {
                                Image(systemName: "mappin.circle").font(.system(size: 16)).foregroundStyle(Color.inkMuted)
                                    .frame(width: 34)
                                VStack(alignment: .leading, spacing: 1) {
                                    Text(r.title).font(.geist(15)).foregroundStyle(Color.ink).lineLimit(1)
                                    if !r.subtitle.isEmpty {
                                        Text(r.subtitle).font(.geist(12)).foregroundStyle(Color.inkMuted).lineLimit(1)
                                    }
                                }
                                Spacer()
                            }
                            .padding(.horizontal, 16).padding(.vertical, 11).contentShape(Rectangle())
                        }.buttonStyle(.plain)
                        Divider().padding(.leading, 62)
                    }
                }
                .padding(.top, 8)
            }
            .scrollDismissesKeyboard(.immediately)
        }
        .background(Color.cream.ignoresSafeArea())
        .onAppear { focused = true }
    }
}
