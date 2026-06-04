/*
 * Copyright 2020 The Android Open Source Project
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

@file:OptIn(ExperimentalComposeUiApi::class)

package androidx.compose.desktop.examples.swingexample

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposePanel
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import java.awt.BorderLayout
import java.awt.Color as AwtColor
import java.awt.Dimension
import java.time.LocalDateTime
import javax.swing.JButton
import javax.swing.JFrame
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.swing.border.LineBorder

fun main() {
    SwingUtilities.invokeLater {
        swingMain()
    }
}

private fun swingMain() {
    val frame = JFrame("CMP-4556 repro")
    frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
    frame.minimumSize = Dimension(500, 400)
    frame.isResizable = false

    val mainPanel = JPanel(BorderLayout()).apply {
        border = LineBorder(AwtColor.MAGENTA)
        isOpaque = false

        val composePanel = ComposePanel()
        composePanel.border = LineBorder(java.awt.Color.RED)

        var itemHeight by mutableStateOf(10)

        composePanel.setContent {
            Row(
                modifier = Modifier.border(1.dp, Color.DarkGray).background(Color.White),
                verticalAlignment = Alignment.Bottom
            ) {
                println("${LocalDateTime.now()} Recompose with itemHeight = $itemHeight")
                Box(Modifier.background(Color.Blue).size(10.dp, itemHeight.dp))
            }
        }
        add(composePanel, BorderLayout.CENTER)

        val button = JButton("Add")
        button.addActionListener {
            println("${LocalDateTime.now()} Button clicked")
            itemHeight = (itemHeight + 50) % 200
        }

        add(JPanel().apply {
            isOpaque = false
            border = LineBorder(java.awt.Color.CYAN)
            add(button, BorderLayout.SOUTH)
        }, BorderLayout.EAST)
    }

    frame.contentPane.add(mainPanel, BorderLayout.NORTH)
    frame.isVisible = true
}
