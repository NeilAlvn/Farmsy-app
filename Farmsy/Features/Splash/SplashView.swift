import SwiftUI

// MARK: - Farmsy sprout mark (vector recreation of the farmsy.app icon)

/// Stem of the sprout — drawn with a stroke so it can animate in via trim.
struct SproutStem: Shape {
    func path(in rect: CGRect) -> Path {
        let s = rect.width / 20
        var p = Path()
        p.move(to: CGPoint(x: 10 * s, y: 17 * s))
        p.addLine(to: CGPoint(x: 10 * s, y: 9 * s))
        return p
    }
}

/// The two leaves, filled.
struct SproutLeaves: Shape {
    func path(in rect: CGRect) -> Path {
        let s = rect.width / 20
        var p = Path()
        // Left leaf
        p.move(to: CGPoint(x: 10 * s, y: 12 * s))
        p.addCurve(
            to: CGPoint(x: 5 * s, y: 8 * s),
            control1: CGPoint(x: 10 * s, y: 12 * s),
            control2: CGPoint(x: 6 * s, y: 11 * s)
        )
        p.addCurve(
            to: CGPoint(x: 10 * s, y: 10 * s),
            control1: CGPoint(x: 5 * s, y: 8 * s),
            control2: CGPoint(x: 8 * s, y: 7 * s)
        )
        p.closeSubpath()
        // Right leaf
        p.move(to: CGPoint(x: 10 * s, y: 9 * s))
        p.addCurve(
            to: CGPoint(x: 15 * s, y: 5 * s),
            control1: CGPoint(x: 10 * s, y: 9 * s),
            control2: CGPoint(x: 14 * s, y: 8 * s)
        )
        p.addCurve(
            to: CGPoint(x: 10 * s, y: 7 * s),
            control1: CGPoint(x: 15 * s, y: 5 * s),
            control2: CGPoint(x: 12 * s, y: 4 * s)
        )
        p.closeSubpath()
        return p
    }
}

/// Sprout on a rounded forest-green tile, reusable at any size.
struct FarmsyMark: View {
    var size: CGFloat
    var animatedTrim: CGFloat = 1
    var leavesScale: CGFloat = 1

    var body: some View {
        ZStack {
            RoundedRectangle(cornerRadius: size * 0.22, style: .continuous)
                .fill(Color.farmGreen)
            SproutStem()
                .trim(from: 0, to: animatedTrim)
                .stroke(.white, style: StrokeStyle(lineWidth: size * 0.055, lineCap: .round))
                .frame(width: size * 0.62, height: size * 0.62)
            SproutLeaves()
                .fill(.white)
                .scaleEffect(leavesScale, anchor: UnitPoint(x: 0.5, y: 0.55))
                .frame(width: size * 0.62, height: size * 0.62)
        }
        .frame(width: size, height: size)
    }
}

// MARK: - Splash

/// Opening animation: the green tile pops in, the sprout grows out of it,
/// then the wordmark settles underneath.
/// Word-only opening: "Farmsy" in italic Fraunces, letters cascading up into
/// place, then a green underline sweeps beneath the word.
struct SplashView: View {
    var onFinished: () -> Void

    private static let letters = Array("Farmsy")

    @State private var lettersIn = false
    @State private var underlineIn = false
    @State private var settle = false

    var body: some View {
        ZStack {
            Color.cream.ignoresSafeArea()

            VStack(spacing: 16) {
                HStack(spacing: 0) {
                    ForEach(Self.letters.indices, id: \.self) { i in
                        Text(String(Self.letters[i]))
                            .font(.displayItalic(60, weight: .medium))
                            .foregroundStyle(Color.ink)
                            .opacity(lettersIn ? 1 : 0)
                            .offset(y: lettersIn ? 0 : 38)
                            .blur(radius: lettersIn ? 0 : 7)
                            .rotationEffect(.degrees(lettersIn ? 0 : 7), anchor: .bottom)
                            .animation(
                                .spring(duration: 0.6, bounce: 0.32).delay(Double(i) * 0.075),
                                value: lettersIn
                            )
                    }
                }
                .accessibilityElement(children: .combine)
                .accessibilityLabel("Farmsy")

                Capsule()
                    .fill(Color.farmGreen)
                    .frame(width: underlineIn ? 132 : 0, height: 4)
                    .opacity(underlineIn ? 1 : 0)
            }
            .scaleEffect(settle ? 1 : 1.06)
        }
        .onAppear { runSequence() }
    }

    private func runSequence() {
        lettersIn = true
        withAnimation(.spring(duration: 0.55, bounce: 0.25).delay(0.85)) {
            underlineIn = true
        }
        withAnimation(.easeOut(duration: 0.7).delay(0.2)) {
            settle = true
        }
        Task {
            var hold = 2.3
            #if DEBUG
            // Screenshot tour: keep the composed splash on screen long enough
            // for the simulator to capture it.
            if CommandLine.arguments.contains("--hold-splash") { hold = 7 }
            #endif
            try? await Task.sleep(for: .seconds(hold))
            Haptics.tap()
            onFinished()
        }
    }
}

#Preview {
    SplashView {}
}
