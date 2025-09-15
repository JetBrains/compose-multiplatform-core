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

package androidx.glance.wear

import android.content.Intent
import android.os.IBinder
import androidx.glance.wear.data.IWearWidgetProvider
import androidx.glance.wear.data.WearWidgetProviderImpl
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope

/**
 * Service used for communication between the Host and a Widget Provider.
 *
 * This should be typically used as:
 * ```
 * class MyGlanceWearWidgetService : GlanceWearWidgetService() {
 *     override val widget: GlanceWearWidget = MyGlanceWearWidget()
 * }
 * ```
 */
public abstract class GlanceWearWidgetService() : LifecycleService() {

    /** Instance of [GlanceWearWidget] associated with this provider. */
    public abstract val widget: GlanceWearWidget

    private val provider: IWearWidgetProvider.Stub by
        lazy(LazyThreadSafetyMode.PUBLICATION) {
            WearWidgetProviderImpl(this, lifecycleScope, widget)
        }

    final override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        if (intent.action == ACTION_BIND_WIDGET_PROVIDER) {
            return provider
        }
        return null
    }

    public companion object {
        /** Intent action for binding to a Widget Service. */
        public const val ACTION_BIND_WIDGET_PROVIDER: String =
            "androidx.glance.wear.action.BIND_WIDGET_PROVIDER"
    }
}
