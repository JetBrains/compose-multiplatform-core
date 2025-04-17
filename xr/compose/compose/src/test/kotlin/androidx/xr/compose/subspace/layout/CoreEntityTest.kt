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

package androidx.xr.compose.subspace.layout

import android.util.Log
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.xr.compose.spatial.ApplicationSubspace
import androidx.xr.compose.subspace.MainPanel
import androidx.xr.compose.testing.SubspaceTestingActivity
import androidx.xr.compose.testing.TestSetup
import androidx.xr.compose.testing.onSubspaceNodeWithTag
import androidx.xr.scenecore.PanelEntity
import androidx.xr.scenecore.scene
import com.google.common.truth.Truth.assertThat
import java.lang.IllegalArgumentException
import kotlin.test.assertFailsWith
import org.hamcrest.Matchers.containsString
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.junit.rules.ExpectedLogMessagesRule

@RunWith(AndroidJUnit4::class)
class CoreEntityTest {

    @get:Rule val composeTestRule = createAndroidComposeRule<SubspaceTestingActivity>()
    @get:Rule val expectedLogMessagesRule = ExpectedLogMessagesRule()

    @Test
    fun coreEntity_coreContentlessEntity_shouldThrowIfNotContentless() {
        composeTestRule.setContent { TestSetup {} }

        assertFailsWith<IllegalArgumentException> {
            CoreContentlessEntity(composeTestRule.activity.session.scene.activitySpace)
        }
    }

    @Test
    fun CoreMainPanelEntity_mainPanelSizeNonZero_shouldNotBeHidden() {
        composeTestRule.setContent {
            TestSetup {
                ApplicationSubspace {
                    MainPanel(SubspaceModifier.width(100.dp).height(100.dp).testTag("mainPanel"))
                }
            }
        }
        composeTestRule.waitForIdle()

        val mainPanelNode = composeTestRule.onSubspaceNodeWithTag("mainPanel").fetchSemanticsNode()
        val mainPanelSceneCoreEntity = mainPanelNode.semanticsEntity as PanelEntity?

        assertThat(mainPanelSceneCoreEntity).isNotNull()
        assertThat(mainPanelSceneCoreEntity!!.isHidden()).isFalse()
    }

    @Test
    fun coreMainPanelEntity_mainPanelSizeZero_shouldBeHiddenAndNotCrash() {
        composeTestRule.setContent {
            TestSetup {
                ApplicationSubspace {
                    MainPanel(SubspaceModifier.width(0.dp).height(0.dp).testTag("mainPanel"))
                }
            }
        }
        composeTestRule.waitForIdle()

        val mainPanelNode = composeTestRule.onSubspaceNodeWithTag("mainPanel").fetchSemanticsNode()
        val mainPanelSceneCoreEntity = mainPanelNode.semanticsEntity as PanelEntity?

        assertThat(mainPanelSceneCoreEntity).isNotNull()
        assertThat(mainPanelSceneCoreEntity!!.isHidden()).isTrue()
        expectedLogMessagesRule.expectLogMessage(
            Log.WARN,
            "MainPanel",
            containsString("The main panel will be hidden."),
        )
    }
}
