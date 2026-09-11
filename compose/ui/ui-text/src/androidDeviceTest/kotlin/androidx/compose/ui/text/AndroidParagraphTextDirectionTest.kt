/*
 * Copyright 2022 The Android Open Source Project
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

package androidx.compose.ui.text

import androidx.compose.ui.text.android.InternalPlatformTextApi
import androidx.compose.ui.text.android.LayoutCompat
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.style.TextDirection
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SmallTest
@OptIn(InternalPlatformTextApi::class)
class AndroidParagraphTextDirectionTest {

<<<<<<< HEAD
    private lateinit var defaultLocale: Locale
    private val ltrLocaleList = LocaleList("en")
    private val rtlLocaleList = LocaleList("ar")
    private val ltrLocale = Locale.ENGLISH
    private val rtlLocale = Locale("ar")

    @Before
    fun before() {
        defaultLocale = Locale.getDefault()
    }

    @After
    fun after() {
        Locale.setDefault(defaultLocale)
    }

    @Test
    fun resolveTextDirectionHeuristics_unspecifiedTextDirection_nullLocaleList_defaultLtrLocale() {
        Locale.setDefault(ltrLocale)

        assertThat(
                resolveTextDirectionHeuristics(
                    textDirection = TextDirection.Unspecified,
                    localeList = null,
                )
            )
            .isEqualTo(LayoutCompat.TEXT_DIRECTION_FIRST_STRONG_LTR)
    }

    @Test
    fun resolveTextDirectionHeuristics_unspecifiedTextDirection_nullLocaleList_defaultRtlLocale() {
        Locale.setDefault(rtlLocale)

        assertThat(
                resolveTextDirectionHeuristics(
                    textDirection = TextDirection.Unspecified,
                    localeList = null,
                )
            )
            .isEqualTo(LayoutCompat.TEXT_DIRECTION_FIRST_STRONG_RTL)
    }
=======
    private val ltrLocale = Locale("en")
    private val rtlLocale = Locale("ar")
>>>>>>> a80c61f2261aa88096b3ac6d7ee2e4baf56aa3f1

    @Test
    fun resolveTextDirectionHeuristics_unspecifiedTextDirection_ltrLocaleList() {
        assertThat(
                resolveTextDirectionHeuristics(
                    textDirection = TextDirection.Unspecified,
                    locale = ltrLocale,
                )
            )
            .isEqualTo(LayoutCompat.TEXT_DIRECTION_FIRST_STRONG_LTR)
    }

    @Test
    fun resolveTextDirectionHeuristics_unspecifiedTextDirection_RtlLocaleList() {
        assertThat(
                resolveTextDirectionHeuristics(
                    textDirection = TextDirection.Unspecified,
                    locale = rtlLocale,
                )
            )
            .isEqualTo(LayoutCompat.TEXT_DIRECTION_FIRST_STRONG_RTL)
    }

    @Test
    fun resolveTextDirectionHeuristics_contentTextDirection_LtrLocaleList() {
        assertThat(
                resolveTextDirectionHeuristics(
                    textDirection = TextDirection.Content,
                    locale = ltrLocale,
                )
            )
            .isEqualTo(LayoutCompat.TEXT_DIRECTION_FIRST_STRONG_LTR)
    }

    @Test
    fun resolveTextDirectionHeuristics_contentTextDirection_RtlLocaleList() {
        assertThat(
                resolveTextDirectionHeuristics(
                    textDirection = TextDirection.Content,
                    locale = rtlLocale,
                )
            )
            .isEqualTo(LayoutCompat.TEXT_DIRECTION_FIRST_STRONG_RTL)
    }

    @Test
    fun resolveTextDirectionHeuristics_ltrTextDirection_RtlLocaleList() {
        assertThat(
                resolveTextDirectionHeuristics(
                    textDirection = TextDirection.Ltr,
                    locale = rtlLocale,
                )
            )
            .isEqualTo(LayoutCompat.TEXT_DIRECTION_LTR)
    }

    @Test
    fun resolveTextDirectionHeuristics_rtlTextDirection_LtrLocaleList() {
        assertThat(
                resolveTextDirectionHeuristics(
                    textDirection = TextDirection.Rtl,
                    locale = ltrLocale,
                )
            )
            .isEqualTo(LayoutCompat.TEXT_DIRECTION_RTL)
    }

    @Test
    fun resolveTextDirectionHeuristics_ContentOrLtr_RtlLocaleList() {
        assertThat(
                resolveTextDirectionHeuristics(
                    textDirection = TextDirection.ContentOrLtr,
                    locale = rtlLocale,
                )
            )
            .isEqualTo(LayoutCompat.TEXT_DIRECTION_FIRST_STRONG_LTR)
    }

    @Test
    fun resolveTextDirectionHeuristics_ContentOrRtl_LtrLocaleList() {
        assertThat(
                resolveTextDirectionHeuristics(
                    textDirection = TextDirection.ContentOrRtl,
                    locale = ltrLocale,
                )
            )
            .isEqualTo(LayoutCompat.TEXT_DIRECTION_FIRST_STRONG_RTL)
    }
}
