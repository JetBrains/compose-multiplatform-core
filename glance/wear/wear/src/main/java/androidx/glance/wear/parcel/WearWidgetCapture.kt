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
@file:OptIn(ExperimentalRemoteCreationApi::class, ExperimentalRemoteCreationComposeApi::class)

package androidx.glance.wear.parcel

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.os.Bundle
import androidx.compose.remote.creation.CreationDisplayInfo
import androidx.compose.remote.creation.ExperimentalRemoteCreationApi
import androidx.compose.remote.creation.compose.ExperimentalRemoteCreationComposeApi
import androidx.compose.remote.creation.compose.capture.WriterEvents
import androidx.compose.remote.creation.compose.capture.captureSingleRemoteDocument
import androidx.compose.remote.creation.compose.layout.RemoteComposable
import androidx.compose.remote.creation.profile.RcPlatformProfiles
import androidx.compose.runtime.Composable
import androidx.glance.wear.WearWidgetRawContent
import kotlinx.coroutines.ExperimentalCoroutinesApi

internal object WearWidgetCapture {
    const val PENDING_INTENT_KEY = "pending_intents"

    /**
     * Directly capture a RemoteCompose document and gather the pending intents used in the layout.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    internal suspend fun capture(
        context: Context,
        creationDisplayInfo: CreationDisplayInfo,
        content: @Composable @RemoteComposable () -> Unit,
    ): WearWidgetRawContent {
        val writerEvents = WidgetPendingIntents()
        val remoteDocument =
            captureSingleRemoteDocument(
                context,
                creationDisplayInfo,
                RcPlatformProfiles.WEAR_WIDGETS,
                writerEvents,
                content,
            )
        return WearWidgetRawContent(
            rcDocument = remoteDocument,
            extras = Bundle().addPendingIntents(writerEvents),
        )
    }

    private fun Bundle.addPendingIntents(widgetPendingIntents: WidgetPendingIntents): Bundle {
        putParcelable(WearWidgetCapture.PENDING_INTENT_KEY, widgetPendingIntents.toBundle())
        return this
    }
}

/** The collected [PendingIntent] on the widget, to be sent as sidecar bundle with the document. */
@SuppressLint("RestrictedApiAndroidX")
internal class WidgetPendingIntents : WriterEvents {
    private val pendingIntentList: MutableList<PendingIntent> = mutableListOf()

    override fun storePendingIntent(pendingIntent: PendingIntent): Int {
        val existingIndex = pendingIntentList.indexOfFirst { it === pendingIntent }
        if (existingIndex != -1) {
            return existingIndex
        }

        pendingIntentList.add(pendingIntent)
        return pendingIntentList.lastIndex
    }

    override fun onDocumentAvailable(documentBytes: ByteArray) {
        // Not used currently, in favour of result of captureSingleRemoteDocument
    }

    fun get(index: Int): PendingIntent? = pendingIntentList.getOrNull(index)

    fun size() = pendingIntentList.size

    fun toBundle() =
        Bundle().apply {
            for (i in pendingIntentList.indices) {
                putParcelable("$i", get(i))
            }
        }
}
