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

package androidx.xr.glimmer.demos

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.xr.glimmer.GlimmerTheme

@Composable
fun DemoApp(
    currentDemo: Demo,
    onNavigateToDemo: (Demo) -> Unit,
) {
    val overlayOnBackground = OverlayOnBackgroundSetting.asState().value
    GlimmerTheme {
        Column(
            Modifier.demoBackground(overlayOnBackground)
                .windowInsetsPadding(WindowInsets.systemBars)
        ) {
            DisplayDemo(currentDemo, onNavigateToDemo)
            val context = LocalContext.current
            Spacer(Modifier.weight(1f, fill = true))
            ListItem(onClick = { OverlayOnBackgroundSetting.set(context, !overlayOnBackground) }) {
                BasicText(
                    "${if (overlayOnBackground) "Disable" else "Enable"} overlay on background",
                    Modifier.height(56.dp).wrapContentSize(Alignment.Center),
                    TextStyle(color = Color.White)
                )
            }
        }
    }
}

@Composable
private fun DisplayDemo(demo: Demo, onNavigate: (Demo) -> Unit) {
    when (demo) {
        is ComposableDemo -> demo.content()
        is DemoCategory -> DisplayDemoCategory(demo, onNavigate)
    }
}

@Composable
private fun DisplayDemoCategory(category: DemoCategory, onNavigate: (Demo) -> Unit) {
    LazyColumn {
        items(category.demos) { demo ->
            ListItem(onClick = { onNavigate(demo) }) {
                BasicText(
                    demo.title,
                    Modifier.height(56.dp).wrapContentSize(Alignment.Center),
                    TextStyle(color = Color.White)
                )
            }
        }
    }
}

@Composable
private fun ListItem(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (() -> Unit)
) {
    Box(
        modifier
            .heightIn(min = 48.dp)
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp)
            .wrapContentHeight(Alignment.CenterVertically),
        contentAlignment = Alignment.CenterStart
    ) {
        content()
    }
}

@Composable
private fun Modifier.demoBackground(overlayOnBackground: Boolean) =
    this.then(
        if (overlayOnBackground) {
            Modifier.background(
                    brush = Brush.linearGradient(0f to Color(0xFF375672), 1f to Color(0xFF6D6637))
                )
                .graphicsLayer {
                    compositingStrategy = CompositingStrategy.Offscreen
                    blendMode = BlendMode.Screen
                }
        } else {
            Modifier.background(GlimmerTheme.colors.surface)
        }
    )
