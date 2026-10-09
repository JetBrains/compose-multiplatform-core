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

package androidx.compose.ui.viewinterop

import androidx.compose.ui.ExperimentalComposeUiApi

/**
 * Determines which UIKit safe-area insets are exposed to an interop component.
 */
@ExperimentalComposeUiApi
sealed interface UIKitInteropSafeAreaInsetsPolicy {
    /**
     * The interop component receives zero insets while its host overlaps UIKit's unsafe area, and
     * UIKit's computed insets otherwise.
     */
    data object Automatic : UIKitInteropSafeAreaInsetsPolicy

    /** The interop component receives UIKit's computed safe-area insets. */
    data object Inherit : UIKitInteropSafeAreaInsetsPolicy

    /** The interop component and its UIKit descendants receive zero safe-area insets. */
    data object Ignore : UIKitInteropSafeAreaInsetsPolicy
}
