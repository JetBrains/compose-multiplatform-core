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
import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.LocalSystemTheme
import androidx.compose.ui.graphics.asComposeCanvas
import androidx.compose.ui.navigationevent.IosBackNavigationEventInput
import androidx.compose.ui.platform.DefaultArchitectureComponentsOwner
import androidx.compose.ui.platform.FrameChoreographer
import androidx.compose.ui.platform.MotionDurationScaleImpl
import androidx.compose.ui.platform.PlatformContext
import androidx.compose.ui.platform.WindowContext
import androidx.compose.ui.platform.registerSkikoComposeImplementation
import androidx.compose.ui.uikit.ComposeContainerConfiguration
import androidx.compose.ui.uikit.InterfaceOrientation
import androidx.compose.ui.uikit.LocalUIViewController
import androidx.compose.ui.uikit.PlistSanityCheck
import androidx.compose.ui.uikit.density
import androidx.compose.ui.uikit.embedSubview
import androidx.compose.ui.uikit.utils.CMPKeyValueObserver
import androidx.compose.ui.uikit.utils.CMPUIWindowSceneUtils
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
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.CPointed
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.nativeHeap
import kotlinx.cinterop.ptr
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import org.jetbrains.skiko.SystemTheme
import platform.Foundation.NSKeyValueObservingOptionNew
import platform.Foundation.addObserver
import platform.Foundation.removeObserver
import platform.UIKit.UIAccessibilityIsReduceMotionEnabled
import platform.UIKit.UIApplication
import platform.UIKit.UIResponder
import platform.UIKit.UIUserInterfaceLayoutDirection
import platform.UIKit.UIUserInterfaceLayoutDirection.UIUserInterfaceLayoutDirectionLeftToRight
import platform.UIKit.UIUserInterfaceLayoutDirection.UIUserInterfaceLayoutDirectionRightToLeft
import platform.UIKit.UIUserInterfaceStyle
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene
import platform.objc.OBJC_ASSOCIATION_RETAIN
import platform.objc.objc_getAssociatedObject
import platform.objc.objc_setAssociatedObject

/**
 * The class represents a common part of Compose integration for all iOS containers.
 */
internal class ComposeContainer(
    private val configuration: ComposeContainerConfiguration,
    private val content: @Composable () -> Unit,
    private val lifecycleDelegate: ComposeContainerLifecycleDelegate
) {
    // Register before any property initializer / scene setup below touches the Skiko backend, so
    // every iOS entry point (ComposeHostingView, ComposeHostingViewController) is covered.
    init {
        registerSkikoComposeImplementation()
    }

    val view = ComposeContainerView(
        transparentForTouches = false,
        useOpaqueConfiguration = configuration.opaque,
    )

    private val frameChoreographer: FrameChoreographer?
        get() = view.window?.windowScene?.let { FrameChoreographer.choreographerForScene(it) }

    private var mediator: ComposeSceneMediator? = null
    private val windowContext = WindowContext()
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

    private val interfaceOrientationObserver = SceneGeometryObserver {
        updateInterfaceOrientationState()
    }
    private val navigationEventInput = IosBackNavigationEventInput(
        density = view.density,
        initialLayoutDirection = layoutDirection,
        getTopLeftOffsetInWindow = { IntOffset.Zero }, //full screen
        endEdgePanGestureBehavior = configuration.endEdgePanGestureBehavior
    )
    private var layoutInvalidationHandler: LayoutInvalidationHandler? = null
    val hasInteropViews: Boolean get() = mediator?.hasInteropViews ?: false

    /*
     * Initial value is arbitrarily chosen to avoid propagating invalid value logic
     * It's never the case in the real usage scenario to reflect that in type system
     */
    private val interfaceOrientationState: MutableState<InterfaceOrientation> = mutableStateOf(
        InterfaceOrientation.Portrait
    )
    private val systemThemeState: MutableState<SystemTheme> = mutableStateOf(SystemTheme.UNKNOWN)

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
        val activeContext = mediator?.coroutineContext ?: Dispatchers.Main
        return CoroutineScope(activeContext + addedContext + Job(parent = sceneJob))
    }

    fun prepareAndGetSizeTransitionAnimation(withProgress: suspend ((Float) -> Unit) -> Unit): suspend () -> Unit {
        return mediator?.prepareAndGetSizeTransitionAnimation(withProgress) ?: {}
    }

    fun hasInvalidations(): Boolean {
        return mediator?.hasInvalidations == true || layersHolder?.layersViewController?.hasInvalidations == true
    }

    private val currentInterfaceOrientation: InterfaceOrientation?
        get() {
            return InterfaceOrientation.getByRawValue(
                CMPUIWindowSceneUtils.interfaceOrientationForWindowScene(windowScene)
            )
        }

    private fun onLayoutSubviews() {
        windowContext.updateWindowContainerSize()

        mediator?.measureAndLayout()
    }

    private fun onTraitCollectionDidChange() {
        layoutDirection = view.effectiveUserInterfaceLayoutDirection.asLayoutDirection()
    }

    private fun onDidMoveToWindow(window: UIWindow?) {
        navigationEventInput.onDidMoveToWindow(window, view)
        interfaceOrientationObserver.windowScene = window?.windowScene

        window ?: return

        updateInterfaceOrientationState()

        layersHolder?.layersViewController?.containerWindow = view.window
        windowContext.window = window
        updateMotionSpeed()
        lifecycleDelegate.windowScene = window.windowScene
    }

    fun updateInterfaceOrientationState() {
        currentInterfaceOrientation?.let {
            interfaceOrientationState.value = it
        }
    }

    fun sceneDidAppear() {
        mediator?.sceneDidAppear()

        // Because the container view can change during the modal transition animation,
        // the gesture handlers and layers view are added back when the animation ends.
        navigationEventInput.onDidMoveToWindow(view.window, view)

        layoutInvalidationHandler?.invalidateLayoutIfNeeded()
        view.setNeedsDisplay()
    }

    fun sceneWillDisappear() {
        mediator?.sceneWillDisappear()

        navigationEventInput.onDidMoveToWindow(null, view)
    }

    fun updateUserInterfaceStyle(style: UIUserInterfaceStyle) {
        systemThemeState.value = style.asComposeSystemTheme()
    }

    fun initializeComposeScene() {
        sceneJob = Job()
        val frameChoreographer = frameChoreographer ?: error("No window scene found")
        val containerCoroutineContext = frameChoreographer.coroutineContext + motionDurationScale + sceneJob

        val layoutInvalidationHandler = LayoutInvalidationHandler(containerCoroutineContext) {
            view.setNeedsLayout()
            view.invalidateIntrinsicContentSize()
        }
        this.layoutInvalidationHandler = layoutInvalidationHandler

        val metalView = MetalView(
            retrieveInteropTransaction = {
                mediator?.retrieveInteropTransaction() ?: object : UIKitInteropTransaction {
                    override val actions = emptyList<UIKitInteropAction>()
                    override val isInteropActive = false
                }
            },
            useSeparateRenderThreadWhenPossible = configuration.parallelRendering,
            draw = { canvas ->
                layoutInvalidationHandler.postponeLayoutInvalidationCalls {
                    mediator?.draw(canvas.asComposeCanvas())
                }
            }
        )
        metalView.canBeOpaque = configuration.opaque
        val holder = ComposeLayersHolder(
            useSeparateRenderThreadWhenPossible = configuration.parallelRendering,
            coroutineContext = containerCoroutineContext,
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
            frameChoreographer = frameChoreographer,
            onFocusBehavior = configuration.onFocusBehavior,
            isClearFocusOnMouseDownEnabled = configuration.isClearFocusOnMouseDownEnabled,
            focusedViewsList = focusedViewsList,
            windowContext = windowContext,
            architectureComponentsOwner = architectureComponentsOwner,
            coroutineContext = containerCoroutineContext,
            composeSceneFactory = { context ->
                PlatformLayersComposeScene(
                    frameRecomposer = frameChoreographer.frameRecomposer,
                    density = view.density,
                    layoutDirection = layoutDirection,
                    composeSceneContext = createComposeSceneContext(
                        frameChoreographer = frameChoreographer,
                        platformContext = context,
                        layersHolder = holder,
                        containerCoroutineContext = containerCoroutineContext
                    ),
                    invalidateLayout = {
                        layoutInvalidationHandler.invalidateLayoutIfNeeded()
                    },
                    invalidateDraw = {
                        view.setNeedsDisplay()
                    },
                )
            },
            navigationEventInput = navigationEventInput,
            interfaceOrientationState = interfaceOrientationState,
        ).also { mediator ->
            view.embedSubview(mediator.backgroundView)
            view.updateMetalView(
                metalView = metalView,
                onDidMoveToWindow = ::onDidMoveToWindow,
                onLayoutSubviews = ::onLayoutSubviews,
                onTraitCollectionDidChange = ::onTraitCollectionDidChange,
            )
            view.embedSubview(mediator.overlayView)

            mediator.setContent(parentCompositionContext = view.findParentCompositionContext()) {
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

        interfaceOrientationObserver.isObservingEnabled = true

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

        interfaceOrientationObserver.isObservingEnabled = false
    }

    private fun createComposeSceneContext(
        frameChoreographer: FrameChoreographer,
        platformContext: PlatformContext,
        layersHolder: ComposeLayersHolder,
        containerCoroutineContext: CoroutineContext,
    ): ComposeSceneContext {
        return object : ComposeSceneContext {
            override val platformContext: PlatformContext = platformContext

            override fun createLayer(
                density: Density,
                layoutDirection: LayoutDirection,
                focusable: Boolean,
                consumePointerInputOutside: Boolean,
            ): ComposeSceneLayer {
                val layer = IosComposeSceneLayer(
                    frameChoreographer = frameChoreographer,
                    onClosed = {
                        layersHolder.getLayersViewController().detach(it)
                        onFocusConditionsChanged()
                    },
                    createComposeSceneContext = {
                        createComposeSceneContext(
                            frameChoreographer = frameChoreographer,
                            platformContext = it,
                            layersHolder = layersHolder,
                            containerCoroutineContext = containerCoroutineContext
                        )
                    },
                    layersViewController = layersHolder.getLayersViewController(),
                    initialLayoutDirection = layoutDirection,
                    configuration = configuration,
                    onFocusConditionsChanged = ::onFocusConditionsChanged,
                    focusedViewsList = if (focusable) focusedViewsList.childFocusedViewsList() else null,
                    consumePointerInputOutside = consumePointerInputOutside,
                    parentCoroutineContext = containerCoroutineContext,
                    ownerProvider = architectureComponentsOwner,
                    interfaceOrientationState = interfaceOrientationState,
                    invalidateLayout = { layersHolder.getLayersViewController().invalidateLayout() },
                    invalidateDraw = { layersHolder.getLayersViewController().invalidateDraw() },
                )

                layersHolder.getLayersViewController().attach(layer)
                onFocusConditionsChanged()

                return layer
            }
        }
    }

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
            LocalSystemTheme provides systemThemeState.value,
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

private fun UIUserInterfaceStyle.asComposeSystemTheme(): SystemTheme {
    return when (this) {
        UIUserInterfaceStyle.UIUserInterfaceStyleLight -> SystemTheme.LIGHT
        UIUserInterfaceStyle.UIUserInterfaceStyleDark -> SystemTheme.DARK
        else -> SystemTheme.UNKNOWN
    }
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

private class SceneGeometryObserver(
    val onGeometryChanged: () -> Unit
) : CMPKeyValueObserver() {
    private val observingKey = "effectiveGeometry"

    var windowScene: UIWindowScene? = null
        set(value) {
            if (field == value) return
            removeObserverIfNeeded()
            field = value
            addObserverIfNeeded()
        }

    var isObservingEnabled = false
        set(value) {
            if (field == value) return
            field = value
            if (value) {
                addObserverIfNeeded()
            } else {
                removeObserverIfNeeded()
            }
        }

    private var isObservingAdded = false

    private fun addObserverIfNeeded() {
        if (isObservingEnabled && !isObservingAdded) {
            isObservingAdded = true
            windowScene?.addObserver(this, observingKey, NSKeyValueObservingOptionNew, null)
        }
    }

    private fun removeObserverIfNeeded() {
        windowScene?.removeObserver(this, observingKey)
        isObservingAdded = false
    }

    override fun observeValueForKeyPath(
        keyPath: String?,
        ofObject: Any?,
        change: Map<Any?, *>?,
        context: CPointer<out CPointed>?
    ) {
        onGeometryChanged()
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

/**
 * Prevent cases where invalidate layout may be called during the rendering process,
 * which lead to another frame rendering during the same frame.
 */
internal class LayoutInvalidationHandler(
    coroutineContext: CoroutineContext,
    private var doInvalidateLayout: () -> Unit
) {
    private var invalidationPostponed = false
    private var hasInvalidations = false
    private val scope = CoroutineScope(coroutineContext)

    init {
        coroutineContext.job.invokeOnCompletion {
            doInvalidateLayout = {}
        }
    }

    fun invalidateLayoutIfNeeded() {
        if (invalidationPostponed) {
            hasInvalidations = true
            return
        }
        doInvalidateLayout()
        hasInvalidations = false
    }

    fun postponeLayoutInvalidationCalls(block: () -> Unit) {
        assert(!invalidationPostponed)
        invalidationPostponed = true
        try {
            block()
        } finally {
            invalidationPostponed = false
        }
        if (hasInvalidations) {
            scope.launch {
                invalidateLayoutIfNeeded()
            }
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private val compositionContextAssociationKey: COpaquePointer = nativeHeap.alloc<IntVar>().ptr

@OptIn(ExperimentalForeignApi::class)
internal var UIResponder.attachedCompositionContext: CompositionContext?
    get() = objc_getAssociatedObject(this, compositionContextAssociationKey) as? CompositionContext
    set(value) {
        objc_setAssociatedObject(this, compositionContextAssociationKey, value, OBJC_ASSOCIATION_RETAIN)
    }

internal fun UIResponder.findParentCompositionContext(): CompositionContext {
    if (this is UIWindow) {
        return FrameChoreographer.choreographerForScene(
            scene = windowScene ?: error("Window scene is null")
        ).frameRecomposer.compositionContext
    }
    this.attachedCompositionContext?.let {
        return it
    }
    return nextResponder?.findParentCompositionContext()
        ?: error("Unable to find parent composition context")
}
