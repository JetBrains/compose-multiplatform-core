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

package androidx.compose.foundation.text

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test

class TextAutoSizeDesktopTest {
    @Suppress("DEPRECATION") @get:Rule val rule = createComposeRule()

    @Test
    fun ellipsis_shrinksTextToFitSingleLine() {
        lateinit var result: TextLayoutResult
        rule.setContent {
            Box(Modifier.width(100.dp)) {
                BasicText(
                    text = "123".repeat(20),
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1,
                    autoSize = TextAutoSize.StepBased(minFontSize = 1.sp),
                    onTextLayout = { result = it },
                )
            }
        }
        rule.runOnIdle {
            assertThat(result.layoutInput.style.fontSize.value).isAtLeast(1f)
            assertThat(result.layoutInput.style.fontSize.value).isLessThan(112f)
            assertThat(result.lineCount).isEqualTo(1)
            assertThat(result.isLineEllipsized(0)).isFalse()
            assertThat(result.multiParagraph.didExceedMaxLines).isFalse()
            assertThat(result.getLineEnd(0, visibleEnd = true)).isEqualTo(60)
            assertThat(result.hasVisualOverflow).isFalse()
        }
    }

    @Test
    fun ellipsis_usesMinimumWhenTextDoesNotFit() {
        lateinit var result: TextLayoutResult
        rule.setContent {
            Box(Modifier.width(100.dp)) {
                BasicText(
                    text = "123".repeat(20),
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1,
                    autoSize = TextAutoSize.StepBased(minFontSize = 16.sp),
                    onTextLayout = { result = it },
                )
            }
        }
        rule.runOnIdle {
            assertThat(result.layoutInput.style.fontSize).isEqualTo(16.sp)
            assertThat(result.isLineEllipsized(0)).isTrue()
        }
    }
}
