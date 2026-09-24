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

package androidx.compose.mpp.demo

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.captionBar
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fitInside
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.mandatorySystemGestures
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContent
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.safeGestures
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.foundation.layout.tappableElement
import androidx.compose.foundation.layout.waterfall
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.Button
import androidx.compose.material.Checkbox
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Placeable.PlacementScope
import androidx.compose.ui.layout.RectRulers
import androidx.compose.ui.layout.WindowInsetsRulers
import androidx.compose.ui.layout.getDisplayCutoutBounds
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

val WindowInsetsDemos = Screen.Selection(
    "Window Insets",
    Screen.Fullscreen("Window Insets Padding") { WindowInsetsPaddingDemo(it) },
    Screen.Fullscreen("Window Insets Rulers") { WindowInsetsRulersDemo(it) },
    Screen.Fullscreen("Display Cutouts") { DisplayCutoutsDemo(it) },
)

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun WindowInsetsPaddingDemo(back: () -> Unit) {
    InsetOverlayDemo(
        title = "Window Insets Padding",
        overlays = WindowInsetOverlays,
        back = back,
    ) { overlay ->
        Box(
            Modifier.fillMaxSize()
                .windowInsetsPadding(overlay.insets())
                .border(4.dp, overlay.color)
                .background(overlay.color.copy(alpha = OverlayAlpha)),
        )
    }
}

@Composable
private fun WindowInsetsRulersDemo(back: () -> Unit) {
    InsetOverlayDemo(
        title = "Window Insets Rulers",
        overlays = WindowInsetsRulerOverlays,
        back = back,
    ) { overlay ->
        Box(
            Modifier.fillMaxSize()
                .fitInside(overlay.rulers.current)
                .border(4.dp, overlay.color)
                .background(overlay.color.copy(alpha = OverlayAlpha)),
        )
    }
}

@Composable
private fun DisplayCutoutsDemo(back: () -> Unit) {
    var cutoutBounds by remember { mutableStateOf(emptyList<IntRect>()) }
    val density = LocalDensity.current

    MaterialTheme {
        Box(
            Modifier.fillMaxSize()
                .background(Color(0xFFF8F9FA))
                .captureDisplayCutoutBounds { bounds ->
                    if (cutoutBounds != bounds) {
                        cutoutBounds = bounds
                    }
                },
        ) {
            cutoutBounds.forEachIndexed { index, bounds ->
                val width = with(density) { bounds.width.toDp() }
                val height = with(density) { bounds.height.toDp() }
                val color = OverlayColors[index % OverlayColors.size]
                Box(
                    Modifier.offset { IntOffset(bounds.left, bounds.top) }
                        .size(width, height)
                        .border(4.dp, color)
                        .background(color.copy(alpha = OverlayAlpha)),
                )
            }
            DisplayCutoutsControls(back, cutoutBounds.size)
        }
    }
}

@Composable
private fun <T : InsetOverlay> InsetOverlayDemo(
    title: String,
    overlays: List<T>,
    back: () -> Unit,
    content: @Composable (T) -> Unit,
) {
    var selectedLabels by remember { mutableStateOf(setOf("statusBars")) }

    MaterialTheme {
        Box(Modifier.fillMaxSize().background(Color(0xFFF8F9FA))) {
            overlays.filter { it.label in selectedLabels }.forEach { content(it) }
            InsetOverlayControls(
                title = title,
                overlays = overlays,
                selectedLabels = selectedLabels,
                onSelectedLabelsChange = { selectedLabels = it },
                back = back,
            )
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun InsetOverlayControls(
    title: String,
    overlays: List<InsetOverlay>,
    selectedLabels: Set<String>,
    onSelectedLabelsChange: (Set<String>) -> Unit,
    back: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeContent)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = back) {
                Text("Back")
            }
            Spacer(Modifier.width(12.dp))
            Text(title, style = MaterialTheme.typography.h6)
        }
        Box {
            Button(onClick = { menuExpanded = true }) {
                Text("Select insets (${selectedLabels.size})")
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
                modifier = Modifier.heightIn(max = 440.dp),
            ) {
                overlays.forEach { overlay ->
                    val selected = overlay.label in selectedLabels
                    DropdownMenuItem(
                        onClick = {
                            onSelectedLabelsChange(
                                if (selected) selectedLabels - overlay.label
                                else selectedLabels + overlay.label,
                            )
                        },
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = selected, onCheckedChange = null)
                            Spacer(Modifier.width(8.dp))
                            Text(overlay.label)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun DisplayCutoutsControls(back: () -> Unit, cutoutCount: Int) {
    Column(
        modifier = Modifier.fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeContent)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = back) {
                Text("Back")
            }
            Spacer(Modifier.width(12.dp))
            Text("Display Cutouts", style = MaterialTheme.typography.h6)
        }
        Text("Reported cutouts: $cutoutCount")
    }
}

private fun Modifier.captureDisplayCutoutBounds(
    onBoundsChange: (List<IntRect>) -> Unit,
): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    layout(placeable.width, placeable.height) {
        placeable.place(0, 0)
        onBoundsChange(getDisplayCutoutBounds().mapNotNull(::readRect))
    }
}

private fun PlacementScope.readRect(rulers: RectRulers): IntRect? {
    val left = rulers.left.current(Float.NaN)
    val top = rulers.top.current(Float.NaN)
    val right = rulers.right.current(Float.NaN)
    val bottom = rulers.bottom.current(Float.NaN)
    if (left.isNaN() || top.isNaN() || right.isNaN() || bottom.isNaN()) {
        return null
    }
    return IntRect(left.roundToInt(), top.roundToInt(), right.roundToInt(), bottom.roundToInt())
}

private interface InsetOverlay {
    val label: String
    val color: Color
}

private data class WindowInsetOverlay(
    override val label: String,
    override val color: Color,
    val insets: @Composable () -> WindowInsets,
) : InsetOverlay

private data class WindowInsetsRulerOverlay(
    override val label: String,
    override val color: Color,
    val rulers: WindowInsetsRulers,
) : InsetOverlay

private const val OverlayAlpha = 0.25f

private val OverlayColors = listOf(
    Color(0xFFE53935),
    Color(0xFFD81B60),
    Color(0xFF8E24AA),
    Color(0xFF5E35B1),
    Color(0xFF3949AB),
    Color(0xFF1E88E5),
    Color(0xFF00897B),
    Color(0xFF43A047),
    Color(0xFF7CB342),
    Color(0xFFFDD835),
    Color(0xFFFFB300),
    Color(0xFFFB8C00),
    Color(0xFFF4511E),
)

private val WindowInsetOverlays = insetOverlayDefinitions { label, color, insets ->
    WindowInsetOverlay(label, color, insets)
}

private val WindowInsetsRulerOverlays = insetOverlayDefinitions { label, color, _ ->
    WindowInsetsRulerOverlay(
        label = label,
        color = color,
        rulers = when (label) {
            "captionBar" -> WindowInsetsRulers.CaptionBar
            "displayCutout" -> WindowInsetsRulers.DisplayCutout
            "ime" -> WindowInsetsRulers.Ime
            "mandatorySystemGestures" -> WindowInsetsRulers.MandatorySystemGestures
            "navigationBars" -> WindowInsetsRulers.NavigationBars
            "statusBars" -> WindowInsetsRulers.StatusBars
            "systemBars" -> WindowInsetsRulers.SystemBars
            "systemGestures" -> WindowInsetsRulers.SystemGestures
            "tappableElement" -> WindowInsetsRulers.TappableElement
            "waterfall" -> WindowInsetsRulers.Waterfall
            "safeDrawing" -> WindowInsetsRulers.SafeDrawing
            "safeGestures" -> WindowInsetsRulers.SafeGestures
            "safeContent" -> WindowInsetsRulers.SafeContent
            else -> error("Unknown inset ruler: $label")
        },
    )
}

@OptIn(ExperimentalComposeUiApi::class)
private fun <T> insetOverlayDefinitions(
    create: (String, Color, @Composable () -> WindowInsets) -> T,
): List<T> = listOf(
    create("captionBar", OverlayColors[0]) { WindowInsets.captionBar },
    create("displayCutout", OverlayColors[1]) { WindowInsets.displayCutout },
    create("ime", OverlayColors[2]) { WindowInsets.ime },
    create("mandatorySystemGestures", OverlayColors[3]) { WindowInsets.mandatorySystemGestures },
    create("navigationBars", OverlayColors[4]) { WindowInsets.navigationBars },
    create("statusBars", OverlayColors[5]) { WindowInsets.statusBars },
    create("systemBars", OverlayColors[6]) { WindowInsets.systemBars },
    create("systemGestures", OverlayColors[7]) { WindowInsets.systemGestures },
    create("tappableElement", OverlayColors[8]) { WindowInsets.tappableElement },
    create("waterfall", OverlayColors[9]) { WindowInsets.waterfall },
    create("safeDrawing", OverlayColors[10]) { WindowInsets.safeDrawing },
    create("safeGestures", OverlayColors[11]) { WindowInsets.safeGestures },
    create("safeContent", OverlayColors[12]) { WindowInsets.safeContent },
)
