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

/**
 * Flag to disable WindowInsetsRulers. Some integrations need to disable WindowInsets Rulers for all
 * Compose roots, so this is a global switch. We don't want them to add a new Compose root and have
 * it suddenly request WindowInsets updates, changing the behavior so that the insets suddenly
 * notify.
 */
internal var areWindowInsetsRulersEnabled: Boolean = true

/**
 * Used to disable [androidx.compose.ui.layout.WindowInsetsRulers]. This can be used when UI never
 * reads WindowInsets across all Compose roots to reduce the overhead of requesting WindowInsets
 * updates. Only call this when no Compose roots will ever need to handle insets over the lifetime of
 * the application. This should be called before the first Compose root is created.
 */
@ExperimentalComposeUiApi
fun disableWindowInsetsRulers() {
    areWindowInsetsRulersEnabled = false
}
