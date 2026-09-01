import SwiftUI

/// The "why-farm" survey: all seven questions on ONE scrolling screen (never a
/// wizard — people finish a form they can see the length of). Questions come from
/// `SurveyAPI` localized; only the local chrome (title, counters, buttons, the
/// post-answer feedback box) is authored here. Chrome copy is authored inline
/// because messages/*.json carries only the OLD 2-step map survey's `survey.*`
/// keys, not this survey — reported to the thread.
///
/// Presented as a `.sheet` from the map overlay entry point (see MapScreen), the
/// same presentation the other secondary surfaces use.
struct SurveyView: View {
    /// What the entry point opens: the seven questions (not-answered), or the
    /// feedback box directly (already answered — "it never goes away"). Aviah's spec:
    /// the button stays after answering and becomes a place to leave feedback.
    enum Mode { case questions, feedback }
    var mode: Mode = .questions

    @Environment(SessionStore.self) private var session
    @Environment(LanguageManager.self) private var language
    @Environment(\.dismiss) private var dismiss

    // Load state for the questions fetch (existing error/retry shape: a message +
    // a "Try again" button, as MapScreen and FarmDetail use). `.feedback` is the
    // answered-user path (feedback box, no questions); `.thanks` is the just-answered
    // path (both show FeedbackView, differing only by its `justAnswered` flag).
    private enum Phase { case loading, failed, ready, thanks, feedback }
    @State private var phase: Phase = .loading
    @State private var def: SurveyDefinition?

    // Answers, keyed by question id.
    @State private var single: [String: String] = [:]      // one → chosen option id
    @State private var multi: [String: [String]] = [:]      // many → ordered ids (for the cap-swap)
    @State private var texts: [String: String] = [:]        // text → free text

    // Signed-out identity block.
    @State private var name = ""
    @State private var email = ""

    @State private var submitting = false
    @State private var submitError: String?

    /// DEBUG only: true when the gate said hide (admin / already answered) but a
    /// Debug build showed the survey anyway for testing. Always false in Release.
    @State private var gateWouldHide = false

    private var locale: String {
        SurveyAPI.clampLocale(language.current == .system
            ? (Locale.autoupdatingCurrent.language.languageCode?.identifier ?? "en")
            : language.current.rawValue)
    }

    var body: some View {
        ZStack {
            Color.cream.ignoresSafeArea()
            switch phase {
            case .loading:  ProgressView().tint(.farmGreenMap)
            case .failed:   loadError
            case .ready:    form
            case .thanks:   FeedbackView(justAnswered: true, onClose: { dismiss() })
            case .feedback: FeedbackView(justAnswered: false, onClose: { dismiss() })
            }
        }
        .task { await load() }
    }

    // MARK: - Load + gate

    private func load() async {
        phase = .loading
        gateWouldHide = false
        let token = session.session?.accessToken
        // Gate first: an already-answered person or an admin must never see the
        // questions (an answer from us is indistinguishable from a real one later).
        // A FAILED gate call is not a hide — `gate()` is best-effort and returns
        // all-false on any error, so a network blip falls through to `.ready`/`.failed`
        // below rather than silently dismissing.
        let gate = await SurveyAPI.gate(accessToken: token)
        // Admin never sees the survey in either mode. In `.questions` mode an
        // already-answered user is also excluded (they get `.feedback` mode from the
        // button instead). In `.feedback` mode `answered` is expected — do not exclude.
        let excluded = gate.isAdmin || (mode == .questions && gate.answered)
        if excluded {
            #if DEBUG
            // Debug builds render the survey anyway so a developer — whose account is
            // `role = admin`, so the gate returns isAdmin:true and hides it (this is
            // the build-20 "loads then closes" report) — can test the UI. RELEASE
            // still excludes admins and already-answered users unchanged. Note the
            // submit itself still returns `is_admin` for an admin account, so exercise
            // the submit/thanks path with a non-admin account.
            gateWouldHide = true
            #else
            dismiss(); return
            #endif
        }
        // Feedback mode skips the questions fetch entirely — straight to the box.
        if mode == .feedback {
            phase = .feedback
            return
        }
        do {
            def = try await SurveyAPI.questions(locale: locale)
            phase = .ready
        } catch {
            phase = .failed
        }
    }

    private var loadError: some View {
        VStack(spacing: 10) {
            Text("Couldn't load the survey. Check your connection and try again.")
                .font(.geist(14, .medium)).foregroundStyle(Color.inkMuted)
                .multilineTextAlignment(.center)
            Button("Try again") { Haptics.tap(); Task { await load() } }
                .font(.geist(15, .semibold)).foregroundStyle(Color.farmGreenMap)
        }
        .padding(.horizontal, 24)
    }

    /// DEBUG-only notice shown when the gate would have hidden the survey (admin or
    /// already answered) but a Debug build rendered it for testing. Never compiled
    /// into a Release build's reachable state (`gateWouldHide` stays false there).
    private var debugGateBanner: some View {
        HStack(spacing: 8) {
            Image(systemName: "hammer.fill").font(.system(size: 12))
            Text("Debug: the gate would hide this (admin or already answered). Shown for testing — Release excludes it.")
                .font(.geist(12, .medium))
        }
        .foregroundStyle(Color(hex: 0x9A6B00))
        .padding(.horizontal, 12).padding(.vertical, 10)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color(hex: 0xFEF3C7), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
    }

    // MARK: - The form

    private var form: some View {
        VStack(spacing: 0) {
            header
            ScrollView {
                VStack(alignment: .leading, spacing: 36) {
                    if gateWouldHide { debugGateBanner }
                    if let def {
                        ForEach(Array(def.questions.enumerated()), id: \.element.id) { i, q in
                            questionBlock(number: i + 1, q: q)
                        }
                    }
                    if !session.isAuthenticated { identityBlock }
                }
                .padding(.horizontal, 20)
                .padding(.top, 12)
                .padding(.bottom, 24)
            }
            footer
        }
    }

    private var header: some View {
        HStack {
            Text("Seven questions about buying from farms")
                .font(.geist(18, .bold)).foregroundStyle(Color.ink)
                .fixedSize(horizontal: false, vertical: true)
            Spacer()
            Button { Haptics.tap(); dismiss() } label: {
                Image(systemName: "xmark").font(.system(size: 14, weight: .semibold))
                    .foregroundStyle(Color(hex: 0x6B7280))
                    .frame(width: 32, height: 32).background(Color(hex: 0xF3F4F6), in: Circle())
            }
            .buttonStyle(.plain)
        }
        .padding(.horizontal, 16).padding(.top, 16).padding(.bottom, 8)
    }

    @ViewBuilder
    private func questionBlock(number: Int, q: SurveyQuestion) -> some View {
        HStack(alignment: .top, spacing: 12) {
            Text("\(number)")
                .font(.geist(12, .bold)).monospacedDigit()
                .foregroundStyle(Color.farmGreen)
                .padding(.top, 1)
            VStack(alignment: .leading, spacing: 10) {
                Text(q.text).font(.geist(15, .semibold)).foregroundStyle(Color.ink)
                if let hint = hint(for: q) {
                    Text(hint).font(.geist(12)).foregroundStyle(Color.inkMuted)
                }
                switch q.kind {
                case .one:  ForEach(q.options) { opt in optionRow(q: q, opt: opt, multiSelect: false) }
                case .many: ForEach(q.options) { opt in optionRow(q: q, opt: opt, multiSelect: true) }
                case .text: textRow(q: q)
                }
            }
        }
    }

    private func hint(for q: SurveyQuestion) -> String? {
        switch q.kind {
        case .many where q.max != nil: return String(localized: "Choose up to \(q.max!)")
        case .many:                    return String(localized: "More than one answer is possible")
        default:                       return nil
        }
    }

    // MARK: - Option / text rows

    private func isOn(_ q: SurveyQuestion, _ opt: SurveyOption, multiSelect: Bool) -> Bool {
        multiSelect ? (multi[q.id]?.contains(opt.id) ?? false) : (single[q.id] == opt.id)
    }

    private func optionRow(q: SurveyQuestion, opt: SurveyOption, multiSelect: Bool) -> some View {
        let on = isOn(q, opt, multiSelect: multiSelect)
        return Button {
            Haptics.tap()
            toggle(q: q, opt: opt, multiSelect: multiSelect)
        } label: {
            HStack(spacing: 10) {
                indicator(on: on, square: multiSelect)
                Text(opt.label)
                    .font(.geist(14, on ? .semibold : .regular))
                    .foregroundStyle(Color.ink)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
            .padding(.horizontal, 14).padding(.vertical, 10)
            .background(
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .fill(on ? Color.farmGreen.opacity(0.08) : .clear)
                    .stroke(on ? Color.farmGreen.opacity(0.55) : Color.hairline, lineWidth: 1)
            )
        }
        .buttonStyle(.plain)
    }

    /// The indicator shape is the only thing telling someone whether they may pick
    /// more than one: a circle for single-answer, a rounded square for multi. Do not
    /// unify them to ticks (Aviah's spec).
    @ViewBuilder
    private func indicator(on: Bool, square: Bool) -> some View {
        let fill = on ? Color.farmGreen : Color.clear
        let stroke = on ? Color.farmGreen : Color.hairline
        ZStack {
            if square {
                RoundedRectangle(cornerRadius: 5, style: .continuous)
                    .fill(fill)
                    .overlay(RoundedRectangle(cornerRadius: 5, style: .continuous)
                        .stroke(stroke, lineWidth: 1.5))
            } else {
                Circle().fill(fill)
                    .overlay(Circle().stroke(stroke, lineWidth: 1.5))
            }
            if on {
                Image(systemName: "checkmark").font(.system(size: 9, weight: .bold))
                    .foregroundStyle(.white)
            }
        }
        .frame(width: 16, height: 16)
    }

    private func toggle(q: SurveyQuestion, opt: SurveyOption, multiSelect: Bool) {
        if !multiSelect {
            single[q.id] = opt.id
            return
        }
        var ids = multi[q.id] ?? []
        if let idx = ids.firstIndex(of: opt.id) {
            ids.remove(at: idx)                       // tapping a selected one clears it
        } else {
            ids.append(opt.id)
            // Cap: a tap past `max` swaps out the OLDEST rather than being refused.
            if let cap = q.max, ids.count > cap { ids.removeFirst(ids.count - cap) }
        }
        multi[q.id] = ids
    }

    private func textRow(q: SurveyQuestion) -> some View {
        TextField(String(localized: "Optional"), text: Binding(
            get: { texts[q.id] ?? "" }, set: { texts[q.id] = $0 }
        ), axis: .vertical)
            .font(.geist(14)).lineLimit(3, reservesSpace: true)
            .padding(.horizontal, 14).padding(.vertical, 10)
            .background(
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .fill(.white.opacity(0.6))
                    .stroke(Color.hairline, lineWidth: 1)
            )
    }

    // MARK: - Signed-out identity

    private var identityBlock: some View {
        VStack(alignment: .leading, spacing: 12) {
            Divider().background(Color.hairline)
            Text("And who may we thank?")
                .font(.geist(15, .semibold)).foregroundStyle(Color.ink)
            Text("Your email lets us follow up if you asked us something. Name is optional.")
                .font(.geist(12)).foregroundStyle(Color.inkMuted)
            plainField(String(localized: "Name (optional)"), text: $name)
            plainField(String(localized: "Email"), text: $email, keyboard: .emailAddress)
        }
    }

    private func plainField(_ placeholder: String, text: Binding<String>,
                            keyboard: UIKeyboardType = .default) -> some View {
        TextField(placeholder, text: text)
            .font(.geist(14)).keyboardType(keyboard)
            .textInputAutocapitalization(keyboard == .emailAddress ? .never : nil)
            .autocorrectionDisabled(keyboard == .emailAddress)
            .padding(.horizontal, 14).padding(.vertical, 10)
            .background(
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .fill(.white.opacity(0.6))
                    .stroke(Color.hairline, lineWidth: 1)
            )
    }

    // MARK: - Footer (submit + "N to go")

    private var footer: some View {
        VStack(spacing: 8) {
            if let submitError { Text(submitError).font(.geist(13, .medium)).foregroundStyle(Color.warnRed) }
            HStack(spacing: 12) {
                Text(remainingLabel).font(.geist(12)).foregroundStyle(Color.inkMuted)
                Spacer()
                Button { Haptics.tap(); Task { await submit() } } label: {
                    Group {
                        if submitting { ProgressView().tint(.white) }
                        else { Text("Send").font(.geist(15, .semibold)) }
                    }
                    .foregroundStyle(.white)
                    .padding(.horizontal, 20).padding(.vertical, 10)
                    .background(canSubmit ? Color.farmGreenMap : Color.farmGreenMap.opacity(0.4),
                                in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                }
                .buttonStyle(.plain).disabled(!canSubmit || submitting)
            }
        }
        .padding(.horizontal, 20).padding(.top, 10).padding(.bottom, 14)
        .background(Color.cream).overlay(Divider().background(Color.hairline), alignment: .top)
    }

    /// Required = the non-optional questions (the five choice ones) unanswered, plus
    /// the email when signed out. Mirrors web: "N questions to go", then "just your
    /// email address" when that is all that is left.
    private var requiredQuestionsRemaining: Int {
        guard let def else { return 0 }
        return def.questions.filter { q in
            guard !q.optional else { return false }
            switch q.kind {
            case .one:  return single[q.id] == nil
            case .many: return (multi[q.id]?.isEmpty ?? true)
            case .text: return false
            }
        }.count
    }

    private var needsEmail: Bool {
        !session.isAuthenticated && email.trimmingCharacters(in: .whitespaces).isEmpty
    }

    private var canSubmit: Bool { requiredQuestionsRemaining == 0 && !needsEmail }

    private var remainingLabel: String {
        let q = requiredQuestionsRemaining
        if q > 0 { return String(localized: "\(q) questions to go") }
        if needsEmail { return String(localized: "Just your email address") }
        return ""
    }

    private func submit() async {
        submitError = nil
        submitting = true
        defer { submitting = false }

        var answers: [String: SurveyAPI.Answer] = [:]
        for q in def?.questions ?? [] {
            switch q.kind {
            case .one:
                if let v = single[q.id] { answers[q.id] = .init(options: [v], text: nil) }
            case .many:
                if let v = multi[q.id], !v.isEmpty { answers[q.id] = .init(options: v, text: nil) }
            case .text:
                let t = (texts[q.id] ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
                if !t.isEmpty { answers[q.id] = .init(options: nil, text: t) }
            }
        }

        do {
            try await SurveyAPI.submit(
                locale: locale, answers: answers,
                name: session.isAuthenticated ? nil : name,
                email: session.isAuthenticated ? nil : email,
                accessToken: session.session?.accessToken
            )
            Haptics.success()
            phase = .thanks
        } catch SurveyError.refused(let reason) {
            // `incomplete` is "you missed a question"; the rest are "couldn't save".
            submitError = reason == "incomplete"
                ? String(localized: "It looks like a question is still unanswered.")
                : String(localized: "We couldn't save that. Please try again.")
        } catch {
            submitError = String(localized: "We couldn't save that. Please try again.")
        }
    }
}

/// The feedback surface. Reached two ways (Aviah's spec — the entry button never
/// goes away, it changes what it opens): `justAnswered = true` right after the seven
/// questions, or `justAnswered = false` when an already-answered person taps the
/// button again ("What could be better?"). Either way it's subject + message (+ name
/// /email when signed out) → `POST /api/contact` with `topic: "feedback"`. The order
/// is the point: a remark from someone who told us what they came for is worth more.
private struct FeedbackView: View {
    let justAnswered: Bool
    let onClose: () -> Void

    @Environment(SessionStore.self) private var session

    @State private var subject = ""
    @State private var message = ""
    @State private var name = ""
    @State private var email = ""
    @State private var sending = false
    @State private var error: String?
    @State private var sent = false

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Spacer()
                Button { Haptics.tap(); onClose() } label: {
                    Image(systemName: "xmark").font(.system(size: 14, weight: .semibold))
                        .foregroundStyle(Color(hex: 0x6B7280))
                        .frame(width: 32, height: 32).background(Color(hex: 0xF3F4F6), in: Circle())
                }
                .buttonStyle(.plain)
            }
            .padding(.horizontal, 16).padding(.top, 16)

            ScrollView {
                VStack(spacing: 16) {
                    if justAnswered {
                        // Just finished the questions — a small celebration, then feedback.
                        Image(systemName: "checkmark.circle.fill")
                            .font(.system(size: 44)).foregroundStyle(Color.farmGreen)
                            .padding(.top, 8)
                        Text("Thank you, we have your answers.")
                            .font(.geist(18, .bold)).foregroundStyle(Color.ink)
                            .multilineTextAlignment(.center)
                        Text("This genuinely decides what we build next.")
                            .font(.geist(14)).foregroundStyle(Color.inkMuted)
                            .multilineTextAlignment(.center)
                    } else {
                        // Already answered, tapped the button again — straight to feedback.
                        Text("What could be better?")
                            .font(.geist(18, .bold)).foregroundStyle(Color.ink)
                            .multilineTextAlignment(.center).padding(.top, 8)
                        Text("You have already answered the questions, thank you. Anything you write here comes straight to us.")
                            .font(.geist(14)).foregroundStyle(Color.inkMuted)
                            .multilineTextAlignment(.center)
                    }

                    if sent {
                        // After sending feedback (Aviah's copy) — with a way to add more.
                        VStack(spacing: 10) {
                            Text("Thank you, we read every one.")
                                .font(.geist(15, .semibold)).foregroundStyle(Color.farmGreen)
                            Button("Add something else") {
                                Haptics.tap()
                                subject = ""; message = ""; sent = false
                            }
                            .font(.geist(14, .medium)).foregroundStyle(Color.farmGreenMap)
                        }
                        .padding(.top, 8)
                    } else {
                        feedbackBox.padding(.top, 8)
                    }
                }
                .padding(.horizontal, 20).padding(.bottom, 24)
            }
        }
        .background(Color.cream)
    }

    private var feedbackBox: some View {
        VStack(alignment: .leading, spacing: 12) {
            Divider().background(Color.hairline)
            Text("Anything else on your mind?")
                .font(.geist(15, .semibold)).foregroundStyle(Color.ink)
            field(String(localized: "Subject"), text: $subject)
            TextField(String(localized: "Your message"), text: $message, axis: .vertical)
                .font(.geist(14)).lineLimit(4, reservesSpace: true)
                .padding(.horizontal, 14).padding(.vertical, 10)
                .background(RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .fill(.white.opacity(0.6)).stroke(Color.hairline, lineWidth: 1))
            if !session.isAuthenticated {
                field(String(localized: "Name"), text: $name)
                field(String(localized: "Email"), text: $email, keyboard: .emailAddress)
            }
            if let error { Text(error).font(.geist(13, .medium)).foregroundStyle(Color.warnRed) }
            Button { Haptics.tap(); Task { await send() } } label: {
                Group {
                    if sending { ProgressView().tint(.white) }
                    else { Text("Send feedback").font(.geist(15, .semibold)) }
                }
                .foregroundStyle(.white).frame(maxWidth: .infinity).padding(.vertical, 12)
                .background(canSend ? Color.farmGreenMap : Color.farmGreenMap.opacity(0.4),
                            in: RoundedRectangle(cornerRadius: 12, style: .continuous))
            }
            .buttonStyle(.plain).disabled(!canSend || sending)
        }
    }

    private func field(_ placeholder: String, text: Binding<String>,
                       keyboard: UIKeyboardType = .default) -> some View {
        TextField(placeholder, text: text)
            .font(.geist(14)).keyboardType(keyboard)
            .textInputAutocapitalization(keyboard == .emailAddress ? .never : nil)
            .autocorrectionDisabled(keyboard == .emailAddress)
            .padding(.horizontal, 14).padding(.vertical, 10)
            .background(RoundedRectangle(cornerRadius: 12, style: .continuous)
                .fill(.white.opacity(0.6)).stroke(Color.hairline, lineWidth: 1))
    }

    /// /api/contact requires name + email + message. Signed in, we still need a
    /// name/email to send — use the session's.
    private var effectiveName: String { session.isAuthenticated ? session.displayName : name }
    private var effectiveEmail: String { session.isAuthenticated ? session.email : email }

    private var canSend: Bool {
        !message.trimmingCharacters(in: .whitespaces).isEmpty
            && !effectiveName.trimmingCharacters(in: .whitespaces).isEmpty
            && !effectiveEmail.trimmingCharacters(in: .whitespaces).isEmpty
    }

    private func send() async {
        error = nil; sending = true
        defer { sending = false }
        do {
            try await SurveyAPI.sendFeedback(
                subject: subject, message: message,
                name: effectiveName, email: effectiveEmail,
                accessToken: session.session?.accessToken
            )
            Haptics.success()
            sent = true
        } catch {
            self.error = String(localized: "We couldn't send that. Please try again.")
        }
    }
}
