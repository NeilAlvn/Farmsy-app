import SwiftUI
import MapKit
import PhotosUI

/// "Add a farm shop" — mirrors the website's submission form. New farms land
/// in the farm_submissions table as pending and go live once an admin
/// approves them. Members only (the server enforces it too).
struct AddFarmView: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(SessionStore.self) private var session
    @Environment(LocationManager.self) private var locationManager

    @State private var form = SubmissionAPI.FarmSubmission()
    @State private var selectedCategories: Set<FarmCategory> = []
    @State private var photoItems: [PhotosPickerItem] = []
    @State private var photoPreviews: [UIImage] = []
    @State private var pinCoordinate: CLLocationCoordinate2D?
    @State private var mapCamera: MapCameraPosition = .region(
        MKCoordinateRegion(
            center: CLLocationCoordinate2D(latitude: 51.8, longitude: 4.7),
            span: MKCoordinateSpan(latitudeDelta: 3.4, longitudeDelta: 3.4)
        )
    )
    @State private var isSubmitting = false
    @State private var errorMessage: String?
    @State private var isDone = false

    private var canSubmit: Bool {
        !form.name.trimmingCharacters(in: .whitespaces).isEmpty
            && !form.city.trimmingCharacters(in: .whitespaces).isEmpty
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
            .navigationTitle("Add a farm shop")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("Cancel") { dismiss() }
                        .foregroundStyle(Color.inkMuted)
                }
            }
        }
    }

    // MARK: - Form

    private var formBody: some View {
        ScrollView(showsIndicators: false) {
            VStack(alignment: .leading, spacing: 18) {
                DisplayTitle(leading: String(localized: "Put a farm "),
                             emphasis: String(localized: "on the map"),
                             trailing: "", size: 28)
                    .padding(.top, 8)

                Text("Know a farm shop that isn't on Farmsy yet? Fill in what you know — our team checks every submission before it goes live.")
                    .font(.geist(14))
                    .foregroundStyle(Color.inkMuted)
                    .lineSpacing(2)

                if !session.hasFullAccess {
                    membersNote
                }

                section("The essentials") {
                    labeledField("Farm name *", text: $form.name, prompt: "e.g. Boerderij De Groene Weide")
                    labeledField("City *", text: $form.city, prompt: "e.g. Utrecht")
                    countryPicker
                }

                section("What do they sell?") {
                    categoryChips
                }

                section("Tell us more (optional)") {
                    VStack(alignment: .leading, spacing: 6) {
                        Text("Description")
                            .font(.geist(13, .semibold))
                            .foregroundStyle(Color.inkMuted)
                        TextEditor(text: $form.description)
                            .font(.geist(15))
                            .frame(minHeight: 90)
                            .padding(8)
                            .scrollContentBackground(.hidden)
                            .background(Color.cream, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                    }
                    labeledField("Street address", text: $form.address, prompt: "Street + number")
                    labeledField("Postal code", text: $form.postalCode, prompt: "1234 AB")
                    labeledField("Phone", text: $form.phone, prompt: "+31 …", keyboard: .phonePad)
                    labeledField("Website", text: $form.website, prompt: "https://…", keyboard: .URL)
                    labeledField("Email", text: $form.email, prompt: "info@…", keyboard: .emailAddress)
                    labeledField("Opening hours", text: $form.openingHours, prompt: "e.g. Mo–Sa 9:00–17:00")
                }

                section("Where is it?") {
                    locationPicker
                }

                section("Photos (up to 5)") {
                    photoPicker
                }

                if let errorMessage {
                    Text(errorMessage)
                        .font(.geist(14, .medium))
                        .foregroundStyle(Color.warnRed)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }

                Button {
                    submit()
                } label: {
                    Group {
                        if isSubmitting {
                            ProgressView().tint(.white)
                        } else {
                            Text("Submit farm shop")
                        }
                    }
                    .frame(maxWidth: .infinity)
                }
                .buttonStyle(PrimaryButtonStyle())
                .disabled(!canSubmit)
                .opacity(canSubmit ? 1 : 0.55)
                .accessibilityIdentifier("submit-farm")
                .padding(.bottom, 22)
            }
            .padding(.horizontal, 18)
        }
        .scrollDismissesKeyboard(.interactively)
    }

    private var membersNote: some View {
        HStack(spacing: 10) {
            Image(systemName: "lock.fill")
                .foregroundStyle(Color.farmGreen)
            Text("Adding farm shops is a member feature — your account doesn't have full access yet.")
                .font(.geist(13, .medium))
                .foregroundStyle(Color.ink)
        }
        .padding(12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.farmGreenSoft, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
    }

    // MARK: - Pieces

    private func section(_ title: LocalizedStringKey, @ViewBuilder content: () -> some View) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(title)
                .font(.display(19, weight: .semibold))
                .foregroundStyle(Color.ink)
            content()
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(.white, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
    }

    private func labeledField(
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

    private var countryPicker: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text("Country")
                .font(.geist(13, .semibold))
                .foregroundStyle(Color.inkMuted)
            Picker("Country", selection: $form.country) {
                Text("Netherlands").tag("Netherlands")
                Text("Belgium").tag("Belgium")
            }
            .pickerStyle(.segmented)
        }
    }

    private var categoryChips: some View {
        CategoryPickChips(categories: FarmCategory.allCases, selected: $selectedCategories)
    }

    private var locationPicker: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(pinCoordinate == nil
                 ? "Tap the map to drop a pin (optional — the address is enough)."
                 : "Pin dropped. Tap again to adjust.")
                .font(.geist(13))
                .foregroundStyle(Color.inkMuted)

            MapReader { proxy in
                Map(position: $mapCamera) {
                    if let pinCoordinate {
                        Annotation("", coordinate: pinCoordinate, anchor: .bottom) {
                            Image(systemName: "mappin.circle.fill")
                                .font(.system(size: 34))
                                .foregroundStyle(Color.farmGreen, .white)
                                .shadow(color: .black.opacity(0.25), radius: 3, y: 2)
                        }
                    }
                }
                .mapStyle(.standard(pointsOfInterest: .excludingAll))
                .onTapGesture { position in
                    if let coordinate = proxy.convert(position, from: .local) {
                        Haptics.tap()
                        pinCoordinate = coordinate
                    }
                }
            }
            .frame(height: 200)
            .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))

            Button {
                Haptics.tap()
                locationManager.request()
                if let loc = locationManager.location {
                    pinCoordinate = loc.coordinate
                    withAnimation {
                        mapCamera = .region(MKCoordinateRegion(
                            center: loc.coordinate,
                            span: MKCoordinateSpan(latitudeDelta: 0.05, longitudeDelta: 0.05)
                        ))
                    }
                }
            } label: {
                Label("Use my location", systemImage: "location.fill")
                    .font(.geist(14, .semibold))
                    .foregroundStyle(Color.farmGreen)
            }
        }
    }

    private var photoPicker: some View {
        VStack(alignment: .leading, spacing: 10) {
            PhotosPicker(
                selection: $photoItems,
                maxSelectionCount: 5,
                matching: .images
            ) {
                Label(photoPreviews.isEmpty ? "Choose photos" : "Change photos",
                      systemImage: "photo.on.rectangle.angled")
                    .font(.geist(14, .semibold))
                    .foregroundStyle(Color.farmGreen)
                    .padding(.vertical, 11)
                    .padding(.horizontal, 14)
                    .background(
                        Capsule().fill(.white).stroke(Color.farmGreen, lineWidth: 1.5)
                    )
            }
            .onChange(of: photoItems) {
                Task { await loadPhotos() }
            }

            if !photoPreviews.isEmpty {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(photoPreviews.indices, id: \.self) { i in
                            Image(uiImage: photoPreviews[i])
                                .resizable()
                                .scaledToFill()
                                .frame(width: 84, height: 84)
                                .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                        }
                    }
                }
            }
        }
    }

    private var doneState: some View {
        VStack(spacing: 16) {
            Spacer()
            Image(systemName: "checkmark.seal.fill")
                .font(.system(size: 56))
                .foregroundStyle(Color.farmGreen)
            DisplayTitle(leading: String(localized: "Thanks — it's "),
                         emphasis: String(localized: "in review"),
                         trailing: "", size: 28)
            Text("Our team looks at every submission. Once approved, the farm appears on the map for everyone.")
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

    // MARK: - Actions

    private func loadPhotos() async {
        var previews: [UIImage] = []
        var datas: [Data] = []
        for item in photoItems.prefix(5) {
            guard let raw = try? await item.loadTransferable(type: Data.self),
                  let image = UIImage(data: raw) else { continue }
            // Keep uploads light: cap the long edge at 1600px, JPEG 80%.
            let scaled = image.scaled(maxDimension: 1600)
            if let jpeg = scaled.jpegData(compressionQuality: 0.8) {
                previews.append(scaled)
                datas.append(jpeg)
            }
        }
        photoPreviews = previews
        form.imageData = datas
    }

    private func submit() {
        guard let token = session.session?.accessToken else {
            errorMessage = SubmissionError.notSignedIn.errorDescription
            return
        }
        Haptics.tap()
        errorMessage = nil
        isSubmitting = true
        form.farmType = selectedCategories.map(\.rawValue).sorted()
        form.lat = pinCoordinate?.latitude
        form.lng = pinCoordinate?.longitude
        Task {
            defer { isSubmitting = false }
            do {
                try await SubmissionAPI.submitFarm(form, accessToken: token)
                Haptics.success()
                withAnimation(.spring(duration: 0.4)) { isDone = true }
            } catch {
                errorMessage = error.localizedDescription
            }
        }
    }
}

/// Wrapping multi-select category chips.
struct CategoryPickChips: View {
    let categories: [FarmCategory]
    @Binding var selected: Set<FarmCategory>

    private let columns = [GridItem(.adaptive(minimum: 108), spacing: 8)]

    var body: some View {
        LazyVGrid(columns: columns, alignment: .leading, spacing: 8) {
            ForEach(categories) { cat in
                let isOn = selected.contains(cat)
                Button {
                    Haptics.tap()
                    if isOn { selected.remove(cat) } else { selected.insert(cat) }
                } label: {
                    Text("\(cat.emoji) \(cat.label)")
                        .font(.geist(13, .semibold))
                        .foregroundStyle(isOn ? .white : Color.ink)
                        .padding(.vertical, 8)
                        .padding(.horizontal, 10)
                        .frame(maxWidth: .infinity)
                        .background(
                            Capsule().fill(isOn ? Color.farmGreen : Color.cream)
                        )
                }
                .buttonStyle(.plain)
            }
        }
    }
}

private extension UIImage {
    func scaled(maxDimension: CGFloat) -> UIImage {
        let longEdge = max(size.width, size.height)
        guard longEdge > maxDimension else { return self }
        let scale = maxDimension / longEdge
        let newSize = CGSize(width: size.width * scale, height: size.height * scale)
        let renderer = UIGraphicsImageRenderer(size: newSize)
        return renderer.image { _ in
            draw(in: CGRect(origin: .zero, size: newSize))
        }
    }
}
