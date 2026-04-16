package androidx.compose.ui.kdt.macos

import androidx.compose.ui.platform.UriHandler
import org.jetbrains.desktop.macos.Application

internal class MacOsUriHandler: UriHandler {
    override fun openUri(uri: String) {
        Application.openURL(uri)
    }
}
