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

package androidx.privacysandbox.databridge.client

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.GuardedBy
import androidx.annotation.VisibleForTesting
import androidx.preference.PreferenceManager
import androidx.privacysandbox.databridge.client.SyncFailureCallback.Companion.ERROR_ADDING_KEYS
import androidx.privacysandbox.databridge.client.SyncFailureCallback.Companion.ERROR_SYNCING_UPDATES_FROM_DATABRIDGE
import androidx.privacysandbox.databridge.client.SyncFailureCallback.Companion.ERROR_SYNCING_UPDATES_FROM_SHARED_PREFERENCES
import androidx.privacysandbox.databridge.client.SyncFailureCallback.Companion.ErrorCode
import androidx.privacysandbox.databridge.core.Key
import androidx.privacysandbox.databridge.core.Type
import java.lang.Exception
import java.util.concurrent.Executor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * This class facilitates the sync between app's default [SharedPreferences] and [DataBridgeClient].
 * The syncing will be done only for the keys which are added using the
 * [DataSynchronizationManager.addKeys] APIs.
 *
 * When data in app's default [SharedPreferences] changes, [DataSynchronizationManager] updates
 * [DataBridgeClient] to reflect those changes, and vice-versa.
 *
 * **Note:** The synchronization happens on a best-effort basis and it is possible for data to be
 * out of sync due to failures. Any sync failures will be reported using the
 * [SyncFailureCallback.onSyncFailure] method which should be registered through the
 * [DataSynchronizationManager.addSyncFailureCallback] API
 */
public abstract class DataSynchronizationManager private constructor() {
    public companion object {
        private val instanceLock = Any()
        @GuardedBy("instanceLock") private var instance: DataSynchronizationManager? = null

        /**
         * Get an instance of [DataSynchronizationManager].
         *
         * @param context: Application Context. If anything other [Context] instance is passed, it
         *   will be converted to application context using [Context.getApplicationContext]
         * @return DataSynchronizationManager instance
         */
        @JvmStatic
        public fun getInstance(context: Context): DataSynchronizationManager {
            synchronized(instanceLock) {
                return instance
                    ?: DataSynchronizationManagerImpl(
                            context.applicationContext,
                            CoroutineScope(Dispatchers.IO),
                        )
                        .also { instance = it }
            }
        }

        @JvmStatic
        @VisibleForTesting
        internal fun getInstance(
            context: Context,
            scope: CoroutineScope,
        ): DataSynchronizationManager {
            synchronized(instanceLock) {
                instance = DataSynchronizationManagerImpl(context.applicationContext, scope)
                return instance!!
            }
        }
    }

    /**
     * Add to the set of keys which needs to be written to [DataBridgeClient] and
     * [SharedPreferences]. This operation also propagates the value for the keys to
     * [DataBridgeClient] and [SharedPreferences]
     *
     * The type of value supported for synchronisation are [Int], [Long], [Float], [Boolean],
     * [String], [Set<String>]. [DataBridgeClient] supports [Double] and [ByteArray] which
     * [SharedPreferences] does not. In case the input contains key of type [Double] or [ByteArray],
     * an [IllegalArgumentException] will be raised
     *
     * @param keyValueMap: A map of the [Key] and associated initial values to be set
     * @throws IllegalArgumentException if any key in the map is of type [Double] or [ByteArray]
     */
    public abstract fun addKeys(keyValueMap: Map<Key, Any?>)

    /**
     * Retrieve the set of keys which are being synced.
     *
     * @return Set of [Key] which are being synced
     */
    public abstract fun getKeys(): Set<Key>

    /**
     * Add a callback to receive notifications of data synchronization failures.
     *
     * @param executor: The executor from which the callback is executed.
     * @param syncFailureCallback: A callback to let the caller know about any failures during sync
     *   process.
     */
    public abstract fun addSyncFailureCallback(
        executor: Executor,
        syncFailureCallback: SyncFailureCallback,
    )

    /**
     * Remove a callback to stop receiving updates on data synchronization failures.
     *
     * @param syncFailureCallback: [SyncFailureCallback] callback which is to be unregistered.
     */
    public abstract fun removeSyncFailureCallback(syncFailureCallback: SyncFailureCallback)

    private class DataSynchronizationManagerImpl(context: Context, val scope: CoroutineScope) :
        DataSynchronizationManager() {

        private val dataBridgeClient: DataBridgeClient = DataBridgeClient.getInstance(context)

        private val sharedPreference: SharedPreferences =
            PreferenceManager.getDefaultSharedPreferences(context)

        private val syncFailureCallbackAndExecutorList =
            mutableSetOf<Pair<SyncFailureCallback, Executor>>()

        @GuardedBy("keySet") private val keySet = mutableSetOf<Key>()

        companion object {
            private const val SOURCE_ADD_KEYS = "addKeys"
            private const val SOURCE_SHARED_PREFERENCE = "sharedPreference"
            private const val SOURCE_DATABRIDGE = "dataBridge"
        }

        override fun addKeys(keyValueMap: Map<Key, Any?>) {
            if (keyValueMap.keys.any { it.type == Type.DOUBLE || it.type == Type.BYTE_ARRAY }) {
                throw IllegalArgumentException(
                    "Invalid type. Double and ByteArray not supported for synchronization"
                )
            }

            synchronized(keySet) { keySet.addAll(keyValueMap.keys) }

            updateDataBridgeClient(keyValueMap, SOURCE_ADD_KEYS)
            updateSharedPreference(keyValueMap, SOURCE_ADD_KEYS)
        }

        override fun getKeys(): Set<Key> {
            synchronized(keySet) {
                return keySet.toSet()
            }
        }

        override fun addSyncFailureCallback(
            executor: Executor,
            syncFailureCallback: SyncFailureCallback,
        ) {
            syncFailureCallbackAndExecutorList.add(Pair(syncFailureCallback, executor))
        }

        override fun removeSyncFailureCallback(syncFailureCallback: SyncFailureCallback) {
            syncFailureCallbackAndExecutorList.removeAll { it -> it.first == syncFailureCallback }
        }

        @ErrorCode
        private fun getErrorCodeFromSource(source: String): Int {
            return when (source) {
                SOURCE_ADD_KEYS -> ERROR_ADDING_KEYS
                SOURCE_SHARED_PREFERENCE -> ERROR_SYNCING_UPDATES_FROM_SHARED_PREFERENCES
                SOURCE_DATABRIDGE -> ERROR_SYNCING_UPDATES_FROM_DATABRIDGE
                else -> {
                    throw IllegalStateException()
                }
            }
        }

        private fun updateDataBridgeClient(keyValueMap: Map<Key, Any?>, source: String) {
            scope.launch {
                try {
                    dataBridgeClient.setValues(keyValueMap)
                } catch (exception: Exception) {
                    sendSyncFailure(
                        keyValueMap,
                        getErrorCodeFromSource(source),
                        exception.message.toString(),
                    )
                }
            }
        }

        @Suppress("UNCHECKED_CAST")
        private fun updateSharedPreference(keyValueMap: Map<Key, Any?>, source: String) {

            val editor = sharedPreference.edit()

            try {
                keyValueMap.forEach { (key, value) ->
                    when (key.type) {
                        Type.INT -> editor.putInt(key.name, value as Int)
                        Type.LONG -> editor.putLong(key.name, value as Long)
                        Type.FLOAT -> editor.putFloat(key.name, value as Float)
                        Type.BOOLEAN -> editor.putBoolean(key.name, value as Boolean)
                        Type.STRING -> editor.putString(key.name, value as String)
                        Type.STRING_SET -> editor.putStringSet(key.name, parseStringSetData(value))
                        Type.DOUBLE,
                        Type.BYTE_ARRAY -> {
                            throw IllegalStateException("Data type not supported")
                        }
                    }
                }
            } catch (exception: Exception) {
                sendSyncFailure(
                    keyValueMap,
                    getErrorCodeFromSource(source),
                    exception.message.toString(),
                )
            }
            scope.launch {
                if (!editor.commit()) {
                    sendSyncFailure(
                        keyValueMap,
                        getErrorCodeFromSource(source),
                        "Failed to update SharedPreference",
                    )
                }
            }
        }

        private fun sendSyncFailure(
            keyValueMap: Map<Key, Any?>,
            @ErrorCode errorCode: Int,
            errorMessage: String,
        ) {
            syncFailureCallbackAndExecutorList.forEach { (syncFailureCallback, executor) ->
                executor.execute {
                    syncFailureCallback.onSyncFailure(keyValueMap, errorCode, errorMessage)
                }
            }
        }

        private fun parseStringSetData(value: Any?): Set<String> {
            return if (value is Iterable<*>) {
                value.filterIsInstance<String>().toSet()
            } else {
                emptySet()
            }
        }
    }
}
