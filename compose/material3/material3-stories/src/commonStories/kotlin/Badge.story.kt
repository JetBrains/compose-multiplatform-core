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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.storytale.story

val `Content Badge Story` by story {
    // Common parameters
    val containerColor by parameter(Color(0xFFB3261E))
    val contentColor by parameter(Color.White)
    
    // Badge content parameters
    val showIcon by parameter(true)
    val badgeText by parameter("1")

    Badge(
        containerColor = containerColor,
        contentColor = contentColor,
        content = {
            if (showIcon) {
                Icon(
                    imageVector = Icons.Filled.Favorite,
                    contentDescription = "Favorite",
                    modifier = Modifier.size(8.dp)
                )
            } else {
                Text(badgeText)
            }
        }
    )
}

val `BadgedBox Story` by story {
    // Common parameters
    val containerColor by parameter(Color(0xFFB3261E))
    val contentColor by parameter(Color.White)
    val badgeText by parameter("1")

    BadgedBox(
        badge = {
            Badge(
                containerColor = containerColor,
                contentColor = contentColor,
                content = { Text(badgeText) }
            )
        }
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .background(Color.LightGray, shape = RoundedCornerShape(4.dp))
        )
    }
}

// A hack for development needs:
// If we need to customize the parameters controller UI, for example for a missing parameter type,
// then we can do it here.
// This relies on the fact that Storytale compile plugin will invoke the initialization of all properties in any file with stories.
private val initialization: Int = initializationForParameters()
private fun initializationForParameters(): Int {
    @Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
    org.jetbrains.compose.storytale.gallery.material3.parameterUiControllerCustomizer = null
        // org.jetbrains.compose.storytale.gallery.material3.ParameterUiControllerCustomizer { { Text(it.name) } }

    return 1
}
