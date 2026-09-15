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

package androidx.compose.foundation.text

import androidx.annotation.VisibleForTesting
import androidx.compose.runtime.Composable
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalPlatformBackgroundTextMeasurementExecutor
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.MultiParagraphIntrinsics
import androidx.compose.ui.text.ParagraphIntrinsics
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.resolveDefaults
import androidx.compose.ui.util.trace
import kotlinx.coroutines.Runnable

@OptIn(InternalComposeUiApi::class)
@Composable
@NonRestartableComposable
internal actual fun BackgroundTextMeasurement(
    text: String,
    style: TextStyle,
    fontFamilyResolver: FontFamily.Resolver,
    softWrap: Boolean,
) {
    val executor = LocalPlatformBackgroundTextMeasurementExecutor.current
    if (executor != null && shouldPrefetch(text.length)) {
        val layoutDirection = LocalLayoutDirection.current
        val density = LocalDensity.current

        try {
            val task = Runnable {
                trace("BackgroundTextMeasurement") {
                    Snapshot.withMutableSnapshot {
                        val resolvedStyle = resolveDefaults(style, layoutDirection)
                        val intrinsics =
                            ParagraphIntrinsics(
                                text = text,
                                style = resolvedStyle,
                                density = density,
                                fontFamilyResolver = fontFamilyResolver,
                                annotations =
                                    emptyList<AnnotatedString.Range<AnnotatedString.Annotation>>(),
                                placeholders = emptyList(),
                                softWrap = softWrap,
                            )
                        // It is important that maxIntrinsicWidth is called before minIntrinsicWidth
                        // because the primary role of background text measurement is to warm the
                        // platform word layout cache.

                        // maxIntrinsicWidth premeasures all words in the given text. This warms
                        // the platform word layout cache so that when the UI thread starts
                        // measuring the Text composable, the text layout would be faster.
                        intrinsics.maxIntrinsicWidth
                        // minIntrinsicWidth creates a BreakIterator which in turn initializes and
                        // caches an instance of BreakIteratorCache in `android.icu.text`
                        intrinsics.minIntrinsicWidth
                    }
                }
            }
            executor.execute(task)
        } catch (_: Exception) {}
    }
}

@OptIn(InternalComposeUiApi::class)
@Composable
@NonRestartableComposable
internal actual fun BackgroundTextMeasurement(
    text: AnnotatedString,
    style: TextStyle,
    fontFamilyResolver: FontFamily.Resolver,
    placeholders: List<AnnotatedString.Range<Placeholder>>?,
    softWrap: Boolean,
) {
    val executor = LocalPlatformBackgroundTextMeasurementExecutor.current
    if (executor != null && shouldPrefetch(text.length)) {
        val layoutDirection = LocalLayoutDirection.current
        val density = LocalDensity.current

        try {
            val task = Runnable {
                trace("BackgroundTextMeasurement") {
                    Snapshot.withMutableSnapshot {
                        val resolvedStyle = resolveDefaults(style, layoutDirection)
                        val intrinsics =
                            MultiParagraphIntrinsics(
                                annotatedString = text,
                                style = resolvedStyle,
                                density = density,
                                placeholders = placeholders ?: emptyList(),
                                fontFamilyResolver = fontFamilyResolver,
                                softWrap = softWrap,
                            )
                        // It is important that maxIntrinsicWidth is called before minIntrinsicWidth
                        // because the primary role of background text measurement is to warm the
                        // platform word layout cache.

                        // maxIntrinsicWidth premeasures all words in the given text. This warms
                        // the platform word layout cache so that when the UI thread starts
                        // measuring the Text composable, the text layout would be faster.
                        intrinsics.maxIntrinsicWidth
                        // minIntrinsicWidth creates a BreakIterator which in turn initializes and
                        // caches an instance of BreakIteratorCache in `android.icu.text`
                        intrinsics.minIntrinsicWidth
                    }
                }
            }
            executor.execute(task)
        } catch (_: Exception) {}
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
private const val MinTextLengthThreshold = 8

/**
 * Defines the longest text length that can be considered for prefetching. Texts that are longer
 * than this number have a chance to flood the cache to cause overflow, essentially leading to
 * double measurement that causes performance regression.
 */
private const val MaxTextLengthThreshold = 1000

/** Reading the core count is expensive. Do it once and cache it globally. */
private var backingCoreCountSatisfactory: Boolean? = null

@VisibleForTesting
internal val coreCountSatisfactory: Boolean
    get() {
        if (backingCoreCountSatisfactory == null) {
            backingCoreCountSatisfactory =
                getDeviceAvailableCoreProcessors() >= PrefetchTextMinimumCoreCount
        }
        return backingCoreCountSatisfactory!!
    }

internal fun shouldPrefetch(textLength: Int): Boolean {
    return textLength >= MinTextLengthThreshold &&
        textLength < MaxTextLengthThreshold &&
        coreCountSatisfactory
}

internal expect fun getDeviceAvailableCoreProcessors(): Int