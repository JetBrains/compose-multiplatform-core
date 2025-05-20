/*
 * Copyright 2022 The Android Open Source Project
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

package androidx.camera.integration.core.util

import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.os.Build
import androidx.annotation.OptIn
import androidx.camera.camera2.Camera2Config
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.camera2.pipe.integration.CameraPipeConfig
import androidx.camera.camera2.pipe.integration.interop.Camera2CameraControl as CPCamera2CameraControl
import androidx.camera.camera2.pipe.integration.interop.Camera2CameraInfo as CPCamera2CameraInfo
import androidx.camera.camera2.pipe.integration.interop.Camera2Interop as CPCamera2Interop
import androidx.camera.camera2.pipe.integration.interop.CaptureRequestOptions as CPCaptureRequestOptions
import androidx.camera.camera2.pipe.integration.interop.ExperimentalCamera2Interop as CPExperimentalCamera2Interop
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraInfo
import androidx.camera.core.ExtendableBuilder
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

@kotlin.OptIn(CPExperimentalCamera2Interop::class)
@OptIn(markerClass = [ExperimentalCamera2Interop::class])
object Camera2InteropUtil {

    @JvmStatic
    fun <T> setCamera2InteropOptions(
        implName: String,
        builder: ExtendableBuilder<T>,
        captureCallback: CameraCaptureSession.CaptureCallback? = null,
        deviceStateCallback: CameraDevice.StateCallback? = null,
        sessionStateCallback: CameraCaptureSession.StateCallback? = null,
        physicalCameraId: String? = null
    ) {
        if (implName == CameraPipeConfig::class.simpleName) {
            val extendedBuilder = CPCamera2Interop.Extender(builder)
            captureCallback?.let { extendedBuilder.setSessionCaptureCallback(it) }
            deviceStateCallback?.let { extendedBuilder.setDeviceStateCallback(it) }
            sessionStateCallback?.let { extendedBuilder.setSessionStateCallback(it) }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && physicalCameraId != null) {
                extendedBuilder.setPhysicalCameraId(physicalCameraId)
            }
        } else {
            val extendedBuilder = Camera2Interop.Extender(builder)
            captureCallback?.let { extendedBuilder.setSessionCaptureCallback(it) }
            deviceStateCallback?.let { extendedBuilder.setDeviceStateCallback(it) }
            sessionStateCallback?.let { extendedBuilder.setSessionStateCallback(it) }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && physicalCameraId != null) {
                extendedBuilder.setPhysicalCameraId(physicalCameraId)
            }
        }
    }

    @JvmStatic
    fun <T> setCameraCaptureSessionCallback(
        implName: String,
        builder: ExtendableBuilder<T>,
        captureCallback: CameraCaptureSession.CaptureCallback
    ) {
        setCamera2InteropOptions(
            implName = implName,
            builder = builder,
            captureCallback = captureCallback
        )
    }

    @JvmStatic
    fun <T> setDeviceStateCallback(
        implName: String,
        builder: ExtendableBuilder<T>,
        stateCallback: CameraDevice.StateCallback
    ) {
        setCamera2InteropOptions(
            implName = implName,
            builder = builder,
            deviceStateCallback = stateCallback
        )
    }

    @JvmStatic
    fun <T> setSessionStateCallback(
        implName: String,
        builder: ExtendableBuilder<T>,
        stateCallback: CameraCaptureSession.StateCallback
    ) {
        setCamera2InteropOptions(
            implName = implName,
            builder = builder,
            sessionStateCallback = stateCallback
        )
    }

    @JvmStatic
    fun getCameraId(implName: String, cameraInfo: CameraInfo): String =
        if (implName == CameraPipeConfig::class.simpleName) {
            CPCamera2CameraInfo.from(cameraInfo).getCameraId()
        } else {
            Camera2CameraInfo.from(cameraInfo).cameraId
        }

    @JvmStatic
    fun <T> getCamera2CameraInfoCharacteristics(
        implName: String,
        cameraInfo: CameraInfo,
        key: CameraCharacteristics.Key<T>
    ): T? =
        if (implName == Camera2Config::class.simpleName) {
            Camera2CameraInfo.from(cameraInfo).getCameraCharacteristic(key)
        } else {
            CPCamera2CameraInfo.from(cameraInfo).getCameraCharacteristic(key)
        }

    interface Camera2CameraInfoWrapper {
        fun <T> getCameraCharacteristic(key: CameraCharacteristics.Key<T>): T?

        companion object
    }

    interface Camera2CameraControlWrapper {
        fun setCaptureRequestOptions(bundle: CaptureRequestOptionsWrapper): ListenableFuture<Void?>

        fun clearCaptureRequestOptions(): ListenableFuture<Void?>

        companion object
    }

    @JvmStatic
    fun Camera2CameraInfoWrapper.Companion.from(
        implName: String,
        cameraInfo: CameraInfo
    ): Camera2CameraInfoWrapper {
        return when (implName) {
            CameraPipeConfig::class.simpleName ->
                object : Camera2CameraInfoWrapper {
                    private val wrappedCameraInfo = CPCamera2CameraInfo.from(cameraInfo)

                    override fun <T> getCameraCharacteristic(
                        key: CameraCharacteristics.Key<T>
                    ): T? {
                        return wrappedCameraInfo.getCameraCharacteristic(key)
                    }
                }
            Camera2Config::class.simpleName ->
                object : Camera2CameraInfoWrapper {
                    private val wrappedCameraInfo = Camera2CameraInfo.from(cameraInfo)

                    override fun <T> getCameraCharacteristic(
                        key: CameraCharacteristics.Key<T>
                    ): T? {
                        return wrappedCameraInfo.getCameraCharacteristic(key)
                    }
                }
            else -> throw IllegalArgumentException("Unexpected implementation: $implName")
        }
    }

    interface CaptureRequestOptionsWrapper {

        fun unwrap(): Any

        interface Builder {
            fun <ValueT : Any> setCaptureRequestOption(
                key: CaptureRequest.Key<ValueT>,
                value: ValueT
            ): Builder

            fun build(): CaptureRequestOptionsWrapper
        }

        companion object
    }

    @JvmStatic
    fun CaptureRequestOptionsWrapper.Companion.builder(
        implName: String
    ): CaptureRequestOptionsWrapper.Builder {
        return when (implName) {
            CameraPipeConfig::class.simpleName ->
                object : CaptureRequestOptionsWrapper.Builder {
                    private val wrappedBuilder = CPCaptureRequestOptions.Builder()

                    override fun <ValueT : Any> setCaptureRequestOption(
                        key: CaptureRequest.Key<ValueT>,
                        value: ValueT
                    ): CaptureRequestOptionsWrapper.Builder {
                        wrappedBuilder.setCaptureRequestOption(key, value)
                        return this
                    }

                    override fun build(): CaptureRequestOptionsWrapper {
                        val wrappedOptions = wrappedBuilder.build()
                        return object : CaptureRequestOptionsWrapper {
                            override fun unwrap() = wrappedOptions
                        }
                    }
                }
            Camera2Config::class.simpleName ->
                object : CaptureRequestOptionsWrapper.Builder {
                    private val wrappedBuilder = CaptureRequestOptions.Builder()

                    override fun <ValueT : Any> setCaptureRequestOption(
                        key: CaptureRequest.Key<ValueT>,
                        value: ValueT
                    ): CaptureRequestOptionsWrapper.Builder {
                        wrappedBuilder.setCaptureRequestOption(key, value)
                        return this
                    }

                    override fun build(): CaptureRequestOptionsWrapper {
                        val wrappedOptions = wrappedBuilder.build()
                        return object : CaptureRequestOptionsWrapper {
                            override fun unwrap() = wrappedOptions
                        }
                    }
                }
            else -> throw IllegalArgumentException("Unexpected implementation: $implName")
        }
    }

    @JvmStatic
    fun Camera2CameraControlWrapper.Companion.from(
        implName: String,
        cameraControl: CameraControl
    ): Camera2CameraControlWrapper {
        return when (implName) {
            CameraPipeConfig::class.simpleName ->
                object : Camera2CameraControlWrapper {
                    private val wrappedCameraControl = CPCamera2CameraControl.from(cameraControl)

                    override fun setCaptureRequestOptions(
                        bundle: CaptureRequestOptionsWrapper
                    ): ListenableFuture<Void?> {
                        return wrappedCameraControl.setCaptureRequestOptions(
                            bundle.unwrap() as CPCaptureRequestOptions
                        )
                    }

                    override fun clearCaptureRequestOptions(): ListenableFuture<Void?> {
                        return wrappedCameraControl.clearCaptureRequestOptions()
                    }
                }
            Camera2Config::class.simpleName ->
                object : Camera2CameraControlWrapper {
                    private val wrappedCameraControl = Camera2CameraControl.from(cameraControl)

                    override fun setCaptureRequestOptions(
                        bundle: CaptureRequestOptionsWrapper
                    ): ListenableFuture<Void?> {
                        return wrappedCameraControl.setCaptureRequestOptions(
                            bundle.unwrap() as CaptureRequestOptions
                        )
                    }

                    override fun clearCaptureRequestOptions(): ListenableFuture<Void?> {
                        return wrappedCameraControl.clearCaptureRequestOptions()
                    }
                }
            else -> throw IllegalArgumentException("Unexpected implementation: $implName")
        }
    }

    class CaptureCallback(
        val _timeout: Long = TimeUnit.SECONDS.toMillis(5),
        val _numOfCaptures: Int = 1
    ) : CameraCaptureSession.CaptureCallback() {

        val waitingList = mutableListOf<CaptureContainer>()

        /** Wait for the specified number of captures, then verify the results. */
        suspend fun waitFor(
            timeout: Long = _timeout,
            numOfCaptures: Int = _numOfCaptures,
        ) {
            verifyFor(timeout = timeout, numOfCaptures = numOfCaptures, breakWhenSuccess = false)
        }

        /**
         * Verify the results until the specified number of captures reaches.
         *
         * @param timeout the timeout for waiting for the captures.
         * @param numOfCaptures the number of captures to wait.
         * @param verifyBlock the block for verifying the capture requests and results. It should
         *   return `true` if the requests and results is expected, otherwise `false`.
         */
        suspend fun verifyFor(
            timeout: Long = _timeout,
            numOfCaptures: Int = _numOfCaptures,
            breakWhenSuccess: Boolean = true,
            verifyBlock:
                (
                    captureRequests: List<CaptureRequest>, captureResults: List<TotalCaptureResult>
                ) -> Boolean =
                { _, _ ->
                    true
                }
        ) {
            val resultContainer =
                CaptureContainer(
                    count = numOfCaptures,
                    breakWhenSuccess = breakWhenSuccess,
                    verifyBlock = verifyBlock
                )
            waitingList.add(resultContainer)
            withTimeout(timeout) { resultContainer.signal.await() }
            waitingList.remove(resultContainer)
        }

        fun <T> verifyLastCaptureRequest(
            keyValueMap: Map<CaptureRequest.Key<T>, T>,
            numOfCaptures: Int = 30
        ) = runBlocking {
            verifyFor(numOfCaptures = numOfCaptures) { captureRequests, _ ->
                keyValueMap.forEach {
                    if (captureRequests.last()[it.key] != it.value) return@verifyFor false
                }
                true
            }
        }

        fun <T> verifyLastCaptureResult(
            keyValueMap: Map<CaptureResult.Key<T>, T>,
            numOfCaptures: Int = 30
        ) = runBlocking {
            verifyFor(numOfCaptures = numOfCaptures) { _, captureResults ->
                keyValueMap.forEach {
                    if (captureResults.last()[it.key] != it.value) return@verifyFor false
                }
                true
            }
        }

        override fun onCaptureCompleted(
            session: CameraCaptureSession,
            request: CaptureRequest,
            result: TotalCaptureResult
        ) {
            val copyList = waitingList
            copyList.toList().forEach {
                it.apply {
                    captureRequests.add(request)
                    captureResults.add(result)
                    val success = verifyBlock(captureRequests, captureResults)
                    if (success && breakWhenSuccess) {
                        signal.complete(Unit)
                        return@forEach
                    }
                    if (count-- <= 0) {
                        if (success) {
                            signal.complete(Unit)
                        } else {
                            signal.completeExceptionally(
                                TimeoutException(
                                    "Test doesn't complete after waiting for $_numOfCaptures frames."
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    data class CaptureContainer(
        var count: Int,
        val breakWhenSuccess: Boolean = true,
        val signal: CompletableDeferred<Unit> = CompletableDeferred(),
        val captureRequests: MutableList<CaptureRequest> = mutableListOf(),
        val captureResults: MutableList<TotalCaptureResult> = mutableListOf(),
        val verifyBlock:
            (
                captureRequests: List<CaptureRequest>, captureResults: List<TotalCaptureResult>
            ) -> Boolean =
            { _, _ ->
                true
            }
    )
}
