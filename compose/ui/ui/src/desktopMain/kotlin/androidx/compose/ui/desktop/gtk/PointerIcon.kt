package androidx.compose.ui.desktop.gtk

import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.PointerIconService
import org.jetbrains.desktop.gtk.PointerShape
import org.jetbrains.desktop.gtk.Window

internal data class GtkPointerIcon(val icon: PointerShape) : PointerIcon

internal class GtkPointerIconService(
    private val application: GtkApplication,
    private val nativeWindow: Window,
) : PointerIconService {
    private var currentIcon: PointerShape = PointerShape.Default
    private var hiddenUntilPointerMoves = false
    private var hiddenDepth = 0

    override fun getIcon(): PointerIcon = GtkPointerIcon(currentIcon)

    override fun setIcon(value: PointerIcon?) {
        currentIcon = (value as? GtkPointerIcon)?.icon ?: PointerShape.Default
        if (!isHidden()) {
            application.onEventLoopAsync {
                nativeWindow.setPointerShape(currentIcon)
            }
        }
    }

    override fun getStylusHoverIcon(): PointerIcon? = null

    override fun setStylusHoverIcon(value: PointerIcon?) {}

    fun setHiddenUntilPointerMoves(hidden: Boolean) {
        hiddenUntilPointerMoves = hidden
        application.onEventLoopAsync {
            nativeWindow.setPointerShape(if (isHidden()) PointerShape.Hidden else currentIcon)
        }
    }

    fun pushHide() {
        hiddenDepth += 1
        application.onEventLoopAsync {
            nativeWindow.setPointerShape(PointerShape.Hidden)
        }
    }

    fun popHide() {
        hiddenDepth = (hiddenDepth - 1).coerceAtLeast(0)
        application.onEventLoopAsync {
            nativeWindow.setPointerShape(if (isHidden()) PointerShape.Hidden else currentIcon)
        }
    }

    private fun isHidden(): Boolean = hiddenUntilPointerMoves || hiddenDepth > 0
}
