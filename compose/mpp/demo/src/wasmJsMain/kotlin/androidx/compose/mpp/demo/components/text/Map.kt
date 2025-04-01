import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.WebElementView
import kotlinx.browser.document
import org.w3c.dom.HTMLIFrameElement

@Composable
fun MapExample() {
    WebElementView(
        factory = {
            (document.createElement("iframe") as HTMLIFrameElement).apply {
                src = "https://www.google.com/maps/embed?pb=!1m18!1m12!1m3!1d3278.253037823882!2d32.4244870765934!3d34.74921817290249!2m3!1f0!2f0!3f0!3m2!1i1024!2i768!4f13.1!3m3!1m2!1s0x14e706b84a013d37%3A0x46a5e16befd26997!2z0KPQvdC40LLQtdGA0YHQuNGC0LXRgiDCq9Cd0LXQsNC_0L7Qu9C40YHCuw!5e0!3m2!1sru!2s!4v1741689185735!5m2!1sru!2s"
                style.apply {
                    width = "100%"
                    height = "100%"
                    border = "none"
                    position = "absolute"
                    top = "0"
                }
            }
        },
        modifier = Modifier.fillMaxSize(),
        update = { iframe -> iframe.src = iframe.src }
    )
}
