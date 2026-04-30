package androidx.compose.ui.desktop.gtk

import androidx.compose.ui.platform.UriHandler

internal class GtkUriHandler : UriHandler {
    override fun openUri(uri: String) {
        val application = currentGtkNativeApplication()
        if (application.isEventLoopThread()) {
            application.openURL(uri, null) // TODO
        } else {
            application.runOnEventLoopAsync {
                application.openURL(uri, null) // TODO
            }
        }
    }
}
