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

package androidx.compose.mpp.demo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

@Composable
fun BuildInfoDialog(onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colors.surface,
        ) {
            Column(Modifier.padding(20.dp).widthIn(max = 420.dp)) {
                val infoRows = listOf(
                    "Branch" to BuildInfo.branch,
                    "Built at" to BuildInfo.buildTime,
                    "Commit" to BuildInfo.commitHash,
                    "Author" to BuildInfo.author,
                )

                Text("Build info", style = MaterialTheme.typography.h6)
                Spacer(Modifier.height(12.dp))

                SelectionContainer {
                    Column {
                        infoRows.forEach { (label, value) -> InfoRow(label, value) }

                        Spacer(Modifier.height(12.dp))
                        Text("Commit message", style = MaterialTheme.typography.subtitle2)
                        Spacer(Modifier.height(4.dp))
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(max = 160.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text(
                                text = BuildInfo.commitMessage,
                                style = MaterialTheme.typography.body2.copy(fontFamily = FontFamily.Monospace)
                            )
                        }
                    }
                }

                val clipboard = LocalClipboardManager.current
                val allInfo = remember {
                    buildString {
                        infoRows.forEach { (label, value) -> appendLine("$label: $value") }
                        appendLine()
                        appendLine("Commit message:")
                        append(BuildInfo.commitMessage)
                    }
                }

                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = {
                        clipboard.setText(AnnotatedString(allInfo))
                        onDismiss()
                    }) {
                        Text("Copy all and close")
                    }
                    TextButton(onClick = onDismiss) {
                        Text("Close")
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.padding(vertical = 2.dp)) {
        Text(
            text = "$label: ",
            style = MaterialTheme.typography.body2.copy(fontWeight = FontWeight.Bold)
        )
        Text(text = value, style = MaterialTheme.typography.body2)
    }
}
