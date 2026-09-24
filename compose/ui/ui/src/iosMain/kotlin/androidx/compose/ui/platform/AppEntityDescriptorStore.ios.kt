/*
 * Copyright 2026 The Android Open Source Project
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

package androidx.compose.ui.platform

import androidx.compose.runtime.collection.MutableVector
import androidx.compose.runtime.collection.mutableVectorOf
import androidx.compose.ui.AppEntityReference
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.node.WeakReference
import androidx.compose.ui.uikit.utils.CMPAppEntityDescriptor
import androidx.compose.ui.uikit.utils.CMPAppEntityUIElementProvider
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.nativeHeap
import kotlinx.cinterop.ptr
import platform.CoreGraphics.CGRectMake
import platform.UIKit.UIView
import platform.objc.OBJC_ASSOCIATION_RETAIN
import platform.objc.objc_getAssociatedObject
import platform.objc.objc_setAssociatedObject

/**
 * Holds the App Entity modifier nodes attached to one Compose root view.
 *
 * Bounds are read when UIKit requests descriptors, so scrolling does not publish descriptor
 * arrays across the bridge.
 */
internal class AppEntityDescriptorStore private constructor(
    view: UIView,
) {
    private val appEntityModifierNodes: MutableVector<AppEntityDescriptorNode> = mutableVectorOf()
    private val storeRef = WeakReference(this)

    private val viewRef = WeakReference(view)
    private val uiElementProvider = CMPAppEntityUIElementProvider(view) {
        storeRef.get()?.appEntityDescriptors() ?: emptyList<CMPAppEntityDescriptor>()
    }

    private var isDisposed = false

    internal fun onAttach(node: AppEntityDescriptorNode) {
        check(!isDisposed) { "Cannot add a node to a disposed AppEntityDescriptorStore." }

        appEntityModifierNodes += node
    }

    internal fun onDetach(node: AppEntityDescriptorNode) {
        appEntityModifierNodes -= node
        if (appEntityModifierNodes.isEmpty()) {
            dispose()
        }
    }

    private fun appEntityDescriptors(): List<CMPAppEntityDescriptor> {
        if (isDisposed) return emptyList()

        val descriptors = buildList(capacity = appEntityModifierNodes.size) {
            appEntityModifierNodes.forEach { node ->
                node.boundsInRootOrNull()?.let { bounds ->
                    add(node.entity.toDescriptor(bounds))
                }
            }
        }

        return descriptors
    }

    private fun dispose() {
        if (isDisposed) return
        isDisposed = true

        viewRef.get()?.let { unassociate(it, this) }

        appEntityModifierNodes.clear()
        uiElementProvider.dispose()
    }

    internal companion object {
        fun forView(view: UIView): AppEntityDescriptorStore {
            return view.appEntityDescriptorStore ?: AppEntityDescriptorStore(view).also {
                view.appEntityDescriptorStore = it
            }
        }

        private fun unassociate(view: UIView, store: AppEntityDescriptorStore) {
            if (view.appEntityDescriptorStore === store) {
                view.appEntityDescriptorStore = null
            }
        }

        fun descriptorsForTest(view: UIView): List<CMPAppEntityDescriptor> =
            view.appEntityDescriptorStore?.appEntityDescriptors() ?: emptyList()

    }
}

internal interface AppEntityDescriptorNode {
    val entity: AppEntityReference

    fun boundsInRootOrNull(): Rect?
}

private fun AppEntityReference.toDescriptor(bounds: Rect): CMPAppEntityDescriptor =
    CMPAppEntityDescriptor(
        typeName = typeName,
        rawIdentifier = id,
        bounds = CGRectMake(
            x = bounds.left.toDouble(),
            y = bounds.top.toDouble(),
            width = bounds.width.toDouble(),
            height = bounds.height.toDouble(),
        ),
        selected = selected,
    )

@OptIn(ExperimentalForeignApi::class)
private val appEntityDescriptorStoreAssociationKey: COpaquePointer = nativeHeap.alloc<IntVar>().ptr

@OptIn(ExperimentalForeignApi::class)
private var UIView.appEntityDescriptorStore: AppEntityDescriptorStore?
    get() = objc_getAssociatedObject(this, appEntityDescriptorStoreAssociationKey) as? AppEntityDescriptorStore
    set(value) {
        objc_setAssociatedObject(this, appEntityDescriptorStoreAssociationKey, value, OBJC_ASSOCIATION_RETAIN)
    }
