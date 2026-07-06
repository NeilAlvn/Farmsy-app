import XCTest

/// Walks every screen of the app and attaches a screenshot of each frame.
/// Run with: -only-testing:FarmsyUITests/ScreenshotTour
/// Export the frames afterwards with `xcresulttool export attachments`.
final class ScreenshotTour: XCTestCase {

    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    private func shot(_ app: XCUIApplication, _ name: String) {
        let attachment = XCTAttachment(screenshot: app.screenshot())
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }

    /// The onboarding pager keeps every step mounted, so existence is always
    /// true — what matters is whether the element is on-screen and tappable.
    @discardableResult
    private func waitHittable(_ element: XCUIElement, timeout: TimeInterval = 10) -> Bool {
        let deadline = Date().addingTimeInterval(timeout)
        while Date() < deadline {
            if element.exists && element.isHittable { return true }
            Thread.sleep(forTimeInterval: 0.3)
        }
        return false
    }

    /// Splash → all onboarding steps → sign-up and log-in screens.
    func testAOnboardingTour() throws {
        let app = XCUIApplication()
        app.launchArguments = ["--reset-onboarding", "--hold-splash"]
        app.launch()

        // --hold-splash keeps the composed splash up for ~7s; land inside it.
        Thread.sleep(forTimeInterval: 3.0)
        shot(app, "01-splash")

        let continueCategory = app.buttons["continue-category"]
        XCTAssertTrue(waitHittable(continueCategory, timeout: 12))
        shot(app, "02-onboarding-categories")
        continueCategory.tap()

        let utrecht = app.buttons["Utrecht"]
        XCTAssertTrue(waitHittable(utrecht))
        utrecht.tap()
        Thread.sleep(forTimeInterval: 0.4)
        shot(app, "03-onboarding-location")
        app.buttons["continue-location"].tap()

        // Finding interstitial (auto-advances after the pins load).
        Thread.sleep(forTimeInterval: 0.6)
        shot(app, "04-onboarding-finding")

        // Counts grid becomes tappable when the interstitial finishes.
        let continueCounts = app.buttons["continue-counts"]
        XCTAssertTrue(waitHittable(continueCounts, timeout: 40))
        Thread.sleep(forTimeInterval: 0.8)
        shot(app, "05-onboarding-real-counts")
        continueCounts.tap()

        let continueValue = app.buttons["continue-value"]
        XCTAssertTrue(waitHittable(continueValue))
        Thread.sleep(forTimeInterval: 0.5)
        shot(app, "06-onboarding-value")
        continueValue.tap()

        let noButton = app.buttons["No"]
        XCTAssertTrue(waitHittable(noButton))
        Thread.sleep(forTimeInterval: 0.5)
        shot(app, "07-onboarding-notifications")
        noButton.tap()

        let skip = app.buttons["Skip"]
        XCTAssertTrue(waitHittable(skip))
        Thread.sleep(forTimeInterval: 0.5)
        shot(app, "08-onboarding-referral")
        skip.tap()

        let createAccount = app.buttons["Create account"]
        XCTAssertTrue(createAccount.waitForExistence(timeout: 5))
        shot(app, "09-auth-signup")

        app.buttons["Log in"].firstMatch.tap()
        Thread.sleep(forTimeInterval: 0.6)
        shot(app, "10-auth-login")
    }

    /// Signed-in screens via the debug demo session: map, list, locked farm
    /// detail, saved, settings.
    func testBMainAppTour() throws {
        let app = XCUIApplication()
        app.launchArguments = ["--demo-session"]
        app.launch()

        // Wait out the splash, then let the real pins land on the map.
        let mapToggle = app.buttons["toggle-view"]
        XCTAssertTrue(mapToggle.waitForExistence(timeout: 12))
        // Wait until the pins RPC finishes ("N farms" replaces the loading
        // chip), then give the annotation render time to settle.
        let countChip = app.staticTexts.matching(
            NSPredicate(format: "label ENDSWITH ' farms'")
        ).firstMatch
        XCTAssertTrue(countChip.waitForExistence(timeout: 60))
        Thread.sleep(forTimeInterval: 10)
        shot(app, "11-map-real-pins")

        // Discover tab: feed of random farms with photos.
        app.buttons["tab-discover"].tap()
        Thread.sleep(forTimeInterval: 8)
        shot(app, "12-discover")
        app.buttons["tab-map"].tap()
        Thread.sleep(forTimeInterval: 1.0)

        mapToggle.tap()
        let firstCard = app.buttons.matching(identifier: "farm-card").firstMatch
        XCTAssertTrue(firstCard.waitForExistence(timeout: 10))
        Thread.sleep(forTimeInterval: 0.8)
        shot(app, "13-list-view")

        firstCard.tap()
        Thread.sleep(forTimeInterval: 1.5)
        shot(app, "14-farm-detail-locked")
        app.navigationBars.buttons.firstMatch.tap()

        app.buttons["tab-saved"].tap()
        Thread.sleep(forTimeInterval: 0.8)
        shot(app, "15-saved-empty")

        app.buttons["tab-settings"].tap()
        Thread.sleep(forTimeInterval: 1.0)
        shot(app, "16-settings")
    }
}
