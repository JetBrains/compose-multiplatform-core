// Use `xcodegen` first, then `open ./SkikoSample.xcodeproj` and then Run button in XCode.
package androidx.compose.mpp.demo

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.mpp.demo.bugs.IosBugs
import androidx.compose.mpp.demo.bugs.StartRecompositionCheck
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.modifier.modifierLocalConsumer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.UIKitView
import androidx.compose.ui.viewinterop.UIKitViewController
import androidx.compose.ui.window.ComposeUIViewController
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.autoreleasepool
import kotlinx.cinterop.cstr
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.toCValues
import platform.Foundation.NSStringFromClass
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationDelegateProtocol
import platform.UIKit.UIApplicationDelegateProtocolMeta
import platform.UIKit.UIApplicationMain
import platform.UIKit.UIColor
import platform.UIKit.UILabel
import platform.UIKit.UIResponder
import platform.UIKit.UIResponderMeta
import platform.UIKit.UIScene
import platform.UIKit.UISceneConfiguration
import platform.UIKit.UISceneConnectionOptions
import platform.UIKit.UISceneDelegateProtocol
import platform.UIKit.UISceneSession
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene
import platform.UIKit.UIWindowSceneDelegateProtocol

/**
 * To run the demo project:
 * - install the latest version of the XCode
 * - in terminal, navigate to the directory "compose/mpp/demo"
 * - run the `./regenerate_xcode_project.sh` command
 * - XCode will open this project automatically
 * - press the Run (Cmd+R) button in the XCode
 */
@OptIn(ExperimentalComposeUiApi::class)
fun main(vararg args: String) {
    androidx.compose.ui.util.enableTraceOSLog()

    val arg = args.firstOrNull() ?: ""
    UIKitMain {
        ComposeUIViewController(
            configure = {
                parallelRendering = true
            }
        ) {
            var widthProgress by remember { mutableStateOf(0.5f) }
            Column(modifier = Modifier.safeDrawingPadding()) {
                Slider(widthProgress, onValueChange = {
                    widthProgress = it
                    println("widthProgress: $widthProgress")
                }, modifier = Modifier.fillMaxWidth())

                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    // IosDemo(arg)
                    Column {
                        Row {
                            Box(
                                modifier = Modifier.width((400 * widthProgress).dp).height(30.dp)
                                    .border(2.dp, Color.Blue)
                            )
                            UIKitView({
                                val label = UILabel()
                                label.text =
                                    "Hello text Hello text Hello text Hello text Hello text Hello text Hello text Hello text Hello text Hello text Hello text Hello text Hello text Hello text Hello text Hello text"
                                label.numberOfLines = 0
                                label.backgroundColor = UIColor.redColor
                                label
                            }, modifier = Modifier.border(2.dp, Color.Red))

                        }
                        UIKitViewController({
                            val vc = UIViewController()
                            vc.view.backgroundColor = UIColor.greenColor
                            vc
                        }, modifier = Modifier.size(40.dp).border(2.dp, Color.Green))

                    }
                }
            }
        }
    }
}

@Composable
fun IosDemo(arg: String, makeHostingController: ((Int) -> UIViewController)? = null) {
    val app = remember {
        App(
            extraScreens = listOf(
                IosBugs,
                IosSpecificFeatures,
            ) + listOf(makeHostingController).mapNotNull {
                it?.let {
                    SwiftUIInteropExample(it)
                }
            }
        )
    }
    when (arg) {
        "demo=StartRecompositionCheck" ->
            // The issue tested by this demo can be properly reproduced/tested only right after app
            // start
            StartRecompositionCheck.content()
        else -> app.Content()
    }
}

private lateinit var MakeRootViewController: () -> UIViewController
@OptIn(BetaInteropApi::class)
private fun UIKitMain(makeRootViewController: () -> UIViewController) {
    MakeRootViewController = makeRootViewController
    memScoped {
        val argc = 1
        val argv = arrayOf("ComposeDemo").map { it.cstr.ptr }.toCValues()
        autoreleasepool {
            UIApplicationMain(argc, argv, null, NSStringFromClass(IOSAppDelegate))
        }
    }
}

private class IOSAppDelegate : UIResponder, UIApplicationDelegateProtocol {
    companion object Companion : UIResponderMeta(), UIApplicationDelegateProtocolMeta

    @Suppress("unused")
    @OptIn(BetaInteropApi::class)
    @OverrideInit
    constructor() : super()

    override fun application(
        application: UIApplication,
        didFinishLaunchingWithOptions: Map<Any?, *>?
    ): Boolean = true

    @OptIn(BetaInteropApi::class)
    override fun application(
        application: UIApplication,
        configurationForConnectingSceneSession: UISceneSession,
        options: UISceneConnectionOptions
    ): UISceneConfiguration {
        val config = UISceneConfiguration()
        config.delegateClass = IOSSceneDelegate.`class`()
        config.sceneClass = UIWindowScene.`class`()
        return config
    }
}

private class IOSSceneDelegate: UIResponder, UIWindowSceneDelegateProtocol, UISceneDelegateProtocol {
    companion object Companion : UIResponderMeta(), UIApplicationDelegateProtocolMeta

    @Suppress("unused")
    @OptIn(BetaInteropApi::class)
    @OverrideInit
    constructor() : super()

    private var _window: UIWindow? = null
    override fun window() = _window

    override fun scene(
        scene: UIScene,
        willConnectToSession: UISceneSession,
        options: UISceneConnectionOptions
    ) {
        scene as UIWindowScene
        _window = UIWindow(windowScene = scene)
        _window!!.rootViewController = MakeRootViewController()
        _window!!.makeKeyAndVisible()
    }
}
