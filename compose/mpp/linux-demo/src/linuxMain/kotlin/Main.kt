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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window

fun main() {
    Window(title = "Compose Multiplatform Linux Native Demo") {
        var count by remember { mutableStateOf(0) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF1C1B1F)), // Dark background
            contentAlignment = Alignment.Center
        ) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF2B2930)
                ),
                modifier = Modifier
                    .width(400.dp)
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Compose Linux Native",
                        color = Color(0xFFD0BCFF),
                        fontSize = 24.sp,
                        style = MaterialTheme.typography.titleLarge
                    )

                    Divider(color = Color(0xFF49454F))

                    Text(
                        text = "Count: $count",
                        color = Color.White,
                        fontSize = 36.sp,
                        style = MaterialTheme.typography.headlineLarge
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = { count-- },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF4F378B)
                            )
                        ) {
                            Text("-", fontSize = 20.sp)
                        }

                        Button(
                            onClick = { count++ },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF4F378B)
                            )
                        ) {
                            Text("+", fontSize = 20.sp)
                        }
                    }
                }
            }
        }
    }
}
