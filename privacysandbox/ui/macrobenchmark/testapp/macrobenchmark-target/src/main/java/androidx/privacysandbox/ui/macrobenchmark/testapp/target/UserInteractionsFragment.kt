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
package androidx.privacysandbox.ui.macrobenchmark.testapp.target

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.privacysandbox.ui.client.view.SandboxedSdkView
import androidx.privacysandbox.ui.macrobenchmark.testapp.sdkproviderutils.AnimationUtils
import androidx.privacysandbox.ui.macrobenchmark.testapp.sdkproviderutils.SdkApiConstants

/** Hidden fragment for user interactions benchmarking. */
class UserInteractionFragment : BaseHiddenFragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        val inflatedView =
            inflater.inflate(R.layout.hidden_fragment_user_interactions, container, false)
        val ssv = inflatedView.findViewById<SandboxedSdkView>(R.id.ad_layout)
        loadBannerAd(
            adType = SdkApiConstants.Companion.AdType.SCROLLABLE_AD_WITH_ANIMATION,
            currentMediationOption,
            sandboxedSdkView = ssv,
            shouldDrawViewabilityLayer,
            waitInsideOnDraw = true,
        )
        val animationContainer = inflatedView.findViewById<LinearLayout>(R.id.animation_container)
        AnimationUtils.startAnimations(animationContainer)
        return inflatedView
    }
}
