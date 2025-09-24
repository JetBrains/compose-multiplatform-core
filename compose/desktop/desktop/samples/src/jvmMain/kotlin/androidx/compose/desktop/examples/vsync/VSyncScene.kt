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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toIntSize
import noria.NoriaContext

@Composable
fun NoriaContext.AnimatedTransitionExample(rpm: Int) {
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
fun NoriaContext.RunningSquares(windowSize: DpSize, refreshRate: Int) {
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
fun NoriaContext.WindowContent(windowSize: DpSize, refreshRate: Int) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(4.dp)
            .background(Color.Black)
            .padding(2.dp)
            .background(Color.White)
    ) {
        AnimatedTransitionExample(rpm = 10)
        RunningSquares(windowSize, refreshRate)
    }
}