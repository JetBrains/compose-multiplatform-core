package androidx.compose.ui.kdt.headless

import androidx.compose.ui.kdt.Screen
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp

class HeadlessScreen(
    override val name: String = "Headless Screen",
    override val size: DpSize = DpSize(1920.dp, 1080.dp),
    devicePixelRatio: Float = 1.0f,
    override val refreshRate: Int = 60,
) : Screen {
    override val density: Density = Density(devicePixelRatio)
    override val nativeScreen: Any? = null
}
