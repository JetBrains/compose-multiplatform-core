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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.storytale.story

val `Text Story` by story {
    // Parameters for customization
    val text by parameter("Hello, World!")
    val fontSize by parameter(16f)

    Column(modifier = Modifier.padding(16.dp)) {
        Text(
            text = text,
            fontSize = fontSize.sp
        )
    }
}

val `Text Styling Story` by story {
    // Parameters for customization
    val text by parameter("Styled Text")
    val fontSize by parameter(20f)
    val useCustomColor by parameter(true)
    val customColor by parameter(Color.Blue)
    val fontWeightType by parameter("Bold")
    val fontStyleType by parameter("Italic")
    val textDecorationType by parameter("Underline")
    val backgroundColor by parameter(Color.LightGray.copy(alpha = 0.3f))

    // Convert string parameters to actual types
    val fontWeight = when (fontWeightType) {
        "Normal" -> FontWeight.Normal
        "Bold" -> FontWeight.Bold
        "Light" -> FontWeight.Light
        "Medium" -> FontWeight.Medium
        "SemiBold" -> FontWeight.SemiBold
        "ExtraBold" -> FontWeight.ExtraBold
        "Thin" -> FontWeight.Thin
        else -> FontWeight.Normal
    }

    val fontStyle = when (fontStyleType) {
        "Normal" -> FontStyle.Normal
        "Italic" -> FontStyle.Italic
        else -> FontStyle.Normal
    }

    val textDecoration = when (textDecorationType) {
        "None" -> TextDecoration.None
        "Underline" -> TextDecoration.Underline
        "LineThrough" -> TextDecoration.LineThrough
        "Underline LineThrough" -> TextDecoration.combine(listOf(TextDecoration.Underline, TextDecoration.LineThrough))
        else -> TextDecoration.None
    }

    Column(modifier = Modifier.padding(16.dp)) {
        Text(
            text = text,
            fontSize = fontSize.sp,
            color = if (useCustomColor) customColor else Color.Unspecified,
            fontWeight = fontWeight,
            fontStyle = fontStyle,
            textDecoration = textDecoration,
            modifier = Modifier
                .fillMaxWidth()
                .background(backgroundColor)
                .padding(8.dp)
        )
    }
}
