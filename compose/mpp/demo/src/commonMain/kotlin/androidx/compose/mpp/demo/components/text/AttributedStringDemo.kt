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

package androidx.compose.mpp.demo.components.text

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.mpp.demo.Screen

@Composable
fun AttributedStringDemo() {
    var lastClickedLink by remember { mutableStateOf<String?>(null) }

    val linkInteractionListener = LinkInteractionListener { link ->
        lastClickedLink = when (link) {
            is LinkAnnotation.Url -> link.url
            is LinkAnnotation.Clickable -> link.tag
            else -> "unknown"
        }
    }

    Column(modifier = Modifier.padding(16.dp)) {
        Text(
            text = buildAnnotatedString {
                append("LinkAnnotation can be included in an AnnotatedString. \nTry: \n- ")
                withStyle(linkStyle) {
                    withLink(
                        LinkAnnotation.Clickable(
                            tag = "compose",
                            linkInteractionListener = linkInteractionListener
                        )
                    ) {
                        append("clickable link")
                    }
                }
                append(" - to trigger its interaction listener\n- ")
                withStyle(urlLinkStyle) {
                    withLink(
                        LinkAnnotation.Url(
                            url = "https://github.com/JetBrains/compose-multiplatform-core",
                            linkInteractionListener = null // default
                        )
                    ) {
                        append("URL link")
                    }
                }
                append(" - it should open CMP core GitHub repository")
            }
        )

        lastClickedLink?.let {
            Text(
                text = "Last clicked link: $it",
                modifier = Modifier.padding(top = 16.dp)
            )
        }
    }
}

private val linkStyle = SpanStyle(
    color = Color(0xFF1565C0),
    textDecoration = TextDecoration.Underline
)

private val urlLinkStyle = SpanStyle(
    color = Color(0xFF6A1B9A),
    textDecoration = TextDecoration.Underline
)
