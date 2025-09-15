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

package androidx.glance.wear.data

import android.content.Context
import androidx.glance.wear.GlanceWearWidget
import androidx.glance.wear.WearWidgetRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Implementation of the [IWearWidgetProvider] Stub.
 *
 * @property context The Context of the service that communicates using this Stub
 * @property mainScope A main-thread scope
 * @property widget The widget used to receive the calls to this stub
 */
internal class WearWidgetProviderImpl(
    private val context: Context,
    private val mainScope: CoroutineScope,
    private val widget: GlanceWearWidget,
) : IWearWidgetProvider.Stub() {

    override fun getApiVersion(): Int = API_VERSION

    override fun onWidgetRequest(
        requestData: WearWidgetRequestData?,
        callback: IWearWidgetCallback?,
    ) {
        requireNotNull(requestData) { "Invalid widget request." }
        requireNotNull(callback) { "Invalid widget callback." }
        mainScope.launch {
            val request = WearWidgetRequest.fromData(requestData)
            request?.let {
                val widgetContent = widget.provideWidgetContent(context, request)
                val rawContent = widgetContent.toRawContent()
                callback.updateWidgetContent(rawContent.toData())
            }
        }
    }
}
