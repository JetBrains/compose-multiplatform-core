package androidx.compose.ui.kdt

import androidx.compose.ui.kdt.macos.MacOsKdtMainDispatcher
import androidx.compose.ui.platform.DesktopPlatform
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.MainCoroutineDispatcher

actual fun getComposeDispatcher(): MainCoroutineDispatcher =
    when (DesktopPlatform.Current) {
        DesktopPlatform.MacOS -> MacOsKdtMainDispatcher.INSTANCE
        DesktopPlatform.Linux -> TODO()
        DesktopPlatform.Windows -> TODO()
        DesktopPlatform.Unknown -> error("Unsupported desktop platform: ${DesktopPlatform.Current}")
    }
