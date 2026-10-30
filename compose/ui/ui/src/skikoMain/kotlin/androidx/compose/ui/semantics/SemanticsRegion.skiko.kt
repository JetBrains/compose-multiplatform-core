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

import org.jetbrains.skia.Region

private class SemanticRegionImpl : SemanticsRegion {
    val region = Region()

    private var currentBoundsLeft = 0
    private var currentBoundsTop = 0
    private var currentBoundsRight = 0
    private var currentBoundsBottom = 0

    override fun set(left: Int, top: Int, right: Int, bottom: Int) {
        region.setRect(left, top, right, bottom)
    }

    override fun updateBounds() {
        val bounds = region.bounds
        currentBoundsLeft = bounds.left
        currentBoundsTop = bounds.top
        currentBoundsRight = bounds.right
        currentBoundsBottom = bounds.bottom
    }

    override val boundsLeft: Int
        get() = currentBoundsLeft

    override val boundsTop: Int
        get() = currentBoundsTop

    override val boundsRight: Int
        get() = currentBoundsRight

    override val boundsBottom: Int
        get() = currentBoundsBottom

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
