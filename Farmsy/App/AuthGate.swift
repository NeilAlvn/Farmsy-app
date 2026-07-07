import SwiftUI

/// Environment hook for "this action needs an account".
/// MainView installs the real implementation (presents the login sheet);
/// the default is a no-op so previews keep working.
private struct RequestAuthKey: EnvironmentKey {
    static let defaultValue: () -> Void = {}
}

extension EnvironmentValues {
    var requestAuth: () -> Void {
        get { self[RequestAuthKey.self] }
        set { self[RequestAuthKey.self] = newValue }
    }
}
