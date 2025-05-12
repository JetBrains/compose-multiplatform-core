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

import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.state.ToggleableState
import org.jetbrains.compose.storytale.story

val `Checkbox Story` by story {
    // Common parameters
    var checked by parameter(false)
    val enabled by parameter(true)

    // Custom colors parameters
    val useCustomColors by parameter(false)
    val checkedColor by parameter(Color.Magenta)
    val uncheckedColor by parameter(Color.LightGray)
    val checkmarkColor by parameter(Color.White)
    val disabledColor by parameter(Color.Gray)

    // Create the appropriate colors if needed
    val colors = if (useCustomColors) {
        CheckboxDefaults.colors(
            checkedColor = checkedColor,
            uncheckedColor = uncheckedColor,
            checkmarkColor = checkmarkColor,
            disabledCheckedColor = disabledColor,
            disabledUncheckedColor = disabledColor
        )
    } else {
        CheckboxDefaults.colors()
    }

    Checkbox(
        checked = checked,
        onCheckedChange = { checked = it },
        modifier = Modifier,
        enabled = enabled,
        colors = colors
    )
}

val `TriStateCheckbox Story` by story {
    // Common parameters
    var stateIndex by parameter(0)
    val enabled by parameter(true)

    // Convert stateIndex to ToggleableState
    val state = when (stateIndex) {
        0 -> ToggleableState.Off
        1 -> ToggleableState.On
        else -> ToggleableState.Indeterminate
    }

    // Custom colors parameters
    val useCustomColors by parameter(false)
    val checkedColor by parameter(Color.Magenta)
    val uncheckedColor by parameter(Color.LightGray)
    val checkmarkColor by parameter(Color.White)
    val disabledColor by parameter(Color.Gray)

    // Create the appropriate colors if needed
    val colors = if (useCustomColors) {
        CheckboxDefaults.colors(
            checkedColor = checkedColor,
            uncheckedColor = uncheckedColor,
            checkmarkColor = checkmarkColor,
            disabledCheckedColor = disabledColor,
            disabledUncheckedColor = disabledColor,
            disabledIndeterminateColor = disabledColor
        )
    } else {
        CheckboxDefaults.colors()
    }

    // Create a custom onClick handler to cycle through the three states
    val onClick = {
        stateIndex = when (stateIndex) {
            0 -> 1       // Off -> On
            1 -> 2       // On -> Indeterminate
            else -> 0    // Indeterminate -> Off
        }
    }

    TriStateCheckbox(
        state = state,
        onClick = onClick,
        modifier = Modifier,
        enabled = enabled,
        colors = colors
    )
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
