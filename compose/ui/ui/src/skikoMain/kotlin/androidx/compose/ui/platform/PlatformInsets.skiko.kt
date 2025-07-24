/*
 * Copyright 2023 The Android Open Source Project
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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * This class represents platform insets.
 */
interface PlatformInsets {
    /**
     * The left inset in pixels.
     */
    val left: Int

    /**
     * The top inset in pixels.
     */
    val top: Int

    /**
     * The right inset in pixels.
     */
    val right: Int

    /**
     * The bottom inset in pixels.
     */
    val bottom: Int

    companion object {
        val Unspecified = object : PlatformInsets {
            override val left: Int = Int.MAX_VALUE
            override val right: Int = Int.MAX_VALUE
            override val top: Int = Int.MAX_VALUE
            override val bottom: Int = Int.MAX_VALUE
        }

        val Zero = object : PlatformInsets {
            override val left: Int = 0
            override val right: Int = 0
            override val top: Int = 0
            override val bottom: Int = 0
        }
    }
}
