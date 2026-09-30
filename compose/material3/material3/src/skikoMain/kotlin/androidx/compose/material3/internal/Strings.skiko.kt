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

package androidx.compose.material3.internal

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.text.intl.Locale
import androidx.compose.material3.l10n.en
import androidx.compose.material3.l10n.translationFor
import kotlin.jvm.JvmInline

@Immutable
@JvmInline
internal actual value class Strings(val value: Int) {
    actual companion object {
        actual inline val NavigationMenu: Strings
            get() = Strings(0)
        actual inline val CloseDrawer: Strings
            get() = Strings(1)
        actual inline val CloseRail: Strings
            get() = Strings(2)
        actual inline val CloseSheet: Strings
            get() = Strings(3)
        actual inline val DefaultErrorMessage: Strings
            get() = Strings(4)
        actual inline val ExposedDropdownMenu: Strings
            get() = Strings(5)
        actual inline val SliderRangeStart: Strings
            get() = Strings(6)
        actual inline val SliderRangeEnd: Strings
            get() = Strings(7)
        actual inline val Dialog: Strings
            get() = Strings(8)
        actual inline val MenuExpanded: Strings
            get() = Strings(9)
        actual inline val MenuCollapsed: Strings
            get() = Strings(10)
        actual inline val ToggleDropdownMenu: Strings
            get() = Strings(11)
        actual inline val SnackbarDismiss: Strings
            get() = Strings(12)
        actual inline val SnackbarPaneTitle: Strings
            get() = Strings(13)
        actual inline val SearchBarSearch: Strings
            get() = Strings(14)
        actual inline val SuggestionsAvailable: Strings
            get() = Strings(15)
        actual inline val DatePickerTitle: Strings
            get() = Strings(16)
        actual inline val DatePickerHeadline: Strings
            get() = Strings(17)
        actual inline val DatePickerYearPickerPaneTitle: Strings
            get() = Strings(18)
        actual inline val DatePickerSwitchToYearSelection: Strings
            get() = Strings(19)
        actual inline val DatePickerSwitchToDaySelection: Strings
            get() = Strings(20)
        actual inline val DatePickerSwitchToNextMonth: Strings
            get() = Strings(21)
        actual inline val DatePickerSwitchToPreviousMonth: Strings
            get() = Strings(22)
        actual inline val DatePickerNavigateToYearDescription: Strings
            get() = Strings(23)
        actual inline val DatePickerHeadlineDescription: Strings
            get() = Strings(24)
        actual inline val DatePickerNoSelectionDescription: Strings
            get() = Strings(25)
        actual inline val DatePickerTodayDescription: Strings
            get() = Strings(26)
        actual inline val DatePickerScrollToShowLaterYears: Strings
            get() = Strings(27)
        actual inline val DatePickerScrollToShowEarlierYears: Strings
            get() = Strings(28)
        actual inline val DateInputTitle: Strings
            get() = Strings(29)
        actual inline val DateInputHeadline: Strings
            get() = Strings(30)
        actual inline val DateInputLabel: Strings
            get() = Strings(31)
        actual inline val DateInputHeadlineDescription: Strings
            get() = Strings(32)
        actual inline val DateInputNoInputDescription: Strings
            get() = Strings(33)
        actual inline val DateInputInvalidNotAllowed: Strings
            get() = Strings(34)
        actual inline val DateInputInvalidForPattern: Strings
            get() = Strings(35)
        actual inline val DateInputInvalidYearRange: Strings
            get() = Strings(36)
        actual inline val DatePickerSwitchToCalendarMode: Strings
            get() = Strings(37)
        actual inline val DatePickerSwitchToInputMode: Strings
            get() = Strings(38)
        actual inline val DateRangePickerTitle: Strings
            get() = Strings(39)
        actual inline val DateRangePickerStartHeadline: Strings
            get() = Strings(40)
        actual inline val DateRangePickerEndHeadline: Strings
            get() = Strings(41)
        actual inline val DateRangePickerScrollToShowNextMonth: Strings
            get() = Strings(42)
        actual inline val DateRangePickerScrollToShowPreviousMonth: Strings
            get() = Strings(43)
        actual inline val DateRangePickerDayInRange: Strings
            get() = Strings(44)
        actual inline val DateRangeInputTitle: Strings
            get() = Strings(45)
        actual inline val DateRangeInputInvalidRangeInput: Strings
            get() = Strings(46)
        actual inline val FloatingToolbarCollapse: Strings
            get() = Strings(47)
        actual inline val FloatingToolbarExpand: Strings
            get() = Strings(48)
        actual inline val FloatingToolbarMoreOptions: Strings
            get() = Strings(49)
        actual inline val BottomSheetPaneTitle: Strings
            get() = Strings(50)
        actual inline val BottomSheetDragHandleDescription: Strings
            get() = Strings(51)
        actual inline val BottomSheetPartialExpandDescription: Strings
            get() = Strings(52)
        actual inline val BottomSheetDismissDescription: Strings
            get() = Strings(53)
        actual inline val BottomSheetExpandDescription: Strings
            get() = Strings(54)
        actual inline val TooltipLongPressLabel: Strings
            get() = Strings(55)
        actual inline val TimePickerAM: Strings
            get() = Strings(56)
        actual inline val TimePickerPM: Strings
            get() = Strings(57)
        actual inline val TimePickerPeriodToggle: Strings
            get() = Strings(58)
        actual inline val TimePickerHourSelection: Strings
            get() = Strings(59)
        actual inline val TimePickerMinuteSelection: Strings
            get() = Strings(60)
        actual inline val TimePickerHourSuffix: Strings
            get() = Strings(61)
        actual inline val TimePicker24HourSuffix: Strings
            get() = Strings(62)
        actual inline val TimePickerMinuteSuffix: Strings
            get() = Strings(63)
        actual inline val TimePickerHour: Strings
            get() = Strings(64)
        actual inline val TimePickerMinute: Strings
            get() = Strings(65)
        actual inline val TimePickerHourTextField: Strings
            get() = Strings(66)
        actual inline val TimePickerMinuteTextField: Strings
            get() = Strings(67)
        actual inline val TimePickerDialogTitle: Strings
            get() = Strings(68)
        actual inline val TimeScrollDialogTitle: Strings
            get() = Strings(69)
        actual inline val TimeInputDialogTitle: Strings
            get() = Strings(70)
        actual inline val TimePickerToggleKeyboard: Strings
            get() = Strings(71)
        actual inline val TimePickerToggleScroll: Strings
            get() = Strings(72)
        actual inline val TimePickerToggleTouch: Strings
            get() = Strings(73)
        actual inline val TimePickerMinuteError: Strings
            get() = Strings(74)
        actual inline val TimePickerHourError: Strings
            get() = Strings(75)
        actual inline val TimePicker24HourError: Strings
            get() = Strings(76)
        actual inline val TooltipPaneDescription: Strings
            get() = Strings(77)
        actual inline val WideNavigationRailPaneTitle: Strings
            get() = Strings(78)
        actual inline val ButtonGroupMoreOptions: Strings
            get() = Strings(79)
        // When adding values here, make sure to also add them in material3/build-fork.gradle,
        // updateTranslations task (stringByResourceName parameter), and re-run the task
    }
}

// TODO check if we should replace it by a more performant implementation
//  (without creating intermediate strings)
// TODO current implementation doesn't support sophisticated formatting like %.2f,
//  but currently we use it only for integers and strings
internal actual fun formatString(string: String, vararg formatArgs: Any?): String {
    var result = string
    formatArgs.forEachIndexed { index, arg ->
        result = result
            .replace("%${index+1}\$d", arg.toString())
            .replace("%${index+1}\$s", arg.toString())
    }
    return result
}

private fun getTranslation(string: Strings, locale: Locale): String {
    val tag = localeTag(language = locale.language, region = locale.region)
    val translation = translationByLocaleTag.getOrPut(tag) {
        findTranslation(locale)
    }
    return translation[string] ?: error("Missing translation for $string")
}

@Composable
@ReadOnlyComposable
internal actual fun getString(string: Strings): String {
    val locale = Locale.current
    return getTranslation(string, locale)
}

@Composable
@ReadOnlyComposable
internal actual fun getString(string: Strings, vararg formatArgs: Any): String {
    val locale = Locale.current
    return formatString(getTranslation(string, locale), *formatArgs)
}

/**
 * A single translation; should contain all the [Strings].
 */
internal typealias Translation = Map<Strings, String>

/**
 * Translations we've already loaded, mapped by the locale tag (see [localeTag]).
 */
private val translationByLocaleTag = mutableMapOf<String, Translation>()

/**
 * Returns the tag for the given locale.
 *
 * Note that this is our internal format; this isn't the same as [Locale.toLanguageTag].
 */
private fun localeTag(language: String, region: String) = when {
    language == "" -> ""
    region == "" -> language
    else -> "${language}_$region"
}

/**
 * Returns a sequence of locale tags to use as keys to look up the translation for the given locale.
 *
 * Note that we don't need to check children (e.g. use `fr_FR` if `fr` is missing) because the
 * translations should never have a missing parent.
 */
private fun localeTagChain(locale: Locale) = sequence {
    if (locale.region != "") {
        yield(localeTag(language = locale.language, region = locale.region))
    }
    if (locale.language != "") {
        yield(localeTag(language = locale.language, region = ""))
    }
    yield(localeTag("", ""))
}

/**
 * Finds a [Translation] for the given locale.
 */
private fun findTranslation(locale: Locale): Map<Strings, String> {
    // We don't need to merge translations because each one should contain all the strings.
    return localeTagChain(locale).firstNotNullOf { translationFor(it) }
}

/**
 * This object is only needed to provide a namespace for the [Translation] provider functions
 * (e.g. [Translations.en]), to avoid polluting the global namespace.
 */
internal object Translations