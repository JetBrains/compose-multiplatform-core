package androidx.compose.ui.desktop.macos

import androidx.compose.ui.platform.UriHandler
import org.jetbrains.desktop.macos.Application

internal class MacOsUriHandler: UriHandler {
    override fun openUri(uri: String) {
        Application.openURL(uri)
    }
}
