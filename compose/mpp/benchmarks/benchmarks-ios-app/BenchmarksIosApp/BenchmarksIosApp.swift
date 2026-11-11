import UIKit
import BenchmarksApp

@main
class AppDelegate: UIResponder, UIApplicationDelegate {
    fileprivate var arguments: [String] = []

    override init() {
        super.init()
        Main_iosKt.initializeStartupTiming()
    }

    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        arguments = Array(ProcessInfo.processInfo.arguments.dropFirst())
        precondition(arguments.count == 4, "Expected collector URL, test name, benchmark type and id")
        if Main_iosKt.isRenderBenchmark(benchmarkType: arguments[2]) {
            Main_iosKt.runRenderBenchmark(
                benchmarkCollectorUrl: arguments[0],
                testName: arguments[1],
                benchmarkType: arguments[2],
                id: arguments[3]
            )
        }
        return true
    }
}

class SceneDelegate: UIResponder, UIWindowSceneDelegate {
    var window: UIWindow?

    func scene(
        _ scene: UIScene,
        willConnectTo session: UISceneSession,
        options connectionOptions: UIScene.ConnectionOptions
    ) {
        guard let windowScene = scene as? UIWindowScene,
              let appDelegate = UIApplication.shared.delegate as? AppDelegate else {
            return
        }
        let arguments = appDelegate.arguments
        if Main_iosKt.isRenderBenchmark(benchmarkType: arguments[2]) {
            return
        }

        let window = UIWindow(windowScene: windowScene)
        window.rootViewController = Main_iosKt.MainViewController(
            benchmarkCollectorUrl: arguments[0],
            testName: arguments[1],
            benchmarkType: arguments[2],
            id: arguments[3]
        )
        window.makeKeyAndVisible()
        self.window = window
    }
}
