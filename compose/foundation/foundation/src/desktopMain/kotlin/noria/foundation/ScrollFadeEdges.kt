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

package noria.foundation

import androidx.compose.runtime.State
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.noriaComposed
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.times
import noria.ClosureContext

fun DrawScope.drawScrollFadeEdges(color: Color, fadePaddingValues: PaddingValues, scroll: ClosureContext.() -> ScrollState) {
  val scrollState = scroll(ClosureContext)
  val left = scrollState.isOverflowLeft(fadePaddingValues.calculateLeftPadding(layoutDirection)).toPx()
  if (left > 0f) {
    val brush = Brush.horizontalGradient(listOf(color, Color.Transparent), endX = left)
    drawRect(brush)
  }

  val right = scrollState.isOverflowRight(fadePaddingValues.calculateRightPadding(layoutDirection)).toPx()
  if (right > 0f) {
    val brush = Brush.horizontalGradient(listOf(Color.Transparent, color), startX = size.width - right, endX = size.width)
    drawRect(brush)
  }

  val top = scrollState.isOverflowTop(fadePaddingValues.calculateTopPadding()).toPx()
  if (top > 0f) {
    val brush = Brush.verticalGradient(listOf(color, Color.Transparent), endY = top)
    drawRect(brush)
  }

  val bottom = scrollState.isOverflowBottom(fadePaddingValues.calculateBottomPadding()).toPx()
  if (bottom > 0f) {
    val brush = Brush.verticalGradient(listOf(Color.Transparent, color), startY = size.height - bottom, endY = size.height)
    drawRect(brush)
  }
}

fun Modifier.scrollFadeEdges(color: Color, fadePaddingValues: PaddingValues, scroll: State<ScrollState>): Modifier =
  noriaComposed { noria ->
    with(noria) {
      Modifier.drawWithContent {
        drawContent()

        drawScrollFadeEdges(color, fadePaddingValues) { scroll.value }
      }
    }
  }

private fun ScrollState.isOverflowLeft(fadeSide: Dp): Dp {
  if (fadeSide <= 0.dp) return 0.dp
  val ratio = position.x / fadeSide
  return (ratio * fadeSide).coerceIn(0.dp, fadeSide)
}

private fun ScrollState.isOverflowRight(fadeSide: Dp): Dp {
  if (fadeSide <= 0.dp) return 0.dp
  val contentSize = contentSize ?: return 0.dp
  val size = scrollSize ?: return 0.dp
  val ratio = (contentSize.width - position.x - size.width) / fadeSide
  return (ratio * fadeSide).coerceIn(0.dp, fadeSide)
}

private fun ScrollState.isOverflowTop(fadeSide: Dp): Dp {
  if (fadeSide <= 0.dp) return 0.dp
  val ratio = position.y / fadeSide
  return (ratio * fadeSide).coerceIn(0.dp, fadeSide)
}

private fun ScrollState.isOverflowBottom(fadeSide: Dp): Dp {
  if (fadeSide <= 0.dp) return 0.dp
  val contentSize = contentSize ?: return 0.dp
  val size = scrollSize ?: return 0.dp
  val ratio = (contentSize.height - position.y - size.height) / fadeSide
  return (ratio * fadeSide).coerceIn(0.dp, fadeSide)
}

data class FadeOptions(
  val color: Color,
  val width: PaddingValues = PaddingValues(0.dp)
)