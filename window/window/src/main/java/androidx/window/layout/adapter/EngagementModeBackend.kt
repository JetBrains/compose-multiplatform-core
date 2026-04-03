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

package androidx.window.layout.adapter

import android.content.Context
import androidx.annotation.UiContext
import androidx.core.util.Consumer
import androidx.window.layout.WindowLayoutInfo.EngagementMode
import java.util.concurrent.Executor

internal interface EngagementModeBackend {
    fun engagementMode(@UiContext context: Context): EngagementMode

    fun addEngagementLayoutChangeCallback(
        @UiContext context: Context,
        executor: Executor,
        callback: Consumer<EngagementMode>,
    )

    fun removeEngagementLayoutChangeCallback(callback: Consumer<EngagementMode>)

    fun getCurrentEngagementLayoutMode(@UiContext uiContext: Context): EngagementMode
}
