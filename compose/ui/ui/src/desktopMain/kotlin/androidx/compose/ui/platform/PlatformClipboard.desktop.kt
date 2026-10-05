/*
 * Copyright 2025 The Android Open Source Project
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

package androidx.compose.ui.platform

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.text.AnnotatedString
import java.awt.HeadlessException
import java.awt.Toolkit
import java.awt.datatransfer.ClipboardOwner
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.io.IOException
import java.io.StringReader
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val MIME_TYPE_PLAIN_TEXT = "text/plain"
private const val MIME_TYPE_HTML = "text/html"
private const val MIME_TYPE_URL = "text/uri-list"

private val PLAIN_TEXT_STRING_FLAVOR =
    DataFlavor("$MIME_TYPE_PLAIN_TEXT;class=java.lang.String")
private val HTML_FLAVOR = DataFlavor.fragmentHtmlFlavor
private val URL_FLAVOR = DataFlavor("$MIME_TYPE_URL;class=java.lang.String")

@Suppress("DEPRECATION")
private val AWT_PLAIN_TEXT_FLAVOR = DataFlavor.plainTextFlavor

actual typealias NativeClipboard = Any

private val systemClipboard by lazy {
    try {
        Toolkit.getDefaultToolkit().systemClipboard
    } catch (_: HeadlessException) {
        null
    }
}

@Deprecated(
    "Use AwtPlatformClipboard instead, which supports suspend functions.",
    ReplaceWith("AwtPlatformClipboard", "androidx.compose.ui.platform.AwtPlatformClipboard"),
)
@Suppress("DEPRECATION")
internal class AwtClipboardManager : ClipboardManager {
    override fun getText(): AnnotatedString? =
        getClipboardText()?.let { AnnotatedString(it) }

    override fun setText(annotatedString: AnnotatedString) {
        setClipboardText(annotatedString.text)
    }

    override fun hasText(): Boolean = !getClipboardText().isNullOrEmpty()

    override fun getClip(): ClipEntry? = null

    @Suppress("GetterSetterNames")
    override fun setClip(clipEntry: ClipEntry?) = Unit

    private fun setClipboardText(text: String) {
        systemClipboard?.setContents(StringSelection(text), null)
    }

    private fun getClipboardText(): String? {
        return try {
            systemClipboard?.getData(DataFlavor.stringFlavor) as String?
        } catch (_: UnsupportedFlavorException) {
            null
        } catch (_: IllegalStateException) {
            null
        } catch (_: IOException) {
            null
        }
    }
}

internal class AwtPlatformClipboard internal constructor(
    private val awtClipboard: java.awt.datatransfer.Clipboard? = systemClipboard
) : Clipboard {
    override suspend fun getClipEntry(): ClipEntry? {
        val transferable = awtClipboard?.getContents(null) ?: return null
        if (transferable.transferDataFlavorsSafely().isEmpty()) return null
        return ClipEntry(transferable)
    }

    override suspend fun setClipEntry(clipEntry: ClipEntry?) {
        val transferable = clipEntry?.asAwtTransferable
        try {
            awtClipboard?.setContents(
                /* contents = */ transferable ?: EmptyTransferable,
                /* owner = */ transferable as? ClipboardOwner,
            )
        } catch (_: IllegalStateException) { }  // thrown when clipboard is unavailable
    }

    /**
     * Provides an instance of a platform clipboard.
     * The actual implementation may vary depending on the underlying GUI toolkit.
     * See [awtClipboard] to access [java.awt.datatransfer.Clipboard].
     */
    override val nativeClipboard: NativeClipboard
        get() = awtClipboard ?: NoClipboard
}

/**
 * The object returned as the [NativeClipboard] when [AwtPlatformClipboard.nativeClipboard] is null.
 */
private data object NoClipboard

/**
 * Returns [java.awt.datatransfer.Clipboard] instance if it's available, or null otherwise.
 */
@Suppress("DEPRECATION")
@ExperimentalComposeUiApi
val Clipboard.awtClipboard: java.awt.datatransfer.Clipboard?
    get() = nativeClipboard as? java.awt.datatransfer.Clipboard

/**
 * A wrapper for a platform clip entry instance which can be used to access
 * or set the Clipboard content. The actual implementation may vary
 * depending on the underlying GUI toolkit and on the actual implementation
 * of Clipboard.nativeClipboard.
 *
 * See [asAwtTransferable] to access [Transferable].
 */
actual class ClipEntry
@ExperimentalComposeUiApi
constructor(
    @property:ExperimentalComposeUiApi
    val nativeClipEntry: Any
) {
    actual val clipMetadata: ClipMetadata = createClipMetadata()

    private fun createClipMetadata(): ClipMetadata {
        val flavors = (nativeClipEntry as? Transferable)?.transferDataFlavorsSafely().orEmpty()
        val hasPlainText = flavors.any { it.isPlainTextFlavor() }
        val hasHtml = flavors.any { it.isHtmlFlavor() }
        val hasUrl = flavors.any { it.isUrlFlavor() }
        return ClipMetadata(
            hasTextValue = hasPlainText || hasHtml || hasUrl,
            hasPlainTextValue = hasPlainText,
            hasHtmlValue = hasHtml,
            hasUrlValue = hasUrl,
        )
    }

    actual suspend fun readText(): String? = readPlainText() ?: readHtml() ?: readUrl()

    actual suspend fun readPlainText(): String? = readRepresentation { it.isPlainTextFlavor() }

    actual suspend fun readHtml(): String? = readRepresentation { it.isHtmlFlavor() }

    actual suspend fun readUrl(): String? = readRepresentation { it.isUrlFlavor() }

    private suspend fun readRepresentation(
        isSupportedFlavor: (DataFlavor) -> Boolean
    ): String? =
        withContext(Dispatchers.IO) {
            val transferable = nativeClipEntry as? Transferable ?: return@withContext null
            for (flavor in transferable.transferDataFlavorsSafely()) {
                if (!isSupportedFlavor(flavor)) continue
                val value =
                    try {
                        if (flavor.isFlavorTextType) {
                            flavor.getReaderForText(transferable).use { it.readText() }
                        } else {
                            transferable.getTransferData(flavor)?.toString()
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        null
                    }
                if (value != null) return@withContext value
            }
            null
        }

    actual companion object {
        actual fun withText(plainText: String, html: String?): ClipEntry =
            createClipEntry(createRepresentationTransferable(plainText, html, null))

        actual fun withUrl(url: String, plainText: String?, html: String?): ClipEntry =
            createClipEntry(createRepresentationTransferable(plainText, html, url))
    }
}

private fun Transferable.transferDataFlavorsSafely(): Array<DataFlavor> =
    try {
        transferDataFlavors
    } catch (_: Exception) {
        emptyArray()
    }

private fun DataFlavor.isPlainTextFlavor(): Boolean =
    this == DataFlavor.stringFlavor || isMimeTypeEqual(MIME_TYPE_PLAIN_TEXT)

private fun DataFlavor.isHtmlFlavor(): Boolean = isMimeTypeEqual(MIME_TYPE_HTML)

private fun DataFlavor.isUrlFlavor(): Boolean = isMimeTypeEqual(MIME_TYPE_URL)

private fun DataFlavor.matches(requestedFlavor: DataFlavor): Boolean =
    this == requestedFlavor ||
        (
            isMimeTypeEqual(requestedFlavor) &&
                representationClass == requestedFlavor.representationClass
        )

@OptIn(ExperimentalComposeUiApi::class)
private fun createClipEntry(transferable: Transferable): ClipEntry = ClipEntry(transferable)

@Suppress("DEPRECATION")
private fun createRepresentationTransferable(
    plainText: String?,
    html: String?,
    url: String?,
): Transferable {
    val representations = mutableListOf<Pair<DataFlavor, String>>()
    plainText?.let {
        representations.add(DataFlavor.stringFlavor to it)
        representations.add(PLAIN_TEXT_STRING_FLAVOR to it)
        representations.add(AWT_PLAIN_TEXT_FLAVOR to it)
    }
    html?.let { representations.add(HTML_FLAVOR to it) }
    url?.let { representations.add(URL_FLAVOR to it) }
    return AwtClipEntryTransferable(representations)
}

private class AwtClipEntryTransferable(
    private val representations: List<Pair<DataFlavor, String>>
) : Transferable {
    override fun getTransferDataFlavors(): Array<DataFlavor> =
        representations.map { it.first }.toTypedArray()

    override fun isDataFlavorSupported(flavor: DataFlavor?): Boolean =
        flavor != null && representations.any { (supportedFlavor, _) ->
            supportedFlavor.matches(flavor)
        }

    override fun getTransferData(flavor: DataFlavor?): Any {
        val requestedFlavor = flavor ?: throw UnsupportedFlavorException(DataFlavor.stringFlavor)
        val value =
            representations.firstOrNull { (supportedFlavor, _) ->
                supportedFlavor.matches(requestedFlavor)
            }?.second ?: throw UnsupportedFlavorException(requestedFlavor)
        return if (
            requestedFlavor.isFlavorTextType &&
                requestedFlavor.representationClass != String::class.java
        ) {
            StringReader(value)
        } else {
            value
        }
    }
}

/**
 * Returns a [Transferable] instance if the [ClipEntry.nativeClipEntry]
 * type is [Transferable]. Otherwise, it returns null.
 */
@ExperimentalComposeUiApi
val ClipEntry.asAwtTransferable: Transferable?
    get() = nativeClipEntry as? Transferable

private object EmptyTransferable : Transferable {
    override fun getTransferDataFlavors(): Array<DataFlavor> {
        return emptyArray()
    }

    override fun isDataFlavorSupported(flavor: DataFlavor?): Boolean = false

    override fun getTransferData(flavor: DataFlavor?): Any {
        throw UnsupportedFlavorException(flavor)
    }
}

@Suppress("DEPRECATION")
internal actual fun createPlatformClipboardManager(): ClipboardManager = AwtClipboardManager()

internal actual fun createPlatformClipboard(): Clipboard = AwtPlatformClipboard()

