package androidx.compose.ui.desktop.linux

import androidx.compose.ui.desktop.GloballyPositionedScreen
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.DpSize

data class LinuxScreen(
    override val nativeScreen: org.jetbrains.desktop.linux.Screen,
    override val name: String = nativeScreen.name ?: "",
    override val size: DpSize = nativeScreen.size.toDpSize(),
    override val density: Density = Density(1.0f),
    override val refreshRate: Int = nativeScreen.maximumFramesPerSecond,
    override val position: DpOffset = nativeScreen.origin.toDpOffset(),
    override val bounds: DpRect = DpRect(position, size),
) : GloballyPositionedScreen
