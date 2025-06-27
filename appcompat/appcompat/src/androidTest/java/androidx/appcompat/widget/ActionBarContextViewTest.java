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

package androidx.appcompat.widget;

import static org.junit.Assert.assertEquals;

import android.view.View;

import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class ActionBarContextViewTest {

    @Rule
    public final ActivityScenarioRule<ActionBarContextViewActivity> mActivityTestRule =
            new ActivityScenarioRule<>(ActionBarContextViewActivity.class);

    @Test
    public void testOnConfigurationChanged_contentHeight() {
        mActivityTestRule.getScenario().onActivity(activity -> {
            final ActionBarContextView view = new ActionBarContextView(activity);

            // The value is specified in actionModeStyle
            assertEquals(200, view.getContentHeight());

            view.dispatchConfigurationChanged(view.getResources().getConfiguration());

            // The value must be the same after calling onConfigurationChanged
            assertEquals(200, view.getContentHeight());
        });
    }

    @Test
    public void testOnMeasure_externalVerticalPadding() {
        mActivityTestRule.getScenario().onActivity(activity -> {
            final ActionBarContextView view = new ActionBarContextView(activity);

            // The values are specified in actionModeStyle
            assertEquals(10, view.getPaddingLeft());
            assertEquals(20, view.getPaddingTop());
            assertEquals(30, view.getPaddingRight());
            assertEquals(40, view.getPaddingBottom());

            view.setPadding(15, 25, 35, 45);

            view.measure(
                    View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(2000, View.MeasureSpec.AT_MOST));

            // 210 = 200 (content height) + 5 (external top padding) + 5 (external bottom padding)
            assertEquals(210, view.getMeasuredHeight());
        });
    }
}
