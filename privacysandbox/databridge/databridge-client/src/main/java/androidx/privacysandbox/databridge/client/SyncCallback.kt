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

import android.content.SharedPreferences
import androidx.annotation.IntDef
import androidx.annotation.RestrictTo
import androidx.privacysandbox.databridge.core.Key

/**
 * A callback to notify of synchronization failures performed by [DataSynchronizationManager].
 *
 * The callback needs to be registered using [DataSynchronizationManager.addSyncCallback] to receive
 * notifications.
 */
public interface SyncCallback {
    /**
     * Callback invoked whenever some key value pair fails to sync after being added for
     * synchronization using the [DataSynchronizationManager.addKeys]
     *
     * @param keyValueMap: A map of the key and associated values which failed to be synced
     * @param errorCode: [ErrorCode] specifying the error code
     * @param errorMessage: Error message for the failure
     */
    public fun onSyncFailure(
        keyValueMap: Map<Key, Any?>,
        @ErrorCode errorCode: Int,
        errorMessage: String,
    ) {}

    public companion object {
        /**
         * Indicates an error occurred when synchronizing an update from [DataBridgeClient] to the
         * app's default [SharedPreferences]
         */
        public const val ERROR_SYNCING_UPDATES_FROM_DATABRIDGE: Int = 1

        /**
         * Indicates an error occurred when synchronizing an update from the app's default
         * [SharedPreferences] to [DataBridgeClient]
         */
        public const val ERROR_SYNCING_UPDATES_FROM_SHARED_PREFERENCES: Int = 2

        /**
         * Indicates an error occurred when synchronizing the initial values passed to
         * [DataSynchronizationManager.addKeys]
         */
        public const val ERROR_ADDING_KEYS: Int = 3

        @RestrictTo(RestrictTo.Scope.LIBRARY)
        @Retention(AnnotationRetention.SOURCE)
        @IntDef(
            ERROR_SYNCING_UPDATES_FROM_SHARED_PREFERENCES,
            ERROR_SYNCING_UPDATES_FROM_DATABRIDGE,
            ERROR_ADDING_KEYS,
        )
        public annotation class ErrorCode
    }
}
