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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.storytale.story

val `TextField Story` by story {
    // Common parameters
    var text by parameter("")
    val enabled by parameter(true)
    val readOnly by parameter(false)
    val label by parameter("Label")
    val placeholder by parameter("Placeholder")

    // Variant selection
    val variant by parameter(listOf(
        "Basic", 
        "With Icons", 
        "Password", 
        "Error State", 
        "With Prefix and Suffix"
    ), 0)

    // Icon parameters
    val showLeadingIcon by parameter(false)
    val showTrailingIcon by parameter(false)

    // Error state parameters
    val isError by parameter(false)
    val errorMessage by parameter("Error message")

    // Prefix/suffix parameters
    val prefix by parameter("$")
    val suffix by parameter(".00")

    // Password parameters
    val hidePassword by parameter(true)

    // State management
    var passwordVisible by remember { mutableStateOf(false) }
    
    // Custom implementation of Visibility and VisibilityOff icons
    // These are copied from the material-icons-extended library to avoid adding it as a dependency
    val visibilityIcon = remember {
        materialIcon(name = "Filled.Visibility") {
            materialPath {
                moveTo(12.0f, 4.5f)
                curveTo(7.0f, 4.5f, 2.73f, 7.61f, 1.0f, 12.0f)
                curveToRelative(1.73f, 4.39f, 6.0f, 7.5f, 11.0f, 7.5f)
                reflectiveCurveToRelative(9.27f, -3.11f, 11.0f, -7.5f)
                curveToRelative(-1.73f, -4.39f, -6.0f, -7.5f, -11.0f, -7.5f)
                close()
                moveTo(12.0f, 17.0f)
                curveToRelative(-2.76f, 0.0f, -5.0f, -2.24f, -5.0f, -5.0f)
                reflectiveCurveToRelative(2.24f, -5.0f, 5.0f, -5.0f)
                reflectiveCurveToRelative(5.0f, 2.24f, 5.0f, 5.0f)
                reflectiveCurveToRelative(-2.24f, 5.0f, -5.0f, 5.0f)
                close()
                moveTo(12.0f, 9.0f)
                curveToRelative(-1.66f, 0.0f, -3.0f, 1.34f, -3.0f, 3.0f)
                reflectiveCurveToRelative(1.34f, 3.0f, 3.0f, 3.0f)
                reflectiveCurveToRelative(3.0f, -1.34f, 3.0f, -3.0f)
                reflectiveCurveToRelative(-1.34f, -3.0f, -3.0f, -3.0f)
                close()
            }
        }
    }
    
    val visibilityOffIcon = remember {
        materialIcon(name = "Filled.VisibilityOff") {
            materialPath {
                moveTo(12.0f, 7.0f)
                curveToRelative(2.76f, 0.0f, 5.0f, 2.24f, 5.0f, 5.0f)
                curveToRelative(0.0f, 0.65f, -0.13f, 1.26f, -0.36f, 1.83f)
                lineToRelative(2.92f, 2.92f)
                curveToRelative(1.51f, -1.26f, 2.7f, -2.89f, 3.43f, -4.75f)
                curveToRelative(-1.73f, -4.39f, -6.0f, -7.5f, -11.0f, -7.5f)
                curveToRelative(-1.4f, 0.0f, -2.74f, 0.25f, -3.98f, 0.7f)
                lineToRelative(2.16f, 2.16f)
                curveTo(10.74f, 7.13f, 11.35f, 7.0f, 12.0f, 7.0f)
                close()
                moveTo(2.0f, 4.27f)
                lineToRelative(2.28f, 2.28f)
                lineToRelative(0.46f, 0.46f)
                curveTo(3.08f, 8.3f, 1.78f, 10.02f, 1.0f, 12.0f)
                curveToRelative(1.73f, 4.39f, 6.0f, 7.5f, 11.0f, 7.5f)
                curveToRelative(1.55f, 0.0f, 3.03f, -0.3f, 4.38f, -0.84f)
                lineToRelative(0.42f, 0.42f)
                lineTo(19.73f, 22.0f)
                lineTo(21.0f, 20.73f)
                lineTo(3.27f, 3.0f)
                lineTo(2.0f, 4.27f)
                close()
                moveTo(7.53f, 9.8f)
                lineToRelative(1.55f, 1.55f)
                curveToRelative(-0.05f, 0.21f, -0.08f, 0.43f, -0.08f, 0.65f)
                curveToRelative(0.0f, 1.66f, 1.34f, 3.0f, 3.0f, 3.0f)
                curveToRelative(0.22f, 0.0f, 0.44f, -0.03f, 0.65f, -0.08f)
                lineToRelative(1.55f, 1.55f)
                curveToRelative(-0.67f, 0.33f, -1.41f, 0.53f, -2.2f, 0.53f)
                curveToRelative(-2.76f, 0.0f, -5.0f, -2.24f, -5.0f, -5.0f)
                curveToRelative(0.0f, -0.79f, 0.2f, -1.53f, 0.53f, -2.2f)
                close()
                moveTo(11.84f, 9.02f)
                lineToRelative(3.15f, 3.15f)
                lineToRelative(0.02f, -0.16f)
                curveToRelative(0.0f, -1.66f, -1.34f, -3.0f, -3.0f, -3.0f)
                lineToRelative(-0.17f, 0.01f)
                close()
            }
        }
    }

    Column(modifier = Modifier.padding(16.dp)) {
        when (variant) {
            "Basic" -> {
                // Basic TextField
                TextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(label) },
                    placeholder = { Text(placeholder) },
                    enabled = enabled,
                    readOnly = readOnly,
                    modifier = Modifier.padding(8.dp)
                )
            }
            "With Icons" -> {
                // TextField with icons
                TextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(label) },
                    leadingIcon = if (showLeadingIcon) { 
                        { Icon(Icons.Filled.Email, contentDescription = "Email Icon") }
                    } else null,
                    trailingIcon = if (showTrailingIcon) {
                        {
                            if (text.isNotEmpty()) {
                                IconButton(onClick = { text = "" }) {
                                    Icon(
                                        imageVector = Icons.Filled.Clear,
                                        contentDescription = "Clear text"
                                    )
                                }
                            }
                        }
                    } else null,
                    enabled = enabled,
                    readOnly = readOnly,
                    modifier = Modifier.padding(8.dp)
                )
            }
            "Password" -> {
                // Password TextField
                TextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(label) },
                    visualTransformation = if (passwordVisible || !hidePassword) 
                        VisualTransformation.None 
                    else 
                        PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = if (hidePassword) {
                        {
                            val image = if (passwordVisible)
                                visibilityOffIcon
                            else 
                                visibilityIcon

                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = image,
                                    contentDescription = if (passwordVisible) 
                                        "Hide password" 
                                    else 
                                        "Show password"
                                )
                            }
                        }
                    } else null,
                    enabled = enabled,
                    readOnly = readOnly,
                    modifier = Modifier.padding(8.dp)
                )
            }
            "Error State" -> {
                // TextField with error state
                TextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(label) },
                    isError = isError,
                    supportingText = {
                        if (isError) {
                            Text(
                                text = errorMessage,
                                color = Color.Red
                            )
                        }
                    },
                    enabled = enabled,
                    readOnly = readOnly,
                    modifier = Modifier.padding(8.dp)
                )
            }
            "With Prefix and Suffix" -> {
                // TextField with prefix and suffix
                TextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(label) },
                    prefix = { Text(prefix) },
                    suffix = { Text(suffix) },
                    enabled = enabled,
                    readOnly = readOnly,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.padding(8.dp)
                )
            }
        }
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