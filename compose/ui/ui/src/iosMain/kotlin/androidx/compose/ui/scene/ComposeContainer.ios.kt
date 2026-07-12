/*
 * Copyright 2025 The Android Open Source Project
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.LocalSystemTheme
import androidx.compose.ui.graphics.asComposeCanvas
import androidx.compose.ui.navigationevent.UIKitNavigationEventInput
import androidx.compose.ui.platform.DefaultArchitectureComponentsOwner
import androidx.compose.ui.platform.FrameRecomposer
import androidx.compose.ui.platform.IosMediaEnvironment
import androidx.compose.ui.platform.MotionDurationScaleImpl
import androidx.compose.ui.platform.PlatformContext
import androidx.compose.ui.platform.PlatformWindowContext
import androidx.compose.ui.uikit.ComposeContainerConfiguration
import androidx.compose.ui.uikit.LocalUIViewController
import androidx.compose.ui.uikit.PlistSanityCheck
import androidx.compose.ui.uikit.density
import androidx.compose.ui.uikit.embedSubview
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.util.fastForEachReversed
import androidx.compose.ui.viewinterop.UIKitInteropAction
import androidx.compose.ui.viewinterop.UIKitInteropTransaction
import androidx.compose.ui.window.ComposeContainerLifecycleDelegate
import androidx.compose.ui.window.ComposeContainerView
import androidx.compose.ui.window.FocusedViewsList
import androidx.compose.ui.window.MetalView
import androidx.compose.ui.window.SceneActiveStateListener
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.enableSavedStateHandles
import androidx.savedstate.SavedState
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import platform.UIKit.UIAccessibilityIsReduceMotionEnabled
import platform.UIKit.UIApplication
import platform.UIKit.UIResponder
import platform.UIKit.UITraitCollection
import platform.UIKit.UIUserInterfaceLayoutDirection
import platform.UIKit.UIUserInterfaceLayoutDirection.UIUserInterfaceLayoutDirectionLeftToRight
import platform.UIKit.UIUserInterfaceLayoutDirection.UIUserInterfaceLayoutDirectionRightToLeft
import platform.UIKit.UIUserInterfaceStyle
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene

/**
 * The class represents a common part of Compose integration for all iOS containers.
 */
internal class ComposeContainer(
    private val configuration: ComposeContainerConfiguration,
    private val content: @Composable () -> Unit,
    private val coroutineContext: CoroutineContext,
    private val lifecycleDelegate: ComposeContainerLifecycleDelegate
) {

    val view = ComposeContainerView(
        transparentForTouches = false,
        useOpaqueConfiguration = configuration.opaque,
    )

    private var mediator: ComposeSceneMediator? = null
    private val windowContext = PlatformWindowContext()
    private var layersHolder: ComposeLayersHolder? = null
    private var layoutDirection = getApplicationLayoutDirection()
        set(value) {
            field = value
            mediator?.layoutDirection = value
            navigationEventInput.layoutDirection = value
        }
    private val motionDurationScale = MotionDurationScaleImpl()
    private var activeStateListener: SceneActiveStateListener? = null
    private var sceneJob: Job = Job().also {
        // The initial state of the container considered as "not active".
        // The `initializeComposeScene` must be called to set the active `sceneJob`.
        it.cancel()
    }
    private val viewModelStore = ViewModelStore()
    private var savedState: SavedState? = null
    private var mediatorComponentsOwner: DefaultArchitectureComponentsOwner? = null
    private val architectureComponentsOwner: DefaultArchitectureComponentsOwner
        get() = mediatorComponentsOwner
            ?: error("ArchitectureComponentsOwner is not initialized yet.")

    private val iosMediaEnvironment = IosMediaEnvironment(windowContext.windowInfo, ::windowScene)

    private val navigationEventInput = UIKitNavigationEventInput(
        density = view.density,
        initialLayoutDirection = layoutDirection,
        getTopLeftOffsetInWindow = { IntOffset.Zero }, //full screen
        endEdgePanGestureBehavior = configuration.endEdgePanGestureBehavior
    )
    val hasInteropViews: Boolean get() = mediator?.hasInteropViews ?: false


    private val focusedViewsList = FocusedViewsList()

    val currentLifecycleState: Lifecycle.State get() =
        architectureComponentsOwner.lifecycle.currentState

    init {
        if (configuration.enforceStrictPlistSanityCheck) {
            PlistSanityCheck.performIfNeeded()
        }
        lifecycleDelegate.runOnDeinit {
            windowContext.dispose()
        }
    }

    fun nestedCoroutineScope(
        addedContext: CoroutineContext = EmptyCoroutineContext
    ): CoroutineScope {
        return CoroutineScope(coroutineContext + addedContext + Job(parent = sceneJob))
    }

    fun prepareAndGetSizeTransitionAnimation(withProgress: suspend ((Float) -> Unit) -> Unit): suspend () -> Unit {
        return mediator?.prepareAndGetSizeTransitionAnimation(withProgress) ?: {}
    }

    fun hasInvalidations(): Boolean {
        return mediator?.hasInvalidations == true || layersHolder?.layersViewController?.hasInvalidations == true
    }


    private fun onLayoutSubviews() {
        windowContext.updateWindowContainerSize()
    }

    private fun onTraitCollectionDidChange(previousTraitCollection: UITraitCollection?) {
        layoutDirection = view.effectiveUserInterfaceLayoutDirection.asLayoutDirection()
    }

    private fun onDidMoveToWindow(window: UIWindow?) {
        navigationEventInput.onDidMoveToWindow(window, view)
        iosMediaEnvironment.onDidMoveToWindow(window)
        window ?: return

        layersHolder?.layersViewController?.containerWindow = view.window
        windowContext.window = window
        updateMotionSpeed()
        lifecycleDelegate.windowScene = window.windowScene
    }

    fun updateInterfaceOrientationState() = iosMediaEnvironment.updateInterfaceOrientationState()

    fun sceneDidAppear() {
        mediator?.sceneDidAppear()

        // Because the container view can change during the modal transition animation,
        // the gesture handlers and layers view are added back when the animation ends.
        navigationEventInput.onDidMoveToWindow(view.window, view)
    }

    fun sceneWillDisappear() {
        mediator?.sceneWillDisappear()

        navigationEventInput.onDidMoveToWindow(null, view)
    }

    fun updateUserInterfaceStyle(style: UIUserInterfaceStyle) = iosMediaEnvironment.updateUserInterfaceStyle(style)

    fun initializeComposeScene() {
        sceneJob = Job()
        val sceneCoroutineContext = coroutineContext + motionDurationScale + sceneJob
        val metalView = MetalView(
            retrieveInteropTransaction = {
                mediator?.retrieveInteropTransaction() ?: object : UIKitInteropTransaction {
                    override val actions = emptyList<UIKitInteropAction>()
                    override val isInteropActive = false
                }
            },
            useSeparateRenderThreadWhenPossible = configuration.parallelRendering,
            render = { canvas, nanoTime ->
                mediator?.render(canvas.asComposeCanvas(), nanoTime)
            }
        )
        metalView.canBeOpaque = configuration.opaque
        val holder = ComposeLayersHolder(
            useSeparateRenderThreadWhenPossible = configuration.parallelRendering,
            coroutineContext = sceneCoroutineContext,
            view = view
        ).also {
            layersHolder = it
        }

        mediatorComponentsOwner = DefaultArchitectureComponentsOwner(
            savedState = savedState,
            viewModelStore = viewModelStore
        )
        architectureComponentsOwner.enableSavedStateHandles()
        lifecycleDelegate.onLifecycleStateUpdated = architectureComponentsOwner::setLifecycleState

        mediator = ComposeSceneMediator(
            onFocusBehavior = configuration.onFocusBehavior,
            isClearFocusOnMouseDownEnabled = configuration.isClearFocusOnMouseDownEnabled,
            focusedViewsList = focusedViewsList,
            windowContext = windowContext,
            architectureComponentsOwner = architectureComponentsOwner,
            coroutineContext = sceneCoroutineContext,
            redrawer = metalView.redrawer,
            composeSceneFactory = { invalidate, context, frameRecomposer ->
                createComposeScene(invalidate, context, holder, frameRecomposer)
            },
            navigationEventInput = navigationEventInput,
            mediaEnvironment = iosMediaEnvironment,
        ).also { mediator ->
            view.embedSubview(mediator.backgroundView)
            view.updateMetalView(
                metalView = metalView,
                onDidMoveToWindow = ::onDidMoveToWindow,
                onLayoutSubviews = ::onLayoutSubviews,
                onTraitCollectionDidChange = ::onTraitCollectionDidChange,
            )
            view.embedSubview(mediator.overlayView)

            mediator.setContent {
                ProvideContainerCompositionLocals(content)
            }
        }

        activeStateListener = SceneActiveStateListener(
            getScene = ::windowScene
        ) { isSceneActive ->
            if (isSceneActive) {
                updateMotionSpeed()
            }
        }

        iosMediaEnvironment.initialize()

        architectureComponentsOwner.navigationEventDispatcher.addInput(navigationEventInput)
        lifecycleDelegate.windowScene = windowScene
        navigationEventInput.onDidMoveToWindow(view.window, view)
        onFocusConditionsChanged()
    }

    fun disposeComposeScene() {
        // Store the current state in the local savedState property. It is used to
        // provide the saved state to the next Compose scene when the container re-enters
        // the window hierarchy.
        savedState = architectureComponentsOwner.saveState()

        sceneJob.cancel()

        view.updateMetalView(metalView = null)
        navigationEventInput.onDidMoveToWindow(null, view)
        architectureComponentsOwner.navigationEventDispatcher.removeInput(navigationEventInput)

        mediator = null

        activeStateListener?.dispose()
        activeStateListener = null

        layersHolder = null

        iosMediaEnvironment.dispose()
    }

    private fun createComposeSceneContext(
        platformContext: PlatformContext,
        layersHolder: ComposeLayersHolder,
        frameRecomposer: FrameRecomposer,
    ): ComposeSceneContext {
        return object : ComposeSceneContext {
            override val platformContext: PlatformContext = platformContext

            override fun createLayer(
                density: Density,
                layoutDirection: LayoutDirection,
                focusable: Boolean,
                consumePointerInputOutside: Boolean,
            ): ComposeSceneLayer {
                val layer = UIKitComposeSceneLayer(
                    onClosed = {
                        layersHolder.getLayersViewController().detach(it)
                        onFocusConditionsChanged()
                    },
                    createComposeSceneContext = {
                        createComposeSceneContext(it, layersHolder, frameRecomposer)
                    },
                    hostCompositionLocals = { ProvideContainerCompositionLocals(it) },
                    layersViewController = layersHolder.getLayersViewController(),
                    initialLayoutDirection = layoutDirection,
                    configuration = configuration,
                    onFocusConditionsChanged = ::onFocusConditionsChanged,
                    focusedViewsList = if (focusable) focusedViewsList.childFocusedViewsList() else null,
                    consumePointerInputOutside = consumePointerInputOutside,
                    // FIXME: Do not use [compositionContext.effectCoroutineContext] for
                    //  [FrameRecomposer] creation.
                    parentCoroutineContext = frameRecomposer.compositionContext.effectCoroutineContext,
                    ownerProvider = architectureComponentsOwner,
                    mediaEnvironment = iosMediaEnvironment
                )

                layersHolder.getLayersViewController().attach(layer)
                onFocusConditionsChanged()

                return layer
            }
        }
    }

    private fun createComposeScene(
        invalidate: () -> Unit,
        platformContext: PlatformContext,
        layersHolder: ComposeLayersHolder,
        frameRecomposer: FrameRecomposer,
    ): ComposeScene = PlatformLayersComposeScene(
        frameRecomposer = frameRecomposer,
        density = view.density,
        layoutDirection = layoutDirection,
        composeSceneContext = createComposeSceneContext(
            platformContext = platformContext,
            layersHolder = layersHolder,
            frameRecomposer = frameRecomposer,
        ),
        // TODO: Split these into UIKit layout vs display invalidation. `invalidateLayout`
        // should call into layout scheduling, while `invalidateDraw` should schedule display.
        invalidateLayout = invalidate,
        invalidateDraw = invalidate,
    )

    /**
     * Enables or disables accessibility for each layer, as well as the root mediator, taking into
     * account layer order and ability to overlay underlying content.
     */
    private fun onFocusConditionsChanged() {
        var isFocusEnabled = true
        layersHolder?.layersViewController?.withLayers {
            it.fastForEachReversed { layer ->
                layer.isFocusEnabled = isFocusEnabled
                isFocusEnabled = isFocusEnabled && !layer.focusable
            }
        }
        mediator?.isFocusEnabled = isFocusEnabled
    }

    private val containingViewController: UIViewController get() {
        var responder: UIResponder? = view
        while (responder != null) {
            if (responder is UIViewController) {
                return responder
            }
            responder = responder.nextResponder
        }
        error("Compose Container mut be located inside a UIViewController")
    }

    @Composable
    private fun ProvideContainerCompositionLocals(content: @Composable () -> Unit) =
        CompositionLocalProvider(
            LocalUIViewController provides containingViewController,
            LocalSystemTheme provides iosMediaEnvironment.systemTheme,
            content = content
        )

    private fun updateMotionSpeed() {
        motionDurationScale.scaleFactor = if (UIAccessibilityIsReduceMotionEnabled()) {
            // 0f would cause motion to finish in the next frame callback.
            // See [MotionDurationScale.scaleFactor] for more details.
            0f
        } else {
            1f / (view.window?.layer?.speed?.takeIf { it > 0 } ?: 1f)
        }
    }

    private val windowScene: UIWindowScene?
        get() = view.window?.windowScene
}

private fun getApplicationLayoutDirection() =
    when (UIApplication.sharedApplication().userInterfaceLayoutDirection) {
        UIUserInterfaceLayoutDirectionRightToLeft -> LayoutDirection.Rtl
        else -> LayoutDirection.Ltr
    }

private class ComposeLayersHolder(
    private val useSeparateRenderThreadWhenPossible: Boolean,
    private val coroutineContext: CoroutineContext,
    private val view: ComposeContainerView
) {
    var layersViewController: ComposeLayersViewController? = null
        private set

    fun getLayersViewController(): ComposeLayersViewController {
        return layersViewController ?: run {
            val layers = ComposeLayersViewController(
                useSeparateRenderThreadWhenPossible = useSeparateRenderThreadWhenPossible,
                coroutineContext = coroutineContext,
                hostingComposeView = view
            )
            layers.containerWindow = view.window
            layersViewController = layers
            layers
        }
    }
}


private fun UIUserInterfaceLayoutDirection.asLayoutDirection(): LayoutDirection = when (this) {
    UIUserInterfaceLayoutDirectionLeftToRight -> LayoutDirection.Ltr
    UIUserInterfaceLayoutDirectionRightToLeft -> LayoutDirection.Rtl
    else -> {
        println("ComposeContainer: unexpected UIUserInterfaceLayoutDirection=$this, falling back to Ltr")
        LayoutDirection.Ltr
    }
}
