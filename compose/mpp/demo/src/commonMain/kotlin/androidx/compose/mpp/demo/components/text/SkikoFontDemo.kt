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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontLoadingStrategy
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.SkikoFont
import androidx.compose.ui.text.font.toFontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.io.IOException
import org.jetbrains.skia.Data
import org.jetbrains.skia.FontMgr
import org.jetbrains.skia.Typeface

@Composable
fun SkikoFontDemo() {
    val downloader = remember { Downloader() }
    DisposableEffect(downloader) {
        onDispose { downloader.close() }
    }

    val fontUrl =
        "https://fonts.gstatic.com/s/betaniapatmos/v2/9oRXNYMTrDYnkuhOrHhyQracaunDNbEH8qpU.ttf"
    val fontFamilyResolver = LocalFontFamilyResolver.current
    val fontFamily = remember {
        // identity is the unvaried source key (URL/file id), not weight/style.
        Font(
            identity = fontUrl,
            loadData = { downloader.downloadBytes(url = fontUrl) ?: error("download error") },
            weight = FontWeight.Normal,
            style = FontStyle.Normal,
            loadingStrategy = FontLoadingStrategy.Async,
        ).toFontFamily()
    }
    LaunchedEffect(Unit) {
        fontFamilyResolver.preload(fontFamily)
    }
    Column {
        val textFieldState =
            rememberTextFieldState(
                "Compose Multiplatform is a declarative framework for sharing UI code " +
                    "across multiple platforms with Kotlin. It is based on Jetpack Compose " +
                    "and developed by JetBrains and open-source contributors."
            )
        TextField(
            state = textFieldState,
            modifier = Modifier.fillMaxWidth().height(200.dp),
            textStyle = TextStyle(
                fontFamily = fontFamily,
                fontSize = 30.sp
            )
        )
    }
}

private fun Font(
    identity: String,
    loadData: suspend () -> ByteArray,
    weight: FontWeight,
    style: FontStyle,
    loadingStrategy: FontLoadingStrategy,
    variationSettings: FontVariation.Settings = FontVariation.Settings(weight, style)
): Font = DataFont(identity, loadData, weight, style, loadingStrategy, variationSettings)

private class DataFont(
    override val identity: String,
    loadData: suspend () -> ByteArray,
    override val weight: FontWeight,
    override val style: FontStyle,
    loadingStrategy: FontLoadingStrategy,
    variationSettings: FontVariation.Settings
) : SkikoFont(loadingStrategy, ByteArrayTypefaceLoader(loadData), variationSettings) {

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

private class ByteArrayTypefaceLoader(
    private val loadData: suspend () -> ByteArray
) : SkikoFont.TypefaceLoader {

    override fun loadBlocking(font: SkikoFont): Typeface? = null

    override suspend fun awaitLoad(font: SkikoFont): Typeface? {
        val bytes = loadData()
        val data = Data.makeFromBytes(bytes)
        return try {
            FontMgr.default.makeFromData(data)
        } finally {
            data.close()
        }
    }
}

private class Downloader {
    private val client = HttpClient()
    private val maxRetries = 3
    private val retryDelay = 2.seconds

    suspend fun downloadBytes(url: String): ByteArray? {
        var attempt = 0
        while (attempt <= maxRetries) {
            try {
                val response: HttpResponse = client.get(url)
                return response.body()
            } catch (e: Exception) {
                if (isRetryableError(e) && attempt < maxRetries) {
                    delay(retryDelay)
                    attempt++
                } else {
                    return null
                }
            }
        }
        return null
    }

    fun close() {
        client.close()
    }

    private fun isRetryableError(error: Throwable): Boolean {
        return when (error) {
            is IOException -> true
            is io.ktor.client.plugins.HttpRequestTimeoutException -> true
            is io.ktor.client.network.sockets.SocketTimeoutException -> true
            is io.ktor.client.network.sockets.ConnectTimeoutException -> true
            else -> false
        }
    }
}
