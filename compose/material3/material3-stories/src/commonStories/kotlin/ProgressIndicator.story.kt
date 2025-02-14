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

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import org.jetbrains.compose.storytale.story


@OptIn(ExperimentalMaterial3Api::class)
val `CircularProgressIndicator Story` by story {
    // Progress (determinate or indeterminate)
    val isDeterminate by parameter(true)
    var progress by parameter(0.65f)
    val useAnimation by parameter(true)
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec
    )

    // Appearance customization
    val useCustomColor by parameter(false)
    val color by parameter(Color(0xFF6750A4))

    val strokeWidth by parameter(4f)

    val useCustomTrackColor by parameter(false)
    val trackColor by parameter(Color(0xFFE6E0E9))

    val useRoundedCap by parameter(true)

    val useCustomGapSize by parameter(false)
    val gapSize by parameter(8f)

    val size by parameter(48f)

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (isDeterminate) {
            CircularProgressIndicator(
                progress = { if (useAnimation) animatedProgress else progress },
                modifier = Modifier.size(size.dp),
                color = if (useCustomColor) color else ProgressIndicatorDefaults.circularColor,
                strokeWidth = strokeWidth.dp,
                trackColor = if (useCustomTrackColor) trackColor else ProgressIndicatorDefaults.circularDeterminateTrackColor,
                strokeCap = if (useRoundedCap) StrokeCap.Round else StrokeCap.Butt,
                gapSize = if (useCustomGapSize) gapSize.dp else ProgressIndicatorDefaults.CircularIndicatorTrackGapSize
            )

            Spacer(Modifier.height(16.dp))
            Text("Progress: ${(progress * 100).toInt()}%", color = MaterialTheme.colorScheme.onSurface)
            Slider(
                modifier = Modifier.width(300.dp),
                value = progress,
                onValueChange = { progress = it },
                valueRange = 0f..1f
            )
        } else {
            CircularProgressIndicator(
                modifier = Modifier.size(size.dp),
                color = if (useCustomColor) color else ProgressIndicatorDefaults.circularColor,
                strokeWidth = strokeWidth.dp,
                trackColor = if (useCustomTrackColor) trackColor else ProgressIndicatorDefaults.circularIndeterminateTrackColor,
                strokeCap = if (useRoundedCap) StrokeCap.Round else StrokeCap.Butt
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
val `LinearProgressIndicator Story` by story {
    // Progress (determinate or indeterminate)
    val isDeterminate by parameter(true)
    var progress by parameter(0.65f)
    val useAnimation by parameter(true)
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec
    )

    // Appearance customization
    val useCustomColor by parameter(false)
    val color by parameter(Color(0xFF6750A4))

    val useCustomTrackColor by parameter(false)
    val trackColor by parameter(Color(0xFFE6E0E9))

    val useRoundedCap by parameter(true)

    val useCustomGapSize by parameter(false)
    val gapSize by parameter(8f)

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (isDeterminate) {
            LinearProgressIndicator(
                progress = { if (useAnimation) animatedProgress else progress },
                modifier = Modifier.width(280.dp),
                color = if (useCustomColor) color else ProgressIndicatorDefaults.linearColor,
                trackColor = if (useCustomTrackColor) trackColor else ProgressIndicatorDefaults.linearTrackColor,
                strokeCap = if (useRoundedCap) StrokeCap.Round else StrokeCap.Butt,
                gapSize = if (useCustomGapSize) gapSize.dp else ProgressIndicatorDefaults.LinearIndicatorTrackGapSize
            )

            Spacer(Modifier.height(16.dp))
            Text("Progress: ${(progress * 100).toInt()}%", color = MaterialTheme.colorScheme.onSurface)
            Slider(
                modifier = Modifier.width(280.dp),
                value = progress,
                onValueChange = { progress = it },
                valueRange = 0f..1f
            )
        } else {
            LinearProgressIndicator(
                modifier = Modifier.width(280.dp),
                color = if (useCustomColor) color else ProgressIndicatorDefaults.linearColor,
                trackColor = if (useCustomTrackColor) trackColor else ProgressIndicatorDefaults.linearTrackColor,
                strokeCap = if (useRoundedCap) StrokeCap.Round else StrokeCap.Butt
            )
        }
    }
}
