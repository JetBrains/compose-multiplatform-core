/*
 * Copyright 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.compose.ui.desktop.windows

import androidx.compose.ui.desktop.KdtMainDispatcher
import androidx.compose.ui.desktop.ClipboardEntry
import androidx.compose.ui.desktop.ClipboardFormat
import androidx.compose.ui.desktop.ClipboardItem
import androidx.compose.ui.desktop.ClipboardItemsEntry
import androidx.compose.ui.desktop.LightweightWindowId
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import java.lang.ref.Cleaner
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jetbrains.desktop.win32.Application as Win32Application
import org.jetbrains.desktop.win32.Clipboard as Win32Clipboard
import org.jetbrains.desktop.win32.ClipboardResult
import org.jetbrains.desktop.win32.DataFormat as Win32DataFormat
import org.jetbrains.desktop.win32.DataObject
import org.jetbrains.desktop.win32.DataObjectBuilder
import org.jetbrains.desktop.win32.DataTransferStatus
import org.jetbrains.desktop.win32.isBusy

private const val WINDOW_LOCAL_DRAG_MIME_TYPE = "org.jetbrains.fleet.window-local-drag"

private val pngFormat by lazy { Win32DataFormat.register("PNG") }
private val windowLocalDragFormat by lazy {
    Win32DataFormat.register(
        WINDOW_LOCAL_DRAG_MIME_TYPE,
    )
}

/**
 * Checks that the caller runs on the application dispatcher thread (the OLE STA). Every
 * [Win32Clipboard] and [DataObject] call requires that thread; the toolkit documents calling from
 * any other thread as undefined behavior.
 */
private fun checkOnDispatcherThread() {
    check(currentWindowsNativeApplication().isDispatcherThread()) {
        "Windows clipboard access must run on the application dispatcher thread (OLE STA)"
    }
}

class WindowsClipboard : Clipboard {
    override fun getClipEntrySync(): ClipEntry {
        checkOnDispatcherThread()
        check(clipboardMutex.tryLock()) {
            "Cannot synchronously block the application dispatcher thread (OLE STA) while waiting" +
                "for another caller to release the clipboard mutex because they're also expected " +
                "to run on the application dispatcher thread (OLE STA); Refactor to use " +
                "suspending clipboard access instead"
        }
        val dataObject = try {
            Win32Clipboard.withBusyRetry { get() }
        } finally {
            clipboardMutex.unlock()
        }
        return ClipEntry(WindowsClipboardEntry(dataObject))
    }

    override suspend fun getClipEntry(): ClipEntry =
        withContext(KdtMainDispatcher.INSTANCE.immediate) {
            val dataObject = clipboardMutex.withLock { Win32Clipboard.withRetry { get() } }
            ClipEntry(WindowsClipboardEntry(dataObject))
        }

    override suspend fun setClipEntry(clipEntry: ClipEntry?): Unit {
        clipEntry ?: return
        withContext(KdtMainDispatcher.INSTANCE.immediate) {
            val entry = clipEntry.nativeClipEntry
            when (entry) {
                is ClipboardItemsEntry -> {
                    DataObject.build {
                        addClipboardItems(entry.items.toWindowsClipboardItems())
                    }.use { data ->
                        clipboardMutex.withLock {
                            Win32Clipboard.withRetry { set(data) }
                            Win32Clipboard.withRetry { flush() }
                        }
                    }
                }
                is WindowsClipboardEntry -> {
                    clipboardMutex.withLock {
                        Win32Clipboard.withRetry { set(entry.dataObject) }
                        Win32Clipboard.withRetry { flush() }
                    }
                }
                else -> throw IllegalArgumentException()
            }
        }
    }

    override val nativeClipboard: Any
        get() = Win32Clipboard
}

internal fun List<ClipboardItem>.toWindowsClipboardItems(): List<WindowsClipboardItem> {
    return flatMap { item ->
        item.elements.map { element ->
            @Suppress("UNCHECKED_CAST")
            when (element.format) {
                ClipboardFormat.Utf8PlainText -> WindowsClipboardItem.Text(element.value as String)
                ClipboardFormat.Html -> WindowsClipboardItem.Html(element.value as String)
                ClipboardFormat.File -> WindowsClipboardItem.Files(listOf(element.value as String))
                ClipboardFormat.Png -> WindowsClipboardItem.Png(element.value as ByteArray)
                ClipboardFormat.WindowLocalDrag -> WindowsClipboardItem.Custom(
                    WINDOW_LOCAL_DRAG_MIME_TYPE,
                    (element.value as LightweightWindowId).value.toString()
                        .toByteArray(Charsets.UTF_8),
                )
                is ClipboardFormat.CustomSerializable<*> -> WindowsClipboardItem.Custom(
                    element.format.mimeType,
                    (element.format as ClipboardFormat.CustomSerializable<Any>).encode(element.value)
                        .toByteArray(),
                )
            }
        }
    }
}

// TODO[unterhofer]: Lift the `AutoCloseable` interface up into `ClipboardEntry` and have the user
//  manage the native resource lifetime instead of relying on a shared cleaner.
/**
 * Shared cleaner that releases the native data object of any [WindowsClipboardEntry] collected
 * without having been [closed][WindowsClipboardEntry.close].
 */
private val clipboardEntryCleaner: Cleaner = Cleaner.create()

/**
 * Snapshot of the clipboard captured by [WindowsClipboard.getClipEntrySync], backed by the OLE
 * [DataObject] read at that moment. Reads pull directly from that data object, so the entry reflects
 * the clipboard contents at capture time rather than at read time.
 *
 * The entry owns the data object's COM reference. Reading the clipboard does not transfer ownership
 * to the reader, so callers are not required to [close]: the registered [Cleaner] releases the data
 * object once the entry becomes unreachable. A caller with a known scope may [close] it (on the
 * dispatcher thread) for prompt native release instead.
 */
class WindowsClipboardEntry(internal val dataObject: DataObject) : ClipboardEntry, AutoCloseable {
    // The cleanup action must not capture `this`, otherwise the entry could never become collectible.
    private val cleanable: Cleaner.Cleanable =
        clipboardEntryCleaner.register(
            this,
            DataObjectReleaser(dataObject, currentWindowsNativeApplication()),
        )

    override suspend fun <T : Any> getForFormat(format: ClipboardFormat<T>): List<T> =
        withContext(KdtMainDispatcher.INSTANCE.immediate) { getForFormatSync(format) }

    override fun <T : Any> getForFormatSync(format: ClipboardFormat<T>): List<T> {
        checkOnDispatcherThread()
        return dataObject.readForFormat(format)
    }

    override fun close() {
        checkOnDispatcherThread()
        cleanable.clean()
    }

    /**
     * Releases the data object on the dispatcher thread (the OLE STA). Runs from the [Cleaner]'s
     * thread, marshaling onto the dispatcher, in the usual case. Holds no reference to
     * the enclosing entry, so it stays collectible.
     */
    private class DataObjectReleaser(
        private val dataObject: DataObject,
        private val application: Win32Application,
    ) : Runnable {
        override fun run() {
            if (application.isDispatcherThread()) {
                dataObject.close()
            } else {
                application.invokeOnDispatcher { dataObject.close() }
            }
        }
    }
}

internal fun <T : Any> DataObject.readForFormat(format: ClipboardFormat<T>): List<T> {
    @Suppress("UNCHECKED_CAST")
    return when (format) {
        ClipboardFormat.Utf8PlainText -> {
            val text = tryReadTextItem()
            if (text != null) listOf(text) else emptyList()
        }
        ClipboardFormat.Html -> {
            val html = tryReadHtmlFragment()
            if (html != null) listOf(html) else emptyList()
        }
        ClipboardFormat.Png -> {
            val bytes = tryReadItemOfType(pngFormat)
            if (bytes != null) listOf(bytes) else emptyList()
        }
        ClipboardFormat.File -> {
            tryReadListOfFiles() ?: emptyList()
        }
        ClipboardFormat.WindowLocalDrag -> {
            val bytes = tryReadItemOfType(windowLocalDragFormat)
            if (bytes != null) {
                val id = String(bytes, charset = Charsets.UTF_8).toLongOrNull()
                if (id != null) listOf(LightweightWindowId(id)) else emptyList()
            } else {
                emptyList()
            }
        }
        is ClipboardFormat.CustomSerializable<*> -> {
            val customFormat = Win32DataFormat.register(format.mimeType)
            val bytes = tryReadItemOfType(customFormat)
            if (bytes != null) {
                listOf(format.decode(String(bytes, charset = Charsets.UTF_8)))
            } else {
                emptyList()
            }
        }
    } as List<T>
}

sealed interface WindowsClipboardItem {
    data class Text(val text: String) : WindowsClipboardItem
    data class Html(val html: String) : WindowsClipboardItem
    data class Files(val files: List<String>) : WindowsClipboardItem
    data class Png(val bytes: ByteArray) : WindowsClipboardItem
    data class Custom(val mimeType: String, val data: ByteArray) : WindowsClipboardItem
}

internal fun DataObjectBuilder.addClipboardItems(items: List<WindowsClipboardItem>) {
    for (item in items) {
        when (item) {
            is WindowsClipboardItem.Text -> addTextItem(item.text)
            is WindowsClipboardItem.Html -> addHtmlFragment(item.html)
            is WindowsClipboardItem.Files -> addListOfFiles(item.files)
            is WindowsClipboardItem.Png -> addItemOfType(pngFormat, item.bytes)
            is WindowsClipboardItem.Custom -> addItemOfType(
                Win32DataFormat.register(item.mimeType),
                item.data,
            )
        }
    }
}

internal fun ClipboardFormat<*>.toWin32DataFormat(): Win32DataFormat? = when (this) {
    ClipboardFormat.Utf8PlainText -> Win32DataFormat.Text
    ClipboardFormat.Html -> Win32DataFormat.Html
    ClipboardFormat.Png -> pngFormat
    ClipboardFormat.File -> Win32DataFormat.FileList
    ClipboardFormat.WindowLocalDrag -> windowLocalDragFormat
    is ClipboardFormat.CustomSerializable<*> -> Win32DataFormat.register(mimeType)
}


private val clipboardMutex = Mutex()

/**
 * Retrying the transient case where another process holds the clipboard open is necessary to
 * achieve reliable clipboard integration: Win32Clipboard reports it as a busy [ClipboardResult.Failure].
 */
private suspend fun <T> Win32Clipboard.withRetry(operation: Win32Clipboard.() -> ClipboardResult<T>): T {
    lateinit var latestFailure: ClipboardResult.Failure
    for (retryDelay in clipboardRetryDelays) {
        when (val result = operation()) {
            is ClipboardResult.Success<T> -> return result.value
            is ClipboardResult.Failure if result.isBusy -> {
                latestFailure = result
                delay(retryDelay)
            }
            is ClipboardResult.Failure -> throw ClipboardException(result)
        }
    }
    throw ClipboardException(latestFailure)
}

private val clipboardRetryDelays = listOf(
    10.milliseconds,
    25.milliseconds,
    50.milliseconds,
    100.milliseconds,
    200.milliseconds,
    400.milliseconds,
    800.milliseconds,
)

/**
 * Retrying the transient case where another process holds the clipboard open is necessary to
 * achieve reliable clipboard integration: Win32Clipboard reports it as a busy [ClipboardResult.Failure].
 */
private fun <T> Win32Clipboard.withBusyRetry(operation: Win32Clipboard.() -> ClipboardResult<T>): T {
    lateinit var latestFailure: ClipboardResult.Failure
    // busy retry fails earlier and tries more often to not block the UI thread as long
    repeat(CLIPBOARD_RETRY_ATTEMPT_COUNT) {
        when (val result = operation()) {
            is ClipboardResult.Success -> return result.value
            is ClipboardResult.Failure if result.isBusy -> {
                // Recorded so exhausting the retries throws ClipboardException(latestFailure)
                // below rather than tripping the lateinit. (Upstream Noria never assigns it —
                // 8 consecutive busy results there crash with UninitializedPropertyAccessException.)
                latestFailure = result
                Thread.sleep(CLIPBOARD_BUSY_RETRY_SLEEP_MS)
            }
            is ClipboardResult.Failure -> throw ClipboardException(result)
        }
    }
    throw ClipboardException(latestFailure)
}

private const val CLIPBOARD_RETRY_ATTEMPT_COUNT = 8
private const val CLIPBOARD_BUSY_RETRY_SLEEP_MS = 10L

class ClipboardException(failure: ClipboardResult.Failure) :
    RuntimeException(failure.clipboardExceptionMessage()) {
    val status: DataTransferStatus = failure.status
    val nativeCode: Int = failure.nativeCode
    val nativeMessage: String? = failure.message
}

private fun ClipboardResult.Failure.clipboardExceptionMessage(): String {
    val detail = if (nativeCode == 0) {
        ""
    } else {
        " (HRESULT 0x${nativeCode.toUInt().toString(16).padStart(8, '0')})"
    }
    val message = message?.takeIf { it.isNotBlank() }?.let { ": $it" } ?: ""
    return "Clipboard operation failed: $status$detail$message"
}
