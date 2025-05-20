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

package androidx.xr.scenecore.impl;

import static com.google.common.truth.Truth.assertThat;

import android.app.Activity;

import androidx.xr.runtime.internal.Dimensions;
import androidx.xr.scenecore.impl.extensions.XrExtensionsProvider;
import androidx.xr.scenecore.testing.FakeScheduledExecutorService;

import com.android.extensions.xr.XrExtensions;
import com.android.extensions.xr.node.Node;

import com.google.androidxr.splitengine.SubspaceNode;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public final class SubspaceNodeEntityImplTest {
    private XrExtensions mXrExtensions;
    private EntityManager mEntityManager;
    private FakeScheduledExecutorService mExecutor;
    private Activity mActivity;
    private ActivitySpaceImpl mActivitySpace;
    private SubspaceNodeEntityImpl mSubspaceNodeEntity;

    @Before
    public void setUp() {
        mActivity = Robolectric.buildActivity(Activity.class).create().get();
        mXrExtensions = XrExtensionsProvider.getXrExtensions();
        mEntityManager = new EntityManager();
        mExecutor = new FakeScheduledExecutorService();
        mActivitySpace =
                new ActivitySpaceImpl(
                        mXrExtensions.createNode(),
                        mActivity,
                        mXrExtensions,
                        mEntityManager,
                        () -> mXrExtensions.getSpatialState(mActivity),
                        /* unscaledGravityAlignedActivitySpace= */ false,
                        mExecutor);

        Node node = mXrExtensions.createNode();
        SubspaceNode subspaceNode = new SubspaceNode(0, node);
        Dimensions size = new Dimensions(1.0f, 2.0f, 3.0f);

        mSubspaceNodeEntity =
                new SubspaceNodeEntityImpl(
                        mXrExtensions,
                        mEntityManager,
                        mExecutor,
                        subspaceNode.getSubspaceNode(),
                        size);
        mSubspaceNodeEntity.setParent(mActivitySpace);
    }

    @Test
    public void setSize_setsSize() {
        Dimensions size = new Dimensions(1.0f, 2.0f, 3.0f);
        Dimensions subspaceNodeSize = mSubspaceNodeEntity.getSize();

        assertThat(subspaceNodeSize.width).isEqualTo(size.width);
        assertThat(subspaceNodeSize.height).isEqualTo(size.height);
        assertThat(subspaceNodeSize.depth).isEqualTo(size.depth);

        size = new Dimensions(11.0f, 12.0f, 13.0f);
        mSubspaceNodeEntity.setSize(size);
        subspaceNodeSize = mSubspaceNodeEntity.getSize();

        assertThat(subspaceNodeSize.width).isEqualTo(size.width);
        assertThat(subspaceNodeSize.height).isEqualTo(size.height);
        assertThat(subspaceNodeSize.depth).isEqualTo(size.depth);
    }
}
