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

import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.Looper
import androidx.glance.wear.data.IWearWidgetCallback
import androidx.glance.wear.data.IWearWidgetProvider
import androidx.glance.wear.data.WearWidgetRequestData
import androidx.glance.wear.proto.WearWidgetRequestProto
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class GlanceWearWidgetServiceTest {

    private val mockWidgetCallback = mock<IWearWidgetCallback>()

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

    @Test
    fun onWidgetRequest_callsWidgetOnUpdate() {
        val service: TestService = Robolectric.setupService(TestService::class.java)
        val binder: IBinder? =
            service.onBind(Intent(GlanceWearWidgetService.ACTION_BIND_WIDGET_PROVIDER))
        val stub = IWearWidgetProvider.Stub.asInterface(binder)
        val requestProto = WearWidgetRequestProto(instance_id = 17)
        val requestData = WearWidgetRequestData()
        requestData.payload = WearWidgetRequestProto.ADAPTER.encode(requestProto)

        stub.onWidgetRequest(requestData, mockWidgetCallback)
        shadowOf(Looper.getMainLooper()).idle()

        assertThat(service.widget.instanceId).isEqualTo(requestProto.instance_id)
    }

    @Test
    fun onWidgetRequest_callbackIsCalled() {
        val service: TestService = Robolectric.setupService(TestService::class.java)
        val binder: IBinder? =
            service.onBind(Intent(GlanceWearWidgetService.ACTION_BIND_WIDGET_PROVIDER))
        val stub = IWearWidgetProvider.Stub.asInterface(binder)
        val requestProto = WearWidgetRequestProto(instance_id = 17)
        val requestData = WearWidgetRequestData()
        requestData.payload = WearWidgetRequestProto.ADAPTER.encode(requestProto)

        stub.onWidgetRequest(requestData, mockWidgetCallback)
        shadowOf(Looper.getMainLooper()).idle()

        verify(mockWidgetCallback).updateWidgetContent(any())
    }

    class TestWidget : GlanceWearWidget() {
        var instanceId: Int? = null

        override suspend fun provideWidgetContent(
            context: Context,
            request: WearWidgetRequest,
        ): WearWidgetContent {
            instanceId = request.instanceId
            return WearWidgetContent(ByteArray(0))
        }
    }

    private class TestService() : GlanceWearWidgetService() {
        override val widget = TestWidget()
    }
}
