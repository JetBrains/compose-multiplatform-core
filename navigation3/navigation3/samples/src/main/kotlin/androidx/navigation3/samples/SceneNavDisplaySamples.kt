/*
 * Copyright 2024 The Android Open Source Project
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

package androidx.navigation3.samples

import androidx.annotation.Sampled
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.ViewModelStoreNavEntryDecorator
import androidx.navigation3.LocalNavAnimatedContentScope
import androidx.navigation3.NavEntry
import androidx.navigation3.NavEntryDecorator
import androidx.navigation3.SaveableStateNavEntryDecorator
import androidx.navigation3.SavedStateNavEntryDecorator
import androidx.navigation3.Scene
import androidx.navigation3.SceneNavDisplay
import androidx.navigation3.entry
import androidx.navigation3.entryProvider

class ProfileViewModel : ViewModel() {
    val name = "no user"
}

@Sampled
@Composable
fun SceneNav() {
    val backStack = rememberMutableStateListOf(Profile)
    val showDialog = remember { mutableStateOf(false) }
    SceneNavDisplay(
        backStack = backStack,
        postEntryDecorators =
            listOf(
                SaveableStateNavEntryDecorator,
                SavedStateNavEntryDecorator,
                ViewModelStoreNavEntryDecorator
            ),
        onBack = { backStack.removeLast() },
        entryProvider =
            entryProvider({ NavEntry(Unit) { Text(text = "Invalid Key") } }) {
                entry<Profile> {
                    val viewModel = viewModel<ProfileViewModel>()
                    Profile(viewModel, { backStack.add(it) }) { backStack.removeLast() }
                }
                entry<Scrollable> { Scrollable({ backStack.add(it) }) { backStack.removeLast() } }
                entry<DialogBase> {
                    DialogBase(onClick = { showDialog.value = true }) { backStack.removeLast() }
                }
                entry<Dashboard> { dashboardArgs ->
                    val userId = dashboardArgs.userId
                    Dashboard(userId, onBack = { backStack.removeLast() })
                }
            }
    )
    if (showDialog.value) {
        DialogContent(onDismissRequest = { showDialog.value = false })
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Sampled
@Composable
fun <T : Any> SceneNavSharedEntrySample() {

    /** The [SharedTransitionScope] of the [SceneNavDisplay]. */
    val LocalNavSharedTransitionScope: ProvidableCompositionLocal<SharedTransitionScope> =
        compositionLocalOf<SharedTransitionScope> {
            throw IllegalStateException(
                "Unexpected access to LocalNavSharedTransitionScope. You must provide a " +
                    "SharedTransitionScope from a call to SharedTransitionLayout() or " +
                    "SharedTransitionScope()"
            )
        }

    /**
     * A [NavEntryDecorator] that wraps each entry in a shared element that is controlled by the
     * [Scene].
     */
    val sharedEntryInSceneNavEntryDecorator =
        object : NavEntryDecorator {
            @Composable
            override fun <T : Any> DecorateEntry(entry: NavEntry<T>) {
                with(LocalNavSharedTransitionScope.current) {
                    Box(
                        Modifier.sharedElement(
                            rememberSharedContentState(entry.key),
                            animatedVisibilityScope = LocalNavAnimatedContentScope.current,
                        ),
                    ) {
                        entry.content(entry.key)
                    }
                }
            }
        }

    val backStack = rememberMutableStateListOf(CatList)
    SharedTransitionLayout {
        CompositionLocalProvider(LocalNavSharedTransitionScope provides this) {
            SceneNavDisplay(
                backStack = backStack,
                onBack = { backStack.removeLast() },
                preEntryDecorators = listOf(sharedEntryInSceneNavEntryDecorator),
                entryProvider =
                    entryProvider {
                        entry<CatList> {
                            CatList(this@SharedTransitionLayout) { cat ->
                                backStack.add(CatDetail(cat))
                            }
                        }
                        entry<CatDetail> { args ->
                            CatDetail(args.cat, this@SharedTransitionLayout) {
                                backStack.removeLast()
                            }
                        }
                    }
            )
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Sampled
@Composable
fun <T : Any> SceneNavSharedElementSample() {
    val backStack = rememberMutableStateListOf(CatList)
    SharedTransitionLayout {
        SceneNavDisplay(
            backStack = backStack,
            onBack = { backStack.removeLast() },
            entryProvider =
                entryProvider {
                    entry<CatList> {
                        CatList(this@SharedTransitionLayout) { cat ->
                            backStack.add(CatDetail(cat))
                        }
                    }
                    entry<CatDetail> { args ->
                        CatDetail(args.cat, this@SharedTransitionLayout) { backStack.removeLast() }
                    }
                }
        )
    }
}
