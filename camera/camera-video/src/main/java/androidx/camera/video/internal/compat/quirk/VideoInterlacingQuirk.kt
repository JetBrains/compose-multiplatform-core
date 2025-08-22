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

package androidx.camera.video.internal.compat.quirk

import android.annotation.SuppressLint
import android.os.Build
import androidx.camera.core.internal.compat.quirk.SurfaceProcessingQuirk
import java.util.Locale

/**
 * QuirkSummary
 * - Bug Id: b/391508996
 * - Description: Quirk denotes the recorded video is interlaced. This issue occurs on devices using
 *   the Exynos 7420 Octa (14 nm) chipset. Enabling the OpenGL pipeline work around this issue.
 * - Device(s): SM-N9208, SM-G920V
 */
@SuppressLint("CameraXQuirksClassDetector")
public object VideoInterlacingQuirk : SurfaceProcessingQuirk {

    private val DEVICE_MODELS: List<String>
        get() = listOf("SM-N9208", "SM-G920V")

    @JvmStatic
    public fun load(): Boolean {
        return DEVICE_MODELS.contains(Build.MODEL.uppercase(Locale.getDefault()))
    }
}
