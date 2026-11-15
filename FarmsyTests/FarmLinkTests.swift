import Foundation
import Testing
@testable import Farmsy

/// A farm page link (F3) is /boerderijwinkels/<place>/<name> on farmsy.app or
/// www.farmsy.app, optionally under /en /fr /de /es. Everything else — /join
/// referrals included — is not a farm link and must be left alone.
struct FarmLinkTests {
    private func parse(_ s: String) -> FarmPathRef? { FarmLink.parse(URL(string: s)!) }
    private let hoeve = FarmPathRef(place: "ede", name: "de-hoeve")

    @Test("a farm page on either host, in any language, with or without a query")
    func farmPages() {
        #expect(parse("https://www.farmsy.app/boerderijwinkels/ede/de-hoeve") == hoeve)
        #expect(parse("https://farmsy.app/boerderijwinkels/ede/de-hoeve") == hoeve)
        #expect(parse("https://www.farmsy.app/en/boerderijwinkels/ede/de-hoeve") == hoeve)
        #expect(parse("https://www.farmsy.app/es/boerderijwinkels/ede/de-hoeve/") == hoeve)
        #expect(parse("https://www.farmsy.app/boerderijwinkels/ede/de-hoeve?utm_source=qr&utm_medium=poster") == hoeve)
    }

    @Test("anything else is not a farm link")
    func notFarmPages() {
        #expect(parse("https://www.farmsy.app/join?ref=ABC123") == nil)
        #expect(parse("https://www.farmsy.app/boerderijwinkels/ede") == nil)
        #expect(parse("https://www.farmsy.app/boerderijwinkels/ede/de-hoeve/extra") == nil)
        #expect(parse("https://www.farmsy.app/nl/boerderijwinkels/ede/de-hoeve") == nil)
        #expect(parse("https://www.farmsy.app/boerderijwinkels/Ede/de-hoeve") == nil)
        #expect(parse("https://www.farmsy.app/boerderijwinkels/ede/de--hoeve") == nil)
        #expect(parse("https://elders.example/boerderijwinkels/ede/de-hoeve") == nil)
        #expect(parse("http://www.farmsy.app/boerderijwinkels/ede/de-hoeve") == nil)
    }

    @Test("a trailing newline in a segment is rejected")
    func trailingNewline() {
        #expect(parse("https://www.farmsy.app/boerderijwinkels/ede/de-hoeve%0A") == nil)
    }

    @Test("source is the link's utm_source, else link")
    func source() {
        #expect(FarmLink.source(of: URL(string: "https://www.farmsy.app/boerderijwinkels/ede/de-hoeve?utm_source=qr")!) == "qr")
        #expect(FarmLink.source(of: URL(string: "https://www.farmsy.app/boerderijwinkels/ede/de-hoeve")!) == "link")
    }

    @Test("the lookup asks the web for this place and name")
    func lookupURL() {
        let url = FarmLink.lookupURL(hoeve, base: URL(string: "https://www.farmsy.app/api")!)
        #expect(url?.absoluteString == "https://www.farmsy.app/api/farm/by-path?place=ede&name=de-hoeve")
    }
}
