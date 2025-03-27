import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.WebUiView
import kotlinx.browser.document
import org.w3c.dom.HTMLElement

@Composable
fun MapExample() {
    WebUiView(
        factory = {
            (document.createElement("iframe") as HTMLElement).apply {
                setAttribute("src", "https://www.example.com")
                setAttribute("style", "width:100%; height:100%; border:none;")
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}
