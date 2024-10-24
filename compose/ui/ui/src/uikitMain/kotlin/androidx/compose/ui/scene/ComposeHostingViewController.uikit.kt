/*
 * Copyright 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.compose.ui.scene

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ExperimentalComposeApi
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.LocalSystemTheme
import androidx.compose.ui.SystemTheme
import androidx.compose.ui.graphics.asComposeCanvas
import androidx.compose.ui.hapticfeedback.CupertinoHapticFeedback
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalInternalViewModelStoreOwner
import androidx.compose.ui.platform.PlatformContext
import androidx.compose.ui.platform.PlatformWindowContext
import androidx.compose.ui.uikit.ComposeUIViewControllerConfiguration
import androidx.compose.ui.uikit.InterfaceOrientation
import androidx.compose.ui.uikit.LocalInterfaceOrientation
import androidx.compose.ui.uikit.LocalUIViewController
import androidx.compose.ui.uikit.PlistSanityCheck
import androidx.compose.ui.uikit.density
import androidx.compose.ui.uikit.embedSubview
import androidx.compose.ui.uikit.utils.CMPViewController
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.asDpRect
import androidx.compose.ui.unit.roundToIntRect
import androidx.compose.ui.viewinterop.UIKitInteropAction
import androidx.compose.ui.viewinterop.UIKitInteropTransaction
import androidx.compose.ui.window.ComposeView
import androidx.compose.ui.window.FocusStack
import androidx.compose.ui.window.GestureEvent
import androidx.compose.ui.window.MetalView
import androidx.compose.ui.window.ViewControllerBasedLifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.coroutines.CoroutineContext
import kotlin.native.runtime.GC
import kotlin.native.runtime.NativeRuntimeApi
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.CValue
import kotlinx.cinterop.ExportObjCClass
import kotlinx.cinterop.readValue
import kotlinx.cinterop.useContents
import kotlinx.coroutines.Dispatchers
import org.jetbrains.skiko.OS
import org.jetbrains.skiko.OSVersion
import org.jetbrains.skiko.available
import platform.CoreGraphics.CGSize
import platform.CoreGraphics.CGSizeEqualToSize
import platform.Foundation.NSStringFromClass
import platform.UIKit.UIApplication
import platform.UIKit.UIEvent
import platform.UIKit.UIStatusBarAnimation
import platform.UIKit.UIStatusBarStyle
import platform.UIKit.UITraitCollection
import platform.UIKit.UIUserInterfaceLayoutDirection
import platform.UIKit.UIUserInterfaceStyle
import platform.UIKit.UIViewController
import platform.UIKit.UIViewControllerTransitionCoordinatorProtocol
import platform.UIKit.UIWindow
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

@OptIn(BetaInteropApi::class, ExperimentalComposeApi::class)
@ExportObjCClass
internal class ComposeHostingViewController(
    private val configuration: ComposeUIViewControllerConfiguration,
    private val content: @Composable () -> Unit,
    private val coroutineContext: CoroutineContext = Dispatchers.Main
) : CMPViewController(nibName = null, bundle = null) {
    private val lifecycleOwner = ViewControllerBasedLifecycleOwner()
    private val hapticFeedback = CupertinoHapticFeedback()

    private val rootView = ComposeView(
        onDidMoveToWindow = ::onDidMoveToWindow,
        onLayoutSubviews = ::onLayoutSubviews,
        useOpaqueConfiguration = configuration.opaque
    )
    private var isInsideSwiftUI = false
    private var mediator: ComposeSceneMediator? = null
    private val layers = UIKitComposeSceneLayersHolder()
    private val layoutDirection get() = getLayoutDirection()
    private var hasViewAppeared: Boolean = false

    fun hasInvalidations(): Boolean {
        return mediator?.hasInvalidations == true || layers.hasInvalidations
    }

    /*
     * Initial value is arbitrarily chosen to avoid propagating invalid value logic
     * It's never the case in real usage scenario to reflect that in type system
     */
    private val interfaceOrientationState: MutableState<InterfaceOrientation> = mutableStateOf(
        InterfaceOrientation.Portrait
    )
    private val systemThemeState: MutableState<SystemTheme> = mutableStateOf(SystemTheme.Unknown)

    var focusStack: FocusStack? = FocusStack()
    private val windowContext = PlatformWindowContext().apply {
        isWindowFocused = true
    }

    /*
     * On iOS >= 13.0 interfaceOrientation will be deduced from [UIWindowScene] of [UIWindow]
     * to which our [ComposeViewController] is attached.
     * It's never UIInterfaceOrientationUnknown, if accessed after owning [UIWindow] was made key and visible:
     * https://developer.apple.com/documentation/uikit/uiwindow/1621601-makekeyandvisible?language=objc
     */
    private val currentInterfaceOrientation: InterfaceOrientation?
        get() {
            // Modern: https://developer.apple.com/documentation/uikit/uiwindowscene/3198088-interfaceorientation?language=objc
            // Deprecated: https://developer.apple.com/documentation/uikit/uiapplication/1623026-statusbarorientation?language=objc
            return InterfaceOrientation.getByRawValue(
                if (available(OS.Ios to OSVersion(13))) {
                    view.window?.windowScene?.interfaceOrientation
                        ?: UIApplication.sharedApplication.statusBarOrientation
                } else {
                    UIApplication.sharedApplication.statusBarOrientation
                }
            )
        }

    override fun preferredStatusBarStyle(): UIStatusBarStyle =
        configuration.delegate.preferredStatusBarStyle
            ?: super.preferredStatusBarStyle()

    override fun preferredStatusBarUpdateAnimation(): UIStatusBarAnimation =
        configuration.delegate.preferredStatysBarAnimation
            ?: super.preferredStatusBarUpdateAnimation()

    override fun prefersStatusBarHidden(): Boolean =
        configuration.delegate.prefersStatusBarHidden
            ?: super.prefersStatusBarHidden()

    override fun loadView() {
        view = rootView
    }

    override fun viewDidLoad() {
        super.viewDidLoad()

        view.embedSubview(metalView)

        if (configuration.enforceStrictPlistSanityCheck) {
            PlistSanityCheck.performIfNeeded()
        }

        configuration.delegate.viewDidLoad()
        systemThemeState.value = traitCollection.userInterfaceStyle.asComposeSystemTheme()
    }

    override fun traitCollectionDidChange(previousTraitCollection: UITraitCollection?) {
        super.traitCollectionDidChange(previousTraitCollection)

        systemThemeState.value = traitCollection.userInterfaceStyle.asComposeSystemTheme()
    }

    private fun onLayoutSubviews() {
        windowContext.updateWindowContainerSize()

        mediator?.updateInteractionRect()
    }

    private fun onDidMoveToWindow(window: UIWindow?) {
        val windowContainer = if (configuration.platformLayers) {
            window ?: return
        } else {
            view
        }

        updateInterfaceOrientationState()

        windowContext.setWindowContainer(windowContainer)
    }

    private fun updateInterfaceOrientationState() {
        currentInterfaceOrientation?.let {
            interfaceOrientationState.value = it
        }
    }

    override fun viewWillTransitionToSize(
        size: CValue<CGSize>,
        withTransitionCoordinator: UIViewControllerTransitionCoordinatorProtocol
    ) {
        super.viewWillTransitionToSize(size, withTransitionCoordinator)

        updateInterfaceOrientationState()

        if (isInsideSwiftUI || presentingViewController != null) {
            // SwiftUI will do full layout and scene constraints update on each frame of orientation change animation
            // This logic is not needed

            // When presented modally, UIKit performs non-trivial hierarchy update during orientation change,
            // its logic is not feasible to integrate into
            return
        }

        // Happens during orientation change from LandscapeLeft to LandscapeRight, for example
        val isSameSizeTransition = view.frame.useContents {
            CGSizeEqualToSize(size, this.size.readValue())
        }
        if (isSameSizeTransition) {
            return
        }

        mediator?.performOrientationChangeAnimation(
            targetSize = size,
            coordinator = withTransitionCoordinator,
        )
    }

    override fun viewWillAppear(animated: Boolean) {
        super.viewWillAppear(animated)

        isInsideSwiftUI = checkIfInsideSwiftUI()
        createMediatorIfNeeded()

        lifecycleOwner.handleViewWillAppear()
        configuration.delegate.viewWillAppear(animated)
    }

    override fun viewDidAppear(animated: Boolean) {
        super.viewDidAppear(animated)
        hasViewAppeared = true
        mediator?.sceneDidAppear()
        layers.viewDidAppear()
        configuration.delegate.viewDidAppear(animated)
    }

    override fun viewWillDisappear(animated: Boolean) {
        super.viewWillDisappear(animated)
        hasViewAppeared = false
        mediator?.sceneWillDisappear()
        layers.viewWillDisappear()
        configuration.delegate.viewWillDisappear(animated)
    }

    @OptIn(NativeRuntimeApi::class)
    override fun viewDidDisappear(animated: Boolean) {
        super.viewDidDisappear(animated)

        dispatch_async(dispatch_get_main_queue()) {
            GC.collect()
        }

        lifecycleOwner.handleViewDidDisappear()
        configuration.delegate.viewDidDisappear(animated)
    }

    override fun viewControllerDidLeaveWindowHierarchy() {
        super.viewControllerDidLeaveWindowHierarchy()

        dispose()
    }

    @OptIn(NativeRuntimeApi::class)
    override fun didReceiveMemoryWarning() {
        println("didReceiveMemoryWarning")
        GC.collect()
        super.didReceiveMemoryWarning()
    }

    private fun createComposeSceneContext(platformContext: PlatformContext): ComposeSceneContext {
        return object : ComposeSceneContext {
            override val platformContext: PlatformContext = platformContext

            override fun createLayer(
                density: Density,
                layoutDirection: LayoutDirection,
                focusable: Boolean,
                compositionContext: CompositionContext
            ): ComposeSceneLayer {
                val layer = UIKitComposeSceneLayer(
                    onClosed = ::detachLayer,
                    createComposeSceneContext = ::createComposeSceneContext,
                    providingCompositionLocals = { ProvideContainerCompositionLocals(it) },
                    metalView = layers.metalView,
                    onGestureEvent = layers::onGestureEvent,
                    initDensity = density,
                    initLayoutDirection = layoutDirection,
                    configuration = configuration,
                    focusStack = if (focusable) focusStack else null,
                    windowContext = windowContext,
                    compositionContext = compositionContext,
                )

                attachLayer(layer)

                return layer
            }
        }
    }

    @OptIn(ExperimentalComposeApi::class)
    private fun createComposeScene(
        invalidate: () -> Unit,
        platformContext: PlatformContext,
        coroutineContext: CoroutineContext,
    ): ComposeScene = if (configuration.platformLayers) {
        PlatformLayersComposeScene(
            density = view.density,
            layoutDirection = layoutDirection,
            coroutineContext = coroutineContext,
            composeSceneContext = createComposeSceneContext(
                platformContext = platformContext
            ),
            invalidate = invalidate,
        )
    } else {
        CanvasLayersComposeScene(
            density = view.density,
            layoutDirection = layoutDirection,
            coroutineContext = coroutineContext,
            platformContext = platformContext,
            invalidate = invalidate,
        )
    }

    private fun createMediatorIfNeeded() {
        if (mediator == null) {
            mediator = createMediator()
        }
    }

    private fun createMediator() = ComposeSceneMediator(
        parentView = view,
        configuration = configuration,
        focusStack = focusStack,
        windowContext = windowContext,
        coroutineContext = coroutineContext,
        redrawer = metalView.redrawer,
        onGestureEvent = ::onGestureEvent,
        composeSceneFactory = ::createComposeScene,
    ).also { mediator ->
        mediator.updateInteractionRect()
        mediator.setContent {
            ProvideContainerCompositionLocals(content)
        }

        view.bringSubviewToFront(metalView)
    }

    private val metalView by lazy {
        MetalView(
            retrieveInteropTransaction = {
                mediator?.retrieveInteropTransaction() ?: object : UIKitInteropTransaction {
                    override val actions = emptyList<UIKitInteropAction>()
                    override val isInteropActive = false
                }
            },
            render = { canvas, nanoTime ->
                mediator?.render(canvas.asComposeCanvas(), nanoTime)
            }
        ).apply {
            canBeOpaque = configuration.opaque
        }
    }

    /**
     * When there is an ongoing gesture, we need notify redrawer about it. It should unconditionally
     * unpause CADisplayLink which affects frequency of polling UITouch events on high frequency
     * display and force it to match display refresh rate.
     *
     * Otherwise [UIEvent]s will be dispatched with the 60hz frequency.
     */
    private fun onGestureEvent(gestureEvent: GestureEvent) {
        metalView.needsProactiveDisplayLink = when (gestureEvent) {
            GestureEvent.BEGAN -> true
            GestureEvent.ENDED -> false
        }
    }

    private fun dispose() {
        lifecycleOwner.dispose()
        mediator?.dispose()
        rootView.dispose()
        mediator = null

        layers.dispose(hasViewAppeared)
    }

    private fun attachLayer(layer: UIKitComposeSceneLayer) {
        val window = checkNotNull(view.window) {
            "Cannot attach layer if the view is not in the window hierarchy"
        }

        layers.attach(window, layer, hasViewAppeared)
    }

    private fun detachLayer(layer: UIKitComposeSceneLayer) {
        layers.detach(layer, hasViewAppeared)
    }

    @Composable
    private fun ProvideContainerCompositionLocals(content: @Composable () -> Unit) =
        CompositionLocalProvider(
            LocalHapticFeedback provides hapticFeedback,
            LocalUIViewController provides this,
            LocalInterfaceOrientation provides interfaceOrientationState.value,
            LocalSystemTheme provides systemThemeState.value,
            LocalLifecycleOwner provides lifecycleOwner,
            LocalInternalViewModelStoreOwner provides lifecycleOwner,
            content = content
        )

    private fun ComposeSceneMediator.updateInteractionRect() {
        interactionBounds = with(density) {
            view.bounds.useContents { asDpRect() }.toRect().roundToIntRect()
        }
    }
}

@OptIn(BetaInteropApi::class)
private fun UIViewController.checkIfInsideSwiftUI(): Boolean {
    var parent = parentViewController

    while (parent != null) {
        val isUIHostingController = parent.`class`()?.let {
            val className = NSStringFromClass(it)
            // SwiftUI UIHostingController has mangled name depending on generic instantiation type,
            // It always contains UIHostingController substring though
            return className.contains("UIHostingController")
        } ?: false

        if (isUIHostingController) {
            return true
        }

        parent = parent.parentViewController
    }

    return false
}

private fun UIUserInterfaceStyle.asComposeSystemTheme(): SystemTheme {
    return when (this) {
        UIUserInterfaceStyle.UIUserInterfaceStyleLight -> SystemTheme.Light
        UIUserInterfaceStyle.UIUserInterfaceStyleDark -> SystemTheme.Dark
        else -> SystemTheme.Unknown
    }
}

private fun getLayoutDirection() =
    when (UIApplication.sharedApplication().userInterfaceLayoutDirection) {
        UIUserInterfaceLayoutDirection.UIUserInterfaceLayoutDirectionRightToLeft -> LayoutDirection.Rtl
        else -> LayoutDirection.Ltr
    }
