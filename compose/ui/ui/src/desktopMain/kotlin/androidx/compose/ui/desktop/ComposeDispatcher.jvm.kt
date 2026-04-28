package androidx.compose.ui.desktop

import androidx.compose.ui.desktop.macos.MacOsKdtMainDispatcher
import androidx.compose.ui.platform.DesktopPlatform
import kotlinx.coroutines.MainCoroutineDispatcher

actual fun getComposeDispatcher(): MainCoroutineDispatcher =
    when (DesktopPlatform.Current) {
        DesktopPlatform.MacOS -> MacOsKdtMainDispatcher.INSTANCE
        DesktopPlatform.Linux -> TODO()
        DesktopPlatform.Windows -> TODO()
        DesktopPlatform.Unknown -> error("Unsupported desktop platform: ${DesktopPlatform.Current}")
    }
