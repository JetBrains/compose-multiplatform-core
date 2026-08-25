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

package androidx.compose.mpp.demo.bug

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.Text
import androidx.compose.mpp.demo.Screen
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.dp
import androidx.compose.mpp.demo.resources.Res
import org.jetbrains.compose.resources.painterResource

// https://youtrack.jetbrains.com/issue/CMP-4993
// On Web, VectorPainter cannot get the correct size and draw content when running inside other Painters
val VectorPainterInPainter = Screen.Example(
    "VectorPainter inside another Painter"
) {
    val iconVector = remember {
        ImageVector.Builder(
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = androidx.compose.ui.graphics.SolidColor(Color.Black)) {
                moveTo(19f, 5f)
                verticalLineTo(19f)
                lineTo(5f, 19f)
                lineTo(5f, 5f)
                horizontalLineTo(19f)
                moveTo(19f, 3f)
                lineTo(5f, 3f)
                curveTo(3.9f, 3f, 3f, 3.9f, 3f, 5f)
                verticalLineTo(19f)
                curveTo(3f, 20.1f, 3.9f, 21f, 5f, 21f)
                horizontalLineTo(19f)
                curveTo(20.1f, 21f, 21f, 20.1f, 21f, 19f)
                lineTo(21f, 5f)
                curveTo(21f, 3.9f, 20.1f, 3f, 19f, 3f)
                close()

                moveTo(14.14f, 11.86f)
                lineToRelative(-3f, 3.87f)
                lineTo(9f, 13.14f)
                lineTo(6f, 17f)
                horizontalLineTo(18f)
                lineToRelative(-3.86f, -5.14f)
                close()
            }
        }.build()
    }

    val vectorPainter = rememberVectorPainter(iconVector)

    Row(Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("1. Image with VectorPainter")
            Image(
                painter = vectorPainter,
                contentDescription = null,
                modifier = Modifier.size(100.dp).background(Color.Cyan)
            )

            Text("2. drawWithContent calling painter.draw")
            Box(
                modifier = Modifier.size(100.dp)
                    .background(Color.Magenta)
                    .drawWithContent {
                        with(vectorPainter) {
                            draw(Size(24f, 24f))
                        }
                    }
            )

            Text("3. Custom IconPainter wrapping VectorPainter")
            val iconPainter = remember(vectorPainter) {
                IconPainter(icon = vectorPainter, background = Color.Green)
            }
            Image(
                painter = iconPainter,
                contentDescription = null,
                modifier = Modifier.size(100.dp)
            )

            Text("intrinsicSize: ${vectorPainter.intrinsicSize}")
        }

        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            val iconImagePainter1 = painterResource(Res.drawable.ic_image_outline)
            Text("1. Image with painterResource")
            Image(
                painter = iconImagePainter1,
                contentDescription = null,
                modifier = Modifier.size(100.dp).background(Color.Cyan)
            )

            val iconImagePainter2 = painterResource(Res.drawable.ic_image_outline)
            Text("2. drawWithContent calling painter.draw")
            Box(
                modifier = Modifier.size(100.dp)
                    .background(Color.Magenta)
                    .drawWithContent {
                        with(iconImagePainter2) {
                            draw(Size(24f, 24f))
                        }
                    }
            )

            val iconImagePainter3 = painterResource(Res.drawable.ic_image_outline)
            Text("3. Custom IconPainter wrapping painterResource")
            val iconPainter = remember(iconImagePainter3) {
                IconPainter(icon = iconImagePainter3, background = Color.Green)
            }
            Image(
                painter = iconPainter,
                contentDescription = null,
                modifier = Modifier.size(100.dp)
            )

            Text("intrinsicSize: ${iconImagePainter3.intrinsicSize}")
        }
    }
}

@Stable
private class IconPainter(
    val icon: Painter,
    val background: Color? = null,
    val iconSize: Size? = null,
    val iconTint: Color? = null,
) : Painter() {

    private var alpha: Float = 1.0f
    private var colorFilter: ColorFilter? = null

    override val intrinsicSize: Size = Size.Unspecified

    override fun DrawScope.onDraw() {
        if (background != null) {
            drawRect(
                color = background,
                topLeft = Offset.Zero,
                size = this@onDraw.size,
                alpha = alpha,
                colorFilter = colorFilter
            )
        }

        val realIconSize = iconSize ?: icon.intrinsicSize
        val translateLeft = (size.width - realIconSize.width) / 2
        val translateTop = (size.height - realIconSize.height) / 2
        translate(left = translateLeft, top = translateTop) {
            with(icon) {
                val filter = iconTint?.let { ColorFilter.tint(it) }
                draw(size = realIconSize, colorFilter = filter)
            }
        }
    }

    override fun applyAlpha(alpha: Float): Boolean {
        this.alpha = alpha
        return true
    }

    override fun applyColorFilter(colorFilter: ColorFilter?): Boolean {
        this.colorFilter = colorFilter
        return true
    }
}
