package androidx.compose.ui.desktop.macos

import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.PointerIconService
import org.jetbrains.desktop.macos.Cursor

internal data class MacOsPointerIcon(val icon: Cursor.Icon) : PointerIcon

internal object MacOsPointerIconService : PointerIconService {
    override fun getIcon(): PointerIcon = MacOsPointerIcon(Cursor.icon)
    override fun setIcon(value: PointerIcon?) {
        Cursor.icon = (value as? MacOsPointerIcon)?.icon ?: Cursor.Icon.ArrowCursor
    }

    override fun getStylusHoverIcon(): PointerIcon? {
        return null
    }

    override fun setStylusHoverIcon(value: PointerIcon?) {
    }

    fun setHiddenUntilPointerMoves(hidden: Boolean) {
        Cursor.setHiddenUntilMouseMoves(hidden)
    }

    fun pushHide() {
        Cursor.pushHide()
    }

    fun popHide() {
        Cursor.popHide()
    }
}
