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
package androidx.coordinatorlayout.widget;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.pressKey;
import static androidx.test.espresso.action.ViewActions.swipeUp;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isCompletelyDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;

import static org.junit.Assert.assertEquals;

import android.graphics.Rect;
import android.view.KeyEvent;
import android.view.View;

import androidx.coordinatorlayout.test.R;
import androidx.coordinatorlayout.testutils.AppBarStateChangedListener;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.testutils.PollingCheck;

import com.google.android.material.appbar.AppBarLayout;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.atomic.AtomicInteger;

@SuppressWarnings({"unchecked", "rawtypes"})
@LargeTest
@RunWith(AndroidJUnit4.class)
public class CoordinatorLayoutWithRecyclerViewKeyEventTest {

    @Rule
    public ActivityScenarioRule<CoordinatorWithRecyclerViewActivity> mActivityScenarioRule =
            new ActivityScenarioRule(CoordinatorWithRecyclerViewActivity.class);

    private AppBarLayout mAppBarLayout;
    private RecyclerView mRecyclerView;
    // Used to verify that the RecyclerView's item location is zero when using the UP Key.
    private LinearLayoutManager mLinearLayoutManager;

    private AppBarStateChangedListener.State mAppBarState =
            AppBarStateChangedListener.State.UNKNOWN;

    public static Matcher<View> isAtLeastHalfVisible() {
        return new TypeSafeMatcher<View>() {
            @Override
            protected boolean matchesSafely(View view) {
                Rect rect = new Rect();
                return view.getGlobalVisibleRect(rect)
                        && rect.width() * rect.height() >= (view.getWidth() * view.getHeight()) / 2;
            }
            @Override
            public void describeTo(Description description) {
                description.appendText("is at least half visible on screen");
            }
        };
    }

    @Before
    public void setup() {
        mActivityScenarioRule.getScenario().onActivity(activity -> {
            mAppBarLayout = activity.mAppBarLayout;
            mRecyclerView = activity.mRecyclerView;
            mLinearLayoutManager = (LinearLayoutManager) mRecyclerView.getLayoutManager();

            mAppBarLayout.addOnOffsetChangedListener(new AppBarStateChangedListener() {
                @Override
                public void onStateChanged(AppBarLayout appBarLayout, State state) {
                    mAppBarState = state;
                }
            });
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }

    /*** Tests ***/
    @Test
    @LargeTest
    public void isCollapsingToolbarExpanded_swipeDownMultipleKeysUp_isExpanded() {
        onView(withId(R.id.recycler_view)).check(matches(isAtLeastHalfVisible()));

        // Scrolls down content and collapses the CollapsingToolbarLayout in the AppBarLayout.
        onView(withId(R.id.coordinator)).perform(swipeUp());
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();

        // Espresso doesn't properly support swipeUp() with a CoordinatorLayout,
        // AppBarLayout/CollapsingToolbarLayout, and RecyclerView. From testing, it only
        // handles waiting until the AppBarLayout/CollapsingToolbarLayout is finished with its
        // transition, NOT waiting until the RecyclerView is finished with its scrolling.
        // This PollingCheck waits until the scroll is finished in the RecyclerView.
        AtomicInteger previousScroll = new AtomicInteger();
        PollingCheck.waitFor(() -> {
            AtomicInteger currentScroll = new AtomicInteger();

            mActivityScenarioRule.getScenario().onActivity(activity -> {
                currentScroll.set(activity.mRecyclerView.getScrollY());
            });

            boolean isDone = currentScroll.get() == previousScroll.get();
            previousScroll.set(currentScroll.get());

            return isDone;
        });

        // Verifies the CollapsingToolbarLayout in the AppBarLayout is collapsed.
        assertEquals(AppBarStateChangedListener.State.COLLAPSED, mAppBarState);
        onView(withId(R.id.recycler_view)).check(matches(isCompletelyDisplayed()));

        // First up keystroke gains focus (doesn't move any content).
        onView(withId(R.id.recycler_view)).perform(pressKey(KeyEvent.KEYCODE_DPAD_UP));
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();

        // Retrieve top visible item in the RecyclerView.
        int currentTopVisibleItem = mLinearLayoutManager.findFirstCompletelyVisibleItemPosition();

        // Scroll up to the 0 position in the RecyclerView via UP Keystroke.
        while (currentTopVisibleItem > 0) {
            onView(withId(R.id.recycler_view)).perform(pressKey(KeyEvent.KEYCODE_DPAD_UP));
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            currentTopVisibleItem = mLinearLayoutManager.findFirstCompletelyVisibleItemPosition();
        }

        // This is a fail-safe in case the DPAD UP isn't making any changes, we break out of the
        // loop.
        float previousAppBarLayoutY = 0.0f;

        // Performs a key press until the app bar is either expanded completely or no changes are
        // made in the app bar between the previous call and the current call (failure case).
        while (mAppBarState != AppBarStateChangedListener.State.EXPANDED
                && (mAppBarLayout.getY() != previousAppBarLayoutY)
        ) {
            previousAppBarLayoutY = mAppBarLayout.getY();

            // Partially expands the CollapsingToolbarLayout.
            onView(withId(R.id.recycler_view)).perform(pressKey(KeyEvent.KEYCODE_DPAD_UP));
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        }

        // Checks CollapsingToolbarLayout (in the AppBarLayout) is fully expanded.
        assertEquals(AppBarStateChangedListener.State.EXPANDED, mAppBarState);
    }

    @Test
    @LargeTest
    public void doesAppBarCollapse_pressKeyboardDownMultipleTimes() {
        onView(withId(R.id.recycler_view)).check(matches(isAtLeastHalfVisible()));

        // Scrolls down content (key) and collapses the CollapsingToolbarLayout in the AppBarLayout.
        // Gains focus
        onView(withId(R.id.recycler_view)).perform(pressKey(KeyEvent.KEYCODE_DPAD_DOWN));
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();

        // This is a fail-safe in case the DPAD UP isn't making any changes, we break out of the
        // loop.
        float previousAppBarLayoutY = 0.0f;

        // Performs a key press until the app bar is either completely collapsed or no changes are
        // made in the app bar between the previous call and the current call (failure case).
        while (mAppBarState != AppBarStateChangedListener.State.COLLAPSED
                && (mAppBarLayout.getY() != previousAppBarLayoutY)
        ) {
            previousAppBarLayoutY = mAppBarLayout.getY();

            // Partial collapse of the CollapsingToolbarLayout.
            onView(withId(R.id.recycler_view)).perform(pressKey(KeyEvent.KEYCODE_DPAD_DOWN));
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        }

        // Espresso doesn't properly support down with a CoordinatorLayout,
        // AppBarLayout/CollapsingToolbarLayout, and RecyclerView. From testing, it only
        // handles waiting until the AppBarLayout/CollapsingToolbarLayout is finished with its
        // transition, NOT waiting until the RecyclerView is finished with its scrolling.
        // This PollingCheck waits until the scroll is finished in the RecyclerView.
        AtomicInteger previousScroll = new AtomicInteger();
        PollingCheck.waitFor(() -> {
            AtomicInteger currentScroll = new AtomicInteger();

            mActivityScenarioRule.getScenario().onActivity(activity -> {
                currentScroll.set(activity.mRecyclerView.getScrollY());
            });

            boolean isDone = currentScroll.get() == previousScroll.get();
            previousScroll.set(currentScroll.get());

            return isDone;
        });

        // Verifies the CollapsingToolbarLayout in the AppBarLayout is collapsed.
        assertEquals(AppBarStateChangedListener.State.COLLAPSED, mAppBarState);
        onView(withId(R.id.recycler_view)).check(matches(isCompletelyDisplayed()));
    }
}
