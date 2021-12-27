/*
 * Copyright 2021 The Android Open Source Project
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

package androidx.compose.ui.platform

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.focus.requestFocus
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.node.LayoutNode
import androidx.compose.ui.semantics.AccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsConfiguration
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.delay
import org.jetbrains.skia.BreakIterator
import java.awt.Color
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Font
import java.awt.FontMetrics
import java.awt.Point
import java.awt.Rectangle
import java.awt.event.FocusListener
import java.util.Locale
import javax.accessibility.Accessible
import javax.accessibility.AccessibleAction
import javax.accessibility.AccessibleComponent
import javax.accessibility.AccessibleContext
import javax.accessibility.AccessibleContext.ACCESSIBLE_CARET_PROPERTY
import javax.accessibility.AccessibleContext.ACCESSIBLE_STATE_PROPERTY
import javax.accessibility.AccessibleContext.ACCESSIBLE_TEXT_PROPERTY
import javax.accessibility.AccessibleEditableText
import javax.accessibility.AccessibleExtendedText
import javax.accessibility.AccessibleRelation
import javax.accessibility.AccessibleRole
import javax.accessibility.AccessibleState
import javax.accessibility.AccessibleStateSet
import javax.accessibility.AccessibleText
import javax.accessibility.AccessibleText.CHARACTER
import javax.accessibility.AccessibleText.SENTENCE
import javax.accessibility.AccessibleText.WORD
import javax.accessibility.AccessibleTextSequence
import javax.accessibility.AccessibleValue
import javax.swing.text.AttributeSet

internal class AccessibilityControllerImpl(
    val owner: SkiaBasedOwner,
    val desktopComponent: PlatformComponent
) : AccessibilityController {
    private var currentNodesInvalidated = true
    var _currentNodes: Map<Int, ComposeAccessible> = mapOf()
    val currentNodes: Map<Int, ComposeAccessible>
        get() {
            if (currentNodesInvalidated) {
                _currentNodes = syncNodes(_currentNodes)
                currentNodesInvalidated = false
            }
            return _currentNodes
        }

    fun fireNewNodeEvent(accessible: ComposeAccessible) {}

    fun fireRemovedNodeEvent(accessible: ComposeAccessible) {}

    fun fireChangedNodeEvent(
        component: ComposeAccessible,
        previousSemanticsNode: SemanticsNode,
        newSemanticsNode: SemanticsNode
    ) {
        for (entry in newSemanticsNode.config) {
            val prev = previousSemanticsNode.config.getOrNull(entry.key)
            if (entry.value != prev) {
                when (entry.key) {
                    SemanticsProperties.Text -> {
                        component.accessibleContext.firePropertyChange(
                            ACCESSIBLE_TEXT_PROPERTY,
                            prev, entry.value
                        )
                    }
                    SemanticsProperties.EditableText -> {
                        component.accessibleContext.firePropertyChange(
                            ACCESSIBLE_TEXT_PROPERTY,
                            prev, entry.value
                        )
                    }
                    SemanticsProperties.TextSelectionRange -> {
                        component.accessibleContext.firePropertyChange(
                            ACCESSIBLE_CARET_PROPERTY,
                            prev, (entry.value as TextRange).start
                        )
                    }
                    SemanticsProperties.Focused ->
                        if (entry.value as Boolean) {
                            component.accessibleContext.firePropertyChange(
                                ACCESSIBLE_STATE_PROPERTY,
                                null, AccessibleState.FOCUSED
                            )
                        } else {
                            component.accessibleContext.firePropertyChange(
                                ACCESSIBLE_STATE_PROPERTY,
                                AccessibleState.FOCUSED, null
                            )
                        }
                    SemanticsProperties.ToggleableState -> {
                        when (entry.value as ToggleableState) {
                            ToggleableState.On ->
                                component.accessibleContext.firePropertyChange(
                                    ACCESSIBLE_STATE_PROPERTY,
                                    null, AccessibleState.CHECKED
                                )
                            ToggleableState.Off, ToggleableState.Indeterminate ->
                                component.accessibleContext.firePropertyChange(
                                    ACCESSIBLE_STATE_PROPERTY,
                                    AccessibleState.CHECKED, null
                                )
                        }
                    }
                }
            }
        }
    }

    private var lastActive: Long = 0

    private val backgroundSyncActiveThreshold = 1000 * 60 * 5

    internal fun itsActiveNow() {
        lastActive = System.currentTimeMillis()
    }

    override suspend fun syncLoop() {
        while (true) {
            if (System.currentTimeMillis() - lastActive < backgroundSyncActiveThreshold) {
                _currentNodes = syncNodes(_currentNodes)
                currentNodesInvalidated = false
            }
            delay(100)
        }
    }

    private fun syncNodes(previous: Map<Int, ComposeAccessible>): Map<Int, ComposeAccessible> {
        val nodes = mutableMapOf<Int, ComposeAccessible>()
        if (!rootSemanticNode.layoutNode.isPlaced) {
            return nodes
        }

        fun findAllSemanticNodesRecursive(currentNode: SemanticsNode) {
            nodes[currentNode.id] = previous[currentNode.id]?.let {
                val prevSemanticsNode = it.semanticsNode
                it.semanticsNode = currentNode
                fireChangedNodeEvent(it, prevSemanticsNode, currentNode)
                it
            } ?: ComposeAccessible(currentNode, this).also {
                fireNewNodeEvent(it)
            }

            // TODO fake nodes?
            // TODO find only visible nodes?

            val children = currentNode.replacedChildren
            for (i in children.size - 1 downTo 0) {
                findAllSemanticNodesRecursive(children[i])
            }
        }

        findAllSemanticNodesRecursive(rootSemanticNode)
        for ((id, prevNode) in previous.entries) {
            if (nodes[id] == null) {
                fireRemovedNodeEvent(prevNode)
            }
        }
        return nodes
    }

    override fun onLayoutChange(layoutNode: LayoutNode) {
        currentNodesInvalidated = true
    }

    override fun onSemanticsChange() {
        currentNodesInvalidated = true
    }

    val rootSemanticNode: SemanticsNode
        get() = owner.semanticsOwner.rootSemanticsNode

    val rootAccessible: ComposeAccessible
        get() = currentNodes[rootSemanticNode.id]!!
}

fun Accessible.print(level: Int = 0) {
    val context = accessibleContext
    val id = if (this is ComposeAccessible) {
        this.semanticsNode.id.toString()
    } else {
        "unknown"
    }
    val str = buildString {
        (1..level).forEach {
            append('\t')
        }
        append(
            "ID: $id Name: ${context.accessibleName} " +
                "Description: ${context.accessibleDescription} " +
                "Role: ${context.accessibleRole} " +
                "Bounds: ${(context as? AccessibleComponent)?.bounds}"
        )
    }
    println(str)

    (0 until context.accessibleChildrenCount).forEach { child ->
        context.getAccessibleChild(child).print(level + 1)
    }
}

private fun <T> SemanticsConfiguration.getFirst(key: SemanticsPropertyKey<List<T>>): T? {
    return getOrNull(key)?.firstOrNull()
}

internal class ComposeAccessible(
    var semanticsNode: SemanticsNode,
    val controller: AccessibilityControllerImpl? = null
) : Accessible {
    val accessibleContext: ComposeAccessibleComponent by lazy { ComposeAccessibleComponent() }
    override fun getAccessibleContext(): AccessibleContext = accessibleContext

    open inner class ComposeAccessibleComponent : AccessibleContext(), AccessibleComponent {
        val textSelectionRange
            get() = semanticsNode.config.getOrNull(SemanticsProperties.TextSelectionRange)
        val setText
            get() = semanticsNode.config.getOrNull(SemanticsActions.SetText)
        val setSelection
            get() = semanticsNode.config.getOrNull(SemanticsActions.SetSelection)
        val text
            get() = semanticsNode.config.getOrNull(SemanticsProperties.EditableText)
                ?: semanticsNode.config.getFirst(SemanticsProperties.Text)

        val textLayoutResult: TextLayoutResult?
            get() {
                val textLayoutResults = mutableListOf<TextLayoutResult>()
                val getLayoutResult = semanticsNode.config
                    .getOrNull(SemanticsActions.GetTextLayoutResult)
                    ?.action?.invoke(textLayoutResults)
                return if (getLayoutResult == true) {
                    textLayoutResults[0]
                } else {
                    null
                }
            }

        val focused
            get() = semanticsNode.config.getOrNull(SemanticsProperties.Focused)

        val selected
            get() = semanticsNode.config.getOrNull(SemanticsProperties.Selected)

        private val density: Density
            get() = controller?.owner?.density ?: Density(1f)

        val horizontalScroll
            get() = semanticsNode.config.getOrNull(SemanticsProperties.HorizontalScrollAxisRange)

        val verticalScroll
            get() = semanticsNode.config.getOrNull(SemanticsProperties.VerticalScrollAxisRange)

        val scrollBy
            get() = semanticsNode.config.getOrNull(SemanticsActions.ScrollBy)

        val isPassword
            get() = semanticsNode.config.getOrNull(SemanticsProperties.Password) != null

        val toggleableState
            get() = semanticsNode.config.getOrNull(SemanticsProperties.ToggleableState)

        val auxiliaryChildren = mutableListOf<Accessible>().let { children ->
            horizontalScroll?.let {
                children.add(makeScrollbarChild(false))
            }
            verticalScroll?.let {
                children.add(makeScrollbarChild(true))
            }

            children.toList()
        }

        private fun makeScrollbarChild(
            vertical: Boolean
        ): Accessible {
            val bar = ScrollBarAccessible(vertical)

            val controlledBy = AccessibleRelation(
                AccessibleRelation.CONTROLLED_BY,
                bar
            )
            val controllerFor = AccessibleRelation(
                AccessibleRelation.CONTROLLER_FOR,
                this@ComposeAccessible
            )
            bar.context.accessibleRelationSet.add(controllerFor)
            accessibleRelationSet.add(controlledBy)
            return bar
        }

        private fun Point.toComposeOffset(): Offset {
            return Offset(x.toFloat() * density.density, y.toFloat() * density.density)
        }

        private fun Rect.toAwtRectangle() =
            Rectangle(
                (left / density.density).toInt(),
                (top / density.density).toInt(),
                (width / density.density).toInt(),
                (height / density.density).toInt()
            )

        private fun Offset.toAwtPoint() =
            Point(
                (x / density.density).toInt(),
                (y / density.density).toInt()
            )

        private fun IntSize.toAwtDimension() =
            Dimension(
                (width / density.density).toInt(),
                (height / density.density).toInt()
            )

        override fun getAccessibleName(): String? {
            return text?.toString()
        }

        override fun getAccessibleDescription(): String? {
            return semanticsNode.unmergedConfig.getFirst(SemanticsProperties.ContentDescription)
        }

        override fun getAccessibleParent(): Accessible? {
            return semanticsNode.parent?.id?.let { id ->
                controller?.let { it.currentNodes[id]!! }
            } ?: accessibleParent
        }

        override fun getAccessibleComponent(): AccessibleComponent? {
            return this
        }

        // we have to store a reference to AccessibleAction, because AWT itself uses weak
        // references and GC could delete an object which is, in fact, in use
        var _accessibleAction: AccessibleAction? = null

        override fun getAccessibleAction(): AccessibleAction? {
            val actions = mutableListOf<Pair<String?, ActionKey>>()

            fun addActionIfExist(key: SemanticsPropertyKey<AccessibilityAction<() -> Boolean>>) {
                semanticsNode.config.getOrNull(key)?.let {
                    actions.add(Pair(it.label, key))
                }
            }
            semanticsNode.config.getOrNull(SemanticsActions.OnClick)?.let {
                // AWT expects "click" label for click actions, at least on macOS...
                actions.add(Pair("click", SemanticsActions.OnClick))
            }

            addActionIfExist(SemanticsActions.OnLongClick)
            addActionIfExist(SemanticsActions.Expand)
            addActionIfExist(SemanticsActions.Collapse)
            addActionIfExist(SemanticsActions.Dismiss)

            if (actions.isEmpty()) {
                return null
            }
            _accessibleAction = object : AccessibleAction {
                override fun getAccessibleActionCount(): Int = actions.size

                override fun getAccessibleActionDescription(i: Int): String? {
                    val (label, _) = actions[i]
                    return label
                }

                override fun doAccessibleAction(i: Int): Boolean {
                    val (_, actionKey) = actions[i]
                    return semanticsNode.config.getOrNull(actionKey)?.let {
                        it.action?.invoke()
                    } ?: false
                }
            }
            return _accessibleAction
        }

        override fun getAccessibleValue(): AccessibleValue? {
            if (toggleableState != null) {
                return object : AccessibleValue {
                    override fun getCurrentAccessibleValue(): Number {
                        return when (toggleableState) {
                            ToggleableState.On -> 1
                            else -> 0
                        }
                    }

                    override fun setCurrentAccessibleValue(n: Number?): Boolean {
                        TODO("Not yet implemented")
                    }

                    override fun getMinimumAccessibleValue(): Number {
                        return 0
                    }

                    override fun getMaximumAccessibleValue(): Number {
                        return 1
                    }
                }
            }
            return null
        }

        override fun getAccessibleIndexInParent(): Int {
            val parentChildren = semanticsNode.parent?.replacedChildren
            return parentChildren?.indexOfFirst { it.id == semanticsNode.id } ?: -1
        }

        override fun getAccessibleChildrenCount(): Int {
            return semanticsNode.replacedChildren.size + auxiliaryChildren.size
        }

        override fun getAccessibleChild(i: Int): Accessible? {
            return semanticsNode.replacedChildren.getOrNull(i)?.id?.let { id ->
                controller?.let { it.currentNodes[id] }
            } ?: auxiliaryChildren[i - semanticsNode.replacedChildren.size]
        }

        override fun getLocale(): Locale = Locale.getDefault()

        override fun getLocationOnScreen(): Point {
            val rootLocation = controller?.desktopComponent?.locationOnScreen ?: Point(0, 0)
            val position = semanticsNode.positionInRoot
            return Point(
                (rootLocation.x + position.x / density.density).toInt(),
                (rootLocation.y + position.y.toInt() / density.density).toInt()
            )
        }

        override fun getLocation(): Point {
            return semanticsNode.positionInRoot.toAwtPoint()
        }

        override fun getBounds(): Rectangle {
            return semanticsNode.boundsInRoot.toAwtRectangle()
        }

        override fun getSize(): Dimension {
            return semanticsNode.size.toAwtDimension()
        }

        @OptIn(ExperimentalComposeUiApi::class)
        override fun isVisible(): Boolean =
            semanticsNode.config.getOrNull(SemanticsProperties.InvisibleToUser) == null

        override fun isEnabled(): Boolean =
            semanticsNode.config.getOrNull(SemanticsProperties.Disabled) == null

        // TODO check actual visibility
        override fun isShowing(): Boolean = true

        override fun contains(p: Point): Boolean {
            return bounds.contains(p)
        }

        override fun getAccessibleAt(p: Point): Accessible? {
            for (i in 0 until accessibleChildrenCount) {
                val child = (getAccessibleChild(i)?.accessibleContext as? AccessibleComponent)
                child?.getAccessibleAt(p)?.let {
                    return it
                }
            }
            if (contains(p)) {
                return this@ComposeAccessible
            }

            return null
        }

        override fun isFocusTraversable(): Boolean {
            return focused != null
        }

        override fun requestFocus() {
            if (focused == false) {
                semanticsNode.layoutNode.outerLayoutNodeWrapper.findLastFocusWrapper()
                    ?.requestFocus(propagateFocus = false)
            }
        }

        override fun addFocusListener(l: FocusListener?) {
            println("Not implemented: addFocusListener")
            TODO("Not yet implemented")
        }

        override fun removeFocusListener(l: FocusListener?) {
            println("Not implemented: removeFocusListener")
            TODO("Not yet implemented")
        }

        // -----------------------------------

        override fun getAccessibleRole(): AccessibleRole {
            controller?.itsActiveNow()
            when (semanticsNode.config.getOrNull(SemanticsProperties.Role)) {
                Role.Button -> return AccessibleRole.PUSH_BUTTON
                Role.Checkbox -> return AccessibleRole.CHECK_BOX
                Role.RadioButton -> return AccessibleRole.RADIO_BUTTON
                Role.Tab -> AccessibleRole.PAGE_TAB
                // ?
                //  Role.Switch ->
                Role.Image -> return AccessibleRoleExt.IMAGE
            }
            if (isPassword) {
                return AccessibleRole.PASSWORD_TEXT
            }
            if (scrollBy != null) {
                return AccessibleRole.SCROLL_PANE
            }
            if (setText != null) {
                return AccessibleRole.TEXT
            }
            if (text != null) {
                return AccessibleRole.LABEL
            }
            return AccessibleRole.PANEL
        }

        override fun getAccessibleStateSet(): AccessibleStateSet {
            return AccessibleStateSet().apply {
                // can we support these
                // AccessibleState.SINGLE_LINE
                // AccessibleState.MULTI_LINE

                if (isEnabled)
                    add(AccessibleState.ENABLED)
                if (isShowing)
                    add(AccessibleState.SHOWING)
                if (isVisible)
                    add(AccessibleState.VISIBLE)
                if (isFocusTraversable)
                    add(AccessibleState.FOCUSABLE)
                if (focused == true)
                    add(AccessibleState.FOCUSED)

                when (toggleableState) {
                    ToggleableState.On ->
                        add(AccessibleState.CHECKED)
                    ToggleableState.Indeterminate ->
                        add(AccessibleState.INDETERMINATE)
                    ToggleableState.Off, null -> {
                    }
                }

                val canExpand = semanticsNode.config.getOrNull(SemanticsActions.Expand) != null
                val canCollapse = semanticsNode.config.getOrNull(SemanticsActions.Collapse) != null

                if (canExpand || canCollapse)
                    add(AccessibleState.EXPANDABLE)

                if (canExpand)
                    add(AccessibleState.COLLAPSED)

                if (canCollapse)
                    add(AccessibleState.EXPANDED)

                if (canCollapse)
                    add(AccessibleState.EXPANDED)

                if (selected != null)
                    add(AccessibleState.SELECTABLE)

                if (selected == true)
                    add(AccessibleState.SELECTED)
            }
        }

        open inner class ComposeAccessibleText() : AccessibleText, AccessibleExtendedText {
            override fun getIndexAtPoint(p: Point): Int {
                return textLayoutResult!!.getOffsetForPosition(p.toComposeOffset())
            }

            override fun getCharacterBounds(i: Int): Rectangle {
                if (i < 0 || i >= text!!.length) {
                    return Rectangle(
                        (location.x / density.density).toInt(),
                        (location.y / density.density).toInt(),
                        0,
                        0
                    )
                }
                return textLayoutResult!!.getBoundingBox(i).toAwtRectangle()
            }

            override fun getCharCount(): Int {
                return text!!.length
            }

            override fun getCaretPosition(): Int {
                return textSelectionRange?.start ?: -1
            }

            private fun partToBreakIterator(part: Int): BreakIterator {
                val iter = when (part) {
                    SENTENCE -> BreakIterator.makeSentenceInstance()
                    WORD -> BreakIterator.makeWordInstance()
                    CHARACTER -> BreakIterator.makeCharacterInstance()
                    else -> throw IllegalArgumentException()
                }
                iter.setText(text!!.toString())
                return iter
            }

            override fun getAtIndex(part: Int, index: Int): String {
                return when (val end = partToBreakIterator(part).following(index)) {
                    BreakIterator.DONE -> ""
                    else -> text!!.subSequence(index, end).toString()
                }
            }

            override fun getAfterIndex(part: Int, index: Int): String {
                val iterator = partToBreakIterator(part)
                var start = index
                do {
                    start = iterator.following(start)
                    if (start == BreakIterator.DONE) return ""
                } while (text!![start] == ' ' || text!![start] == '\n')
                val end = when (val end = iterator.next()) {
                    BreakIterator.DONE -> iterator.last()
                    else -> end
                }
                return text!!.subSequence(start, end).toString()
            }

            override fun getBeforeIndex(part: Int, index: Int): String {
                return when (val start = partToBreakIterator(part).preceding(index)) {
                    BreakIterator.DONE -> ""
                    else -> text!!.subSequence(start, index).toString()
                }
            }

            override fun getCharacterAttribute(i: Int): AttributeSet {
                println("Not implemented: getCharacterAttribute")
                TODO("Not yet implemented")
            }

            override fun getSelectionStart(): Int {
                return textSelectionRange?.start ?: 0
            }

            override fun getSelectionEnd(): Int {
                return textSelectionRange?.end ?: 0
            }

            override fun getSelectedText(): String {
                return textSelectionRange?.let { selection ->
                    // could be end less than start here?
                    text!!.subSequence(selection.start, selection.end).toString()
                } ?: ""
            }

            override fun getTextRange(startIndex: Int, endIndex: Int): String {
                return text!!.subSequence(startIndex, endIndex).toString()
            }

            override fun getTextSequenceAt(part: Int, index: Int): AccessibleTextSequence {
                println("Not implemented: getBeforeIndex")
                TODO("Not yet implemented")
            }

            override fun getTextSequenceAfter(part: Int, index: Int): AccessibleTextSequence {
                println("Not implemented: getTextSequenceAfter")
                TODO("Not yet implemented")
            }

            override fun getTextSequenceBefore(part: Int, index: Int): AccessibleTextSequence {
                println("Not implemented: getTextSequenceBefore")
                TODO("Not yet implemented")
            }

            override fun getTextBounds(startIndex: Int, endIndex: Int): Rectangle {
                println("Not implemented: getTextBounds")
                TODO("Not yet implemented")
            }
        }

        inner class ScrollBarAccessible(
            val vertical: Boolean
        ) : Accessible {
            val context: AccessibleContext = object : AccessibleContext(), AccessibleValue {
                private val range = if (vertical) {
                    verticalScroll!!
                } else {
                    horizontalScroll!!
                }

                override fun getAccessibleValue(): AccessibleValue = this

                override fun getAccessibleRole(): AccessibleRole = AccessibleRole.SCROLL_BAR

                override fun getAccessibleStateSet(): AccessibleStateSet {
                    return AccessibleStateSet().apply {
                        add(AccessibleState.ENABLED)
                        if (vertical) {
                            add(AccessibleState.VERTICAL)
                        } else {
                            add(AccessibleState.HORIZONTAL)
                        }
                    }
                }

                override fun getAccessibleParent(): Accessible {
                    return this@ComposeAccessible
                }

                override fun getAccessibleIndexInParent(): Int {
                    return auxiliaryChildren.indexOf(this@ScrollBarAccessible)
                }

                override fun getAccessibleChildrenCount(): Int = 0

                override fun getAccessibleChild(i: Int): Accessible? = null
                override fun getLocale(): Locale {
                    return Locale.getDefault()
                }

                override fun getCurrentAccessibleValue(): Number = range.value()

                override fun setCurrentAccessibleValue(n: Number?): Boolean {
                    return if (vertical) {
                        scrollBy!!.action!!.invoke(0f, n!!.toFloat() - range.value())
                    } else {
                        scrollBy!!.action!!.invoke(n!!.toFloat() - range.value(), 0f)
                    }
                }

                override fun getMinimumAccessibleValue(): Number = 0

                override fun getMaximumAccessibleValue(): Number = range.maxValue()
            }

            override fun getAccessibleContext(): AccessibleContext = context
        }

        val accessibleText by lazy {
            when {
                setText != null -> {
                    ComposeAccessibleEditableText()
                }
                text != null -> {
                    ComposeAccessibleText()
                }
                else -> {
                    null
                }
            }
        }

        override fun getAccessibleText(): AccessibleText? {
            return accessibleText
        }

        inner class ComposeAccessibleEditableText :
            ComposeAccessibleText(), AccessibleEditableText {
            override fun setTextContents(s: String) {
                setText!!.action!!.invoke(AnnotatedString(s))
            }

            override fun insertTextAtIndex(index: Int, s: String) {
                val text = text!!
                setText!!.action!!.invoke(
                    buildAnnotatedString {
                        append(text.subSequence(0, index))
                        append(s)
                        append(text.subSequence(index, text.length - 1))
                    }
                )
            }

            override fun getTextRange(startIndex: Int, endIndex: Int): String {
                return text!!.substring(startIndex, endIndex)
            }

            override fun delete(startIndex: Int, endIndex: Int) {
                val text = text!!
                setText!!.action!!.invoke(
                    buildAnnotatedString {
                        append(text.subSequence(0, startIndex))
                        append(text.subSequence(endIndex, text.length - 1))
                    }
                )
            }

            override fun cut(startIndex: Int, endIndex: Int) {
                TODO("Not yet implemented")
            }

            override fun paste(startIndex: Int) {
                TODO("Not yet implemented")
            }

            override fun replaceText(startIndex: Int, endIndex: Int, s: String) {
                val text = text!!
                setText!!.action!!.invoke(
                    buildAnnotatedString {
                        append(text.subSequence(0, startIndex))
                        append(s)
                        append(text.subSequence(endIndex, text.length - 1))
                    }
                )
            }

            override fun selectText(startIndex: Int, endIndex: Int) {
                // I'm not sure about traversalMode = true here
                setSelection!!.action!!.invoke(startIndex, endIndex, false)
            }

            override fun setAttributes(startIndex: Int, endIndex: Int, `as`: AttributeSet?) {
                println("Not implemented: setAttributes")
                TODO("Not yet implemented")
            }
        }

        override fun getAccessibleEditableText(): AccessibleEditableText? {
            val accessibleText = accessibleText
            return if (accessibleText is AccessibleEditableText) {
                accessibleText
            } else {
                null
            }
        }

        // -----------------------------------

        override fun setBounds(r: Rectangle?) {
            println("Not implemented: setBounds")
            TODO("Not yet implemented")
        }

        override fun setSize(d: Dimension?) {
            println("Not implemented: setSize")
            TODO("Not yet implemented")
        }

        override fun setLocation(p: Point?) {
            println("Not implemented: setLocation")
            TODO("Not yet implemented")
        }

        override fun getBackground(): Color {
            println("Not implemented: getBackground")
            TODO("Not yet implemented")
        }

        override fun setBackground(c: Color?) {
            println("Not implemented: setBackground")
            TODO("Not yet implemented")
        }

        override fun getForeground(): Color {
            println("Not implemented: getForeground")
            TODO("Not yet implemented")
        }

        override fun setForeground(c: Color?) {
            println("Not implemented: setForeground")
            TODO("Not yet implemented")
        }

        override fun getCursor(): Cursor {
            println("Not implemented: getCursor")
            TODO("Not yet implemented")
        }

        override fun setCursor(cursor: Cursor?) {
            println("Not implemented: setCursor")
            TODO("Not yet implemented")
        }

        override fun getFont(): Font {
            println("Not implemented: getFont")
            TODO("Not yet implemented")
        }

        override fun setFont(f: Font?) {
            println("Not implemented: setFont")
            TODO("Not yet implemented")
        }

        override fun getFontMetrics(f: Font?): FontMetrics {
            println("Not implemented: getFontMetrics")
            TODO("Not yet implemented")
        }

        override fun setEnabled(b: Boolean) {
            println("Not implemented: setEnabled")
            TODO("Not yet implemented")
        }

        override fun setVisible(b: Boolean) {
            println("Not implemented: setVisible")
            TODO("Not yet implemented")
        }
    }
}

private typealias ActionKey = SemanticsPropertyKey<AccessibilityAction<() -> Boolean>>

internal class AccessibleRoleExt(key: String) : AccessibleRole(key) {
    companion object {
        val IMAGE = AccessibleRoleExt("image")
    }
}