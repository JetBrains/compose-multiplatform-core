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
import android.companion.virtual.VirtualDeviceManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.annotation.RequiresApi
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.xr.projected.ProjectedContext.PROJECTED_DEVICE_NAME
import androidx.xr.projected.ProjectedServiceBinding.ACTION_BIND
import androidx.xr.projected.experimental.ExperimentalProjectedApi
import androidx.xr.projected.platform.IEngagementModeCallback
import androidx.xr.projected.platform.IEngagementModeService
import androidx.xr.projected.platform.IProjectedService
import com.google.common.truth.Truth.assertThat
import kotlin.test.assertFailsWith
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter

@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
class ProjectedDisplayControllerTest {

    private val mockProjectedService = mock<IProjectedService>()
    private val mockProjectedServiceStub =
        mock<IProjectedService.Stub> {
            on { queryLocalInterface(any()) }.thenReturn(mockProjectedService)
        }
    private val context: Application = ApplicationProvider.getApplicationContext()

    private lateinit var projectedDisplayController: ProjectedDisplayController

    @Before
    fun setUp() {
        shadowOf(context.packageManager).apply {
            addServiceIfNotPresent(COMPONENT_NAME)
            addOrUpdateService(SERVICE_INFO)
            addIntentFilterForService(COMPONENT_NAME, IntentFilter(ACTION_BIND))
            installPackage(PACKAGE_INFO)
        }

        shadowOf(context).apply {
            setComponentNameAndServiceForBindService(COMPONENT_NAME, mockProjectedServiceStub)
            setBindServiceCallsOnServiceConnectedDirectly(true)
        }
    }

    @Test
    fun create_returnsProjectedDisplayControllerInstance() =
        launchTestProjectedDeviceActivity { projectedDeviceActivity ->
            runBlocking {
                projectedDisplayController =
                    ProjectedDisplayController.create(projectedDeviceActivity)
            }

            assertThat(projectedDisplayController).isNotNull()
        }

    @Test
    fun create_bindingToServiceNotPermitted_throwsException() =
        launchTestProjectedDeviceActivity { projectedDeviceActivity ->
            shadowOf(context).declareComponentUnbindable(COMPONENT_NAME)

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
    @OptIn(ExperimentalProjectedApi::class)
    fun addFlags_callsService() = launchTestProjectedDeviceActivity { projectedDeviceActivity ->
        runBlocking {
            projectedDisplayController = ProjectedDisplayController.create(projectedDeviceActivity)
        }
        val flags = WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON

        projectedDisplayController.addLayoutParamsFlags(flags)

        verify(mockProjectedService).addWindowFlags(flags)
    }

    @Test
    @OptIn(ExperimentalProjectedApi::class)
    fun removeFlags_callsService() = launchTestProjectedDeviceActivity { projectedDeviceActivity ->
        runBlocking {
            projectedDisplayController = ProjectedDisplayController.create(projectedDeviceActivity)
        }
        val flags = WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON

        projectedDisplayController.removeLayoutParamsFlags(flags)
        verify(mockProjectedService).clearWindowFlags(flags)
    }

    @Test
    fun isDisplayCapable_serviceReturnsTrue_returnsTrue() =
        launchTestProjectedDeviceActivity { projectedDeviceActivity ->
            runBlocking {
                projectedDisplayController =
                    ProjectedDisplayController.create(projectedDeviceActivity)
            }
            whenever(mockProjectedService.isDisplayCapable()).thenReturn(true)

            assertThat(projectedDisplayController.isDisplayCapable()).isTrue()
        }

    @Test
    fun isDisplayCapable_serviceReturnsFalse_returnsFalse() =
        launchTestProjectedDeviceActivity { projectedDeviceActivity ->
            runBlocking {
                projectedDisplayController =
                    ProjectedDisplayController.create(projectedDeviceActivity)
            }
            whenever(mockProjectedService.isDisplayCapable()).thenReturn(false)

            assertThat(projectedDisplayController.isDisplayCapable()).isFalse()
        }

    @Test
    fun close_disconnectsConnection() =
        launchTestProjectedDeviceActivity { projectedDeviceActivity ->
            runBlocking {
                projectedDisplayController =
                    ProjectedDisplayController.create(projectedDeviceActivity)
            }
            check(shadowOf(context).boundServiceConnections.size == 1)

            projectedDisplayController.close()

            assertThat(shadowOf(context).boundServiceConnections).isEmpty()
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun windowLayoutInfo_fromService_returnsEngagementMode() {
        launchTestProjectedDeviceActivity { projectedDeviceActivity ->
            // The windowLayoutInfo needs to be called from the main thread.
            val dispatcher = UnconfinedTestDispatcher()
            Dispatchers.setMain(dispatcher)
            runTest {
                val mockEngagementModeService = mock<IEngagementModeService>()
                val mockEngagementModeServiceStub =
                    mock<IEngagementModeService.Stub> {
                        on { queryLocalInterface(any()) }.thenReturn(mockEngagementModeService)
                    }
                val engagementModeServiceComponent =
                    ComponentName(SYSTEM_PACKAGE_NAME, ENGAGEMENT_MODE_SYSTEM_CLASS_NAME)
                shadowOf(context.packageManager).apply {
                    addServiceIfNotPresent(engagementModeServiceComponent)
                    addOrUpdateService(
                        ServiceInfo().apply {
                            packageName = SYSTEM_PACKAGE_NAME
                            name = ENGAGEMENT_MODE_SYSTEM_CLASS_NAME
                        }
                    )
                    addIntentFilterForService(
                        engagementModeServiceComponent,
                        IntentFilter(EngagementModeClient.SERVICE_ACTION),
                    )
                }
                shadowOf(context).apply {
                    setComponentNameAndServiceForBindService(
                        engagementModeServiceComponent,
                        mockEngagementModeServiceStub,
                    )
                    setBindServiceCallsOnServiceConnectedDirectly(true)
                }
                projectedDisplayController =
                    ProjectedDisplayController.create(projectedDeviceActivity)

                val layoutInfoFlow = projectedDisplayController.windowLayoutInfo(context)
                var result: WindowLayoutInfo? = null
                val job = launch { result = layoutInfoFlow.first() }

                advanceUntilIdle()

                // Trigger the callback.
                val callbackCaptor = argumentCaptor<IEngagementModeCallback>()
                verify(mockEngagementModeService).registerCallback(callbackCaptor.capture())
                callbackCaptor.firstValue.onEngagementModeChanged(
                    EngagementModeClient.ENGAGEMENT_MODE_FLAG_AUDIO_ON
                )

                advanceUntilIdle()

                assertThat(result)
                    .isEqualTo(WindowLayoutInfo(EngagementModeClient.ENGAGEMENT_MODE_FLAG_AUDIO_ON))

                job.cancel()
            }
        }
    }

    private fun launchTestProjectedDeviceActivity(block: (Activity) -> Unit) {
        shadowOf(context.packageManager)
            .addOrUpdateActivity(
                ActivityInfo().apply {
                    name = TestProjectedDeviceActivity::class.java.name
                    packageName = context.packageName
                }
            )
        val activityScenario: ActivityScenario<TestProjectedDeviceActivity> =
            ActivityScenario.launch(Intent(context, TestProjectedDeviceActivity::class.java))
        activityScenario.onActivity { activity -> block(activity) }
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
        private const val SYSTEM_CLASS_NAME = "com.system.service.ProjectedService"
        private const val ENGAGEMENT_MODE_SYSTEM_CLASS_NAME =
            "com.system.service.EngagementModeService"
        private val COMPONENT_NAME = ComponentName(SYSTEM_PACKAGE_NAME, SYSTEM_CLASS_NAME)
        private val SERVICE_INFO =
            ServiceInfo().apply {
                packageName = SYSTEM_PACKAGE_NAME
                name = SYSTEM_CLASS_NAME
            }
        private val PACKAGE_INFO =
            PackageInfo().apply {
                packageName = SYSTEM_PACKAGE_NAME
                services = arrayOf(SERVICE_INFO)
                applicationInfo = ApplicationInfo().apply { flags = ApplicationInfo.FLAG_SYSTEM }
            }
    }
}

private class TestActivity : Activity()

@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
private class TestProjectedDeviceActivity : Activity() {

    private lateinit var virtualDeviceManager: VirtualDeviceManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        virtualDeviceManager =
            getSystemService(Context.VIRTUAL_DEVICE_SERVICE) as VirtualDeviceManager
        createVirtualDevice()
    }

    override fun getDeviceId(): Int {
        return virtualDeviceManager.virtualDevices.first().deviceId
    }

    private fun createVirtualDevice() {
        val virtualDeviceParamsBuilderClass =
            Class.forName("android.companion.virtual.VirtualDeviceParams\$Builder")
        val virtualDeviceParamsClass =
            Class.forName("android.companion.virtual.VirtualDeviceParams")
        var virtualDeviceParamsBuilder =
            ReflectionHelpers.callConstructor(virtualDeviceParamsBuilderClass)
        virtualDeviceParamsBuilder =
            ReflectionHelpers.callInstanceMethod(
                virtualDeviceParamsBuilder,
                "setName",
                ClassParameter(String::class.java, PROJECTED_DEVICE_NAME),
            )
        virtualDeviceParamsBuilder =
            ReflectionHelpers.callInstanceMethod(virtualDeviceParamsBuilder, "build")

        ReflectionHelpers.callInstanceMethod<Any?>(
            virtualDeviceManager,
            "createVirtualDevice",
            ClassParameter(Int::class.javaPrimitiveType, 1),
            ClassParameter(virtualDeviceParamsClass, virtualDeviceParamsBuilder),
        )
    }
}
