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
                    .verticalScroll(scrollState)
                    .padding(16.dp)
            ) {
                val rockMusicians = listOf(
                    "Jimi Hendrix", "Eric Clapton", "Jimmy Page", "Jeff Beck", "Keith Richards",
                    "Brian Jones", "Mick Jagger", "Robert Plant", "John Bonham", "David Gilmour", "Roger Waters",
                    "Pete Townshend", "Roger Daltrey", "Kurt Cobain", "Eddie Vedder", "Chris Cornell", "Layne Staley", "Axl Rose",
                    "Slash", "Steven Tyler", "Joe Perry", "Freddie Mercury", "Brian May", "John Deacon",
                    "Bono", "The Edge", "Adam Clayton", "Larry Mullen Jr.", "Bruce Springsteen", "Bob Dylan",
                    "Chuck Berry", "Elvis Presley", "Little Richard", "Jerry Lee Lewis", "Janis Joplin",
                    "Jim Morrison", "John Lennon", "Paul McCartney", "George Harrison", "Ringo Starr",
                    "David Bowie", "Iggy Pop", "Lou Reed", "Patti Smith", "Johnny Ramone", "Joey Ramone",
                    "Dee Dee Ramone", "Marky Ramone", "Sid Vicious", "Steve Jones", "Paul Simonon", "Billy Idol",
                    "Ozzy Osbourne", "Tony Iommi", "Geezer Butler", "Bill Ward", "Robert Trujillo", "Angus Young",
                    "Malcolm Young", "Brian Johnson", "Bon Scott", "Lemmy Kilmister", "Phil Campbell", "Mikkey Dee",
                    "Prince", "Stevie Ray Vaughan", "Jeff Beck", "Eric Clapton", "Carlos Santana", "Jack White",
                    "Dave Grohl", "Eddie Van Halen", "Alex Van Halen", "David Lee Roth", "Sammy Hagar",
                    "Robert Plant", "Jimmy Page", "John Paul Jones", "Keith Richards", "Mick Jagger", "Brian Jones",
                    "Bruce Springsteen", "Stevie Wonder", "Bob Marley", "John Lennon", "Paul McCartney", "George Harrison",
                    "Ringo Starr", "Freddie Mercury", "Brian May", "Roger Taylor", "Chuck Berry", "Little Richard",
                    "Elvis Presley", "Jerry Lee Lewis", "Janis Joplin", "Jim Morrison"
                )

                rockMusicians.forEachIndexed { index, rockMusician ->
                    Text(text = "[#$index] $rockMusician", modifier = Modifier.padding(vertical = 8.dp))
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

