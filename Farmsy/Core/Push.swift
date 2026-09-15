import Foundation
import Observation
import UIKit
import UserNotifications

// Push notifications: the phone tells the server how to reach it, and a
// notification tap opens the farm it is about.
//
// WHAT THIS DOES NOT DECIDE
// Whether a person gets alerts at all is the server's call (the two alert
// crons check the membership, the opt-out and the cooldown, then email and
// push). This file only keeps the device token registered for the signed-in
// account, and removed on sign-out. No key on the server yet means the token
// is stored and nothing is sent, which is the right order to build it in.
//
// WHEN THE TOKEN IS SENT
// APNs hands the app a token on every launch that calls
// registerForRemoteNotifications, and it can change. It is posted when it
// differs from the last one posted for this account, so a launch costs no
// request in the common case.

@MainActor
@Observable
final class PushRegistrar {
    static let shared = PushRegistrar()

    /// A farm a notification tap asked for. The shell opens it and clears it.
    var pendingOsmId: String?

    private var deviceToken: String?
    private var accessToken: String?
    private var userId: String?
    private let defaults = UserDefaults.standard
    private let sentKey = "pushTokenSent"   // "<userId>|<token>"

    private init() {}

    /// Called whenever the session changes and when the app comes to the
    /// foreground. Asks the system for a token when permission was granted
    /// (the onboarding "notify" step or Settings), which arrives in
    /// `received(_:)`.
    func sync(userId: String?, accessToken: String?) {
        self.userId = userId
        self.accessToken = accessToken
        guard userId != nil else { return }
        Task {
            let settings = await UNUserNotificationCenter.current().notificationSettings()
            guard settings.authorizationStatus == .authorized || settings.authorizationStatus == .provisional else { return }
            UIApplication.shared.registerForRemoteNotifications()
            await post()
        }
    }

    /// From the app delegate, with APNs' token bytes.
    func received(_ data: Data) {
        deviceToken = data.map { String(format: "%02x", $0) }.joined()
        Task { await post() }
    }

    /// On sign-out, before the session is dropped: the phone must stop
    /// buzzing for an account it no longer holds.
    func unregister(accessToken: String) async {
        guard let deviceToken else { return }
        var request = URLRequest(url: Backend.webAPI.appending(path: "profile").appending(path: "push-token"))
        request.httpMethod = "DELETE"
        request.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try? JSONSerialization.data(withJSONObject: ["token": deviceToken])
        _ = try? await URLSession.shared.data(for: request)
        defaults.removeObject(forKey: sentKey)
    }

    private func post() async {
        guard let deviceToken, let userId, let accessToken else { return }
        let stamp = "\(userId)|\(deviceToken)"
        guard defaults.string(forKey: sentKey) != stamp else { return }
        var request = URLRequest(url: Backend.webAPI.appending(path: "profile").appending(path: "push-token"))
        request.httpMethod = "POST"
        request.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try? JSONSerialization.data(withJSONObject: [
            "platform": "ios",
            "token": deviceToken,
            "locale": Locale.current.language.languageCode?.identifier ?? "en",
        ])
        guard let (_, resp) = try? await URLSession.shared.data(for: request),
              (resp as? HTTPURLResponse)?.statusCode == 200 else { return }
        defaults.set(stamp, forKey: sentKey)
    }
}

/// The UIKit hooks push needs: the token callback, and what a tap opens.
final class AppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate {
    func application(_ application: UIApplication,
                     didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {
        UNUserNotificationCenter.current().delegate = self
        return true
    }

    func application(_ application: UIApplication, didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data) {
        Task { @MainActor in PushRegistrar.shared.received(deviceToken) }
    }

    func application(_ application: UIApplication, didFailToRegisterForRemoteNotificationsWithError error: Error) {
        // The simulator, or no entitlement: nothing to register, nothing to do.
    }

    /// Show the banner even while the app is open: an alert about a farm you
    /// are not looking at is still news.
    func userNotificationCenter(_ center: UNUserNotificationCenter, willPresent notification: UNNotification) async -> UNNotificationPresentationOptions {
        [.banner, .sound]
    }

    /// A tap opens the farm the alert was about (`osmId` rides in the payload).
    func userNotificationCenter(_ center: UNUserNotificationCenter, didReceive response: UNNotificationResponse) async {
        if let osmId = response.notification.request.content.userInfo["osmId"] as? String {
            await MainActor.run { PushRegistrar.shared.pendingOsmId = osmId }
        }
    }
}
