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

package noria.foundation

import androidx.compose.ui.unit.Dp

/**
 * Determines how the scroll target will be positioned inside the viewport
 */
sealed class ScrollKind {
  /**
   * Scroll in this dimension will not be performed.
   */
  data object None : ScrollKind()

  /**
   * Preferred scrolling kind for editor is ratio with default ratio value,
   * which represent the golden ratio.
   * Space (B)efore target center will relate to the space (A)fter after as B / A + B = ratio
   */
  data class Ratio(val centerIfInViewport: Boolean, val ratio: Float = 0.381f) : ScrollKind()

  /**
   * Target will be geometrically centered
   */
  data class Center(val centerIfInViewport: Boolean) : ScrollKind()

  /**
   * Smallest viewport change will be applied for target to become visible
   */
  data object Smallest : ScrollKind()

  /**
   * Viewport start will be positioned at target left top corner
   */
  data object Exact : ScrollKind()

  /**
   * Viewport will try to stick to the left (in case of horizontal) or top (in case of vertical)
   * as much as possible
   */
  data object Gravitate : ScrollKind()

  data class FixedPartOfTarget(val part: Dp) : ScrollKind()

  /**
   * Smallest viewport change will be applied for target + pads to become visible
   */
  data class Padded(val padBefore: Dp, val padAfter: Dp) : ScrollKind()
}
