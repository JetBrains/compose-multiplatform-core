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

package androidx.privacysandbox.ui.integration.testapp.endtoend

import androidx.privacysandbox.ui.integration.testapp.fragments.FragmentOptions
import androidx.privacysandbox.ui.integration.testapp.fragments.hidden.AbstractReusableListHiddenFragment
import androidx.test.filters.LargeTest
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@LargeTest
@RunWith(Parameterized::class)
// TODO(b/421852465): test the CUJ for Compose LazyList
class ReusableListTests(private val adFormat: String) :
    AutomatedEndToEndTest(
        FragmentOptions.FRAGMENT_REUSABLE_LIST_HIDDEN,
        FragmentOptions.UI_FRAMEWORK_VIEW,
        adFormat,
        FragmentOptions.MEDIATION_TYPE_NON_MEDIATED,
        FragmentOptions.Z_ORDER_BELOW,
    ) {
    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "adFormat={0}")
        fun parameters(): Collection<Array<String>> {
            return customParams(
                arrayOf(FragmentOptions.AD_FORMAT_BANNER_AD, FragmentOptions.AD_FORMAT_NATIVE_AD)
            )
        }
    }

    private lateinit var reusableListFragment: AbstractReusableListHiddenFragment

    @Before
    fun setup() {
        reusableListFragment = getFragment() as AbstractReusableListHiddenFragment
    }

    @Test
    fun reusableListViewScrolledAway_uiRemainsDisplayed() {
        val lastItemIndex = reusableListFragment.itemCount - 1
        scenario.onActivity { activity -> reusableListFragment.scrollToPosition(lastItemIndex) }

        assertThat(reusableListFragment.ensureUiIsDetached(CALLBACK_WAIT_MS)).isTrue()
        assertThat(reusableListFragment.ensureUiIsClosed(CALLBACK_WAIT_MS)).isFalse()
    }

    @Test
    fun reusableListRemovedFromLayout_uiCloses() {
        scenario.onActivity { activity -> reusableListFragment.removeReusableListFromLayout() }

        assertThat(reusableListFragment.ensureUiIsDetached(CALLBACK_WAIT_MS)).isTrue()
        assertThat(reusableListFragment.ensureUiIsClosed(CALLBACK_WAIT_MS)).isTrue()
    }
}
