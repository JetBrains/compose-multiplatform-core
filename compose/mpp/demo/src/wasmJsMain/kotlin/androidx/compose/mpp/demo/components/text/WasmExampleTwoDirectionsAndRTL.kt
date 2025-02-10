import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.mpp.demo.Screen
import androidx.compose.mpp.demo.addHtmlElementWithCompose
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

val WasmLayouts = Screen.Selection(
    "WasmLayouts",
    Screen.Example("WasmExampleTwoDirectionsAndRTL") { WasmExampleTwoDirectionsAndRTL() }
)

@Composable
private fun WasmExampleTwoDirectionsAndRTL() {
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
            LazyColumn(
                Modifier.fillMaxSize().padding(all = 20.dp),
            ) {
                items(rows) { row ->
                    LazyRow(Modifier.height(rowHeight)) {
                        items(columns) { col ->
                            val color = colors[(row + col) % colors.size]
                            val elementId = "$row:$col"

                            Box(
                                Modifier
                                    .size(200.dp, rowHeight)
                                    .background(color)
                                    .addHtmlElementWithCompose (id = elementId, tagName = "div"){
                                        innerText = elementId
                                        style.apply {
                                            position = "absolute"
                                            backgroundColor = "white"
                                            padding = "10px"
                                            borderRadius = "2px"
                                        }
                                    }
                            )
                        }
                    }
                }
            }
        }
    }
}
