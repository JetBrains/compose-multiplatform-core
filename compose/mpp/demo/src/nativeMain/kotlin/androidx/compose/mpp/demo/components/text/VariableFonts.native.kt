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

package androidx.compose.mpp.demo.components.text

import kotlinx.cinterop.readBytes
import platform.Foundation.NSBundle
import platform.Foundation.NSData
import platform.Foundation.dataWithContentsOfFile

actual suspend fun loadResource(file: String): ByteArray? {
    val lastDotIndex = file.lastIndexOf('.')
    val name = file.substring(0, lastDotIndex)
    val extension = file.substring(lastDotIndex + 1)
    val filePath = NSBundle.mainBundle.pathForResource(name, extension)
    val fileData = filePath?.let { NSData.dataWithContentsOfFile(it) }
        ?: throw Error("failed reading $file")
    return fileData.bytes!!.readBytes(fileData.length.toInt())
}