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

package androidx.privacysandbox.sdkruntime.integration.testaidl

import android.annotation.SuppressLint
import android.os.IBinder
import android.os.Parcel
import android.os.Parcelable

/** Wrapper for SandboxedSdks and AppOwnedSDKs - to pass them via IPC from SDK to App */
@SuppressLint("BanParcelableUsage") // Internal test-only object
class LoadedSdkInfo(val sdkInterface: IBinder, val sdkName: String?, val sdkVersion: Long?) :
    Parcelable {

    override fun describeContents() = 0

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeStrongBinder(sdkInterface)
        dest.writeString(sdkName)
        if (sdkVersion != null) {
            dest.writeInt(1)
            dest.writeLong(sdkVersion)
        } else {
            dest.writeInt(0)
        }
    }

    companion object {
        @JvmField
        val CREATOR =
            object : Parcelable.Creator<LoadedSdkInfo> {
                override fun createFromParcel(source: Parcel): LoadedSdkInfo {
                    val sdkInterface = source.readStrongBinder()
                    val sdkName = source.readString()
                    val sdkVersion =
                        if (source.readInt() == 1) {
                            source.readLong()
                        } else {
                            null
                        }
                    return LoadedSdkInfo(sdkInterface, sdkName, sdkVersion)
                }

                override fun newArray(size: Int): Array<LoadedSdkInfo?> = Array(size) { null }
            }
    }
}
