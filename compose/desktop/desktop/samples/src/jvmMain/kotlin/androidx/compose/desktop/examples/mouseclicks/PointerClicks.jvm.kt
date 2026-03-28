/*
 * Copyright 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.compose.desktop.examples.mouseclicks

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.onPointerClick
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.Card
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.isTertiaryPressed
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.singleWindowApplication
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * A comprehensive showcase of the [onPointerClick] modifier.
 * Demonstrates hardware button routing, keyboard modifier combinations,
 * coordinate tracking, and selective ripple indications.
 */
@OptIn(ExperimentalFoundationApi::class)
fun main() = singleWindowApplication(
    title = "Advanced Pointer Click Showcase",
    state = WindowState(width = 900.dp, height = 650.dp)
) {
    MaterialTheme {
        var logs by remember { mutableStateOf(listOf<String>()) }
        val gridSize = 5

        // Track the color of each cell
        val cellColors = remember { mutableStateListOf<Color>().apply {
            repeat(gridSize * gridSize) { add(Color(0xFFE0E0E0)) }
        }}

        // Track if a cell has a "Right-Click Flag"
        val cellFlags = remember { mutableStateListOf<Boolean>().apply {
            repeat(gridSize * gridSize) { add(false) }
        }}

        fun logEvent(message: String) {
            val time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS"))
            logs = (listOf("[$time] $message") + logs).take(50) // Keep last 50
        }

        Row(Modifier.fillMaxSize().background(Color(0xFFF5F5F5)).padding(16.dp)) {
            // LEFT PANEL: The Interactive Grid
            Column(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Interactive Canvas",
                    style = MaterialTheme.typography.h5,
                    fontWeight = FontWeight.Bold
                )
                Text("Test different mouse buttons and keyboard modifiers.", color = Color.Gray)

                Spacer(modifier = Modifier.height(24.dp))

                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White)
                        .border(1.dp, Color.LightGray, RoundedCornerShape(8.dp))
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    for (row in 0 until gridSize) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            for (col in 0 until gridSize) {
                                val index = row * gridSize + col
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(70.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(cellColors[index])
                                        .border(
                                            width = if (cellFlags[index]) 3.dp else 1.dp,
                                            color = if (cellFlags[index]) Color.Red else Color.Transparent,
                                            shape = RoundedCornerShape(4.dp)
                                        )
                                        .onPointerClick(
                                            triggerPressIndication = {
                                                it.buttons.isPrimaryPressed || it.buttons.isTertiaryPressed
                                            }
                                        ) { event ->
                                            val x = event.position.x.toInt()
                                            val y = event.position.y.toInt()
                                            val isShift = event.keyboardModifiers.isShiftPressed
                                            val buttons = event.buttons

                                            when {
                                                // Shift + Left Click
                                                isShift && buttons?.isPrimaryPressed == true -> {
                                                    cellColors[index] = Color(0xFFFFD54F) // Yellow Highlight
                                                    logEvent("Cell [$row,$col]: Shift + Left Click at ($x, $y)")
                                                }
                                                // Standard Left Click
                                                buttons?.isPrimaryPressed == true -> {
                                                    cellColors[index] = Color(0xFF4FC3F7) // Blue Paint
                                                    logEvent("Cell [$row,$col]: Left Click at ($x, $y)")
                                                }
                                                // Right Click
                                                buttons?.isSecondaryPressed == true -> {
                                                    cellFlags[index] = !cellFlags[index] // Toggle Flag
                                                    logEvent("Cell [$row,$col]: Right Click at ($x, $y)")
                                                }
                                                // Middle Click
                                                buttons?.isTertiaryPressed == true -> {
                                                    cellColors[index] = Color(0xFFE0E0E0) // Reset Paint
                                                    cellFlags[index] = false              // Reset Flag
                                                    logEvent("Cell [$row,$col]: Middle Click (Cleared)")
                                                }
                                            }
                                        }
                                ) {
                                    if (cellFlags[index]) {
                                        Text("🚩", fontSize = 24.sp)
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(1f))
                Button(onClick = {
                    cellColors.fill(Color(0xFFE0E0E0))
                    cellFlags.fill(false)
                    logEvent("Canvas Reset")
                }) {
                    Text("Reset Canvas")
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // RIGHT PANEL: The Event Inspector
            Card(
                modifier = Modifier.weight(0.8f).fillMaxHeight(),
                elevation = 4.dp
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Event Inspector", style = MaterialTheme.typography.h6)
                    Divider(Modifier.padding(vertical = 8.dp))

                    Text("Legend:", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("• Left Click: Paint Blue (Shows Ripple)", fontSize = 12.sp)
                    Text("• Shift + Left Click: Highlight Yellow (Shows Ripple)", fontSize = 12.sp)
                    Text("• Right Click: Toggle Flag (No Ripple)", fontSize = 12.sp)
                    Text("• Middle Click: Clear Cell (Shows Ripple)", fontSize = 12.sp)

                    Spacer(modifier = Modifier.height(16.dp))

                    Box(Modifier.fillMaxSize().background(Color(0xFF2B2B2B), RoundedCornerShape(4.dp)).padding(8.dp)) {
                        LazyColumn(Modifier.fillMaxSize()) {
                            items(logs) { log ->
                                Text(
                                    text = log,
                                    color = Color(0xFFA9B7C6),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}