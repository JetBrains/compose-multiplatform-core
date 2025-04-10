/*
 * Copyright 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.wear.watchface.push.tests

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.wear.watchface.push.*
import androidx.wear.watchface.push.WatchFacePushManager.RemoveWatchFaceException
import androidx.wear.watchface.push.WatchFacePushManager.WatchFaceDetails
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class RemoveWatchFaceTest {
    private var context: Context = ApplicationProvider.getApplicationContext()
    private var wfp = WatchFacePushManager(context)

    @Before
    fun setup() {
        setup(context, SAMPLE_WATCHFACE)
    }

    @Test
    fun removeWatchFace() {
        runBlocking {
            val details: WatchFaceDetails =
                readWatchFace(context, VALID_APK).use { pipe ->
                    wfp.addWatchFace(pipe.readFd, VALID_TOKEN)
                }
            assertThat(wfp.listWatchFaces().installedWatchFaceDetails).isNotEmpty()
            wfp.removeWatchFace(details.slotId)
            assertThat(wfp.listWatchFaces().installedWatchFaceDetails).isEmpty()
        }
    }

    @Test
    fun removeWatchFace_invalidSlot() {
        val exception =
            Assert.assertThrows(RemoveWatchFaceException::class.java) {
                runBlocking { wfp.removeWatchFace("an invalid slot") }
            }

        assertThat(exception.errorCode)
            .isEqualTo(RemoveWatchFaceException.Companion.ERROR_INVALID_SLOT_ID)
        assertThat(exception).hasMessageThat().contains("slot ID")
    }
}
