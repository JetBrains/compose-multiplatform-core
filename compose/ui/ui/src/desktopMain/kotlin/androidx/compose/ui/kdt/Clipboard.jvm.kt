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

package androidx.compose.ui.kdt

import androidx.compose.ui.kdt.macos.macOsClipboardEntry
import androidx.compose.ui.kdt.windows.windowsClipboardEntry
import androidx.compose.ui.platform.DesktopPlatform
import fleet.util.multiplatform.Actual

@Actual
internal fun clipboardEntryJvm(vararg items: ClipboardItem): ClipboardEntry {
    return when (DesktopPlatform.Current) {
        DesktopPlatform.MacOS -> macOsClipboardEntry(*items)
        DesktopPlatform.Linux -> fixedMimeTransferClipboardEntry(*items)
        DesktopPlatform.Windows -> windowsClipboardEntry(*items)
        DesktopPlatform.Unknown -> error("Unsupported desktop platform: ${DesktopPlatform.Current}")
    }
}
