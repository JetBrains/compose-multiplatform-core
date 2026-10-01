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

package androidx.compose.ui.tooling.recomposition

import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.desktop.WindowOverlayRenderer
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.unit.Density

/**
 * Draws a fading rectangle around the nodes of every scope that recomposed in the last
 * [fadeMillis], colored by why it ran, and a red frame around looping scopes.
 */
@OptIn(InternalComposeUiApi::class)
class RecompositionOverlay internal constructor(
    private val inspector: RecompositionInspector,
    private val requestRedraw: () -> Unit,
) : WindowOverlayRenderer {

    /** How long a flash stays visible, in milliseconds. */
    var fadeMillis: Long = 500

    /** Only draw scopes at least this deep, to hide the window root scopes. */
    var minDepth: Int = 0

    private val fill = Paint().apply { style = PaintingStyle.Fill }
    private val stroke = Paint().apply { style = PaintingStyle.Stroke }

    override fun onRenderOverlay(canvas: Canvas, density: Density) {
        val snapshot = inspector.snapshot.value
        val now = System.nanoTime()
        var needsMoreFrames = false
        stroke.strokeWidth = 1.5f * density.density
        for (scope in snapshot.scopes) {
            val bounds = scope.bounds ?: continue
            if (scope.depth < minDepth) continue
            val ageMillis = (now - scope.lastRunNanos) / 1_000_000
            val looping = scope.consecutiveFrames >= inspector.settings.loopFrameThreshold
            if (ageMillis < 0 || ageMillis >= fadeMillis) continue
            val alpha = 1f - ageMillis.toFloat() / fadeMillis
            val color = colorFor(scope.lastRunCause, looping)
            needsMoreFrames = true
            fill.color = color.copy(alpha = alpha * 0.18f)
            stroke.color = color.copy(alpha = alpha)
            canvas.drawRect(bounds, fill)
            canvas.drawRect(bounds, stroke)
            if (looping) {
                stroke.color = LoopColor
                canvas.drawRect(bounds.inflate(2f * density.density), stroke)
            }
        }
        if (needsMoreFrames) requestRedraw()
    }

    private fun colorFor(cause: RunCause, looping: Boolean): Color = when {
        looping -> LoopColor
        cause == RunCause.ParentDriven -> ParentDrivenColor
        cause == RunCause.ExplicitInvalidate -> ExplicitColor
        cause == RunCause.Initial -> InitialColor
        else -> OwnStateColor
    }

    companion object {
        val OwnStateColor = Color(0xFF2E9E5B)
        val ParentDrivenColor = Color(0xFFE08A1E)
        val ExplicitColor = Color(0xFF7A5AF8)
        val InitialColor = Color(0xFF3B82F6)
        val LoopColor = Color(0xFFE5484D)
    }
}
