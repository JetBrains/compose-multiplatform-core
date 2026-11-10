import SwiftUI
import BenchmarksApp

@main
struct ComposeApp: App {
    private let arguments = Array(ProcessInfo.processInfo.arguments.dropFirst())

    var body: some Scene {
        WindowGroup {
            ContentView(
                bechmarkCollectorUrl: arguments.first ?? "",
                testName: arguments.dropFirst().first ?? ""
            ).ignoresSafeArea(.all)
        }
    }
}

struct ContentView: UIViewControllerRepresentable {
    let bechmarkCollectorUrl: String
    let testName: String

    func makeUIViewController(context: Context) -> UIViewController {
        Main_iosKt.MainViewController(bechmarkCollectorUrl: bechmarkCollectorUrl, testName: testName)
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {
        // Updates will be handled by Compose.
    }
}
