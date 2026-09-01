package androidx.compose.ui.graphics.vector

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.assertColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class VectorPainterNestedDrawTest {

    // Verify VectorPainter draws correctly when nested inside another Painter
    @Suppress("DEPRECATION")
    @Test
    fun vectorPainterDrawsInsideCustomPainter() = runSkikoComposeUiTest(Size(100f, 100f)) {
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                val vectorPainter = rememberVectorPainter(
                    defaultWidth = 24.dp, defaultHeight = 24.dp,
                    viewportWidth = 24f, viewportHeight = 24f,
                ) { _, _ ->
                    Path(
                        pathData = listOf(
                            PathNode.MoveTo(0f, 0f),
                            PathNode.LineTo(24f, 0f),
                            PathNode.LineTo(24f, 24f),
                            PathNode.LineTo(0f, 24f),
                            PathNode.Close,
                        ),
                        fill = SolidColor(Color.Red),
                    )
                }

                val wrapperPainter = remember(vectorPainter) {
                    WrapperPainter(vectorPainter)
                }

                Image(
                    painter = wrapperPainter,
                    contentDescription = null,
                    modifier = Modifier.size(100.dp)
                )
            }
        }

        waitForIdle()
        // The center pixel
        captureToImage().asSkiaBitmap().assertColor(Color.Red, 50, 50)
    }
}


// Minimal wrapper painter that delegates drawing to the inner painter
private class WrapperPainter(val inner: Painter) : Painter() {
    override val intrinsicSize: Size = Size.Unspecified

    override fun DrawScope.onDraw() {
        // Draw the inner painter at the full available size
        with(inner) {
            draw(size = this@onDraw.size)
        }
    }
}