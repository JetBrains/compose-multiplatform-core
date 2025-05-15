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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.HorizontalUncontainedCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.storytale.story

@OptIn(ExperimentalMaterial3Api::class)
val `HorizontalMultiBrowseCarousel Story` by story {
    val preferredItemWidth by parameter(186f)
    val itemSpacing by parameter(4f)
    val itemHeight by parameter(205f)
    val itemCount by parameter(5)

    // Create a list of colors for the items
    val colors = listOf(
        Color(0xFF1B5E20), // Dark Green
        Color(0xFF0D47A1), // Dark Blue
        Color(0xFFF57F17), // Dark Amber
        Color(0xFF4A148C), // Dark Purple
        Color(0xFF006064), // Dark Cyan
        Color(0xFF424242), // Dark Gray
        Color(0xFF212121), // Darker Gray
        Color(0xFF757575), // Medium Gray
        Color(0xFF000000), // Black
        Color(0xFFB71C1C)  // Dark Red
    )

    HorizontalMultiBrowseCarousel(
        state = rememberCarouselState { itemCount },
        modifier = Modifier.width(420.dp).height(400.dp),
        preferredItemWidth = preferredItemWidth.dp,
        itemSpacing = itemSpacing.dp,
        minSmallItemWidth = 40.dp,
        maxSmallItemWidth = 56.dp
    ) { i ->
        Box(
            modifier = Modifier
                .height(itemHeight.dp)
                .maskClip(MaterialTheme.shapes.extraLarge),
            contentAlignment = Alignment.Center
        ) {
            // Colored box instead of an image
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        color = colors[i % colors.size],
                        shape = MaterialTheme.shapes.extraLarge
                    )
            )

            // Add a text label to show the item index
            Text(
                text = "Item ${i + 1}\n" +
                    "Share business logic across Android, iOS, desktop, and web. " +
                    "One language, multiple targets.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
val `HorizontalUncontainedCarousel Story` by story {
    val itemWidth by parameter(186f)
    val itemSpacing by parameter(4f)
    val itemHeight by parameter(205f)
    val itemCount by parameter(5)

    // Create a list of colors for the items
    val colors = listOf(
        Color(0xFF1B5E20), // Dark Green
        Color(0xFF0D47A1), // Dark Blue
        Color(0xFFF57F17), // Dark Amber
        Color(0xFF4A148C), // Dark Purple
        Color(0xFF006064), // Dark Cyan
        Color(0xFF424242), // Dark Gray
        Color(0xFF212121), // Darker Gray
        Color(0xFF757575), // Medium Gray
        Color(0xFF000000), // Black
        Color(0xFFB71C1C) // Dark Red
    )

    HorizontalUncontainedCarousel(
        state = rememberCarouselState { itemCount },
        modifier = Modifier.width(420.dp).height(400.dp),
        itemWidth = itemWidth.dp,
        itemSpacing = itemSpacing.dp
    ) { i ->
        Box(
            modifier = Modifier
                .height(itemHeight.dp)
                .maskClip(MaterialTheme.shapes.extraLarge),
            contentAlignment = Alignment.Center
        ) {
            // Colored box instead of an image
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        color = colors[i % colors.size],
                        shape = MaterialTheme.shapes.extraLarge
                    )
            )

            // Add a text label to show the item index
            Text(
                text = "Item ${i + 1}\n" +
                    "With Kotlin Multiplatform and Compose, " +
                    "you can target all major platforms with a single codebase. " +
                    "Efficient and elegant.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}
