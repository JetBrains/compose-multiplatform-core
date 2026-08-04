package androidx.compose.ui.desktop

import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.draganddrop.DragAndDropTransferData
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.desktop.gtk.GtkWindow
import androidx.compose.ui.desktop.linux.LinuxWindow
import androidx.compose.ui.desktop.macos.MacOsWindow
import androidx.compose.ui.platform.PlatformDragAndDropManager
import androidx.compose.ui.platform.PlatformDragAndDropSource

@OptIn(InternalComposeUiApi::class)
internal class KdtDragAndDropManager(
    private val window: Window,
) : PlatformDragAndDropManager {
    override val isRequestDragAndDropTransferRequired: Boolean = true

    override fun requestDragAndDropTransfer(
        source: PlatformDragAndDropSource,
        offset: Offset,
    ) {
        var isTransferStarted = false
        val startTransferScope = object : PlatformDragAndDropSource.StartTransferScope {
            override fun startDragAndDropTransfer(
                transferData: DragAndDropTransferData,
                decorationSize: Size,
                drawDragDecoration: DrawScope.() -> Unit,
            ): Boolean {
                isTransferStarted = true
                startDrag(
                    offset,
                    transferData,
                    transferData.dragDecorationSize ?: decorationSize,
                    drawDragDecoration,
                )
                return isTransferStarted
            }
        }
        with(source) {
            startTransferScope.startDragAndDropTransfer(offset) {
                isTransferStarted
            }
        }
    }

    fun startDrag(
        offset: Offset,
        transferData: DragAndDropTransferData,
        decorationSize: Size,
        drawDragDecoration: DrawScope.() -> Unit,
    ) {
        when (window) {
            is MacOsWindow -> window.startDragSession(
                offset,
                transferData,
                decorationSize,
                drawDragDecoration,
            )
            is LinuxWindow -> window.startDragSession(
                offset,
                transferData,
                decorationSize,
                drawDragDecoration,
            )
            is GtkWindow -> window.startDragSession(
                offset,
                transferData,
                decorationSize,
                drawDragDecoration,
            )
            // TODO: add androidx.compose.ui.desktop.windows.WindowsWindow when the Windows
            //  backend lands (drag source is not supported there; the branch should be a no-op).
            else -> error("Unsupported drag source window: ${window::class.qualifiedName}")
        }
    }
}
