/*
 * Copyright 2024 The Android Open Source Project
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

package androidx.lifecycle.viewmodel.testing.internal

import android.os.Bundle
import android.os.Parcel
import androidx.savedstate.SavedState

internal actual fun platformEncodeDecode(savedState: SavedState): SavedState {
    val parcel = Parcel.obtain()
    savedState.writeToParcel(parcel, Bundle.PARCELABLE_WRITE_RETURN_VALUE)
    parcel.setDataPosition(0)
    val restored = SavedState.CREATOR.createFromParcel(parcel)
    val bytes = parcel.marshall()
    parcel.recycle()
    // 1 MB = 1024 kilobytes, and 1 KB = 1024 bytes.
    check(bytes.size <= 1024 * 1024) {
        "SavedState exceeds maximum size (1 MB): ${bytes.size} bytes."
    }
    return restored
}
