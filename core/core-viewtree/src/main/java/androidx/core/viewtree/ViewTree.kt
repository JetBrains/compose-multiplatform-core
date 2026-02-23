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

@file:JvmName("ViewTree")

package androidx.core.viewtree

import android.view.View
import android.view.ViewParent

/**
 * Assigns the disjoint parent of the given `view` to `parent`.
 *
 * A disjoint parent acts as an alternative to [View.getParent] and is used to link two disjoint
 * view hierarchies together. ViewOverlays, popups, and dialogs are all examples of when two view
 * hierarchies are disjoint but related. In all of these cases, there is either a new window or new
 * root view that has no parent of its own but conceptually is owned or otherwise related to another
 * specific view in the disjoint hierarchy.
 *
 * A disjoint parent is only used when a view has no parent of its own, so it is important to set
 * this tag only once at the root of the tree that is disconnected from its parent. AndroidX
 * automatically sets disjoint parents for hierarchies it creates in certain use cases, like for
 * view overlays (used in transitions).
 *
 * Setting the disjoint parent allows other components to resolve information from the disjoint
 * parent. AndroidX will use disjoint parents to resolve values stored in the view tree, like
 * ViewModel store owners, lifecycle owners, and more. They are not used or set by the platform.
 * Other View-based libraries should consider setting this property to allow these and similar
 * lookups to occur between disconnected view hierarchies. Opting in is not mandatory, and therefore
 * disjoint parents are not guaranteed to be a comprehensive source of view ownership in all
 * situations.
 *
 * To prevent a leak, a view must not outlive its disjoint parent. Additionally, you should avoid
 * accidentally creating a cycle in the parent and disjoint parent hierarchy. A view can be
 * re-parented to a different disjoint parent by calling this method again in the future, and can
 * have its disjoint parent cleared by setting it to `null`.
 *
 * @param parent The disjoint parent to set on `view`
 * @receiver The view to set the disjoint parent of
 * @see [getParentOrViewTreeDisjointParent]
 */
public fun View.setViewTreeDisjointParent(parent: ViewParent?) {
    setTag(R.id.view_tree_disjoint_parent, parent)
}

/**
 * Looks up a disjoint parent previously set by [setViewTreeDisjointParent].
 *
 * @return The [View.getParent] or the disjoint parent of the given `view`. If present, the parent
 *   view is always preferred by this method over the disjoint parent. If the view has neither a
 *   parent nor a disjoint parent, `null` is returned.
 * @receiver The view to get the parent or disjoint parent from
 */
public fun View.getParentOrViewTreeDisjointParent(): ViewParent? {
    val parent = parent
    if (parent != null) return parent

    val djParent = getTag(R.id.view_tree_disjoint_parent)
    return if ((djParent is ViewParent)) djParent else null
}
