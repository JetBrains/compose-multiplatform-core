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

package androidx.compose.ui.text.intl

import androidx.compose.runtime.Immutable
import platform.posix.getenv
import kotlinx.cinterop.toKString
import kotlinx.cinterop.ExperimentalForeignApi

@Immutable
actual class Locale actual constructor(languageTag: String) {
    private val languageTag: String = languageTag.replace('_', '-')
    private val parts = this.languageTag.split('-')

    actual val language: String = parts.getOrNull(0)?.lowercase().orEmpty()

    actual val script: String = parts.drop(1).firstOrNull { it.length == 4 && it.all { c -> c.isLetter() } }?.lowercase()?.replaceFirstChar { it.uppercase() }.orEmpty()

    actual val region: String = parts.drop(1).firstOrNull { 
        (it.length == 2 && it.all { c -> c.isLetter() }) || (it.length == 3 && it.all { c -> c.isDigit() }) 
    }?.uppercase().orEmpty()

    actual fun toLanguageTag(): String = buildString {
        append(language)
        if (script.isNotEmpty()) {
            append("-")
            append(script)
        }
        if (region.isNotEmpty()) {
            append("-")
            append(region)
        }
    }

    actual override operator fun equals(other: Any?): Boolean {
        if (other == null) return false
        if (other !is Locale) return false
        if (this === other) return true
        return toLanguageTag() == other.toLanguageTag()
    }

    actual override fun hashCode(): Int = toLanguageTag().hashCode()

    actual override fun toString(): String = toLanguageTag()

    actual companion object {
        actual val current: Locale
            get() = platformLocaleDelegate.current[0]
    }
}

@OptIn(ExperimentalForeignApi::class)
internal actual fun createPlatformLocaleDelegate(): PlatformLocaleDelegate =
    object : PlatformLocaleDelegate {
        override val current: LocaleList
            get() {
                val lang = getenv("LANG")?.toKString()?.substringBefore(".")?.replace('_', '-') ?: "en-US"
                return LocaleList(listOf(Locale(lang)))
            }
    }

internal actual fun Locale.isRtl(): Boolean {
    val rtlLanguages = setOf("ar", "he", "iw", "fa", "ur", "yi", "ji", "syr", "dv", "ku")
    return rtlLanguages.contains(language)
}
