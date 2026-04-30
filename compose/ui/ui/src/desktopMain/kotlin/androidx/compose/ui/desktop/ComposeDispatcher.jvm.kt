package androidx.compose.ui.desktop

import androidx.compose.ui.desktop.gtk.GtkKdtMainDispatcher
import androidx.compose.ui.desktop.linux.LinuxKdtMainDispatcher
import androidx.compose.ui.desktop.macos.MacOsKdtMainDispatcher
import androidx.compose.ui.platform.DesktopPlatform
import kotlinx.coroutines.MainCoroutineDispatcher

actual fun getComposeDispatcher(): MainCoroutineDispatcher =
    when (DesktopPlatform.Current) {
        DesktopPlatform.MacOS -> MacOsKdtMainDispatcher.INSTANCE
        DesktopPlatform.Linux -> when (currentLinuxWindowSystem()) {
            LinuxWindowSystem.Wayland -> LinuxKdtMainDispatcher.INSTANCE
            LinuxWindowSystem.Gtk -> GtkKdtMainDispatcher.INSTANCE
        }
        DesktopPlatform.Windows -> TODO()
        DesktopPlatform.Unknown -> error("Unsupported desktop platform: ${DesktopPlatform.Current}")
    }
