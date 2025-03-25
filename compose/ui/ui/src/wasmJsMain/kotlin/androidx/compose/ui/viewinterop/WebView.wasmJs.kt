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

package androidx.compose.ui.viewinterop

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.roundToIntRect
import kotlinx.browser.document
import org.w3c.dom.HTMLElement

//val NoOpUpdate: Element.() -> Unit = {}
//
//@Composable
//private fun <T : Any> createNodeFactory(
//    factory: () -> T,
//): () -> LayoutNode {
//
//    return {
//        factory().layoutNode
//    }
//}
//
//internal abstract class WebUiApplier(root: Element) : AbstractApplier<Element>(root) {
//    override fun remove(index: Int, count: Int) {
//        current.remove()
//        // Реализация удаления узлов
//    }
//
//    override fun move(from: Int, to: Int, count: Int) {
//        // Реализация перемещения узлов
//    }
//}
//
//@Composable
//fun <T : Element> WebView (
//    factory: () -> T,
//    modifier: Modifier = Modifier,
//    update: (T) -> Unit = NoOpUpdate,
//) {
//    WebView(factory = factory, modifier = modifier, update = update, onRelease = NoOpUpdate)
//}
//
//@Composable
//fun <T : Element> WebView (
//    factory: () -> T,
//    modifier: Modifier = Modifier,
//    update: (T) -> Unit = NoOpUpdate,
//    onRelease: (T) -> Unit = NoOpUpdate,
//    onReset: ((T) -> Unit)? = null,
//){
//    val compositeKeyHash = currentCompositeKeyHash
////    val materializedModifiermaterializedModifier = currentComposer.materialize(modifier.focusInteropModifier())
//    val density = LocalDensity.current
//    val layoutDirection = LocalLayoutDirection.current
//    val compositionLocalMap = currentComposer.currentCompositionLocalMap
//
//    val lifecycleOwner = LocalLifecycleOwner.current
////    val savedStateRegistryOwner = LocalSavedStateRegistryOwner.current
//
//    if (onReset != null) {
//        ReusableComposeNode<LayoutNode, WebUiApplier>(
//            factory = createNodeFactory(factory),
//            update = {}
//        )
//    } else {
//        ComposeNode<LayoutNode, WebUiApplier>(
//            factory = {},
//            update = {}
//        )
//    }
//}

@Suppress("ACTUAL_WITHOUT_EXPECT") // https://youtrack.jetbrains.com/issue/KT-37316
internal actual typealias InteropViewGroup = HTMLElement

@Composable
fun <T : HTMLElement> WebUiView(
    factory: () -> T,
    modifier: Modifier = Modifier,
    update: (T) -> Unit = NoOp,
    onRelease: (T) -> Unit = NoOp,
    onReset: ((T) -> Unit)? = null,
) {
    val interopContainer = LocalInteropContainer.current

    InteropView(
        factory = { compositeKeyHash ->
            WebUiInteropViewHolder()
        },
        modifier.onGloballyPositioned {  },
        onReset,
        onRelease,
        update = {
            update(it)
        }
    )
}

internal class WebUiInteropViewHolder<T : HTMLElement>(
    factory: () -> T,
    interopContainer: InteropContainer,
    properties: WebInteropProperties,
    compositeKeyHash: Int,
) : WebUiInteropElementHolder<T>(
    factory,
    interopContainer,
    properties,
    compositeKeyHash
) {
    init {
        group.appendChild(typedInteropView)
    }

    override var userComponentRect: String
        get() = typedInteropView.style.cssText
        set(value) {
            typedInteropView.style.cssText = value
        }

    override fun insertInteropView(root: InteropViewGroup, index: Int) {
        val referenceNode = root.children.item(index)
        if (referenceNode != null) {
            root.insertBefore(group, referenceNode)
        } else {
            root.appendChild(group)
        }
        super.insertInteropView(root, index)
    }


    override fun removeInteropView(root: InteropViewGroup) {
        root.removeChild(group)
        super.removeInteropView(root)
    }
}

internal abstract class WebUiInteropElementHolder<T : HTMLElement>(
    factory: () -> T,
    interopContainer: InteropContainer,
    private val interopWrapper: HTMLElement,
    properties: WebInteropProperties,
    compositeKeyHash: Int
) : TypedInteropViewHolder<T>(
    factory = factory,
    interopContainer = interopContainer,
    group = interopWrapper,
    compositeKeyHash = compositeKeyHash,
    measurePolicy = MeasurePolicy { _, constraints ->
        layout(constraints.minWidth, constraints.minHeight) {
            // Пока ничего, так как HTML-элементы сами определяют размер
        }
    }
) {
    constructor(
        factory: () -> T,
        interopContainer: InteropContainer,
        properties: WebInteropProperties,
        compositeKeyHash: Int,
    ) : this(
        factory,
        interopContainer,
        interopWrapper = document.createElement("div") as HTMLElement,
        properties,
        compositeKeyHash
    )

    private var currentRect: IntRect? = null

    var properties = properties
        set(value) {
            if (field != value) {
                field = value
                onPropertiesChanged()
            }
        }

    protected abstract var userComponentRect: String

    override fun layoutAccordingTo(layoutCoordinates: LayoutCoordinates) {
        val newRect = layoutCoordinates.boundsInWindow().roundToIntRect()

        if (currentRect != newRect) {
            interopContainer.scheduleUpdate {
                group.style.apply {
                    left = "${newRect.left}px"
                    top = "${newRect.top}px"
                    width = "${newRect.width}px"
                    height = "${newRect.height}px"
                }
            }
            currentRect = newRect
        }
    }

    private fun onPropertiesChanged() {
        interopWrapper.style.apply {
            pointerEvents = if (properties.isInteractive) "auto" else "none"
            visibility = if (properties.isVisible) "visible" else "hidden"
        }
    }
}


data class WebInteropProperties(
    val isInteractive: Boolean = true,
    val isVisible: Boolean = true
) {
    companion object {
        val Default = WebInteropProperties()
    }
}
