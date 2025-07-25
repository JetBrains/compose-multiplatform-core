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

package androidx.savedstate

import android.os.Parcel

actual fun platformEncodeDecode(savedState: SavedState, doMarshalling: Boolean): SavedState {
    val parcel =
        Parcel.obtain().apply {
            savedState.writeToParcel(this, 0)
            setDataPosition(0)
        }
    val newParcel =
        if (doMarshalling) {
            val bytes = parcel.marshall()
            Parcel.obtain().apply {
                unmarshall(bytes, 0, bytes.size)
                setDataPosition(0)
            }
        } else {
            parcel
        }
    return newParcel.readBundle(savedState::class.java.classLoader)!!
}
