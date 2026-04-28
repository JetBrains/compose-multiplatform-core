package androidx.compose.ui.desktop.macos

import androidx.compose.ui.desktop.GloballyPositionedScreen
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.DpSize


//todo[unterhofer] Make these reactive and turn it into a class with referential identity
data class MacOsScreen(
    override val nativeScreen: org.jetbrains.desktop.macos.Screen,
    override val name: String = nativeScreen.name,
    override val size: DpSize = nativeScreen.size.toDpSize(),
    override val density: Density = Density(nativeScreen.scale.toFloat()),
    override val refreshRate: Int = nativeScreen.maximumFramesPerSecond,
    override val position: DpOffset = nativeScreen.origin.toDpOffset(),
    override val bounds: DpRect = DpRect(position, size),
) : GloballyPositionedScreen
