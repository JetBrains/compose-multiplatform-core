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

@file:OptIn(InternalComposeUiApi::class)

package androidx.compose.foundation.text

import androidx.compose.foundation.ComposeFoundationFlags
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.contextmenu.data.TextContextMenuData
import androidx.compose.foundation.text.contextmenu.data.TextContextMenuItemWithComposableLeadingIcon
import androidx.compose.foundation.text.contextmenu.data.TextContextMenuKeys
import androidx.compose.foundation.text.contextmenu.data.TextContextMenuSession
import androidx.compose.foundation.text.contextmenu.modifier.collectTextContextMenuData
import androidx.compose.foundation.text.contextmenu.modifier.showTextContextMenuOnSecondaryClick
import androidx.compose.foundation.text.contextmenu.provider.LocalTextContextMenuDropdownProvider
import androidx.compose.foundation.text.contextmenu.provider.LocalTextContextMenuToolbarProvider
import androidx.compose.foundation.text.contextmenu.provider.TextContextMenuDataProvider
import androidx.compose.foundation.text.contextmenu.provider.TextContextMenuProvider
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.internal.selection.TextFieldSelectionState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.selection.SelectionContainerInputElement
import androidx.compose.foundation.text.selection.SelectionManager
import androidx.compose.foundation.text.selection.TextFieldSelectionManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.NativeTextInputContextMenuCustomAction
import androidx.compose.ui.platform.TextInputContainer
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import kotlin.coroutines.resume
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Context menu area for [BasicTextField] (with [TextFieldValue] argument).
 */
@Composable
internal actual fun ContextMenuArea(
    manager: TextFieldSelectionManager,
    content: @Composable () -> Unit
) {
    val holder = remember(manager) { { manager.state?.holder } }

    if (ComposeFoundationFlags.isNewContextMenuEnabled) {
        // The first time the menu is called up, the menu item provider contains a non-final set of
        // menu items, which causes the context menu callout to blink.
        // Adding a small delay resolves this issue.
        ProvideNewContextMenuDefaultProviders(
            holder = holder,
            selection = remember(manager) { { manager.value.selection } },
            menuDelay = 100.milliseconds,
            modifier = manager.contextMenuAreaModifier,
            content = content
        )
    } else {
        LaunchedEffect(manager) { manager.updateClipboardEntry() }
        val scope = rememberCoroutineScope()
        LegacyNativeEditMenuArea(
            holder = holder,
            items = remember(manager, scope) { { manager.editMenuItems(scope) } },
            content = content
        )
    }
}

/**
 * Context menu area for [BasicTextField] (with [TextFieldState] argument).
 */
@Composable
internal actual fun ContextMenuArea(
    selectionState: TextFieldSelectionState,
    enabled: Boolean,
    content: @Composable () -> Unit
) {
    val holder = remember(selectionState) { { selectionState.textFieldState.holder } }

    if (ComposeFoundationFlags.isNewContextMenuEnabled) {
        ProvideNewContextMenuDefaultProviders(
            holder = holder,
            selection = remember(selectionState) {
                { selectionState.textFieldState.visualText.selection }
            },
            modifier = if (enabled) {
                Modifier.showTextContextMenuOnSecondaryClick(
                    onPreShowContextMenu = { selectionState.updateClipboardEntry() }
                )
            } else {
                Modifier
            },
            content = content
        )
    } else {
        LaunchedEffect(selectionState) { selectionState.updateClipboardEntry() }
        // this should be the same scope as at the root of BasicTextField
        val scope = rememberCoroutineScope()
        LegacyNativeEditMenuArea(
            holder = holder,
            items = remember(selectionState, scope) { { selectionState.editMenuItems(scope) } },
            content = content
        )
    }
}

/**
 * Context menu area for [SelectionContainer].
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal actual fun ContextMenuArea(
    manager: SelectionManager,
    content: @Composable () -> Unit
) {
    // We should adopt the native iOS text input approach for non-editable containers as well
    // https://youtrack.jetbrains.com/issue/CMP-9733/Adopt-NITI-approach-to-the-Selection-Container
    if (ComposeFoundationFlags.isNewContextMenuEnabled) {
        ProvideNewContextMenuDefaultProviders(
            holder = remember(manager) { { manager.holder } },
            selection = remember(manager) { { manager.selection?.toTextRange() } },
            menuDelay = 100.milliseconds,
            modifier = manager.contextMenuAreaModifier then SelectionContainerInputElement(manager),
            content = content
        )
    } else {
        content()
    }
}

@Composable
private fun ProvideNewContextMenuDefaultProviders(
    holder: () -> TextInputContainer.Holder?,
    selection: () -> TextRange?,
    menuDelay: Duration = Duration.ZERO,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    var coordinates: LayoutCoordinates? by remember { mutableStateOf(null, neverEqualPolicy()) }

    val provider = remember(holder, menuDelay) {
        ContextMenuToolbarProvider(
            holder = holder,
            coordinates = { coordinates },
            menuDelay = menuDelay
        )
    }

    CompositionLocalProvider(
        LocalTextContextMenuToolbarProvider providesDefault provider,
        LocalTextContextMenuDropdownProvider providesDefault provider,
    ) {
        Box(
            modifier = modifier
                .onGloballyPositioned { coordinates = it }
                .then(NativeEditMenuElement(holder, selection)),
            propagateMinConstraints = true
        ) {
            content()
        }
    }
}

@Composable
private fun LegacyNativeEditMenuArea(
    holder: () -> TextInputContainer.Holder?,
    items: () -> ContextMenuItems,
    content: @Composable () -> Unit
) {
    LaunchedEffect(holder, items) {
        snapshotFlow { holder() to items() }.collect { (holder, items) ->
            holder?.updateNativeTextInputEditMenuState(
                copy = items.copy,
                cut = items.cut,
                paste = items.paste,
                selectAll = items.selectAll,
                customActions = items.customActions
            )
        }
    }
    content()
}

private data class NativeEditMenuElement(
    private val holder: () -> TextInputContainer.Holder?,
    private val selection: () -> TextRange?
) : ModifierNodeElement<NativeEditMenuNode>() {

    override fun create() = NativeEditMenuNode(holder, selection)

    override fun update(node: NativeEditMenuNode) {
        node.update(holder, selection)
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "nativeEditMenu"
    }
}

private class NativeEditMenuNode(
    private var holder: () -> TextInputContainer.Holder?,
    private var selection: () -> TextRange?
) : Modifier.Node() {

    private var job: Job? = null

    override fun onAttach() {
        observeMenuItems()
    }

    override fun onDetach() {
        job?.cancel()
        job = null
    }

    fun update(holder: () -> TextInputContainer.Holder?, selection: () -> TextRange?) {
        if (this.holder === holder && this.selection === selection) return
        this.holder = holder
        this.selection = selection
        if (isAttached) {
            observeMenuItems()
        }
    }

    private fun observeMenuItems() {
        job?.cancel()
        job = coroutineScope.launch {
            snapshotFlow {
                val holder = holder() ?: return@snapshotFlow null
                if (!holder.usingNativeTextInput()) return@snapshotFlow null
                holder to selection()
            }
                .filterNotNull()
                .collect { (holder, _) ->
                    val items =
                        collectTextContextMenuData().toContextMenuItems(NoOpTextContextMenuSession)
                    holder.updateNativeTextInputEditMenuState(
                        copy = items.copy,
                        cut = items.cut,
                        paste = items.paste,
                        selectAll = items.selectAll,
                        customActions = items.customActions
                    )
                }
        }
    }
}

private class ContextMenuToolbarProvider(
    private val holder: () -> TextInputContainer.Holder?,
    private val coordinates: () -> LayoutCoordinates?,
    private val menuDelay: Duration
) : TextContextMenuProvider {

    override suspend fun showTextContextMenu(dataProvider: TextContextMenuDataProvider) {
        var session: TextContextMenuSession? = null
        coroutineScope {
            val job = launch {
                delay(menuDelay)
                snapshotFlow {
                    if (holder().isNativeTextInput) return@snapshotFlow null
                    val coordinates = coordinates() ?: return@snapshotFlow null

                    val rect = dataProvider.contentBounds(coordinates)
                        .translate(coordinates.positionInRoot())

                    rect to dataProvider.data().toContextMenuItems(session)
                }
                    .filterNotNull()
                    .collect { (rect, items) ->
                        holder()?.showEditMenuAtRect(
                            targetRect = rect,
                            copy = items.copy,
                            cut = items.cut,
                            paste = items.paste,
                            selectAll = items.selectAll,
                            customActions = items.customActions
                        )
                    }
            }

            suspendCancellableCoroutine { continuation ->
                session = TextContextMenuSessionImpl(holder, continuation)
                continuation.invokeOnCancellation {
                    holder()?.hideEditMenu()
                }
            }
            job.cancel()
        }
    }
}

private class TextContextMenuSessionImpl(
    private val holder: () -> TextInputContainer.Holder?,
    private val continuation: CancellableContinuation<Unit>
) : TextContextMenuSession {
    override fun close() {
        holder()?.hideEditMenu()
        if (continuation.isActive) {
            continuation.resume(Unit)
        }
    }
}

private class ContextMenuItems(
    val copy: (() -> Unit)?,
    val cut: (() -> Unit)?,
    val paste: (() -> Unit)?,
    val selectAll: (() -> Unit)?,
    val customActions: List<NativeTextInputContextMenuCustomAction> = emptyList()
)

private val NoOpTextContextMenuSession = object : TextContextMenuSession {
    override fun close() = Unit
}

private fun TextContextMenuData.toContextMenuItems(
    session: TextContextMenuSession?
): ContextMenuItems {
    var copy: (() -> Unit)? = null
    var cut: (() -> Unit)? = null
    var paste: (() -> Unit)? = null
    var selectAll: (() -> Unit)? = null
    val customActions = mutableListOf<NativeTextInputContextMenuCustomAction>()

    components.forEach { component ->
        if (component !is TextContextMenuItemWithComposableLeadingIcon) return@forEach
        if (!component.enabled) return@forEach
        val action: () -> Unit = { with(component) { session?.onClick() } }

        when (component.key) {
            TextContextMenuKeys.CopyKey -> copy = action
            TextContextMenuKeys.CutKey -> cut = action
            TextContextMenuKeys.PasteKey -> paste = action
            TextContextMenuKeys.SelectAllKey -> selectAll = action
            else -> customActions.add(
                NativeTextInputContextMenuCustomAction(
                    title = component.label,
                    action = action
                )
            )
        }
    }

    return ContextMenuItems(
        copy = copy,
        cut = cut,
        paste = paste,
        selectAll = selectAll,
        customActions = customActions
    )
}

/**
 * The context menu items of a [BasicTextField] (with [TextFieldValue] argument) for
 * [ComposeFoundationFlags.isNewContextMenuEnabled] being `false`.
 */
private fun TextFieldSelectionManager.editMenuItems(scope: CoroutineScope): ContextMenuItems {
    fun action(isEnabled: Boolean, block: () -> Unit): (() -> Unit)? {
        if (!isEnabled) return null
        return {
            block()
            scope.launch { updateClipboardEntry() }
        }
    }

    return ContextMenuItems(
        copy = action(isCopyAllowed()) { copy(cancelSelection = false) },
        cut = action(canShowCutMenuItem()) { cut() },
        paste = action(canShowPasteMenuItem()) { paste() },
        selectAll = action(canShowSelectAllMenuItem()) { selectAll() }
    )
}

/**
 * The context menu items of a [BasicTextField] (with [TextFieldState] argument) for
 * [ComposeFoundationFlags.isNewContextMenuEnabled] being `false`.
 */
private fun TextFieldSelectionState.editMenuItems(scope: CoroutineScope): ContextMenuItems {
    fun action(isEnabled: Boolean, block: suspend () -> Unit): (() -> Unit)? {
        if (!isEnabled) return null
        return {
            scope.launch {
                block()
                updateClipboardEntry()
            }
        }
    }

    return ContextMenuItems(
        copy = action(canShowCopyMenuItem()) { copy(cancelSelection = false) },
        cut = action(canShowCutMenuItem()) { cut() },
        paste = action(canShowPasteMenuItem()) { paste() },
        selectAll = action(canShowSelectAllMenuItem()) { selectAll() }
    )
}
