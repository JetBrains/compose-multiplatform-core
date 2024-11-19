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

package androidx.core.viewtree

import android.app.Activity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.RelativeLayout
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
class ViewTreeTest {

    @get:Rule val activityTestRule = ActivityScenarioRule(Activity::class.java)

    @Test
    fun setDisjointParent() {
        onActivity { activity ->
            val contentView = FrameLayout(activity)
            activity.setContentView(contentView)

            val disjointChild = View(activity)
            disjointChild.setViewTreeDisjointParent(contentView)

            assertEquals(contentView, disjointChild.getParentOrViewTreeDisjointParent())
        }
    }

    @Test
    fun replaceDisjointParent() {
        onActivity { activity ->
            val contentView = FrameLayout(activity)
            val contentChild = LinearLayout(activity)
            contentView.addView(contentChild)
            activity.setContentView(contentView)

            val disjointChild = View(activity)
            disjointChild.setViewTreeDisjointParent(contentView)
            assertEquals(contentView, disjointChild.getParentOrViewTreeDisjointParent())

            disjointChild.setViewTreeDisjointParent(contentChild)
            assertEquals(contentChild, disjointChild.getParentOrViewTreeDisjointParent())
        }
    }

    @Test
    fun clearDisjointParent() {
        onActivity { activity ->
            val contentView = FrameLayout(activity)
            activity.setContentView(contentView)

            val disjointChild = View(activity)
            disjointChild.setViewTreeDisjointParent(contentView)
            assertEquals(contentView, disjointChild.getParentOrViewTreeDisjointParent())

            disjointChild.setViewTreeDisjointParent(null)
            assertEquals(null, disjointChild.getParentOrViewTreeDisjointParent())
        }
    }

    @Test
    fun disjointParentsAllTheWayDown() {
        onActivity { activity ->
            val root = FrameLayout(activity)
            val child = LinearLayout(activity)
            val grandChild = RelativeLayout(activity)
            val greatGrandChild = View(activity)

            child.setViewTreeDisjointParent(root)
            grandChild.setViewTreeDisjointParent(child)
            greatGrandChild.setViewTreeDisjointParent(grandChild)

            assertEquals(null, root.getParentOrViewTreeDisjointParent())
            assertEquals(root, child.getParentOrViewTreeDisjointParent())
            assertEquals(child, grandChild.getParentOrViewTreeDisjointParent())
            assertEquals(grandChild, greatGrandChild.getParentOrViewTreeDisjointParent())
        }
    }

    @Test
    fun disjointParentDoesNotEraseParent() {
        onActivity { activity ->
            val root = FrameLayout(activity)
            val child = LinearLayout(activity)
            val grandChild = RelativeLayout(activity)
            val greatGrandChild = View(activity)

            root.addView(child)
            child.addView(grandChild)
            grandChild.addView(greatGrandChild)

            activity.setContentView(root)

            greatGrandChild.setViewTreeDisjointParent(root)
            assertEquals(grandChild, greatGrandChild.getParentOrViewTreeDisjointParent())
        }
    }

    private inline fun onActivity(crossinline action: (Activity) -> Unit) {
        activityTestRule.scenario.onActivity { action(it) }
    }
}
