import SwiftUI
import UIKit

// Farmsy design tokens.
//
// Structure (type scale, spacing, radii, elevation, motion) is the Vision Tech
// base system shared with Nime (`nime-app/src/theme/base.ts`). Only the brand
// slots differ: Farmsy keeps its own greens and cream. Two colour families per
// semantic on purpose — `positive/warning/critical` are text-safe, the `vivid*`
// set is for fills only (dots, rings, bars) and never for text.
extension Color {
    init(hex: UInt32) {
        self.init(
            .sRGB,
            red: Double((hex >> 16) & 0xFF) / 255,
            green: Double((hex >> 8) & 0xFF) / 255,
            blue: Double(hex & 0xFF) / 255
        )
    }

    // Brand slots
    static let farmGreen     = Color(hex: 0x234725)  // accent
    static let farmGreenDeep = Color(hex: 0x18321A)  // darker, for gradients
    static let farmGreenMap  = Color(hex: 0x4E7F54)  // lighter green for on-map controls
    static let farmGreenSoft = Color(hex: 0xE3ECE0)  // accentSoft: one soft card per screen at most
    static let vivid         = Color(hex: 0x9BE15D)  // accentVivid: fills only, never text
    static let cream         = Color(hex: 0xFCFAF6)  // canvas
    static let surface       = Color.white           // cards, rows, the tab pill
    static let creamCard     = Color.white           // legacy name for `surface`
    static let creamFill     = Color(hex: 0xF3EAD9)  // tile: search field, image wells, skeletons
    static let ink           = Color(hex: 0x15110D)
    static let inkMuted      = Color(hex: 0x68625E)
    static let inkFaint      = Color(hex: 0x8A837D)
    static let hairline      = Color(hex: 0xE1DDD8)
    static let star          = Color(hex: 0xFBBF24)  // a rating reads as stars, not brand

    // Semantic, text-safe (WCAG AA on cream and white)
    static let positive = Color(hex: 0x137A4A)
    static let warning  = Color(hex: 0x9A5B00)
    static let critical = Color(hex: 0xBA2B28)
    static let warnRed  = Color(hex: 0xBA2B28)  // legacy name for `critical`
    static let positiveSoft = Color(hex: 0xE4F3EA)
    static let warningSoft  = Color(hex: 0xFBEFD9)
    static let criticalSoft = Color(hex: 0xFBE5E5)

    // Semantic, fills only (open dot, availability ring, closed pin)
    static let vividPositive = Color(hex: 0x9BE15D)
    static let vividWarning  = Color(hex: 0xFF8A00)
    static let vividCritical = Color(hex: 0xFF2D46)
}

/// Spacing scale, base 4. Gutter 16, card padding 20, section 24–32.
enum Space {
    static let s1: CGFloat = 4, s2: CGFloat = 8, s3: CGFloat = 12, s4: CGFloat = 16
    static let s5: CGFloat = 20, s6: CGFloat = 24, s8: CGFloat = 32, s12: CGFloat = 48
}

enum Radius {
    static let pill: CGFloat = 999, card: CGFloat = 20, tile: CGFloat = 16
    static let input: CGFloat = 14, sheet: CGFloat = 28, thumb: CGFloat = 12
}

/// The bottom inset a scrolling tab screen reserves so its last row clears the
/// floating tab pill (64pt pill + 12pt gap + breathing room).
enum TabBarInset {
    static let height: CGFloat = 64
    static let content: CGFloat = height + 12 + 24
}

extension Font {
    /// Plus Jakarta Sans — the one family. Weight resolves to a file, never to a
    /// synthetic weight, so bold is real bold on both platforms.
    static func ui(_ size: CGFloat, _ weight: Font.Weight = .regular) -> Font {
        let name = switch weight {
        case .medium: "PlusJakartaSans-Medium"
        case .semibold: "PlusJakartaSans-SemiBold"
        case .bold, .heavy, .black: "PlusJakartaSans-Bold"
        default: "PlusJakartaSans-Regular"
        }
        return .custom(name, size: size)
    }
}

enum TextRole {
    case display, title, heading, subheading, body, bodySm, caption, label

    /// Size / weight pairs of the scale: display 40/700, title 28/700,
    /// heading 22/600, subheading 17/600, body 16, bodySm 15, caption 13, label 12/600.
    var font: Font {
        switch self {
        case .display: .ui(40, .bold)
        case .title: .ui(28, .bold)
        case .heading: .ui(22, .semibold)
        case .subheading: .ui(17, .semibold)
        case .body: .ui(16)
        case .bodySm: .ui(15)
        case .caption: .ui(13)
        case .label: .ui(12, .semibold)
        }
    }

    var tracking: CGFloat {
        switch self {
        case .display: -0.8
        case .title: -0.4
        case .heading: -0.2
        case .label: 0.3
        default: 0
        }
    }

    /// Extra leading beyond the font's own, so 16/24 body reads as 24.
    var lineSpacing: CGFloat {
        switch self {
        case .body: 4
        case .bodySm, .caption, .label: 2
        default: 0
        }
    }
}

extension View {
    /// Type role + tone in one call: `Text("…").role(.heading)`.
    func role(_ role: TextRole, _ tone: Color = .ink) -> some View {
        font(role.font)
            .tracking(role.tracking)
            .lineSpacing(role.lineSpacing)
            .foregroundStyle(tone)
    }
}

/// Headline. Kept for its call sites; the italic-word treatment went with the
/// serif, so the whole line now renders as one bold title.
struct DisplayTitle: View {
    let text: String
    var size: CGFloat = 32

    init(leading: String, emphasis: String, trailing: String, size: CGFloat = 32) {
        text = leading + emphasis + trailing
        self.size = size
    }

    /// One localized sentence; `*asterisks*` from the old markup are stripped.
    init(_ marked: String, size: CGFloat = 32) {
        text = marked.replacingOccurrences(of: "*", with: "")
        self.size = size
    }

    var body: some View {
        Text(text)
            .font(.ui(size, .bold))
            .tracking(-0.4)
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

/// Primary pill: ink on white, 56pt. Green is the app's answer, so it is not
/// also every button. `fill` stays for the few on-map callers that pass one.
struct PrimaryButtonStyle: ButtonStyle {
    var fill: Color = .ink

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.ui(17, .semibold))
            .foregroundStyle(.white)
            .frame(maxWidth: .infinity, minHeight: 56)
            .padding(.horizontal, Space.s6)
            .background(fill, in: Capsule())
            .scaleEffect(configuration.isPressed ? 0.97 : 1)
            .animation(.spring(duration: 0.25), value: configuration.isPressed)
    }
}

/// Secondary pill: tile fill, ink label.
struct SecondaryButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.ui(17, .semibold))
            .foregroundStyle(Color.ink)
            .frame(maxWidth: .infinity, minHeight: 56)
            .padding(.horizontal, Space.s6)
            .background(Color.creamFill, in: Capsule())
            .scaleEffect(configuration.isPressed ? 0.97 : 1)
            .animation(.spring(duration: 0.25), value: configuration.isPressed)
    }
}

/// Card: white, radius 20, padding 20, flat. `edged` adds a hairline ring for a
/// card that has to hold its own on a white ground; `soft` is the accent wash.
struct CardBackground: ViewModifier {
    var padding: CGFloat = Space.s5
    var edged = false
    var soft = false
    func body(content: Content) -> some View {
        content
            .padding(padding)
            .background(soft ? Color.farmGreenSoft : Color.surface,
                        in: RoundedRectangle(cornerRadius: Radius.card, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: Radius.card, style: .continuous)
                    .stroke(Color.hairline, lineWidth: edged ? 1 : 0)
            )
    }
}

extension View {
    func card(padding: CGFloat = Space.s5, edged: Bool = false, soft: Bool = false) -> some View {
        modifier(CardBackground(padding: padding, edged: edged, soft: soft))
    }
}

/// A stat cell: a bold green value over a muted caption.
struct StatTile: View {
    let value: String
    let caption: String
    var body: some View {
        VStack(spacing: 3) {
            Text(value)
                .font(.ui(22, .bold))
                .foregroundStyle(Color.farmGreen)
                .lineLimit(1)
                .minimumScaleFactor(0.6)
            Text(caption)
                .font(.ui(13))
                .foregroundStyle(Color.inkMuted)
        }
        .frame(maxWidth: .infinity)
    }
}

/// Small green uppercase kicker line above titles.
struct Kicker: View {
    let text: String
    var body: some View {
        Text(text.uppercased())
            .font(.ui(12, .semibold))
            .tracking(0.6)
            .foregroundStyle(Color.farmGreen)
    }
}
