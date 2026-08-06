package androidx.compose.mpp.demo.components.text

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.skiko.hostOs

private val HighlightColor = Color(0xFFA5D6A7)
private val HighlightPadding = 2.dp
private val HighlightCornerRadius = 4.dp

@Composable
fun TextHighlightDemo() {
    var query by remember { mutableStateOf("") }
    var isSearchVisible by remember { mutableStateOf(false) }
    val contentFocusRequester = remember { FocusRequester() }

    fun closeSearch() {
        query = ""
        isSearchVisible = false
        contentFocusRequester.requestFocus()
    }

    LaunchedEffect(Unit) {
        contentFocusRequester.requestFocus()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(contentFocusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                when {
                    event.isSearchShortcut -> {
                        query = ""
                        isSearchVisible = true
                        true
                    }
                    event.isEscape && isSearchVisible -> {
                        closeSearch()
                        true
                    }
                    else -> false
                }
            }
    ) {
        HighlightedContent(query)

        if (isSearchVisible) {
            SearchBar(
                query = query,
                onQueryChange = { query = it },
                onClose = { closeSearch() },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

@Composable
private fun HighlightedContent(query: String) {
    var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    val shortcutHint = if (hostOs.isMacOS) "Cmd+F" else "Ctrl+F"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text("Text Highlight Demo", fontSize = 20.sp)
        Spacer(Modifier.height(4.dp))
        Text(
            "Press $shortcutHint to search. Press Esc to close search.",
            fontSize = 13.sp,
            color = Color.Gray
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = LoremIpsum,
            fontSize = 15.sp,
            onTextLayout = { layoutResult = it },
            modifier = Modifier.highlightMatchingText(query, layoutResult)
        )
        Spacer(Modifier.height(80.dp))
    }
}

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Surface(elevation = 8.dp, modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                label = { Text("Search text (Esc to close)") },
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester)
                    .onPreviewKeyEvent { event ->
                        if (event.isEscape) {
                            onClose()
                            true
                        } else {
                            false
                        }
                    }
            )
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Close search")
            }
        }
    }
}

private fun Modifier.highlightMatchingText(query: String, textLayoutResult: TextLayoutResult?) =
    this then HighlightMatchingTextElement(query, textLayoutResult)

private data class HighlightMatchingTextElement(
    val query: String,
    val textLayoutResult: TextLayoutResult?
) : ModifierNodeElement<HighlightMatchingTextNode>() {

    override fun create() = HighlightMatchingTextNode(query, textLayoutResult)

    override fun update(node: HighlightMatchingTextNode) {
        node.query = query
        node.textLayoutResult = textLayoutResult
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "highlightMatchingText"
        properties["query"] = query
        properties["textLayoutResult"] = textLayoutResult
    }
}

private class HighlightMatchingTextNode(
    var query: String,
    var textLayoutResult: TextLayoutResult?
) : Modifier.Node(), DrawModifierNode {

    override fun ContentDrawScope.draw() {
        val layout = textLayoutResult
        if (layout != null && query.isNotEmpty()) {
            val text = layout.layoutInput.text.text
            var index = text.indexOf(query, ignoreCase = true)
            while (index >= 0) {
                drawHighlight(layout, index, index + query.length)
                index = text.indexOf(query, index + query.length, ignoreCase = true)
            }
        }
        drawContent()
    }
}

private fun DrawScope.drawHighlight(layout: TextLayoutResult, start: Int, end: Int) {
    val padding = HighlightPadding.toPx()
    val cornerRadius = CornerRadius(HighlightCornerRadius.toPx())

    for (line in layout.getLineForOffset(start)..layout.getLineForOffset(end - 1)) {
        val lineStart = maxOf(start, layout.getLineStart(line))
        val lineEnd = minOf(end, layout.getLineEnd(line))
        if (lineStart >= lineEnd) continue

        val x1 = layout.getHorizontalPosition(lineStart, usePrimaryDirection = true)
        val x2 = layout.getHorizontalPosition(lineEnd, usePrimaryDirection = true)
        val left = minOf(x1, x2) - padding
        val top = layout.getLineTop(line) - padding

        drawRoundRect(
            color = HighlightColor,
            topLeft = Offset(left, top),
            size = Size(
                width = maxOf(x1, x2) + padding - left,
                height = layout.getLineBottom(line) + padding - top
            ),
            cornerRadius = cornerRadius
        )
    }
}

private val KeyEvent.isEscape: Boolean
    get() = type == KeyEventType.KeyDown && key == Key.Escape

private val KeyEvent.isSearchShortcut: Boolean
    get() = type == KeyEventType.KeyDown && key == Key.F &&
        if (hostOs.isMacOS) isMetaPressed else isCtrlPressed

private val LoremIpsum: AnnotatedString = buildAnnotatedString {
    append("Lorem ipsum dolor sit amet, consectetur adipiscing elit. ")
    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Color(0xFFD32F2F))) {
        append("Sed do eiusmod tempor incididunt ")
    }
    append(
        "ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud " +
            "exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat.\n\n"
    )

    withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = Color(0xFF1976D2))) {
        append("Duis aute irure dolor in reprehenderit ")
    }
    append("in voluptate velit esse cillum dolore eu fugiat nulla pariatur. ")
    append("Excepteur sint occaecat cupidatat non proident, ")
    append("sunt in culpa qui officia deserunt mollit anim id est laborum.\n\n")

    append("Curabitur pretium tincidunt lacus. Nulla gravida orci a odio. ")
    withStyle(
        SpanStyle(fontSize = 18.sp, color = Color(0xFF388E3C), fontWeight = FontWeight.Bold)
    ) {
        append("Nullam varius, turpis et commodo pharetra, ")
    }
    append(
        "est eros bibendum elit, nec luctus magna felis sollicitudin mauris. " +
            "Integer in mauris eu nibh euismod gravida. "
    )
    withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
        append("Duis ac tellus et risus vulputate vehicula. ")
    }
    appendLine(
        "Donec lobortis risus a elit. Etiam dui sem, fermentum vitae, sagittis id, " +
            "malesuada in, quam."
    )
    append(
        """
        Construct beef noodles artisanal-ware rebar Chiba tanto towards youtube boat shanty town systema shoes otaku Shibuya gang fetishism tattoo. Shanty town tube sprawl decay corrupted nodality physical uplink boy modem. Narrative boat gang smart-sub-orbital vinyl assassin dead bridge shrine rifle military-grade realism RAF disposable wonton soup sign construct papier-mache warehouse. Cyber-physical towards motion bicycle systema artisanal pistol A.I.. Shibuya soul-delay savant Chiba fluidity tower crypto-monofilament network skyscraper render-farm wonton soup tube shrine realism urban.

        Bicycle motion girl weathered drone modem lights systema-ware post-chrome Chiba physical disposable. Gang jeans face forwards hotdog tank-traps voodoo god convenience store. Pre-render-farm pistol rain drugs dissident cardboard sensory dolphin nodal point gang tank-traps tattoo denim Kowloon market sign stimulate. Skyscraper computer network rain Legba. Tanto Kowloon j-pop knife network sub-orbital media tank-traps papier-mache film tiger-team convenience store silent.

        Network military-grade artisanal range-rover industrial grade bridge jeans. Legba meta-A.I. neural rebar marketing vehicle. Stimulate engine tube film render-farm plastic lights faded. Smart-bridge boat cartel spook soul-delay disposable silent. Cartel Shibuya camera assassin sub-orbital alcohol San Francisco vehicle free-market DIY garage range-rover 3D-printed shrine shoes.
        """.trimIndent()
    )
}
