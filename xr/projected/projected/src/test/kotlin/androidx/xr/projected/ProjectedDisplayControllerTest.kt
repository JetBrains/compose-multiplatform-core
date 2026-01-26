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

package androidx.xr.projected

import android.app.Activity
import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.ServiceInfo
import android.os.Build
import android.view.WindowManager
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.xr.projected.ProjectedDisplayController.PresentationMode
import androidx.xr.projected.ProjectedDisplayController.PresentationModeFlags
import androidx.xr.projected.experimental.ExperimentalProjectedApi
import androidx.xr.projected.platform.IEngagementModeCallback
import androidx.xr.projected.platform.IEngagementModeService
import androidx.xr.projected.testing.ProjectedTestRule
import com.google.common.truth.Truth.assertThat
import java.util.function.Consumer
import kotlin.test.assertFailsWith
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
@OptIn(ExperimentalProjectedApi::class)
class ProjectedDisplayControllerTest {

    @get:Rule() val projectedTestRule = ProjectedTestRule()
    private val context: Application = ApplicationProvider.getApplicationContext()

    private lateinit var projectedDisplayController: ProjectedDisplayController

    @Test
    fun create_returnsProjectedDisplayControllerInstance() =
        projectedTestRule.launchTestProjectedDeviceActivity { projectedDeviceActivity ->
            runBlocking {
                projectedDisplayController =
                    ProjectedDisplayController.create(projectedDeviceActivity)
            }

            assertThat(projectedDisplayController).isNotNull()
        }

    @Test
    fun create_throwsIllegalStateException() =
        projectedTestRule.launchTestProjectedDeviceActivity { projectedDeviceActivity ->
            projectedTestRule.throwIllegalStateExceptionWhenCreatingControllers = true

            assertFailsWith<IllegalStateException> {
                runBlocking {
                    projectedDisplayController =
                        ProjectedDisplayController.create(projectedDeviceActivity)
                }
            }
        }

    @Test
    fun create_nonProjectedDeviceActivity_throwsException() =
        launchTestActivity { nonProjectedDeviceActivity ->
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking { ProjectedDisplayController.create(nonProjectedDeviceActivity) }
            }
        }

    @Test
    fun addFlags_callsService() =
        projectedTestRule.launchTestProjectedDeviceActivity { projectedDeviceActivity ->
            runBlocking {
                projectedDisplayController =
                    ProjectedDisplayController.create(projectedDeviceActivity)
            }
            val flags = WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON

            projectedDisplayController.addLayoutParamsFlags(flags)

            assertThat(projectedTestRule.projectedLayoutParamFlags.and(flags) == flags).isTrue()
        }

    @Test
    fun removeFlags_callsService() =
        projectedTestRule.launchTestProjectedDeviceActivity { projectedDeviceActivity ->
            runBlocking {
                projectedDisplayController =
                    ProjectedDisplayController.create(projectedDeviceActivity)
            }
            val flags = WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON

            projectedDisplayController.addLayoutParamsFlags(flags)
            check(projectedTestRule.projectedLayoutParamFlags.and(flags) == flags)

            projectedDisplayController.removeLayoutParamsFlags(flags)
            assertThat(projectedTestRule.projectedLayoutParamFlags.and(flags) == flags).isFalse()
        }

    @Test
    fun close_disconnectsConnection() =
        projectedTestRule.launchTestProjectedDeviceActivity { projectedDeviceActivity ->
            runBlocking {
                projectedDisplayController =
                    ProjectedDisplayController.create(projectedDeviceActivity)
            }
            check(shadowOf(context).boundServiceConnections.size == 1)

            projectedDisplayController.close()

            assertThat(shadowOf(context).boundServiceConnections).isEmpty()
        }

    @OptIn(ExperimentalCoroutinesApi::class, ExperimentalProjectedApi::class)
    @Test
    fun addPresentationModeChangedListener_callsService() {
        projectedTestRule.launchTestProjectedDeviceActivity { projectedDeviceActivity ->
            val dispatcher = UnconfinedTestDispatcher()
            Dispatchers.setMain(dispatcher)
            runBlocking {
                val mockEngagementModeService = setUpEngagementModeService()
                projectedDisplayController =
                    ProjectedDisplayController.create(projectedDeviceActivity)

                val listener = Consumer<PresentationModeFlags> {}
                projectedDisplayController.addPresentationModeChangedListener(listener = listener)

                verify(mockEngagementModeService).registerCallback(any())

                removeEngagementModeService()
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class, ExperimentalProjectedApi::class)
    @Test
    fun addPresentationModeChangedListener_receivesUpdates() {
        projectedTestRule.launchTestProjectedDeviceActivity { projectedDeviceActivity ->
            val dispatcher = UnconfinedTestDispatcher()
            Dispatchers.setMain(dispatcher)
            runBlocking {
                val mockEngagementModeService = setUpEngagementModeService()
                projectedDisplayController =
                    ProjectedDisplayController.create(projectedDeviceActivity)

                var presentationModes: PresentationModeFlags? = null

                projectedDisplayController.addPresentationModeChangedListener { updatedModes ->
                    presentationModes = updatedModes
                }

                // Trigger the callback.
                val callbackCaptor = argumentCaptor<IEngagementModeCallback>()
                verify(mockEngagementModeService).registerCallback(callbackCaptor.capture())
                callbackCaptor.firstValue.onEngagementModeChanged(
                    EngagementModeClient.ENGAGEMENT_MODE_FLAG_VISUALS_ON
                )

                assertThat(presentationModes).isNotNull()
                assertThat(presentationModes?.hasPresentationMode(PresentationMode.VISUALS_ON))
                    .isTrue()
                assertThat(presentationModes?.hasPresentationMode(PresentationMode.AUDIO_ON))
                    .isTrue()
                removeEngagementModeService()
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class, ExperimentalProjectedApi::class)
    @Test
    fun addMultipleEngagementModeChangedListener_receivesUpdates() {
        projectedTestRule.launchTestProjectedDeviceActivity { projectedDeviceActivity ->
            val dispatcher = UnconfinedTestDispatcher()
            Dispatchers.setMain(dispatcher)
            runBlocking {
                val mockEngagementModeService = setUpEngagementModeService()
                projectedDisplayController =
                    ProjectedDisplayController.create(projectedDeviceActivity)

                var presentationModes1: PresentationModeFlags? = null
                var callCount1 = 0
                val listener1 =
                    Consumer<PresentationModeFlags> { updatedModes ->
                        presentationModes1 = updatedModes
                        ++callCount1
                    }
                var presentationModes2: PresentationModeFlags? = null
                var callCount2 = 0
                val listener2 =
                    Consumer<PresentationModeFlags> { updatedModes ->
                        presentationModes2 = updatedModes
                        ++callCount2
                    }
                projectedDisplayController.addPresentationModeChangedListener(listener = listener1)
                projectedDisplayController.addPresentationModeChangedListener(listener = listener2)

                // Trigger the callback.
                val callbackCaptor = argumentCaptor<IEngagementModeCallback>()
                verify(mockEngagementModeService).registerCallback(callbackCaptor.capture())
                callbackCaptor.firstValue.onEngagementModeChanged(
                    EngagementModeClient.ENGAGEMENT_MODE_FLAG_VISUALS_ON
                )

                // Verify that both listeners were called once.
                assertThat(callCount1).isEqualTo(1)
                assertThat(presentationModes1).isNotNull()
                assertThat(presentationModes1?.hasPresentationMode(PresentationMode.VISUALS_ON))
                    .isTrue()
                assertThat(presentationModes1?.hasPresentationMode(PresentationMode.AUDIO_ON))
                    .isTrue()
                assertThat(callCount2).isEqualTo(1)
                assertThat(presentationModes2).isNotNull()
                assertThat(presentationModes2?.hasPresentationMode(PresentationMode.VISUALS_ON))
                    .isTrue()
                assertThat(presentationModes1?.hasPresentationMode(PresentationMode.AUDIO_ON))
                    .isTrue()

                // Remove the second callback.
                projectedDisplayController.removePresentationModeChangedListener(listener2)

                // Trigger another callback.
                callbackCaptor.firstValue.onEngagementModeChanged(0)

                // Verify that only the first listener was called again
                assertThat(callCount1).isEqualTo(2)
                assertThat(presentationModes1).isNotNull()
                assertThat(presentationModes1?.hasPresentationMode(PresentationMode.VISUALS_ON))
                    .isFalse()
                assertThat(presentationModes1?.hasPresentationMode(PresentationMode.AUDIO_ON))
                    .isTrue()
                assertThat(callCount2).isEqualTo(1)
                assertThat(presentationModes2).isNotNull()
                assertThat(presentationModes2?.hasPresentationMode(PresentationMode.VISUALS_ON))
                    .isTrue()
                assertThat(presentationModes1?.hasPresentationMode(PresentationMode.AUDIO_ON))
                    .isTrue()
                removeEngagementModeService()
            }
        }
    }

    private fun setUpEngagementModeService(): IEngagementModeService {
        val mockEngagementModeService = mock<IEngagementModeService>()
        val mockEngagementModeServiceStub =
            mock<IEngagementModeService.Stub> {
                on { queryLocalInterface(any()) }.thenReturn(mockEngagementModeService)
            }
        shadowOf(context.packageManager).apply {
            addServiceIfNotPresent(ENGAGEMENT_MODE_SERVICE_COMPONENT)
            addOrUpdateService(
                ServiceInfo().apply {
                    packageName = SYSTEM_PACKAGE_NAME
                    name = ENGAGEMENT_MODE_SYSTEM_CLASS_NAME
                }
            )
            addIntentFilterForService(
                ENGAGEMENT_MODE_SERVICE_COMPONENT,
                IntentFilter(EngagementModeClient.SERVICE_ACTION),
            )
        }
        shadowOf(context).apply {
            setComponentNameAndServiceForBindService(
                ENGAGEMENT_MODE_SERVICE_COMPONENT,
                mockEngagementModeServiceStub,
            )
            setBindServiceCallsOnServiceConnectedDirectly(true)
        }
        return mockEngagementModeService
    }

    private fun removeEngagementModeService() {
        shadowOf(context.packageManager).apply { removeService(ENGAGEMENT_MODE_SERVICE_COMPONENT) }
    }

    private fun launchTestActivity(block: (Activity) -> Unit) {
        shadowOf(context.packageManager)
            .addOrUpdateActivity(
                ActivityInfo().apply {
                    name = TestActivity::class.java.name
                    packageName = context.packageName
                }
            )
        val activityScenario: ActivityScenario<TestActivity> =
            ActivityScenario.launch(Intent(context, TestActivity::class.java))
        activityScenario.onActivity { activity -> block(activity) }
    }

    companion object {
        private const val SYSTEM_PACKAGE_NAME = "com.system.service"
        private const val ENGAGEMENT_MODE_SYSTEM_CLASS_NAME =
            "com.system.service.EngagementModeService"
        private val ENGAGEMENT_MODE_SERVICE_COMPONENT =
            ComponentName(SYSTEM_PACKAGE_NAME, ENGAGEMENT_MODE_SYSTEM_CLASS_NAME)
    }
}

private class TestActivity : Activity()
