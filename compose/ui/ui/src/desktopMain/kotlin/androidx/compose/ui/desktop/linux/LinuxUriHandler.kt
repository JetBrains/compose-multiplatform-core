package androidx.compose.ui.desktop.linux

import androidx.compose.ui.platform.UriHandler

internal class LinuxUriHandler : UriHandler {
    override fun openUri(uri: String) {
        val application = currentLinuxNativeApplication()
        if (application.isEventLoopThread()) {
            application.openURL(uri, null) // TODO
        } else {
            application.runOnEventLoopAsync {
                application.openURL(uri, null) // TODO
            }
        }
    }
}
