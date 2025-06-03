/*
 * Copyright 2024 The Android Open Source Project
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

package androidx.compose.ui.window

import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.unit.IntSize
import kotlinx.browser.document
import org.w3c.dom.Element
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.HTMLStyleElement
import org.w3c.dom.HTMLTitleElement

private const val defaultCanvasElementId = "ComposeTarget"

/**
 * EXPERIMENTAL! Might be deleted or changed in the future!
 *
 * Initializes the composition in HTML canvas identified by [canvasElementId].
 *
 * It can be resized by providing [requestResize].
 * By default, it will listen to the window resize events.
 *
 * By default, styles will be applied to use the entire inner window, disabling scrollbars.
 * This can be turned off by setting [applyDefaultStyles] to false.
 */
@ExperimentalComposeUiApi
fun CanvasBasedWindow(
    title: String? = null,
    canvasElementId: String = defaultCanvasElementId,
    requestResize: (suspend () -> IntSize)? = null,
    applyDefaultStyles: Boolean = true,
    content: @Composable () -> Unit = { }
): DisposeComposeWindow {
    if (title != null) {
        val htmlTitleElement = (
            document.head!!.getElementsByTagName("title").item(0)
                ?: document.createElement("title").also { document.head!!.appendChild(it) }
            ) as HTMLTitleElement
        htmlTitleElement.textContent = title
    }

    if (applyDefaultStyles) {
        document.head!!.appendChild(
            (document.createElement("style") as HTMLStyleElement).apply {
                type = "text/css"
                appendChild(
                    document.createTextNode(
                        "body { margin: 0; overflow: hidden; } #$canvasElementId { outline: none; }"
                    )
                )
            }
        )
    }

    val canvas = document.getElementById(canvasElementId)?.let { it as HTMLCanvasElement }
        ?: error("failed to find element with id '$canvasElementId'")

    val window = ComposeWindow(
        canvas = canvas,
        content = content,
        state = if (requestResize == null) DefaultWindowState(document.documentElement!!) else ComposeWindowState.createFromLambda(requestResize)
    )

    return DisposeComposeWindow {
        window.dispose()
    }
}

/**
 * EXPERIMENTAL! Might be deleted or changed in the future!
 *
 * Creates the composition in HTML canvas created in parent container identified by [viewportContainerId] id.
 * This size of canvas is adjusted with the size of the container
 */
@ExperimentalComposeUiApi
fun ComposeViewport(
    viewportContainerId: String,
    content: @Composable () -> Unit = { }
): DisposeComposeWindow {
    val canvasContainer = document.getElementById(viewportContainerId) ?: error("failed to find element by viewportContainerId: '$viewportContainerId'")
    return ComposeViewport(canvasContainer, content)
}

/**
 * EXPERIMENTAL! Might be deleted or changed in the future!
 *
 * Creates the composition in HTML canvas created in parent container identified by [viewportContainer] Element.
 * This size of canvas is adjusted with the size of the container
 */
@ExperimentalComposeUiApi
fun ComposeViewport(
    viewportContainer: Element,
    content: @Composable () -> Unit = { }
): DisposeComposeWindow {
    val canvas = document.createElement("canvas") as HTMLCanvasElement
    canvas.setAttribute("tabindex", "0")

    viewportContainer.appendChild(canvas)

    val window = ComposeWindow(
        canvas = canvas,
        content = content,
        state = DefaultWindowState(viewportContainer)
    )

    return DisposeComposeWindow {
        window.dispose()
    }
}

/**
 * EXPERIMENTAL! Might be deleted or changed in the future!
 *
 * Interface for disposing Compose window resources.
 * It also removes the canvas element from the HTML hierarchy.
 * Returned by [CanvasBasedWindow] and [ComposeViewport] functions to allow proper cleanup
 * of resources when the window is no longer needed.
 */
@ExperimentalComposeUiApi
fun interface DisposeComposeWindow {
    /**
     * Disposes the Compose window and releases all associated resources.
     * Should be called when the window is no longer needed to prevent memory leaks.
     */
    fun dispose()
}
