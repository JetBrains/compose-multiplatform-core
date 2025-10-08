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

package androidx.glance.wear.parcel

import android.content.ComponentName
import android.content.Context
import android.os.Looper
import androidx.glance.wear.ActiveWearWidgetHandle
import androidx.glance.wear.ContainerType
import androidx.glance.wear.GlanceWearWidget
import androidx.glance.wear.WearWidgetContent
import androidx.glance.wear.WearWidgetRequest
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class WearWidgetProviderImplTest {
    private val testWidget = TestGlanceWearWidget()
    private val mockExecutionCallback = mock<IExecutionCallback>()
    private val mockWidgetCallback = mock<IWearWidgetCallback>()
    private val testScope = TestScope()
    private val testName = ComponentName("package.name", "class.name")
    private val context: Context = getApplicationContext()
    private val provider = WearWidgetProviderImpl(context, testName, testScope, testWidget)

    @Test
    fun onWidgetRequest_callsProvideWidgetContent() {
        val widgetRequest = WearWidgetRequest(instanceId = 17)

        provider.onWidgetRequest(widgetRequest.toParcel(), mockWidgetCallback)
        testScope.advanceUntilIdle()

        assertThat(testWidget.lastRequestedInstanceId).isEqualTo(widgetRequest.instanceId)
    }

    @Test
    fun onWidgetRequest_callbackIsCalled() {
        val widgetRequest = WearWidgetRequest(instanceId = 17)

        provider.onWidgetRequest(widgetRequest.toParcel(), mockWidgetCallback)
        testScope.advanceUntilIdle()
        shadowOf(Looper.getMainLooper()).idle()

        verify(mockWidgetCallback).updateWidgetContent(any())
    }

    @Test
    fun onActivated_callsWidgetAndCallback() {
        val handle = ActiveWearWidgetHandle(testName, 12, ContainerType.Large)

        provider.onActivated(handle.toParcel(), mockExecutionCallback)
        testScope.advanceUntilIdle()

        assertThat(testWidget.activatedHandle).isEqualTo(handle)
        verify(mockExecutionCallback).onSuccess()
    }

    @Test
    fun onActivated_throws_callsCallbackOnError() {
        val handle = ActiveWearWidgetHandle(testName, 12, ContainerType.Large)
        testWidget.enableFailureMode = true

        provider.onActivated(handle.toParcel(), mockExecutionCallback)
        testScope.advanceUntilIdle()

        assertThat(testWidget.activatedHandle).isEqualTo(handle)
        verify(mockExecutionCallback).onError()
    }

    @Test
    fun onDeactivated_callsWidgetAndCallback() {
        val handle = ActiveWearWidgetHandle(testName, 12, ContainerType.Small)

        provider.onDeactivated(handle.toParcel(), mockExecutionCallback)
        testScope.advanceUntilIdle()

        assertThat(testWidget.deactivatedHandle).isEqualTo(handle)
        verify(mockExecutionCallback).onSuccess()
    }

    @Test
    fun onDeativated_throws_callsCallbackOnError() {
        val handle = ActiveWearWidgetHandle(testName, 12, ContainerType.Large)
        testWidget.enableFailureMode = true

        provider.onDeactivated(handle.toParcel(), mockExecutionCallback)
        testScope.advanceUntilIdle()

        assertThat(testWidget.deactivatedHandle).isEqualTo(handle)
        verify(mockExecutionCallback).onError()
    }

    internal class TestGlanceWearWidget : GlanceWearWidget() {
        var lastRequestedInstanceId: Int? = null
        var activatedHandle: ActiveWearWidgetHandle? = null
        var deactivatedHandle: ActiveWearWidgetHandle? = null
        var enableFailureMode = false

        override suspend fun provideWidgetContent(
            context: Context,
            request: WearWidgetRequest,
        ): WearWidgetContent {
            lastRequestedInstanceId = request.instanceId
            if (enableFailureMode) {
                throw Exception("Test exception")
            }
            return WearWidgetContent(ByteArray(0))
        }

        override suspend fun onActivated(context: Context, widgetHandle: ActiveWearWidgetHandle) {
            activatedHandle = widgetHandle
            if (enableFailureMode) {
                throw Exception("Test exception")
            }
        }

        override suspend fun onDeactivated(context: Context, widgetHandle: ActiveWearWidgetHandle) {
            deactivatedHandle = widgetHandle
            if (enableFailureMode) {
                throw Exception("Test exception")
            }
        }
    }
}
