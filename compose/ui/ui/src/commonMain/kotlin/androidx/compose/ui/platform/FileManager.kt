/*
 * Copyright 2026 The Android Open Source Project
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

package androidx.compose.ui.platform

import kotlin.coroutines.CoroutineContext
import kotlinx.io.files.Path

interface FileManager {
    val name: String
    fun revealFileOrFolder(path: Path)
    fun openFileOrFolder(path: Path)
}

val CoroutineContext.fileManager: FileManager?
    get() = this[FileManagerCoroutineContextElement]?.fileManager

data class FileManagerCoroutineContextElement(val fileManager: FileManager) : CoroutineContext.Element {
    companion object : CoroutineContext.Key<FileManagerCoroutineContextElement>

    override val key: CoroutineContext.Key<*> get() = FileManagerCoroutineContextElement
}
