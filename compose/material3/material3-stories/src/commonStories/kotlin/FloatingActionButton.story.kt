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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.storytale.story

val `Basic FloatingActionButton` by story {
    // Parameters for customization
    val useCustomColor by parameter(false)
    val containerColor by parameter(Color(0xFF6200EE))
    val contentColor by parameter(Color.White)
    
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(
            modifier = Modifier.padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            FloatingActionButton(
                onClick = { /* Handle FAB click */ },
                containerColor = if (useCustomColor) containerColor else FloatingActionButtonDefaults.containerColor,
                contentColor = if (useCustomColor) contentColor else Color.Unspecified
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "Add"
                )
            }
        }
    }
}

val `Extended FloatingActionButton` by story {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(
            modifier = Modifier.padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            ExtendedFloatingActionButton(
                text = { Text("Extended") },
                icon = { Icon(Icons.Filled.Add, contentDescription = "Add") },
                onClick = { /* Handle FAB click */ }
            )
        }
    }
}

val `Animated Extended FloatingActionButton` by story {
    // State to track expanded state
    var expanded by remember { mutableStateOf(true) }
    
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ExtendedFloatingActionButton(
                text = { Text("Extended") },
                icon = { Icon(Icons.Filled.Edit, contentDescription = "Edit") },
                onClick = { expanded = !expanded },
                expanded = expanded
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "Click the FAB to ${if (expanded) "collapse" else "expand"} it",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}