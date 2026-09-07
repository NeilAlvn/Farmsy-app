import Foundation
import Testing
@testable import Farmsy

/// T-1: who has access.
///
/// The same rule lives in two languages: `hasPaidAccess()` on the web and
/// `Profile.hasFullAccess` here (Models.swift). Nothing but this table keeps them
/// agreeing. The web has the SAME eleven rows in `src/lib/subscription.test.ts`;
/// the duplication is the point — two implementations checked against one truth.
/// If a row changes here it changes there, in the same commit.
///
/// Two rows matter more than the rest:
/// - `canceled` with a future end date is TRUE. Someone who cancelled but paid
///   through the end of the month keeps what they paid for. Getting this wrong
///   takes away something a customer already paid for.
/// - `farmer` and `admin` are TRUE with no subscription at all. These are the rows
///   that leak the moment anyone writes a new gate on `subscription_status` alone.
struct AccessRuleTests {

    /// One row of the shared matrix. `end` is relative to now so the table never
    /// goes stale: `.future` is a year ahead, `.past` a year behind.
    struct Row: CustomTestStringConvertible {
        enum End { case none, future, past }
        let role: String
        let founding: Bool
        let status: String
        let end: End
        let expected: Bool

        var testDescription: String {
            "role=\(role) founding=\(founding) status=\(status) end=\(end) → \(expected)"
        }
    }

    /// The eleven rows, in the ticket's order. Same table on the web.
    static let matrix: [Row] = [
        Row(role: "user",   founding: false, status: "active",   end: .none,   expected: true),
        Row(role: "user",   founding: false, status: "trialing", end: .none,   expected: true),
        Row(role: "user",   founding: false, status: "free",     end: .none,   expected: false),
        Row(role: "user",   founding: false, status: "past_due", end: .none,   expected: false),
        Row(role: "user",   founding: false, status: "canceled", end: .future, expected: true),
        Row(role: "user",   founding: false, status: "canceled", end: .past,   expected: false),
        Row(role: "user",   founding: false, status: "canceled", end: .none,   expected: false),
        Row(role: "user",   founding: true,  status: "free",     end: .none,   expected: true),
        Row(role: "farmer", founding: false, status: "free",     end: .none,   expected: true),
        Row(role: "admin",  founding: false, status: "free",     end: .none,   expected: true),
        Row(role: "user",   founding: false, status: "expired",  end: .past,   expected: false),
    ]

    @Test("hasFullAccess matches the shared access matrix", arguments: matrix)
    func accessMatrix(row: Row) {
        let profile = Self.profile(row)
        #expect(profile.hasFullAccess == row.expected)
    }

    @Test("the matrix has exactly the eleven rows the web checks")
    func matrixIsComplete() {
        #expect(Self.matrix.count == 11)
    }

    /// Builds the row the way `/api/profile/status` would deliver it, so the test
    /// exercises the same stored properties the app reads at runtime.
    private static func profile(_ row: Row) -> Profile {
        let end: Date? = switch row.end {
        case .none:   nil
        case .future: Date().addingTimeInterval(365 * 24 * 3600)
        case .past:   Date().addingTimeInterval(-365 * 24 * 3600)
        }
        return Profile(
            subscriptionStatus: row.status,
            subscriptionPlan: nil,
            subscriptionEndDate: end,
            subscriptionSource: nil,
            role: row.role,
            foundingMember: row.founding,
            firstName: nil,
            lastName: nil,
            // P0-3 added this to Profile after this matrix was written. It is not
            // part of the access rule and never should be — preferences are what
            // someone wants to see, access is what they are allowed to see.
            preferences: nil
        )
    }
}
