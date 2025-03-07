import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.mpp.demo.HtmlElement
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

@Composable
fun MapExample() {
    var layoutDirection by remember { mutableStateOf(LayoutDirection.Ltr) }

    CompositionLocalProvider(
        LocalLayoutDirection provides layoutDirection
    ) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            HtmlElement(
                tagName = "iframe",
                id = "mapIframe",
                modifier = Modifier.height(600.dp).fillMaxSize().background(color = androidx.compose.ui.graphics.Color.LightGray),
                configure = {
                    setAttribute("src", "https://www.google.com/maps/embed?pb=!1m18!1m12!1m3!1d2435.010394190394!2d-74.0059412843063!3d40.71277697933075!2m3!1f0!2f0!3f0!3m2!1i1024!2i768!4f13.1!3m3!1m2!1s0x89c25a1b3072e0a5%3A0x10f2b2dbb11e27b0!2sNew%20York%20City%2C%20NY!5e0!3m2!1sen!2sus!4v1618237420494!5m2!1sen!2sus")
                    style.apply {
                        width = "600px"
                        height = "600px"
                        position = "absolute"
                    }
                }
            )
        }
    }
}
