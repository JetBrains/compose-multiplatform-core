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
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GlanceWearTilesTest {

    @Test
    fun onBind_withWidgetIntent_returnsWidgetProvider() {
        val service: TestService = Robolectric.setupService(TestService::class.java)

        val binder: IBinder? =
            service.onBind(Intent(GlanceWearWidgetService.ACTION_BIND_WIDGET_PROVIDER))

        assertThat(binder).isInstanceOf(IWearWidgetProvider::class.java)
    }

    @Test
    fun onBind_withWrongIntent_returnsNull() {
        val service: TestService = Robolectric.setupService(TestService::class.java)

        val binder: IBinder? = service.onBind(Intent())

        assertThat(binder).isNull()
    }

    private class TestService() : GlanceWearWidgetService()
}
