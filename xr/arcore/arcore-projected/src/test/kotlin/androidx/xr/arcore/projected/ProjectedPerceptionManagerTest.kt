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
package androidx.xr.arcore.projected

import androidx.xr.runtime.VpsAvailabilityAvailable
import androidx.xr.runtime.VpsAvailabilityErrorInternal
import androidx.xr.runtime.VpsAvailabilityNetworkError
import androidx.xr.runtime.VpsAvailabilityNotAuthorized
import androidx.xr.runtime.VpsAvailabilityResourceExhausted
import androidx.xr.runtime.VpsAvailabilityResult
import androidx.xr.runtime.VpsAvailabilityUnavailable
import com.google.common.truth.Truth.assertThat
import com.google.testing.junit.testparameterinjector.TestParameter
import com.google.testing.junit.testparameterinjector.TestParameterInjector
import kotlin.reflect.KClass
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mock
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(TestParameterInjector::class)
class ProjectedPerceptionManagerTest {
    @Mock private lateinit var mockPerceptionService: IProjectedPerceptionService.Stub
    private lateinit var perceptionManager: ProjectedPerceptionManager

    // vpsState is the enum VpsAvailability, see the code in
    // third_party/arcore/java/com/google/ar/core/VpsAvailability.java
    // and the onVpsAvailabilityChanged callback call in
    // java/com/google/android/projection/core/modules/perception/PerceptionManagerService.java.
    enum class VpsAvailabilityTestCase(
        val vpsState: Int,
        val expectedResult: KClass<out VpsAvailabilityResult>,
    ) {
        // VpsAvailability.AVAILABLE
        AVAILABLE(1, VpsAvailabilityAvailable::class),
        // VpsAvailability.UNAVAILABLE
        UNAVAILABLE(2, VpsAvailabilityUnavailable::class),
        // VpsAvailability.ERROR_NETWORK_CONNECTION
        NETWORK_ERROR(-2, VpsAvailabilityNetworkError::class),
        // VpsAvailability.ERROR_NOT_AUTHORIZED
        NOT_AUTHORIZED(-3, VpsAvailabilityNotAuthorized::class),
        // VpsAvailability.ERROR_RESOURCE_EXHAUSTED
        RESOURCE_EXHAUSTED(-4, VpsAvailabilityResourceExhausted::class),
        // VpsAvailability.ERROR_INTERNAL
        INTERNAL_ERROR(-1, VpsAvailabilityErrorInternal::class),
        // VpsAvailability.UNKNOWN
        UNKNOWN(0, VpsAvailabilityErrorInternal::class),
    }

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)
        `when`(mockPerceptionService.asBinder()).thenReturn(mockPerceptionService)
        `when`(mockPerceptionService.queryLocalInterface(anyString()))
            .thenReturn(mockPerceptionService)
        perceptionManager = ProjectedPerceptionManager(ProjectedTimeSource())
    }

    @Test
    fun checkVpsAvailability_returnsCorrectResult(
        @TestParameter testCase: VpsAvailabilityTestCase
    ) = runTest {
        perceptionManager.xrResources.service = mockPerceptionService
        doAnswer { invocation ->
                val callback = invocation.getArgument<IVpsAvailabilityCallback>(2)
                callback.onVpsAvailabilityChanged(testCase.vpsState)
                null
            }
            .`when`(mockPerceptionService)
            .checkVpsAvailability(eq(1.0), eq(2.0), any(IVpsAvailabilityCallback::class.java))

        val result = perceptionManager.checkVpsAvailability(1.0, 2.0)

        assertThat(result).isInstanceOf(testCase.expectedResult.java)
        verify(mockPerceptionService)
            .checkVpsAvailability(eq(1.0), eq(2.0), any(IVpsAvailabilityCallback::class.java))
    }
}
