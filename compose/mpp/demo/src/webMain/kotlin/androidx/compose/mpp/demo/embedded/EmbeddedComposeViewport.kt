package androidx.compose.mpp.demo.embedded

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.ComposeViewport
import kotlinx.browser.document
import org.w3c.dom.HTMLDivElement
import org.w3c.dom.HTMLElement

@OptIn(ExperimentalComposeUiApi::class)
// A reproducer for https://youtrack.jetbrains.com/issue/CMP-10351
fun embeddedScrollDemo(composeScroll: Boolean = true) {
    // Override the default fullscreen styles to allow page scrolling
    val style = document.createElement("style")
    style.textContent = """
        html, body {
            width: 100%;
            height: auto !important;
            margin: 0;
            padding: 0;
            overflow: auto !important;
        }
        body {
            display: block !important;
        }
        #composeApplication {
            display: none;
        }
    """.trimIndent()
    document.head?.appendChild(style)

    val body = document.body ?: return

    // Title
    val heading = document.createElement("h2")
    heading.textContent = "Embedded ComposeViewport Scroll Demo"
    (heading as HTMLElement).style.padding = "16px"
    body.appendChild(heading)

    val description = document.createElement("p")
    description.textContent = "This demo shows a ComposeViewport embedded in a scrollable HTML page. " +
        "Scroll the page to see Compose co-operating with native HTML scroll gestures." +
        if (!composeScroll) " Compose content has no internal scroll." else ""
    (description as HTMLElement).style.apply {
        padding = "0 16px"
        fontStyle = "italic"
        color = "#777"
    }
    body.appendChild(description)

    // HTML content before Compose
    addHtmlParagraphs(body, 5, "Above Compose —")

    // Compose container
    val composeContainer = document.createElement("div") as HTMLDivElement
    composeContainer.style.apply {
        width = "100%"
        height = "400px"
        borderTop = "2px solid #1976D2"
        borderBottom = "2px solid #1976D2"
        margin = "16px 0"
    }
    body.appendChild(composeContainer)

    // HTML content after Compose
    addHtmlParagraphs(body, 10, "Below Compose —")

    ComposeViewport(composeContainer) {
        MaterialTheme {
            var counter by remember { mutableStateOf(0) }
            Column(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Compose content inside HTML scroll", fontSize = 18.sp)
                        Button(onClick = { counter++ }) {
                            Text("Clicked $counter times")
                        }
                    }
                }

                if (composeScroll) {
                    ComposeScrollableContent()
                }
            }
        }
    }
}

@Composable
fun ComposeScrollableContent() {
    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        items(30) { index ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .border(1.dp, Color.LightGray)
                    .padding(12.dp)
            ) {
                Text("Compose LazyColumn item #$index")
            }
        }
    }
}

private fun addHtmlParagraphs(container: org.w3c.dom.Element, count: Int, prefix: String) {
    for (i in 1..count) {
        val p = document.createElement("p")
        p.textContent = "$prefix paragraph $i: Lorem ipsum dolor sit amet, consectetur adipiscing elit. " +
            "Sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. " +
            "Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris."
        (p as HTMLElement).style.apply {
            padding = "0 16px"
            lineHeight = "1.6"
        }
        container.appendChild(p)
    }
}
