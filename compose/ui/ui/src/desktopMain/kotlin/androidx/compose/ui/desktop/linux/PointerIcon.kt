package androidx.compose.ui.desktop.linux

import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.PointerIconService
import org.jetbrains.desktop.linux.PointerShape
import org.jetbrains.desktop.linux.Window

internal data class LinuxPointerIcon(val icon: PointerShape) : PointerIcon

internal class LinuxPointerIconService(
    private val application: LinuxApplication,
    private val nativeWindow: Window,
) : PointerIconService {
    private var currentIcon: PointerShape = PointerShape.Default
    private var hiddenUntilPointerMoves = false
    private var hiddenDepth = 0

    override fun getIcon(): PointerIcon = LinuxPointerIcon(currentIcon)

    override fun setIcon(value: PointerIcon?) {
        currentIcon = (value as? LinuxPointerIcon)?.icon ?: PointerShape.Default
        if (!isHidden()) {
            application.onEventLoopAsync {
                nativeWindow.setPointerShape(currentIcon)
            }
        }
    }

    override fun setHiddenUntilPointerMoves(hidden: Boolean) {
        hiddenUntilPointerMoves = hidden
        application.onEventLoopAsync {
            nativeWindow.setPointerShape(if (isHidden()) PointerShape.Hidden else currentIcon)
        }
    }

    override fun pushHide() {
        hiddenDepth += 1
        application.onEventLoopAsync {
            nativeWindow.setPointerShape(PointerShape.Hidden)
        }
    }

    override fun popHide() {
        hiddenDepth = (hiddenDepth - 1).coerceAtLeast(0)
        application.onEventLoopAsync {
            nativeWindow.setPointerShape(if (isHidden()) PointerShape.Hidden else currentIcon)
        }
    }

    private fun isHidden(): Boolean = hiddenUntilPointerMoves || hiddenDepth > 0
}
