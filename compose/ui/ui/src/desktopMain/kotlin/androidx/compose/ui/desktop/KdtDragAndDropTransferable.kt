package androidx.compose.ui.desktop

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.draganddrop.DragAndDropTransferable
import androidx.compose.ui.platform.ClipEntry

@OptIn(ExperimentalComposeUiApi::class)
data class KdtDragAndDropTransferable(val clipboardEntry: ClipEntry) : DragAndDropTransferable
