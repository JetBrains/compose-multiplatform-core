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
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.filters.SdkSuppress
import androidx.xr.projected.ProjectedInputEvent.ProjectedInputAction
import androidx.xr.projected.experimental.ExperimentalProjectedApi
import androidx.xr.projected.testing.ProjectedTestRule
import com.google.common.truth.Truth.assertThat
import kotlin.test.assertFailsWith
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalProjectedApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Config.TARGET_SDK])
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
class ProjectedActivityCompatTest {

    @get:Rule() val projectedTestRule = ProjectedTestRule()
    private val context: Application = ApplicationProvider.getApplicationContext()

    @Test
    fun create_returnsProjectedActivityCompatInstance() = runBlocking {
        val projectedActivityCompat = ProjectedActivityCompat.create(context)

        assertThat(projectedActivityCompat).isNotNull()
    }

    @Test
    fun create_throwsIllegalStateException() {
        projectedTestRule.throwIllegalStateExceptionWhenCreatingControllers = true

        assertFailsWith<IllegalStateException> {
            runBlocking { ProjectedActivityCompat.create(context) }
        }
    }

    @Test
    fun projectedInputEvents_emitsProjectedInputEvent() =
        runTest(UnconfinedTestDispatcher()) {
            val projectedActivityCompat = ProjectedActivityCompat.create(context)

            launch {
                val receivedInputEvent = projectedActivityCompat.projectedInputEvents.first()
                assertThat(receivedInputEvent.inputAction)
                    .isEqualTo(ProjectedInputAction.TOGGLE_APP_CAMERA)
            }

            projectedTestRule.sendProjectedInputEvent(ProjectedInputAction.TOGGLE_APP_CAMERA)
        }

    @Test
    fun projectedInputEvents_flowIsClosed_afterCloseCalled() =
        runTest(UnconfinedTestDispatcher()) {
            val projectedActivityCompat = ProjectedActivityCompat.create(context)
            var isFlowClosed = false
            val job =
                backgroundScope.launch {
                    try {
                        projectedActivityCompat.projectedInputEvents.collect { /* Do nothing */ }
                    } finally {
                        isFlowClosed = true
                    }
                }

            projectedActivityCompat.close()
            job.join()

            assertThat(isFlowClosed).isTrue()
        }

    @Test
    fun projectedInputAction_fromCode_returnsCorrectEnum() {
        val action = ProjectedInputAction.fromCode(ProjectedInputAction.TOGGLE_APP_CAMERA.code)

        assertThat(action).isEqualTo(ProjectedInputAction.TOGGLE_APP_CAMERA)
    }

    @Test
    fun projectedInputAction_fromCode_withInvalidCode_throwsException() {
        assertFailsWith<IllegalArgumentException> {
            ProjectedInputAction.fromCode(INVALID_PROJECTED_ACTION_CODE)
        }
    }

    companion object {
        private const val INVALID_PROJECTED_ACTION_CODE = -50
    }
}
