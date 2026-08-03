package androidx.compose.ui.desktop.macos

import androidx.compose.ui.desktop.ClipboardEntry
import androidx.compose.ui.desktop.ClipboardFormat
import androidx.compose.ui.desktop.ClipboardItem
import androidx.compose.ui.desktop.ClipboardItemsEntry
import androidx.compose.ui.desktop.LightweightWindowId
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import java.nio.file.Path
import kotlinx.coroutines.withContext
import org.jetbrains.desktop.macos.Pasteboard
import org.jetbrains.desktop.macos.PasteboardType

object MacOsClipboard : Clipboard {
    override fun getClipEntrySync(): ClipEntry {
        return ClipEntry(MacOsClipboardEntry(PasteboardType.General))
    }

    override suspend fun getClipEntry(): ClipEntry? = getClipEntrySync()

    override suspend fun setClipEntry(clipEntry: ClipEntry?) {
        clipEntry ?: return
        val entry = clipEntry.nativeClipEntry
        require(entry is ClipboardItemsEntry)
        withContext(MacOsKdtMainDispatcher.INSTANCE.immediate) {
            Pasteboard.clear()
            Pasteboard.writeObjects(entry.items.toPasteboardItems())
        }
    }

    override val nativeClipboard: Any
        get() = Pasteboard
}

internal fun macOsClipboardEntry(vararg items: ClipboardItem): ClipboardItemsEntry {
    return ClipboardItemsEntry(items.toList())
}

internal fun List<ClipboardItem>.toPasteboardItems(): List<Pasteboard.Item> {
    return flatMap { item ->
        val elements = item.elements.flatMap {
            @Suppress("UNCHECKED_CAST")
            when (it.format) {
                ClipboardFormat.Utf8PlainText -> listOf(
                    Pasteboard.Element.ofString(
                        UniformTypeIdentifiers.utf8PlainText,
                        it.value as String,
                    ),
                )
                ClipboardFormat.Html -> listOf(
                    Pasteboard.Element.ofString(
                        UniformTypeIdentifiers.html,
                        it.value as String,
                    ),
                )
                ClipboardFormat.File -> listOf(
                    Pasteboard.Element.ofFilePath(Path.of(it.value as String)),
                )
                ClipboardFormat.Png -> listOf(
                    Pasteboard.Element(UniformTypeIdentifiers.png, it.value as ByteArray),
                )
                ClipboardFormat.WindowLocalDrag -> listOf(
                    Pasteboard.Element.ofString(
                        UniformTypeIdentifiers.windowLocalDrag,
                        (it.value as LightweightWindowId).value.toString(),
                    ),
                )
                is ClipboardFormat.CustomSerializable<*> -> listOf(
                    Pasteboard.Element.ofString(
                        it.format.toUniformTypeIdentifier(),
                        (it.format as ClipboardFormat.CustomSerializable<Any>).encode(it.value),
                    ),
                )
            }
        }

        listOf(Pasteboard.Item(elements))
    }
}

class MacOsClipboardEntry(private val pasteboardType: PasteboardType) : ClipboardEntry {
    override suspend fun <T : Any> getForFormat(format: ClipboardFormat<T>): List<T> {
        return withContext(MacOsKdtMainDispatcher.INSTANCE.immediate) {
            getForFormatSync(format)
        }
    }

    override fun <T : Any> getForFormatSync(format: ClipboardFormat<T>): List<T> {
        return decodeClipboardData(
            format,
            readBytesForType = { uti -> Pasteboard.readItemsOfType(uti, pasteboardType) },
            readFilePaths = { Pasteboard.readFileItemPaths(pasteboardType).map { it.toString() } },
        )
    }
}

private fun <T : Any> decodeClipboardData(
    format: ClipboardFormat<T>,
    readBytesForType: (uti: String) -> List<ByteArray>,
    readFilePaths: () -> List<String>,
): List<T> {
    @Suppress("UNCHECKED_CAST")
    return when (format) {
        ClipboardFormat.Utf8PlainText -> readBytesForType(UniformTypeIdentifiers.utf8PlainText)
            .map { String(it, charset = Charsets.UTF_8) }
        ClipboardFormat.Html -> readBytesForType(UniformTypeIdentifiers.html)
            .map { String(it, charset = Charsets.UTF_8) }
        ClipboardFormat.Png -> readBytesForType(UniformTypeIdentifiers.png)
        ClipboardFormat.File -> readFilePaths()
        ClipboardFormat.WindowLocalDrag -> readBytesForType(UniformTypeIdentifiers.windowLocalDrag)
            .mapNotNull { String(it, charset = Charsets.UTF_8).toLongOrNull()?.let(::LightweightWindowId) }
        is ClipboardFormat.CustomSerializable<*> -> readBytesForType(format.toUniformTypeIdentifier())
            .map { format.decode(String(it, charset = Charsets.UTF_8)) }
    } as List<T>
}

internal object UniformTypeIdentifiers {
    const val utf8PlainText = Pasteboard.STRING_TYPE
    const val html = Pasteboard.HTML_TYPE
    const val file = Pasteboard.FILE_URL_TYPE
    const val png = Pasteboard.PNG_IMAGE_TYPE
    const val windowLocalDrag = "org.jetbrains.fleet.window-local-drag"
}

internal fun <T : Any> ClipboardFormat<T>.toUniformTypeIdentifier(): String {
    return when (this) {
        ClipboardFormat.Utf8PlainText -> UniformTypeIdentifiers.utf8PlainText
        ClipboardFormat.Html -> UniformTypeIdentifiers.html
        ClipboardFormat.File -> UniformTypeIdentifiers.file
        ClipboardFormat.Png -> UniformTypeIdentifiers.png
        ClipboardFormat.WindowLocalDrag -> UniformTypeIdentifiers.windowLocalDrag
        is ClipboardFormat.CustomSerializable<*> -> "org.jetbrains.fleet.${
            mimeType.replace(
                "/",
                "-",
            )
        }"
    }
}
