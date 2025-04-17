/*
 * Copyright 2023 The Android Open Source Project
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
package androidx.wear.remote.interactions

import android.content.Context
import android.net.Uri
import android.os.OutcomeReceiver
import androidx.annotation.ChecksSdkIntAtLeast
import com.google.wear.Sdk
import com.google.wear.services.remoteinteractions.RemoteInteractionsManager
import java.util.concurrent.Executor
import java.util.function.Consumer

/** Forwards remote interactions to [RemoteInteractionsManager]. */
internal open class RemoteInteractionsManagerCompat(context: Context) : IRemoteInteractionsManager {

    // TODO(b/307543793): Reuse the generalized `WearApiVersionHelper` once available.
    private val wearApiVersion: WearApiVersion = WearApiVersion(context)

    private val remoteInteractionsManager: RemoteInteractionsManager? =
        if (isAvailabilityStatusApiSupported)
            Sdk.getWearManager(context, RemoteInteractionsManager::class.java)
        else null

    override val isAvailabilityStatusApiSupported: Boolean
        get() = wearApiVersion.wearSdkVersion >= 4

    @get:ChecksSdkIntAtLeast(api = 36)
    override val isStartRemoteActivityApiSupported: Boolean
        get() = wearApiVersion.wearSdkVersion >= 6

    override fun registerRemoteActivityHelperStatusListener(
        executor: Executor,
        listener: Consumer<Int>
    ) {
        if (isAvailabilityStatusApiSupported) {
            remoteInteractionsManager!!.registerRemoteActivityHelperStatusListener(
                executor,
                listener
            )
        } else {
            throw UnsupportedOperationException("Should not call wear sdk when not supported.")
        }
    }

    override fun unregisterRemoteActivityHelperStatusListener(listener: Consumer<Int>) {
        if (isAvailabilityStatusApiSupported) {
            remoteInteractionsManager!!.unregisterRemoteActivityHelperStatusListener(listener)
        } else {
            throw UnsupportedOperationException("Should not call wear sdk when not supported.")
        }
    }

    override fun startRemoteActivity(
        dataUri: Uri,
        additionalCategories: List<String>,
        executor: Executor,
        outcomeReceiver: OutcomeReceiver<Void, Throwable>
    ) {
        if (isStartRemoteActivityApiSupported) {
            remoteInteractionsManager!!.startRemoteActivity(
                dataUri,
                additionalCategories,
                executor,
                outcomeReceiver
            )
        } else {
            throw UnsupportedOperationException("Should not call wear sdk when not supported.")
        }
    }
}
