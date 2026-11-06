import ComposeApp
import Foundation
import SwiftUI
import UIKit

@main
struct IOSApp: App {
    var body: some Scene {
        WindowGroup {
            ComposeRootView()
                .ignoresSafeArea()
        }
    }
}

private struct ComposeRootView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        #if DEBUG
        let environment = ProcessInfo.processInfo.environment
        return MainViewControllerKt.MainViewController(
            mode: environment["ELCOMP_API_MODE"],
            baseUrl: environment["ELCOMP_API_BASE_URL"],
            mockScenario: environment["ELCOMP_MOCK_SCENARIO"]
        )
        #else
        return MainViewControllerKt.MainViewController(
            mode: nil,
            baseUrl: nil,
            mockScenario: nil
        )
        #endif
    }

    func updateUIViewController(_ viewController: UIViewController, context: Context) {}
}
