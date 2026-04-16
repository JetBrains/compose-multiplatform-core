package androidx.compose.ui.kdt.headless

import androidx.compose.ui.platform.UriHandler

class HeadlessUriHandler: UriHandler {
    override fun openUri(uri: String) {
        FakeBrowser.lastOpenedUrl = uri
    }
}
