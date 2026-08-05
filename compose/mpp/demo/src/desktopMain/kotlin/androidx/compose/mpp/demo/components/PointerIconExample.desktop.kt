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

package androidx.compose.mpp.demo.components

import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.res.useResource
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Cursor
import java.awt.Image
import java.awt.Point
import java.awt.RenderingHints
import java.awt.Toolkit
import java.awt.image.BufferedImage

private val CUSTOM_CURSOR_IMAGE: Image = BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB).apply {
    createGraphics().apply {
        setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        // Orange filled circle with a white outline.
        color = Color(0xFF, 0x57, 0x22)
        fillOval(4, 4, 24, 24)
        color = Color.WHITE
        stroke = BasicStroke(2f)
        drawOval(4, 4, 24, 24)
        // White center dot.
        fillOval(13, 13, 6, 6)
        dispose()
    }
}

/**
 * Creates a custom AWT image [Cursor] with the given hotspot and name.
 * @see java.awt.Toolkit.createCustomCursor
 */
private fun customImageCursor(hotspotX: Int, hotspotY: Int, name: String): Cursor =
    Toolkit.getDefaultToolkit().createCustomCursor(
        CUSTOM_CURSOR_IMAGE,
        Point(hotspotX, hotspotY),
        name,
    )


/**
 * Custom AWT image cursor built from an image loaded from project resources
 * (`desktopMain/resources/custom-cursor.png`). Analogous to the web target's
 * `url("loading.svg"), auto` cursor, but resolved via the JVM classloader.
 */
@Suppress("DEPRECATION")
private val RESOURCE_IMAGE_CURSOR: Cursor = Toolkit.getDefaultToolkit().createCustomCursor(
    useResource("custom-cursor.png", javax.imageio.ImageIO::read),
    Point(16, 16),
    "compose-demo-custom-resource",
)

internal actual val platformPointerIcons: List<Pair<String, PointerIcon>> = listOf(
    "MOVE_CURSOR" to PointerIcon(Cursor(Cursor.MOVE_CURSOR)),
    "WAIT_CURSOR" to PointerIcon(Cursor(Cursor.WAIT_CURSOR)),
    "N_RESIZE_CURSOR" to PointerIcon(Cursor(Cursor.N_RESIZE_CURSOR)),
    "E_RESIZE_CURSOR" to PointerIcon(Cursor(Cursor.E_RESIZE_CURSOR)),
    "NE_RESIZE_CURSOR" to PointerIcon(Cursor(Cursor.NE_RESIZE_CURSOR)),
    "NW_RESIZE_CURSOR" to PointerIcon(Cursor(Cursor.NW_RESIZE_CURSOR)),

    // Custom AWT image cursor with a top-left (0, 0) hotspot. Analogous to the web target's
    // `url(...) , auto` cursor built from an inline SVG data URL.
    "custom image (hotspot=0,0)" to PointerIcon(
        customImageCursor(hotspotX = 0, hotspotY = 0, name = "compose-demo-custom-topleft")
    ),
    // Same custom image, but with an explicit centered hotspot. Analogous to the web target's
    // `url(...) 16 16, pointer` cursor.
    "custom image (hotspot=16,16)" to PointerIcon(
        customImageCursor(hotspotX = 16, hotspotY = 16, name = "compose-demo-custom-center")
    ),

    "custom-cursor.png (from resources)" to PointerIcon(RESOURCE_IMAGE_CURSOR),
)
