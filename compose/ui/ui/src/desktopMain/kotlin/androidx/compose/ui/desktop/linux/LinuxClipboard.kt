package androidx.compose.ui.desktop.linux

import androidx.compose.ui.desktop.ClipboardItemsEntry
import androidx.compose.ui.desktop.MimeTransferClipboardEntry
import androidx.compose.ui.desktop.Utf8PlainTextMimeType
import androidx.compose.ui.desktop.Utf8PlainTextMimeTypeFallback
import androidx.compose.ui.desktop.encodeClipboardItemsToMimeData
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import org.jetbrains.desktop.linux.Application
import org.jetbrains.desktop.linux.DataSource
import org.jetbrains.desktop.linux.Event

internal class LinuxClipboard(
    private val application: Application,
) : Clipboard {
    private val nextSerial = AtomicInteger(0)
    private val lock = Any()
    private val pendingClipboardRequests = mutableMapOf<Int, String>()
    private val pendingPrimarySelectionRequests = mutableMapOf<Int, CancellableContinuation<String?>>()
    private val clipboardMimeData = linkedMapOf<String, ByteArray>()
    private val primarySelectionMimeData = linkedMapOf<String, ByteArray>()

    override fun getClipEntrySync(): ClipEntry {
        return ClipEntry(
            MimeTransferClipboardEntry {
                synchronized(lock) {
                    clipboardMimeData.toMap()
                }
            },
        )
    }

    override suspend fun getClipEntry(): ClipEntry = getClipEntrySync()

    override suspend fun setClipEntry(clipEntry: ClipEntry?) {
        clipEntry ?: return
        val itemsEntry = clipEntry.nativeClipEntry as? ClipboardItemsEntry ?: return
        val mimeData = encodeClipboardItemsToMimeData(itemsEntry.items)
        synchronized(lock) {
            clipboardMimeData.clear()
            clipboardMimeData.putAll(mimeData)
        }
        application.runOnEventLoopAsync {
            application.clipboardPut(mimeData.keys.toList())
        }
    }

    override suspend fun systemSelection(): String? {
        return suspendCancellableCoroutine { continuation ->
            val serial = nextSerial.incrementAndGet()
            synchronized(lock) {
                pendingPrimarySelectionRequests[serial] = continuation
            }
            continuation.invokeOnCancellation {
                synchronized(lock) {
                    pendingPrimarySelectionRequests.remove(serial)
                }
            }
            application.runOnEventLoopAsync {
                application.primarySelectionPaste(serial, listOf(Utf8PlainTextMimeType, Utf8PlainTextMimeTypeFallback))
                synchronized(lock) {
                    pendingPrimarySelectionRequests.remove(serial)
                }?.resume(null)
            }
        }
    }

    override suspend fun setSystemSelection(text: String?) {
        synchronized(lock) {
            primarySelectionMimeData.clear()
            text?.encodeToByteArray()?.let { bytes ->
                primarySelectionMimeData[Utf8PlainTextMimeType] = bytes
                primarySelectionMimeData[Utf8PlainTextMimeTypeFallback] = bytes
            }
        }
        application.runOnEventLoopAsync {
            application.primarySelectionPut(listOf(Utf8PlainTextMimeType, Utf8PlainTextMimeTypeFallback))
        }
    }

    override val nativeClipboard: Any
        get() = application

    fun onDataTransferAvailable(event: Event.DataTransferAvailable): Boolean {
        return when (event.dataSource) {
            DataSource.Clipboard -> {
                updateClipboardMimeTypes(event.mimeTypes)
                true
            }
            else -> false
        }
    }

    fun onDataReceived(event: Event.DataTransfer): Boolean {
        val content = event.content ?: return false
        synchronized(lock) {
            pendingClipboardRequests.remove(event.serial)
        }?.let { mimeType ->
            synchronized(lock) {
                clipboardMimeData[mimeType] = content.data
            }
            return true
        }

        synchronized(lock) {
            pendingPrimarySelectionRequests.remove(event.serial)
        }?.let { continuation ->
            continuation.resume(content.data.decodeToString())
            return true
        }

        return false
    }

    fun getMimeData(mimeType: String): ByteArray? {
        return synchronized(lock) {
            clipboardMimeData[mimeType]
        }
    }

    fun getPrimarySelectionMimeData(mimeType: String): ByteArray? {
        return synchronized(lock) {
            primarySelectionMimeData[mimeType]
        }
    }

    private fun updateClipboardMimeTypes(mimeTypes: List<String>) {
        synchronized(lock) {
            clipboardMimeData.clear()
        }
        mimeTypes.distinct().forEach { mimeType ->
            val serial = nextSerial.incrementAndGet()
            synchronized(lock) {
                pendingClipboardRequests[serial] = mimeType
            }
            application.runOnEventLoopAsync {
                application.clipboardPaste(serial, listOf(mimeType))
                synchronized(lock) {
                    pendingClipboardRequests.remove(serial)
                }
            }
        }
    }
}
