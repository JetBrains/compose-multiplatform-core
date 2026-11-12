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

@file:OptIn(ExperimentalWasmJsInterop::class)

package androidx.compose.ui.window

import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsAny
import kotlin.js.js
import kotlinx.browser.document
import org.w3c.dom.HTMLElement

/**
 * We use a custom element for the Compose container to implement the implicit window disposal via
 * the custom element `disconnectedCallback`.
 */
internal object ComposeWindowCustomElement {
    private val DomDisposableRegistry = WeakMap<JsAny>()

    fun onDispose(element: HTMLElement, handler: () -> Unit) {
        DomDisposableRegistry.set(element, {
            handler()
            DomDisposableRegistry.delete(element)
        })
    }

    fun createComposeComponent(): HTMLElement {
        if (customElements.get("compose-component") == null) {
            customElements.define(
                "compose-component",
                composeComponentElementCtor(DomDisposableRegistry)
            )
        }

        return document.createElement("compose-component") as HTMLElement
    }
}

// A real ES6 `class extends HTMLElement` is required by `customElements.define`.
// Kotlin/Wasm does not emit Kotlin classes as JS constructors, so we create the
// constructor in JS and return it as a JS function so it can be passed directly
// to `customElements.define`.
private fun composeComponentElementCtor(weakMap: WeakMap<JsAny>): JsAny = js(
    """
    (() => {
        class ComposeComponentElement extends HTMLElement {
            disconnectedCallback() {
                const cb = weakMap.get(this);
                if (cb) cb();
            }
        }
        return ComposeComponentElement;
    })()
"""
)

// https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/WeakMap
// Kotlin/Wasm JS interop  only allows type parameters with an upper bound of `JsAny` or its subtypes,
// while the actual value stored here is a Kotlin function type `() -> Unit`.
private external class WeakMap<K : JsAny>() : JsAny {
    fun get(key: K): (() -> Unit)?
    fun set(key: K, value: () -> Unit): WeakMap<K>
    fun has(key: K): Boolean
    fun delete(key: K): Boolean
}

private external interface CustomElementRegistry : JsAny {
    fun get(name: String): JsAny?
    fun define(name: String, constructor: JsAny)
}

private external val customElements: CustomElementRegistry
