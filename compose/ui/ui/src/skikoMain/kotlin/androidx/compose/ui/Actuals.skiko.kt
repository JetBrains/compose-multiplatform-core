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

package androidx.compose.ui

import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal expect val PostDelayedDispatcher: CoroutineContext

expect val ComposeUIDispatcher: MainCoroutineDispatcher

@OptIn(DelicateCoroutinesApi::class)
internal actual fun postDelayed(delayMillis: Long, block: () -> Unit): Any {
    // TODO https://youtrack.jetbrains.com/issue/CMP-7153
    return GlobalScope.launch(PostDelayedDispatcher) {
        delay(delayMillis)
        block()
    }
}

internal actual fun removePost(token: Any?) {
    val job = token as? Job?
    job?.cancel()
}
