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

package androidx.wear.compose.material3

import android.content.res.Resources
import android.os.Build
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import androidx.wear.compose.material3.internal.Plurals
import androidx.wear.compose.material3.internal.Strings
import androidx.wear.compose.material3.samples.TimePickerSample
import androidx.wear.compose.material3.samples.TimePickerWith12HourClockSample
import androidx.wear.compose.material3.samples.TimePickerWithMinutesAndSecondsSample
import androidx.wear.compose.material3.samples.TimePickerWithSecondsSample
import com.google.common.truth.Truth.assertThat
import java.time.LocalTime
import java.time.temporal.ChronoField
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@SdkSuppress(minSdkVersion = Build.VERSION_CODES.O)
@MediumTest
@RunWith(AndroidJUnit4::class)
class TimePickerTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun timePicker_supports_testtag() {
        rule.setContentWithTheme {
            TimePicker(
                onTimePicked = {},
                modifier = Modifier.testTag(TEST_TAG),
                initialTime = LocalTime.now(),
            )
        }

        rule.onNodeWithTag(TEST_TAG).assertExists()
    }

    @Test
    fun timePicker_samples_build() {
        rule.setContentWithTheme {
            TimePickerSample()
            TimePickerWithSecondsSample()
            TimePickerWith12HourClockSample()
            TimePickerWithMinutesAndSecondsSample()
        }
    }

    @Test
    fun timePicker_hhmmss_initial_state() {
        val initialTime = LocalTime.of(/* hour= */ 14, /* minute= */ 23, /* second= */ 31)
        rule.setContentWithTheme {
            TimePicker(
                onTimePicked = {},
                initialTime = initialTime,
                timePickerType = TimePickerType.HoursMinutesSeconds24H,
            )
        }

        rule
            .onNodeWithTimeValue(
                selectedValue = initialTime.hour,
                selectionMode = SelectionMode.Hour,
            )
            .assertIsDisplayed()
            .assertIsFocused()
        rule
            .onNodeWithTimeValue(
                selectedValue = initialTime.minute,
                selectionMode = SelectionMode.Minute,
            )
            .assertIsDisplayed()
        rule
            .onNodeWithTimeValue(
                selectedValue = initialTime.second,
                selectionMode = SelectionMode.Second,
            )
            .assertIsDisplayed()
        rule.onNodeWithText("AM", useUnmergedTree = true).assertDoesNotExist()
        rule.onNodeWithText("PM", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun timePicker_hhmm_initial_state() {
        val initialTime = LocalTime.of(/* hour= */ 14, /* minute= */ 23)
        rule.setContentWithTheme {
            TimePicker(
                onTimePicked = {},
                initialTime = initialTime,
                timePickerType = TimePickerType.HoursMinutes24H,
            )
        }

        rule
            .onNodeWithTimeValue(
                selectedValue = initialTime.hour,
                selectionMode = SelectionMode.Hour,
            )
            .assertIsDisplayed()
            .assertIsFocused()
        rule
            .onNodeWithTimeValue(
                selectedValue = initialTime.minute,
                selectionMode = SelectionMode.Minute,
            )
            .assertIsDisplayed()
        rule
            .onNodeWithTimeValue(
                selectedValue = initialTime.second,
                selectionMode = SelectionMode.Second,
            )
            .assertDoesNotExist()
        rule.onNodeWithText("AM", useUnmergedTree = true).assertDoesNotExist()
        rule.onNodeWithText("PM", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun timePicker_mmss_initial_state() {
        val initialTime = LocalTime.of(/* hour= */ 14, /* minute= */ 23, /* second= */ 45)
        rule.setContentWithTheme {
            TimePicker(
                onTimePicked = {},
                initialTime = initialTime,
                timePickerType = TimePickerType.MinutesSeconds,
            )
        }

        rule
            .onNodeWithTimeValue(
                selectedValue = initialTime.minute,
                selectionMode = SelectionMode.Minute,
            )
            .assertIsDisplayed()
            .assertIsFocused()
        rule
            .onNodeWithTimeValue(
                selectedValue = initialTime.second,
                selectionMode = SelectionMode.Second,
            )
            .assertIsDisplayed()
        rule
            .onNodeWithTimeValue(
                selectedValue = initialTime.hour,
                selectionMode = SelectionMode.Hour,
            )
            .assertDoesNotExist()
        rule.onNodeWithText("AM", useUnmergedTree = true).assertDoesNotExist()
        rule.onNodeWithText("PM", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun timePicker_hhmm12h_initial_state() {
        val initialTime = LocalTime.of(/* hour= */ 14, /* minute= */ 23)
        rule.setContentWithTheme {
            TimePicker(
                onTimePicked = {},
                modifier = Modifier.testTag(TEST_TAG),
                initialTime = initialTime,
                timePickerType = TimePickerType.HoursMinutesAmPm12H,
            )
        }

        rule
            .onNodeWithTimeValue(
                selectedValue = initialTime.get(ChronoField.CLOCK_HOUR_OF_AMPM),
                selectionMode = SelectionMode.Hour,
            )
            .assertIsDisplayed()
            .assertIsFocused()
        rule
            .onNodeWithTimeValue(
                selectedValue = initialTime.minute,
                selectionMode = SelectionMode.Minute,
            )
            .assertIsDisplayed()
        rule
            .onNodeWithTimeValue(
                selectedValue = initialTime.second,
                selectionMode = SelectionMode.Second,
            )
            .assertDoesNotExist()
        rule.onNodeWithText("AM", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithText("PM", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun timePicker_switch_to_minutes() {
        val initialTime = LocalTime.of(/* hour= */ 14, /* minute= */ 23, /* second= */ 31)
        rule.setContentWithTheme { TimePicker(onTimePicked = {}, initialTime = initialTime) }

        rule
            .onNodeWithTimeValue(
                selectedValue = initialTime.minute,
                selectionMode = SelectionMode.Minute,
            )
            .performClick()

        rule
            .onNodeWithTimeValue(
                selectedValue = initialTime.minute,
                selectionMode = SelectionMode.Minute,
            )
            .assertIsFocused()
    }

    @Test
    fun timePicker_select_hour() {
        val initialTime = LocalTime.of(/* hour= */ 14, /* minute= */ 23, /* second= */ 31)
        val expectedHour = 9
        rule.setContentWithTheme {
            TimePicker(
                onTimePicked = {},
                initialTime = initialTime,
                timePickerType = TimePickerType.HoursMinutesSeconds24H,
            )
        }

        rule
            .onNodeWithTimeValue(
                selectedValue = initialTime.hour,
                selectionMode = SelectionMode.Hour,
            )
            .performScrollToIndex(expectedHour)
        rule.waitForIdle()

        rule
            .onNodeWithTimeValue(selectedValue = expectedHour, selectionMode = SelectionMode.Hour)
            .assertIsDisplayed()
    }

    @Test
    fun timePicker_hhmmss_confirmed() {
        lateinit var confirmedTime: LocalTime
        val initialTime = LocalTime.of(/* hour= */ 14, /* minute= */ 23, /* second= */ 31)
        val expectedTime = LocalTime.of(/* hour= */ 9, /* minute= */ 11, /* second= */ 59)
        rule.setContentWithTheme {
            TimePicker(
                onTimePicked = { confirmedTime = it },
                initialTime = initialTime,
                timePickerType = TimePickerType.HoursMinutesSeconds24H,
            )
        }

        rule
            .onNodeWithTimeValue(
                selectedValue = initialTime.hour,
                selectionMode = SelectionMode.Hour,
            )
            .performScrollToIndex(expectedTime.hour)
        rule
            .onNodeWithTimeValue(
                selectedValue = initialTime.minute,
                selectionMode = SelectionMode.Minute,
            )
            .performScrollToIndex(expectedTime.minute)
        rule
            .onNodeWithTimeValue(
                selectedValue = initialTime.second,
                selectionMode = SelectionMode.Second,
            )
            .performScrollToIndex(expectedTime.second)
        rule.confirmButton().performClick()
        rule.waitForIdle()

        assertThat(confirmedTime).isEqualTo(expectedTime)
    }

    @Test
    fun timePicker_hhmm_confirmed() {
        lateinit var confirmedTime: LocalTime
        val initialTime = LocalTime.of(/* hour= */ 14, /* minute= */ 23, /* second= */ 31)
        val expectedTime = LocalTime.of(/* hour= */ 9, /* minute= */ 11, /* second= */ 0)
        rule.setContentWithTheme {
            TimePicker(
                onTimePicked = { confirmedTime = it },
                initialTime = initialTime,
                timePickerType = TimePickerType.HoursMinutes24H,
            )
        }

        rule
            .onNodeWithTimeValue(
                selectedValue = initialTime.hour,
                selectionMode = SelectionMode.Hour,
            )
            .performScrollToIndex(expectedTime.hour)
        rule
            .onNodeWithTimeValue(
                selectedValue = initialTime.minute,
                selectionMode = SelectionMode.Minute,
            )
            .performScrollToIndex(expectedTime.minute)
        rule.confirmButton().performClick()
        rule.waitForIdle()

        assertThat(confirmedTime).isEqualTo(expectedTime)
    }

    @Test
    fun timePicker_12h_confirmed() {
        lateinit var confirmedTime: LocalTime
        val initialTime = LocalTime.of(/* hour= */ 14, /* minute= */ 23, /* second= */ 31)
        val expectedTime = LocalTime.of(/* hour= */ 9, /* minute= */ 11, /* second= */ 0)
        rule.setContentWithTheme {
            TimePicker(
                onTimePicked = { confirmedTime = it },
                initialTime = initialTime,
                timePickerType = TimePickerType.HoursMinutesAmPm12H,
            )
        }

        rule
            .onNodeWithTimeValue(
                selectedValue = initialTime.get(ChronoField.CLOCK_HOUR_OF_AMPM),
                selectionMode = SelectionMode.Hour,
            )
            .performScrollToIndex(expectedTime.get(ChronoField.CLOCK_HOUR_OF_AMPM) - 1)
        rule
            .onNodeWithTimeValue(
                selectedValue = initialTime.minute,
                selectionMode = SelectionMode.Minute,
            )
            .performScrollToIndex(expectedTime.minute)
        rule.onNodeWithContentDescription("PM").performScrollToIndex(0)
        rule.confirmButton().performClick()
        rule.waitForIdle()

        assertThat(confirmedTime).isEqualTo(expectedTime)
    }

    @Test
    fun timePicker_mmss_confirmed() {
        lateinit var confirmedTime: LocalTime
        val initialTime = LocalTime.of(/* hour= */ 10, /* minute= */ 23, /* second= */ 45)
        val expectedTime = LocalTime.of(/* hour= */ 0, /* minute= */ 11, /* second= */ 20)
        rule.setContentWithTheme {
            TimePicker(
                onTimePicked = { confirmedTime = it },
                initialTime = initialTime,
                timePickerType = TimePickerType.MinutesSeconds,
            )
        }

        rule
            .onNodeWithTimeValue(
                selectedValue = initialTime.minute,
                selectionMode = SelectionMode.Minute,
            )
            .performScrollToIndex(expectedTime.minute)
        rule
            .onNodeWithTimeValue(
                selectedValue = initialTime.second,
                selectionMode = SelectionMode.Second,
            )
            .performScrollToIndex(expectedTime.second)
        rule.confirmButton().performClick()
        rule.waitForIdle()

        assertThat(confirmedTime).isEqualTo(expectedTime)
    }

    @Test
    fun parsing_standard12hPattern_isCorrect() {
        val pattern = "h:mm a"

        val parsed = parsePattern(pattern)
        val grouped = groupTimeParts(parsed)

        assertThat(parsed)
            .containsExactly(
                TimePatternPart.ComponentPart(FocusableElement.Hour),
                TimePatternPart.SeparatorPart(":"),
                TimePatternPart.ComponentPart(FocusableElement.Minute),
                TimePatternPart.SeparatorPart(" "),
                TimePatternPart.ComponentPart(FocusableElement.Period),
            )
            .inOrder()
        assertThat(grouped)
            .containsExactly(
                TimeLayoutElement.TimeGroup(
                    listOf(
                        TimePatternPart.ComponentPart(FocusableElement.Hour),
                        TimePatternPart.SeparatorPart(":"),
                        TimePatternPart.ComponentPart(FocusableElement.Minute),
                    )
                ),
                TimeLayoutElement.Standalone(TimePatternPart.SeparatorPart(" ")),
                TimeLayoutElement.Standalone(TimePatternPart.ComponentPart(FocusableElement.Period)),
            )
            .inOrder()
    }

    @Test
    fun parsing_periodFirstPattern_isCorrect() {
        val pattern = "a h:mm"

        val parsed = parsePattern(pattern)
        val grouped = groupTimeParts(parsed)

        assertThat(parsed)
            .containsExactly(
                TimePatternPart.ComponentPart(FocusableElement.Period),
                TimePatternPart.SeparatorPart(" "),
                TimePatternPart.ComponentPart(FocusableElement.Hour),
                TimePatternPart.SeparatorPart(":"),
                TimePatternPart.ComponentPart(FocusableElement.Minute),
            )
            .inOrder()
        assertThat(grouped)
            .containsExactly(
                TimeLayoutElement.Standalone(
                    TimePatternPart.ComponentPart(FocusableElement.Period)
                ),
                TimeLayoutElement.Standalone(TimePatternPart.SeparatorPart(" ")),
                TimeLayoutElement.TimeGroup(
                    listOf(
                        TimePatternPart.ComponentPart(FocusableElement.Hour),
                        TimePatternPart.SeparatorPart(":"),
                        TimePatternPart.ComponentPart(FocusableElement.Minute),
                    )
                ),
            )
            .inOrder()
    }

    @Test
    fun parsing_24hWithSecondsPattern_isCorrect() {
        val pattern = "HH:mm:ss"

        val parsed = parsePattern(pattern)
        val grouped = groupTimeParts(parsed)

        assertThat(parsed)
            .containsExactly(
                TimePatternPart.ComponentPart(FocusableElement.Hour),
                TimePatternPart.SeparatorPart(":"),
                TimePatternPart.ComponentPart(FocusableElement.Minute),
                TimePatternPart.SeparatorPart(":"),
                TimePatternPart.ComponentPart(FocusableElement.Second),
            )
            .inOrder()
        assertThat(grouped)
            .containsExactly(
                TimeLayoutElement.TimeGroup(
                    listOf(
                        TimePatternPart.ComponentPart(FocusableElement.Hour),
                        TimePatternPart.SeparatorPart(":"),
                        TimePatternPart.ComponentPart(FocusableElement.Minute),
                        TimePatternPart.SeparatorPart(":"),
                        TimePatternPart.ComponentPart(FocusableElement.Second),
                    )
                )
            )
            .inOrder()
    }

    @Test
    fun parsing_patternWithNoSpaceSeparator_isCorrect() {
        val pattern = "aK:mm" // e.g. Japanese

        val parsed = parsePattern(pattern)

        // The heuristic should inject a space separator between 'a' and 'K'.
        assertThat(parsed)
            .containsExactly(
                TimePatternPart.ComponentPart(FocusableElement.Period),
                TimePatternPart.SeparatorPart(" "),
                TimePatternPart.ComponentPart(FocusableElement.Hour),
                TimePatternPart.SeparatorPart(":"),
                TimePatternPart.ComponentPart(FocusableElement.Minute),
            )
            .inOrder()
    }

    @Test
    fun parsing_patternWithDotSeparator_isCorrect() {
        val pattern = "H.mm" // e.g. Finnish

        val parsed = parsePattern(pattern)
        val grouped = groupTimeParts(parsed)

        assertThat(parsed)
            .containsExactly(
                TimePatternPart.ComponentPart(FocusableElement.Hour),
                TimePatternPart.SeparatorPart("."),
                TimePatternPart.ComponentPart(FocusableElement.Minute),
            )
            .inOrder()
        assertThat(grouped)
            .containsExactly(
                TimeLayoutElement.TimeGroup(
                    listOf(
                        TimePatternPart.ComponentPart(FocusableElement.Hour),
                        TimePatternPart.SeparatorPart("."),
                        TimePatternPart.ComponentPart(FocusableElement.Minute),
                    )
                )
            )
            .inOrder()
    }

    private fun SemanticsNodeInteractionsProvider.onNodeWithContentDescription(
        label: String
    ): SemanticsNodeInteraction = onAllNodesWithContentDescription(label).onFirst()

    private fun SemanticsNodeInteractionsProvider.confirmButton(): SemanticsNodeInteraction =
        onAllNodesWithContentDescription(
                InstrumentationRegistry.getInstrumentation()
                    .context
                    .resources
                    .getString(Strings.PickerConfirmButtonContentDescription.value)
            )
            .onFirst()

    private fun SemanticsNodeInteractionsProvider.onNodeWithTimeValue(
        selectedValue: Int,
        selectionMode: SelectionMode,
    ): SemanticsNodeInteraction =
        onAllNodesWithContentDescription(
                contentDescriptionForValue(
                    InstrumentationRegistry.getInstrumentation().context.resources,
                    selectedValue,
                    selectionMode.contentDescriptionResource,
                )
            )
            .onFirst()

    private fun contentDescriptionForValue(
        resources: Resources,
        selectedValue: Int,
        contentDescriptionResource: Plurals,
    ): String =
        resources.getQuantityString(contentDescriptionResource.value, selectedValue, selectedValue)

    private enum class SelectionMode(val contentDescriptionResource: Plurals) {
        Hour(Plurals.TimePickerHoursContentDescription),
        Minute(Plurals.TimePickerMinutesContentDescription),
        Second(Plurals.TimePickerSecondsContentDescription),
    }
}
