/*
 * Copyright 2023 The Android Open Source Project
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
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.util.fastMapNotNull
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsArray
import kotlin.js.JsName
import kotlin.js.JsString
import kotlin.js.get
import kotlin.js.js
import kotlin.js.length

@Immutable
actual class Locale internal constructor(internal val platformLocale: IntlLocale) {
    private val languageTag = platformLocale._baseName

    actual val language: String
        get() = platformLocale._language
    actual val script: String
        get() = platformLocale._script.orEmpty()
    actual val region: String
        get() = platformLocale._region.orEmpty()

    actual fun toLanguageTag(): String = languageTag

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

    actual constructor(languageTag: String): this(languageTag.toIntlLocale())
}

// Used when the browser reports no usable preferred language, so that Locale.current always
// returns at least one locale as required by PlatformLocaleDelegate's contract.
// The locale code en-001 denotes the English language spoken in the World.
private const val FALLBACK_LANGUAGE_TAG = "en-001"

internal actual fun createPlatformLocaleDelegate(): PlatformLocaleDelegate =
    object : PlatformLocaleDelegate {
        override val current: LocaleList
            get() = localeListFromLanguageTags(userPreferredLanguages())
    }

internal fun localeListFromLanguageTags(tags: List<String>): LocaleList {
    val locales = tags.fastMapNotNull { it.toIntlLocaleOrNull()?.let(::Locale) }
    return LocaleList(
        locales.ifEmpty { listOf(Locale(FALLBACK_LANGUAGE_TAG.toIntlLocale())) }
    )
}

// The list of RTL languages is taken from https://github.com/openjdk/jdk/blob/master/src/java.desktop/share/classes/java/awt/ComponentOrientation.java#L156
private val rtlLanguagesSet = setOf("ar", "fa", "he", "iw", "ji", "ur", "yi")

// Implemented according to ComponentOrientation.getOrientation (AWT),
// since there is no js API for this.
@InternalComposeUiApi
actual fun Locale.isRtl(): Boolean = this.language in rtlLanguagesSet

// K/JS and K/Wasm stdlib doesn't have this type. Therefore, we declare it here.
// Ideally it would not be necessary, or at least we would make it internal, but Compose common API
// requires that expect PlatformLocale is actualized by a "standard/native" type of Locale.
// https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Intl/Locale
// Note: Since Compose common code introduced PlatformLocale with extension properties with the same names,
// we had to change the names of the properties in kotlin to avoid the name shadowing.
internal external class IntlLocale {
    @JsName("language")
    internal val _language: String
    @JsName("script")
    internal val _script: String?
    @JsName("region")
    internal val _region: String?
    @JsName("baseName")
    internal val _baseName: String
}

internal fun parseLanguageTagToIntlLocale(languageTag: String): IntlLocale =
    js("new Intl.Locale(languageTag)")

private fun String.toIntlLocale(): IntlLocale = parseLanguageTagToIntlLocale(this)

// `new Intl.Locale(tag)` throws a RangeError for tags the browser considers invalid, so we
// guard against those cases when the tag comes from an untrusted source like navigator.languages.
private fun String.toIntlLocaleOrNull(): IntlLocale? = try {
    parseLanguageTagToIntlLocale(this)
} catch (_: Throwable) {
    null
}

@OptIn(ExperimentalWasmJsInterop::class)
private fun userPreferredLanguages(): List<String> {
    val jsStringArray = getUserPreferredLanguagesAsArray()
    return buildList {
        repeat(jsStringArray.length) {
            add(jsStringArray[it].toString())
        }
    }
}

@OptIn(ExperimentalWasmJsInterop::class)
private fun getUserPreferredLanguagesAsArray(): JsArray<JsString> =
    js("window.navigator.languages")