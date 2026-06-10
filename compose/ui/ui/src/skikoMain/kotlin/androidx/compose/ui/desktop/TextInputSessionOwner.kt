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

package androidx.compose.ui.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.NoriaOnly
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.platform.PlatformTextInputSessionScope

/**
 * Owns the platform text input session for a desktop window. A window delegates the platform text
 * input session lifecycle to its implementation of this interface (e.g. via Kotlin delegation), so
 * that all desktop platforms route IME through a single, consistent session-owner abstraction.
 */
interface TextInputSessionOwner {
    suspend fun textInputSession(
        session: suspend PlatformTextInputSessionScope<*>.() -> Nothing
    ): Nothing

    /**
     * Should be used in tests to wait when editor is ready to handle typing.
     */
    @NoriaOnly
    fun isTextInputSessionActive(): Boolean

    @NoriaOnly
    fun handleEventWithInputSession(keyEvent: KeyEvent): Boolean
}

/**
 * Provides the [TextInputSessionOwner] of the hosting desktop window to the composition. Desktop
 * windows provide their own owner; it is the same instance returned by
 * [androidx.compose.ui.platform.PlatformContext.textInputSessionOwner].
 */
val LocalTextInputSessionOwner = staticCompositionLocalOf<TextInputSessionOwner?> { null }

/**
 * Returns the [TextInputSessionOwner] of the hosting desktop window. Used by editors to offer key
 * events to the active IME session (e.g. [TextInputSessionOwner.handleEventWithInputSession]).
 */
@Composable
fun requireOwner(): TextInputSessionOwner =
    LocalTextInputSessionOwner.current
        ?: error("No TextInputSessionOwner provided in the current composition")
