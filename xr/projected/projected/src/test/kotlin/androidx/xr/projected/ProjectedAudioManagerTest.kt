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

import android.app.Application
import android.content.ComponentName
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.ServiceInfo
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.test.core.app.ApplicationProvider
import androidx.xr.projected.ProjectedServiceBinding.ACTION_BIND
import com.google.common.truth.Truth.assertThat
import kotlin.test.assertFailsWith
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class ProjectedAudioManagerTest {

    private lateinit var context: Application
    private lateinit var lifecycleOwner: LifecycleOwner
    private lateinit var lifecycleRegistry: LifecycleRegistry

    private val testProjectedService = TestProjectedService()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        lifecycleOwner =
            object : LifecycleOwner {
                private val registry = LifecycleRegistry(this)

                init {
                    registry.currentState = Lifecycle.State.CREATED
                }

                override val lifecycle: Lifecycle
                    get() = registry
            }
        lifecycleRegistry = lifecycleOwner.lifecycle as LifecycleRegistry
        shadowOf(context.packageManager).apply {
            addServiceIfNotPresent(COMPONENT_NAME)
            addOrUpdateService(SERVICE_INFO)
            addIntentFilterForService(COMPONENT_NAME, IntentFilter(ACTION_BIND))
            installPackage(PACKAGE_INFO)
        }

        shadowOf(context)
            .setComponentNameAndServiceForBindService(COMPONENT_NAME, testProjectedService)
    }

    @Test
    fun getSupportedAudioConfigs_returnsAudioConfigs() = runBlocking {
        shadowOf(context).setBindServiceCallsOnServiceConnectedDirectly(true)

        val projectedAudioManager = ProjectedAudioManager.create(context, lifecycleOwner)

        val supportedAudioConfigs = projectedAudioManager.getSupportedAudioCaptureConfigs()
        assertThat(supportedAudioConfigs).hasSize(1)
        assertThat(supportedAudioConfigs[0].sourceType).isEqualTo(TEST_SOURCE_TYPE)
        assertThat(supportedAudioConfigs[0].channelCounts).isEqualTo(TEST_CHANNEL_COUNTS)
        assertThat(supportedAudioConfigs[0].sampleRatesHz).isEqualTo(TEST_SAMPLE_RATES_HZ)
    }

    @Test
    fun initialize_returnsProjectedCapabilitiesInstance() = runBlocking {
        shadowOf(context).setBindServiceCallsOnServiceConnectedDirectly(true)

        val projectedAudioManager = ProjectedAudioManager.create(context, lifecycleOwner)

        assertThat(projectedAudioManager).isNotNull()
    }

    @Test
    fun initialize_bindingToServiceNotPermitted_throwsException() {
        shadowOf(context).declareComponentUnbindable(COMPONENT_NAME)

        assertFailsWith<IllegalStateException> {
            runBlocking {
                val unused = ProjectedAudioManager.create(context, lifecycleOwner)
            }
        }
    }

    companion object {
        private const val SYSTEM_PACKAGE_NAME = "com.system.service"
        private const val SYSTEM_CLASS_NAME = "com.system.service.ProjectedService"
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
        private const val TEST_SOURCE_TYPE = 1
        private val TEST_CHANNEL_COUNTS = intArrayOf(2)
        private val TEST_SAMPLE_RATES_HZ = intArrayOf(5000)
    }

    private class TestProjectedService : IProjectedService.Stub() {
        override fun getSupportedCaptureAudioConfigs(): Array<out SupportedCaptureAudioConfig?>? =
            arrayOf(
                SupportedCaptureAudioConfig().apply {
                    sourceType = TEST_SOURCE_TYPE
                    channelCounts = TEST_CHANNEL_COUNTS
                    sampleRatesHz = TEST_SAMPLE_RATES_HZ
                }
            )
    }
}
