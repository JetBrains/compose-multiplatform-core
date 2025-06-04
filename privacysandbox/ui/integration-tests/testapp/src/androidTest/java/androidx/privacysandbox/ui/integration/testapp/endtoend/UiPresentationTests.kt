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

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Bundle
import androidx.privacysandbox.sdkruntime.client.SdkSandboxManagerCompat
import androidx.privacysandbox.ui.client.view.SandboxedSdkView
import androidx.privacysandbox.ui.integration.sdkproviderutils.IAutomatedTestCallback
import androidx.privacysandbox.ui.integration.sdkproviderutils.ILoadSdkCallback
import androidx.privacysandbox.ui.integration.sdkproviderutils.SdkApiConstants.Companion.AUTOMATED_TEST_CALLBACK
import androidx.privacysandbox.ui.integration.sdkproviderutils.SdkApiConstants.Companion.FragmentOption
import androidx.privacysandbox.ui.integration.sdkproviderutils.SdkApiConstants.Companion.MediationOption
import androidx.privacysandbox.ui.integration.sdkproviderutils.SdkApiConstants.Companion.UiFrameworkOption
import androidx.privacysandbox.ui.integration.sdkproviderutils.SdkApiConstants.Companion.ZOrderOption
import androidx.privacysandbox.ui.integration.testapp.MainActivity
import androidx.privacysandbox.ui.integration.testapp.fragments.FragmentOptions
import androidx.privacysandbox.ui.integration.testapp.fragments.hidden.AbstractResizeHiddenFragment
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.hasChildCount
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.filters.MediumTest
import com.google.common.truth.Truth.assertThat
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.floor
import org.hamcrest.Matchers.instanceOf
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@MediumTest
@RunWith(Parameterized::class)
class UiPresentationTests(
    @UiFrameworkOption private val uiFrameworkOption: String,
    @MediationOption private val mediationOption: String,
    @ZOrderOption private val zOrdering: String,
) {
    private lateinit var scenario: ActivityScenario<MainActivity>
    private lateinit var sdkToClientCallbackBundle: Bundle
    private lateinit var sdkToClientCallback: SdkToClientCallback
    private lateinit var resizeFragment: AbstractResizeHiddenFragment

    companion object {
        private const val CALLBACK_WAIT_MS = 2000L

        private val uiFrameworkOptions =
            arrayOf(FragmentOptions.UI_FRAMEWORK_VIEW, FragmentOptions.UI_FRAMEWORK_COMPOSE)
        private val mediationOptions =
            arrayOf(
                FragmentOptions.MEDIATION_TYPE_NON_MEDIATED,
                FragmentOptions.MEDIATION_TYPE_IN_RUNTIME,
                FragmentOptions.MEDIATION_TYPE_IN_APP,
            )
        private val zOrderings =
            arrayOf(FragmentOptions.Z_ORDER_BELOW, FragmentOptions.Z_ORDER_ABOVE)

        @JvmStatic
        @Parameterized.Parameters(
            name = "uiFrameworkOption={0}, mediationOption={1}, zOrdering={2}"
        )
        fun data(): Collection<Array<String>> {
            val testData = mutableListOf<Array<String>>()
            for (uiFrameworkOption in uiFrameworkOptions) {
                for (mediation in mediationOptions) {
                    for (zOrder in zOrderings) {
                        testData.add(arrayOf(uiFrameworkOption, mediation, zOrder))
                    }
                }
            }
            return testData
        }
    }

    @Before
    fun setup() {
        launchTestAppAndWaitForLoadingSdks(uiFrameworkOption, mediationOption, zOrdering)
        sdkToClientCallback = SdkToClientCallback()
        sdkToClientCallbackBundle = Bundle()
        sdkToClientCallbackBundle.putBinder(AUTOMATED_TEST_CALLBACK, sdkToClientCallback)
        scenario.onActivity { activity ->
            resizeFragment = activity.getCurrentFragment() as AbstractResizeHiddenFragment
            resizeFragment.loadAd(sdkToClientCallbackBundle)
        }
        assertThat(resizeFragment.ensureUiIsDisplayed(CALLBACK_WAIT_MS)).isTrue()
    }

    @After
    fun tearDown() {
        val sdkSandboxManager =
            SdkSandboxManagerCompat.from(ApplicationProvider.getApplicationContext())
        sdkSandboxManager.unloadSdk(MainActivity.Companion.SDK_NAME)
        sdkSandboxManager.unloadSdk(MainActivity.Companion.MEDIATEE_SDK_NAME)
        scenario.close()
    }

    @Test
    fun resizeTest() {
        val resizedWidth = 100
        val resizedHeight = 200
        scenario.onActivity { activity ->
            resizeFragment.performResize(resizedWidth, resizedHeight)
        }

        val sandboxedSdkView = getSandboxedSdkView()
        val contentView = sandboxedSdkView.getChildAt(0)
        assertThat(sdkToClientCallback.resizeLatch.await(CALLBACK_WAIT_MS, TimeUnit.MILLISECONDS))
            .isTrue()
        assertThat(sandboxedSdkView.width).isEqualTo(resizedWidth)
        assertThat(sandboxedSdkView.height).isEqualTo(resizedHeight)
        assertThat(contentView.width).isEqualTo(resizedWidth)
        assertThat(contentView.height).isEqualTo(resizedHeight)
        assertThat(sdkToClientCallback.remoteViewWidth).isEqualTo(resizedWidth)
        assertThat(sdkToClientCallback.remoteViewHeight).isEqualTo(resizedHeight)
    }

    @Test
    fun paddingAppliedTest() {
        val sandboxedSdkView = getSandboxedSdkView()
        val resizableViewWidth = sandboxedSdkView.width
        val resizableViewHeight = sandboxedSdkView.height
        val paddingLeft = floor(resizableViewWidth * 0.05).toInt()
        val paddingTop = floor(resizableViewHeight * 0.05).toInt()
        val paddingRight = paddingLeft
        val paddingBottom = paddingTop

        scenario.onActivity { activity ->
            resizeFragment.applyPadding(paddingLeft, paddingTop, paddingRight, paddingBottom)
        }

        assertThat(sdkToClientCallback.resizeLatch.await(CALLBACK_WAIT_MS, TimeUnit.MILLISECONDS))
            .isTrue()

        // TODO(b/411324280): Verify the size of SandboxedSdkView after applying padding.
        val contentView = sandboxedSdkView.getChildAt(0)
        assertThat(contentView.width).isEqualTo(resizableViewWidth - paddingLeft - paddingRight)
        assertThat(contentView.height).isEqualTo(resizableViewHeight - paddingTop - paddingBottom)
        assertThat(sdkToClientCallback.remoteViewWidth).isEqualTo(contentView.width)
        assertThat(sdkToClientCallback.remoteViewHeight).isEqualTo(contentView.height)
    }

    @Test
    fun orientationChangedTest() {
        scenario.onActivity { activity ->
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }

        assertThat(sdkToClientCallback.configLatch.await(CALLBACK_WAIT_MS, TimeUnit.MILLISECONDS))
            .isTrue()
        assertThat(getSandboxedSdkView().context.resources.configuration)
            .isEqualTo(sdkToClientCallback.remoteViewConfiguration)
    }

    private fun getSandboxedSdkView(): SandboxedSdkView {
        var sandboxedSdkView: SandboxedSdkView? = null
        Espresso.onView(instanceOf(SandboxedSdkView::class.java))
            .check(matches(isDisplayed()))
            .check(matches(hasChildCount(1)))
            .check { view, exception -> sandboxedSdkView = view as SandboxedSdkView }
        requireNotNull(sandboxedSdkView) { "SandboxedSdkView was not displayed." }
        return sandboxedSdkView
    }

    // TODO(b/402065627): Move to a common util file
    private fun launchTestAppAndWaitForLoadingSdks(
        @FragmentOption uiFrameworkOption: String,
        @FragmentOption mediationOption: String,
        @FragmentOption zOrdering: String,
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
        extras.putString(FragmentOptions.KEY_FRAGMENT, FragmentOptions.FRAGMENT_RESIZE_HIDDEN)
        extras.putString(FragmentOptions.KEY_UI_FRAMEWORK, uiFrameworkOption)
        extras.putString(FragmentOptions.KEY_MEDIATION, mediationOption)
        extras.putBoolean(FragmentOptions.KEY_DRAW_VIEWABILITY, false)
        extras.putString(FragmentOptions.KEY_Z_ORDER, zOrdering)
        extras.putBinder(FragmentOptions.LOAD_SDK_COMPLETE, loadSdkCallback)
        intent.putExtras(extras)

        scenario = ActivityScenario.launch(intent)
        assertThat(loadSdkCallback.latch.await(CALLBACK_WAIT_MS, TimeUnit.MILLISECONDS)).isTrue()
    }

    private class SdkToClientCallback : IAutomatedTestCallback.Stub() {
        var remoteViewWidth: Int? = null
        var remoteViewHeight: Int? = null
        var remoteViewConfiguration: Configuration? = null
        val resizeLatch = CountDownLatch(1)
        val configLatch = CountDownLatch(1)

        override fun onResizeOccurred(width: Int, height: Int) {
            remoteViewWidth = width
            remoteViewHeight = height
            resizeLatch.countDown()
        }

        override fun onConfigurationChanged(configuration: Configuration) {
            remoteViewConfiguration = configuration
            configLatch.countDown()
        }
    }
}
