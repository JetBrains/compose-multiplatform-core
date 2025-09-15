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

package androidx.xr.scenecore.spatial.rendering;

import static com.google.common.truth.Truth.assertThat;

import androidx.xr.runtime.math.Pose;
import androidx.xr.runtime.math.Quaternion;
import androidx.xr.runtime.math.Vector3;
import androidx.xr.scenecore.impl.extensions.XrExtensionsProvider;
import androidx.xr.scenecore.internal.Dimensions;
import androidx.xr.scenecore.testing.FakeScheduledExecutorService;

import com.android.extensions.xr.XrExtensions;
import com.android.extensions.xr.node.Node;
import com.android.extensions.xr.node.NodeRepository;

import com.google.androidxr.splitengine.SubspaceNode;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public final class SubspaceNodeFeatureImplTest {
    private XrExtensions mXrExtensions;
    private SubspaceNodeFeatureImpl mSubspaceNodeFeature;
    private Node mSubSpaceNode;
    private final NodeRepository mNodeRepository = NodeRepository.getInstance();

    @Before
    public void setUp() {
        mXrExtensions = XrExtensionsProvider.getXrExtensions();
        FakeScheduledExecutorService executor = new FakeScheduledExecutorService();

        mSubSpaceNode = mXrExtensions.createNode();
        SubspaceNode subspaceNode = new SubspaceNode(0, mSubSpaceNode);
        Dimensions size = new Dimensions(1.0f, 2.0f, 3.0f);

        mSubspaceNodeFeature =
                new SubspaceNodeFeatureImpl(
                        null,
                        null,
                        mXrExtensions,
                        subspaceNode.getSubspaceNode(),
                        size);
    }

    @Test
    public void setSize_updatesInnerNodeSize() {
        Dimensions size = new Dimensions(3.0f, 4.0f, 5.0f);

        mSubspaceNodeFeature.setSize(size);

        assertThat(mNodeRepository.getScale(mSubSpaceNode).x).isEqualTo(size.width);
        assertThat(mNodeRepository.getScale(mSubSpaceNode).y).isEqualTo(size.height);
        assertThat(mNodeRepository.getScale(mSubSpaceNode).z).isEqualTo(size.depth);
    }

    @Test
    public void setPose_updatesInnerNodePose() {
        Pose testPose =
                new Pose(
                        new Vector3(1.1f, 2.2f, 3.3f),
                        new Quaternion(0.1f, 0.2f, 0.3f, 0.92736185f)
                                .toNormalized() // Ensure normalized
                        );

        mSubspaceNodeFeature.setPose(testPose);

        // Assert entity's pose
        assertThat(mNodeRepository.getPosition(mSubSpaceNode).x)
                .isEqualTo(testPose.getTranslation().getX());
        assertThat(mNodeRepository.getPosition(mSubSpaceNode).y)
                .isEqualTo(testPose.getTranslation().getY());
        assertThat(mNodeRepository.getPosition(mSubSpaceNode).z)
                .isEqualTo(testPose.getTranslation().getZ());
        assertThat(mNodeRepository.getOrientation(mSubSpaceNode).x)
                .isEqualTo(testPose.getRotation().getX());
        assertThat(mNodeRepository.getOrientation(mSubSpaceNode).y)
                .isEqualTo(testPose.getRotation().getY());
        assertThat(mNodeRepository.getOrientation(mSubSpaceNode).z)
                .isEqualTo(testPose.getRotation().getZ());
        assertThat(mNodeRepository.getOrientation(mSubSpaceNode).w)
                .isEqualTo(testPose.getRotation().getW());
    }

    @Test
    public void setScale_updatesInnerNodeScale() {
        Vector3 scale = new Vector3(1.0f, 2.0f, 3.0f);
        Dimensions size = new Dimensions(2f, 2f, 2f);

        mSubspaceNodeFeature.setSize(size);
        mSubspaceNodeFeature.setScale(scale);

        assertThat(mNodeRepository.getScale(mSubSpaceNode).x).isEqualTo(scale.getX() * size.width);
        assertThat(mNodeRepository.getScale(mSubSpaceNode).y).isEqualTo(scale.getY() * size.height);
        assertThat(mNodeRepository.getScale(mSubSpaceNode).z).isEqualTo(scale.getZ() * size.depth);
    }

    @Test
    public void setAlpha_updatesInnerNodeAlpha() {
        float alpha = 0.5f;

        mSubspaceNodeFeature.setAlpha(alpha);

        assertThat(mNodeRepository.getAlpha(mSubSpaceNode)).isEqualTo(alpha);
    }

    @Test
    public void setHidden_updatesInnerNodeVisibility() {
        boolean hidden = true;

        mSubspaceNodeFeature.setHidden(hidden);

        assertThat(mNodeRepository.isVisible(mSubSpaceNode)).isFalse();
    }
}
