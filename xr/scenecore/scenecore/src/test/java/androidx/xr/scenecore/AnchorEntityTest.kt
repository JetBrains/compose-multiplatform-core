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

package androidx.xr.scenecore

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.xr.runtime.math.Pose
import androidx.xr.runtime.math.Quaternion
import androidx.xr.runtime.math.Vector3
import androidx.xr.scenecore.internal.AnchorEntity as RtAnchorEntity
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.MoreExecutors.directExecutor
import java.util.function.Consumer
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class AnchorEntityTest {
    private val mockAnchorEntityImpl = mock<RtAnchorEntity>()
    private val entityManager = EntityManager()

    @Test
    fun setOnStateChangedListener_receivesStateChangedCallback() {
        val anchorEntity = AnchorEntity.create(mockAnchorEntityImpl, entityManager)
        val stateChangedListener =
            Consumer<Int> { newState ->
                assertThat(newState).isEqualTo(RtAnchorEntity.State.ANCHORED)
            }

        anchorEntity.setOnStateChangedListener(directExecutor(), stateChangedListener)
        verify(mockAnchorEntityImpl)
            .setOnStateChangedListener(any<RtAnchorEntity.OnStateChangedListener>())
    }

    @Test
    fun setOnSpaceUpdatedListener_withNullParams_callsRuntimeSetOnSpaceUpdatedListener() {
        val anchorEntity = AnchorEntity.create(mockAnchorEntityImpl, entityManager)
        anchorEntity.setOnSpaceUpdatedListener(null)
        verify(mockAnchorEntityImpl).setOnSpaceUpdatedListener(eq(null), eq(null))
    }

    @Test
    fun setOnSpaceUpdatedListener_receivesRuntimeSetOnSpaceUpdatedListenerCallbacks() {
        var listenerCalled = false
        val captor = argumentCaptor<Runnable>()
        val anchorEntity = AnchorEntity.create(mockAnchorEntityImpl, entityManager)
        anchorEntity.setOnSpaceUpdatedListener(directExecutor()) { listenerCalled = true }

        verify(mockAnchorEntityImpl).setOnSpaceUpdatedListener(captor.capture(), any())
        assertThat(listenerCalled).isFalse()
        captor.firstValue.run()
        assertThat(listenerCalled).isTrue()
    }

    @Test
    fun getParentSpacePose_throwsIllegalArgumentException() {
        val anchorEntity = AnchorEntity.create(mockAnchorEntityImpl, entityManager)
        assertThrows(IllegalArgumentException::class.java) { anchorEntity.getPose(Space.PARENT) }
    }

    @Test
    fun getActivitySpacePose_returnsIdentity() {
        whenever(mockAnchorEntityImpl.getPose(Space.ACTIVITY))
            .thenReturn(Pose(Vector3.Zero, Quaternion.Identity))
        val anchorEntity = AnchorEntity.create(mockAnchorEntityImpl, entityManager)
        val pose = anchorEntity.getPose(Space.ACTIVITY)
        assertThat(pose.translation).isEqualTo(Vector3.Zero)
        assertThat(pose.rotation).isEqualTo(Quaternion.Identity)
    }

    @Test
    fun getRealWorldSpacePose_returnsPerceptionSpacePose() {
        whenever(mockAnchorEntityImpl.getPose(Space.REAL_WORLD))
            .thenReturn(Pose(Vector3.Zero, Quaternion.Identity))
        val anchorEntity = AnchorEntity.create(mockAnchorEntityImpl, entityManager)
        val pose = anchorEntity.getPose(Space.REAL_WORLD)
        assertThat(pose.translation).isEqualTo(Vector3.Zero)
        assertThat(pose.rotation).isEqualTo(Quaternion.Identity)
    }

    @Test
    fun setPose_throwsUnsupportedOperationException() {
        val anchorEntity = AnchorEntity.create(mockAnchorEntityImpl, entityManager)
        assertThrows(UnsupportedOperationException::class.java) {
            anchorEntity.setPose(Pose(Vector3.Zero, Quaternion.Identity))
        }
    }

    @Test
    fun getParentSpaceScale_throwsIllegalArgumentException() {
        val anchorEntity = AnchorEntity.create(mockAnchorEntityImpl, entityManager)
        assertThrows(IllegalArgumentException::class.java) { anchorEntity.getScale(Space.PARENT) }
    }

    @Test
    fun getActivitySpaceScale_returnsIdentity() {
        whenever(mockAnchorEntityImpl.getScale(Space.ACTIVITY)).thenReturn(Vector3.One)
        val anchorEntity = AnchorEntity.create(mockAnchorEntityImpl, entityManager)
        val scale = anchorEntity.getScale(Space.ACTIVITY)
        assertThat(scale).isEqualTo(1f)
    }

    @Test
    fun getRealWorldSpaceScale_returnsIdentity() {
        whenever(mockAnchorEntityImpl.getScale(Space.REAL_WORLD)).thenReturn(Vector3.One)
        val anchorEntity = AnchorEntity.create(mockAnchorEntityImpl, entityManager)
        val scale = anchorEntity.getScale(Space.REAL_WORLD)
        assertThat(scale).isEqualTo(1f)
    }

    @Test
    fun setScale_float_throwsUnsupportedOperationException() {
        val anchorEntity = AnchorEntity.create(mockAnchorEntityImpl, entityManager)
        assertThrows(UnsupportedOperationException::class.java) {
            anchorEntity.setScale(1f, Space.PARENT)
        }
    }

    @Test
    fun setScale_vector_throwsUnsupportedOperationException() {
        val anchorEntity = AnchorEntity.create(mockAnchorEntityImpl, entityManager)
        assertThrows(UnsupportedOperationException::class.java) {
            anchorEntity.setScale(Vector3.One, Space.PARENT)
        }
    }
}
