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

package androidx.glance.wear.parcel

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.RemoteException
import android.util.Log
import androidx.glance.wear.parcel.legacy.TileUpdateRequestData
import androidx.glance.wear.parcel.legacy.TileUpdateRequesterService
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

internal class WidgetUpdateClientImpl(
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : WidgetUpdateClient {
    private val binderMutex = Mutex()

    // Writes must be guarded by binderMutex.
    @Volatile
    private var legacyBinder: WidgetUpdateBinder<TileUpdateRequesterService, ComponentName>? = null
    private val scope = CoroutineScope(dispatcher + SupervisorJob())

    override fun sendUpdateBroadcast(context: Context, provider: ComponentName) {
        val intent =
            Intent(ACTION_REQUEST_TILE_UPDATE_BROADCAST_LEGACY).apply {
                putExtra(Intent.EXTRA_COMPONENT_NAME, provider)
            }
        context.sendBroadcast(intent)
    }

    override fun requestUpdate(context: Context, provider: ComponentName) {
        scope.launch {
            try {
                withTimeout(UPDATE_TIMEOUT) {
                    val binder =
                        legacyBinder
                            ?: binderMutex.withLock {
                                legacyBinder
                                    ?: createLegacyBinder(context.applicationContext, dispatcher)
                                        .also { legacyBinder = it }
                            }
                    binder.requestUpdate(provider)
                }
            } catch (ex: Exception) {
                Log.e(TAG, "Failed to request widget update", ex)
            }
        }
    }

    internal companion object {
        const val TAG = "WidgetUpdateClientImpl"
        const val ACTION_BIND_UPDATE_REQUESTER_LEGACY =
            "androidx.wear.tiles.action.BIND_UPDATE_REQUESTER"
        private val UPDATE_TIMEOUT = 10.seconds

        /** Intent action to broadcast debugging update requests. */
        internal const val ACTION_REQUEST_TILE_UPDATE_BROADCAST_LEGACY =
            "androidx.wear.tiles.action.REQUEST_TILE_UPDATE"

        /** Create a binder that can be used to request updates from the legacy Tile Renderer. */
        fun createLegacyBinder(
            context: Context,
            dispatcher: CoroutineDispatcher,
        ): WidgetUpdateBinder<TileUpdateRequesterService, ComponentName> =
            WidgetUpdateBinder(
                context = context,
                action = ACTION_BIND_UPDATE_REQUESTER_LEGACY,
                asInterface = { TileUpdateRequesterService.Stub.asInterface(it) },
                dispatcher = dispatcher,
                sendRequest = { service, componentName ->
                    try {
                        service.requestUpdate(componentName, TileUpdateRequestData())
                    } catch (ex: RemoteException) {
                        Log.e(TAG, "while requesting widget update", ex)
                    }
                },
            )
    }
}
