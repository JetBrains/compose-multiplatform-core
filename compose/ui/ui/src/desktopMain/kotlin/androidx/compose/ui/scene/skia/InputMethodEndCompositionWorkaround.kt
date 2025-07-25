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

package androidx.compose.ui.scene.skia

import java.awt.AWTEvent
import java.awt.Component
import java.awt.event.FocusEvent
import java.awt.im.InputContext
import java.util.*
import org.jetbrains.skiko.OS
import org.jetbrains.skiko.hostOs
import org.jetbrains.skiko.SkiaLayer

/**
 * The interface for a workaround applied to [SkiaLayer] that commits the input method composition
 * on focus changes.
 *
 * See https://github.com/JetBrains/compose-multiplatform-core/pull/2026 for a discussion of the
 * issue(s) and the fix.
 */
internal interface InputMethodEndCompositionWorkaround {

    /**
     * The [InputContext] that [SkiaLayer.getInputContext] should return; `null` if it should return
     * the default one.
     */
    val inputContext: InputContext?

    /**
     * An implementation of the workaround for [sun.lwawt.macosx.CInputMethod].
     */
    class CInputMethodWorkaround(
        val componentInputContext: () -> InputContext?
    ) : InputMethodEndCompositionWorkaround {
        override val inputContext: InputContext = object : DelegatingInputContext(componentInputContext) {
            override fun dispatchEvent(event: AWTEvent) {
                val componentInputContext = componentInputContext() ?: return

                // Try to end the composition already on focus-lost, but can't do it if some other
                // component is gaining focus because then it might receive the InputMethodEvent
                // committing the composition.
                // Note that this works on JBR (17.0.14), but not on e.g. Corretto, because in
                // Corretto, by the time CInputMethod.unmarkText is called, there is no
                // fAwtFocussedComponent.
                if ((event is FocusEvent) && (event.id == FocusEvent.FOCUS_LOST) && (event.oppositeComponent == null)) {
                    componentInputContext.endComposition()
                }

                componentInputContext.dispatchEvent(event)

                if (event.id == FocusEvent.FOCUS_GAINED) {
                    componentInputContext.endComposition()
                }
            }
        }
    }

    companion object {
        /**
         * Returns the workaround for the current JVM/OS.
         *
         * @param componentInputContext A function that returns the [SkiaLayer]s original
         * [InputContext]
         */
        fun forCurrentEnvironment(
            componentInputContext: () -> InputContext?
        ): InputMethodEndCompositionWorkaround? = when (hostOs) {
            OS.MacOS -> CInputMethodWorkaround(componentInputContext)
            else -> null
        }
    }
}

/**
 * An [InputContext] that redirects all calls to [delegate].
 */
private abstract class DelegatingInputContext(
    val delegate: () -> InputContext?,
) : InputContext() {

    override fun dispatchEvent(event: AWTEvent) {
        delegate()?.dispatchEvent(event)
    }

    override fun selectInputMethod(locale: Locale?): Boolean {
        return delegate()?.selectInputMethod(locale) ?: false
    }

    override fun getLocale(): Locale? {
        return delegate()?.locale
    }

    override fun setCharacterSubsets(subsets: Array<out Character.Subset>?) {
        delegate()?.setCharacterSubsets(subsets)
    }

    override fun setCompositionEnabled(enable: Boolean) {
        delegate()?.setCompositionEnabled(enable)
    }

    override fun isCompositionEnabled(): Boolean {
        return delegate()?.isCompositionEnabled ?: false
    }

    override fun reconvert() {
        delegate()?.reconvert()
    }

    override fun removeNotify(client: Component?) {
        delegate()?.removeNotify(client)
    }

    override fun endComposition() {
        delegate()?.endComposition()
    }

    override fun dispose() {
        delegate()?.dispose()
    }

    override fun getInputMethodControlObject(): Any? {
        return delegate()?.inputMethodControlObject
    }
}