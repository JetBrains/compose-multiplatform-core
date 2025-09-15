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

import androidx.xr.runtime.VpsAvailabilityErrorInternal
import com.google.common.truth.Truth.assertThat
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
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ProjectedPerceptionManagerTest {
    @Mock private lateinit var mockPerceptionService: IProjectedPerceptionService.Stub
    private lateinit var perceptionManager: ProjectedPerceptionManager

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)
        `when`(mockPerceptionService.asBinder()).thenReturn(mockPerceptionService)
        `when`(mockPerceptionService.queryLocalInterface(anyString()))
            .thenReturn(mockPerceptionService)
        perceptionManager = ProjectedPerceptionManager(ProjectedTimeSource())
    }

    @Test
    fun checkVpsAvailability_callsServiceWithCorrectParameters() = runTest {
        perceptionManager.service = mockPerceptionService
        doAnswer { invocation ->
                val callback = invocation.getArgument<IVpsAvailabilityCallback>(2)
                callback.onVpsAvailabilityChanged(0)
                null
            }
            .`when`(mockPerceptionService)
            .checkVpsAvailability(eq(1.0), eq(2.0), any(IVpsAvailabilityCallback::class.java))

        val result = perceptionManager.checkVpsAvailability(1.0, 2.0)

        assertThat(result).isInstanceOf(VpsAvailabilityErrorInternal::class.java)
        verify(mockPerceptionService)
            .checkVpsAvailability(eq(1.0), eq(2.0), any(IVpsAvailabilityCallback::class.java))
    }
}
