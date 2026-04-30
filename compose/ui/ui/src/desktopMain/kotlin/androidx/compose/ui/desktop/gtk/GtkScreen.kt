package androidx.compose.ui.desktop.gtk

import androidx.compose.ui.desktop.GloballyPositionedScreen
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.DpSize

data class GtkScreen(
    override val nativeScreen: org.jetbrains.desktop.gtk.Screen,
    override val name: String = nativeScreen.name ?: "",
    override val size: DpSize = nativeScreen.size.toDpSize(),
    override val density: Density = Density(nativeScreen.scale.toFloat()),
    override val refreshRate: Int = nativeScreen.millihertz.toInt() / 1000,
    override val position: DpOffset = nativeScreen.origin.toDpOffset(),
    override val bounds: DpRect = DpRect(position, size),
) : GloballyPositionedScreen
