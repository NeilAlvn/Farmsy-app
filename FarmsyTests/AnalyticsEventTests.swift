import Foundation
import Testing
@testable import Farmsy

/// One typo in an event name splits a funnel into two bars that never add up.
/// The names are spelled once, here, and this pins the spelling rule.
struct AnalyticsEventTests {

    static let snake = try! NSRegularExpression(pattern: "^[a-z][a-z0-9_]*$")
    static func isSnake(_ s: String) -> Bool {
        snake.firstMatch(in: s, range: NSRange(s.startIndex..., in: s)) != nil
    }

    @Test("every event name is snake_case and unique")
    func eventNames() {
        let names = AnalyticsEvent.allCases.map(\.rawValue)
        #expect(Set(names).count == names.count)
        for n in names { #expect(Self.isSnake(n), "\(n)") }
    }

    @Test("the three new events exist under the agreed names")
    func newEvents() {
        #expect(AnalyticsEvent.tabViewed.rawValue == "tab_viewed")
        #expect(AnalyticsEvent.shoppingItemAdded.rawValue == "shopping_item_added")
        #expect(AnalyticsEvent.authPrompted.rawValue == "auth_prompted")
        #expect(AnalyticsValue.ListSource.farmDetail.rawValue == "farm_detail")
        #expect(AnalyticsValue.ListSource.discover.rawValue == "discover")
        #expect(AnalyticsValue.Trigger.trips.rawValue == "trips")
        #expect(AnalyticsValue.Trigger.restore.rawValue == "restore")
    }
}
