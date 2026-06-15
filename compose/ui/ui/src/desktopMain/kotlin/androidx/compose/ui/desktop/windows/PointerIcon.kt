package androidx.compose.ui.desktop.windows

import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.PointerIconService
import org.jetbrains.desktop.win32.Cursor
import org.jetbrains.desktop.win32.CursorIcon as Win32CursorIcon
import org.jetbrains.desktop.win32.Window as Win32Window

internal data class WindowsPointerIcon(val icon: Win32CursorIcon) : PointerIcon

internal class WindowsPointerIconService(
    private val nativeWindow: Win32Window,
) : PointerIconService {
    private var isHiddenUntilPointerMoves = false
    private var currentIcon: Win32CursorIcon = Win32CursorIcon.Arrow

    override fun getIcon(): PointerIcon = WindowsPointerIcon(currentIcon)

    override fun setIcon(value: PointerIcon?) {
        val icon = (value as? WindowsPointerIcon)?.icon ?: Win32CursorIcon.Arrow
        currentIcon = icon
        if (!isHiddenUntilPointerMoves) {
            nativeWindow.setCursor(icon)
        }
    }

    override fun getStylusHoverIcon(): PointerIcon? {
        return null
    }

    override fun setStylusHoverIcon(value: PointerIcon?) {
    }

    fun setHiddenUntilPointerMoves(hidden: Boolean) {
        when {
            hidden && !isHiddenUntilPointerMoves -> {
                isHiddenUntilPointerMoves = true
                Cursor.hide()
            }
            !hidden && isHiddenUntilPointerMoves -> {
                isHiddenUntilPointerMoves = false
                Cursor.show()
            }
        }
    }

    fun pushHide() {
        if (isHiddenUntilPointerMoves) {
            isHiddenUntilPointerMoves = false
        } else {
            Cursor.hide()
        }
    }

    fun popHide() {
        Cursor.show()
    }
}
