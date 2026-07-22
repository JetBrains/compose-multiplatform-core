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

package androidx.compose.ui.text.platform

import androidx.annotation.VisibleForTesting
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.FontRasterizationSettings
import androidx.compose.ui.text.ParagraphIntrinsics
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlatformParagraph
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import kotlin.coroutines.CoroutineContext
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized

/**
 * Factory surface a text backend implements to provide ui-text's platform primitives.
 *
 * The implementation is registered at runtime, so text users compile against stable APIs without
 * depending on a specific backend.
 */
@InternalComposeUiApi
interface PlatformText {
    /** Lays out [text] with [style] into a backend [PlatformParagraph] bounded by [constraints]. */
    fun createParagraph(
        text: String,
        style: TextStyle,
        annotations: List<AnnotatedString.Range<out AnnotatedString.Annotation>>,
        placeholders: List<AnnotatedString.Range<Placeholder>>,
        maxLines: Int,
        overflow: TextOverflow,
        constraints: Constraints,
        density: Density,
        fontFamilyResolver: FontFamily.Resolver,
    ): PlatformParagraph

    /** Re-lays out a previously measured [paragraphIntrinsics] under new [constraints]. */
    fun createParagraph(
        paragraphIntrinsics: ParagraphIntrinsics,
        maxLines: Int,
        overflow: TextOverflow,
        constraints: Constraints,
    ): PlatformParagraph

    /** Measures [text] with [style] into reusable [ParagraphIntrinsics]. */
    fun createParagraphIntrinsics(
        text: String,
        style: TextStyle,
        annotations: List<AnnotatedString.Range<out AnnotatedString.Annotation>>,
        placeholders: List<AnnotatedString.Range<Placeholder>>,
        density: Density,
        fontFamilyResolver: FontFamily.Resolver,
    ): ParagraphIntrinsics

    /** Creates a [FontFamily.Resolver] for use outside of composition. */
    fun createFontFamilyResolver(): FontFamily.Resolver

    /** Creates a [FontFamily.Resolver] that loads async fonts on the given [coroutineContext]. */
    fun createFontFamilyResolver(coroutineContext: CoroutineContext): FontFamily.Resolver

    /** Returns the index of the grapheme-cluster break preceding [index] in [text]. */
    fun findPrecedingBreak(text: String, index: Int): Int

    /** Returns the index of the grapheme-cluster break following [index] in [text]. */
    fun findFollowingBreak(text: String, index: Int): Int

    /**
     * The platform-default [androidx.compose.ui.text.FontRasterizationSettings], backing
     * [androidx.compose.ui.text.FontRasterizationSettings.Companion.PlatformDefault]. The backend computes these from the
     * host platform so the platform-specific detection stays out of ui-text.
     */
    @OptIn(ExperimentalTextApi::class)
    val defaultFontRasterizationSettings: FontRasterizationSettings
}

@InternalComposeUiApi
object PlatformTextRegistry {
    private var implementation: PlatformText? = null
    private val lock = SynchronizedObject()

    fun register(implementation: PlatformText) {
        synchronized(lock) {
            val current = this.implementation
            when {
                current == null -> this.implementation = implementation
                current === implementation -> Unit
                else -> error(
                    "Compose UI text implementation is already registered with a different " +
                        "instance. Call clear() first if replacement is intentional."
                )
            }
        }
    }

    internal fun requireCurrent(): PlatformText =
        implementation ?: error("No Compose UI text implementation is registered.")

    /**
     * Clears the current implementation.
     *
     * Intended for tests or controlled teardown only.
     */
    @VisibleForTesting
    fun clear() {
        implementation = null
    }
}
