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
import androidx.xr.scenecore.internal.GltfFeature
import androidx.xr.scenecore.internal.MaterialResource
import java.util.concurrent.Executor

/** Test-only implementation of [GltfFeature] */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
public class FakeGltfFeature(nodeHolder: NodeHolder<*>) :
    FakeBaseRenderingFeature(nodeHolder), GltfFeature {
    private var mockGltfFeature: GltfFeature? = null

    override val animationState: Int = mockGltfFeature?.animationState ?: 0

    override fun startAnimation(loop: Boolean, animationName: String?, executor: Executor) {
        mockGltfFeature?.startAnimation(loop, animationName, executor)
    }

    override fun stopAnimation() {
        mockGltfFeature?.stopAnimation()
    }

    override fun setMaterialOverride(
        material: MaterialResource,
        nodeName: String,
        primitiveIndex: Int,
    ) {
        mockGltfFeature?.setMaterialOverride(material, nodeName, primitiveIndex)
    }

    override fun clearMaterialOverride(nodeName: String, primitiveIndex: Int) {
        mockGltfFeature?.clearMaterialOverride(nodeName, primitiveIndex)
    }

    override fun setColliderEnabled(enableCollider: Boolean) {
        mockGltfFeature?.setColliderEnabled(enableCollider)
    }

    override fun dispose() {
        mockGltfFeature?.dispose()
    }

    public companion object {
        public fun createWithMockFeature(
            feature: GltfFeature,
            nodeHolder: NodeHolder<*>,
        ): GltfFeature {
            val fakeGltfFeature = FakeGltfFeature(nodeHolder)
            fakeGltfFeature.mockGltfFeature = feature
            return fakeGltfFeature
        }
    }
}
