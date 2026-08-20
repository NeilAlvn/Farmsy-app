import SwiftUI

/// "This is my farm" — creates a pending ownership claim, reviewed by the
/// Farmsy team, exactly like the claim flow on the website. Verified either
/// by a business email on the farm's domain or by a KVK number.
struct ClaimFarmView: View {
    let pin: FarmPin

    @Environment(\.dismiss) private var dismiss
    @Environment(SessionStore.self) private var session

    enum Method: String, CaseIterable {
        case email, kvk

        var label: String {
            switch self {
            case .email: String(localized: "Business email")
            case .kvk: String(localized: "KVK number")
            }
        }
    }

    @State private var fullName = ""
    @State private var email = ""
    @State private var phone = ""
    @State private var method: Method = .email
    @State private var kvkNumber = ""
    @State private var message = ""
    @State private var isSubmitting = false
    @State private var errorMessage: String?
    @State private var isDone = false

    private var canSubmit: Bool {
        !fullName.trimmingCharacters(in: .whitespaces).isEmpty
            && !email.trimmingCharacters(in: .whitespaces).isEmpty
            && !phone.trimmingCharacters(in: .whitespaces).isEmpty
            && (method == .email || !kvkNumber.trimmingCharacters(in: .whitespaces).isEmpty)
            && !isSubmitting
    }

    var body: some View {
        NavigationStack {
            Group {
                if isDone {
                    doneState
                } else {
                    formBody
                }
            }
            .background(Color.cream.ignoresSafeArea())
            .navigationTitle("Claim this farm")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("Cancel") { dismiss() }
                        .foregroundStyle(Color.inkMuted)
                }
            }
            .onAppear {
                if email.isEmpty { email = session.email }
            }
        }
    }

    private var formBody: some View {
        ScrollView(showsIndicators: false) {
            VStack(alignment: .leading, spacing: 18) {
                VStack(alignment: .leading, spacing: 6) {
                    Kicker(text: String(localized: "For farm owners"))
                    DisplayTitle(leading: String(localized: "Is "),
                                 emphasis: pin.name,
                                 trailing: String(localized: " yours?"), size: 26)
                    Text("Claim it to keep your details up to date. Our team verifies every claim before handing over the keys.")
                        .font(.geist(14))
                        .foregroundStyle(Color.inkMuted)
                        .lineSpacing(2)
                }
                .padding(.top, 8)

                VStack(alignment: .leading, spacing: 12) {
                    field("Your full name *", text: $fullName, prompt: "First and last name")
                    field("Email *", text: $email, prompt: "you@yourfarm.nl", keyboard: .emailAddress)
                    field("Phone *", text: $phone, prompt: "+31 …", keyboard: .phonePad)
                }
                .padding(16)
                .background(.white, in: RoundedRectangle(cornerRadius: 16, style: .continuous))

                VStack(alignment: .leading, spacing: 12) {
                    Text("How should we verify you?")
                        .font(.display(19, weight: .semibold))
                        .foregroundStyle(Color.ink)
                    Picker("Verification", selection: $method) {
                        ForEach(Method.allCases, id: \.self) { m in
                            Text(m.label).tag(m)
                        }
                    }
                    .pickerStyle(.segmented)

                    if method == .kvk {
                        field("KVK number *", text: $kvkNumber, prompt: "12345678", keyboard: .numberPad)
                    } else {
                        Text("Use an email address on your farm's own domain and we can verify you fastest.")
                            .font(.geist(13))
                            .foregroundStyle(Color.inkMuted)
                    }

                    VStack(alignment: .leading, spacing: 6) {
                        Text("Anything we should know? (optional)")
                            .font(.geist(13, .semibold))
                            .foregroundStyle(Color.inkMuted)
                        TextEditor(text: $message)
                            .font(.geist(15))
                            .frame(minHeight: 80)
                            .padding(8)
                            .scrollContentBackground(.hidden)
                            .background(Color.cream, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                    }
                }
                .padding(16)
                .background(.white, in: RoundedRectangle(cornerRadius: 16, style: .continuous))

                if let errorMessage {
                    Text(errorMessage)
                        .font(.geist(14, .medium))
                        .foregroundStyle(Color.warnRed)
                }

                Button {
                    submit()
                } label: {
                    Group {
                        if isSubmitting {
                            ProgressView().tint(.white)
                        } else {
                            Text("Send claim")
                        }
                    }
                    .frame(maxWidth: .infinity)
                }
                .buttonStyle(PrimaryButtonStyle())
                .disabled(!canSubmit)
                .opacity(canSubmit ? 1 : 0.55)
                .accessibilityIdentifier("submit-claim")
                .padding(.bottom, 22)
            }
            .padding(.horizontal, 18)
        }
        .scrollDismissesKeyboard(.interactively)
    }

    private func field(
        _ label: LocalizedStringKey,
        text: Binding<String>,
        prompt: LocalizedStringKey,
        keyboard: UIKeyboardType = .default
    ) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(label)
                .font(.geist(13, .semibold))
                .foregroundStyle(Color.inkMuted)
            TextField(prompt, text: text)
                .font(.geist(15))
                .keyboardType(keyboard)
                .autocorrectionDisabled()
                .textInputAutocapitalization(keyboard == .default ? .words : .never)
                .padding(.vertical, 11)
                .padding(.horizontal, 12)
                .background(Color.cream, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
        }
    }

    private var doneState: some View {
        VStack(spacing: 16) {
            Spacer()
            Image(systemName: "checkmark.seal.fill")
                .font(.system(size: 56))
                .foregroundStyle(Color.farmGreen)
            DisplayTitle(String(localized: "Claim *sent*"), size: 30)
            Text("We'll be in touch at \(email) once the team has verified your claim.")
                .font(.geist(15))
                .foregroundStyle(Color.inkMuted)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 30)
            Spacer()
            Button {
                dismiss()
            } label: {
                Text("Done").frame(maxWidth: .infinity)
            }
            .buttonStyle(PrimaryButtonStyle())
            .padding(.horizontal, 18)
            .padding(.bottom, 22)
        }
    }

    private func submit() {
        guard let token = session.session?.accessToken else {
            errorMessage = SubmissionError.notSignedIn.errorDescription
            return
        }
        Haptics.tap()
        errorMessage = nil
        isSubmitting = true
        let claim = SubmissionAPI.FarmClaim(
            farmOsmId: pin.osmId,
            farmName: pin.name,
            fullName: fullName.trimmingCharacters(in: .whitespaces),
            email: email.trimmingCharacters(in: .whitespaces),
            phone: phone.trimmingCharacters(in: .whitespaces),
            verificationMethod: method.rawValue,
            kvkNumber: method == .kvk ? kvkNumber.trimmingCharacters(in: .whitespaces) : nil,
            message: message.isEmpty ? nil : message
        )
        Task {
            defer { isSubmitting = false }
            do {
                try await SubmissionAPI.submitClaim(claim, accessToken: token)
                Haptics.success()
                withAnimation(.spring(duration: 0.4)) { isDone = true }
            } catch {
                errorMessage = error.localizedDescription
            }
        }
    }
}
