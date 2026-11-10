import UIKit
import BenchmarksApp

@main
class AppDelegate: UIResponder, UIApplicationDelegate {
    var window: UIWindow?

    override init() {
        super.init()
        Main_iosKt.initializeStartupTiming()
    }

    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        let arguments = Array(ProcessInfo.processInfo.arguments.dropFirst())
        precondition(arguments.count == 4, "Expected collector URL, test name, benchmark type and id")
        let benchmarkCollectorUrl = arguments[0]
        let testName = arguments[1]
        let benchmarkType = arguments[2]
        let id = arguments[3]

        if Main_iosKt.isRenderBenchmark(benchmarkType: benchmarkType) {
            Main_iosKt.runRenderBenchmark(
                benchmarkCollectorUrl: benchmarkCollectorUrl,
                testName: testName,
                benchmarkType: benchmarkType,
                id: id
            )
        } else {
            let window = UIWindow(frame: UIScreen.main.bounds)
            window.rootViewController = Main_iosKt.MainViewController(
                benchmarkCollectorUrl: benchmarkCollectorUrl,
                testName: testName,
                benchmarkType: benchmarkType,
                id: id
            )
            window.makeKeyAndVisible()
            self.window = window
        }
        return true
    }
}
