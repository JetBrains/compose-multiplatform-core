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

package androidx.xr.scenecore.testing

import androidx.annotation.RestrictTo
import androidx.xr.runtime.NodeHolder
import androidx.xr.runtime.math.Pose
import androidx.xr.runtime.math.Vector3
import androidx.xr.scenecore.internal.Dimensions
import androidx.xr.scenecore.internal.SubspaceNodeFeature

/**
 * Test-only implementation of [SubspaceNodeFeature].
 *
 * @param _nodeHolder hold the node from XrExtensions. Could be the SubspaceNode's node.
 * @param size set the size of the node.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
public class FakeSubspaceNodeFeature(
    private val _nodeHolder: NodeHolder<*>,
    initSize: Dimensions = Dimensions(1.0f, 1.0f, 1.0f),
) : FakeBaseRenderingFeature(_nodeHolder), SubspaceNodeFeature {
    private var mockSubspaceNodeFeature: SubspaceNodeFeature? = null

    private var _size: Dimensions = Dimensions(1.0f, 1.0f, 1.0f)

    override fun getNodeHolder(): NodeHolder<*> = _nodeHolder

    override var size: Dimensions
        get() = mockSubspaceNodeFeature?.size ?: _size
        set(value) {
            if (mockSubspaceNodeFeature == null) {
                _size = value
            } else {
                mockSubspaceNodeFeature!!.size = value
            }
        }

    init {
        // Unable set size before mockSubspaceNodeFeature set.  Do it in creator.
        _size = initSize
    }

    override fun setPose(pose: Pose) {
        mockSubspaceNodeFeature?.setPose(pose)
    }

    override fun setScale(scaleActivity: Vector3) {
        mockSubspaceNodeFeature?.setScale(scaleActivity)
    }

    override fun setAlpha(alpha: Float) {
        mockSubspaceNodeFeature?.setAlpha(alpha)
    }

    override fun setHidden(hidden: Boolean) {
        mockSubspaceNodeFeature?.setHidden(hidden)
    }

    override fun dispose() {
        mockSubspaceNodeFeature?.dispose()
    }

    public companion object {
        // Inject mock feature into FakeSubspaceNodeFeature.
        public fun createWithMockFeature(
            mockFeature: SubspaceNodeFeature,
            nodeHolder: NodeHolder<*>,
            size: Dimensions,
        ): SubspaceNodeFeature {
            val fakeSubspaceNodeFeature = FakeSubspaceNodeFeature(nodeHolder, size)
            fakeSubspaceNodeFeature.mockSubspaceNodeFeature = mockFeature
            fakeSubspaceNodeFeature.size = size
            return fakeSubspaceNodeFeature
        }
    }
}
