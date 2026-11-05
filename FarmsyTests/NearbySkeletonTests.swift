import Testing
import SwiftUI
@testable import Farmsy

/// Home is one vertical column, so the widest child sets the width of the whole
/// page. Three fixed-width placeholder tiles are wider than any iPhone; if the
/// row asks for that width, every card above and below it is laid out past both
/// screen edges until the real tiles arrive, and then snaps back.
@MainActor
struct NearbySkeletonTests {

    @Test("the placeholder row never asks for more width than it is offered")
    func staysWithinOfferedWidth() {
        let host = UIHostingController(rootView: NearbySkeleton())
        let offered = CGSize(width: 320, height: CGFloat.greatestFiniteMagnitude)
        #expect(host.sizeThatFits(in: offered).width <= 320)
    }
}
