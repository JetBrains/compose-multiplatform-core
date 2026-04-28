package androidx.compose.ui.desktop.macos

import androidx.compose.ui.desktop.ClipboardEntry
import androidx.compose.ui.desktop.ClipboardFormat
import androidx.compose.ui.desktop.ClipboardItem
import androidx.compose.ui.desktop.WindowLocalDragData
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import java.nio.file.Path
import org.jetbrains.desktop.macos.Pasteboard
import org.jetbrains.desktop.macos.PasteboardType
import org.jetbrains.desktop.macos.UrlUtils

object MacOsClipboard : Clipboard {
    override fun getClipEntrySync(): ClipEntry {
        return ClipEntry(MacOsClipboardEntry.Dummy(PasteboardType.General))
    }

    override suspend fun getClipEntry(): ClipEntry? = getClipEntrySync()

    override suspend fun setClipEntry(clipEntry: ClipEntry?) {
        clipEntry ?: return
        require(clipEntry.nativeClipEntry is MacOsClipboardEntry.Items)
        Pasteboard.clear()
        Pasteboard.writeObjects(clipEntry.nativeClipEntry.items)
    }

    override val nativeClipboard: Any
        get() = Pasteboard
}

internal fun macOsClipboardEntry(vararg items: ClipboardItem): MacOsClipboardEntry.Items {
    return MacOsClipboardEntry.Items(
        items.flatMap { item ->
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
                            (it.value as WindowLocalDragData).serialize(),
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
        },
    )
}

sealed interface MacOsClipboardEntry : ClipboardEntry {
    data class Items(val items: List<Pasteboard.Item>) : MacOsClipboardEntry {
        override suspend fun <T : Any> getForFormat(format: ClipboardFormat<T>): List<T> {
            return getForFormatSync(format)
        }

        override fun <T : Any> getForFormatSync(format: ClipboardFormat<T>): List<T> {
            return decodeClipboardData(
                format,
                readBytesForType = { uti -> items.flatMap { it.elements }.filter { it.type == uti }.map { it.content } },
                readFilePaths = {
                    items.flatMap { it.elements }
                        .filter { it.type == UniformTypeIdentifiers.file }
                        .mapNotNull { UrlUtils.urlToFilePath(String(it.content, charset = Charsets.UTF_8)) }
                },
            )
        }
    }

    class Dummy(private val pasteboardType: PasteboardType) : MacOsClipboardEntry {
        override suspend fun <T : Any> getForFormat(format: ClipboardFormat<T>): List<T> {
            return getForFormatSync(format)
        }

        override fun <T : Any> getForFormatSync(format: ClipboardFormat<T>): List<T> {
            return decodeClipboardData(
                format,
                readBytesForType = { uti -> Pasteboard.readItemsOfType(uti, pasteboardType) },
                readFilePaths = { Pasteboard.readFileItemPaths(pasteboardType).map { it.toString() } },
            )
        }
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
            .mapNotNull { WindowLocalDragData.deserialize(String(it, charset = Charsets.UTF_8)) }
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

private fun <T : Any> ClipboardFormat<T>.toUniformTypeIdentifier(): String {
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
