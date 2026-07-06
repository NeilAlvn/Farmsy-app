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

    static let farmGreen     = Color(hex: 0x3F5E3A)
    static let farmGreenDeep = Color(hex: 0x2E4A2B)
    static let farmGreenSoft = Color(hex: 0x3F5E3A).opacity(0.14)
    static let cream         = Color(hex: 0xF8F6F0)
    static let creamCard     = Color(hex: 0xF1EEE5)
    static let ink           = Color(hex: 0x16211B)
    static let inkMuted      = Color(hex: 0x6B7280)
    static let warnRed       = Color(hex: 0xDC2626)
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
        (Text(leading).font(.display(size, weight: .medium))
            + Text(emphasis).font(.displayItalic(size, weight: .medium))
            + Text(trailing).font(.display(size, weight: .medium)))
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
    var fill: Color = .farmGreen

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.geist(18, .semibold))
            .foregroundStyle(.white)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 17)
            .background(fill, in: RoundedRectangle(cornerRadius: 22, style: .continuous))
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
        content
            .padding(padding)
            .background(Color.creamCard, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
    }
}

extension View {
    func card(padding: CGFloat = 16) -> some View { modifier(CardBackground(padding: padding)) }
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
