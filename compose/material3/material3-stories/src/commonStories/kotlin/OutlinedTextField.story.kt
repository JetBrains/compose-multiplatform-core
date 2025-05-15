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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.storytale.story

val `OutlinedTextField Story` by story {
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
        "With Prefix and Suffix",
        "Custom Style"
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

    // Text field options
    val singleLine by parameter(false)
    val maxLines by parameter(if (singleLine) 1 else 5)
    val minLines by parameter(1)

    // Keyboard options
    val keyboardTypeOptions = listOf(
        "Text", "ASCII", "Number", "Phone", "Uri", "Email", "Password", "NumberPassword"
    )
    val keyboardType by parameter(keyboardTypeOptions, 0)

    val imeActionOptions = listOf(
        "Default", "None", "Go", "Search", "Send", "Next", "Previous", "Done"
    )
    val imeAction by parameter(imeActionOptions, 0)

    // Shape parameters
    val useCustomShape by parameter(false)
    val cornerRadius by parameter(4f)

    // Color parameters
    val useCustomColors by parameter(false)
    val customContainerColor by parameter(Color(0xFFE8DEF8))
    val customTextColor by parameter(Color(0xFF1D192B))

    // State management
    var passwordVisible by remember { mutableStateOf(false) }

    // Convert parameters to actual values
    val selectedKeyboardType = when (keyboardType) {
        "ASCII" -> KeyboardType.Ascii
        "Number" -> KeyboardType.Number
        "Phone" -> KeyboardType.Phone
        "Uri" -> KeyboardType.Uri
        "Email" -> KeyboardType.Email
        "Password" -> KeyboardType.Password
        "NumberPassword" -> KeyboardType.NumberPassword
        else -> KeyboardType.Text
    }

    val selectedImeAction = when (imeAction) {
        "None" -> ImeAction.None
        "Go" -> ImeAction.Go
        "Search" -> ImeAction.Search
        "Send" -> ImeAction.Send
        "Next" -> ImeAction.Next
        "Previous" -> ImeAction.Previous
        "Done" -> ImeAction.Done
        else -> ImeAction.Default
    }

    val keyboardActionsInstance = KeyboardActions(
        onDone = { /* Handle done action */ },
        onGo = { /* Handle go action */ },
        onNext = { /* Handle next action */ },
        onPrevious = { /* Handle previous action */ },
        onSearch = { /* Handle search action */ },
        onSend = { /* Handle send action */ }
    )

    val shape: Shape = if (useCustomShape) RoundedCornerShape(cornerRadius.dp) else OutlinedTextFieldDefaults.shape

    val colors = if (useCustomColors) {
        OutlinedTextFieldDefaults.colors(
            focusedTextColor = customTextColor,
            unfocusedTextColor = customTextColor,
            disabledTextColor = customTextColor.copy(alpha = 0.38f),
            focusedContainerColor = customContainerColor,
            unfocusedContainerColor = customContainerColor,
            disabledContainerColor = customContainerColor.copy(alpha = 0.38f)
        )
    } else {
        OutlinedTextFieldDefaults.colors()
    }

    Column(modifier = Modifier.padding(16.dp)) {
        when (variant) {
            "Basic" -> {
                // Basic OutlinedTextField
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(label) },
                    placeholder = { Text(placeholder) },
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
                    isError = isError,
                    supportingText = {
                        if (isError) {
                            Text(
                                text = errorMessage,
                                color = Color.Red
                            )
                        }
                    },
                    prefix = { Text(prefix) },
                    suffix = { Text(suffix) },
                    enabled = enabled,
                    readOnly = readOnly,
                    singleLine = singleLine,
                    maxLines = maxLines,
                    minLines = minLines,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = selectedKeyboardType,
                        imeAction = selectedImeAction
                    ),
                    keyboardActions = keyboardActionsInstance,
                    shape = shape,
                    colors = colors,
                    modifier = Modifier.padding(8.dp)
                )
            }
            "With Icons" -> {
                // OutlinedTextField with icons
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(label) },
                    placeholder = { Text(placeholder) },
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
                    isError = isError,
                    supportingText = {
                        if (isError) {
                            Text(
                                text = errorMessage,
                                color = Color.Red
                            )
                        }
                    },
                    prefix = { Text(prefix) },
                    suffix = { Text(suffix) },
                    enabled = enabled,
                    readOnly = readOnly,
                    singleLine = singleLine,
                    maxLines = maxLines,
                    minLines = minLines,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = selectedKeyboardType,
                        imeAction = selectedImeAction
                    ),
                    keyboardActions = keyboardActionsInstance,
                    shape = shape,
                    colors = colors,
                    modifier = Modifier.padding(8.dp)
                )
            }
            "Password" -> {
                // Password OutlinedTextField
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(label) },
                    placeholder = { Text(placeholder) },
                    visualTransformation = if (!hidePassword) 
                        VisualTransformation.None 
                    else 
                        PasswordVisualTransformation(),
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
                    isError = isError,
                    supportingText = {
                        if (isError) {
                            Text(
                                text = errorMessage,
                                color = Color.Red
                            )
                        }
                    },
                    prefix = { Text(prefix) },
                    suffix = { Text(suffix) },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = selectedImeAction
                    ),
                    keyboardActions = keyboardActionsInstance,
                    enabled = enabled,
                    readOnly = readOnly,
                    singleLine = singleLine,
                    maxLines = maxLines,
                    minLines = minLines,
                    shape = shape,
                    colors = colors,
                    modifier = Modifier.padding(8.dp)
                )
            }
            "Error State" -> {
                // OutlinedTextField with error state
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(label) },
                    placeholder = { Text(placeholder) },
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
                    isError = isError,
                    supportingText = {
                        if (isError) {
                            Text(
                                text = errorMessage,
                                color = Color.Red
                            )
                        }
                    },
                    prefix = { Text(prefix) },
                    suffix = { Text(suffix) },
                    enabled = enabled,
                    readOnly = readOnly,
                    singleLine = singleLine,
                    maxLines = maxLines,
                    minLines = minLines,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = selectedKeyboardType,
                        imeAction = selectedImeAction
                    ),
                    keyboardActions = keyboardActionsInstance,
                    shape = shape,
                    colors = colors,
                    modifier = Modifier.padding(8.dp)
                )
            }
            "With Prefix and Suffix" -> {
                // OutlinedTextField with prefix and suffix
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(label) },
                    placeholder = { Text(placeholder) },
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
                    isError = isError,
                    supportingText = {
                        if (isError) {
                            Text(
                                text = errorMessage,
                                color = Color.Red
                            )
                        }
                    },
                    prefix = { Text(prefix) },
                    suffix = { Text(suffix) },
                    enabled = enabled,
                    readOnly = readOnly,
                    singleLine = singleLine,
                    maxLines = maxLines,
                    minLines = minLines,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = selectedKeyboardType,
                        imeAction = selectedImeAction
                    ),
                    keyboardActions = keyboardActionsInstance,
                    shape = shape,
                    colors = colors,
                    modifier = Modifier.padding(8.dp)
                )
            }
            "Custom Style" -> {
                // OutlinedTextField with custom text style
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(label) },
                    placeholder = { Text(placeholder) },
                    textStyle = TextStyle(
                        color = MaterialTheme.colorScheme.primary,
                        fontStyle = MaterialTheme.typography.bodyLarge.fontStyle,
                        fontWeight = MaterialTheme.typography.bodyLarge.fontWeight,
                        fontFamily = MaterialTheme.typography.bodyLarge.fontFamily
                    ),
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
                    isError = isError,
                    supportingText = {
                        if (isError) {
                            Text(
                                text = errorMessage,
                                color = Color.Red
                            )
                        }
                    },
                    prefix = { Text(prefix) },
                    suffix = { Text(suffix) },
                    enabled = enabled,
                    readOnly = readOnly,
                    singleLine = singleLine,
                    maxLines = maxLines,
                    minLines = minLines,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = selectedKeyboardType,
                        imeAction = selectedImeAction
                    ),
                    keyboardActions = keyboardActionsInstance,
                    shape = shape,
                    colors = colors,
                    modifier = Modifier.padding(8.dp)
                )
            }
        }
    }
}
