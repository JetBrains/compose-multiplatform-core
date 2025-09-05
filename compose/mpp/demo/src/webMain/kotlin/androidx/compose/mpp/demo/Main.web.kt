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

package androidx.compose.mpp.demo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.mpp.demo.bugs.BugsScreen
import androidx.compose.mpp.demo.components.text.loadResource
import androidx.compose.mpp.demo.interops.HtmlInteropDemos
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ComposeViewport
import androidx.navigation.ExperimentalBrowserHistoryApi
import androidx.navigation.bindToBrowserNavigation
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll

private const val notoColorEmoji = "./NotoColorEmoji.ttf"
private const val notoSansSC = "./NotoSansSC-Regular.ttf"

@OptIn(ExperimentalComposeUiApi::class)
@ExperimentalBrowserHistoryApi
fun main() {
    BoxList()
}

@OptIn(ExperimentalComposeUiApi::class)
@ExperimentalBrowserHistoryApi
private fun BoxList() {
    ComposeViewport(viewportContainerId = "composeApplication") {
        val scrollState = rememberScrollState()
        Box(modifier = Modifier.fillMaxSize().background(Color(0xFFEEEEEE))) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .then(AwaitFirstLayoutModifier())
                    .verticalScroll(scrollState)
                    .padding(16.dp)
            ) {
                repeat(100) { index ->
                    Text(text = "Item #$index", modifier = Modifier.padding(vertical = 8.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@ExperimentalBrowserHistoryApi
private fun App() {
    ComposeViewport(viewportContainerId = "composeApplication") {
        val navController = rememberNavController()
        val fontFamilyResolver = LocalFontFamilyResolver.current
        val fontsLoaded = remember { mutableStateOf(false) }
        val app = remember { App(
            extraScreens = listOf(
                BugsScreen,
                Screen.Example("Web Clipboard API example") {
                    WebClipboardDemo()
                },
                HtmlInteropDemos
            )
        ) }

        if (fontsLoaded.value) {
            app.Content(navController)

            // TODO: possibly suboptimal workaround for https://youtrack.jetbrains.com/issue/CMP-7136/web-Its-non-trivial-to-bind-to-navigation-if-NavHost-is-called-asynchronously
            LaunchedEffect(Unit) {
                navController.bindToBrowserNavigation()
            }
        }

        LaunchedEffect(Unit) {
            val load1 = async {
                loadResource(notoColorEmoji) ?: ByteArray(0)
            }
            val load2 = async {
                loadResource(notoSansSC) ?: ByteArray(0)
            }
            val fontsDeferred = awaitAll(load1, load2).zip(listOf(
                "NotoColorEmoji",
                "NotoSansSC"
            ))

            fontsDeferred.forEach { (font, name) ->
                val fontFamily = FontFamily(listOf(Font(name, font)))
                fontFamilyResolver.preload(fontFamily)
            }

            fontsLoaded.value = true
        }

        LaunchedEffect(Unit) {
            setupBackingTextAreaDebugHints()
        }
    }

}

