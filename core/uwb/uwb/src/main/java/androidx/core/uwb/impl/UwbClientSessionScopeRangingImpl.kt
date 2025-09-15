/*
 * Copyright (C) 2022 The Android Open Source Project
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

package androidx.core.uwb.impl

import android.annotation.SuppressLint
import android.os.Build
import android.ranging.DataNotificationConfig
import android.ranging.RangingData
import android.ranging.RangingDevice
import android.ranging.RangingManager
import android.ranging.RangingPreference
import android.ranging.RangingSession
import android.ranging.SensorFusionParams
import android.ranging.SessionConfig
import android.ranging.uwb.UwbRangingParams
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.uwb.RangingCapabilities
import androidx.core.uwb.RangingMeasurement
import androidx.core.uwb.RangingParameters
import androidx.core.uwb.RangingPosition
import androidx.core.uwb.RangingResult
import androidx.core.uwb.UwbAddress
import androidx.core.uwb.UwbClientSessionScope
import androidx.core.uwb.UwbDevice
import androidx.core.uwb.helper.handleApiException
import com.google.android.gms.common.api.ApiException
import java.util.Collections
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

@RequiresApi(Build.VERSION_CODES.BAKLAVA)
private fun convertUwbAndroidxAddressToUwbRangingAddress(
    androidxAddress: UwbAddress
): android.ranging.uwb.UwbAddress {
    return androidxAddress.let { android.ranging.uwb.UwbAddress.fromBytes(it.address) }
}

@RequiresApi(Build.VERSION_CODES.BAKLAVA)
internal abstract class UwbClientSessionScopeRangingImpl(
    private val mRangingManager: RangingManager,
    override val rangingCapabilities: RangingCapabilities,
    override val localAddress: UwbAddress,
) : UwbClientSessionScope {
    companion object {
        private const val TAG = "UwbClientSessionScope"
    }

    private val mExecutor: Executor = Executors.newSingleThreadExecutor()
    private var sessionStarted = false
    protected var mRangingParams: RangingParameters? = null
    val mAddressDeviceMap: MutableMap<UwbAddress, RangingDevice> =
        Collections.synchronizedMap(mutableMapOf())
    protected var mRangingSession: RangingSession? = null

    protected abstract fun buildRangingPreference(parameters: RangingParameters): RangingPreference

    @SuppressLint("MissingPermission")
    override fun prepareSession(parameters: RangingParameters): Flow<RangingResult> = callbackFlow {
        if (sessionStarted) {
            throw IllegalStateException(
                "Ranging has already started. To initiate " +
                    "a new ranging session, create a new client session scope."
            )
        }

        mRangingParams = parameters
        val rangingPreference = buildRangingPreference(parameters)

        val rangingSessionCallback =
            object : RangingSession.Callback {
                override fun onClosed(reason: Int) {
                    channel.close()
                }

                override fun onOpenFailed(reason: Int) {
                    channel.close(
                        IllegalStateException("Ranging session open failed with reason: $reason")
                    )
                }

                override fun onOpened() {
                    val result =
                        trySend(RangingResult.RangingResultInitialized(UwbDevice(localAddress)))
                    if (!result.isSuccess) {
                        Log.e(TAG, "Failed to send onOpened event: $result")
                    }
                }

                override fun onResults(peer: RangingDevice, data: RangingData) {

                    val uwbAddress = mAddressDeviceMap.entries.find { it.value == peer }?.key

                    if (uwbAddress == null) {
                        throw IllegalStateException("UwbAddress not found for peer: ${peer.uuid}")
                    }
                    uwbAddress.let {
                        val result =
                            trySend(
                                RangingResult.RangingResultPosition(
                                    UwbDevice(it),
                                    RangingPosition(
                                        data.distance?.measurement?.let { it1 ->
                                            RangingMeasurement(it1.toFloat())
                                        },
                                        data.azimuth?.measurement?.let { it1 ->
                                            RangingMeasurement(it1.toFloat())
                                        },
                                        data.elevation?.measurement?.let { it1 ->
                                            RangingMeasurement(it1.toFloat())
                                        },
                                        data.timestampMillis * 1_000_000L,
                                    ),
                                )
                            )
                        if (!result.isSuccess) {
                            Log.e(TAG, "Failed to send onResults event: $result")
                        }
                    }
                }

                override fun onStarted(peer: RangingDevice, technology: Int) {
                    Log.v(TAG, "Ranging has started for peer: $peer")
                    //                    val uwbAddress = mAddressDeviceMap.entries.find { it.value
                    // == peer }?.key
                    //                    uwbAddress?.let {
                    //                        val result =
                    // trySend(RangingResult.RangingResultInitialized(UwbDevice(it)))
                    //                        if (result.isSuccess) {
                    //                            Log.d(TAG, "Successfully sent onStarted event:
                    // $result")
                    //                        } else {
                    //                            Log.e(
                    //                                TAG,
                    //                                "Failed to send onStarted event: $result",
                    //                                result.exceptionOrNull(),
                    //                            )
                    //                        }
                    //                    }
                }

                override fun onStopped(peer: RangingDevice, technology: Int) {

                    val uwbAddress = mAddressDeviceMap.entries.find { it.value == peer }?.key
                    if (uwbAddress == null) {
                        throw IllegalStateException("UwbAddress not found for peer: ${peer.uuid}")
                    }

                    val result =
                        trySend(RangingResult.RangingResultPeerDisconnected(UwbDevice(uwbAddress)))
                    if (!result.isSuccess) {
                        Log.e(
                            TAG,
                            "Failed to send onStopped event: $result",
                            result.exceptionOrNull(),
                        )
                    }
                }
            }

        try {
            mRangingSession =
                mRangingManager.createRangingSession(mExecutor, rangingSessionCallback)
            mRangingSession?.start(rangingPreference)
            sessionStarted = true
        } catch (e: Exception) {
            throw IllegalStateException("Failed to start ranging session, with Exception: $e")
        }

        awaitClose {
            CoroutineScope(Dispatchers.Main.immediate).launch {
                try {
                    if (sessionStarted) {
                        mRangingSession?.stop()
                        sessionStarted = false
                    } else {
                        Log.d(TAG, "Ranging session already stopped")
                    }
                } catch (e: ApiException) {
                    handleApiException(e)
                }
            }
        }
    }

    override suspend fun reconfigureRangeDataNtf(
        configType: Int,
        proximityNear: Int,
        proximityFar: Int,
    ) {
        throw IllegalStateException("Reconfiguring data ntf dynamically is not supported.")
    }

    @RequiresApi(Build.VERSION_CODES.BAKLAVA)
    fun buildUwbRangingParams(
        peerAddress: UwbAddress,
        subSessionId: Int = mRangingParams!!.subSessionId,
        subSessionKeyInfo: ByteArray? = mRangingParams!!.subSessionKeyInfo,
    ): UwbRangingParams {

        return UwbRangingParams.Builder(
                mRangingParams!!.sessionId,
                mRangingParams!!.uwbConfigType,
                convertUwbAndroidxAddressToUwbRangingAddress(localAddress),
                convertUwbAndroidxAddressToUwbRangingAddress(peerAddress),
            )
            .apply {
                mRangingParams?.complexChannel?.let { complexChannel ->
                    setComplexChannel(
                        android.ranging.uwb.UwbComplexChannel.Builder()
                            .setChannel(complexChannel.channel)
                            .setPreambleIndex(complexChannel.preambleIndex)
                            .build()
                    )
                }
            }
            .apply {
                mRangingParams?.sessionKeyInfo?.let { sessionKeyInfo ->
                    setSessionKeyInfo(sessionKeyInfo)
                }
            }
            .setSubSessionId(subSessionId)
            .apply { subSessionKeyInfo?.let { setSubSessionKeyInfo(it) } }
            .setRangingUpdateRate(mRangingParams!!.updateRateType)
            .setSlotDuration(
                if (
                    mRangingParams!!.slotDurationMillis ==
                        RangingParameters.Companion.RANGING_SLOT_DURATION_1_MILLIS
                ) {
                    UwbRangingParams.DURATION_1_MS
                } else UwbRangingParams.DURATION_2_MS
            )
            .build()
    }

    fun buildSessionConfig(): SessionConfig {
        val sessionConfigBuilder =
            SessionConfig.Builder()
                .setAngleOfArrivalNeeded(!mRangingParams!!.isAoaDisabled)
                .setSensorFusionParams(
                    SensorFusionParams.Builder().setSensorFusionEnabled(true).build()
                )

        mRangingParams!!.uwbRangeDataNtfConfig?.let { ntfConfig ->
            val dataNtfConfig =
                DataNotificationConfig.Builder()
                    .setNotificationConfigType(ntfConfig.configType)
                    .setProximityNearCm(ntfConfig.ntfProximityNearCm)
                    .setProximityFarCm(ntfConfig.ntfProximityFarCm)
                    .build()
            sessionConfigBuilder.setDataNotificationConfig(dataNtfConfig)
        }

        return sessionConfigBuilder.build()
    }
}
