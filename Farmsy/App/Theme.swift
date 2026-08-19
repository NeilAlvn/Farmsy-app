import SwiftUI
import UIKit

// Farmsy design tokens — mirrors the web app: warm cream background,
// forest-green primary, serif display type.
extension Color {
    init(hex: UInt32) {
        self.init(
            .sRGB,
            red: Double((hex >> 16) & 0xFF) / 255,
            green: Double((hex >> 8) & 0xFF) / 255,
            blue: Double(hex & 0xFF) / 255
        )
    }

    // Values measured out of the web app (docs/DESIGN-SYSTEM.md), converted from
    // oklch to sRGB hex — not eyeballed. Two greens on purpose: `farmGreen` is the
    // brand green for surfaces away from the map; `farmGreenMap` is lighter, for
    // controls sitting *on* the map where the dark green reads as a heavy block.
    static let farmGreen     = Color(hex: 0x234725)  // --primary
    static let farmGreenDeep = Color(hex: 0x18321A)  // darker, for gradients
    static let farmGreenMap  = Color(hex: 0x4E7F54)  // --primary-soft (on-map controls)
    static let farmGreenSoft = Color(hex: 0x234725).opacity(0.10)  // primary tint, no new swatch
    static let cream         = Color(hex: 0xFCFAF6)  // --background, warm off-white
    static let creamCard     = Color(hex: 0xFDFCF9)  // --card, a hair lighter than ground
    static let creamFill     = Color(hex: 0xF3EAD9)  // --cream, marketing blocks only
    static let ink           = Color(hex: 0x15110D)  // --foreground, warm near-black
    static let inkMuted      = Color(hex: 0x68625E)  // --muted-foreground
    static let hairline      = Color(hex: 0xE1DDD8)  // --border
    static let star          = Color(hex: 0xFBBF24)  // amber — a rating reads as stars, not brand
    static let warnRed       = Color(hex: 0xBA2B28)  // --destructive
}

extension Font {
    /// Fraunces — the website's display serif. PostScript names verified
    /// from the bundled TTFs.
    static func display(_ size: CGFloat, weight: Font.Weight = .bold) -> Font {
        let name = switch weight {
        case .regular: "Fraunces-Regular"
        case .medium: "Fraunces-Medium"
        case .semibold: "Fraunces-SemiBold"
        default: "Fraunces-Bold"
        }
        return .custom(name, size: size)
    }

    /// Fraunces Italic — the site's signature emphasis style.
    static func displayItalic(_ size: CGFloat, weight: Font.Weight = .regular) -> Font {
        .custom(weight == .medium ? "Fraunces-MediumItalic" : "Fraunces-Italic", size: size)
    }

    /// Geist — body/UI text, matching the website.
    static func geist(_ size: CGFloat, _ weight: Font.Weight = .regular) -> Font {
        let name = switch weight {
        case .medium: "Geist-Medium"
        case .semibold: "Geist-SemiBold"
        case .bold, .heavy, .black: "Geist-Bold"
        default: "Geist-Regular"
        }
        return .custom(name, size: size)
    }

    static func geistMono(_ size: CGFloat, _ weight: Font.Weight = .regular) -> Font {
        .custom(weight == .medium ? "GeistMono-Medium" : "GeistMono-Regular", size: size)
    }
}

/// Serif headline with the website's signature one-italic-word treatment.
struct DisplayTitle: View {
    let leading: String
    let emphasis: String
    let trailing: String
    var size: CGFloat = 32

    var body: some View {
        // Own the spacing between the parts rather than relying on every caller
        // remembering a trailing space in its string. It survives here only because
        // Swift literals keep their whitespace — the same code on Android read the
        // strings from XML, which strips it, and shipped "farm'sfull story".
        let lead = leading.trimmingCharacters(in: .whitespaces)
        let emph = emphasis.trimmingCharacters(in: .whitespaces)
        let trail = trailing.trimmingCharacters(in: .whitespaces)
        return (Text(lead.isEmpty || emph.isEmpty ? lead : lead + " ")
            .font(.display(size, weight: .medium))
            + Text(emph).font(.displayItalic(size, weight: .medium))
            + Text(trail.isEmpty ? "" : (emph.isEmpty ? trail : " " + trail))
                .font(.display(size, weight: .medium)))
            .foregroundStyle(Color.ink)
            .multilineTextAlignment(.center)
    }
}

/// Bell that keeps ringing — swings on its clapper with a pulsing badge.
struct RingingBell: View {
    var size: CGFloat = 76
    @State private var swing = false
    @State private var pulse = false

    var body: some View {
        ZStack(alignment: .topTrailing) {
            Image(systemName: "bell.fill")
                .font(.system(size: size))
                .foregroundStyle(Color(hex: 0xF5B301))
                .rotationEffect(.degrees(swing ? 14 : -14), anchor: .top)
                .animation(.easeInOut(duration: 0.4).repeatForever(autoreverses: true), value: swing)
            Circle()
                .fill(Color(hex: 0xEF4444))
                .frame(width: size * 0.3, height: size * 0.3)
                .scaleEffect(pulse ? 1.15 : 0.9)
                .offset(x: size * 0.06, y: -size * 0.04)
                .animation(.easeInOut(duration: 0.8).repeatForever(autoreverses: true), value: pulse)
        }
        .onAppear {
            swing = true
            pulse = true
        }
    }
}

enum Haptics {
    static func tap() { UIImpactFeedbackGenerator(style: .light).impactOccurred() }
    static func success() { UINotificationFeedbackGenerator().notificationOccurred(.success) }
    static func warning() { UINotificationFeedbackGenerator().notificationOccurred(.warning) }
}

/// Big rounded primary CTA, like the reference app's Continue buttons.
struct PrimaryButtonStyle: ButtonStyle {
    var fill: Color = .farmGreenMap

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.geist(18, .semibold))
            .foregroundStyle(.white)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 17)
            .background(fill, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
            .scaleEffect(configuration.isPressed ? 0.97 : 1)
            .animation(.spring(duration: 0.25), value: configuration.isPressed)
    }
}

/// Grey secondary pill (the "No" / "Not yet" style).
struct SecondaryButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.geist(18, .semibold))
            .foregroundStyle(Color.ink)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 17)
            .background(Color(hex: 0xE5E4DF), in: Capsule())
            .scaleEffect(configuration.isPressed ? 0.97 : 1)
            .animation(.spring(duration: 0.25), value: configuration.isPressed)
    }
}

struct CardBackground: ViewModifier {
    var padding: CGFloat = 16
    func body(content: Content) -> some View {
        // Web card: --card fill with a 1px hairline, no shadow at rest. Shadows are
        // for things that float (sheets, popovers), not for items in a list.
        content
            .padding(padding)
            .background(Color.creamCard, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: 16, style: .continuous)
                    .stroke(Color.hairline, lineWidth: 1)
            )
    }
}

extension View {
    func card(padding: CGFloat = 16) -> some View { modifier(CardBackground(padding: padding)) }
}

/// A stat cell: a bold green value over a muted caption. Used in the farm card
/// header and elsewhere numbers need a compact, centered treatment.
struct StatTile: View {
    let value: String
    let caption: String
    var body: some View {
        VStack(spacing: 3) {
            Text(value)
                .font(.geist(22, .bold))
                .foregroundStyle(Color.farmGreen)
                .lineLimit(1)
                .minimumScaleFactor(0.6)
            Text(caption)
                .font(.geist(13))
                .foregroundStyle(Color.inkMuted)
        }
        .frame(maxWidth: .infinity)
    }
}

/// Small green uppercase kicker line above serif titles ("PERSONALIZATION" style).
struct Kicker: View {
    let text: String
    var body: some View {
        Text(text.uppercased())
            .font(.geist(14, .semibold))
            .kerning(1.6)
            .foregroundStyle(Color.farmGreen)
    }
}
