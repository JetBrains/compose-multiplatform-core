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

import androidx.xr.runtime.NodeHolder;
import androidx.xr.scenecore.impl.impress.ImpressApi;
import androidx.xr.scenecore.impl.impress.ImpressNode;
import androidx.xr.scenecore.internal.RenderingFeature;

import com.android.extensions.xr.XrExtensions;
import com.android.extensions.xr.node.Node;
import com.android.extensions.xr.node.NodeTransaction;

import com.google.androidxr.splitengine.SplitEngineSubspaceManager;
import com.google.androidxr.splitengine.SubspaceNode;

import org.jspecify.annotations.NonNull;

abstract class BaseRenderingFeature implements RenderingFeature {
    final Node mNode;
    protected final ImpressApi mImpressApi;
    protected final SplitEngineSubspaceManager mSplitEngineSubspaceManager;
    protected final XrExtensions mExtensions;
    // The SubspaceNode isn't final so that we can support setting it to null in dispose(), while
    // still allowing the application to hold a reference to this Entity.
    protected SubspaceNode mSubspace;

    BaseRenderingFeature(
            ImpressApi impressApi,
            SplitEngineSubspaceManager splitEngineSubspaceManager,
            @NonNull XrExtensions extensions) {
        mImpressApi = impressApi;
        mSplitEngineSubspaceManager = splitEngineSubspaceManager;
        mExtensions = extensions;
        mNode = mExtensions.createNode();
    }

    @Override
    @NonNull
    public NodeHolder<?> getNodeHolder() {
        return new NodeHolder<>(mNode, Node.class);
    }

    protected void bindImpressNodeToSubspace(String subspaceName, ImpressNode impressNode) {
        // System will only render Impress nodes that are parented by this subspace node.
        ImpressNode subspaceImpressNode = mImpressApi.createImpressNode();
        subspaceName += subspaceImpressNode.getHandle();

        mSubspace = mSplitEngineSubspaceManager.createSubspace(
                subspaceName, subspaceImpressNode.getHandle());

        // If mSplitEngineSubspaceManager is mock version, createSubspace might return null.
        if (mSubspace != null) {
            try (NodeTransaction transaction = mExtensions.createNodeTransaction()) {
                // Make the Entity node a parent of the subspace node.
                transaction.setParent(mSubspace.getSubspaceNode(), mNode).apply();
            }
        }

        // The CPM node hierarchy is: Entity CPM node --- parent of ---> Subspace CPM node.
        // The Impress node hierarchy is: Subspace Impress node --- parent of ---> Entity Impress
        // node.
        try {
            mImpressApi.setImpressNodeParent(impressNode, subspaceImpressNode);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void dispose() {
        if (mSubspace != null) {
            // The subspace impress node will be destroyed when the subspace is deleted.
            mSplitEngineSubspaceManager.deleteSubspace(mSubspace.subspaceId);
            mSubspace = null;
        }
    }
}
