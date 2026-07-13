//
//  FarmsyApp.swift
//  Farmsy
//
//  Created by Neil Alvin Medallon on 6/9/26.
//

import SwiftUI

@main
struct FarmsyApp: App {
    @State private var session: SessionStore
    @State private var farms = FarmsStore()
    @State private var favorites = FavoritesStore()
    @State private var locationManager = LocationManager()
    @State private var purchases = PurchaseStore()

    init() {
        #if DEBUG
        if CommandLine.arguments.contains("--reset-onboarding") {
            UserDefaults.standard.removeObject(forKey: "didFinishOnboarding")
            UserDefaults.standard.removeObject(forKey: "pendingRefCode")
        }
        #endif
        PurchaseStore.configure()
        let store = SessionStore()
        #if DEBUG
        store.isDemoSession = CommandLine.arguments.contains("--demo-session")
        #endif
        _session = State(initialValue: store)
    }

    var body: some Scene {
        WindowGroup {
            RootView()
                .environment(session)
                .environment(farms)
                .environment(favorites)
                .environment(locationManager)
                .environment(purchases)
                .tint(.farmGreen)
                .preferredColorScheme(.light)
        }
    }
}
