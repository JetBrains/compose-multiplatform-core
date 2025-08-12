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

package androidx.privacysandbox.ui.macrobenchmark.testapp.target.util

import androidx.privacysandbox.ui.macrobenchmark.testapp.sdkproviderutils.SdkApiConstants
import androidx.privacysandbox.ui.macrobenchmark.testapp.target.LazyListFragment
import androidx.privacysandbox.ui.macrobenchmark.testapp.target.OcclusionFragment
import androidx.privacysandbox.ui.macrobenchmark.testapp.target.PoolingContainerFragment
import androidx.privacysandbox.ui.macrobenchmark.testapp.target.ResizeComposeFragment
import androidx.privacysandbox.ui.macrobenchmark.testapp.target.ResizeFragment
import androidx.privacysandbox.ui.macrobenchmark.testapp.target.ScrollComposeFragment
import androidx.privacysandbox.ui.macrobenchmark.testapp.target.ScrollFragment
import androidx.privacysandbox.ui.macrobenchmark.testapp.target.UserInteractionComposeFragment
import androidx.privacysandbox.ui.macrobenchmark.testapp.target.UserInteractionFragment
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

/** Unit tests for [FragmentFactory]. */
@RunWith(AndroidJUnit4::class)
@SmallTest
class FragmentFactoryTest {
    @Test
    fun createFragment_uiFrameworkViewAndCujTypeScroll_createsScrollFragment() {
        val fragment =
            FragmentFactory.createFragment(
                uiFramework = SdkApiConstants.Companion.UiFrameworkOption.VIEW,
                cujType = SdkApiConstants.Companion.FragmentOption.SCROLL,
            )

        assertThat(fragment).isInstanceOf(ScrollFragment::class.java)
    }

    @Test
    fun createFragment_uiFrameworkViewAndCujTypeResize_createsResizeFragment() {
        val fragment =
            FragmentFactory.createFragment(
                uiFramework = SdkApiConstants.Companion.UiFrameworkOption.VIEW,
                cujType = SdkApiConstants.Companion.FragmentOption.RESIZE,
            )

        assertThat(fragment).isInstanceOf(ResizeFragment::class.java)
    }

    @Test
    fun createFragment_uiFrameworkViewAndCujTypePoolingContainer_createsPoolingContainerFragment() {
        val fragment =
            FragmentFactory.createFragment(
                uiFramework = SdkApiConstants.Companion.UiFrameworkOption.VIEW,
                cujType = SdkApiConstants.Companion.FragmentOption.POOLING_CONTAINER,
            )

        assertThat(fragment).isInstanceOf(PoolingContainerFragment::class.java)
    }

    @Test
    fun createFragment_uiFrameworkViewAndCujTypeOcclusionsHidden_createsOcclusionFragment() {
        val fragment =
            FragmentFactory.createFragment(
                uiFramework = SdkApiConstants.Companion.UiFrameworkOption.VIEW,
                cujType = SdkApiConstants.Companion.FragmentOption.OCCLUSIONS_HIDDEN,
            )

        assertThat(fragment).isInstanceOf(OcclusionFragment::class.java)
    }

    @Test
    fun createFragment_uiFrameworkViewAndCujTypeUserInteractions_createsUserInteractionFragment() {
        val fragment =
            FragmentFactory.createFragment(
                uiFramework = SdkApiConstants.Companion.UiFrameworkOption.VIEW,
                cujType = SdkApiConstants.Companion.FragmentOption.USER_INTERACTIONS,
            )

        assertThat(fragment).isInstanceOf(UserInteractionFragment::class.java)
    }

    @Test
    fun createFragment_uiFrameworkComposeAndCujTypeScroll_createsScrollComposeFragment() {
        val fragment =
            FragmentFactory.createFragment(
                uiFramework = SdkApiConstants.Companion.UiFrameworkOption.COMPOSE,
                cujType = SdkApiConstants.Companion.FragmentOption.SCROLL,
            )

        assertThat(fragment).isInstanceOf(ScrollComposeFragment::class.java)
    }

    @Test
    fun createFragment_uiFrameworkComposeAndCujTypeResize_createsResizeComposeFragment() {
        val fragment =
            FragmentFactory.createFragment(
                uiFramework = SdkApiConstants.Companion.UiFrameworkOption.COMPOSE,
                cujType = SdkApiConstants.Companion.FragmentOption.RESIZE,
            )

        assertThat(fragment).isInstanceOf(ResizeComposeFragment::class.java)
    }

    @Test
    fun createFragment_uiFrameworkComposeAndCujTypePoolingContainer_createsLazyListFragment() {
        val fragment =
            FragmentFactory.createFragment(
                uiFramework = SdkApiConstants.Companion.UiFrameworkOption.COMPOSE,
                cujType = SdkApiConstants.Companion.FragmentOption.POOLING_CONTAINER,
            )

        assertThat(fragment).isInstanceOf(LazyListFragment::class.java)
    }

    @Test
    fun createFragment_uiFrameworkComposeAndCujTypeUserInteractions_createsUserInteractionComposeFragment() {
        val fragment =
            FragmentFactory.createFragment(
                uiFramework = SdkApiConstants.Companion.UiFrameworkOption.COMPOSE,
                cujType = SdkApiConstants.Companion.FragmentOption.USER_INTERACTIONS,
            )

        assertThat(fragment).isInstanceOf(UserInteractionComposeFragment::class.java)
    }

    @Test
    fun createFragment_unknownUiFrameworkOption_createsResizeFragment() {
        val fragment =
            FragmentFactory.createFragment(
                uiFramework = -1,
                cujType = SdkApiConstants.Companion.FragmentOption.SCROLL,
            )

        assertThat(fragment).isInstanceOf(ResizeFragment::class.java)
    }
}
