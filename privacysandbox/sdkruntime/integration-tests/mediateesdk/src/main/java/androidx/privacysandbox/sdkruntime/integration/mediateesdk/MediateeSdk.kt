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

package androidx.privacysandbox.sdkruntime.integration.mediateesdk

import android.content.Context
import android.util.Log
import androidx.privacysandbox.sdkruntime.integration.testaidl.IMediateeSdkApi
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.FileNotFoundException

class MediateeSdk(private val sdkContext: Context) : IMediateeSdkApi.Stub() {

    override fun doSomething(param: String): String {
        Log.i(TAG, "MediateeSdk#doSomething($param)")
        return "MediateeSdk result is $param"
    }

    override fun writeToFile(filename: String, data: String) {
        sdkContext.openFileOutput(filename, Context.MODE_PRIVATE).use { outputStream ->
            DataOutputStream(outputStream).use { dataStream -> dataStream.writeUTF(data) }
        }
    }

    override fun readFromFile(filename: String): String? {
        try {
            return sdkContext.openFileInput(filename).use { inputStream ->
                inputStream
                DataInputStream(inputStream).use { dataStream -> dataStream.readUTF() }
            }
        } catch (_: FileNotFoundException) {
            return null
        }
    }

    companion object {
        private const val TAG = "MediateeSdk"
    }
}
