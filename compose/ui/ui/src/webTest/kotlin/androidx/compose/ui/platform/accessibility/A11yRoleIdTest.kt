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

package androidx.compose.ui.platform.accessibility

import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsConfiguration
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.dialog
import androidx.compose.ui.semantics.editableText
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.AnnotatedString
import kotlin.test.Test
import kotlin.test.assertEquals

class A11yRoleIdTest {

    @Test
    fun noRoleAndNoClickIsUnknown() {
        assertEquals(AriaRoleId.Unknown, config { }.getRoleId())
    }

    @Test
    fun clickableWithoutRoleFallsBackToButton() {
        assertEquals(AriaRoleId.Button, config { onClick { true } }.getRoleId())
    }

    @Test
    fun explicitRoleIsMapped() {
        assertEquals(AriaRoleId.Checkbox, config { role = Role.Checkbox }.getRoleId())
        assertEquals(AriaRoleId.Switch, config { role = Role.Switch }.getRoleId())
        assertEquals(AriaRoleId.RadioButton, config { role = Role.RadioButton }.getRoleId())
        assertEquals(AriaRoleId.Tab, config { role = Role.Tab }.getRoleId())
        assertEquals(AriaRoleId.Image, config { role = Role.Image }.getRoleId())
        assertEquals(AriaRoleId.DropdownList, config { role = Role.DropdownList }.getRoleId())
    }

    @Test
    fun explicitRoleIsNotOverriddenByOnClick() {
        listOf(
            Role.Checkbox to AriaRoleId.Checkbox,
            Role.Switch to AriaRoleId.Switch,
            Role.RadioButton to AriaRoleId.RadioButton,
            Role.Tab to AriaRoleId.Tab,
            Role.Image to AriaRoleId.Image,
            Role.DropdownList to AriaRoleId.DropdownList,
        ).forEach { (role, expectedRoleId) ->
            assertEquals(
                expectedRoleId,
                config {
                    this.role = role
                    onClick { true }
                }.getRoleId(),
                "clickable $role"
            )
        }
    }

    @Test
    fun clickableRoleWithoutAriaMappingFallsBackToButton() {
        assertEquals(
            AriaRoleId.Button,
            config {
                role = Role.ValuePicker
                onClick { true }
            }.getRoleId()
        )
    }

    @Test
    fun structuralRolesWinOverClickableFallback() {
        assertEquals(AriaRoleId.Heading, config { heading(); onClick { true } }.getRoleId())
        assertEquals(AriaRoleId.Dialog, config { dialog(); onClick { true } }.getRoleId())
        assertEquals(
            AriaRoleId.TextBox,
            config { editableText = AnnotatedString("text"); onClick { true } }.getRoleId()
        )
    }

    @Test
    fun structuralRolesWinOverExplicitRole() {
        assertEquals(
            AriaRoleId.Heading,
            config { role = Role.Button; heading() }.getRoleId()
        )
        assertEquals(
            AriaRoleId.Dialog,
            config { role = Role.Button; dialog() }.getRoleId()
        )
    }

    @Test
    fun collectionInfoMapsToListOrGrid() {
        assertEquals(
            AriaRoleId.List,
            config { collectionInfo = CollectionInfo(rowCount = 5, columnCount = 1) }.getRoleId()
        )
        assertEquals(
            AriaRoleId.Grid,
            config { collectionInfo = CollectionInfo(rowCount = 5, columnCount = 5) }.getRoleId()
        )
        assertEquals(
            AriaRoleId.List,
            config {
                collectionInfo = CollectionInfo(rowCount = 5, columnCount = 1)
                onClick { true }
            }.getRoleId()
        )
    }

    private fun config(block: SemanticsPropertyReceiver.() -> Unit) =
        SemanticsConfiguration().apply(block)
}
