import Foundation
import CoreLocation
import Observation

@MainActor
@Observable
final class LocationManager: NSObject, CLLocationManagerDelegate {
    private let manager = CLLocationManager()

    private(set) var location: CLLocation?
    private(set) var status: CLAuthorizationStatus = .notDetermined
    private(set) var isRequesting = false

    override init() {
        super.init()
        manager.delegate = self
        manager.desiredAccuracy = kCLLocationAccuracyHundredMeters
        // No `status = manager.authorizationStatus` here: that is a synchronous
        // round trip to locationd, made on the main thread inside `FarmsyApp.init`,
        // before the first frame. Sampled blocking the launch for the whole
        // window. Core Location calls `locationManagerDidChangeAuthorization`
        // as soon as the manager exists, and that sets `status` without waiting.
    }

    func request() {
        isRequesting = true
        switch status {
        case .notDetermined:
            manager.requestWhenInUseAuthorization()
        case .authorizedWhenInUse, .authorizedAlways:
            manager.requestLocation()
        default:
            isRequesting = false
        }
    }

    nonisolated func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        let newStatus = manager.authorizationStatus
        Task { @MainActor in
            self.status = newStatus
            if newStatus == .authorizedWhenInUse || newStatus == .authorizedAlways {
                self.manager.requestLocation()
            } else if newStatus != .notDetermined {
                self.isRequesting = false
            }
        }
    }

    nonisolated func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        let latest = locations.last
        Task { @MainActor in
            self.location = latest
            self.isRequesting = false
        }
    }

    nonisolated func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        Task { @MainActor in self.isRequesting = false }
    }
}
