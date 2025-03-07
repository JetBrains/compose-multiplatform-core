import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.mpp.demo.HtmlElement
import androidx.compose.mpp.demo.LayoutModifier
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp


@Composable
fun WasmExampleTwoDirectionsAndRTL2() {
    val colors = listOf(
        Color.Black,
        Color.LightGray,
        Color.DarkGray,
        Color.Gray
    )

    val rows = 20
    val columns = 20

    val rowHeight = 200.dp

    var layoutDirection by remember { mutableStateOf(LayoutDirection.Ltr) }

    CompositionLocalProvider(
        LocalLayoutDirection provides layoutDirection
    ) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Button(onClick = {
                layoutDirection = when (layoutDirection) {
                    LayoutDirection.Ltr -> LayoutDirection.Rtl
                    LayoutDirection.Rtl -> LayoutDirection.Ltr
                }
            }) {
                Text("Toggle layout direction")
            }
            Column(
                Modifier.fillMaxSize().padding(all = 20.dp).verticalScroll(rememberScrollState()),
            ) {
                repeat(rows) { row ->
                    Row(Modifier.height(rowHeight).fillMaxSize().horizontalScroll(rememberScrollState())) {
                        repeat(columns) { col ->
                            val itemColor = colors[(row + col) % colors.size]
                            val elementId = "$row:$col"

                            Box(
                                modifier = Modifier
                                    .size(rowHeight, rowHeight)
                                    .background(itemColor),
                            ) {
                                HtmlElement(
                                    id = elementId,
                                    tagName = "div",
                                    modifier = Modifier.size(50.dp, 50.dp)
                                        .background(color = Color.Gray),
                                    configure = {
                                        innerText = elementId
                                        style.apply {
                                            position = "absolute"
                                            textAlign = "center"
                                            border = "2px solid white"
                                            boxSizing = "border-box"
                                            width = "50px"
                                            height = "50px"
                                            color = "white"
                                        }
                                        style.setProperty("pointer-events", "none")
                                    },
                                    onClick = { println("Clicked on $elementId") },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
