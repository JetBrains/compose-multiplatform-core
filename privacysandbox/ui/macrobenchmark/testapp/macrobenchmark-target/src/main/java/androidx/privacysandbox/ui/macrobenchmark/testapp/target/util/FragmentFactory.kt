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

import androidx.privacysandbox.ui.macrobenchmark.testapp.sdkproviderutils.SdkApiConstants.Companion.FragmentOption
import androidx.privacysandbox.ui.macrobenchmark.testapp.sdkproviderutils.SdkApiConstants.Companion.UiFrameworkOption
import androidx.privacysandbox.ui.macrobenchmark.testapp.target.BaseFragment
import androidx.privacysandbox.ui.macrobenchmark.testapp.target.LazyListFragment
import androidx.privacysandbox.ui.macrobenchmark.testapp.target.OcclusionFragment
import androidx.privacysandbox.ui.macrobenchmark.testapp.target.PoolingContainerFragment
import androidx.privacysandbox.ui.macrobenchmark.testapp.target.ResizeComposeFragment
import androidx.privacysandbox.ui.macrobenchmark.testapp.target.ResizeFragment
import androidx.privacysandbox.ui.macrobenchmark.testapp.target.ResizeHiddenFragment
import androidx.privacysandbox.ui.macrobenchmark.testapp.target.ScrollComposeFragment
import androidx.privacysandbox.ui.macrobenchmark.testapp.target.ScrollFragment
import androidx.privacysandbox.ui.macrobenchmark.testapp.target.UserInteractionComposeFragment
import androidx.privacysandbox.ui.macrobenchmark.testapp.target.UserInteractionFragment

/** Factory to create a specific fragment. */
object FragmentFactory {
    /** Creates the relevant fragment based on [uiFramework] and [cujType]. */
    fun createFragment(
        @UiFrameworkOption uiFramework: Int,
        @FragmentOption cujType: Int,
    ): BaseFragment {
        return when (uiFramework) {
            UiFrameworkOption.VIEW ->
                when (cujType) {
                    FragmentOption.SCROLL -> ScrollFragment()
                    FragmentOption.RESIZE -> ResizeFragment()
                    FragmentOption.POOLING_CONTAINER -> PoolingContainerFragment()
                    FragmentOption.RESIZE_HIDDEN -> ResizeHiddenFragment()
                    FragmentOption.OCCLUSIONS_HIDDEN -> OcclusionFragment()
                    FragmentOption.USER_INTERACTIONS -> UserInteractionFragment()
                    else -> ResizeFragment()
                }
            UiFrameworkOption.COMPOSE ->
                when (cujType) {
                    FragmentOption.SCROLL -> ScrollComposeFragment()
                    FragmentOption.RESIZE -> ResizeComposeFragment()
                    FragmentOption.POOLING_CONTAINER -> LazyListFragment()
                    FragmentOption.USER_INTERACTIONS -> UserInteractionComposeFragment()
                    else -> ResizeComposeFragment()
                }
            else -> ResizeFragment()
        }
    }
}
