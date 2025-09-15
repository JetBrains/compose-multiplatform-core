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

package androidx.glance.wear

import android.util.Log
import androidx.annotation.RestrictTo
import androidx.glance.wear.data.WearWidgetRequestData
import androidx.glance.wear.proto.WearWidgetRequestProto
import java.io.IOException

/**
 * Request for widget contents.
 *
 * @property instanceId The instance id of the widget for this request.
 *
 * TODO: also provide the widget type requested and sizing based on the device.
 */
public class WearWidgetRequest(public val instanceId: Int) {

    /** Convert this request to [WearWidgetRequestData]. */
    @RestrictTo(RestrictTo.Scope.LIBRARY)
    public fun toData(): WearWidgetRequestData {
        val requestProto = WearWidgetRequestProto(instance_id = instanceId)
        return WearWidgetRequestData().apply { payload = requestProto.encode() }
    }

    internal companion object {
        fun fromData(requestData: WearWidgetRequestData): WearWidgetRequest? {
            try {
                val requestProto = WearWidgetRequestProto.ADAPTER.decode(requestData.payload)
                return WearWidgetRequest(requestProto.instance_id)
            } catch (ex: IOException) {
                Log.e(TAG, "Error deserializing WearWidgetRequestData payload.", ex)
            }
            return null
        }

        private const val TAG = "WearWidgetRequest"
    }
}
