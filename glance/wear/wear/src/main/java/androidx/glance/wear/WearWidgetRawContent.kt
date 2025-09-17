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
import androidx.glance.wear.data.WearWidgetRawContentData
import androidx.glance.wear.proto.WearWidgetRawContentProto
import java.io.IOException
import okio.ByteString.Companion.toByteString

/**
 * Describes the raw contents from [WearWidgetContent]. This is after RC content is captured and
 * serialized.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY)
public class WearWidgetRawContent(public val rcDocument: ByteArray) {

    /** Convert to the parcelable [WearWidgetRawContentData]. */
    @RestrictTo(RestrictTo.Scope.LIBRARY)
    public fun toData(): WearWidgetRawContentData {
        val contentProto = WearWidgetRawContentProto(rc_document = rcDocument.toByteString())
        return WearWidgetRawContentData().apply { payload = contentProto.encode() }
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY)
    public companion object {
        public fun fromData(contentData: WearWidgetRawContentData): WearWidgetRawContent? {
            try {
                val contentProto = WearWidgetRawContentProto.ADAPTER.decode(contentData.payload)
                return WearWidgetRawContent(rcDocument = contentProto.rc_document.toByteArray())
            } catch (ex: IOException) {
                Log.e(TAG, "Error deserializing WearWidgetRawContentData payload.", ex)
            }
            return null
        }

        private const val TAG = "WearWidgetRequest"
    }
}
