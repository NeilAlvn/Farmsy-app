import SwiftUI
import SafariServices

/// A thin SwiftUI wrapper over SFSafariViewController — for opening a farmsy.app
/// page (claim / submit a farm) inside the app rather than kicking out to the
/// browser. Both pages carry their own sign-in where they need one, so an
/// in-app browser gets the user through without ever leaving Farmsy.
struct SafariView: UIViewControllerRepresentable {
    let url: URL

    func makeUIViewController(context: Context) -> SFSafariViewController {
        let vc = SFSafariViewController(url: url)
        vc.preferredControlTintColor = UIColor(Color.farmGreen)
        vc.dismissButtonStyle = .close
        return vc
    }

    func updateUIViewController(_ controller: SFSafariViewController, context: Context) {}
}
