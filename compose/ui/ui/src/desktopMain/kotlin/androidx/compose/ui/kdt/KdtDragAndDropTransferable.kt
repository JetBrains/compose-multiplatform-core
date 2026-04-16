package androidx.compose.ui.kdt

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.draganddrop.DragAndDropTransferable
import androidx.compose.ui.platform.ClipEntry

@OptIn(ExperimentalComposeUiApi::class)
data class KdtDragAndDropTransferable(val clipboardEntry: ClipEntry) : DragAndDropTransferable
