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

package androidx.xr.scenecore

import androidx.xr.runtime.internal.ExrImageResource as RtExrImageResource
import androidx.xr.runtime.internal.GltfModelResource as RtGltfModelResource
import androidx.xr.runtime.internal.JxrPlatformAdapter
import androidx.xr.runtime.internal.MaterialResource as RtMaterialResource
import androidx.xr.runtime.internal.SpatialEnvironment as RtSpatialEnvironment
import com.google.common.truth.Truth.assertThat
import java.util.function.Consumer
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Unit tests for the JXRCore SDK SpatialEnvironment Interface.
 *
 * TODO(b/329902726): Add a TestRuntime and verify CPM Integration.
 */
@RunWith(JUnit4::class)
class SpatialEnvironmentTest {

    private var mockRuntime: JxrPlatformAdapter = mock<JxrPlatformAdapter>()
    private var mockRtEnvironment: RtSpatialEnvironment? = null
    private var environment: SpatialEnvironment? = null

    @Before
    fun setUp() {
        mockRtEnvironment = mock<RtSpatialEnvironment>()
        whenever(mockRuntime.spatialEnvironment).thenReturn(mockRtEnvironment)

        environment = SpatialEnvironment(mockRuntime)
    }

    @Test
    fun getCurrentPassthroughOpacity_getsRuntimePassthroughOpacity() {
        val rtOpacity = 0.3f
        whenever(mockRtEnvironment!!.currentPassthroughOpacity).thenReturn(rtOpacity)
        assertThat(environment!!.getCurrentPassthroughOpacity()).isEqualTo(rtOpacity)
        verify(mockRtEnvironment!!).currentPassthroughOpacity
    }

    @Test
    fun getPassthroughOpacityPreference_getsRuntimePassthroughOpacityPreference() {
        val rtPreference = 0.3f
        whenever(mockRtEnvironment!!.passthroughOpacityPreference).thenReturn(rtPreference)

        assertThat(environment!!.getPassthroughOpacityPreference()).isEqualTo(rtPreference)
        verify(mockRtEnvironment!!).passthroughOpacityPreference
    }

    @Test
    fun getPassthroughOpacityPreferenceNull_getsRuntimePassthroughOpacityPreference() {
        val rtPreference = null as Float?
        whenever(mockRtEnvironment!!.passthroughOpacityPreference).thenReturn(rtPreference)

        assertThat(environment!!.getPassthroughOpacityPreference()).isEqualTo(rtPreference)
        verify(mockRtEnvironment!!).passthroughOpacityPreference
    }

    @Test
    fun setPassthroughOpacityPreference_callsRuntimeSetPassthroughOpacityPreference() {
        val preference = 0.3f

        whenever(mockRtEnvironment!!.setPassthroughOpacityPreference(any()))
            .thenReturn(RtSpatialEnvironment.SetPassthroughOpacityPreferenceResult.CHANGE_APPLIED)
        assertThat(environment!!.setPassthroughOpacityPreference(preference))
            .isInstanceOf(
                SpatialEnvironment.SetPassthroughOpacityPreferenceChangeApplied::class.java
            )

        whenever(mockRtEnvironment!!.setPassthroughOpacityPreference(any()))
            .thenReturn(RtSpatialEnvironment.SetPassthroughOpacityPreferenceResult.CHANGE_PENDING)
        assertThat(environment!!.setPassthroughOpacityPreference(preference))
            .isInstanceOf(
                SpatialEnvironment.SetPassthroughOpacityPreferenceChangePending::class.java
            )

        verify(mockRtEnvironment!!, times(2)).setPassthroughOpacityPreference(preference)
    }

    @Test
    fun setPassthroughOpacityPreferenceNull_callsRuntimeSetPassthroughOpacityPreference() {
        val preference = null as Float?

        whenever(mockRtEnvironment!!.setPassthroughOpacityPreference(anyOrNull()))
            .thenReturn(RtSpatialEnvironment.SetPassthroughOpacityPreferenceResult.CHANGE_APPLIED)
        assertThat(environment!!.setPassthroughOpacityPreference(preference))
            .isInstanceOf(
                SpatialEnvironment.SetPassthroughOpacityPreferenceChangeApplied::class.java
            )

        whenever(mockRtEnvironment!!.setPassthroughOpacityPreference(anyOrNull()))
            .thenReturn(RtSpatialEnvironment.SetPassthroughOpacityPreferenceResult.CHANGE_PENDING)
        assertThat(environment!!.setPassthroughOpacityPreference(preference))
            .isInstanceOf(
                SpatialEnvironment.SetPassthroughOpacityPreferenceChangePending::class.java
            )

        verify(mockRtEnvironment!!, times(2)).setPassthroughOpacityPreference(preference)
    }

    @Test
    fun addOnPassthroughOpacityChangedListener_ReceivesRuntimeOnPassthroughOpacityChangedEvents() {
        var listenerCalledWithValue = 0.0f
        val captor = argumentCaptor<Consumer<Float>>()
        val listener = Consumer<Float> { floatValue: Float -> listenerCalledWithValue = floatValue }
        environment!!.addOnPassthroughOpacityChangedListener(listener)
        verify(mockRtEnvironment!!).addOnPassthroughOpacityChangedListener(captor.capture())
        captor.firstValue.accept(0.3f)
        assertThat(listenerCalledWithValue).isEqualTo(0.3f)
    }

    @Test
    fun removeOnPassthroughOpacityChangedListener_callsRuntimeRemoveOnPassthroughOpacityChangedListener() {
        val captor = argumentCaptor<Consumer<Float>>()
        val listener = Consumer<Float> {}
        environment!!.removeOnPassthroughOpacityChangedListener(listener)
        verify(mockRtEnvironment!!).removeOnPassthroughOpacityChangedListener(captor.capture())
        assertThat(captor.firstValue).isEqualTo(listener)
    }

    @Test
    fun spatialEnvironmentPreferenceEqualsHashcode_returnsTrueIfAllPropertiesAreEqual() {
        val rtImageMock = mock<RtExrImageResource>()
        val rtModelMock = mock<RtGltfModelResource>()
        val rtMaterialMock = mock<RtMaterialResource>()
        val rtMeshName = "meshName"
        val rtAnimationName = "animationName"
        val rtPreference =
            RtSpatialEnvironment.SpatialEnvironmentPreference(
                rtImageMock,
                rtModelMock,
                rtMaterialMock,
                rtMeshName,
                rtAnimationName,
            )
        val preference =
            SpatialEnvironment.SpatialEnvironmentPreference(
                ExrImage(rtImageMock),
                GltfModel(rtModelMock),
                Material(rtMaterialMock),
                rtMeshName,
                rtAnimationName,
            )

        assertThat(preference).isEqualTo(rtPreference.toSpatialEnvironmentPreference())
        assertThat(preference.hashCode())
            .isEqualTo(rtPreference.toSpatialEnvironmentPreference().hashCode())
    }

    @Test
    fun spatialEnvironmentPreferenceEqualsHashcode_returnsFalseIfAnyPropertiesAreNotEqual() {
        val rtImageMock = mock<RtExrImageResource>()
        val rtModelMock = mock<RtGltfModelResource>()
        val rtMaterialMock = mock<RtMaterialResource>()
        val rtMeshName = "meshName"
        val rtAnimationName = "animationName"
        val rtImageMock2 = mock<RtExrImageResource>()
        val rtModelMock2 = mock<RtGltfModelResource>()
        val rtMaterialMock2 = mock<RtMaterialResource>()
        val rtMeshName2 = "meshName2"
        val rtAnimationName2 = "animationName2"
        val rtPreference =
            RtSpatialEnvironment.SpatialEnvironmentPreference(
                rtImageMock,
                rtModelMock,
                rtMaterialMock,
                rtMeshName,
                rtAnimationName,
            )

        val preferenceDiffGeometry =
            SpatialEnvironment.SpatialEnvironmentPreference(
                ExrImage(rtImageMock),
                GltfModel(rtModelMock2),
                Material(rtMaterialMock2),
                rtMeshName2,
                rtAnimationName2,
            )
        assertThat(preferenceDiffGeometry)
            .isNotEqualTo(rtPreference.toSpatialEnvironmentPreference())
        assertThat(preferenceDiffGeometry.hashCode())
            .isNotEqualTo(rtPreference.toSpatialEnvironmentPreference().hashCode())

        val preferenceDiffSkybox =
            SpatialEnvironment.SpatialEnvironmentPreference(
                ExrImage(rtImageMock2),
                GltfModel(rtModelMock),
                Material(rtMaterialMock),
                rtMeshName,
                rtAnimationName,
            )
        assertThat(preferenceDiffSkybox).isNotEqualTo(rtPreference.toSpatialEnvironmentPreference())
        assertThat(preferenceDiffSkybox.hashCode())
            .isNotEqualTo(rtPreference.toSpatialEnvironmentPreference().hashCode())
    }

    @Test
    fun setSpatialEnvironmentPreference_returnsRuntimeEnvironmentResultObject() {
        val rtImageMock = mock<RtExrImageResource>()
        val rtModelMock = mock<RtGltfModelResource>()

        val preference =
            SpatialEnvironment.SpatialEnvironmentPreference(
                ExrImage(rtImageMock),
                GltfModel(rtModelMock)
            )

        whenever(mockRtEnvironment!!.setSpatialEnvironmentPreference(any()))
            .thenReturn(RtSpatialEnvironment.SetSpatialEnvironmentPreferenceResult.CHANGE_APPLIED)
        assertThat(environment!!.setSpatialEnvironmentPreference(preference))
            .isInstanceOf(
                SpatialEnvironment.SetSpatialEnvironmentPreferenceChangeApplied::class.java
            )

        whenever(mockRtEnvironment!!.setSpatialEnvironmentPreference(any()))
            .thenReturn(RtSpatialEnvironment.SetSpatialEnvironmentPreferenceResult.CHANGE_PENDING)
        assertThat(environment!!.setSpatialEnvironmentPreference(preference))
            .isInstanceOf(
                SpatialEnvironment.SetSpatialEnvironmentPreferenceChangePending::class.java
            )

        verify(mockRtEnvironment!!, times(2)).setSpatialEnvironmentPreference(any())
    }

    @Test
    fun setSpatialEnvironmentPreferenceNull_returnsRuntimeEnvironmentResultObject() {
        val preference = null as SpatialEnvironment.SpatialEnvironmentPreference?

        whenever(mockRtEnvironment!!.setSpatialEnvironmentPreference(anyOrNull()))
            .thenReturn(RtSpatialEnvironment.SetSpatialEnvironmentPreferenceResult.CHANGE_APPLIED)
        assertThat(environment!!.setSpatialEnvironmentPreference(preference))
            .isInstanceOf(
                SpatialEnvironment.SetSpatialEnvironmentPreferenceChangeApplied::class.java
            )

        whenever(mockRtEnvironment!!.setSpatialEnvironmentPreference(anyOrNull()))
            .thenReturn(RtSpatialEnvironment.SetSpatialEnvironmentPreferenceResult.CHANGE_PENDING)
        assertThat(environment!!.setSpatialEnvironmentPreference(preference))
            .isInstanceOf(
                SpatialEnvironment.SetSpatialEnvironmentPreferenceChangePending::class.java
            )

        verify(mockRtEnvironment!!, times(2)).setSpatialEnvironmentPreference(anyOrNull())
    }

    @Test
    fun getSpatialEnvironmentPreference_getsRuntimeEnvironmentSpatialEnvironmentPreference() {
        val rtImageMock = mock<RtExrImageResource>()
        val rtModelMock = mock<RtGltfModelResource>()
        val rtPreference =
            RtSpatialEnvironment.SpatialEnvironmentPreference(rtImageMock, rtModelMock)
        whenever(mockRtEnvironment!!.spatialEnvironmentPreference).thenReturn(rtPreference)

        assertThat(environment!!.getSpatialEnvironmentPreference())
            .isEqualTo(rtPreference.toSpatialEnvironmentPreference())
        verify(mockRtEnvironment!!).spatialEnvironmentPreference
    }

    @Test
    fun getSpatialEnvironmentPreferenceNull_getsRuntimeEnvironmentSpatialEnvironmentPreference() {
        val rtPreference = null as RtSpatialEnvironment.SpatialEnvironmentPreference?
        whenever(mockRtEnvironment!!.spatialEnvironmentPreference).thenReturn(rtPreference)

        assertThat(environment!!.getSpatialEnvironmentPreference()).isEqualTo(null)
        verify(mockRtEnvironment!!).spatialEnvironmentPreference
    }

    @Test
    fun isSpatialEnvironmentPreferenceActive_callsRuntimeEnvironmentisSpatialEnvironmentPreferenceActive() {
        whenever(mockRtEnvironment!!.isSpatialEnvironmentPreferenceActive()).thenReturn(true)
        assertThat(environment!!.isSpatialEnvironmentPreferenceActive()).isTrue()
        verify(mockRtEnvironment!!).isSpatialEnvironmentPreferenceActive()
    }

    @Test
    fun addOnSpatialEnvironmentChangedListener_ReceivesRuntimeEnvironmentOnEnvironmentChangedEvents() {
        var listenerCalled = false
        val captor = argumentCaptor<Consumer<Boolean>>()
        val listener = Consumer<Boolean> { called: Boolean -> listenerCalled = called }
        environment!!.addOnSpatialEnvironmentChangedListener(listener)
        verify(mockRtEnvironment!!).addOnSpatialEnvironmentChangedListener(captor.capture())
        captor.firstValue.accept(true)
        assertThat(listenerCalled).isTrue()
    }

    @Test
    fun removeOnSpatialEnvironmentChangedListener_callsRuntimeRemoveOnSpatialEnvironmentChangedListener() {
        val captor = argumentCaptor<Consumer<Boolean>>()
        val listener = Consumer<Boolean> {}
        environment!!.removeOnSpatialEnvironmentChangedListener(listener)
        verify(mockRtEnvironment!!).removeOnSpatialEnvironmentChangedListener(captor.capture())
        assertThat(listener).isEqualTo(captor.firstValue)
    }

    @Test
    fun requestFullSpaceMode_callsThrough() {
        environment!!.requestFullSpaceMode()
        verify(mockRuntime).requestFullSpaceMode()
    }

    @Test
    fun requestHomeSpaceMode_callsThrough() {
        environment!!.requestHomeSpaceMode()
        verify(mockRuntime).requestHomeSpaceMode()
    }
}
