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

package androidx.compose.desktop.examples.vsync

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toIntSize

@Composable
fun AnimatedTransitionExample(rpm: Int) {
    var rotation by remember { mutableStateOf(0f) }
    val infiniteTransition = rememberInfiniteTransition()
    val ticker by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 1000,
                easing = LinearEasing
            )
        )
    )

//    LaunchedEffect(ticker) {
//        rotation += 0.1f * rpm
//    }

    Box(
        Modifier

            .background(color = Color.Green)
    ) {
        Box(Modifier
            .align(Alignment.Center)
            .graphicsLayer { rotationZ = ticker}
            .size(60.dp, 10.dp)
            .background(Color.Red))
    }
}

@Composable
fun RunningSquares(windowSize: DpSize, refreshRate: Int) {
    val frameLogger = remember { FrameLogger() }
    val windowIntSize = with(LocalDensity.current) {
        windowSize.toSize().toIntSize()
    }
    val singleFrameMillis = remember {
        1000 / refreshRate
    }
    var position1 by remember { mutableStateOf(0L) }
    var position2 by remember { mutableStateOf(0L) }
    var isOddFrame by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            withFrameMillis {
                position1 = it % windowIntSize.width
                position2 = (it / 4) % windowIntSize.width
            }
        }
    }

    Canvas(Modifier.fillMaxSize()) {
        for (x in 0..windowIntSize.width step singleFrameMillis) {
            drawLine(Color.Black, Offset(x.toFloat(), 0f), Offset(x.toFloat(), 10f))
        }

        drawRect(Color.Red, Offset(position1.toFloat(), 10f), Size(32f, 32f))
        drawRect(Color.Red, Offset(position2.toFloat(), 50f), Size(32f, 32f))

        // test similar to https://www.vsynctester.com/
        drawRect(if (isOddFrame) Color.Red else Color.Cyan, Offset(10f, 120f), Size(50f, 50f))
        isOddFrame = !isOddFrame

        frameLogger.logFrame()
    }
}

@Composable
fun FancyBorder(
    modifier: Modifier = Modifier,
    borderWidth: Float = 6f,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier.drawBehind {
            val borderWidthPx = borderWidth.dp.toPx()
            val colors = listOf(
                Color(0xFF6A5ACD), // SlateBlue
                Color(0xFF20B2AA), // LightSeaGreen
                Color(0xFFFF6347)  // Tomato
            )

            val segmentSize = 150.dp.toPx() // Constant segment size

            // Top border - 3 segments: left at start, middle at center, right at end
            // Left segment at start
            drawRect(
                color = colors[0],
                topLeft = Offset(0f, 0f),
                size = Size(segmentSize, borderWidthPx)
            )
            // Middle segment at center
            drawRect(
                color = colors[1],
                topLeft = Offset((size.width - segmentSize) / 2f, 0f),
                size = Size(segmentSize, borderWidthPx)
            )
            // Right segment at end
            drawRect(
                color = colors[2],
                topLeft = Offset(size.width - segmentSize, 0f),
                size = Size(segmentSize, borderWidthPx)
            )

            // Bottom border - 3 segments: left at start, middle at center, right at end
            // Left segment at start
            drawRect(
                color = colors[0],
                topLeft = Offset(0f, size.height - borderWidthPx),
                size = Size(segmentSize, borderWidthPx)
            )
            // Middle segment at center
            drawRect(
                color = colors[1],
                topLeft = Offset((size.width - segmentSize) / 2f, size.height - borderWidthPx),
                size = Size(segmentSize, borderWidthPx)
            )
            // Right segment at end
            drawRect(
                color = colors[2],
                topLeft = Offset(size.width - segmentSize, size.height - borderWidthPx),
                size = Size(segmentSize, borderWidthPx)
            )

            // Left border - 3 segments: top at start, middle at center, bottom at end
            // Top segment at start
            drawRect(
                color = colors[0],
                topLeft = Offset(0f, 0f),
                size = Size(borderWidthPx, segmentSize)
            )
            // Middle segment at center
            drawRect(
                color = colors[1],
                topLeft = Offset(0f, (size.height - segmentSize) / 2f),
                size = Size(borderWidthPx, segmentSize)
            )
            // Bottom segment at end
            drawRect(
                color = colors[2],
                topLeft = Offset(0f, size.height - segmentSize),
                size = Size(borderWidthPx, segmentSize)
            )

            // Right border - 3 segments: top at start, middle at center, bottom at end
            // Top segment at start
            drawRect(
                color = colors[0],
                topLeft = Offset(size.width - borderWidthPx, 0f),
                size = Size(borderWidthPx, segmentSize)
            )
            // Middle segment at center
            drawRect(
                color = colors[1],
                topLeft = Offset(size.width - borderWidthPx, (size.height - segmentSize) / 2f),
                size = Size(borderWidthPx, segmentSize)
            )
            // Bottom segment at end
            drawRect(
                color = colors[2],
                topLeft = Offset(size.width - borderWidthPx, size.height - segmentSize),
                size = Size(borderWidthPx, segmentSize)
            )
        }
    ) {
        Box(
            modifier = Modifier
                .padding(borderWidth.dp)
                .background(Color.White)
        ) {
            content()
        }
    }
}

@Composable
fun WindowContent(windowSize: DpSize, refreshRate: Int) {
    FancyBorder(
        modifier = Modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier.fillMaxSize().padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            BasicText(
                text = "Lorem ipsum dolor sit amet, consectetur adipiscing elit. " +
                        "Sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. " +
                        "Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris " +
                        "nisi ut aliquip ex ea commodo consequat. Duis aute irure dolor in " +
                        "reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur. " +
                        "Excepteur sint occaecat cupidatat non proident, sunt in culpa qui " +
                        "officia deserunt mollit anim id est laborum.",
                style = TextStyle(
                    fontSize = 18.sp,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
            )
        }
//        AnimatedTransitionExample(rpm = 10)
//        RunningSquares(windowSize, refreshRate)
    }
}