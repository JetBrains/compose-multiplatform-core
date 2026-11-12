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

package androidx.compose.ui.text

/**
 * These flags are considered temporary, and there should be no expectation for these flags be
 * around for an extended period of time. If you have a regression that one of these flags fixes, it
 * is strongly encouraged for you to file a bug ASAP.
 */
@ExperimentalTextApi
object IosComposeUiTextFlags {

    /**
     * When `true`, text fields use the native iOS text input by default. Can be overridden for a
     * particular text field via
     * [androidx.compose.ui.text.input.PlatformImeOptionsConfiguration.usingNativeTextInput].
     */
    @ExperimentalTextApi
    var isNativeTextInputEnabled: Boolean = true
}
