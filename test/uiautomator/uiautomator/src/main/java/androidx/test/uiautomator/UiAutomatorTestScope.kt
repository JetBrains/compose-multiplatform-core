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

package androidx.test.uiautomator

import android.app.Instrumentation
import android.app.UiAutomation
import android.content.Intent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.internal.AppManager
import androidx.test.uiautomator.watcher.ScopedUiWatcher
import androidx.test.uiautomator.watcher.WatcherRegistration

/**
 * Main entry point for ui automator tests. It creates a [UiAutomatorTestScope] in which a test can
 * be defined.
 *
 * Example:
 * ```kotlin
 * @Test
 * fun myTest() = uiAutomator {
 *
 *     startActivity(MyActivity::class.java)
 *
 *     onView { id == "button" }.click()
 *
 *     onView { id == "nested_elements" }
 *         .apply {
 *             onView { text == "First Level" }
 *             onView { text == "Second Level" }
 *             onView { text == "Third Level" }
 *         }
 * }
 * ```
 *
 * @param block A block containing the test to run within the [UiAutomatorTestScope].
 */
public fun uiAutomator(block: UiAutomatorTestScope.() -> (Unit)) {
    val scope = UiAutomatorTestScope()
    block(scope)
    scope.afterBlock()
}

/** A UiAutomator scope that allows to easily access UiAutomator api and utils class. */
public class UiAutomatorTestScope
internal constructor(
    public val instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation(),
    public val uiAutomation: UiAutomation = instrumentation.uiAutomation,
    public val uiDevice: UiDevice = UiDevice.getInstance(instrumentation),
) {

    internal companion object {
        internal const val TAG = "UiAutomatorTestScope"
    }

    private val appManager = AppManager(context = instrumentation.targetContext)
    private val watcherRegistrations = mutableSetOf<WatcherRegistration>()

    internal fun afterBlock() {
        watcherRegistrations.forEach { it.unregister() }
    }

    /**
     * Registers a watcher for this [androidx.test.uiautomator.UiAutomatorTestScope] to handle
     * unexpected UI elements. Internally this method uses the existing [UiDevice.registerWatcher]
     * api. When the given [androidx.test.uiautomator.watcher.ScopedUiWatcher.isVisible] condition
     * is satisfied, then the given [block] is executed. scope. This method returns a handler with
     * the [WatcherRegistration] to unregister it before the block is complete. Note that this api
     * helps with unexpected ui elements, such as system dialogs, and that for expected dialogs the
     * [onView] api should be used.
     *
     * Usage:
     * ```kotlin
     * @Test fun myTest() = uiAutomator {
     *
     *     // Registers a watcher for a permission dialog.
     *     watchFor(PermissionDialog) { clickAllow() }
     *
     *     // Registers a watcher for a custom dialog and unregisters it.
     *     val registration = watchFor(MyDialog) { clickSomething() }
     *     // Do something...
     *     registration.unregister()
     * }
     * ```
     *
     * @param watcher the dialog to watch for.
     * @param block a block to handle.
     * @return the dialog registration.
     */
    public fun <T> watchFor(
        watcher: ScopedUiWatcher<T>,
        block: T.() -> (Unit)
    ): WatcherRegistration {
        val id = watcher.toString()

        uiDevice.registerWatcher(id) {
            val visible = watcher.isVisible()
            if (visible) block(watcher.scope())
            visible
        }

        val registration =
            object : WatcherRegistration {
                override fun unregister() {
                    uiDevice.removeWatcher(id)
                    watcherRegistrations.remove(this)
                }
            }

        watcherRegistrations.add(registration)
        return registration
    }

    /**
     * Performs a DFS on the accessibility tree starting from the root node in the active window and
     * returns the first node matching the given [block]. The node is returned as an [UiObject2]
     * that allows interacting with it. Internally it works searching periodically every
     * [pollIntervalMs].
     *
     * Example:
     * ```kotlin
     * onView { textAsString == "Search" }.click()
     * ```
     *
     * @param timeoutMs a timeout to find the view that satisfies the given condition.
     * @param pollIntervalMs an interval to wait before rechecking the accessibility tree for
     *   updates.
     * @param block a block that specifies a condition on the node to find.
     * @return a [UiObject2] from a node that matches the given [block] condition.
     */
    @JvmOverloads
    public fun onView(
        timeoutMs: Long = 10000,
        pollIntervalMs: Long = 100,
        block: AccessibilityNodeInfo.() -> (Boolean),
    ): UiObject2 = uiDevice.onView(timeoutMs, pollIntervalMs, block)

    /**
     * Performs a DFS on the accessibility tree starting from the root node in the active window and
     * returns the first node matching the given [block]. The node is returned as an [UiObject2]
     * that allows interacting with it. Internally it works searching periodically every
     * [pollIntervalMs].
     *
     * Example:
     * ```kotlin
     * onView { textAsString == "Search" }.click()
     * ```
     *
     * @param timeoutMs a timeout to find the view that satisfies the given condition.
     * @param pollIntervalMs an interval to wait before rechecking the accessibility tree for
     *   updates.
     * @param block a block that specifies a condition on the node to find.
     * @return a [UiObject2] from a node that matches the given [block] condition or null.
     */
    @JvmOverloads
    public fun onViewOrNull(
        timeoutMs: Long = 10000,
        pollIntervalMs: Long = 100,
        block: AccessibilityNodeInfo.() -> (Boolean),
    ): UiObject2? = uiDevice.onViewOrNull(timeoutMs, pollIntervalMs, block)

    /**
     * Performs a DFS on the accessibility tree starting from the root node in the active window and
     * returns the first node matching the given [block]. The node is returned as an [UiObject2]
     * that allows interacting with it. Internally it works searching periodically every
     * [pollIntervalMs].
     *
     * Example:
     * ```kotlin
     * node.onViews { isClass(Button::class.java) }
     * ```
     *
     * @param timeoutMs a timeout to find the view that satisfies the given condition.
     * @param pollIntervalMs an interval to wait before rechecking the accessibility tree for
     *   updates.
     * @param block a block that specifies a condition on the node to find.
     * @return a list of [UiObject2] from nodes that matches the given [block] condition.
     */
    @JvmOverloads
    public fun onViews(
        timeoutMs: Long = 10000,
        pollIntervalMs: Long = 100,
        block: AccessibilityNodeInfo.() -> (Boolean),
    ): List<UiObject2> = uiDevice.onViews(timeoutMs, pollIntervalMs, block)

    /**
     * Waits for an application to become visible. Note that internally it checks if an
     * accessibility node with the given [appPackageName] exists in the accessibility tree.
     *
     * @param appPackageName the package name of the app to wait for. By default is the target app
     *   package name.
     * @param timeoutMs a timeout for the app to become visible.
     * @return whether the app became visible in the given timeout.
     */
    @JvmOverloads
    public fun waitForAppToBeVisible(
        appPackageName: String = instrumentation.targetContext.packageName,
        timeoutMs: Long = 10000L,
    ): Boolean =
        uiDevice.waitForAppToBeVisible(
            appPackageName = appPackageName,
            timeoutMs = timeoutMs,
        )

    /**
     * Types the given [text] string simulating key press through [Instrumentation.sendKeySync].
     * This is similar to tapping the keys on a virtual keyboard and will trigger the same listeners
     * in the target app, as opposed to [AccessibilityNodeInfo.setText] that programmaticaly sets
     * the given text in the target node.
     *
     * @param text the text to type.
     */
    public fun type(text: String): Unit = uiDevice.type(text)

    /**
     * Similar to [type] but presses the delete key for the given [count] times.
     *
     * @param count how many times the press delete key should be pressed.
     */
    public fun pressDelete(count: Int): Unit = uiDevice.pressDelete(count)

    /** Press the enter key. */
    public fun pressEnter(): Boolean = uiDevice.pressEnter()

    /** Press the back key. */
    public fun pressBack(): Boolean = uiDevice.pressBack()

    /** Press the home key. */
    public fun pressHome(): Boolean = uiDevice.pressHome()

    /** Returns all the windows on all the displays. */
    public fun windows(): List<AccessibilityWindowInfo> = uiDevice.windows()

    /** Returns all the window roots on all the displays. */
    public fun windowRoots(): List<AccessibilityNodeInfo> = uiDevice.windowRoots

    /**
     * Returns the active window. Note that calling this method after [startApp], [startActivity] or
     * [startIntent] without waiting for the app to be visible, will return the active window at the
     * time of starting the app, i.e. the launcher if starting from there.
     */
    public fun activeWindow(): AccessibilityWindowInfo = activeWindowRoot().window

    /**
     * Returns the active window root node. Note that calling this method after [startApp],
     * [startActivity] or [startIntent] without waiting for the app to be visible, will return the
     * active window root at the time of starting the app, i.e. the root of the launcher if starting
     * from there.
     */
    public fun activeWindowRoot(): AccessibilityNodeInfo = uiDevice.waitForRootInActiveWindow()

    /** Starts the instrumentation test target app using the target app package name. */
    public fun startApp(): Unit = startApp(instrumentation.targetContext.packageName)

    /**
     * Starts the app with the given [packageName].
     *
     * @param packageName the package name of the app to start
     */
    public fun startApp(packageName: String): Unit = appManager.startApp(packageName = packageName)

    /**
     * Starts an activity with the given [packageName] and [activityName].
     *
     * @param packageName the app package name of the activity to start.
     * @param activityName the name of the activity to start.
     */
    public fun startActivity(
        packageName: String,
        activityName: String,
    ): Unit = appManager.startActivity(packageName = packageName, activityName = activityName)

    /**
     * Starts an activity with the given class.
     *
     * @param clazz the class of the activity to start.
     */
    public fun startActivity(clazz: Class<*>): Unit = appManager.startActivity(clazz = clazz)

    /**
     * Starts the given [intent].
     *
     * @param intent an intent to start
     */
    public fun startIntent(intent: Intent): Unit = appManager.startIntent(intent = intent)

    /** Clears the instrumentation test target app data. */
    public fun clearAppData(): Unit = appManager.clearAppData()
}
