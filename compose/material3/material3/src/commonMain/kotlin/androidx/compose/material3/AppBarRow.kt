/*
 * Copyright 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package androidx.compose.material3

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.internal.subtractConstraintSafely
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.*
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.util.fastMap
import androidx.compose.ui.util.fastMaxOfOrNull
import kotlin.math.max

/**
 * An [AppBarRow] arranges its children in a horizontal sequence, and if any children overflow the
 * constraints, an overflow indicator is displayed.
 *
 * This composable lays out its children from left to right in LTR layouts and from right to left in
 * RTL layouts. If the children's combined width exceeds the available width, [overflowIndicator] is
 * displayed at the end of the row, replacing the content that otherwise cannot fit. The items are
 * constructed through a DSL in [AppBarRowScope]. Each item provides a way to render itself in the
 * row layout, and an alternative way, to render inside of a dropdown menu, when there is overflow.
 *
 * @param overflowIndicator A composable that is displayed at the end of the row when the content
 *   overflows. It receives an [AppBarRowMenuState] instance.
 * @param modifier The modifier to be applied to the row.
 * @param content The content to be arranged in the row, defined using a dsl with [AppBarRowScope].
 */
@Composable
@Suppress("ComposableLambdaParameterPosition", "KotlinDefaultParameterOrder")
fun AppBarRow(
    overflowIndicator: @Composable (AppBarRowMenuState) -> Unit,
    modifier: Modifier = Modifier,
    content: AppBarRowScope.() -> Unit,
) {
    val scope: AppBarRowScopeImpl by rememberAppBarRowScopeState(content)
    val menuState = remember { AppBarRowMenuState() }
    val overflowState = rememberAppBarRowOverflowState()
    val measurePolicy = remember(overflowState) { OverflowMeasurePolicy(overflowState) }

    Layout(
        contents =
            listOf(
                { scope.items.fastForEach { it.AppbarContent() } },
                {
                    Box {
                        overflowIndicator(menuState)
                        DropdownMenu(
                            expanded = menuState.isExpanded,
                            onDismissRequest = { menuState.dismiss() }
                        ) {
                            scope.items
                                .subList(
                                    overflowState.visibleItemCount,
                                    overflowState.totalItemCount
                                )
                                .fastForEach { item -> item.MenuContent(menuState) }
                        }
                    }
                }
            ),
        modifier = modifier,
        measurePolicy = measurePolicy,
    )
}

@Composable
private fun rememberAppBarRowScopeState(
    content: AppBarRowScope.() -> Unit
): State<AppBarRowScopeImpl> {
    val latestContent = rememberUpdatedState(content)
    return remember { derivedStateOf { AppBarRowScopeImpl().apply(latestContent.value) } }
}

/** DSL scope for building the content of an [AppBarRow]. */
interface AppBarRowScope {

    /**
     * Adds a clickable item to the [AppBarRow].
     *
     * @param onClick The action to perform when the item is clicked.
     * @param icon The composable representing the item's icon.
     * @param enabled Whether the item is enabled.
     * @param label The text label for the item, used in the overflow menu.
     */
    fun clickableItem(
        onClick: () -> Unit,
        icon: @Composable () -> Unit,
        label: String,
        enabled: Boolean = true,
    )

    /**
     * Adds a toggleable item to the [AppBarRow].
     *
     * @param checked Whether the item is currently checked.
     * @param onCheckedChange The action to perform when the item's checked state changes.
     * @param icon The composable representing the item's icon.
     * @param enabled Whether the item is enabled.
     * @param label The text label for the item, used in the overflow menu.
     */
    fun toggleableItem(
        checked: Boolean,
        onCheckedChange: (Boolean) -> Unit,
        icon: @Composable () -> Unit,
        label: String,
        enabled: Boolean = true,
    )

    /**
     * Adds a custom item to the [AppBarRow].
     *
     * @param appbarContent The composable to display in the app bar.
     * @param menuContent The composable to display in the overflow menu. It receives an
     *   [AppBarRowMenuState] instance.
     */
    fun customItem(
        appbarContent: @Composable () -> Unit,
        menuContent: @Composable (AppBarRowMenuState) -> Unit,
    )
}

private interface AppBarItemProvider {
    val itemsCount: Int
    val items: MutableList<AppBarItem>
}

private class AppBarRowScopeImpl : AppBarRowScope, AppBarItemProvider {

    override val items: MutableList<AppBarItem> = mutableListOf()

    override val itemsCount: Int
        get() = items.size

    override fun clickableItem(
        onClick: () -> Unit,
        icon: @Composable () -> Unit,
        label: String,
        enabled: Boolean,
    ) {
        items.add(
            ClickableAppBarItem(
                onClick = onClick,
                icon = icon,
                enabled = enabled,
                label = label,
            )
        )
    }

    override fun toggleableItem(
        checked: Boolean,
        onCheckedChange: (Boolean) -> Unit,
        icon: @Composable () -> Unit,
        label: String,
        enabled: Boolean,
    ) {
        items.add(
            ToggleableAppBarItem(
                checked = checked,
                onCheckedChange = onCheckedChange,
                icon = icon,
                enabled = enabled,
                label = label,
            )
        )
    }

    override fun customItem(
        appbarContent: @Composable () -> Unit,
        menuContent: @Composable (AppBarRowMenuState) -> Unit,
    ) {
        items.add(CustomAppBarItem(appbarContent, menuContent))
    }
}

/** State class for the overflow menu in [AppBarRow]. */
class AppBarRowMenuState {

    /** Indicates whether the overflow menu is currently expanded. */
    var isExpanded by mutableStateOf(false)
        private set

    /** Closes the overflow menu. */
    fun dismiss() {
        isExpanded = false
    }

    /** Show the overflow menu. */
    fun show() {
        isExpanded = true
    }
}

internal interface AppBarItem {

    /** Composable function to render the item in the app bar. */
    @Composable fun AppbarContent()

    /**
     * Composable function to render the item in the overflow menu.
     *
     * @param state The [AppBarRowMenuState] instance.
     */
    @Composable fun MenuContent(state: AppBarRowMenuState)
}

internal class ClickableAppBarItem(
    private val onClick: () -> Unit,
    private val icon: @Composable () -> Unit,
    private val enabled: Boolean,
    private val label: String
) : AppBarItem {

    @Composable
    override fun AppbarContent() {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            content = icon,
        )
    }

    @Composable
    override fun MenuContent(state: AppBarRowMenuState) {
        DropdownMenuItem(
            enabled = enabled,
            text = { Text(label) },
            onClick = {
                onClick()
                state.dismiss()
            }
        )
    }
}

internal class ToggleableAppBarItem(
    private val checked: Boolean,
    private val onCheckedChange: (Boolean) -> Unit,
    private val icon: @Composable () -> Unit,
    private val enabled: Boolean,
    private val label: String
) : AppBarItem {

    @Composable
    override fun AppbarContent() {
        IconToggleButton(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            content = icon,
        )
    }

    @Composable
    override fun MenuContent(state: AppBarRowMenuState) {
        DropdownMenuItem(
            enabled = enabled,
            text = { Text(label) },
            onClick = {
                onCheckedChange(!checked)
                state.dismiss()
            }
        )
    }
}

internal class CustomAppBarItem(
    private val appbarContent: @Composable () -> Unit,
    private val menuContent: @Composable (AppBarRowMenuState) -> Unit,
) : AppBarItem {
    @Composable
    override fun AppbarContent() {
        appbarContent()
    }

    @Composable
    override fun MenuContent(state: AppBarRowMenuState) {
        menuContent(state)
    }
}

private interface AppBarRowAppBarRowOverflowState {

    var totalItemCount: Int

    var visibleItemCount: Int
}

@Composable
private fun rememberAppBarRowOverflowState(): AppBarRowAppBarRowOverflowState {
    return rememberSaveable(saver = AppBarRowOverflowStateImpl.Saver) {
        AppBarRowOverflowStateImpl()
    }
}

private class AppBarRowOverflowStateImpl : AppBarRowAppBarRowOverflowState {
    override var totalItemCount: Int by mutableIntStateOf(0)
    override var visibleItemCount: Int by mutableIntStateOf(0)

    companion object {
        val Saver: Saver<AppBarRowOverflowStateImpl, *> =
            Saver(
                save = { listOf(it.totalItemCount, it.visibleItemCount) },
                restore = {
                    AppBarRowOverflowStateImpl().apply {
                        totalItemCount = it[0]
                        visibleItemCount = it[1]
                    }
                }
            )
    }
}

private class OverflowMeasurePolicy(private val overflowState: AppBarRowAppBarRowOverflowState) :
    MultiContentMeasurePolicy {
    override fun MeasureScope.measure(
        measurables: List<List<Measurable>>,
        constraints: Constraints
    ): MeasureResult {
        val looseConstraints = constraints.copy(minWidth = 0, minHeight = 0)
        val (contentMeasurables, overflowMeasurables) = measurables

        overflowState.totalItemCount = contentMeasurables.size

        // First, reserve space for the overflow indicator
        val reservedOverflowWidth =
            overflowMeasurables.fastMaxOfOrNull { it.maxIntrinsicWidth(constraints.maxHeight) } ?: 0
        var remainingSpace = constraints.maxWidth
        remainingSpace = remainingSpace.subtractConstraintSafely(reservedOverflowWidth)

        var currentWidth = 0
        val contentPlaceables = mutableListOf<Placeable>()

        // Measure content until it doesn't fit
        for (i in contentMeasurables.indices) {
            val measurable = contentMeasurables[i]
            val placeable = measurable.measure(looseConstraints)
            val isLastContent = i == contentMeasurables.lastIndex
            val hasSufficientSpace =
                placeable.width <= remainingSpace ||
                    (isLastContent && placeable.width <= remainingSpace + reservedOverflowWidth)

            if (hasSufficientSpace) {
                contentPlaceables.add(placeable)
                currentWidth += placeable.width
                remainingSpace = remainingSpace.subtractConstraintSafely(placeable.width)
            } else {
                break
            }
        }

        overflowState.visibleItemCount = contentPlaceables.size

        // Measure overflow if needed
        val overflowPlaceables =
            if (contentPlaceables.size != contentMeasurables.size) {
                val overflowConstraints =
                    looseConstraints.copy(maxWidth = remainingSpace + reservedOverflowWidth)
                overflowMeasurables.fastMap { it.measure(overflowConstraints) }
            } else {
                null
            }

        val overflowWidth = overflowPlaceables?.fastMaxOfOrNull { it.width } ?: 0
        currentWidth += overflowWidth

        val childrenMaxHeight =
            max(
                contentPlaceables.fastMaxOfOrNull { it.height } ?: 0,
                overflowPlaceables?.fastMaxOfOrNull { it.height } ?: 0,
            )

        val width = constraints.constrainWidth(currentWidth)
        val height = constraints.constrainHeight(childrenMaxHeight)

        return layout(width, height) {
            var currentX = 0
            contentPlaceables.fastForEach {
                it.placeRelative(x = currentX, y = 0)
                currentX += it.width
            }
            overflowPlaceables?.fastForEach { it.placeRelative(x = currentX, y = 0) }
        }
    }
}
