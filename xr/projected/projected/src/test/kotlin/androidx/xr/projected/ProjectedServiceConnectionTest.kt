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

import android.app.Application
import android.content.ComponentName
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.lifecycleScope
import androidx.test.core.app.ApplicationProvider
import androidx.xr.projected.IProjectedService
import androidx.xr.projected.ProjectedServiceBinding.ACTION_BIND
import androidx.xr.projected.ProjectedServiceConnection
import com.google.common.truth.Truth.assertThat
import kotlin.test.assertFailsWith
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class ProjectedServiceConnectionTest {

    private lateinit var context: Application
    private lateinit var lifecycleOwner: LifecycleOwner
    private lateinit var lifecycleRegistry: LifecycleRegistry

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
            .setComponentNameAndServiceForBindService(COMPONENT_NAME, mock(IBinder::class.java))
    }

    @Test
    fun connect_bindsAndConnects_returnsInstance() = runBlocking {
        shadowOf(context).setBindServiceCallsOnServiceConnectedDirectly(true)

        val service =
            lifecycleOwner.lifecycleScope.async {
                ProjectedServiceConnection.connect(context, lifecycleOwner)
            }

        assertThat(service.await()).isInstanceOf(IProjectedService::class.java)
    }

    @Test
    fun connect_doesNotBind_throwsException() = runBlocking {
        shadowOf(context).declareComponentUnbindable(COMPONENT_NAME)

        val unused =
            assertFailsWith<IllegalStateException> {
                ProjectedServiceConnection.connect(context, lifecycleOwner)
            }
    }

    @Test
    fun onDestroy_unbinds() {
        lifecycleOwner.lifecycleScope.launch {
            val unused = ProjectedServiceConnection.connect(context, lifecycleOwner)
        }
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED

        assertThat(shadowOf(context).unboundServiceConnections).isNotEmpty()
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
    }
}
