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

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsConfiguration
import androidx.compose.ui.semantics.SemanticsProperties
import org.w3c.dom.Element
import org.w3c.dom.HTMLElement

internal fun setSizeAndPosition(
    element: HTMLElement, left: Float, top: Float, width: Float, height: Float
) {
    // language=javascript
    js(
        """
       element.style.left = "" + left + "px";
       element.style.top = "" + top + "px";
       element.style.width = "" + width + "px";
       element.style.height = "" + height + "px";
    """
    )
}

internal object AriaRoleId {
    const val Unknown = -1

    // Mapped from [androidx.compose.ui.semantics.Role] values:
    const val Button = 0
    const val Checkbox = 1
    const val Switch = 2
    const val RadioButton = 3
    const val Tab = 4
    const val Image = 5
    const val DropdownList = 6
    const val ValuePicker = Unknown // TODO: Any web alternative?
    const val Carousel = Unknown // TODO: Any web alternative?

    // https://developer.mozilla.org/en-US/docs/Web/Accessibility/ARIA/Reference/Roles
    // Other ARIA roles not specified explicitly by [androidx.compose.ui.semantics.Role]:
    const val Heading = 7
    const val TextBox = 8
    const val List = 9
    const val Grid = 10
    const val Dialog = 11
}

internal fun SemanticsConfiguration.getRoleId(): Int {
    // https://developer.mozilla.org/en-US/docs/Web/Accessibility/ARIA/Reference/Roles
    // Unfortunately, Role value is private, so we map it here:
    fun Role.toIntId(): Int = when (this) {
        Role.Button -> AriaRoleId.Button
        Role.Checkbox -> AriaRoleId.Checkbox
        Role.Switch -> AriaRoleId.Switch
        Role.RadioButton -> AriaRoleId.RadioButton
        Role.Tab -> AriaRoleId.Tab
        Role.Image -> AriaRoleId.Image
        Role.DropdownList -> AriaRoleId.DropdownList
        Role.ValuePicker -> AriaRoleId.Unknown // TODO: Any web alternative?
        Role.Carousel -> AriaRoleId.Unknown // TODO: Any web alternative?
        else -> AriaRoleId.Unknown
    }

    var roleId = AriaRoleId.Unknown

    if (this.contains(SemanticsProperties.Role)) {
        roleId = this[SemanticsProperties.Role].toIntId()
    }

    if (SemanticsActions.OnClick in this && roleId == AriaRoleId.Unknown) {
        // TODO: Not everything with OnClick is a button! For now default to button for unknown clickable roles
        roleId = Role.Button.toIntId()
    }

    if (this.contains(SemanticsProperties.Heading)) {
        roleId = AriaRoleId.Heading
    }

    if (this.contains(SemanticsProperties.EditableText)) {
        roleId = AriaRoleId.TextBox
    }

    if (this.contains(SemanticsProperties.CollectionInfo)) {
        val info = this.get(SemanticsProperties.CollectionInfo)
        roleId = if (info.columnCount > 1 && info.rowCount > 1) {
            AriaRoleId.Grid
        } else {
            AriaRoleId.List
        }
    }

    // Checked last: a layer's structural role outranks whatever its content looks like.
    if (this.contains(SemanticsProperties.IsDialog)) {
        roleId = AriaRoleId.Dialog
    }

    return roleId
}

// To avoid passing a Kotlin string to JS, we pass an int instead and map it to String on the JS side.
// See https://developer.mozilla.org/en-US/docs/Web/Accessibility/ARIA/Reference/Roles
internal fun setA11YAriaRole(element: HTMLElement, ariaRoleId: Int) {
    // language=javascript
    js(
        """
        var roleValue = "";
        switch (ariaRoleId) {
            case 0: // Role.Button
                roleValue = "button";
                break;
            case 1: // Role.Checkbox
                roleValue = "checkbox";
                break;
            case 2: // Role.Switch
                roleValue = "switch";
                break;
            case 3: // Role.RadioButton
                roleValue = "radio";
                break;
            case 4: // Role.Tab
                roleValue = "tab";
                break;
            case 5: // Role.Image
                roleValue = "img";
                break;
            case 6: // Role.DropdownList
                roleValue = "menu";
                break;
            case 7: // heading https://developer.mozilla.org/en-US/docs/Web/Accessibility/ARIA/Reference/Roles/heading_role
                roleValue = "heading";
                break;
            case 8: // https://developer.mozilla.org/en-US/docs/Web/Accessibility/ARIA/Reference/Roles/textbox_role
                roleValue = "textbox";
                break;
            case 9: // https://developer.mozilla.org/en-US/docs/Web/Accessibility/ARIA/Reference/Roles/list_role
                roleValue = "list";
                break;
            case 10: // https://developer.mozilla.org/en-US/docs/Web/Accessibility/ARIA/Reference/Roles/grid_role
                roleValue = "grid";
                break;
            case 11: // https://developer.mozilla.org/en-US/docs/Web/Accessibility/ARIA/Reference/Roles/dialog_role
                roleValue = "dialog";
                break;
            default:
                break;
        }
        if (roleValue.length > 0) { 
            element.setAttribute("role", roleValue);
        } else {
            element.removeAttribute("role");
        }
    """
    )
}

internal fun removeAllChildrenOf(element: HTMLElement) {
    // language=javascript
    js("element.replaceChildren()")
}

/**
 * https://developer.mozilla.org/en-US/docs/Web/HTML/Reference/Global_attributes/inert
 *  When an element is inert, it along with all of the element's descendants,
 *  including normally interactive elements such as links, buttons, and form controls are disabled
 *  because they cannot receive focus or be clicked.
 */
internal fun Element.setInert(inert: Boolean) {
    val element = this.unsafeCast<CanToggleAttribute>()
    element.toggleAttribute("inert", inert)
}

private external interface CanToggleAttribute : JsAny {
    // https://developer.mozilla.org/en-US/docs/Web/API/Element/toggleAttribute
    fun toggleAttribute(attributeName: String, force: Boolean): Boolean
}