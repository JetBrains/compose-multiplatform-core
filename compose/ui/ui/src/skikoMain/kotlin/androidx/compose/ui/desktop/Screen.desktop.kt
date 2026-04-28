package androidx.compose.ui.desktop

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.DpSize

interface Screen {
    val name: String
    val size: DpSize
    val density: Density
    val refreshRate: Int

    val nativeScreen: Any?
}

interface GloballyPositionedScreen : Screen {
    val position: DpOffset
    val bounds: DpRect
}
