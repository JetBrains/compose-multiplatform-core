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

package androidx.appfunctions.internal

import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executors
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.android.asCoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher

/** The Coroutine Dispatchers for AppFunction runtime infrastructure. */
internal object Dispatchers {
    /** Android UI thread dispatcher. */
    internal val Main: CoroutineDispatcher by lazy {
        Handler(Looper.getMainLooper()).asCoroutineDispatcher()
    }

    /** AppFunction runtime worker thread dispatcher. */
    internal val Worker: CoroutineDispatcher by lazy {
        Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    }
}
