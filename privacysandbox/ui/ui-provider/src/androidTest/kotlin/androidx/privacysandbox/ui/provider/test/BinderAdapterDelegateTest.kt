/*
 * Copyright 2023 The Android Open Source Project
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

package androidx.privacysandbox.ui.provider.test

import android.content.Context
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.view.Display
import android.view.MotionEvent
import android.view.SurfaceControlViewHost
import android.view.View
import android.widget.LinearLayout
import androidx.privacysandbox.ui.provider.TouchFocusTransferringView
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.slowSwipeLeft
import androidx.test.espresso.action.ViewActions.swipeLeft
import androidx.test.espresso.action.ViewActions.swipeUp
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withParent
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiScrollable
import androidx.test.uiautomator.UiSelector
import androidx.testutils.withActivity
import com.google.common.truth.Truth.assertThat
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@SdkSuppress(minSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
@RunWith(AndroidJUnit4::class)
@LargeTest
class BinderAdapterDelegateTest {
    companion object {
        const val TIMEOUT_MILLIS: Long = 2000
        const val WIDTH = 500
        const val HEIGHT = 500
        const val MAIN_LAYOUT_RES = "androidx.privacysandbox.ui.provider.test:id/main_layout"
    }

    @get:Rule val activityScenarioRule = ActivityScenarioRule(MainActivity::class.java)

    private val transferTouchFocusLatch = CountDownLatch(1)

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val activity = activityScenarioRule.withActivity { this }
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        activityScenarioRule.withActivity {
            val surfaceControlViewHost =
                GestureTransferringSurfaceControlViewHost(
                    activity,
                    activity.display!!,
                    Binder(),
                    transferTouchFocusLatch
                )
            val touchFocusTransferringView =
                TouchFocusTransferringView(context, surfaceControlViewHost)
            touchFocusTransferringView.addView(TestView(context))
            activity
                .findViewById<LinearLayout>(R.id.main_layout)
                .addView(touchFocusTransferringView, WIDTH, HEIGHT)
        }
    }

    @Test
    fun touchFocusTransferredForSwipeUp() {
        onView(withParent(withId(R.id.main_layout))).perform(swipeUp())
        assertThat(transferTouchFocusLatch.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)).isTrue()
    }

    @Test
    fun touchFocusNotTransferredForSwipeLeft() {
        onView(withParent(withId(R.id.main_layout))).perform(swipeLeft())
        assertThat(transferTouchFocusLatch.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)).isFalse()
    }

    @Test
    fun touchFocusNotTransferredForSlowSwipeLeft() {
        onView(withParent(withId(R.id.main_layout))).perform(slowSwipeLeft())
        assertThat(transferTouchFocusLatch.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)).isFalse()
    }

    @Test
    fun touchFocusNotTransferredForClicks() {
        onView(withParent(withId(R.id.main_layout))).perform(click())
        assertThat(transferTouchFocusLatch.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)).isFalse()
    }

    @Test
    fun touchFocusTransferredForFlingForward() {
        val parentSelector = UiSelector().resourceId(MAIN_LAYOUT_RES)
        val testView = UiScrollable(parentSelector.childSelector(UiSelector().index(0)))
        testView.flingForward()
        assertThat(transferTouchFocusLatch.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)).isTrue()
    }

    @Test
    fun touchFocusTransferredForFlingBackward() {
        val parentSelector = UiSelector().resourceId(MAIN_LAYOUT_RES)
        val testView = UiScrollable(parentSelector.childSelector(UiSelector().index(0)))
        testView.flingBackward()
        assertThat(transferTouchFocusLatch.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)).isTrue()
    }

    /**
     * SCVH that takes note of when touch focus is transferred.
     *
     * TODO(b/290629538): Add full integration test.
     */
    private class GestureTransferringSurfaceControlViewHost(
        context: Context,
        display: Display,
        hostToken: IBinder,
        countDownLatch: CountDownLatch
    ) : SurfaceControlViewHost(context, display, hostToken) {

        val latch = countDownLatch

        override fun transferTouchGestureToHost(): Boolean {
            latch.countDown()
            return true
        }
    }

    private class TestView(context: Context) : View(context) {
        override fun onTouchEvent(event: MotionEvent?): Boolean {
            return true
        }
    }
}
