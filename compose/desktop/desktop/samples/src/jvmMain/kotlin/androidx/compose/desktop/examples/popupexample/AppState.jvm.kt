/*
 * Copyright 2020 The Android Open Source Project
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
package androidx.compose.desktop.examples.popupexample

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.awt.image.BufferedImage
import javax.imageio.ImageIO

object AppState {
    private val imageRes: String = "androidx/compose/desktop/example/tray.png"
    private var icon: BufferedImage? = null

    var isMainWindowOpen by mutableStateOf(true)
        private set

    val secondaryWindowIds = mutableStateListOf<Int>()

    private var lastId = 0

    fun openSecondaryWindow() {
        secondaryWindowIds.add(lastId++)
    }

    fun closeMainWindow() {
        isMainWindowOpen = false
    }

    fun closeSecondaryWindow(id: Int) {
        secondaryWindowIds.remove(id)
    }

    fun closeAll() {
        isMainWindowOpen = false
        secondaryWindowIds.clear()
    }

    fun image(): BufferedImage {
        if (icon != null) {
            return icon!!
        }
        try {
            val img = Thread.currentThread().contextClassLoader.getResource(imageRes)
            val bitmap: BufferedImage? = ImageIO.read(img)
            if (bitmap != null) {
                icon = bitmap
                return bitmap
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)
    }

    val wndTitle = mutableStateOf("Desktop Compose Popup")
    val popupState = mutableStateOf(false)
    val amount = mutableStateOf(0)

    val undecorated = mutableStateOf(false)

    val dialogType = mutableStateOf(DialogType.Common)
    val notificationType = mutableStateOf(NotificationType.Notify)
}

enum class DialogType {
    Common, Window, Alert
}

enum class NotificationType {
    Notify, Warn, Error
}
