package androidx.compose.ui.desktop.linux

import androidx.compose.ui.desktop.*
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import org.jetbrains.desktop.linux.Application
import org.jetbrains.desktop.linux.DataSource
import org.jetbrains.desktop.linux.DataTransferContent
import org.jetbrains.desktop.linux.Event

private val primarySelectionMimeTypes = listOf(Utf8PlainTextMimeType, Utf8PlainTextMimeTypeFallback)

internal class LinuxClipboardEntry(
    private val application: Application,
    private val serialCounter: AtomicInteger,
) : ClipboardEntry {
    private var receivers = mutableMapOf<Int, (DataTransferContent?) -> Unit>()
    private var availableMimeTypes = emptyList<String>()

    override suspend fun <T : Any> getForFormat(format: ClipboardFormat<T>): List<T> {
        val availableMimeTypes = synchronized(this) { availableMimeTypes }

        val mimeTypes = format.linuxMimeTypes().filter { availableMimeTypes.contains(it) }
        if (mimeTypes.isEmpty()) {
            return emptyList()
        }

        return suspendCancellableCoroutine { continuation ->
            application.runOnEventLoopAsync {
                val eventSerial = serialCounter.addAndGet(1)
                val onDataReceive = { content: DataTransferContent? ->
                    val data = content?.let { content ->
                        check(mimeTypes.contains(content.mimeType))
                        decodeMimeData(content.data, format)
                    }.orEmpty()
                    continuation.resume(data)
                }
                receivers[eventSerial] = onDataReceive
                continuation.invokeOnCancellation { receivers.remove(eventSerial) }
                application.clipboardPaste(eventSerial, mimeTypes)
            }
        }
    }

    override fun <T : Any> getForFormatSync(format: ClipboardFormat<T>): List<T> {
        return emptyList()
    }

    fun onDataReceived(event: Event.DataTransfer): Boolean {
        return receivers.remove(event.serial)?.let { f ->
            f(event.content)
        } != null
    }

    fun onDataTransferAvailable(event: Event.DataTransferAvailable) {
        synchronized(this) {
            availableMimeTypes = event.mimeTypes
        }
    }
}

internal class LinuxSystemSelectionEntry(
    private val application: Application,
    private val serialCounter: AtomicInteger,
) {
    private var receivers = mutableMapOf<Int, (DataTransferContent?) -> Unit>()
    private var availableMimeTypes = emptyList<String>()

    suspend fun getString(): String? {
        val availableMimeTypes = synchronized(this) { availableMimeTypes }

        val mimeTypes = primarySelectionMimeTypes.filter { availableMimeTypes.contains(it) }
        if (mimeTypes.isEmpty()) {
            return null
        }

        return suspendCancellableCoroutine { continuation ->
            application.runOnEventLoopAsync {
                val eventSerial = serialCounter.addAndGet(1)
                val onDataReceive = { content: DataTransferContent? ->
                    val data = content?.let { content ->
                        check(mimeTypes.contains(content.mimeType))
                        content.data.decodeToString()
                    }
                    continuation.resume(data)
                }
                receivers[eventSerial] = onDataReceive
                continuation.invokeOnCancellation { receivers.remove(eventSerial) }
                application.primarySelectionPaste(eventSerial, mimeTypes)
            }
        }
    }

    fun onDataReceived(event: Event.DataTransfer): Boolean {
        return receivers.remove(event.serial)?.let { f ->
            f(event.content)
        } != null
    }

    fun onDataTransferAvailable(event: Event.DataTransferAvailable) {
        synchronized(this) {
            availableMimeTypes = event.mimeTypes
        }
    }
}

internal class LinuxClipboard(
    private val application: Application,
) : Clipboard {
    private val serialCounter = AtomicInteger(0)
    private val clipboardEntry = LinuxClipboardEntry(application, serialCounter)
    private val primarySelectionEntry = LinuxSystemSelectionEntry(application, serialCounter)

    private val lock = Any()
    private var clipboardData: ClipboardItemsEntry? = null
    private var primarySelectionData: String? = null

    override fun getClipEntrySync(): ClipEntry {
        return synchronized(lock) {
            ClipEntry(clipboardData ?: clipboardEntry)
        }
    }

    override suspend fun getClipEntry(): ClipEntry = getClipEntrySync()

    override suspend fun setClipEntry(clipEntry: ClipEntry?) {
        val itemsEntry = clipEntry?.nativeClipEntry as? ClipboardItemsEntry ?: return
        val mimeTypes = itemsEntry.linuxMimeTypes()

        synchronized(lock) {
            clipboardData = itemsEntry
        }
        application.runOnEventLoopAsync {
            application.clipboardPut(mimeTypes)
        }
    }

    override suspend fun systemSelection(): String? {
        return synchronized(lock) {
            primarySelectionData
        } ?: primarySelectionEntry.getString()
    }

    override suspend fun setSystemSelection(text: String?) {
        val mimeTypes = if (text != null) {
            listOf(Utf8PlainTextMimeType, Utf8PlainTextMimeTypeFallback)
        } else {
            emptyList()
        }
        synchronized(lock) {
            primarySelectionData = text
        }
        application.runOnEventLoopAsync {
            application.primarySelectionPut(mimeTypes)
        }
    }

    override val nativeClipboard: Any
        get() = application

    fun clearClipboardData() {
        synchronized(lock) {
            clipboardData = null
        }
    }

    fun clearPrimarySelectionData() {
        synchronized(lock) {
            primarySelectionData = null
        }
    }

    fun onDataTransferAvailable(event: Event.DataTransferAvailable) {
        when (event.dataSource) {
            DataSource.Clipboard -> synchronized(lock) {
                clipboardEntry.onDataTransferAvailable(event)
            }
            DataSource.PrimarySelection -> synchronized(lock) {
                primarySelectionEntry.onDataTransferAvailable(event)
            }
            else -> {}
        }
    }

    fun onDataReceived(event: Event.DataTransfer): Boolean {
        return synchronized(lock) {
            clipboardEntry.onDataReceived(event) || primarySelectionEntry.onDataReceived(event)
        }
    }

    fun getMimeData(mimeType: String): ByteArray? {
        return synchronized(lock) {
            clipboardData?.getDataForLinuxMimeType(mimeType)
        }
    }

    fun getPrimarySelectionData(mimeType: String): ByteArray? {
        return synchronized(lock) {
            if (primarySelectionMimeTypes.contains(mimeType)) {
                primarySelectionData?.encodeToByteArray()
            } else {
                null
            }
        }
    }
}
