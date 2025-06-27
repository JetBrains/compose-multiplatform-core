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

package androidx.privacysandbox.ui.integration.inappmediateesdk

import android.content.Context
import android.os.Bundle
import androidx.privacysandbox.ui.integration.sdkproviderutils.IAutomatedTestCallbackProxy
import androidx.privacysandbox.ui.integration.sdkproviderutils.IMediationTestCallbackProxy
import androidx.privacysandbox.ui.integration.sdkproviderutils.MediateeSdkApiImpl
import androidx.privacysandbox.ui.integration.sdkproviderutils.SdkApiConstants.Companion.AdFormat
import androidx.privacysandbox.ui.integration.sdkproviderutils.SdkApiConstants.Companion.AdType
import androidx.privacysandbox.ui.integration.sdkproviderutils.SdkApiConstants.Companion.MEDIATION_TEST_CALLBACK

class InAppMediateeSdkApi(private val context: Context) {
    fun loadAd(
        @AdFormat adFormat: Int,
        @AdType adType: Int,
        withSlowDraw: Boolean,
        drawViewability: Boolean,
        mediationTestCallbackProxy: Bundle
    ): Bundle {
        return MediateeSdkApiImpl.loadAdUtil(
            adFormat,
            adType,
            withSlowDraw,
            drawViewability,
            context,
            AutomatedTestCallbackProxy(mediationTestCallbackProxy)
        )
    }

    private class AutomatedTestCallbackProxy(mediationTestCallbackBundle: Bundle) :
        IAutomatedTestCallbackProxy {
        val mediationCallbackBinder = mediationTestCallbackBundle.getBinder(MEDIATION_TEST_CALLBACK)
        val mediationTestCallback: IMediationTestCallbackProxy? =
            mediationCallbackBinder?.let { IMediationTestCallbackProxy.Stub.asInterface(it) }
                ?: throw IllegalStateException(
                    "Received Binder for callback is not of expected type"
                )

        override fun onResizeOccurred(width: Int, height: Int) {
            mediationTestCallback?.onResizeOccurred(width, height)
        }
    }
}
