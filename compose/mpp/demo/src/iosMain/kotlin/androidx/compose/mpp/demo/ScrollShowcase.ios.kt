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
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The very same scrollable content shown twice in the demo app: once with the stock Compose
 * scrolling ([ScrollShowcaseWithDefaultOverscroll]) and once driven by [NavigationOverscrollEffect]
 * ([ScrollShowcaseWithNavigationOverscroll]), so the two can be compared side by side.
 */
@Composable
internal fun ScrollShowcaseWithDefaultOverscroll() {
    val colors = rememberShowcaseColors()
    // Unlike NavigationOverscrollEffect, the stock scrolling knows nothing about the navigation
    // chrome, so the content has to be inset past it by hand
    val safeDrawing = WindowInsets.safeDrawing.asPaddingValues()
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().background(colors.background),
            contentPadding = PaddingValues(
                top = safeDrawing.calculateTopPadding() + 12.dp,
                bottom = safeDrawing.calculateBottomPadding() + 12.dp,
            ),
        ) {
            scrollShowcaseContent(
                title = "Default scrolling",
                subtitle = "Stock Compose overscroll. The navigation bar and the tab bar are not " +
                    "aware of the rubber banding.",
                colors = colors,
            )
        }
        SafeAreaFade(colors)
    }
}

/**
 * Same content as [ScrollShowcaseWithDefaultOverscroll], but the scroll is handed over to
 * [NavigationOverscrollEffect], which drives the surrounding UIKit navigation chrome while the
 * content is rubber banding.
 */
@Composable
internal fun ScrollShowcaseWithNavigationOverscroll() {
    val colors = rememberShowcaseColors()
    val scrollState = rememberLazyListState()
    val density = LocalDensity.current
    val overscrollEffect = remember(density, scrollState) {
        NavigationOverscrollEffect(density = density, scrollableState = scrollState)
    }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = scrollState,
            overscrollEffect = overscrollEffect,
            flingBehavior = overscrollEffect,
            modifier = Modifier.fillMaxSize().background(colors.background),
            contentPadding = PaddingValues(vertical = 12.dp),
        ) {
            scrollShowcaseContent(
                title = "Navigation overscroll",
                subtitle = "The overscroll effect moves the large title and the tab bar along " +
                    "with the content, the way UIScrollView does.",
                colors = colors,
            )
        }
        SafeAreaFade(colors)
    }
}

/**
 * Fades the content out into the screen background behind the translucent navigation bar and tab
 * bar, so the rows do not show through the chrome while they scroll past it. Each band is exactly
 * as tall as the safe drawing inset it covers.
 */
@Composable
private fun BoxScope.SafeAreaFade(colors: ShowcaseColors) {
    val safeDrawing = WindowInsets.safeDrawing.asPaddingValues()
    Spacer(
        Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .height(safeDrawing.calculateTopPadding())
            .background(Brush.verticalGradient(listOf(colors.background, colors.background.copy(alpha = 0.95f), Color.Transparent)))
    )
    Spacer(
        Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .height(safeDrawing.calculateBottomPadding())
            .background(Brush.verticalGradient(listOf(Color.Transparent, colors.background.copy(alpha = 0.95f), colors.background)))
    )
}

private const val ShowcaseItemCount = 60

private fun LazyListScope.scrollShowcaseContent(
    title: String,
    subtitle: String,
    colors: ShowcaseColors,
) {
    item("hero") {
        HeroCard(title = title, subtitle = subtitle)
    }
    item("stats") {
        StatsRow(colors)
    }
    item("section") {
        SectionHeader("Gradients", colors)
    }
    items(ShowcaseItemCount, key = { "item-$it" }) { index ->
        ShowcaseCard(index, colors)
    }
    item("footer") {
        Footer(colors)
    }
}

@Composable
private fun HeroCard(title: String, subtitle: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .height(200.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF6A3DF0), Color(0xFFE05B8A))))
    ) {
        Column(
            modifier = Modifier.align(Alignment.BottomStart).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = subtitle,
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 15.sp,
                lineHeight = 20.sp,
            )
        }
    }
}

@Composable
private fun StatsRow(colors: ShowcaseColors) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StatTile("$ShowcaseItemCount", "items", colors, Modifier.weight(1f))
        StatTile("120", "fps", colors, Modifier.weight(1f))
        StatTile("1", "gesture", colors, Modifier.weight(1f))
    }
}

@Composable
private fun StatTile(value: String, label: String, colors: ShowcaseColors, modifier: Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(colors.card)
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(value, color = colors.title, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        Text(label, color = colors.subtitle, fontSize = 13.sp)
    }
}

@Composable
private fun SectionHeader(text: String, colors: ShowcaseColors) {
    Text(
        text = text.uppercase(),
        color = colors.subtitle,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 8.dp),
    )
}

@Composable
private fun ShowcaseCard(index: Int, colors: ShowcaseColors) {
    val gradient = ShowcaseGradients[index % ShowcaseGradients.size]
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(colors.card)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(gradient.colors)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "${index + 1}",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = gradient.name,
                color = colors.title,
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = gradient.hexes,
                color = colors.subtitle,
                fontSize = 13.sp,
            )
        }
    }
}

@Composable
private fun Footer(colors: ShowcaseColors) {
    Text(
        text = "That's the end of the list.\nKeep pulling to see how the edge behaves.",
        color = colors.subtitle,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 32.dp),
    )
}

private class ShowcaseColors(
    val background: Color,
    val card: Color,
    val title: Color,
    val subtitle: Color,
)

@Composable
private fun rememberShowcaseColors(): ShowcaseColors {
    val dark = isSystemInDarkTheme()
    return remember(dark) {
        if (dark) {
            ShowcaseColors(
                background = Color(0xFF0D0E12),
                card = Color(0xFF1A1C23),
                title = Color(0xFFF1F2F6),
                subtitle = Color(0xFF9EA2B0),
            )
        } else {
            ShowcaseColors(
                background = Color(0xFFF5F6FA),
                card = Color(0xFFFFFFFF),
                title = Color(0xFF14161C),
                subtitle = Color(0xFF70758A),
            )
        }
    }
}

private class ShowcaseGradient(val name: String, val from: Long, val to: Long) {
    val colors get() = listOf(Color(from), Color(to))
    val hexes get() = "#${from.toString(16).uppercase().takeLast(6)} → " +
        "#${to.toString(16).uppercase().takeLast(6)}"
}

private val ShowcaseGradients = listOf(
    ShowcaseGradient("Aurora", 0xFF7F5AF0, 0xFF2CB67D),
    ShowcaseGradient("Sunset", 0xFFFF7E5F, 0xFFFEB47B),
    ShowcaseGradient("Deep Sea", 0xFF2193B0, 0xFF6DD5ED),
    ShowcaseGradient("Mulberry", 0xFFC33764, 0xFF1D2671),
    ShowcaseGradient("Lime Soda", 0xFF56AB2F, 0xFFA8E063),
    ShowcaseGradient("Coral Reef", 0xFFFF512F, 0xFFDD2476),
    ShowcaseGradient("Blue Raspberry", 0xFF00B4DB, 0xFF0083B0),
    ShowcaseGradient("Peach", 0xFFED4264, 0xFFFFEDBC),
    ShowcaseGradient("Moonlit", 0xFF0F2027, 0xFF2C5364),
    ShowcaseGradient("Cotton Candy", 0xFFD9A7C7, 0xFFFFFCDC),
    ShowcaseGradient("Emerald", 0xFF348F50, 0xFF56B4D3),
    ShowcaseGradient("Amethyst", 0xFF9D50BB, 0xFF6E48AA),
)
