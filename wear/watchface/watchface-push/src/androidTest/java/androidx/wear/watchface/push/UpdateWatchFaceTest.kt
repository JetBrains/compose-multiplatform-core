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
import androidx.wear.watchface.push.WatchFacePushManager.UpdateWatchFaceException
import androidx.wear.watchface.push.WatchFacePushManager.WatchFaceDetails
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class UpdateWatchFaceTest {
    private var context: Context = ApplicationProvider.getApplicationContext()
    private var wfp = WatchFacePushManager(context)
    private lateinit var details: WatchFaceDetails

    @Before
    fun setup() {
        setup(
            context,
            listOf(
                SAMPLE_WATCHFACE,
                "androidx.wear.watchface.push.test.watchfacepush.androidxsample2"
            )
        )
        details = runBlocking {
            readWatchFace(context, VALID_APK).use { pipe ->
                wfp.addWatchFace(pipe.readFd, VALID_TOKEN)
            }
        }
    }

    @Test
    fun updateWatchFace() {
        runBlocking {
            readWatchFace(context, "androidxsample2.apk").use { pipe ->
                val newWatchFaceDetails =
                    wfp.updateWatchFace(
                        details.slotId,
                        pipe.readFd,
                        "nBUctGetKYcewjHzkFrrjO1uN63VyaUJyOKsXKiQm8U=:MS4wLjA="
                    )

                assertThat(wfp.listWatchFaces().installedWatchFaceDetails).isNotEmpty()
                assertThat(newWatchFaceDetails.packageName).isNotEqualTo(details.packageName)
            }
        }
    }

    @Test
    fun updateWatchFace_withTheSameApk() {
        runBlocking {
            readWatchFace(context, VALID_APK).use { pipe ->
                val newWatchFaceDetails =
                    wfp.updateWatchFace(details.slotId, pipe.readFd, VALID_TOKEN)

                assertThat(wfp.listWatchFaces().installedWatchFaceDetails).isNotEmpty()
                assertThat(newWatchFaceDetails.packageName).isEqualTo(details.packageName)
            }
        }
    }

    @Test
    fun updateWatchFace_onInvalidValidationToken() {
        runBlocking {
            val exception =
                assertThrows(UpdateWatchFaceException::class.java) {
                    runBlocking {
                        readWatchFace(context, VALID_APK).use { pipe ->
                            wfp.updateWatchFace(details.slotId, pipe.readFd, INVALID_TOKEN)
                        }
                    }
                }

            assertThat(exception.errorCode)
                .isEqualTo(UpdateWatchFaceException.ERROR_INVALID_VALIDATION_TOKEN)
            assertThat(exception).hasMessageThat().contains("token")
        }
    }

    @Test
    fun updateWatchFace_onInvalidSlot() {
        runBlocking {
            val exception =
                assertThrows(UpdateWatchFaceException::class.java) {
                    runBlocking {
                        readWatchFace(context, VALID_APK).use { pipe ->
                            wfp.updateWatchFace("bad slot id", pipe.readFd, VALID_TOKEN)
                        }
                    }
                }

            assertThat(exception.errorCode)
                .isEqualTo(UpdateWatchFaceException.ERROR_INVALID_SLOT_ID)
            assertThat(exception).hasMessageThat().contains("slot ID")
        }
    }

    @Test
    fun updateWatchFace_onMalformedApk() {

        val exception =
            assertThrows(UpdateWatchFaceException::class.java) {
                runBlocking {
                    readWatchFace(context, MALFORMED_APK).use { pipe ->
                        wfp.updateWatchFace(details.slotId, pipe.readFd, VALID_TOKEN)
                    }
                }
            }

        assertThat(exception.errorCode)
            .isEqualTo(UpdateWatchFaceException.ERROR_MALFORMED_WATCHFACE_APK)
        assertThat(exception).hasMessageThat().contains("APK")
    }

    @Test
    fun updateWatchFace_onUnexpectedContent() {
        val exception =
            assertThrows(UpdateWatchFaceException::class.java) {
                runBlocking {
                    readWatchFace(context, UNSECURE_APK).use { pipe ->
                        wfp.updateWatchFace(
                            details.slotId,
                            pipe.readFd,
                            "xK7FaCagHoF1JrN8WZK8JoUOtMwW+HVuO1zyMI6ZFmE=:MS4wLjA="
                        )
                    }
                }
            }

        assertThat(exception).hasMessageThat().contains("Unexpected content")
        assertThat(exception.errorCode).isEqualTo(UpdateWatchFaceException.ERROR_UNEXPECTED_CONTENT)
    }

    @Test
    fun updateWatchFace_onInvalidPackageName() {
        val exception =
            assertThrows(UpdateWatchFaceException::class.java) {
                runBlocking {
                    readWatchFace(context, INVALID_PACKAGE_NAME_APK).use { pipe ->
                        wfp.updateWatchFace(
                            details.slotId,
                            pipe.readFd,
                            "XPdIIO1kCKDsA+lYKq2Mw5bjC1VEAK2kqUApZgA87Eg=:MS4wLjA="
                        )
                    }
                }
            }

        assertThat(exception.errorCode)
            .isEqualTo(UpdateWatchFaceException.ERROR_INVALID_PACKAGE_NAME)
        assertThat(exception).hasMessageThat().contains("package name")
    }
}
