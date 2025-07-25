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

package androidx.wear.watchface.push.tests

import android.content.Context
import android.content.pm.PackageManager.GET_META_DATA
import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry
import androidx.wear.watchface.push.*
import java.io.FileOutputStream
import java.util.concurrent.TimeoutException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

internal const val SAMPLE_WATCHFACE =
    "androidx.wear.watchface.push.test.watchfacepush.androidxsample"
internal const val VALID_TOKEN = "aw83YwJ2kmKf6Gk1YpY43TeWm92/y6pB6ywa8xufxVw=:MS4wLjA="
internal const val INVALID_TOKEN = "an invalid token"
internal const val VALID_APK = "androidxsample.apk"
internal const val MALFORMED_APK = "androidxample_notanapk.apk"
internal const val UNSECURE_APK = "androidxsample_unsecure.apk"
internal const val INVALID_PACKAGE_NAME_APK = "androidxsample_invalid_package_name.apk"

internal fun CoroutineScope.readWatchFace(context: Context, packagePath: String): FdPipe {
    val (readFd, writeFd) = ParcelFileDescriptor.createPipe()
    launch(coroutineContext) {
        writeFd.use {
            context.assets.open(packagePath).use { inStream ->
                FileOutputStream(writeFd.fileDescriptor).use { outStream ->
                    inStream.transferTo(outStream)
                }
            }
        }
    }
    return FdPipe(readFd, writeFd)
}

internal data class FdPipe(
    val readFd: ParcelFileDescriptor,
    private val writeFd: ParcelFileDescriptor,
) : AutoCloseable {
    override fun close() {
        readFd.close()
        writeFd.close()
    }
}

internal suspend fun waitUntil(
    timeout: Long = 5000,
    interval: Long = 500,
    condition: suspend () -> Boolean
) {
    delay(interval)
    val startTime = System.currentTimeMillis()
    while (!condition() && System.currentTimeMillis() - startTime < timeout) {
        delay(interval)
    }
    if (!condition()) {
        throw TimeoutException("Condition not met within timeout")
    }
}

internal suspend fun isAppRunning(context: Context): Boolean {
    try {
        // If the service is down, any call will fail.
        WatchFacePushManager(context).listWatchFaces()
        return true
    } catch (_: Exception) {
        return false
    }
}

internal fun setup(context: Context, sampleWatchFace: String) {
    setup(context, listOf(sampleWatchFace))
}

internal fun setup(context: Context, sampleWatchFaces: List<String>) {

    // Clear storage for the receiver. It will cause the receiver to be stopped so we need
    // to wait for it to come back.
    InstrumentationRegistry.getInstrumentation()
        .uiAutomation
        .executeShellCommand("pm clear com.google.android.wearable.dwf.receiver")

    // Make sure there is no sample watch face left over
    for (sampleWatchface in sampleWatchFaces) {
        InstrumentationRegistry.getInstrumentation()
            .uiAutomation
            .executeShellCommand("pm uninstall $sampleWatchface")
    }

    // pm uninstall is asynchronous - make sure we wait until the watch face is really gone
    runBlocking {
        waitUntil { isAppRunning(context) }

        for (sampleWatchface in sampleWatchFaces) {
            waitUntil {
                !context.packageManager.getInstalledPackages(GET_META_DATA).any { n ->
                    n.equals(sampleWatchface)
                }
            }
        }
    }
}
