import androidx.compose.runtime.Composable
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.platform.FrameRecomposer
import androidx.compose.ui.platform.registerSkikoComposeImplementation
import androidx.compose.ui.scene.CanvasLayersComposeScene
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.Dispatchers

@OptIn(InternalComposeUiApi::class)
internal class BenchmarkComposeScene(width: Int, height: Int) {
    init {
        registerSkikoComposeImplementation()
    }

    private val frameRecomposer = FrameRecomposer(Dispatchers.Unconfined)
    private val scene = CanvasLayersComposeScene(
        frameRecomposer = frameRecomposer,
        size = IntSize(width, height)
    )

    fun setContent(content: @Composable () -> Unit) {
        scene.setContent(content = content)
    }

    fun render(canvas: Canvas, nanoTime: Long) {
        frameRecomposer.performFrame(nanoTime)
        scene.measureAndLayout()
        scene.draw(canvas)
    }

    fun close() {
        scene.close()
        frameRecomposer.close()
    }
}
