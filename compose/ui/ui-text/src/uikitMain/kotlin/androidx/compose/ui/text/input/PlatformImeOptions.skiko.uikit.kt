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

package androidx.compose.ui.text.input

import platform.UIKit.UIKeyboardAppearance
import platform.UIKit.UIKeyboardAppearanceDefault
import platform.UIKit.UIKeyboardType
import platform.UIKit.UIKeyboardTypeDefault
import platform.UIKit.UIReturnKeyType
import platform.UIKit.UITextAutocapitalizationType
import platform.UIKit.UITextAutocorrectionType
import platform.UIKit.UITextContentType

private class PlatformImeOptionsImpl(
    val keyboardType: UIKeyboardType?,
    val keyboardAppearance: UIKeyboardAppearance,
    val returnKeyType: UIReturnKeyType?,
    val textContentType: UITextContentType,
    val isSecureTextEntry: Boolean?,
    val enablesReturnKeyAutomatically: Boolean,
    val autocapitalizationType: UITextAutocapitalizationType?,
    val autocorrectionType: UITextAutocorrectionType?,
    val hasExplicitTextContentType: Boolean
): PlatformImeOptions()

/**
 * Configuration for creating instances of PlatformImeOptions.
 */
class PlatformImeOptionsConfiguration internal constructor() {
    private var keyboardType: UIKeyboardType? = null
    private var keyboardAppearance: UIKeyboardAppearance = UIKeyboardAppearanceDefault
    private var returnKeyType: UIReturnKeyType? = null
    private var textContentType: UITextContentType? = null
    private var isSecureTextEntry: Boolean? = null
    private var enablesReturnKeyAutomatically: Boolean = false
    private var autocapitalizationType: UITextAutocapitalizationType? = null
    private var autocorrectionType: UITextAutocorrectionType? = null
    private var hasExplicitTextContentType: Boolean = false

    /**
     * Sets the keyboard type to be used for the text input field.
     * If not set, the value will be derived from [ImeOptions].
     *
     * See [UIKit documentation](https://developer.apple.com/documentation/uikit/uitextinputtraits/keyboardtype).
     */
    fun keyboardType(value: UIKeyboardType?): PlatformImeOptionsConfiguration = apply {
        keyboardType = value
    }

    /**
     * Sets the appearance of the keyboard.
     *
     * See [UIKit documentation](https://developer.apple.com/documentation/uikit/uitextinputtraits/keyboardappearance).
     */
    fun keyboardAppearance(value: UIKeyboardAppearance): PlatformImeOptionsConfiguration = apply {
        keyboardAppearance = value
    }

    /**
     * Sets the type of return key to display on the keyboard.
     * If not set, the value will be derived from [ImeOptions].
     *
     * See [UIKit documentation](https://developer.apple.com/documentation/uikit/uitextinputtraits/returnkeytype).
     */
    fun returnKeyType(value: UIReturnKeyType?): PlatformImeOptionsConfiguration = apply {
        returnKeyType = value
    }

    /**
     * Sets the semantic meaning of the text being entered.
     *
     * See [UIKit documentation](https://developer.apple.com/documentation/uikit/uitextinputtraits/textcontenttype).
     */
    fun textContentType(value: UITextContentType): PlatformImeOptionsConfiguration = apply {
        textContentType = value
        hasExplicitTextContentType = true
    }

    /**
     * Sets whether the text being entered should be treated as secure input.
     * If not set, the value will be derived from [ImeOptions].
     *
     * See [UIKit documentation](https://developer.apple.com/documentation/uikit/uitextinputtraits/issecuretextentry).
     */
    fun isSecureTextEntry(value: Boolean?): PlatformImeOptionsConfiguration = apply {
        isSecureTextEntry = value
    }

    /**
     * Sets whether the return key should be automatically enabled when text is present.
     *
     * See [UIKit documentation](https://developer.apple.com/documentation/uikit/uitextinputtraits/enablesreturnkeyautomatically).
     */
    fun enablesReturnKeyAutomatically(value: Boolean): PlatformImeOptionsConfiguration = apply {
        enablesReturnKeyAutomatically = value
    }

    /**
     * Sets the autocapitalization style to apply.
     * If not set, the value will be derived from [ImeOptions].
     *
     * See [UIKit documentation](https://developer.apple.com/documentation/uikit/uitextinputtraits/autocapitalizationtype).
     */
    fun autocapitalizationType(value: UITextAutocapitalizationType?): PlatformImeOptionsConfiguration = apply {
        autocapitalizationType = value
    }

    /**
     * Sets the autocorrection behavior to apply.
     * If not set, the value will be derived from [ImeOptions].
     *
     * See [UIKit documentation](https://developer.apple.com/documentation/uikit/uitextinputtraits/autocorrectiontype).
     */
    fun autocorrectionType(value: UITextAutocorrectionType?): PlatformImeOptionsConfiguration = apply {
        autocorrectionType = value
    }

    /**
     * Builds the final PlatformImeOptions instance with the configured values.
     */
    internal fun build(): PlatformImeOptions {
        return PlatformImeOptionsImpl(
            keyboardType = keyboardType,
            keyboardAppearance = keyboardAppearance,
            returnKeyType = returnKeyType,
            textContentType = textContentType,
            isSecureTextEntry = isSecureTextEntry,
            enablesReturnKeyAutomatically = enablesReturnKeyAutomatically,
            autocapitalizationType = autocapitalizationType,
            autocorrectionType = autocorrectionType,
            hasExplicitTextContentType = hasExplicitTextContentType
        )
    }
}
/**
 * Used to configure iOS platform IME options.
 *
 * Allows specifying UIKit-specific input method editor options. Note that any values
 * explicitly specified here will override the corresponding settings from the enclosing [ImeOptions].
 */
fun PlatformImeOptions(configure: PlatformImeOptionsConfiguration.() -> Unit): PlatformImeOptions {
    return PlatformImeOptionsConfiguration().apply(configure).build()
}

/**
 * Instance that mirrors UIKit's default settings.
 *
 * This instance sets all properties to their UIKit default values.
 */
val DefaultPlatformImeOptions: PlatformImeOptions = PlatformImeOptions {
    keyboardType(UIKeyboardTypeDefault)
    keyboardAppearance(UIKeyboardAppearanceDefault)
    returnKeyType(UIReturnKeyType.UIReturnKeyDefault)
    textContentType(null)
    isSecureTextEntry(false)
    enablesReturnKeyAutomatically(false)
    autocapitalizationType(UITextAutocapitalizationType.UITextAutocapitalizationTypeNone)
    autocorrectionType(UITextAutocorrectionType.UITextAutocorrectionTypeDefault)
}

val PlatformImeOptions.keyboardType: UIKeyboardType?
    get() = (this as? PlatformImeOptionsImpl)?.keyboardType

val PlatformImeOptions.keyboardAppearance: UIKeyboardAppearance
    get() = (this as? PlatformImeOptionsImpl)?.keyboardAppearance ?: UIKeyboardAppearanceDefault

val PlatformImeOptions.returnKeyType: UIReturnKeyType?
    get() = (this as? PlatformImeOptionsImpl)?.returnKeyType

val PlatformImeOptions.textContentType: UITextContentType
    get() = (this as? PlatformImeOptionsImpl)?.textContentType

val PlatformImeOptions.isSecureTextEntry: Boolean?
    get() = (this as? PlatformImeOptionsImpl)?.isSecureTextEntry

val PlatformImeOptions.enablesReturnKeyAutomatically: Boolean
    get() = (this as? PlatformImeOptionsImpl)?.enablesReturnKeyAutomatically ?: false

val PlatformImeOptions.autocapitalizationType: UITextAutocapitalizationType?
    get() = (this as? PlatformImeOptionsImpl)?.autocapitalizationType

val PlatformImeOptions.autocorrectionType: UITextAutocorrectionType?
    get() = (this as? PlatformImeOptionsImpl)?.autocorrectionType

val PlatformImeOptions.hasExplicitTextContentType: Boolean
    get() = (this as? PlatformImeOptionsImpl)?.hasExplicitTextContentType ?: false