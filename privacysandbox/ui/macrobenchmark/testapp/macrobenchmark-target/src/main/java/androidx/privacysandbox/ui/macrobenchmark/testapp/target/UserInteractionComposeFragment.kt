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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.viewinterop.AndroidView
import androidx.privacysandbox.ui.client.SandboxedUiAdapterFactory
import androidx.privacysandbox.ui.client.compose.SandboxedSdkUi
import androidx.privacysandbox.ui.core.ExperimentalFeatures
import androidx.privacysandbox.ui.core.SandboxedUiAdapter
import androidx.privacysandbox.ui.macrobenchmark.testapp.sdkproviderutils.AnimationUtils
import androidx.privacysandbox.ui.macrobenchmark.testapp.sdkproviderutils.SdkApiConstants
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

/** Compose fragment for user-interaction benchmarking. */
class UserInteractionComposeFragment : BaseFragment() {

    private var bannerAdapter: SandboxedUiAdapter? by mutableStateOf(null)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        setAdapter()
        return ComposeView(requireContext()).apply { setContent { MainContent() } }
    }

    private fun setAdapter() {
        MainScope().launch {
            bannerAdapter =
                SandboxedUiAdapterFactory.createFromCoreLibInfo(
                    getSdkApi()
                        .loadAd(
                            adFormat = SdkApiConstants.Companion.AdFormat.BANNER_AD,
                            adType = SdkApiConstants.Companion.AdType.SCROLLABLE_AD_WITH_ANIMATION,
                            currentMediationOption,
                            waitInsideOnDraw = false,
                            drawViewability = shouldDrawViewabilityLayer,
                        )
                )
        }
    }

    // OptIn for the experimental API SandboxedSdkUi(SandboxedUiAdapter, Modifier,
    // SandboxedSdkViewEventListener?, Boolean)
    @OptIn(ExperimentalFeatures.ChangingContentUiZOrderApi::class)
    @Composable
    private fun MainContent() {
        Column(modifier = Modifier.fillMaxSize()) {
            AndroidView(
                factory = { context ->
                    LinearLayout(context).apply {
                        AnimationUtils.startAnimations(animationContainer = this)
                    }
                },
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
            bannerAdapter?.let {
                Box(
                    modifier =
                        Modifier.fillMaxWidth()
                            .weight(1f)
                            .semantics { testTagsAsResourceId = true }
                            .testTag(
                                "${context?.resources?.getResourceName(R.id.ad_layout)}" // keep the
                                // resource-id consistent with layout for UserInteractionBenchmark
                            )
                ) {
                    SandboxedSdkUi(
                        sandboxedUiAdapter = it,
                        modifier = Modifier.fillMaxSize(),
                        providerUiOnTop = providerUiOnTop,
                    )
                }
            }
        }
    }
}
