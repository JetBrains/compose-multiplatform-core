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

package androidx.compose.ui.semantics

import androidx.compose.ui.unit.IntRect
import org.jetbrains.skia.IRect
import org.jetbrains.skia.Region

private class SemanticRegionImpl : SemanticsRegion {
    val region = Region()

    private var bounds = IRect.makeLTRB(0, 0, 0, 0)

    override fun set(left: Int, top: Int, right: Int, bottom: Int) {
        region.setRect(left, top, right, bottom)
    }

    override fun updateBounds() {
        bounds = region.bounds
    }

    override val boundsLeft: Int
        get() = bounds.left

    override val boundsTop: Int
        get() = bounds.top

    override val boundsRight: Int
        get() = bounds.right

    override val boundsBottom: Int
        get() = bounds.bottom

    override val isEmpty: Boolean
        get() = region.isEmpty

    override fun intersect(region: SemanticsRegion): Boolean {
        return this.region.op((region as SemanticRegionImpl).region, Region.Op.INTERSECT)
    }

    override fun difference(left: Int, top: Int, right: Int, bottom: Int): Boolean {
        return region.op(left, top, right, bottom, Region.Op.DIFFERENCE)
    }
}

internal actual fun SemanticsRegion(): SemanticsRegion = SemanticRegionImpl()
