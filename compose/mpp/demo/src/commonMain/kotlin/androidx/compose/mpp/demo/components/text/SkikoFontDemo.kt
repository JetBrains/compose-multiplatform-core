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

@file:OptIn(ExperimentalTextApi::class)

package androidx.compose.mpp.demo.components.text

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.Checkbox
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Slider
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontLoadingStrategy
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.SkikoFont
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Data
import org.jetbrains.skia.FontMgr
import org.jetbrains.skia.Typeface

// A monospace font, so the switch from the fallback font is easy to see.
private const val DemoFontFile = "Inconsolata-Regular.ttf"

private enum class LoadStatus { Loading, Loaded, Failed }

private const val SampleText =
    "Compose Multiplatform is a declarative framework for sharing UI code across " +
        "multiple platforms with Kotlin."

/**
 * Shows a [FontLoadingStrategy.Async] [SkikoFont]: text is drawn with the fallback font first and
 * reflows once the simulated load completes.
 */
@Composable
fun SkikoFontDemo() {
    var loadDelayMillis by remember { mutableStateOf(2_000f) }
    var failLoad by remember { mutableStateOf(false) }
    // Loaded typefaces stay cached, so each run uses a new identity to load the font again.
    var run by remember { mutableIntStateOf(0) }
    val loadStatus = remember(run) { mutableStateOf(LoadStatus.Loading) }
    val fontFamily = remember(run) {
        val typefaceLoader =
            DelayedResourceTypefaceLoader(loadDelayMillis.roundToLong(), failLoad) { loaded ->
                loadStatus.value = if (loaded) LoadStatus.Loaded else LoadStatus.Failed
            }
        FontFamily(DemoFont(identity = "$DemoFontFile#$run", typefaceLoader = typefaceLoader))
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Load delay: ${loadDelayMillis.roundToLong()} ms (applies to the next load)")
        Slider(
            value = loadDelayMillis,
            onValueChange = { loadDelayMillis = it },
            valueRange = 0f..10_000f,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = failLoad, onCheckedChange = { failLoad = it })
            Text("Fail the next load (text keeps the fallback font)")
        }
        Button(onClick = { run++ }) {
            Text("Load again")
        }
        Divider()
        LoadStatusText(loadStatus.value)
        Spacer(Modifier.height(8.dp))
        Text(SampleText, style = TextStyle(fontFamily = fontFamily, fontSize = 24.sp))
    }
}

@Composable
private fun LoadStatusText(status: LoadStatus) {
    val (text, color) = when (status) {
        LoadStatus.Loading ->
            "Loading... The text below uses the fallback font." to
                MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
        LoadStatus.Loaded ->
            "Loaded. The text below uses Inconsolata, a monospace font." to
                MaterialTheme.colors.primary
        LoadStatus.Failed ->
            "Load failed. The text below keeps the fallback font." to MaterialTheme.colors.error
    }
    Text(text, color = color, style = MaterialTheme.typography.subtitle1)
}

/**
 * Loads the bundled font file after [delayMillis], imitating a slow network download, and reports
 * through [onFinished] whether it succeeded.
 */
private class DelayedResourceTypefaceLoader(
    private val delayMillis: Long,
    private val fail: Boolean,
    private val onFinished: (loaded: Boolean) -> Unit,
) : SkikoFont.TypefaceLoader {
    override fun loadBlocking(font: SkikoFont): Typeface? = null

    override suspend fun awaitLoad(font: SkikoFont): Typeface? =
        loadTypeface().also { onFinished(it != null) }

    private suspend fun loadTypeface(): Typeface? {
        delay(delayMillis)
        if (fail) return null
        val bytes = loadResource(DemoFontFile) ?: return null
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

private class DemoFont(
    override val identity: String,
    typefaceLoader: SkikoFont.TypefaceLoader,
) : SkikoFont(
    FontLoadingStrategy.Async,
    typefaceLoader,
    FontVariation.Settings(FontWeight.Normal, FontStyle.Normal),
) {
    override val weight: FontWeight = FontWeight.Normal
    override val style: FontStyle = FontStyle.Normal
}
