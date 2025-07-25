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
@file:JvmName("UiObject2Ext")

package androidx.test.uiautomator

import android.graphics.Bitmap
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.uiautomator.internal.TimeoutClock
import androidx.test.uiautomator.internal.notNull

/**
 * Performs a DFS on the accessibility tree starting from the node associated to this [UiObject2]
 * and returns the first node matching the given [block]. The node is returned as an [UiObject2]
 * that allows interacting with it. If the requested node doesn't exist, a [ViewNotFoundException]
 * is thrown. Internally it works searching periodically every [pollIntervalMs].
 *
 * Example:
 * ```kotlin
 * onView { textAsString == "Search" }.click()
 * ```
 *
 * @param timeoutMs a timeout to find the view that satisfies the given condition.
 * @param pollIntervalMs an interval to wait before rechecking the accessibility tree for updates.
 * @param block a block that specifies a condition on the node to find.
 * @return a [UiObject2] from a node that matches the given [block] condition.
 */
@JvmOverloads
public fun UiObject2.onView(
    timeoutMs: Long = 10000,
    pollIntervalMs: Long = 100,
    block: AccessibilityNodeInfo.() -> (Boolean),
): UiObject2 =
    accessibilityNodeInfo.onView(
        timeoutMs = timeoutMs,
        pollIntervalMs = pollIntervalMs,
        block = block
    )

/**
 * Performs a DFS on the accessibility tree starting from the node associated to this [UiObject2]
 * and returns the first node matching the given [block]. The node is returned as an [UiObject2]
 * that allows interacting with it. If the requested node doesn't exist, null is returned.
 * Internally it works searching periodically every [pollIntervalMs].
 *
 * Example:
 * ```kotlin
 * onView { textAsString == "Search" }.click()
 * ```
 *
 * @param timeoutMs a timeout to find the view that satisfies the given condition.
 * @param pollIntervalMs an interval to wait before rechecking the accessibility tree for updates.
 * @param block a block that specifies a condition on the node to find.
 * @return a [UiObject2] from a node that matches the given [block] condition or null.
 */
@JvmOverloads
public fun UiObject2.onViewOrNull(
    timeoutMs: Long = 10000,
    pollIntervalMs: Long = 100,
    block: AccessibilityNodeInfo.() -> (Boolean),
): UiObject2? =
    accessibilityNodeInfo.onViewOrNull(
        timeoutMs = timeoutMs,
        pollIntervalMs = pollIntervalMs,
        block = block
    )

/**
 * Performs a DFS on the accessibility tree starting from the node associated to this [UiObject2]
 * and returns all the nodes matching the given [block]. This method stops waiting as soon as a
 * single node with the given condition is returned. The nodes returned are [UiObject2] that allow
 * interacting with them. Internally it works searching periodically every [pollIntervalMs].
 *
 * Example:
 * ```kotlin
 * node.onViews { isClass(Button::class.java) }
 * ```
 *
 * If multiple nodes are expected but they appear at different times, it's recommended to call
 * [androidx.test.uiautomator.waitForStable] before, to ensure any operation is complete.
 *
 * @param timeoutMs a timeout to find the view that satisfies the given condition.
 * @param pollIntervalMs an interval to wait before rechecking the accessibility tree for updates.
 * @param block a block that specifies a condition on the node to find.
 * @return a list of [UiObject2] from nodes that matches the given [block] condition.
 */
@JvmOverloads
public fun UiObject2.onViews(
    timeoutMs: Long = 10000,
    pollIntervalMs: Long = 100,
    block: AccessibilityNodeInfo.() -> (Boolean),
): List<UiObject2> =
    accessibilityNodeInfo.onViews(
        timeoutMs = timeoutMs,
        pollIntervalMs = pollIntervalMs,
        block = block
    )

/**
 * Keeps scrolling until the given [block] condition is satisfied or until the given [timeoutMs].
 * Throws a [ViewNotFoundException] if the condition is not satisfied at the end of the timeout.
 *
 * Example:
 * ```kotlin
 * onView { isScrollable }.scrollUntilView(Direction.DOWN) { id == "button" }.click()
 * ```
 *
 * @param direction the scroll [Direction].
 * @param timeoutMs a timeout to find the view that satisfies the given condition.
 * @param pollIntervalMs an interval to wait before rechecking the accessibility tree for updates.
 * @param block a block that specifies a condition on the node to find.
 * @return a list of [UiObject2] from nodes that matches the given [block] condition.
 */
@JvmOverloads
public fun UiObject2.scrollUntilView(
    direction: Direction,
    timeoutMs: Long = 10000,
    pollIntervalMs: Long = 100,
    block: AccessibilityNodeInfo.() -> (Boolean),
): UiObject2 =
    scrollUntilViewOrNull(
            direction = direction,
            timeoutMs = timeoutMs,
            pollIntervalMs = pollIntervalMs,
            block = block
        )
        .notNull(ViewNotFoundException())

/**
 * Keeps scrolling until the given [block] condition is satisfied or until the given [timeoutMs].
 * Returns null if the condition is not satisfied at the end of the timeout.
 *
 * Example:
 * ```kotlin
 * onView { isScrollable }.scrollUntilView(Direction.DOWN) { id == "button" }.click()
 * ```
 *
 * @param direction the scroll [Direction].
 * @param timeoutMs a timeout to find the view that satisfies the given condition.
 * @param pollIntervalMs an interval to wait before rechecking the accessibility tree for updates.
 * @param block a block that specifies a condition on the node to find.
 * @return a [UiObject2] that matches the given [block] condition.
 */
@JvmOverloads
public fun UiObject2.scrollUntilViewOrNull(
    direction: Direction,
    timeoutMs: Long = 10000,
    pollIntervalMs: Long = 100,
    block: AccessibilityNodeInfo.() -> (Boolean),
): UiObject2 {
    val clock = TimeoutClock(timeoutMs = timeoutMs, sleepIntervalMs = pollIntervalMs)
    return scrollUntil(direction) {
            try {
                return@scrollUntil onView(timeoutMs = 0, pollIntervalMs = 0, block)
            } catch (e: ViewNotFoundException) {
                if (clock.isTimeoutOrSleep()) throw e
                return@scrollUntil null
            }
        }
        .notNull(ViewNotFoundException())
}

/**
 * Takes a screenshot of the screen that contains the accessibility node associated to this
 * [UiObject2] and cuts only the area covered by it.
 *
 * @return a bitmap containing the image of the node associated to this [UiObject2].
 */
public fun UiObject2.takeScreenshot(): Bitmap = accessibilityNodeInfo.takeScreenshot()
