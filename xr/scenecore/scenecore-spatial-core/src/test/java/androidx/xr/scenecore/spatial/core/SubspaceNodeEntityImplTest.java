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

package androidx.xr.scenecore.spatial.core;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.Activity;

import androidx.xr.runtime.NodeHolder;
import androidx.xr.runtime.math.Vector3;
import androidx.xr.scenecore.impl.extensions.XrExtensionsProvider;
import androidx.xr.scenecore.internal.Dimensions;
import androidx.xr.scenecore.internal.SubspaceNodeFeature;
import androidx.xr.scenecore.testing.FakeScheduledExecutorService;
import androidx.xr.scenecore.testing.FakeSubspaceNodeFeature;

import com.android.extensions.xr.XrExtensions;
import com.android.extensions.xr.node.Node;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public final class SubspaceNodeEntityImplTest {
    private XrExtensions mXrExtensions;
    private Activity mActivity;
    private SubspaceNodeEntityImpl mSubspaceNodeEntity;
    private SubspaceNodeFeature mFakeSubspaceNodeFeature;
    private SubspaceNodeFeature mMockSubspaceNodeFeature = mock(SubspaceNodeFeature.class);

    @Before
    public void setUp() {
        mActivity = Robolectric.buildActivity(Activity.class).create().get();
        mXrExtensions = XrExtensionsProvider.getXrExtensions();
        EntityManager entityManager = new EntityManager();
        FakeScheduledExecutorService executor = new FakeScheduledExecutorService();
        /* unscaledGravityAlignedActivitySpace= */
        ActivitySpaceImpl activitySpace =
                new ActivitySpaceImpl(
                        mXrExtensions.createNode(),
                        mActivity,
                        mXrExtensions,
                        entityManager,
                        () -> mXrExtensions.getSpatialState(mActivity),
                        /* unscaledGravityAlignedActivitySpace= */ false,
                        executor);

        Dimensions size = new Dimensions(1.0f, 2.0f, 3.0f);

        NodeHolder<?> nodeHolder = new NodeHolder<>(mXrExtensions.createNode(), Node.class);
        mFakeSubspaceNodeFeature =
                FakeSubspaceNodeFeature.Companion.createWithMockFeature(
                        mMockSubspaceNodeFeature,
                        nodeHolder,
                        size);

        mSubspaceNodeEntity =
                new SubspaceNodeEntityImpl(
                        mActivity,
                        mFakeSubspaceNodeFeature,
                        mXrExtensions,
                        entityManager,
                        executor);
        mSubspaceNodeEntity.setParent(activitySpace);
    }

    @Test
    public void setSize_featureSizeIsUpdated() {
        Dimensions size = new Dimensions(3.0f, 4.0f, 5.0f);
        mSubspaceNodeEntity.setSize(size);

        // Only test feature receive the size.  No real logic in FakeSubspaceNodeFeature.
        verify(mMockSubspaceNodeFeature).setSize(size);
    }

    @Test
    public void getSize_featureSizeReturns() {
        Dimensions size = new Dimensions(3.0f, 4.0f, 5.0f);
        when(mMockSubspaceNodeFeature.getSize()).thenReturn(size);

        // Only test feature receive the size.  No real logic in FakeSubspaceNodeFeature.
        assertThat(mSubspaceNodeEntity.getSize()).isEqualTo(size);
    }

    @Test
    public void setScale_featureScaleIsUpdated() {
        Vector3 scale = new Vector3(1.0f, 2.0f, 3.0f);

        mSubspaceNodeEntity.setScale(scale);

        // Only test feature receive the size.  No real logic in FakeSubspaceNodeFeature.
        verify(mMockSubspaceNodeFeature).setScale(scale);
    }

    @Test
    public void setAlpha_featureAlphaIsUpdated() {
        float alpha = 0.5f;

        mSubspaceNodeEntity.setAlpha(alpha);

        // Only test feature receive the size.  No real logic in FakeSubspaceNodeFeature.
        verify(mMockSubspaceNodeFeature).setAlpha(alpha);
    }

    @Test
    public void setHidden_featureVisibilityIsUpdated() {
        boolean hidden = true;

        mSubspaceNodeEntity.setHidden(hidden);

        // Only test feature receive the size.  No real logic in FakeSubspaceNodeFeature.
        verify(mMockSubspaceNodeFeature).setHidden(hidden);
    }
}
