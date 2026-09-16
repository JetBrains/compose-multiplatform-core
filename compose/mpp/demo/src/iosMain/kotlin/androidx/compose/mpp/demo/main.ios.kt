// Use `xcodegen` first, then `open ./SkikoSample.xcodeproj` and then Run button in XCode.
package androidx.compose.mpp.demo

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ComposeUIViewController
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.autoreleasepool
import kotlinx.cinterop.cstr
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.toCValues
import kotlinx.coroutines.launch
import platform.Foundation.NSStringFromClass
import platform.UIKit.NSLayoutConstraint
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationDelegateProtocol
import platform.UIKit.UIApplicationDelegateProtocolMeta
import platform.UIKit.UIApplicationMain
import platform.UIKit.UIColor
import platform.UIKit.UIEdgeInsetsMake
import platform.UIKit.UIFont
import platform.UIKit.UIFontTextStyleBody
import platform.UIKit.UIImage
import platform.UIKit.UILabel
import platform.UIKit.UILayoutConstraintAxisVertical
import platform.UIKit.UINavigationController
import platform.UIKit.UINavigationItem
import platform.UIKit.UINavigationItemLargeTitleDisplayMode
import platform.UIKit.UIResponder
import platform.UIKit.UIResponderMeta
import platform.UIKit.UIScene
import platform.UIKit.UISceneConfiguration
import platform.UIKit.UIScrollView
import platform.UIKit.UISceneConnectionOptions
import platform.UIKit.UISceneDelegateProtocol
import platform.UIKit.UISceneSession
import platform.UIKit.UISearchDisplayController
import platform.UIKit.UIStackView
import platform.UIKit.UITabBarController
import platform.UIKit.UITabBarItem
import platform.UIKit.UITabBarMinimizeBehaviorOnScrollDown
import platform.UIKit.UIView
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene
import platform.UIKit.UIWindowSceneDelegateProtocol
import platform.UIKit.labelColor
import platform.UIKit.navigationItem
import platform.UIKit.searchDisplayController
import platform.UIKit.secondarySystemBackgroundColor
import platform.UIKit.setTabBarItem
import platform.UIKit.systemBackgroundColor
import platform.UIKit.tabBarItem

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
        val screen1 = ComposeUIViewController {
            IosDemo(arg)
        }
        val screen2 = ComposeUIViewController {
            Text("Screen2", modifier = Modifier.safeDrawingPadding())
        }

        val navigationController1 = UINavigationController(rootViewController = screen1)
        navigationController1.navigationBar.prefersLargeTitles = true
        screen1.navigationItem.title = "Compose Multiplatform Demo"
        screen1.navigationItem.largeTitleDisplayMode =
            UINavigationItemLargeTitleDisplayMode.UINavigationItemLargeTitleDisplayModeAlways

        val navigationController2 = UINavigationController(rootViewController = screen2)
        navigationController2.navigationBar.prefersLargeTitles = true
        screen2.navigationItem.title = "Screen 2"
        screen2.navigationItem.largeTitleDisplayMode =
            UINavigationItemLargeTitleDisplayMode.UINavigationItemLargeTitleDisplayModeAlways

        val screen3 = NativeScrollViewController()

        val navigationController3 = UINavigationController(rootViewController = screen3)
        navigationController3.navigationBar.prefersLargeTitles = true
        screen3.navigationItem.title = "UIScrollView"
        screen3.navigationItem.largeTitleDisplayMode =
            UINavigationItemLargeTitleDisplayMode.UINavigationItemLargeTitleDisplayModeAlways

        navigationController1.setTabBarItem(
            UITabBarItem(
                title = "Home",
                image = UIImage.systemImageNamed("house"),
                tag = 0
            )
        )
        navigationController2.setTabBarItem(
            UITabBarItem(
                title = "Details",
                image = UIImage.systemImageNamed("list.bullet"),
                tag = 1
            )
        )
        navigationController3.setTabBarItem(
            UITabBarItem(
                title = "Native",
                image = UIImage.systemImageNamed("scroll"),
                tag = 2
            )
        )

        val tabBar = UITabBarController()
        // Shrink the tab bar down to a compact pill once the content is scrolled down
        tabBar.tabBarMinimizeBehavior = UITabBarMinimizeBehaviorOnScrollDown
        tabBar.setViewControllers(
            listOf(navigationController1, navigationController2, navigationController3)
        )
        tabBar
    }
}

@Composable
fun IosDemo(
    arg: String,
    viewControllerFactory: IosDemoViewControllerFactory? = null,
) {
    val scrollState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val overscrollEffect = remember(density, scrollState) {
        NavigationOverscrollEffect(density = density, onScrollToTop = {
            println(">>> Scroll to top!!!")
            coroutineScope.launch {
                scrollState.scrollToItem(0, 0)
            }
        })
    }
    LazyColumn(
        overscrollEffect = overscrollEffect,
        flingBehavior = overscrollEffect,
    ) {
        items(200) {
            Text("Item $it", modifier = Modifier.fillMaxWidth().padding(24.dp))
        }
    }
}

/**
 * A screen made of UIKit components only: a [UIScrollView] with a custom scrollable content,
 * which can be compared side by side with the Compose driven scroll of the other tabs
 */
private class NativeScrollViewController : UIViewController(nibName = null, bundle = null) {
    override fun viewDidLoad() {
        super.viewDidLoad()

        view.backgroundColor = UIColor.systemBackgroundColor

        val scrollView = UIScrollView()
        scrollView.translatesAutoresizingMaskIntoConstraints = false
        scrollView.alwaysBounceVertical = true
        view.addSubview(scrollView)

        val contentStack = UIStackView()
        contentStack.translatesAutoresizingMaskIntoConstraints = false
        contentStack.axis = UILayoutConstraintAxisVertical
        contentStack.spacing = 12.0
        contentStack.layoutMarginsRelativeArrangement = true
        contentStack.layoutMargins = UIEdgeInsetsMake(16.0, 16.0, 16.0, 16.0)
        scrollView.addSubview(contentStack)

        repeat(50) { index ->
            contentStack.addArrangedSubview(makeRow(index))
        }

        NSLayoutConstraint.activateConstraints(
            listOf(
                scrollView.topAnchor.constraintEqualToAnchor(view.topAnchor),
                scrollView.bottomAnchor.constraintEqualToAnchor(view.bottomAnchor),
                scrollView.leadingAnchor.constraintEqualToAnchor(view.leadingAnchor),
                scrollView.trailingAnchor.constraintEqualToAnchor(view.trailingAnchor),

                // The stack is what defines the scrollable content size
                contentStack.topAnchor.constraintEqualToAnchor(
                    scrollView.contentLayoutGuide.topAnchor
                ),
                contentStack.bottomAnchor.constraintEqualToAnchor(
                    scrollView.contentLayoutGuide.bottomAnchor
                ),
                contentStack.leadingAnchor.constraintEqualToAnchor(
                    scrollView.contentLayoutGuide.leadingAnchor
                ),
                contentStack.trailingAnchor.constraintEqualToAnchor(
                    scrollView.contentLayoutGuide.trailingAnchor
                ),

                // while its width is bound to the visible frame, so the content scrolls vertically only
                contentStack.widthAnchor.constraintEqualToAnchor(
                    scrollView.frameLayoutGuide.widthAnchor
                ),
            )
        )
    }

    private fun makeRow(index: Int): UIView {
        val row = UIView()
        row.translatesAutoresizingMaskIntoConstraints = false
        row.backgroundColor = UIColor.secondarySystemBackgroundColor
        row.layer.cornerRadius = 12.0

        val label = UILabel()
        label.translatesAutoresizingMaskIntoConstraints = false
        label.text = "UIScrollView item $index"
        label.font = UIFont.preferredFontForTextStyle(UIFontTextStyleBody)
        label.textColor = UIColor.labelColor
        row.addSubview(label)

        NSLayoutConstraint.activateConstraints(
            listOf(
                row.heightAnchor.constraintEqualToConstant(72.0),
                label.leadingAnchor.constraintEqualToAnchor(row.leadingAnchor, constant = 16.0),
                label.trailingAnchor.constraintEqualToAnchor(row.trailingAnchor, constant = -16.0),
                label.centerYAnchor.constraintEqualToAnchor(row.centerYAnchor),
            )
        )

        return row
    }
}

class IosDemoViewControllerFactory(
    val makeHostingController: (Int) -> UIViewController,
    val makeSwiftUISizeThatFitsSizingDemoController: (UIView, SwiftUISizeThatFitsSizingExample) -> UIViewController,
    val makeSwiftUIIntrinsicSizingDemoController: (UIView, SwiftUIIntrinsicSizingExample) -> UIViewController,
    val makeUIKitSizingDemoController: (UIView, UIKitSizingExample) -> UIViewController,
) {
    internal fun extraScreens(): List<Screen> = listOf(
        IosSizing(
            makeSwiftUISizeThatFitsSizingDemoController,
            makeSwiftUIIntrinsicSizingDemoController,
            makeUIKitSizingDemoController,
        ),
        Screen.Selection(
            "SwiftUI",
            SwiftUIInteropExample(makeHostingController),
        ),
    )
}

private lateinit var MakeRootViewController: () -> UIViewController
@OptIn(BetaInteropApi::class)
private fun UIKitMain(makeRootViewController: () -> UIViewController) {
    MakeRootViewController = makeRootViewController
    memScoped {
        val argc = 1
        val argv = arrayOf("ComposeDemo").map { it.cstr.ptr }.toCValues()
        autoreleasepool {
            UIApplicationMain(argc, argv, null, NSStringFromClass(AppDelegate))
        }
    }
}

private class AppDelegate : UIResponder, UIApplicationDelegateProtocol {
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
        config.delegateClass = SceneDelegate.`class`()
        config.sceneClass = UIWindowScene.`class`()
        return config
    }
}

private class SceneDelegate: UIResponder, UIWindowSceneDelegateProtocol, UISceneDelegateProtocol {
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
