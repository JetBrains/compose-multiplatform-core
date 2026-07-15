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

package androidx.compose.ui.desktop

import androidx.compose.runtime.CompositionLocal
import androidx.compose.runtime.DataSourceContext
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.CoroutineScope


// TODO[wojciech.krystyniak] This should be internal, but we need it for TestWindow
class ApplicationSession /* internal */ constructor(
    internal val coroutineScope: CoroutineScope,
    /**
     * The application-wide [DataSourceContext], fixed at session start: the application
     * composition's frame domain and every window scene created in this session take
     * their frame-cycle units from it.
     */
    val dataSourceContext: DataSourceContext = DataSourceContext(),
)

/* internal */ val ProvidableLocalApplicationSession =
    staticCompositionLocalOf<ApplicationSession> { error("No ApplicationSession provided") }

val LocalApplicationSession: CompositionLocal<ApplicationSession> =
    ProvidableLocalApplicationSession
