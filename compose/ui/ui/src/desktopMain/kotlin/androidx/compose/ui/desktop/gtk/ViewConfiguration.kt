package androidx.compose.ui.desktop.gtk

import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp

class ViewConfiguration(private val density: Density) : ViewConfiguration {
    override val longPressTimeoutMillis: Long = 500
    override val doubleTapTimeoutMillis: Long get() = 300
    override val doubleTapMinTimeMillis: Long = 40
    override val touchSlop: Float get() = with(density) { 18.dp.toPx() }
}
