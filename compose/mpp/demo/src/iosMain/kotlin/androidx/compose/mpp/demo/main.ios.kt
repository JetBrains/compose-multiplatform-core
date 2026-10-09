// Use `xcodegen` first, then `open ./SkikoSample.xcodeproj` and then Run button in XCode.
package androidx.compose.mpp.demo

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ComposeUIViewController
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.CValue
import kotlinx.cinterop.autoreleasepool
import kotlinx.cinterop.cstr
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.readValue
import kotlinx.cinterop.toCValues
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGColorSpaceCreateDeviceRGB
import platform.CoreGraphics.CGColorSpaceRelease
import platform.CoreGraphics.CGContextDrawLinearGradient
import platform.CoreGraphics.CGGradientCreateWithColorComponents
import platform.CoreGraphics.CGGradientRelease
import platform.CoreGraphics.CGPointMake
import platform.CoreGraphics.CGRect
import platform.CoreGraphics.CGRectZero
import platform.Foundation.NSStringFromClass
import platform.UIKit.NSLayoutConstraint
import platform.UIKit.NSTextAlignmentCenter
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationDelegateProtocol
import platform.UIKit.UIApplicationDelegateProtocolMeta
import platform.UIKit.UIApplicationMain
import platform.UIKit.UIColor
import platform.UIKit.UIEdgeInsetsMake
import platform.UIKit.UIFont
import platform.UIKit.UIFontWeightBold
import platform.UIKit.UIFontWeightMedium
import platform.UIKit.UIFontWeightSemibold
import platform.UIKit.UIGraphicsGetCurrentContext
import platform.UIKit.UIImage
import platform.UIKit.UILabel
import platform.UIKit.UILayoutConstraintAxisHorizontal
import platform.UIKit.UILayoutConstraintAxisVertical
import platform.UIKit.UINavigationController
import platform.UIKit.UINavigationItem
import platform.UIKit.UINavigationItemLargeTitleDisplayMode
import platform.UIKit.UIResponder
import platform.UIKit.UIResponderMeta
import platform.UIKit.UIScene
import platform.UIKit.UISceneConfiguration
import platform.UIKit.UISceneConnectionOptions
import platform.UIKit.UISceneDelegateProtocol
import platform.UIKit.UISceneSession
import platform.UIKit.UIScrollView
import platform.UIKit.UISearchDisplayController
import platform.UIKit.UIStackView
import platform.UIKit.UIStackViewAlignmentCenter
import platform.UIKit.UIStackViewDistributionFillEqually
import platform.UIKit.UITabBarController
import platform.UIKit.UITabBarItem
import platform.UIKit.UITabBarMinimizeBehaviorOnScrollDown
import platform.UIKit.UITraitCollection
import platform.UIKit.UIUserInterfaceStyle
import platform.UIKit.UIView
import platform.UIKit.UIViewContentMode
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene
import platform.UIKit.UIWindowSceneDelegateProtocol
import platform.UIKit.colorWithDynamicProvider
import platform.UIKit.navigationItem
import platform.UIKit.searchDisplayController
import platform.UIKit.setTabBarItem
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

    UIKitMain {
        // 1. The UIKit reference: a plain UIScrollView, to compare the feel of the two Compose
        // screens against
        val nativeScreen = NativeScrollViewController()

        // 2. The same Compose content twice: first with the stock Compose scrolling...
        val defaultOverscrollScreen = ComposeUIViewController {
            ScrollShowcaseWithDefaultOverscroll()
        }

        // ...and then driven by NavigationOverscrollEffect, which moves the navigation chrome
        // along with the rubber banding content
        val navigationOverscrollScreen = ComposeUIViewController {
            ScrollShowcaseWithNavigationOverscroll()
        }

        val tabBar = UITabBarController()
        // Shrink the tab bar down to a compact pill once the content is scrolled down
        tabBar.tabBarMinimizeBehavior = UITabBarMinimizeBehaviorOnScrollDown
        tabBar.setViewControllers(
            listOf(
                largeTitleTab(nativeScreen, "UIScrollView", "Native", "scroll", tag = 0),
                largeTitleTab(defaultOverscrollScreen, "Default", "Default", "list.bullet", tag = 1),
                largeTitleTab(
                    navigationOverscrollScreen,
                    "Navigation Overscroll",
                    "Overscroll",
                    "arrow.up.and.down",
                    tag = 2
                ),
            )
        )
        tabBar
    }
}

/**
 * Wraps [screen] into a [UINavigationController] with a large title, and gives that controller a
 * tab bar item, so all the demo tabs are set up the same way.
 */
private fun largeTitleTab(
    screen: UIViewController,
    title: String,
    tabTitle: String,
    systemImageName: String,
    tag: Int,
): UINavigationController {
    screen.navigationItem.title = title
    screen.navigationItem.largeTitleDisplayMode =
        UINavigationItemLargeTitleDisplayMode.UINavigationItemLargeTitleDisplayModeAlways

    val navigationController = UINavigationController(rootViewController = screen)
    navigationController.navigationBar.prefersLargeTitles = true
    navigationController.setTabBarItem(
        UITabBarItem(
            title = tabTitle,
            image = UIImage.systemImageNamed(systemImageName),
            tag = tag.toLong()
        )
    )
    return navigationController
}

@Composable
fun IosDemo(
    arg: String,
    viewControllerFactory: IosDemoViewControllerFactory? = null,
) {
    val scrollState = rememberLazyListState()
    val density = LocalDensity.current
    val overscrollEffect = remember(density, scrollState) {
        NavigationOverscrollEffect(density = density, scrollableState = scrollState)
    }
    LazyColumn(
        state = scrollState,
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
 * which can be compared side by side with the Compose driven scroll of the other tabs.
 *
 * The content mirrors the Compose showcase of `ScrollShowcase.ios.kt` card for card on purpose, so
 * that switching between the three tabs shows the difference in the scroll physics and in the way
 * the navigation chrome reacts to it, and nothing else.
 */
private class NativeScrollViewController : UIViewController(nibName = null, bundle = null) {
    override fun viewDidLoad() {
        super.viewDidLoad()

        view.backgroundColor = NativeShowcase.Background

        val scrollView = UIScrollView()
        scrollView.translatesAutoresizingMaskIntoConstraints = false
        scrollView.alwaysBounceVertical = true
        view.addSubview(scrollView)

        val contentStack = UIStackView()
        contentStack.translatesAutoresizingMaskIntoConstraints = false
        contentStack.axis = UILayoutConstraintAxisVertical
        contentStack.layoutMarginsRelativeArrangement = true
        // Every Compose item carries its own vertical padding, so the gap between two neighbouring
        // cards is twice the padding of a single one, and the outer margins have to absorb both the
        // list content padding and the padding of the first and the last item
        contentStack.spacing = NativeShowcase.CardPadding * 2
        contentStack.layoutMargins = UIEdgeInsetsMake(
            NativeShowcase.ListPadding + NativeShowcase.BlockPadding,
            NativeShowcase.HorizontalMargin,
            NativeShowcase.ListPadding + NativeShowcase.FooterPadding,
            NativeShowcase.HorizontalMargin,
        )
        scrollView.addSubview(contentStack)

        // A UIStackView has no lazy counterpart, so unlike the LazyColumn of the Compose tabs this
        // builds every row up front. Fine for a demo about the feel of the UIScrollView bounce, but
        // it does make the first appearance of this tab more expensive than that of the other two.
        val hero = makeHeroCard()
        val stats = makeStatsRow()
        val sectionHeader = makeSectionHeader("Gradients")
        val cards = List(NativeShowcase.ItemCount) { index -> makeShowcaseCard(index) }
        val footer = makeFooter()
        for (block in listOf(hero, stats, sectionHeader) + cards + footer) {
            contentStack.addArrangedSubview(block)
        }

        // ...and it has a single spacing, so every gap that is not a card to card one has to be
        // restored by hand out of the paddings of the two Compose items it sits between
        contentStack.setCustomSpacing(
            NativeShowcase.BlockPadding * 2,
            afterView = hero,
        )
        contentStack.setCustomSpacing(
            NativeShowcase.BlockPadding + NativeShowcase.SectionHeaderTopPadding,
            afterView = stats,
        )
        contentStack.setCustomSpacing(
            NativeShowcase.SectionHeaderBottomPadding + NativeShowcase.CardPadding,
            afterView = sectionHeader,
        )
        contentStack.setCustomSpacing(
            NativeShowcase.CardPadding + NativeShowcase.FooterPadding,
            afterView = cards.last(),
        )

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

    /** The UIKit twin of `HeroCard`: a tall gradient card titled in its bottom leading corner. */
    private fun makeHeroCard(): UIView {
        val card = GradientView(
            from = NativeShowcase.HeroGradientFrom,
            to = NativeShowcase.HeroGradientTo,
        )
        card.layer.cornerRadius = 28.0
        card.clipsToBounds = true

        val text = UIStackView()
        text.translatesAutoresizingMaskIntoConstraints = false
        text.axis = UILayoutConstraintAxisVertical
        text.spacing = 8.0
        text.addArrangedSubview(
            makeLabel(
                text = "UIScrollView",
                font = UIFont.systemFontOfSize(32.0, UIFontWeightBold),
                color = NativeShowcase.HeroText,
            )
        )
        text.addArrangedSubview(
            makeLabel(
                text = "UIKit reference. UIScrollView moves the large title and the tab bar along " +
                    "with the content for free — this is the feel to match.",
                font = UIFont.systemFontOfSize(15.0),
                color = NativeShowcase.HeroText.colorWithAlphaComponent(0.75),
            )
        )
        card.addSubview(text)

        NSLayoutConstraint.activateConstraints(
            listOf(
                card.heightAnchor.constraintEqualToConstant(200.0),
                text.leadingAnchor.constraintEqualToAnchor(card.leadingAnchor, constant = 24.0),
                text.trailingAnchor.constraintEqualToAnchor(card.trailingAnchor, constant = -24.0),
                text.bottomAnchor.constraintEqualToAnchor(card.bottomAnchor, constant = -24.0),
            )
        )

        return card
    }

    /** The UIKit twin of `StatsRow`: three equally wide tiles, each a big value over a caption. */
    private fun makeStatsRow(): UIView {
        val row = UIStackView()
        row.translatesAutoresizingMaskIntoConstraints = false
        row.axis = UILayoutConstraintAxisHorizontal
        row.distribution = UIStackViewDistributionFillEqually
        row.spacing = 12.0
        row.addArrangedSubview(makeStatTile("${NativeShowcase.ItemCount}", "items"))
        row.addArrangedSubview(makeStatTile("120", "fps"))
        row.addArrangedSubview(makeStatTile("1", "gesture"))
        return row
    }

    private fun makeStatTile(value: String, caption: String): UIView {
        val tile = UIStackView()
        tile.translatesAutoresizingMaskIntoConstraints = false
        tile.axis = UILayoutConstraintAxisVertical
        tile.alignment = UIStackViewAlignmentCenter
        tile.spacing = 2.0
        tile.layoutMarginsRelativeArrangement = true
        tile.layoutMargins = UIEdgeInsetsMake(16.0, 0.0, 16.0, 0.0)
        tile.backgroundColor = NativeShowcase.Card
        tile.layer.cornerRadius = 20.0
        tile.addArrangedSubview(
            makeLabel(
                text = value,
                font = UIFont.systemFontOfSize(22.0, UIFontWeightSemibold),
                color = NativeShowcase.Title,
            )
        )
        tile.addArrangedSubview(
            makeLabel(
                text = caption,
                font = UIFont.systemFontOfSize(13.0),
                color = NativeShowcase.Subtitle,
            )
        )
        return tile
    }

    /** The UIKit twin of `SectionHeader`: an all caps caption, inset deeper than the cards. */
    private fun makeSectionHeader(text: String): UIView = withExtraInset(
        content = makeLabel(
            text = text.uppercase(),
            font = UIFont.systemFontOfSize(12.0, UIFontWeightSemibold),
            color = NativeShowcase.Subtitle,
        ),
        horizontal = 8.0,
    )

    /** The UIKit twin of `ShowcaseCard`: a gradient avatar next to the name of that gradient. */
    private fun makeShowcaseCard(index: Int): UIView {
        val gradient = NativeShowcase.Gradients[index % NativeShowcase.Gradients.size]

        val avatar = GradientView(from = gradient.from, to = gradient.to)
        avatar.layer.cornerRadius = 24.0
        avatar.clipsToBounds = true
        val avatarLabel = makeLabel(
            text = "${index + 1}",
            font = UIFont.systemFontOfSize(16.0, UIFontWeightBold),
            color = UIColor.whiteColor,
        )
        avatar.addSubview(avatarLabel)

        val text = UIStackView()
        text.translatesAutoresizingMaskIntoConstraints = false
        text.axis = UILayoutConstraintAxisVertical
        text.spacing = 3.0
        text.addArrangedSubview(
            makeLabel(
                text = gradient.name,
                font = UIFont.systemFontOfSize(17.0, UIFontWeightMedium),
                color = NativeShowcase.Title,
            )
        )
        text.addArrangedSubview(
            makeLabel(
                text = gradient.hexes,
                font = UIFont.systemFontOfSize(13.0),
                color = NativeShowcase.Subtitle,
            )
        )

        val card = UIStackView()
        card.translatesAutoresizingMaskIntoConstraints = false
        card.axis = UILayoutConstraintAxisHorizontal
        card.alignment = UIStackViewAlignmentCenter
        card.spacing = 14.0
        card.layoutMarginsRelativeArrangement = true
        card.layoutMargins = UIEdgeInsetsMake(14.0, 14.0, 14.0, 14.0)
        card.backgroundColor = NativeShowcase.Card
        card.layer.cornerRadius = 20.0
        card.addArrangedSubview(avatar)
        card.addArrangedSubview(text)

        NSLayoutConstraint.activateConstraints(
            listOf(
                avatar.widthAnchor.constraintEqualToConstant(48.0),
                avatar.heightAnchor.constraintEqualToConstant(48.0),
                avatarLabel.centerXAnchor.constraintEqualToAnchor(avatar.centerXAnchor),
                avatarLabel.centerYAnchor.constraintEqualToAnchor(avatar.centerYAnchor),
            )
        )

        return card
    }

    /** The UIKit twin of `Footer`: a centred sign off inviting one more pull past the last card. */
    private fun makeFooter(): UIView {
        val footer = makeLabel(
            text = "That's the end of the list.\nKeep pulling to see how the edge behaves.",
            font = UIFont.systemFontOfSize(14.0),
            color = NativeShowcase.Subtitle,
        )
        footer.textAlignment = NSTextAlignmentCenter
        return withExtraInset(content = footer, horizontal = 16.0)
    }

    private fun makeLabel(text: String, font: UIFont, color: UIColor): UILabel {
        val label = UILabel()
        label.translatesAutoresizingMaskIntoConstraints = false
        label.text = text
        label.font = font
        label.textColor = color
        // Matches the Compose default of wrapping instead of truncating
        label.numberOfLines = 0
        return label
    }

    /**
     * Wraps [content] into a container inset by [horizontal] points on both sides. A UIStackView
     * applies its layout margins to the whole stack rather than to the individual arranged
     * subviews, so the few items that are inset deeper than the cards need a container of their own.
     */
    private fun withExtraInset(content: UIView, horizontal: Double): UIView {
        val container = UIView()
        container.translatesAutoresizingMaskIntoConstraints = false
        container.addSubview(content)

        NSLayoutConstraint.activateConstraints(
            listOf(
                content.topAnchor.constraintEqualToAnchor(container.topAnchor),
                content.bottomAnchor.constraintEqualToAnchor(container.bottomAnchor),
                content.leadingAnchor.constraintEqualToAnchor(
                    container.leadingAnchor,
                    constant = horizontal
                ),
                content.trailingAnchor.constraintEqualToAnchor(
                    container.trailingAnchor,
                    constant = -horizontal
                ),
            )
        )

        return container
    }
}

/**
 * Draws a two stop linear gradient across its own bounds: the UIKit counterpart of
 * `Brush.linearGradient`, which by default also runs from the top leading to the bottom trailing
 * corner of the node it paints. A `cornerRadius` on the layer clips the drawing, which is what
 * turns the same view into both the rounded hero card and the round avatars.
 */
private class GradientView(from: Long, to: Long) : UIView(frame = CGRectZero.readValue()) {
    // The two stops as the flat run of RGBA components CGGradientCreateWithColorComponents expects
    private val components = doubleArrayOf(
        from.red, from.green, from.blue, from.alpha,
        to.red, to.green, to.blue, to.alpha,
    )

    init {
        translatesAutoresizingMaskIntoConstraints = false
        backgroundColor = UIColor.clearColor
        // Redraw the gradient on every bounds change instead of stretching the previous pixels
        contentMode = UIViewContentMode.UIViewContentModeRedraw
    }

    override fun drawRect(rect: CValue<CGRect>) {
        val context = UIGraphicsGetCurrentContext() ?: return
        val colorSpace = CGColorSpaceCreateDeviceRGB()
        val gradient = CGGradientCreateWithColorComponents(
            colorSpace,
            components.toCValues(),
            null,
            2.toULong(),
        )

        // Spanned over the whole view rather than over the dirty rect, so that a partial redraw
        // still paints the very same gradient
        bounds.useContents {
            CGContextDrawLinearGradient(
                context,
                gradient,
                CGPointMake(origin.x, origin.y),
                CGPointMake(origin.x + size.width, origin.y + size.height),
                0.toUInt(),
            )
        }

        CGGradientRelease(gradient)
        CGColorSpaceRelease(colorSpace)
    }
}

/**
 * The palette, the metrics and the gradient list of the Compose showcase, spelled out again in
 * UIKit terms. Kept as a copy on purpose: `ScrollShowcase.ios.kt` declares its originals privately
 * and in terms of Compose types, and this screen should not need Compose to draw itself.
 *
 * Compose `dp` and `sp` map one to one onto UIKit points, so all the numbers carry over unchanged.
 */
private object NativeShowcase {
    /** Mirrors `ShowcaseItemCount`. */
    const val ItemCount = 60

    // The vertical paddings the Compose items apply to themselves, which the stack view has to
    // translate into spacings between its arranged subviews
    const val ListPadding = 12.0
    const val BlockPadding = 8.0
    const val CardPadding = 5.0
    const val SectionHeaderTopPadding = 20.0
    const val SectionHeaderBottomPadding = 8.0
    const val FooterPadding = 32.0

    /** The horizontal padding shared by every card, applied once as the stack view margin. */
    const val HorizontalMargin = 16.0

    // Mirrors rememberShowcaseColors()
    val Background = dynamicColor(light = 0xFFF5F6FA, dark = 0xFF0D0E12)
    val Card = dynamicColor(light = 0xFFFFFFFF, dark = 0xFF1A1C23)
    val Title = dynamicColor(light = 0xFF14161C, dark = 0xFFF1F2F6)
    val Subtitle = dynamicColor(light = 0xFF70758A, dark = 0xFF9EA2B0)

    // The hero card of this tab keeps one look in both appearances: dark text over a light
    // gradient, the pastel of the vivid one the Compose HeroCard is painted with
    const val HeroGradientFrom = 0xFFDCD0FB
    const val HeroGradientTo = 0xFFFBD3E1
    val HeroText = uiColor(0xFF14161C)

    /** Mirrors `ShowcaseGradients`. */
    val Gradients = listOf(
        Gradient("Aurora", 0xFF7F5AF0, 0xFF2CB67D),
        Gradient("Sunset", 0xFFFF7E5F, 0xFFFEB47B),
        Gradient("Deep Sea", 0xFF2193B0, 0xFF6DD5ED),
        Gradient("Mulberry", 0xFFC33764, 0xFF1D2671),
        Gradient("Lime Soda", 0xFF56AB2F, 0xFFA8E063),
        Gradient("Coral Reef", 0xFFFF512F, 0xFFDD2476),
        Gradient("Blue Raspberry", 0xFF00B4DB, 0xFF0083B0),
        Gradient("Peach", 0xFFED4264, 0xFFFFEDBC),
        Gradient("Moonlit", 0xFF0F2027, 0xFF2C5364),
        Gradient("Cotton Candy", 0xFFD9A7C7, 0xFFFFFCDC),
        Gradient("Emerald", 0xFF348F50, 0xFF56B4D3),
        Gradient("Amethyst", 0xFF9D50BB, 0xFF6E48AA),
    )

    class Gradient(val name: String, val from: Long, val to: Long) {
        val hexes get() = "#${from.toString(16).uppercase().takeLast(6)} → " +
            "#${to.toString(16).uppercase().takeLast(6)}"
    }
}

/**
 * A [UIColor] resolving to [light] or [dark] depending on the current appearance, so that this
 * screen follows the system theme the way `isSystemInDarkTheme` makes the Compose ones follow it.
 */
private fun dynamicColor(light: Long, dark: Long): UIColor =
    UIColor.colorWithDynamicProvider { traitCollection: UITraitCollection? ->
        val isDark = traitCollection?.userInterfaceStyle ==
            UIUserInterfaceStyle.UIUserInterfaceStyleDark
        uiColor(if (isDark) dark else light)
    }

private fun uiColor(argb: Long): UIColor =
    UIColor(red = argb.red, green = argb.green, blue = argb.blue, alpha = argb.alpha)

private val Long.alpha get() = ((this shr 24) and 0xFF) / 255.0
private val Long.red get() = ((this shr 16) and 0xFF) / 255.0
private val Long.green get() = ((this shr 8) and 0xFF) / 255.0
private val Long.blue get() = (this and 0xFF) / 255.0

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
        val config = UISceneConfiguration(
            name = null,
            sessionRole = configurationForConnectingSceneSession.role
        )
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
