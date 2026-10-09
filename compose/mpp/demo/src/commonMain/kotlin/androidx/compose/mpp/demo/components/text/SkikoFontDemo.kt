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

package androidx.compose.mpp.demo.components.text

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.Divider
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontLoadingStrategy
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.SkikoFont
import androidx.compose.ui.text.font.toFontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ktor.client.HttpClient
import io.ktor.client.plugins.onDownload
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Data
import org.jetbrains.skia.FontMgr
import org.jetbrains.skia.Typeface

private const val GoogleFontsRoot =
    "https://raw.githubusercontent.com/google/fonts/7085eb89a950e85db5b166b7a58d414544b4140c"

private class DemoFontEntry(val name: String, path: String) {
    val url = "$GoogleFontsRoot/$path"
}

private val DemoFonts = listOf(
    DemoFontEntry("Roboto", "ofl/roboto/Roboto%5Bwdth,wght%5D.ttf"),
    DemoFontEntry("Open Sans", "ofl/opensans/OpenSans%5Bwdth,wght%5D.ttf"),
    DemoFontEntry("Lato", "ofl/lato/Lato-Regular.ttf"),
    DemoFontEntry("Montserrat", "ofl/montserrat/Montserrat%5Bwght%5D.ttf"),
    DemoFontEntry("Poppins", "ofl/poppins/Poppins-Regular.ttf"),
    DemoFontEntry("Playfair Display", "ofl/playfairdisplay/PlayfairDisplay%5Bwght%5D.ttf"),
    DemoFontEntry("Merriweather", "ofl/merriweather/Merriweather%5Bopsz,wdth,wght%5D.ttf"),
    DemoFontEntry("Source Code Pro", "ofl/sourcecodepro/SourceCodePro%5Bwght%5D.ttf"),
    DemoFontEntry("Lobster", "ofl/lobster/Lobster-Regular.ttf"),
    DemoFontEntry("Pacifico", "ofl/pacifico/Pacifico-Regular.ttf"),
)

@Composable
fun SkikoFontDemo() {
    val repository = remember { FontRepository() }
    DisposableEffect(repository) {
        onDispose { repository.close() }
    }
    val typefaceLoader = remember(repository) { DownloadedTypefaceLoader(repository) }
    val scope = rememberCoroutineScope()
    var selected by remember { mutableStateOf(DemoFonts.first()) }
    val select: (DemoFontEntry) -> Unit = { entry ->
        selected = entry
        scope.launch { repository.download(entry) }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val fontList = @Composable { modifier: Modifier ->
            FontList(repository, selected, onSelect = select, modifier = modifier)
        }
        val preview = @Composable { modifier: Modifier ->
            FontPreview(selected, repository.stateOf(selected), typefaceLoader, modifier)
        }
        if (maxWidth < 600.dp) {
            Column(Modifier.fillMaxSize()) {
                preview(Modifier.fillMaxWidth().height(240.dp))
                Divider()
                fontList(Modifier.fillMaxWidth().weight(1f))
            }
        } else {
            Row(Modifier.fillMaxSize()) {
                fontList(Modifier.width(400.dp).fillMaxHeight())
                Box(
                    Modifier.fillMaxHeight()
                        .width(1.dp)
                        .background(MaterialTheme.colors.onSurface.copy(alpha = 0.12f))
                )
                preview(Modifier.weight(1f).fillMaxHeight())
            }
        }
    }
}

@Composable
private fun FontList(
    repository: FontRepository,
    selected: DemoFontEntry,
    onSelect: (DemoFontEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Fonts", Modifier.weight(1f), style = MaterialTheme.typography.subtitle1)
            TextButton(onClick = { repository.removeAll() }) {
                Text("Clear all cache")
            }
        }
        Divider()
        LazyColumn {
            items(DemoFonts) { entry ->
                FontRow(
                    entry = entry,
                    state = repository.stateOf(entry),
                    selected = entry == selected,
                    onSelect = { onSelect(entry) },
                    onRemove = { repository.remove(entry) },
                )
                Divider()
            }
        }
    }
}

@Composable
private fun FontRow(
    entry: DemoFontEntry,
    state: DownloadState,
    selected: Boolean,
    onSelect: () -> Unit,
    onRemove: () -> Unit,
) {
    val background =
        if (selected) {
            MaterialTheme.colors.primary.copy(alpha = 0.08f)
        } else {
            MaterialTheme.colors.surface
        }
    Row(
        Modifier
            .fillMaxWidth()
            .background(background)
            .clickable(onClick = onSelect)
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .heightIn(min = 40.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(entry.name, style = MaterialTheme.typography.body1)
            Text(
                state.description(),
                style = MaterialTheme.typography.caption,
                color = if (state is DownloadState.Failed) {
                    MaterialTheme.colors.error
                } else {
                    MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                }
            )
            if (state is DownloadState.Downloading) {
                val progress = state.progress
                val indicatorModifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                if (progress != null) {
                    LinearProgressIndicator(progress = progress, modifier = indicatorModifier)
                } else {
                    LinearProgressIndicator(modifier = indicatorModifier)
                }
            }
        }
        if (state is DownloadState.Downloaded) {
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = onRemove) { Text("Remove cache") }
        }
    }
}

@Composable
private fun FontPreview(
    entry: DemoFontEntry,
    state: DownloadState,
    typefaceLoader: SkikoFont.TypefaceLoader,
    modifier: Modifier = Modifier,
) {
    val fontFamily = remember(entry, typefaceLoader) {
        // identity is the unvaried source key (URL/file id), not weight/style.
        DataFont(
            identity = entry.url,
            weight = FontWeight.Normal,
            style = FontStyle.Normal,
            loadingStrategy = FontLoadingStrategy.Async,
            typefaceLoader = typefaceLoader,
            variationSettings = FontVariation.Settings(FontWeight.Normal, FontStyle.Normal),
        ).toFontFamily()
    }
    // Resolve the font before handing it to the text field, so the first layout already uses it.
    val fontFamilyResolver = LocalFontFamilyResolver.current
    val downloaded = state is DownloadState.Downloaded
    var loadedFontFamily by remember(fontFamily) { mutableStateOf<FontFamily?>(null) }
    LaunchedEffect(fontFamily, downloaded) {
        if (!downloaded) {
            loadedFontFamily = null
            return@LaunchedEffect
        }
        try {
            fontFamilyResolver.preload(fontFamily)
            loadedFontFamily = fontFamily
        } catch (e: CancellationException) {
            throw e
        } catch (e: IllegalStateException) {
            // The font failed to load, keep showing the default font.
            loadedFontFamily = null
        }
    }
    val textFieldState =
        rememberTextFieldState(
            "Compose Multiplatform is a declarative framework for sharing UI code " +
                "across multiple platforms with Kotlin. It is based on Jetpack Compose " +
                "and developed by JetBrains and open-source contributors."
        )
    Column(modifier.padding(16.dp)) {
        Text(entry.name, style = MaterialTheme.typography.h6)
        Text(
            when {
                loadedFontFamily != null -> "Loaded through SkikoFont (FontLoadingStrategy.Async)"
                downloaded -> "Loading typeface..."
                state is DownloadState.Failed -> "Download failed. Tap the font to retry."
                else -> "Showing the default font until the download finishes."
            },
            style = MaterialTheme.typography.caption,
        )
        Spacer(Modifier.height(8.dp))
        TextField(
            state = textFieldState,
            modifier = Modifier.fillMaxWidth().weight(1f),
            textStyle = TextStyle(
                fontFamily = loadedFontFamily ?: FontFamily.Default,
                fontSize = 30.sp
            )
        )
    }
}

private sealed interface DownloadState {
    data object NotDownloaded : DownloadState

    data class Downloading(val bytesRead: Long, val totalBytes: Long?) : DownloadState {
        val progress: Float?
            get() = totalBytes?.takeIf { it > 0 }?.let { bytesRead.toFloat() / it }
    }

    class Downloaded(val bytes: ByteArray) : DownloadState

    data class Failed(val message: String) : DownloadState
}

private fun DownloadState.description(): String = when (this) {
    DownloadState.NotDownloaded -> "Not downloaded"
    is DownloadState.Downloading -> {
        val progress = progress
        if (progress != null && totalBytes != null) {
            "Downloading ${(progress * 100).toInt()}% " +
                "(${formatBytes(bytesRead)} / ${formatBytes(totalBytes)})"
        } else {
            "Downloading ${formatBytes(bytesRead)}"
        }
    }
    is DownloadState.Downloaded -> "Downloaded, ${formatBytes(bytes.size.toLong())} cached"
    is DownloadState.Failed -> "Failed: $message"
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kbTenths = bytes * 10 / 1024
    if (kbTenths < 10240) return "${kbTenths / 10}.${kbTenths % 10} KB"
    val mbTenths = bytes * 10 / (1024 * 1024)
    return "${mbTenths / 10}.${mbTenths % 10} MB"
}

/** Downloads font files and keeps their bytes in memory, keyed by URL. */
private class FontRepository {
    private val client = HttpClient { expectSuccess = true }
    private val states = mutableStateMapOf<String, DownloadState>()

    fun stateOf(entry: DemoFontEntry): DownloadState =
        states[entry.url] ?: DownloadState.NotDownloaded

    fun downloadedBytes(url: String): ByteArray? =
        (states[url] as? DownloadState.Downloaded)?.bytes

    suspend fun download(entry: DemoFontEntry) {
        val state = stateOf(entry)
        if (state is DownloadState.Downloading || state is DownloadState.Downloaded) return
        states[entry.url] = DownloadState.Downloading(bytesRead = 0, totalBytes = null)
        try {
            val bytes = client.get(entry.url) {
                onDownload { bytesRead, totalBytes ->
                    states[entry.url] = DownloadState.Downloading(bytesRead, totalBytes)
                }
            }.bodyAsBytes()
            states[entry.url] = DownloadState.Downloaded(bytes)
        } catch (e: CancellationException) {
            states.remove(entry.url)
            throw e
        } catch (e: Exception) {
            states[entry.url] = DownloadState.Failed(e.message ?: e::class.simpleName ?: "error")
        }
    }

    /**
     * Drops the downloaded bytes. Typefaces that Compose has already resolved stay in its own font
     * cache, so downloading the same font again won't trigger another load.
     */
    fun remove(entry: DemoFontEntry) {
        if (states[entry.url] is DownloadState.Downloaded) states.remove(entry.url)
    }

    fun removeAll() {
        states.entries.removeAll { it.value is DownloadState.Downloaded }
    }

    fun close() {
        client.close()
    }
}

private class DataFont(
    override val identity: String,
    override val weight: FontWeight,
    override val style: FontStyle,
    loadingStrategy: FontLoadingStrategy,
    typefaceLoader: SkikoFont.TypefaceLoader,
    variationSettings: FontVariation.Settings
) : SkikoFont(loadingStrategy, typefaceLoader, variationSettings) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DataFont) return false
        if (identity != other.identity) return false
        if (weight != other.weight) return false
        if (style != other.style) return false
        if (loadingStrategy != other.loadingStrategy) return false
        if (variationSettings != other.variationSettings) return false
        return true
    }

    override fun hashCode(): Int {
        var result = identity.hashCode()
        result = 31 * result + weight.hashCode()
        result = 31 * result + style.hashCode()
        result = 31 * result + loadingStrategy.hashCode()
        result = 31 * result + variationSettings.hashCode()
        return result
    }
}

/** Builds typefaces from the bytes [FontRepository] has downloaded for [SkikoFont.identity]. */
private class DownloadedTypefaceLoader(
    private val repository: FontRepository
) : SkikoFont.TypefaceLoader {

    override fun loadBlocking(font: SkikoFont): Typeface? = null

    override suspend fun awaitLoad(font: SkikoFont): Typeface? {
        val bytes = repository.downloadedBytes(font.identity) ?: return null
        return withContext(Dispatchers.Default) {
            val data = Data.makeFromBytes(bytes)
            try {
                FontMgr.default.makeFromData(data)
            } finally {
                data.close()
            }
        }
    }
}
