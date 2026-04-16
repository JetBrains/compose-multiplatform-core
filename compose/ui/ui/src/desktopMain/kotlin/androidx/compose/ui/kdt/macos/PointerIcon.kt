package androidx.compose.ui.kdt.macos

import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.PointerIconService
import org.jetbrains.desktop.macos.Cursor

internal data class MacOsPointerIcon(val icon: Cursor.Icon) : PointerIcon

internal object MacOsPointerIconService : PointerIconService {
    private var isHiddenUntilPointerMoves = false

    override fun getIcon(): PointerIcon = MacOsPointerIcon(Cursor.icon)
    override fun setIcon(value: PointerIcon?) {
        Cursor.icon = (value as? MacOsPointerIcon)?.icon ?: Cursor.Icon.ArrowCursor
    }

    override fun setHiddenUntilPointerMoves(hidden: Boolean) {
        // todo[unterhofer] Hook this up to setHiddenUntilMouseMoves:
        //  https://developer.apple.com/documentation/appkit/nscursor/sethiddenuntilmousemoves(_:)
        when {
            hidden && !isHiddenUntilPointerMoves -> {
                isHiddenUntilPointerMoves = true
                Cursor.pushHide()
            }
            !hidden && isHiddenUntilPointerMoves -> {
                isHiddenUntilPointerMoves = false
                Cursor.popHide()
            }
        }
    }

    override fun pushHide() {
        if (isHiddenUntilPointerMoves) {
            isHiddenUntilPointerMoves = false
        } else {
            Cursor.pushHide()
        }
    }

    override fun popHide() {
        Cursor.popHide()
    }
}
