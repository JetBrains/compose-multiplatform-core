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

package androidx.privacysandbox.ui.integration.testapp.fragments.hidden

import android.os.Bundle
import androidx.privacysandbox.ui.client.SandboxedUiAdapterFactory
import androidx.privacysandbox.ui.client.view.SandboxedSdkViewEventListener
import androidx.privacysandbox.ui.core.SandboxedUiAdapter
import androidx.privacysandbox.ui.integration.sdkproviderutils.SdkApiConstants
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

abstract class AbstractResizeHiddenFragment : BaseHiddenFragment() {
    val uiDisplayedLatch = CountDownLatch(1)
    val eventListener =
        object : SandboxedSdkViewEventListener {
            override fun onUiDisplayed() {
                uiDisplayedLatch.countDown()
            }

            override fun onUiError(error: Throwable) {}

            override fun onUiClosed() {}
        }

    abstract fun performResize(width: Int, height: Int)

    abstract fun applyPadding(
        paddingLeft: Int,
        paddingTop: Int,
        paddingRight: Int,
        paddingBottom: Int,
    )

    abstract fun loadAd(automatedTestCallbackBundle: Bundle)

    fun ensureUiIsDisplayed(callBackWaitMs: Long): Boolean {
        return uiDisplayedLatch.await(callBackWaitMs, TimeUnit.MILLISECONDS)
    }

    suspend fun buildAdapter(automatedTestCallbackBundle: Bundle): SandboxedUiAdapter {
        return SandboxedUiAdapterFactory.createFromCoreLibInfo(
            getSdkApi()
                .loadBannerAdForAutomatedTests(
                    SdkApiConstants.Companion.AdFormat.BANNER_AD,
                    currentAdType,
                    currentMediationOption,
                    false,
                    shouldDrawViewabilityLayer,
                    automatedTestCallbackBundle,
                )
        )
    }
}
