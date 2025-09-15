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

package androidx.glance.wear.data

import android.content.ComponentName
import android.content.Context

internal interface WidgetUpdateClient {
    /**
     * Notify the host that it should fetch a new layout for a specific active widget.
     *
     * If sdk version is API 36 or lower, or the widget [instanceId] is invalid (i.e. doesn't exist
     * or is not owned by your package); [instanceId] will be ignored and an update will be
     * requested for all widgets associated with the [provider] service component.
     *
     * @param context The context for this operation
     * @param provider The component name of the widget provider service to request an update from
     * @param instanceId The id of the active widget to request an update from
     */
    fun requestUpdate(context: Context, provider: ComponentName, instanceId: Int)
}
