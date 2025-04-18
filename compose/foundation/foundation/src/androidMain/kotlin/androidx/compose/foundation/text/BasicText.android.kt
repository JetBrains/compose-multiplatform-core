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

package androidx.compose.foundation.text

import androidx.annotation.VisibleForTesting
import androidx.compose.runtime.Composable
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.MultiParagraphIntrinsics
import androidx.compose.ui.text.ParagraphIntrinsics
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.resolveDefaults
import androidx.compose.ui.util.trace
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException

/**
 * CompositionLocal that enables/disables premeasurement behavior on BasicText when non-null.
 *
 * BasicText premeasure is the process of using a background thread to early start metrics
 * calculation for Text composables on Android to warm up the underlying text layout cache. This
 * becomes especially useful in LazyLists when precomposition may precede premeasurement by at least
 * a frame, which gives the background thread enough time to fully calculate text metrics. This
 * approximately reduces text layout duration on main thread from 50% to 90%.
 *
 * By default this CompositionLocal provides null, which means that prefetch behavior is disabled
 * for BasicText. You can provide an executor like `Executors.newSingleThreadExecutor()` for
 * BasicText to schedule background tasks, by doing so also enabling prefetch behavior.
 *
 * Please note that prefetch text does not guarantee a net performance increase. It may actually be
 * harmful in certain scenarios where there is not enough time between composition and measurement
 * for background thread to actually start warming the cache. Use benchmarking tools to check
 * whether enabling this behavior works well for your use case.
 */
val LocalBackgroundTextMeasurementExecutor = staticCompositionLocalOf<Executor?> { null }

@Composable
@NonRestartableComposable
internal actual fun BackgroundTextMeasurement(
    text: String,
    style: TextStyle,
    fontFamilyResolver: FontFamily.Resolver
) {
    val executor = LocalBackgroundTextMeasurementExecutor.current
    if (executor != null && shouldPrefetch(text.length)) {
        val layoutDirection = LocalLayoutDirection.current
        val density = LocalDensity.current

        try {
            executor.execute {
                trace("BackgroundTextMeasurement") {
                    val resolvedStyle = resolveDefaults(style, layoutDirection)
                    val intrinsics =
                        ParagraphIntrinsics(
                            text = text,
                            style = resolvedStyle,
                            density = density,
                            fontFamilyResolver = fontFamilyResolver,
                            annotations = emptyList()
                        )
                    intrinsics.maxIntrinsicWidth
                }
            }
        } catch (_: RejectedExecutionException) {}
    }
}

@Composable
@NonRestartableComposable
internal actual fun BackgroundTextMeasurement(
    text: AnnotatedString,
    style: TextStyle,
    fontFamilyResolver: FontFamily.Resolver,
    placeholders: List<AnnotatedString.Range<Placeholder>>?
) {
    val executor = LocalBackgroundTextMeasurementExecutor.current
    if (executor != null && shouldPrefetch(text.length)) {
        val layoutDirection = LocalLayoutDirection.current
        val density = LocalDensity.current

        try {
            executor.execute {
                trace("BackgroundTextMeasurement") {
                    val resolvedStyle = resolveDefaults(style, layoutDirection)
                    val intrinsics =
                        MultiParagraphIntrinsics(
                            annotatedString = text,
                            style = resolvedStyle,
                            density = density,
                            placeholders = placeholders ?: emptyList(),
                            fontFamilyResolver = fontFamilyResolver
                        )
                    intrinsics.maxIntrinsicWidth
                }
            }
        } catch (_: RejectedExecutionException) {}
    }
}

/**
 * The minimum number of CPU cores that should exist for us to consider attempting text prefetch.
 */
private const val PrefetchTextMinimumCoreCount = 4

/**
 * Defines the shortest text length that can be considered for prefetching. Texts that are shorter
 * than this number are usually not worth creating a threading overhead.
 */
private const val PrefetchTextLengthThreshold = 8

/** Reading the core count is expensive. Do it once and cache it globally. */
private var backingCoreCountSatisfactory: Boolean? = null

@VisibleForTesting
internal val coreCountSatisfactory: Boolean
    get() {
        if (backingCoreCountSatisfactory == null) {
            backingCoreCountSatisfactory =
                Runtime.getRuntime().availableProcessors() >= PrefetchTextMinimumCoreCount
        }
        return backingCoreCountSatisfactory!!
    }

internal fun shouldPrefetch(textLength: Int): Boolean {
    return textLength >= PrefetchTextLengthThreshold && coreCountSatisfactory
}
