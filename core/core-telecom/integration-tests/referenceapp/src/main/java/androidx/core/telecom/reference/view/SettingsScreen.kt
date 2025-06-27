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

package androidx.core.telecom.reference.view

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.edit

// Constants for SharedPreferences
private const val PREFS_NAME = "MyAppSettings"
private const val KEY_PHONE_PREFIX = "phone_number_prefix"
private const val DEFAULT_PREFIX = "CustomScheme"

/**
 * Loads the saved phone number prefix from SharedPreferences.
 *
 * If no prefix is found, it returns the [DEFAULT_PREFIX].
 *
 * @param context The application context to access SharedPreferences.
 * @return The saved prefix string or the default prefix.
 */
fun loadPhoneNumberPrefix(context: Context): String {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    // Get the saved prefix, defaulting to DEFAULT_PREFIX if not found
    return prefs.getString(KEY_PHONE_PREFIX, DEFAULT_PREFIX) ?: DEFAULT_PREFIX
}

/**
 * Saves the given phone number prefix to SharedPreferences.
 *
 * @param context The application context to access SharedPreferences.
 * @param prefix The prefix string to save.
 */
fun savePhoneNumberPrefix(context: Context, prefix: String) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit { putString(KEY_PHONE_PREFIX, prefix) }
}

/**
 * Composable function that displays the Settings screen.
 *
 * This screen allows the user to view and configure the prefix (e.g., "tel:") that is automatically
 * added before dialing phone numbers. The prefix is loaded from and saved to SharedPreferences. It
 * uses Material 3 components like [Scaffold] and [TopAppBar].
 */
@OptIn(ExperimentalMaterial3Api::class) // Needed for Scaffold/TopAppBar in M3
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    // create state for the prefix.
    var prefix by remember { mutableStateOf(loadPhoneNumberPrefix(context)) }

    Scaffold(topBar = { TopAppBar(title = { Text("Settings") }) }) { paddingValues ->
        Column(
            modifier = Modifier.fillMaxSize().padding(paddingValues).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Configure the prefix added before dialing numbers.",
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Input Field
            OutlinedTextField(
                value = prefix, // Display the current state
                onValueChange = { newValue ->
                    // Update the state when the user types
                    prefix = newValue
                    // Save the new value immediately to SharedPreferences
                    savePhoneNumberPrefix(context, newValue)
                },
                label = { Text("Phone Number Prefix") }, // Label for the field
                placeholder = { Text(DEFAULT_PREFIX) }, // Show default as placeholder hint
                singleLine = true, // Often prefixes are single line
                modifier = Modifier.fillMaxWidth() // Take up available width
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Current Prefix: $prefix", // Display the current value below
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

/** Provides a design-time preview of the [SettingsScreen] composable in Android Studio. */
@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    SettingsScreen()
}
