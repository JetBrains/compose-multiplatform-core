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

package androidx.compose.ui.desktop

import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.platform.InfiniteAnimationPolicy
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.flow.first

/**
 * Desktop application-level content is not synchronized to a display. When a scene temporarily has
 * no native windows, finite animations should complete in a single frame and infinite animations
 * should suspend until a window is attached again.
 *
 * This preserves non-visual side effects and the reconcile needed to materialize the next window
 * without letting no-window scenes spin the frame clock as fast as possible.
 */
internal fun noWindowAnimationCoroutineContext(hasNativeWindows: () -> Boolean): CoroutineContext {
  return NoWindowMotionDurationScale(hasNativeWindows) + NoWindowInfiniteAnimationPolicy(hasNativeWindows)
}

private class NoWindowMotionDurationScale(
  private val hasNativeWindows: () -> Boolean,
) : MotionDurationScale {
  override val scaleFactor: Float
    get() = if (hasNativeWindows()) 1f else 0f
}

private class NoWindowInfiniteAnimationPolicy(
  private val hasNativeWindows: () -> Boolean,
) : InfiniteAnimationPolicy {
  override suspend fun <R> onInfiniteOperation(block: suspend () -> R): R {
    if (!hasNativeWindows()) {
      snapshotFlow { hasNativeWindows() }.first { it }
    }
    return block()
  }
}
