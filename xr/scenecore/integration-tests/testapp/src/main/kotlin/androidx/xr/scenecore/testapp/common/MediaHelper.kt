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

package androidx.xr.scenecore.testapp.common

import android.media.MediaCodecList
import androidx.annotation.OptIn
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi

/**
 * Checks if the device has a decoder that supports the MV-HEVC format.
 *
 * This function iterates through the available media codecs on the device and checks for a decoder
 * that supports the MV-HEVC Mime Type.
 *
 * @return `true` if an MV-HEVC decoder is found, `false` otherwise.
 */
@OptIn(UnstableApi::class) // for MimeTypes.VIDEO_MV_HEVC and normalizeMimeType usage
fun isMvHevcSupported(): Boolean {
    val mediaCodecList = MediaCodecList(MediaCodecList.ALL_CODECS)
    for (codecInfo in mediaCodecList.codecInfos) {
        // We only care about decoders
        if (codecInfo.isEncoder) {
            continue
        }

        val types = codecInfo.supportedTypes
        for (type in types) {
            if (
                MimeTypes.normalizeMimeType(type).equals(MimeTypes.VIDEO_MV_HEVC, ignoreCase = true)
            ) {
                return true
            }
        }
    }
    // No decoder found that supports the MV-HEVC profile.
    return false
}
