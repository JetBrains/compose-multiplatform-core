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
        if arguments.first == "environment" {
            Main_iosKt.collectEnvironment(collectorUrl: arguments[2], id: arguments[1])
            return true
        }
        if Main_iosKt.isRenderBenchmark(benchmarkType: arguments[0]) {
            Main_iosKt.runRenderBenchmark(
                benchmarkCollectorUrl: arguments[2],
                testName: arguments[3],
                benchmarkType: arguments[0],
                id: arguments[1]
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
        if arguments.first == "environment" || Main_iosKt.isRenderBenchmark(benchmarkType: arguments[0]) {
            return
        }

        let window = UIWindow(windowScene: windowScene)
        window.rootViewController = Main_iosKt.MainViewController(
            benchmarkCollectorUrl: arguments[2],
            testName: arguments[3],
            benchmarkType: arguments[0],
            id: arguments[1]
        )
        window.makeKeyAndVisible()
        self.window = window
    }
}
