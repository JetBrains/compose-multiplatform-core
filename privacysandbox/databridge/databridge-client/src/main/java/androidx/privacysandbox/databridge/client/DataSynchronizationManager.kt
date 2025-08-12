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
import androidx.privacysandbox.databridge.core.Key
import java.util.concurrent.Executor

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
 * [SyncCallback.onSyncFailure] method which should be registered through the
 * [DataSynchronizationManager.addSyncCallback] API
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
                    ?: DataSynchronizationManagerImpl(context.applicationContext).also {
                        instance = it
                    }
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
     * @param syncCallback: A callback to let the caller know about any failures during sync
     *   process.
     */
    public abstract fun addSyncCallback(executor: Executor, syncCallback: SyncCallback)

    /**
     * Remove a callback to stop receiving updates on data synchronization failures.
     *
     * @param syncCallback: [SyncCallback] callback which is to be unregistered.
     */
    public abstract fun removeSyncCallback(syncCallback: SyncCallback)

    private class DataSynchronizationManagerImpl(context: Context) : DataSynchronizationManager() {
        override fun addKeys(keyValueMap: Map<Key, Any?>) {
            TODO("Not yet implemented")
        }

        override fun getKeys(): Set<Key> {
            TODO("Not yet implemented")
        }

        override fun addSyncCallback(executor: Executor, syncCallback: SyncCallback) {
            TODO("Not yet implemented")
        }

        override fun removeSyncCallback(syncCallback: SyncCallback) {
            TODO("Not yet implemented")
        }
    }
}
