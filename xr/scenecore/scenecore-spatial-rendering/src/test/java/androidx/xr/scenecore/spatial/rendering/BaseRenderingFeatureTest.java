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

package androidx.xr.scenecore.spatial.rendering;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import androidx.xr.runtime.NodeHolder;
import androidx.xr.runtime.TypeHolder;
import androidx.xr.scenecore.impl.extensions.XrExtensionsProvider;
import androidx.xr.scenecore.impl.impress.FakeImpressApiImpl;
import androidx.xr.scenecore.impl.impress.ImpressApi;
import androidx.xr.scenecore.impl.impress.ImpressNode;

import com.android.extensions.xr.ShadowXrExtensions;
import com.android.extensions.xr.XrExtensions;
import com.android.extensions.xr.node.Node;
import com.android.extensions.xr.node.NodeRepository;

import com.google.androidxr.splitengine.SplitEngineSubspaceManager;
import com.google.androidxr.splitengine.SubspaceNode;

import org.jspecify.annotations.NonNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.robolectric.RobolectricTestRunner;

import java.util.Objects;

@RunWith(RobolectricTestRunner.class)
public class BaseRenderingFeatureTest {
    private static final int OPEN_XR_REFERENCE_SPACE_TYPE = 1;
    private final NodeRepository mNodeRepository = NodeRepository.getInstance();
    private final XrExtensions mXrExtensions =
            Objects.requireNonNull(XrExtensionsProvider.getXrExtensions());

    private final FakeImpressApiImpl mFakeImpressApi = new FakeImpressApiImpl();

    private final SplitEngineSubspaceManager mSplitEngineSubspaceManager =
            Mockito.mock(SplitEngineSubspaceManager.class);

    private static final int SUBSPACE_ID = 5;
    private final Node mSubspaceNode = mXrExtensions.createNode();
    private final SubspaceNode mExpectedSubspace = new SubspaceNode(SUBSPACE_ID, mSubspaceNode);

    // Internal implementation for test only
    class BaseRenderingFeatureImpl extends BaseRenderingFeature {
        BaseRenderingFeatureImpl(
                ImpressApi impressApi,
                SplitEngineSubspaceManager splitEngineSubspaceManager,
                @NonNull XrExtensions extensions) {
            super(impressApi, splitEngineSubspaceManager, extensions);
        }

        // Expose the function to test it.
        void bindImpressNodeToSubspace(ImpressNode impressNode) {
            bindImpressNodeToSubspace("test_node_", impressNode);
        }
    }

    private BaseRenderingFeatureImpl mRenderingFeature;

    @Before
    public void setUp() {
        when(mSplitEngineSubspaceManager.createSubspace(anyString(), anyInt()))
                .thenReturn(mExpectedSubspace);
        ShadowXrExtensions.extract(mXrExtensions)
                .setOpenXrWorldSpaceType(OPEN_XR_REFERENCE_SPACE_TYPE);

        mRenderingFeature = new BaseRenderingFeatureImpl(
                mFakeImpressApi,
                mSplitEngineSubspaceManager,
                mXrExtensions);
    }

    @After
    public void tearDown() {
        mRenderingFeature.dispose();
    }

    @Test
    public void getNodeHolder_hasCorrectHierarchy() {
        NodeHolder<?> nodeHolder = mRenderingFeature.getNodeHolder();
        Node node = TypeHolder.assertGetValue(nodeHolder, Node.class);
        ImpressNode impressNode = mFakeImpressApi.createImpressNode();

        mRenderingFeature.bindImpressNodeToSubspace(impressNode);

        // The CPM node hierarchy is: Entity CPM node --- parent of ---> Subspace CPM node.
        assertThat(mNodeRepository.getParent(mSubspaceNode)).isEqualTo(node);
        // The Impress node hierarchy is: Subspace Impress node --- parent of ---> Entity Impress
        // node. The subspace impress node is not recorded anywhere. We can only know that it has a
        // parent.
        assertThat(mFakeImpressApi.impressNodeHasParent(impressNode)).isTrue();
    }
}
