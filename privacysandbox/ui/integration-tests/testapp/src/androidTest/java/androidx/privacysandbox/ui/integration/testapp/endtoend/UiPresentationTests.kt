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

package androidx.privacysandbox.ui.integration.testapp

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.widget.LinearLayoutCompat
import androidx.privacysandbox.sdkruntime.client.SdkSandboxManagerCompat
import androidx.privacysandbox.ui.client.SandboxedUiAdapterFactory
import androidx.privacysandbox.ui.client.view.SandboxedSdkView
import androidx.privacysandbox.ui.client.view.SandboxedSdkViewEventListener
import androidx.privacysandbox.ui.integration.sdkproviderutils.ILoadSdkCallback
import androidx.privacysandbox.ui.integration.sdkproviderutils.SdkApiConstants.Companion.FragmentOption
import androidx.privacysandbox.ui.integration.testsdkprovider.IAutomatedTestCallback
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.filters.MediumTest
import com.google.common.truth.Truth.assertThat
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.floor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@MediumTest
@RunWith(Parameterized::class)
class UiPresentationTests(
    @FragmentOption val mediationOption: String,
    @FragmentOption val zOrdering: String
) {
    private lateinit var scenario: ActivityScenario<MainActivity>
    private lateinit var sdkToClientCallback: SdkToClientCallback

    companion object {
        private const val CALLBACK_WAIT_MS = 2000L

        private val mediationOptions =
            arrayOf(
                FragmentOptions.MEDIATION_TYPE_NON_MEDIATED,
                FragmentOptions.MEDIATION_TYPE_IN_RUNTIME,
                FragmentOptions.MEDIATION_TYPE_IN_APP
            )
        private val zOrderings =
            arrayOf(FragmentOptions.Z_ORDER_BELOW, FragmentOptions.Z_ORDER_ABOVE)

        @JvmStatic
        @Parameterized.Parameters(name = "mediationOption={0}, zOrdering={1}")
        fun data(): Collection<Array<String>> {
            val testData = mutableListOf<Array<String>>()
            for (mediation in mediationOptions) {
                for (zOrder in zOrderings) {
                    testData.add(arrayOf(mediation, zOrder))
                }
            }
            return testData
        }
    }

    @Before
    fun setup() {
        launchTestAppAndWaitForLoadingSdks(
            FragmentOptions.FRAGMENT_RESIZE_HIDDEN,
            mediationOption,
            zOrdering
        )
        sdkToClientCallback = SdkToClientCallback()
    }

    @After
    fun tearDown() {
        val sdkSandboxManager =
            SdkSandboxManagerCompat.from(ApplicationProvider.getApplicationContext())
        sdkSandboxManager.unloadSdk(MainActivity.SDK_NAME)
        sdkSandboxManager.unloadSdk(MainActivity.MEDIATEE_SDK_NAME)
        scenario.close()
    }

    @Test
    fun resizeTest() {
        loadBannerAdAndWaitForUiDisplayed(R.id.hidden_resizable_ad_view)
        val resizedWidth = 100
        val resizedHeight = 200
        scenario.onActivity { activity ->
            val resizableBannerView =
                activity.findViewById<SandboxedSdkView>(R.id.hidden_resizable_ad_view)
            resizableBannerView.layoutParams =
                LinearLayoutCompat.LayoutParams(resizedWidth, resizedHeight)
        }

        assertThat(sdkToClientCallback.resizeLatch.await(CALLBACK_WAIT_MS, TimeUnit.MILLISECONDS))
            .isTrue()
        assertThat(sdkToClientCallback.remoteViewWidth).isEqualTo(resizedWidth)
        assertThat(sdkToClientCallback.remoteViewHeight).isEqualTo(resizedHeight)
    }

    @Test
    fun paddingAppliedTest() {
        loadBannerAdAndWaitForUiDisplayed(R.id.hidden_resizable_ad_view)

        var resizableViewWidth: Int? = null
        var resizableViewHeight: Int? = null
        var paddingLeft: Int? = null
        var paddingTop: Int? = null
        var paddingRight: Int? = null
        var paddingBottom: Int? = null
        scenario.onActivity { activity ->
            val resizableBannerView =
                activity.findViewById<SandboxedSdkView>(R.id.hidden_resizable_ad_view)
            resizableViewWidth = resizableBannerView.width
            resizableViewHeight = resizableBannerView.height
            paddingLeft = floor(resizableBannerView.width * 0.05).toInt()
            paddingTop = floor(resizableBannerView.height * 0.05).toInt()
            paddingRight = paddingLeft
            paddingBottom = paddingTop
            resizableBannerView.setPadding(
                paddingLeft!!,
                paddingTop!!,
                paddingRight!!,
                paddingBottom!!
            )
        }

        assertThat(sdkToClientCallback.resizeLatch.await(CALLBACK_WAIT_MS, TimeUnit.MILLISECONDS))
            .isTrue()

        var contentViewWidth: Int? = null
        var contentViewHeight: Int? = null
        scenario.onActivity { activity ->
            val resizableBannerView =
                activity.findViewById<SandboxedSdkView>(R.id.hidden_resizable_ad_view)
            contentViewWidth = resizableBannerView.getChildAt(0).width
            contentViewHeight = resizableBannerView.getChildAt(0).height
        }

        assertThat(contentViewWidth)
            .isEqualTo(resizableViewWidth!! - paddingLeft!! - paddingRight!!)
        assertThat(contentViewHeight)
            .isEqualTo(resizableViewHeight!! - paddingTop!! - paddingBottom!!)
        assertThat(sdkToClientCallback.remoteViewWidth).isEqualTo(contentViewWidth)
        assertThat(sdkToClientCallback.remoteViewHeight).isEqualTo(contentViewHeight)
    }

    // TODO(b/402065627): Move to a common util file
    private fun launchTestAppAndWaitForLoadingSdks(
        @FragmentOption fragmentName: String,
        @FragmentOption mediationOption: String,
        @FragmentOption zOrdering: String
    ) {
        val loadSdkCallback =
            object : ILoadSdkCallback.Stub() {
                val latch = CountDownLatch(1)

                override fun onSdksLoaded() {
                    latch.countDown()
                }
            }
        val intent = Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java)
        val extras = Bundle()
        extras.putString(FragmentOptions.KEY_FRAGMENT, fragmentName)
        extras.putString(FragmentOptions.KEY_MEDIATION, mediationOption)
        extras.putBoolean(FragmentOptions.KEY_DRAW_VIEWABILITY, false)
        extras.putString(FragmentOptions.KEY_Z_ORDER, zOrdering)
        extras.putBinder(FragmentOptions.LOAD_SDK_COMPLETE, loadSdkCallback)
        intent.putExtras(extras)

        scenario = ActivityScenario.launch(intent)
        assertThat(loadSdkCallback.latch.await(CALLBACK_WAIT_MS, TimeUnit.MILLISECONDS)).isTrue()
    }

    // TODO(b/402065627): Move to a common util file
    private fun loadBannerAdAndWaitForUiDisplayed(viewId: Int) {
        val eventListener = EventListener()
        scenario.onActivity { activity ->
            val view = activity.findViewById<SandboxedSdkView>(viewId)
            view.setEventListener(eventListener)
            CoroutineScope(Dispatchers.Main).launch {
                val sdkBundle =
                    activity
                        .getSdkApi()
                        .loadBannerAdForAutomatedTests(
                            BaseFragment.currentAdFormat,
                            BaseFragment.currentAdType,
                            BaseFragment.currentMediationOption,
                            /*waitInsideOnDraw=*/ false,
                            BaseFragment.shouldDrawViewabilityLayer,
                            sdkToClientCallback
                        )
                view.setAdapter(SandboxedUiAdapterFactory.createFromCoreLibInfo(sdkBundle))
            }
        }
        assertThat(eventListener.uiDisplayedLatch.await(CALLBACK_WAIT_MS, TimeUnit.MILLISECONDS))
            .isTrue()
    }

    private class SdkToClientCallback : IAutomatedTestCallback {
        var remoteViewWidth: Int? = null
        var remoteViewHeight: Int? = null
        val resizeLatch = CountDownLatch(1)

        override fun onResizeOccurred(width: Int, height: Int) {
            remoteViewWidth = width
            remoteViewHeight = height

            resizeLatch.countDown()
        }
    }

    private class EventListener : SandboxedSdkViewEventListener {
        val uiDisplayedLatch = CountDownLatch(1)

        override fun onUiDisplayed() {
            uiDisplayedLatch.countDown()
        }

        override fun onUiError(error: Throwable) {}

        override fun onUiClosed() {}
    }
}
