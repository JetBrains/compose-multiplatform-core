/*
 * Copyright 2024 The Android Open Source Project
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
package androidx.camera.camera2.pipe.internal

import android.hardware.camera2.CaptureRequest
import androidx.camera.camera2.pipe.graph.SessionLock
import androidx.camera.camera2.pipe.testing.FakeGraphProcessor
import androidx.camera.camera2.pipe.testing.FakeMetadata.Companion.TEST_KEY
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.test.TestScope
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Tests for [CameraGraphParametersImpl] */
@RunWith(RobolectricTestRunner::class)
class CameraGraphParametersImplTest {
    private var parameters =
        CameraGraphParametersImpl(SessionLock(), FakeGraphProcessor(), TestScope())

    @Test
    fun get_returnLatestValue() {
        parameters[TEST_KEY] = 42
        parameters[CAPTURE_REQUEST_KEY] = 2
        parameters[TEST_NULLABLE_KEY] = null

        assertEquals(parameters[TEST_KEY], 42)
        assertEquals(parameters[CAPTURE_REQUEST_KEY], 2)
        assertNull(parameters[TEST_NULLABLE_KEY])
    }

    @Test
    fun setAll_multipleEntriesSet() {
        parameters.setAll(
            mapOf(TEST_KEY to 42, CAPTURE_REQUEST_KEY to 2, TEST_NULLABLE_KEY to null)
        )

        assertEquals(parameters[TEST_KEY], 42)
        assertEquals(parameters[CAPTURE_REQUEST_KEY], 2)
        assertNull(parameters[TEST_NULLABLE_KEY])
    }

    @Test
    fun remove_parameterRemoved() {
        parameters[TEST_KEY] = 42

        parameters.remove(TEST_KEY)

        assertNull(parameters[TEST_KEY])
    }

    @Test
    fun removeAll_valuesEmpty() {
        parameters[TEST_KEY] = 42
        parameters[CAPTURE_REQUEST_KEY] = 2

        parameters.clear()

        assertNull(parameters[TEST_KEY])
        assertNull(parameters[CAPTURE_REQUEST_KEY])
    }

    @Test
    fun fetchUpdatedParameters_returnOnlyDirtyParameters() {
        assertNull(parameters.fetchUpdatedParameters())

        parameters[TEST_KEY] = 42
        assertEquals(parameters.fetchUpdatedParameters(), mapOf<Any, Any?>(TEST_KEY to 42))
        assertNull(parameters.fetchUpdatedParameters())

        parameters[CAPTURE_REQUEST_KEY] = 2
        assertEquals(
            parameters.fetchUpdatedParameters(),
            mapOf<Any, Any?>(TEST_KEY to 42, CAPTURE_REQUEST_KEY to 2)
        )
    }

    companion object {
        private val CAPTURE_REQUEST_KEY = CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION
        private val TEST_NULLABLE_KEY = CaptureRequest.BLACK_LEVEL_LOCK
    }
}
