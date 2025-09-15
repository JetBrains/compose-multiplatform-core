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
import androidx.xr.runtime.math.BoundingBox
import androidx.xr.runtime.math.FloatSize3d
import androidx.xr.runtime.math.Pose
import androidx.xr.runtime.math.Quaternion
import androidx.xr.runtime.math.Vector3
import androidx.xr.scenecore.internal.ActivitySpace as RtActivitySpace
import androidx.xr.scenecore.internal.Dimensions
import androidx.xr.scenecore.internal.JxrPlatformAdapter
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.MoreExecutors.directExecutor
import java.util.function.Consumer
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class ActivitySpaceTest {
    private val mockPlatformAdapter = mock<JxrPlatformAdapter>()
    private val entityManager = EntityManager()
    private var mockActivitySpace = mock<RtActivitySpace>()

    @Before
    fun setUp() {
        whenever(mockPlatformAdapter.activitySpace).thenReturn(mockActivitySpace)
    }

    @Test
    fun getBounds_callsImplGetBounds() {
        whenever(mockActivitySpace.bounds).thenReturn(Dimensions(100f, 200f, 300f))
        val activitySpace = ActivitySpace.create(mockPlatformAdapter, entityManager)

        assertThat(activitySpace.bounds).isNotNull()
        val bounds = activitySpace.bounds
        assertThat(bounds.width).isEqualTo(100f)
        assertThat(bounds.height).isEqualTo(200f)
        assertThat(bounds.depth).isEqualTo(300f)
    }

    @Test
    fun addOnBoundsChangedListener_receivesBoundsChangedCallback() {
        val activitySpace = ActivitySpace.create(mockPlatformAdapter, entityManager)
        val boundsChangedListener =
            Consumer<FloatSize3d> { newBounds ->
                assertThat(newBounds.width).isEqualTo(0.3f)
                assertThat(newBounds.height).isEqualTo(0.2f)
                assertThat(newBounds.depth).isEqualTo(0.1f)
            }

        activitySpace.addOnBoundsChangedListener(directExecutor(), boundsChangedListener)
        verify(mockActivitySpace)
            .addOnBoundsChangedListener(any<RtActivitySpace.OnBoundsChangedListener>())

        activitySpace.removeOnBoundsChangedListener(boundsChangedListener)
        verify(mockActivitySpace)
            .removeOnBoundsChangedListener(any<RtActivitySpace.OnBoundsChangedListener>())
    }

    @Test
    fun addOnSpaceUpdatedListener_receivesRuntimeSetOnSpaceUpdatedListenerCallbacks() {
        whenever(mockPlatformAdapter.activitySpace).thenReturn(mockActivitySpace)
        val activitySpace = ActivitySpace.create(mockPlatformAdapter, entityManager)

        var listenerCalled = false
        val captor = argumentCaptor<Runnable>()
        activitySpace.addOnSpaceUpdatedListener(directExecutor()) { listenerCalled = true }
        verify(mockActivitySpace).setOnSpaceUpdatedListener(captor.capture(), anyOrNull())
        captor.firstValue.run()
        assertThat(listenerCalled).isTrue()
    }

    @Test
    fun addRemoveOnSpaceUpdatedListener_callsRuntimeSetOnSpaceUpdatedListener() {
        whenever(mockPlatformAdapter.activitySpace).thenReturn(mockActivitySpace)
        val activitySpace = ActivitySpace.create(mockPlatformAdapter, entityManager)

        val listener = Runnable { print("Hello, World") }
        activitySpace.addOnSpaceUpdatedListener(listener)

        verify(mockActivitySpace).setOnSpaceUpdatedListener(any(), eq(null))

        activitySpace.removeOnSpaceUpdatedListener(listener)
        verify(mockActivitySpace).setOnSpaceUpdatedListener(eq(null), eq(null))
    }

    @Test
    fun recommendedContentBoxInFullSpace_returnsCorrectBoundingBox() {
        whenever(mockActivitySpace.recommendedContentBoxInFullSpace)
            .thenReturn(BoundingBox(Vector3.Zero, Vector3.One))
        val activitySpace = ActivitySpace.create(mockPlatformAdapter, entityManager)
        val recommendedContentBoxInFullSpace = activitySpace.recommendedContentBoxInFullSpace
        assertThat(recommendedContentBoxInFullSpace.min).isEqualTo(Vector3.Zero)
        assertThat(recommendedContentBoxInFullSpace.max).isEqualTo(Vector3.One)
    }

    @Test
    fun getParentSpacePose_throwsIllegalArgumentException() {
        val activitySpace = ActivitySpace.create(mockPlatformAdapter, entityManager)
        assertThrows(IllegalArgumentException::class.java) { activitySpace.getPose(Space.PARENT) }
    }

    @Test
    fun getActivitySpacePose_returnsIdentity() {
        whenever(mockActivitySpace.getPose(Space.ACTIVITY))
            .thenReturn(Pose(Vector3.Zero, Quaternion.Identity))
        val activitySpace = ActivitySpace.create(mockPlatformAdapter, entityManager)
        val pose = activitySpace.getPose(Space.ACTIVITY)
        assertThat(pose.translation).isEqualTo(Vector3.Zero)
        assertThat(pose.rotation).isEqualTo(Quaternion.Identity)
    }

    @Test
    fun getRealWorldSpacePose_returnsPerceptionSpacePose() {
        whenever(mockActivitySpace.getPose(Space.REAL_WORLD))
            .thenReturn(Pose(Vector3.Zero, Quaternion.Identity))
        val activitySpace = ActivitySpace.create(mockPlatformAdapter, entityManager)
        val pose = activitySpace.getPose(Space.REAL_WORLD)
        assertThat(pose.translation).isEqualTo(Vector3.Zero)
        assertThat(pose.rotation).isEqualTo(Quaternion.Identity)
    }

    @Test
    fun setPose_throwsUnsupportedOperationException() {
        assertThrows(UnsupportedOperationException::class.java) {
            val activitySpace = ActivitySpace.create(mockPlatformAdapter, entityManager)
            activitySpace.setPose(Pose(Vector3.Zero, Quaternion.Identity))
        }
    }

    @Test
    fun getParentSpaceScale_throwsIllegalArgumentException() {
        val activitySpace = ActivitySpace.create(mockPlatformAdapter, entityManager)
        assertThrows(IllegalArgumentException::class.java) { activitySpace.getScale(Space.PARENT) }
    }

    @Test
    fun getActivitySpaceScale_returnsIdentity() {
        whenever(mockActivitySpace.getScale(Space.ACTIVITY)).thenReturn(Vector3.One)
        val activitySpace = ActivitySpace.create(mockPlatformAdapter, entityManager)
        val scale = activitySpace.getScale(Space.ACTIVITY)
        assertThat(scale).isEqualTo(1f)
    }

    @Test
    fun getRealWorldSpaceScale_returnsIdentity() {
        whenever(mockActivitySpace.getScale(Space.REAL_WORLD)).thenReturn(Vector3.One)
        val activitySpace = ActivitySpace.create(mockPlatformAdapter, entityManager)
        val scale = activitySpace.getScale(Space.REAL_WORLD)
        assertThat(scale).isEqualTo(1f)
    }

    @Test
    fun setScale_float_throwsUnsupportedOperationException() {
        val activitySpace = ActivitySpace.create(mockPlatformAdapter, entityManager)
        assertThrows(UnsupportedOperationException::class.java) {
            activitySpace.setScale(1f, Space.PARENT)
        }
    }

    @Test
    fun setScale_vector_throwsUnsupportedOperationException() {
        val activitySpace = ActivitySpace.create(mockPlatformAdapter, entityManager)
        assertThrows(UnsupportedOperationException::class.java) {
            activitySpace.setScale(Vector3.One, Space.PARENT)
        }
    }
}
