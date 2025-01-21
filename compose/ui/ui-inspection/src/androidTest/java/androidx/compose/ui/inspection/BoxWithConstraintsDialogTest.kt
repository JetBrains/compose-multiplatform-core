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

package androidx.compose.ui.inspection

import android.view.inspector.WindowInspector
import androidx.compose.ui.inspection.inspector.InspectorNode
import androidx.compose.ui.inspection.inspector.MutableInspectorNode
import androidx.compose.ui.inspection.rules.JvmtiRule
import androidx.compose.ui.inspection.rules.sendCommand
import androidx.compose.ui.inspection.testdata.BoxWithConstraintsDialogTestActivity
import androidx.compose.ui.inspection.util.GetComposablesCommand
import androidx.compose.ui.inspection.util.GetUpdateSettingsCommand
import androidx.compose.ui.inspection.util.toMap
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.IntRect
import androidx.inspection.testing.InspectorTester
import androidx.test.filters.LargeTest
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import layoutinspector.compose.inspection.LayoutInspectorComposeProtocol.ComposableNode
import layoutinspector.compose.inspection.LayoutInspectorComposeProtocol.GetComposablesResponse
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

@LargeTest
class BoxWithConstraintsDialogTest {
    private val rule = createAndroidComposeRule<BoxWithConstraintsDialogTestActivity>()

    @get:Rule val chain = RuleChain.outerRule(JvmtiRule()).around(rule)!!

    private lateinit var inspectorTester: InspectorTester

    @Before
    fun before() {
        runBlocking {
            inspectorTester = InspectorTester(inspectorId = "layoutinspector.compose.inspection")
        }
    }

    @After
    fun after() {
        inspectorTester.dispose()
    }

    @Test
    fun openDialog(): Unit = runBlocking {
        inspectorTester.sendCommand(GetUpdateSettingsCommand()).updateSettingsResponse

        val roots = WindowInspector.getGlobalWindowViews()
        assertThat(roots).hasSize(2)
        val appViewId = roots.first().uniqueDrawingId
        val dialogViewId = roots.last().uniqueDrawingId
        val app =
            inspectorTester.sendCommand(GetComposablesCommand(appViewId)).getComposablesResponse
        val appRoots = app.roots()
        val column = appRoots.single()
        assertThat(column.name).isEqualTo("Column")
        val text = column.children.single()
        assertThat(text.name).isEqualTo("Text")
        assertThat(text.children).isEmpty()

        val dialog =
            inspectorTester.sendCommand(GetComposablesCommand(dialogViewId)).getComposablesResponse
        val dialogRoots = dialog.roots()
        val box = dialogRoots.single()
        assertThat(box.name).isEqualTo("BoxWithConstraints")
        val alert = box.children.single()
        assertThat(alert.name).isEqualTo("AlertDialog")
        val button = alert.children.single()
        assertThat(button.name).isEqualTo("Button")
        val buttonText = button.children.single()
        assertThat(buttonText.name).isEqualTo("Text")
    }

    private fun GetComposablesResponse.roots(): List<InspectorNode> {
        val strings = stringsList.toMap()
        return rootsList.flatMap { it.nodesList.convert(strings) }
    }

    private fun List<ComposableNode>.convert(strings: Map<Int, String>): List<InspectorNode> = map {
        val node = MutableInspectorNode()
        node.name = strings[it.name] ?: ""
        node.box =
            IntRect(
                it.bounds.layout.x,
                it.bounds.layout.y,
                it.bounds.layout.x + it.bounds.layout.w,
                it.bounds.layout.y + it.bounds.layout.h
            )
        node.children.addAll(it.childrenList.convert(strings))
        node.inlined = (it.flags and ComposableNode.Flags.INLINED_VALUE) != 0
        node.build()
    }
}
