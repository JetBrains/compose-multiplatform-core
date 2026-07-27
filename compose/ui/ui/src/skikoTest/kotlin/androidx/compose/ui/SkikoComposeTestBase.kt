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

package androidx.compose.ui

import androidx.compose.ui.platform.registerSkikoComposeImplementation

/**
 * Base class for tests that use Compose primitives backed by the Skiko implementation.
 *
 * The implementation is registered at construction time (rather than in a per-test setup method)
 * so that it is already available to subclass field initializers. Registration is idempotent.
 */
@OptIn(InternalComposeUiApi::class)
abstract class SkikoComposeTestBase {
    init {
        registerSkikoComposeImplementation()
    }

    // TODO: re-enable per-test cleanup once tests that register the backend asynchronously
    //  (e.g. AWT ComposePanel/ComposeWindow via ComposeContainer on the EDT) no longer rely on
    //  the registration persisting across tests.
    // @AfterTest
    // fun clearSkikoBackend() {
    //     clearSkikoComposeImplementation()
    // }
}
