/*
 * Copyright 2026 The Android Open Source Project
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

@file:OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
@file:Suppress("DuplicatedCode")

package androidx.compose.ui.desktop.windows

import androidx.annotation.MainThread
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.LocalSystemTheme
import androidx.compose.ui.SystemTheme
import androidx.compose.ui.desktop.ApplicationSession
import androidx.compose.ui.desktop.CaptionButtonKind
import androidx.compose.ui.desktop.CaptionButtonsHostWindow
import androidx.compose.ui.desktop.ClipboardItemsEntry
import androidx.compose.ui.desktop.DefaultCustomTitleBarHeightForAir
import androidx.compose.ui.desktop.FrameDispatcher
import androidx.compose.ui.desktop.FramePacer
import androidx.compose.ui.desktop.IconDecoratedWindow
import androidx.compose.ui.desktop.InteractiveMoveInitiator
import androidx.compose.ui.desktop.KdtDragAndDropManager
import androidx.compose.ui.desktop.KdtDragAndDropTransferable
import androidx.compose.ui.desktop.LightweightWindowId
import androidx.compose.ui.desktop.LocalTextInputSessionOwner
import androidx.compose.ui.desktop.LocalWindow
import androidx.compose.ui.desktop.PositionAwareWindow
import androidx.compose.ui.desktop.Window
import androidx.compose.ui.desktop.WindowCloseRequestReason
import androidx.compose.ui.desktop.WindowData
import androidx.compose.ui.desktop.WindowScope
import androidx.compose.ui.desktop.draganddrop.DragAndDropImage
import androidx.compose.ui.desktop.logging.logger
import androidx.compose.ui.draganddrop.DragAndDropTransferAction
import androidx.compose.ui.draganddrop.DragAndDropTransferData
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeCanvas
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.InputModeManagerImpl
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerKeyboardModifiers
import androidx.compose.ui.node.InternalCoreApi
import androidx.compose.ui.platform.DefaultArchitectureComponentsOwner
import androidx.compose.ui.platform.DefaultTextToolbar
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalPointerIconService
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.PlatformContext
import androidx.compose.ui.platform.PlatformDragAndDropManager
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.scene.CanvasLayersComposeScene
import androidx.compose.ui.scene.ComposeScene
import androidx.compose.ui.scene.withFrameTransaction
import androidx.compose.ui.semantics.SemanticsOwner
import androidx.compose.ui.semantics.TestDataMode
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.takeOrElse
import androidx.compose.ui.unit.width
import androidx.compose.ui.window.WindowDecoration
import androidx.compose.ui.window.WindowPlacement
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.enableSavedStateHandles
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.desktop.win32.DataObject
import org.jetbrains.desktop.win32.DragDropContinueResult
import org.jetbrains.desktop.win32.DragDropEffect
import org.jetbrains.desktop.win32.DragDropManager
import org.jetbrains.desktop.win32.DragDropModifier
import org.jetbrains.desktop.win32.DragDropModifiers
import org.jetbrains.desktop.win32.DragImage
import org.jetbrains.desktop.win32.DragSource
import org.jetbrains.desktop.win32.DropTarget
import org.jetbrains.desktop.win32.FileDialog
import org.jetbrains.desktop.win32.FontSmoothing
import org.jetbrains.desktop.win32.FontSmoothingOrientation
import org.jetbrains.desktop.win32.FontSmoothingType
import org.jetbrains.desktop.win32.LogicalPoint
import org.jetbrains.desktop.win32.LogicalRect
import org.jetbrains.desktop.win32.LogicalSize
import org.jetbrains.desktop.win32.NCHitTestResult
import org.jetbrains.desktop.win32.PhysicalPoint
import org.jetbrains.desktop.win32.PhysicalSize
import org.jetbrains.desktop.win32.Screen
import org.jetbrains.desktop.win32.WindowParams
import org.jetbrains.desktop.win32.WindowStyle
import org.jetbrains.desktop.win32.WindowSystemBackdropType
import org.jetbrains.desktop.win32.WindowTitleBarKind
import org.jetbrains.desktop.win32.Event as Win32Event
import org.jetbrains.desktop.win32.EventHandlerResult as Win32EventHandlerResult

class WindowsWindow internal constructor(
    private val application: WindowsApplication,
    internal val session: ApplicationSession,
    private val onCloseRequest: (WindowCloseRequestReason) -> Unit,
    reusedNativeWindow: org.jetbrains.desktop.win32.Window? = null,
    reusedAngleViewContext: AngleViewContext? = null,
    reusedPointerIconService: WindowsPointerIconService? = null,
    reusedInputMode: InputMode? = null,
    override val nativeWindow: org.jetbrains.desktop.win32.Window =
        reusedNativeWindow ?: application.nativeApplication.newWindow(),
) : PositionAwareWindow, InteractiveMoveInitiator, IconDecoratedWindow, CaptionButtonsHostWindow {

    // TODO add invalidation on WM_SETTINGCHANGE
    private val pixelGeometry = currentPixelGeometry()

    private fun currentPixelGeometry(): org.jetbrains.skia.PixelGeometry {
        if (FontSmoothing.getCurrent() == FontSmoothing.Disabled) {
            return org.jetbrains.skia.PixelGeometry.UNKNOWN
        }
        if (FontSmoothingType.getCurrent() != FontSmoothingType.ClearType) {
            return org.jetbrains.skia.PixelGeometry.UNKNOWN
        }
        return when (FontSmoothingOrientation.getCurrent()) {
            FontSmoothingOrientation.Rgb -> org.jetbrains.skia.PixelGeometry.RGB_H
            FontSmoothingOrientation.Bgr -> org.jetbrains.skia.PixelGeometry.BGR_H
        }
    }

    override val id: LightweightWindowId = nativeWindow.assignNewLightweightWindowId()

    @Volatile
    private var isDisposed = false

    private val isReusedNativeWindow = reusedNativeWindow != null

    /**
     * The client size from the latest native size/draw event; what the frame loop presents at.
     * Null until the first native size event (a fresh window starts 1×1 and is resized during
     * creation, so a WM_NCCALCSIZE/WindowDraw arrives before any real frame).
     *
     * A reused native window is already at its final bounds and DPI, so Windows will NOT re-send
     * WM_NCCALCSIZE/WindowDraw for it (see the geometry seeding below). Left null, the first frame
     * the reused window schedules from setContent would find no size and renderCurrentFrame() would
     * bail forever. Seed it from the live HWND's physical client size (logical client size × the
     * window scale) so that first frame renders at the correct size.
     */
    private var latestPhysicalSize: PhysicalSize? =
        reusedNativeWindow?.let { window ->
            val scale = window.getScaleFactor()
            val clientSize = window.getClientSize()
            PhysicalSize(
                (clientSize.width * scale).roundToInt(),
                (clientSize.height * scale).roundToInt(),
            )
        }

    // When reusing a native window, share the angle view context that was created for it so we
    // neither destroy nor recreate the OpenGL/ANGLE resources tied to the surviving HWND.
    private val angleViewContextLazy = lazy {
        reusedAngleViewContext ?: AngleViewContext.create(application.nativeApplication, nativeWindow)
    }
    internal val angleViewContext: AngleViewContext by angleViewContextLazy

    private var titleField: String by mutableStateOf("")

    init {
        // A reused native window is already created and shown; only fresh windows need to be created.
        if (reusedNativeWindow == null) {
            val windowParams = WindowParams(
                title = "",
                origin = LogicalPoint(0f, 0f),
                size = LogicalSize(800f, 600f),
                style = WindowStyle(
                    isResizable = true,
                    isMinimizable = true,
                    isMaximizable = true,
                    titleBarKind = WindowTitleBarKind.Custom,
                    systemBackdropType = WindowSystemBackdropType.Mica,
                ),
            )
            nativeWindow.create(windowParams)
            if (application.systemTheme == SystemTheme.Dark) {
                nativeWindow.setImmersiveDarkMode(true)
            }
        }
        // Registration with the application event loop must happen after inputStateTracker and
        // the compose scene are initialized; see the init block at the bottom of the class.
    }

    override var title: String
        get() = titleField
        set(value) {
            titleField = value
            if (!isDisposed) {
                nativeWindow.setTitle(value)
            }
        }

    // A reused native window is already created at its final bounds and DPI, so Windows will not
    // re-send WM_NCCALCSIZE/WindowMove for it; seed the geometry straight from the live HWND instead
    // of the placeholder defaults that only fresh windows correct via native events.
    override var position: DpOffset by mutableStateOf(
        reusedNativeWindow?.getRect()?.origin?.toDpOffset() ?: DpOffset.Zero,
    )
        private set
    override var size: DpSize by mutableStateOf(
        reusedNativeWindow?.getClientSize()?.toDpSize() ?: DpSize(800.dp, 600.dp),
    )
        private set
    override var contentSize: DpSize by mutableStateOf(
        reusedNativeWindow?.getClientSize()?.toDpSize() ?: DpSize(800.dp, 600.dp),
    )
        private set
    override val bounds: DpRect
        get() = DpRect(position, size)

    override fun requestSize(size: DpSize) {
        if (!isDisposed) {
            nativeWindow.setRect(
                LogicalPoint(position.x.value, position.y.value),
                LogicalSize(
                    size.width.takeOrElse { this.size.width }.value,
                    size.height.takeOrElse { this.size.height }.value,
                ),
            )
        }
    }

    private val minSizeState = mutableStateOf(DpSize.Zero)
    override val minSize: DpSize
        get() = minSizeState.value

    override fun requestMinSize(minSize: DpSize) {
        if (!isDisposed) {
            nativeWindow.setMinSize(
                LogicalSize(
                    minSize.width.takeOrElse { this.minSize.width }.value,
                    minSize.height.takeOrElse { this.minSize.height }.value,
                ),
            )
        }
        minSizeState.value = minSize
    }

    private val maxSizeState = mutableStateOf(DpSize(7680.dp, 4320.dp))
    override val maxSize: DpSize
        get() = maxSizeState.value

    override fun requestMaxSize(maxSize: DpSize) {
        // Max size is tracked locally; Win32 doesn't have a native setMaxSize API
        maxSizeState.value = maxSize
    }

    private val isUserResizableState = mutableStateOf(true)
    override var isUserResizable: Boolean
        get() = isUserResizableState.value
        @MainThread
        private set(value) {
            if (!isDisposed) {
                nativeWindow.setResizable(value)
            }
            isUserResizableState.value = value
        }

    override fun requestUserResizable(userResizable: Boolean) {
        isUserResizable = userResizable
    }

    override fun requestPosition(position: DpOffset) {
        if (!isDisposed) {
            nativeWindow.setRect(
                LogicalPoint(
                    position.x.takeOrElse { this.position.x }.value,
                    position.y.takeOrElse { this.position.y }.value,
                ),
                nativeWindow.getRect().size,
            )
        }
    }

    override fun requestBounds(bounds: DpRect) {
        if (!isDisposed) {
            nativeWindow.setRect(
                LogicalPoint(
                    bounds.left.takeOrElse { position.x }.value,
                    bounds.top.takeOrElse { position.y }.value,
                ),
                LogicalSize(
                    bounds.width.takeOrElse { size.width }.value,
                    bounds.height.takeOrElse { size.height }.value,
                ),
            )
        }
    }

    override fun requestPlacement(placement: WindowPlacement) {
        if (!isDisposed) {
            when (placement) {
                WindowPlacement.Floating -> {
                    nativeWindow.restore()
                }
                WindowPlacement.Maximized -> {
                    nativeWindow.maximize()
                }
                WindowPlacement.Fullscreen -> {
                    // TODO: Win32/KDT has no fullscreen API yet
                }
            }
        }
    }

    override fun requestClose(reason: WindowCloseRequestReason) {
        if (!isDisposed) {
            composeScene.withFrameTransaction {
                onCloseRequest(reason)
            }
        }
    }

    override var isFocused: Boolean by mutableStateOf(true)
        internal set

    override fun requestFocus() {
        if (!isDisposed) {
            nativeWindow.forceFocus()
        }
    }

    override fun requestBringToFront() {
        if (!isDisposed) {
            nativeWindow.forceFocus()
        }
    }

    override fun requestFocusAndBringToFront() {
        if (!isDisposed) {
            nativeWindow.forceFocus()
        }
    }

    @ExperimentalComposeUiApi
    override val decoration: WindowDecoration =
        WindowDecoration.CustomTitleBar(DefaultCustomTitleBarHeightForAir)

    @ExperimentalComposeUiApi
    override fun requestDecoration(vararg decorations: WindowDecoration) {
        // TODO
    }

    @OptIn(ExperimentalComposeUiApi::class)
    override val customTitleBarInsets: Pair<Dp, Dp>?
        get() = when (decoration) {
            is WindowDecoration.CustomTitleBar ->
                0.dp to 0.dp
            else -> null
        }

    private var overriddenSystemTheme by mutableStateOf<SystemTheme?>(null)
    override val systemTheme: SystemTheme
        get() = overriddenSystemTheme ?: application.systemTheme

    override fun requestSystemTheme(systemTheme: SystemTheme?) {
        overriddenSystemTheme = systemTheme
        if (!isDisposed) {
            when (this.systemTheme) {
                SystemTheme.Dark -> {
                    nativeWindow.setImmersiveDarkMode(true)
                }
                SystemTheme.Light -> {
                    nativeWindow.setImmersiveDarkMode(false)
                }
                else -> {}
            }
        }
    }

    private fun placement(): WindowPlacement = when {
        nativeWindow.isMaximized() -> WindowPlacement.Maximized
        else -> WindowPlacement.Floating
    }

    override var placement: WindowPlacement by mutableStateOf(
        if (isReusedNativeWindow) placement() else WindowPlacement.Floating,
    )
        private set

    private var windowRectBeforeMinimization: LogicalRect? = null
    override fun requestMinimized(minimized: Boolean) {
        if (!isDisposed) {
            if (minimized) {
                windowRectBeforeMinimization = nativeWindow.getRect()
                nativeWindow.minimize()
            } else {
                // An OS-initiated minimize (caption button) never records the rect; fall back to
                // the native restore instead of tripping on the missing snapshot.
                windowRectBeforeMinimization
                    ?.let { nativeWindow.setRect(it.origin, it.size) }
                    ?: nativeWindow.restore()
            }
        }
    }

    private fun density() = Density(nativeWindow.getScaleFactor())
    override var density: Density by mutableStateOf(density())
        private set

    // Reuse the pointer icon service of the surviving native window: it owns the
    // isHiddenUntilPointerMoves flag that gates the global Cursor.hide()/show() balance. A fresh
    // instance would start as "not hidden" and could never issue the Cursor.show() needed to undo an
    // outstanding hide, leaving the cursor invisible after reuse. Keeping the same instance lets the
    // new inputModeManager's init-time setHiddenUntilPointerMoves(false) restore the cursor.
    internal val pointerIconService = reusedPointerIconService ?: WindowsPointerIconService(nativeWindow)
    internal val inputModeManager: InputModeManager =
        InputModeManagerImpl(reusedInputMode ?: InputMode.Touch) {
            pointerIconService.setHiddenUntilPointerMoves(it == InputMode.Keyboard)
            true
        }

    override var screen: WindowsScreen by mutableStateOf(
        WindowsScreen(nativeWindow.getScreen()),
    )
        private set

    var layoutDirection: LayoutDirection by mutableStateOf(LayoutDirection.Ltr)
        private set

    val viewConfiguration: ViewConfiguration =
        androidx.compose.ui.desktop.windows.ViewConfiguration { density }

    val windowInfo: WindowInfo = object : WindowInfo {
        override val isWindowFocused: Boolean
            get() = isFocused

        @OptIn(InternalCoreApi::class)
        override val keyboardModifiers: PointerKeyboardModifiers
            get() = inputStateTracker.keyboardModifiers

        @ExperimentalComposeUiApi
        override val containerSize: IntSize
            get() = contentSizeInPx()
    }

    private fun contentSizeInPx(): IntSize = density.run {
        IntSize(contentSize.width.roundToPx(), contentSize.height.roundToPx())
    }

    // ----- Compose scene wiring -----

    // Client coordinates ARE scene coordinates on win32 (the custom title bar lives inside the
    // client area), so local<->window conversion is the identity; screen conversion offsets by
    // the window origin, matching Noria's PositionCalculator.
    private fun positionInScreenPx(): Offset = density().run {
        Offset(position.x.toPx(), position.y.toPx())
    }

    private val architectureComponentsOwner = DefaultArchitectureComponentsOwner().apply {
        enableSavedStateHandles()
        setLifecycleState(Lifecycle.State.RESUMED)
    }

    private val semanticsOwners = mutableStateSetOf<SemanticsOwner>()

    private val platformContext: PlatformContext = object : PlatformContext by PlatformContext.Empty(),
        PlatformContext.SemanticsOwnerListener {
        override val windowInfo: WindowInfo
            get() = this@WindowsWindow.windowInfo
        override val viewConfiguration: ViewConfiguration
            get() = this@WindowsWindow.viewConfiguration
        override val inputModeManager: InputModeManager
            get() = this@WindowsWindow.inputModeManager
        override val architectureComponentsOwner = this@WindowsWindow.architectureComponentsOwner
        override val textToolbar = DefaultTextToolbar()

        override val dragAndDropManager: PlatformDragAndDropManager =
            KdtDragAndDropManager(this@WindowsWindow)

        override fun convertLocalToWindowPosition(localPosition: Offset): Offset = localPosition
        override fun convertWindowToLocalPosition(positionInWindow: Offset): Offset = positionInWindow
        override fun convertLocalToScreenPosition(localPosition: Offset): Offset =
            localPosition + positionInScreenPx()
        override fun convertScreenToLocalPosition(positionOnScreen: Offset): Offset =
            positionOnScreen - positionInScreenPx()

        override fun textInputSessionOwner() = windowsTextInputSessionOwner

        override val semanticsOwnerListener: PlatformContext.SemanticsOwnerListener?
            get() = if (TestDataMode.isEnabled) this else null

        override fun onSemanticsOwnerAppended(semanticsOwner: SemanticsOwner) {
            semanticsOwners.add(semanticsOwner)
        }

        override fun onSemanticsOwnerRemoved(semanticsOwner: SemanticsOwner) {
            semanticsOwners.remove(semanticsOwner)
        }

        override fun onSemanticsChange(semanticsOwner: SemanticsOwner) = Unit

        override fun onLayoutChange(semanticsOwner: SemanticsOwner, semanticsNodeId: Int) = Unit
    }

    // ----- Frame pump -----
    //
    // The window owns its frame loop (the fork's analogue of Noria's per-scene loop in
    // WindowsApplication.launchScene, and of macOS's DisplayLinkFramePump): every frame request
    // funnels into a conflated coroutine channel on the KDT dispatcher, never a Win32 paint
    // message — requestRedraw's region invalidation is silently discarded in several window
    // states (hidden before the first show, mid-creation, re-entered dispatch), and a request
    // lost there wedged the scene behind its own dedup until an external resize rendered
    // directly (AIR-6157).

    private val framePacer = FramePacer(MIN_FRAME_INTERVAL_NS, System::nanoTime)

    private val frameDispatcher: FrameDispatcher = FrameDispatcher(
        CoroutineScope(session.coroutineScope.coroutineContext + WindowsKdtMainDispatcher.INSTANCE),
    ) {
        framePacer.awaitNextFrameSlot()
        var presented = false
        try {
            val ran = withoutRenderReentrancy { presented = renderCurrentFrame() }
            if (!ran) {
                // A synchronous native render (NCCalcSize) is on this stack via re-entered
                // dispatch; rerun the frame once it unwinds.
                frameDispatcher.scheduleFrame()
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            // The frame was still presented (present-even-on-throw lives in
            // AngleViewContext.renderFrame, AIR-5859); keep the loop alive and retry instead of
            // letting the failure cancel it, mirroring Noria's frame loop.
            logger.error(throwable) { "Frame render failed; scheduling a retry frame" }
            frameDispatcher.scheduleFrame()
        } finally {
            // Re-arm for invalidations that arrived during this frame (the scheduled flag was
            // consumed before it began). Gated on `presented` so a skipped render (minimized, no
            // size yet, content not installed) cannot spin the loop; the WindowDraw damage hint
            // re-arms those cases when the window becomes presentable again.
            if (presented && !isDisposed && composeScene.hasInvalidations()) {
                frameDispatcher.scheduleFrame()
            }
        }
    }

    internal val composeScene: ComposeScene = CanvasLayersComposeScene(
        density = density,
        layoutDirection = layoutDirection,
        size = contentSizeInPx(),
        coroutineContext = session.coroutineScope.coroutineContext +
            WindowsKdtMainDispatcher.INSTANCE,
        platformContext = platformContext,
        dataSourceContext = session.dataSourceContext,
        invalidate = { frameDispatcher.scheduleFrame() },
    )

    private val windowsDragAndDropManager = WindowsDragAndDropManager(
        nativeWindow = nativeWindow,
        rootDragAndDropNode = { composeScene.rootDragAndDropNode },
        withMainThreadPrepared = { block -> composeScene.withFrameTransaction { block() } },
    )

    private val dragDropManager: DragDropManager = DragDropManager(nativeWindow).apply {
        registerDropTarget(
            object : DropTarget {
                override fun onDragEnter(
                    dataObject: DataObject,
                    modifiers: DragDropModifiers,
                    point: PhysicalPoint,
                    effect: DragDropEffect,
                ): DragDropEffect =
                    windowsDragAndDropManager.onDragEnter(dataObject, point, effect, modifiers)

                override fun onDragOver(
                    modifiers: DragDropModifiers,
                    point: PhysicalPoint,
                    effect: DragDropEffect,
                ): DragDropEffect = windowsDragAndDropManager.onDragOver(point, effect, modifiers)

                override fun onDragLeave() = windowsDragAndDropManager.onDragLeave()

                override fun onDrop(
                    dataObject: DataObject,
                    modifiers: DragDropModifiers,
                    point: PhysicalPoint,
                    effect: DragDropEffect,
                ): DragDropEffect =
                    windowsDragAndDropManager.onDrop(dataObject, point, effect, modifiers)
            },
        )
    }

    private var activeDragAndDropTransferData: DragAndDropTransferData? = null

    private val dragSource = object : DragSource {
        override fun onQueryContinueDrag(
            escapePressed: Boolean,
            modifiers: DragDropModifiers,
        ): DragDropContinueResult = when {
            escapePressed -> DragDropContinueResult.Cancel
            !modifiers.hasFlag(DragDropModifier.LeftButton) -> DragDropContinueResult.Drop
            else -> DragDropContinueResult.Continue
        }
    }

    /**
     * The drag SOURCE half of Windows drag and drop: a blocking OLE `doDragDrop` loop on the
     * dispatcher thread. The drop-target half lives in [windowsDragAndDropManager]; the KDT DnD
     * manager itself is drop-only.
     */
    @OptIn(InternalCoreApi::class)
    internal fun startDragSession(
        offset: Offset,
        transferData: DragAndDropTransferData,
        decorationSize: Size,
        drawDragDecoration: DrawScope.() -> Unit,
    ) {
        val clipEntry = (transferData.transferable as? KdtDragAndDropTransferable)?.clipboardEntry ?: return
        val itemsEntry = clipEntry.nativeClipEntry as? ClipboardItemsEntry ?: return
        val supportedActions = transferData.supportedActions.toSet()
        if (supportedActions.isEmpty()) return

        // Posted rather than run in place: this is reached synchronously from pointer dispatch,
        // INSIDE the scene's open pointer-ingress frame transaction, and doDragDrop pumps a
        // nested OLE modal loop that would run paced frames (and their snapshot rotation) under
        // that open slice — a fail-fast under frame isolation. One dispatch hop makes the
        // blocking drag loop its own top-level dispatch while keeping drag-over-self repainting
        // alive (the nested loop still pumps posted frame work).
        application.nativeApplication.invokeOnDispatcher {
            if (!isDisposed) {
                runDragSession(transferData, itemsEntry, supportedActions, decorationSize, drawDragDecoration)
            }
        }
    }

    @OptIn(InternalCoreApi::class)
    private fun runDragSession(
        transferData: DragAndDropTransferData,
        itemsEntry: ClipboardItemsEntry,
        supportedActions: Set<DragAndDropTransferAction>,
        decorationSize: Size,
        drawDragDecoration: DrawScope.() -> Unit,
    ) {
        val allowedEffects = supportedActions.fold(DragDropEffect.None) { effect, action ->
            effect or action.toDragDropEffect()
        }

        activeDragAndDropTransferData = transferData

        val image = DragAndDropImage(
            decorationSize,
            density,
            layoutDirection,
            drawDragDecoration,
        ).encodeToPng()

        val items = itemsEntry.items.toWindowsClipboardItems()
        val dragImage = image?.let {
            DragImage(image, transferData.dragDecorationOffset.toPhysicalPoint())
        }
        val resultEffect = DataObject.build {
            addClipboardItems(items)
        }.use { obj ->
            dragDropManager.doDragDrop(obj, allowedEffects, dragSource, dragImage)
        }

        val resultAction = resultEffect.toDragAndDropTransferAction()
        composeScene.withFrameTransaction {
            transferData.onTransferCompleted?.invoke(resultAction)
        }
        if (activeDragAndDropTransferData === transferData) {
            activeDragAndDropTransferData = null
        }
    }

    // The win32 IMM32 IME session owner. Provided to the composition via both
    // platformContext.textInputSessionOwner() and LocalTextInputSessionOwner, and offered native
    // text events first refusal from handleEvent (tryHandleTextInputEvent) and key events from the
    // tracker's sendKeyEvent lambda (handleEventWithInputSession). Declared after composeScene
    // because its client wraps every IME ingress in composeScene.withFrameTransaction.
    private val windowsTextInputSessionOwner =
        WindowsTextInputSessionOwner(nativeWindow, composeScene, density = { density })

    // win32 KDT dialogs are BLOCKING object calls (FileDialog.showOpenFileDialog/showSaveFileDialog
    // return List<String>/String? directly — no async RequestId/FileChooserResponse like Linux/GTK).
    // The fork interface made these suspend (WS3), so — exactly like macOS wrapping its blocking
    // modal panels — each override runs the blocking modal on the UI/STA dispatcher and maps the
    // result. The blocking call is NEVER inside a withFrameTransaction (a frame transaction must not
    // span a suspension/blocking point); it runs under withContext instead. win32 returns plain
    // filesystem paths, so results are wrapped with Path() directly (no percent-decoding).
    override suspend fun showOpenSingleDialog(
        title: String,
        prompt: String,
        message: String?,
        nameFieldStringValue: String?,
        directoryPath: Path?,
        canCreateDirectories: Boolean,
        canSelectHiddenExtensions: Boolean,
        showsHiddenFiles: Boolean,
        isExtensionHidden: Boolean,
        canChooseFiles: Boolean,
        canChooseDirectories: Boolean,
        resolvesAliases: Boolean,
    ): Path? = withContext(WindowsKdtMainDispatcher.INSTANCE.immediate) {
        FileDialog.showOpenFileDialog(
            owner = nativeWindow,
            options = mapWindowsFileDialogOptions(title, prompt, nameFieldStringValue, directoryPath),
            openDialogOptions = FileDialog.FileOpenDialogOptions(
                chooseDirectories = canChooseDirectories,
                allowsMultipleSelection = false,
            ),
        ).firstOrNull()?.let { Path(it) }
    }

    override suspend fun showOpenMultipleDialog(
        title: String,
        prompt: String,
        message: String?,
        nameFieldStringValue: String?,
        directoryPath: Path?,
        canCreateDirectories: Boolean,
        canSelectHiddenExtensions: Boolean,
        showsHiddenFiles: Boolean,
        isExtensionHidden: Boolean,
        canChooseFiles: Boolean,
        canChooseDirectories: Boolean,
        resolvesAliases: Boolean,
    ): List<Path> = withContext(WindowsKdtMainDispatcher.INSTANCE.immediate) {
        FileDialog.showOpenFileDialog(
            owner = nativeWindow,
            options = mapWindowsFileDialogOptions(title, prompt, nameFieldStringValue, directoryPath),
            openDialogOptions = FileDialog.FileOpenDialogOptions(
                chooseDirectories = canChooseDirectories,
                // Noria omits this flag (WindowsWindow.kt:557-568), which silently downgrades the
                // multi-select picker to single-select — a latent Noria bug. A dialog whose whole
                // purpose is choosing multiple files must allow it, so set it explicitly here.
                allowsMultipleSelection = true,
            ),
        ).map { Path(it) }
    }

    override suspend fun showSaveDialog(
        title: String,
        prompt: String,
        message: String?,
        nameFieldLabel: String,
        nameFieldStringValue: String?,
        directoryPath: Path?,
        canCreateDirectories: Boolean,
        canSelectHiddenExtensions: Boolean,
        showsHiddenFiles: Boolean,
        isExtensionHidden: Boolean,
    ): Path? = withContext(WindowsKdtMainDispatcher.INSTANCE.immediate) {
        FileDialog.showSaveFileDialog(
            owner = nativeWindow,
            options = mapWindowsFileDialogOptions(title, prompt, nameFieldStringValue, directoryPath),
        )?.let { Path(it) }
    }

    override fun captureScreenshot(): ImageBitmap {
        TODO()
    }

    override fun dispose() {
        if (!isDisposed) {
            isDisposed = true
            frameDispatcher.cancel()
            application.windows -= id
            // Unconditionally, BEFORE the reuse check below: when the HWND survives into a new
            // wrapper, the sibling re-registers its own drop target on it (Noria's ordering).
            dragDropManager.revokeDropTarget()
            composeScene.close()
            architectureComponentsOwner.setLifecycleState(Lifecycle.State.DESTROYED)
            // When the native window is being handed off for reuse, keep its HWND, lightweight id,
            // and angle view context alive; disposeReusableNativeWindowResources owns their teardown.
            if (!application.reusableNativeWindowResources.peekContains(id)) {
                // A window disposed before its first render must not CREATE the ANGLE renderer
                // just to close it.
                if (angleViewContextLazy.isInitialized()) {
                    angleViewContext.close()
                }
                nativeWindow.destroy()
                nativeWindow.destroyLightweightWindowId()
            }
            if (application.windows.isEmpty()) {
                application.finishStructuredQuitIfNeeded()
            }
        }
    }

    /**
     * Renders the current content at the window's latest known size; the frame loop calls this
     * once per paced frame. Presentation must not depend on WM_PAINT: with the composition-backed
     * surface the OS paint message is only a hint, and it is not delivered at all in some window
     * states (hidden before the first show, mid-creation). Returns whether a frame was presented.
     */
    private fun renderCurrentFrame(): Boolean {
        if (isDisposed || nativeWindow.isMinimized()) return false
        if (!sceneContentInstalled) return false
        val physicalSize = latestPhysicalSize ?: return false
        angleViewContext.renderFrame(physicalSize, pixelGeometry) {
            composeScene.render(asComposeCanvas(), System.nanoTime())
        }
        return true
    }

    @OptIn(ExperimentalComposeUiApi::class, InternalCoreApi::class)
    internal fun handleEvent(event: Win32Event): Win32EventHandlerResult {
        return try {
            // The active IME session gets first refusal of native text events: a committed
            // character (WM_CHAR) or a dead-key composition arriving as Event.CharacterReceived is
            // inserted/composed here and the event stops, rather than routing through the handlers
            // below. Inert (returns false) when no text input session is active.
            if (windowsTextInputSessionOwner.tryHandleTextInputEvent(event)) {
                return Win32EventHandlerResult.Stop
            }
            when (event) {
                is Win32Event.NCCalcSize -> {
                    // WM_NCCALCSIZE is sent when the size and position of a window's client area
                    // must be calculated.
                    //
                    // We handle it because it is the earliest when we can draw a new frame and thus
                    // resize the content efficiently and almost without flickering. The render
                    // here is deliberately synchronous — recomposing at the new size within this
                    // very message is what makes interactive resize track the drag; the frame
                    // loop's paced, asynchronous frame would lag behind it. When a render is
                    // already on the stack the guard skips this one harmlessly: the size writes
                    // above schedule a loss-proof follow-up frame.
                    //
                    // However, it is sent in more cases than just common size / position change.
                    // One such case is when the window is being minimized. When that happens, we
                    // don't want neither to recalculate nor to redraw the window's content.
                    //
                    // NOTE: nativeWindow.isMinimized() uses the IsIconic function internally. It
                    // works inside WM_NCCALCSIZE, but another way to understand that the window is
                    // being minimized is by looking at its position - it will be -32000 on at least
                    // Y axis (X axis might be offset by Win10/11's drop shadow effect).
                    if (!isDisposed && !nativeWindow.isMinimized()) {
                        val logicalSize = LogicalSize(
                            event.size.width / event.scale,
                            event.size.height / event.scale,
                        )
                        // One frame slice for the whole resize ingress: the writes publish (with
                        // their invalidations) when the transaction ends, before the synchronous
                        // render below picks them up. This is the fork translation of Noria's
                        // scene.withPreparedMainThread around the synchronous reconcile.
                        composeScene.withFrameTransaction {
                            contentSize = logicalSize.toDpSize()
                            size = logicalSize.toDpSize()
                            placement = placement()
                            composeScene.size = contentSizeInPx()
                        }
                        latestPhysicalSize = event.size
                        // The render itself must stay OUTSIDE the transaction:
                        // BaseComposeScene.render slices its own phases (recompose/layout/draw)
                        // and an enclosing slice would merge them and defer their delivery.
                        // This is the ONE place a render runs outside the paced frame loop.
                        val ran = withoutRenderReentrancy { renderCurrentFrame() }
                        if (!ran) {
                            // This WM_NCCALCSIZE was delivered synchronously from a native call
                            // inside a running render (setRect → WM_NCCALCSIZE). It must not be
                            // retried in place — an inline retry can re-trigger the same
                            // synchronous re-entry and never return to the message pump.
                            frameDispatcher.scheduleFrame()
                        }
                    }
                    Win32EventHandlerResult.Continue
                }

                is Win32Event.WindowDraw -> {
                    // The OS paint message is only a damage hint here, exactly like upstream
                    // Compose's paint handlers: it feeds the window's frame loop instead of
                    // rendering in place (AIR-6366). The composition surface retains the previous
                    // content, so the exposed region shows it until the loop's frame presents
                    // (at most one paced frame later).
                    if (!isDisposed) {
                        latestPhysicalSize = event.size
                        frameDispatcher.scheduleFrame()
                    }
                    Win32EventHandlerResult.Continue
                }

                is Win32Event.WindowMove -> {
                    val scale = event.scale
                    position = DpOffset(
                        (event.origin.x / scale).dp,
                        (event.origin.y / scale).dp,
                    )
                    Win32EventHandlerResult.Continue
                }

                is Win32Event.WindowScaleChanged -> {
                    val scale = event.scale
                    density = Density(scale)
                    screen = WindowsScreen(nativeWindow.getScreen())
                    position = DpOffset(
                        (event.origin.x / scale).dp,
                        (event.origin.y / scale).dp,
                    )
                    // Propagate to the scene; the accompanying WM_NCCALCSIZE delivers the new
                    // client size and renders synchronously at it.
                    composeScene.density = density
                    Win32EventHandlerResult.Continue
                }

                is Win32Event.WindowTitleChanged -> {
                    titleField = event.title
                    Win32EventHandlerResult.Continue
                }

                is Win32Event.WindowActivated -> {
                    isFocused = event.active
                    if (event.active) {
                        // Restore-time recovery independent of NCCALCSIZE/WM_PAINT delivery: a
                        // fully-parked scene (minimized with pending invalidations) re-arms here.
                        frameDispatcher.scheduleFrame()
                    }
                    inputStateTracker.updateStateAndSendEvents(event, density)
                    Win32EventHandlerResult.Continue
                }

                is Win32Event.WindowCloseRequest -> {
                    composeScene.withFrameTransaction {
                        onCloseRequest(WindowCloseRequestReason.UserRequest)
                    }
                    Win32EventHandlerResult.Stop
                }

                is Win32Event.NCHitTest -> {
                    val result = hitTestTitleBar(event)
                    if (result != null) {
                        event.setHitTestResult(result)
                        Win32EventHandlerResult.Stop
                    } else {
                        Win32EventHandlerResult.Continue
                    }
                }

                is Win32Event.KeyDown -> {
                    val result = inputStateTracker.updateStateAndSendEvents(event, density)
                    // AIR-5776: an unconsumed system key must still reach TranslateMessage so
                    // Alt-based accelerators and dead keys keep working natively.
                    if (result == Win32EventHandlerResult.Continue && event.isSystemKey) {
                        event.translate()
                    }
                    result
                }
                is Win32Event.KeyUp -> {
                    val result = inputStateTracker.updateStateAndSendEvents(event, density)
                    if (result == Win32EventHandlerResult.Continue && event.isSystemKey) {
                        event.translate()
                    }
                    result
                }
                is Win32Event.PointerDown,
                is Win32Event.PointerUp,
                is Win32Event.PointerUpdated,
                is Win32Event.PointerEntered,
                is Win32Event.PointerExited,
                is Win32Event.ScrollWheelX,
                is Win32Event.ScrollWheelY,
                    -> {
                    inputStateTracker.updateStateAndSendEvents(event, density)
                }

                else -> Win32EventHandlerResult.Continue
            }
        } catch (throwable: Throwable) {
            logger.error(throwable) { "Failed to handle event $event; will let it propagate" }
            Win32EventHandlerResult.Continue
        }
    }

    // Caption-button areas (in logical client coordinates) reported by the Compose title bar.
    // Written on the main thread via setCaptionButtonBounds; read on the WndProc thread in
    // hitTestTitleBar. Kept as a copy-on-write immutable map behind a volatile reference.
    @Volatile
    private var captionButtonBounds: Map<CaptionButtonKind, DpRect> = emptyMap()

    @Synchronized
    override fun setCaptionButtonBounds(kind: CaptionButtonKind, bounds: DpRect?) {
        captionButtonBounds = captionButtonBounds.toMutableMap().apply {
            if (bounds == null) remove(kind) else put(kind, bounds)
        }
    }

    @OptIn(ExperimentalComposeUiApi::class)
    private fun hitTestTitleBar(event: Win32Event.NCHitTest): NCHitTestResult? {
        if (isDisposed) return null
        val titleBar = decoration as? WindowDecoration.CustomTitleBar ?: return null

        // WM_NCHITTEST coordinates are physical screen pixels; map them into logical
        // client space so they can be compared against the custom title-bar height.
        val scale = nativeWindow.getScaleFactor()
        val clientPoint = Screen
            .mapToClient(nativeWindow, PhysicalPoint(event.mouseX, event.mouseY))
            .toLogical(scale)
        return hitTestCustomTitleBar(
            captionButtonBounds = captionButtonBounds,
            clientPoint = clientPoint,
            clientSize = nativeWindow.getClientSize(),
            titleBarHeight = titleBar.height.value,
        )
    }

    @OptIn(ExperimentalComposeUiApi::class, InternalCoreApi::class)
    private val inputStateTracker = InputStateTracker(
        inputModeManager = inputModeManager,
        sendPointerEvent = { eventType, position, scrollDelta, timeMillis, type, buttons, modifiers, nativeEvent, button ->
            composeScene.sendPointerEvent(
                eventType = eventType,
                position = position,
                scrollDelta = scrollDelta,
                timeMillis = timeMillis,
                type = type,
                buttons = buttons,
                keyboardModifiers = modifiers,
                nativeEvent = nativeEvent,
                button = button,
            )
        },
        sendKeyEvent = { keyEvent ->
            // Give the active IME session first refusal (the win32 analogue of macOS's
            // offerEventBeforeSendingToApplication): a text-producing KeyDown is handed to Win32
            // TranslateMessage (event.translate()) and consumed here — its text arrives later as
            // Event.CharacterReceived and is committed via tryHandleTextInputEvent. Non-text keys
            // (navigation, editing, shortcuts) are not translated and fall through to Compose.
            windowsTextInputSessionOwner.handleEventWithInputSession(keyEvent) ||
                composeScene.withFrameTransaction {
                    onPreviewKeyEvent(keyEvent) ||
                        composeScene.sendKeyEvent(keyEvent) ||
                        onKeyEvent(keyEvent)
                }
        },
    )

    @Composable
    @ApiStatus.Internal
    override fun Content(onLayout: (WindowData) -> Unit) {
        // ComposeScene drives its own composition; nothing to host here.
        onLayout(WindowData(id, semanticsOwners))
    }

    private var onPreviewKeyEvent: (KeyEvent) -> Boolean = { false }
    private var onKeyEvent: (KeyEvent) -> Boolean = { false }

    private var contentState = mutableStateOf<(@Composable WindowScope.() -> Unit)?>(null)
    private var sceneContentInstalled = false

    @OptIn(InternalCoreApi::class)
    private fun installSceneContentIfNeeded() {
        if (sceneContentInstalled) return
        sceneContentInstalled = true
        val windowScope = object : WindowScope {
            override val window: Window get() = this@WindowsWindow
        }
        composeScene.setContent {
            CompositionLocalProvider(
                LocalSystemTheme provides systemTheme,
                LocalTextToolbar provides remember { DefaultTextToolbar() },
                LocalWindow provides this,
                LocalTextInputSessionOwner provides windowsTextInputSessionOwner,
                LocalPointerIconService provides pointerIconService,
                LocalInputModeManager provides inputModeManager,
            ) {
                contentState.value?.invoke(windowScope)
            }

            // Refresh hit testing under a stationary pointer after relayout. Queued via the scene's
            // (non-immediate) dispatcher so new SuspendingPointerInputFilters are already subscribed;
            // the tracker's generation check cancels stale refreshes as soon as a real event arrives.
            rememberCoroutineScope().launch {
                inputStateTracker
                    .prepareSyntheticPointerEventAfterRelayoutIfNecessary()
                    ?.let(inputStateTracker::sendSyntheticPointerEventAfterRelayoutIfCurrent)
            }
        }
        // Noria showed the native window once the first content was realized (the
        // DisposableEffect around drawContent); with the scene owning composition, content
        // install is that point. A reused native window is already visible.
        if (!isReusedNativeWindow && !isDisposed) {
            nativeWindow.show()
        }
    }

    override fun setContent(
        onPreviewKeyEvent: (KeyEvent) -> Boolean,
        onKeyEvent: (KeyEvent) -> Boolean,
        content: @Composable WindowScope.() -> Unit,
    ) {
        this.onPreviewKeyEvent = onPreviewKeyEvent
        this.onKeyEvent = onKeyEvent
        contentState.value = content
        installSceneContentIfNeeded()
        frameDispatcher.scheduleFrame()
    }

    override fun startInteractiveMove(pointerEvent: PointerEvent) {
        // Window dragging via pointer is not supported on Windows via KDT
    }

    override fun setIcon(icon: ByteArray) {
        if (!isDisposed) {
            nativeWindow.setIcon(icon)
        }
    }

    init {
        // Registered here so that `inputStateTracker` and the compose scene are guaranteed to be
        // initialized before any event dispatched by the application event loop can reach
        // `handleEvent` (see MacOsWindow's/LinuxWindow's identical ordering).
        application.windows += id to this
    }
}

/**
 * Pure core of the custom-title-bar WM_NCHITTEST routing, extracted for unit testing (the window
 * itself is not constructible without KDT natives). All coordinates are LOGICAL client
 * coordinates (win32 logical units == dp, Float).
 *
 * A caption button claims its reported area: returning its HT* code hands native behavior to
 * Windows (Snap Layouts flyover on Maximize, system minimize/maximize/close on click). The
 * remaining title-bar band returns [NCHitTestResult.Caption], making it a native drag region
 * (drag, double-click maximize, Aero Snap, system menu). Anything below the band, or outside the
 * client width, returns null and is left to the toolkit default (HTCLIENT + native resize
 * borders, which the Rust layer resolves before querying userspace).
 */
internal fun hitTestCustomTitleBar(
    captionButtonBounds: Map<CaptionButtonKind, DpRect>,
    clientPoint: LogicalPoint,
    clientSize: LogicalSize,
    titleBarHeight: Float,
): NCHitTestResult? {
    captionButtonBounds.forEach { (kind, r) ->
        if (clientPoint.x in r.left.value..r.right.value &&
            clientPoint.y in r.top.value..r.bottom.value
        ) {
            return when (kind) {
                CaptionButtonKind.Minimize -> NCHitTestResult.MinButton
                CaptionButtonKind.Maximize -> NCHitTestResult.MaxButton
                CaptionButtonKind.Close -> NCHitTestResult.Close
            }
        }
    }

    val insideClientX = clientPoint.x in 0f..clientSize.width
    val insideTitleBar = clientPoint.y in 0f..titleBarHeight
    return if (insideClientX && insideTitleBar) NCHitTestResult.Caption else null
}

/**
 * Maps the fork's cross-platform (macOS-shaped) file-dialog parameters onto win32 KDT's
 * [FileDialog.FileDialogOptions], following Noria's win32 mapping (WindowsWindow.kt:514-594).
 *
 * win32's dialog surface carries [title], [prompt], [nameFieldStringValue] and [directoryPath]. The
 * remaining macOS-oriented parameters — `message`, `canCreateDirectories`, `canSelectHiddenExtensions`,
 * `isExtensionHidden`, `resolvesAliases`, and (for the open dialogs) the separate `canChooseFiles`
 * (win32 exposes only `chooseDirectories`) — have no meaningful win32 counterpart and are intentionally
 * DROPPED, mirroring what the Linux/GTK `mapCommonDialogParams` KDoc does. `showsHiddenFiles` and
 * `nameFieldLabel` do have win32 fields but are left at KDT's defaults to match Noria's validated
 * mapping (their runtime effect is unverifiable until the Windows VM pass).
 *
 * A non-existent [directoryPath] is dropped as well: win32's picker treats a missing initial folder
 * as an error rather than falling back to a default, so only a directory that currently exists is
 * forwarded (Noria's `SystemFileSystem.exists` guard). Extracted as a pure function so the mapping is
 * unit-testable without native windows (see WindowsFileDialogParamsTest).
 */
internal fun mapWindowsFileDialogOptions(
    title: String,
    prompt: String,
    nameFieldStringValue: String?,
    directoryPath: Path?,
): FileDialog.FileDialogOptions =
    FileDialog.FileDialogOptions(
        title = title,
        prompt = prompt,
        nameFieldStringValue = nameFieldStringValue,
        directoryPath = directoryPath?.takeIf { SystemFileSystem.exists(it) }?.toString(),
    )

// Shared across all windows (Noria kept the equivalent flag on the application for the same
// reason) and confined to the KDT dispatcher thread, where every render runs.
private var renderInProgress = false

/**
 * Guards the two synchronous scene-render entry points (the paced frame and the NCCalcSize
 * resize render) against nested native dispatch, and returns whether [block] ran. Windows
 * delivers some messages synchronously from native calls made inside a running render (e.g.
 * `setRect` → `WM_NCCALCSIZE`), and the handler of such a message renders again. Dropping the
 * nested call is safe: the caller schedules a loss-proof follow-up frame through the conflated
 * frame channel. It must not be retried in place — an inline retry can re-trigger the same
 * synchronous re-entry and never return to the message pump.
 */
private inline fun withoutRenderReentrancy(block: () -> Unit): Boolean {
    if (renderInProgress) {
        return false
    }
    renderInProgress = true
    try {
        block()
    } finally {
        renderInProgress = false
    }
    return true
}

private const val MIN_FRAME_INTERVAL_NS = 1_000_000_000L / 60

private val logger = logger<WindowsWindow>()
