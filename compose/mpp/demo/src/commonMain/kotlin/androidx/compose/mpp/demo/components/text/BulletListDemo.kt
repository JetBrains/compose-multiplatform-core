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

package androidx.compose.mpp.demo.components.text

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.Bullet
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

@Composable
fun BulletListDemo() {
    val textStyle = TextStyle(fontSize = 20.sp)

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Case("1. Control: SpanStyle (must work everywhere)") {
            Text(
                buildAnnotatedString {
                    append("plain, ")
                    withStyle(SpanStyle(color = Color.Red, fontWeight = FontWeight.Bold)) {
                        append("red bold")
                    }
                    append(", plain")
                },
                style = textStyle,
            )
        }

        Case("2. Default bullet list") {
            Text(
                buildAnnotatedString {
                    append("Not a bullet item\n")
                    withBulletList {
                        withBulletListItem { append("Item 1") }
                        withBulletListItem { append("Item 2") }
                    }
                },
                style = textStyle,
            )
        }

        Case("3. Nested bullet list") {
            Text(
                buildAnnotatedString {
                    withBulletList {
                        withBulletListItem { append("Item 1") }
                        withBulletList { withBulletListItem { append("Nested item 2") } }
                        withBulletListItem { append("Item 3") }
                    }
                },
                style = textStyle,
            )
        }

        Case("4. Custom bullets: rect / stroke / colored") {
            val rect = Bullet.Default.copy(shape = RectangleShape)
            val stroked = rect.copy(drawStyle = Stroke(width = 2f))
            val colored = rect.copy(brush = SolidColor(Color.Magenta))
            Text(
                buildAnnotatedString {
                    withBulletList(bullet = rect) {
                        withBulletListItem { append("Rectangle") }
                        withBulletListItem(bullet = stroked) { append("Stroke") }
                        withBulletListItem(bullet = colored) { append("Magenta") }
                    }
                },
                style = textStyle,
            )
        }

        Case("5. Multi-line item (marker must be painted once, on the first line)") {
            Text(
                buildAnnotatedString {
                    withBulletList {
                        withBulletListItem {
                            append(
                                "This list item is deliberately long so that it wraps onto " +
                                    "several lines and shows whether the marker is painted once " +
                                    "per paragraph or once per line."
                            )
                        }
                    }
                },
                style = textStyle,
            )
        }

        Case("6. addBullet directly, bypassing withBulletList") {
            Text(
                buildAnnotatedString {
                    withStyle(
                        ParagraphStyle(
                            textIndent = TextIndent(
                                Bullet.DefaultIndentation,
                                Bullet.DefaultIndentation,
                            )
                        )
                    ) {
                        val start = length
                        append("Manual bullet via addBullet")
                        addBullet(Bullet.Default, start, length)
                    }
                },
                style = textStyle,
            )
        }

        Case("7. Reference: same indentation, no Bullet annotation") {
            Text(
                buildAnnotatedString {
                    withStyle(ParagraphStyle(textIndent = TextIndent(1.em, 1.em))) {
                        append("Indented paragraph without any bullet")
                    }
                },
                style = textStyle,
            )
        }
    }
}

@Composable
private fun Case(title: String, content: @Composable () -> Unit) {
    Text(title, style = TextStyle(fontSize = 12.sp, color = Color(0xFF6650A4)))
    Spacer(Modifier.height(4.dp))
    content()
    Spacer(Modifier.height(20.dp))
}
