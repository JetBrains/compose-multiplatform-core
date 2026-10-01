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

package androidx.compose.ui.tooling.recomposition

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.currentRecomposeScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private object Palette {
    val Background = Color(0xFF1E1F22)
    val Panel = Color(0xFF2B2D30)
    val Border = Color(0xFF393B40)
    val Text = Color(0xFFDFE1E5)
    val Dim = Color(0xFF9DA0A8)
    val Accent = Color(0xFF3574F0)
    val Selected = Color(0xFF2E436E)
    val Warning = Color(0xFFE08A1E)
    val Error = Color(0xFFE5484D)
    val Ok = Color(0xFF2E9E5B)
}

private val BodyStyle = TextStyle(color = Palette.Text, fontSize = 12.sp)
private val DimStyle = TextStyle(color = Palette.Dim, fontSize = 11.sp)
private val MonoStyle = TextStyle(color = Palette.Text, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
private val TitleStyle = TextStyle(color = Palette.Text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)

private enum class SortKey { Recent, Total }

private const val MAX_ROWS = 300

/** With "last 5s only" on, rows stay while the scope ran within this many nanoseconds. */
private const val KEEP_ROWS_NANOS = 5_000_000_000L

/**
 * Scopes with their counts, details of the selected one, and the current findings. Excludes its
 * own scopes from recording; built without lazy lists so every row is composed under its root
 * scope and inherits the exclusion.
 */
@Composable
fun RecompositionInspectorPanel(inspector: RecompositionInspector, modifier: Modifier = Modifier) {
    val root = currentRecomposeScope
    // Exclusion must be registered during this composition, before the children below run.
    remember(inspector) {
        inspector.excludeScope(root)
        true
    }
    val snapshot by inspector.snapshot.collectAsState()
    var selectedId by remember { mutableStateOf<Int?>(null) }
    var filter by remember { mutableStateOf("") }
    var sortKey by remember { mutableStateOf(SortKey.Recent) }
    var onlyRecent by remember { mutableStateOf(true) }

    Column(modifier.fillMaxSize().background(Palette.Background).padding(8.dp)) {
        Toolbar(inspector, snapshot)
        Spacer(Modifier.height(6.dp))
        FilterBar(filter, { filter = it }, sortKey, { sortKey = it }, onlyRecent, { onlyRecent = it })
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val f = filter.trim()
            val scopes = snapshot.scopes.asSequence()
                .filter { it.runCount > 0 }
                .filter { !onlyRecent || snapshot.timeNanos - it.lastRunNanos <= KEEP_ROWS_NANOS }
                .filter {
                    f.isEmpty() || it.name.contains(f, ignoreCase = true) ||
                        (it.location?.contains(f, ignoreCase = true) ?: false)
                }
                .sortedWith(
                    when (sortKey) {
                        SortKey.Recent -> compareByDescending<ScopeSnapshot> { it.recentRunCount }.thenByDescending { it.runCount }
                        SortKey.Total -> compareByDescending { it.runCount }
                    }
                )
                .take(MAX_ROWS)
                .toList()
            ScopeList(
                scopes,
                snapshot,
                selectedId,
                loopFrameThreshold = inspector.settings.loopFrameThreshold,
                onSelect = { selectedId = it },
                modifier = Modifier.weight(1.5f).fillMaxHeight(),
            )
            DetailsPane(snapshot, selectedId, Modifier.weight(1f).fillMaxHeight())
        }
    }
}

@Composable
private fun Toolbar(inspector: RecompositionInspector, snapshot: InspectorSnapshot) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        // Paused state comes from the snapshot, which pause()/resume() republish.
        val paused = !snapshot.isRecording
        ToolButton(if (paused) "Resume" else "Pause") { if (paused) inspector.resume() else inspector.pause() }
        ToolButton("Clear") { inspector.clear() }
        ToggleButton("Overlay", inspector.isOverlayVisible) { inspector.isOverlayVisible = it }
        Spacer(Modifier.weight(1f))
        val status = buildString {
            append("frame ").append(snapshot.frameCount)
            append(" · ").append(snapshot.scopes.size).append(" scopes")
            append(" · ").append(snapshot.findings.size).append(" findings")
            if (!snapshot.isRecording) append(" · paused")
            if (!snapshot.hasSourceInformation && snapshot.frameCount > 0) {
                append(" · no names: wrap the window content in RecompositionInspectable")
            }
        }
        BasicText(status, style = DimStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ToolButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier.background(Palette.Panel).border(1.dp, Palette.Border).clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) { BasicText(label, style = BodyStyle) }
}

@Composable
private fun ToggleButton(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Box(
        Modifier.background(if (checked) Palette.Selected else Palette.Panel)
            .border(1.dp, if (checked) Palette.Accent else Palette.Border)
            .clickable { onChange(!checked) }
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) { BasicText(label, style = BodyStyle) }
}

@Composable
private fun FilterBar(
    filter: String,
    onFilter: (String) -> Unit,
    sortKey: SortKey,
    onSort: (SortKey) -> Unit,
    onlyRecent: Boolean,
    onOnlyRecent: (Boolean) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.weight(1f).background(Palette.Panel).border(1.dp, Palette.Border).padding(horizontal = 6.dp, vertical = 4.dp)) {
            if (filter.isEmpty()) BasicText("filter by name or file…", style = DimStyle)
            BasicTextField(
                value = filter,
                onValueChange = onFilter,
                textStyle = BodyStyle,
                cursorBrush = SolidColor(Palette.Text),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        BasicText("sort:", style = DimStyle)
        SortKey.entries.forEach { key -> ToggleButton(key.name, sortKey == key) { onSort(key) } }
        ToggleButton("last 5s only", onlyRecent, onOnlyRecent)
    }
}

@Composable
private fun ScopeList(
    scopes: List<ScopeSnapshot>,
    snapshot: InspectorSnapshot,
    selectedId: Int?,
    loopFrameThreshold: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier,
) {
    Column(modifier.background(Palette.Panel).border(1.dp, Palette.Border)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp)) {
            BasicText("scope", style = TitleStyle, modifier = Modifier.weight(1f))
            BasicText("1s", style = TitleStyle, modifier = Modifier.width(44.dp))
            BasicText("total", style = TitleStyle, modifier = Modifier.width(56.dp))
            BasicText("parent", style = TitleStyle, modifier = Modifier.width(56.dp))
            BasicText("last µs", style = TitleStyle, modifier = Modifier.width(64.dp))
            BasicText("cause", style = TitleStyle, modifier = Modifier.width(110.dp))
        }
        if (scopes.isEmpty()) {
            BasicText("No recompositions recorded yet. Interact with the window.", style = DimStyle, modifier = Modifier.padding(6.dp))
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            scopes.forEach { scope ->
                val hot = scope.lastRunFrame >= snapshot.frameCount - 1
                val looping = scope.consecutiveFrames >= loopFrameThreshold
                Row(
                    Modifier.fillMaxWidth()
                        .background(if (scope.id == selectedId) Palette.Selected else Color.Transparent)
                        .clickable { onSelect(scope.id) }
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.width(6.dp).height(6.dp).background(
                            when {
                                looping -> Palette.Error
                                hot -> Palette.Ok
                                else -> Color.Transparent
                            }
                        )
                    )
                    Spacer(Modifier.width(4.dp))
                    Column(Modifier.weight(1f)) {
                        BasicText(scope.name, style = BodyStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        scope.location?.let { BasicText(it, style = DimStyle, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    }
                    BasicText(scope.recentRunCount.toString(), style = MonoStyle, modifier = Modifier.width(44.dp))
                    BasicText(scope.runCount.toString(), style = MonoStyle, modifier = Modifier.width(56.dp))
                    BasicText(scope.parentDrivenCount.toString(), style = MonoStyle, modifier = Modifier.width(56.dp))
                    BasicText((scope.lastDurationNanos / 1_000).toString(), style = MonoStyle, modifier = Modifier.width(64.dp))
                    BasicText(scope.lastRunCause.name, style = DimStyle, modifier = Modifier.width(110.dp))
                }
            }
        }
    }
}

@Composable
private fun DetailsPane(snapshot: InspectorSnapshot, selectedId: Int?, modifier: Modifier) {
    val scope = selectedId?.let { snapshot.scopesById[it] }
    Column(modifier.background(Palette.Panel).border(1.dp, Palette.Border).padding(8.dp).verticalScroll(rememberScrollState())) {
        BasicText("Findings (${snapshot.findings.size})", style = TitleStyle)
        if (snapshot.findings.isEmpty()) BasicText("nothing suspicious yet", style = DimStyle)
        snapshot.findings.take(20).forEach { finding ->
            val color = when (finding.kind.severity) {
                3 -> Palette.Error
                2 -> Palette.Warning
                else -> Palette.Dim
            }
            Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                BasicText(
                    "${finding.kind.name}: ${finding.scopeName}" + (finding.location?.let { " ($it)" } ?: ""),
                    style = BodyStyle.copy(color = color),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                BasicText(finding.message, style = DimStyle)
            }
        }
        Spacer(Modifier.height(10.dp))
        if (scope == null) {
            BasicText("Select a scope for details.", style = DimStyle)
            Spacer(Modifier.height(10.dp))
            Legend()
            return@Column
        }
        BasicText(scope.name, style = TitleStyle.copy(fontSize = 14.sp))
        scope.location?.let { BasicText(it, style = MonoStyle) }
        Spacer(Modifier.height(6.dp))
        val parent = scope.parentId?.let { snapshot.scopesById[it] }
        KeyValue("parent", parent?.let { "${it.name}${it.location?.let { l -> " ($l)" } ?: ""}" } ?: "—")
        KeyValue("runs", "${scope.runCount} total, ${scope.recentRunCount} in the last second, last in frame ${scope.lastRunFrame}")
        KeyValue("last cause", scope.lastRunCause.name)
        KeyValue("parent-driven runs", scope.parentDrivenCount.toString())
        KeyValue("explicit invalidate()", scope.explicitInvalidateCount.toString())
        KeyValue("invalidated during composition", scope.invalidatedDuringCompositionCount.toString())
        KeyValue("consecutive frames", scope.consecutiveFrames.toString())
        KeyValue("time", "last ${scope.lastDurationNanos / 1_000} µs, total ${scope.totalDurationNanos / 1_000} µs")
        KeyValue("bounds", scope.bounds?.let { "${it.left.toInt()},${it.top.toInt()} ${it.width.toInt()}×${it.height.toInt()} px" } ?: "no placed node")
        Spacer(Modifier.height(8.dp))
        BasicText("Invalidated by", style = TitleStyle)
        if (scope.invalidators.isEmpty()) BasicText("nothing recorded", style = DimStyle)
        scope.invalidators.forEach { inv ->
            Spacer(Modifier.height(4.dp))
            BasicText("×${inv.count}  ${inv.description}", style = MonoStyle)
            inv.rememberedIn?.let { BasicText("remembered in $it", style = DimStyle) }
        }
    }
}

@Composable
private fun KeyValue(key: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
        BasicText(key, style = DimStyle, modifier = Modifier.width(150.dp))
        BasicText(value, style = BodyStyle)
    }
}

@Composable
private fun Legend() {
    BasicText("Overlay colors", style = TitleStyle)
    Spacer(Modifier.height(4.dp))
    LegendRow(RecompositionOverlay.OwnStateColor, "own state changed")
    LegendRow(RecompositionOverlay.ParentDrivenColor, "parent recomposed, scope could not skip")
    LegendRow(RecompositionOverlay.ExplicitColor, "RecomposeScope.invalidate()")
    LegendRow(RecompositionOverlay.InitialColor, "first composition")
    LegendRow(RecompositionOverlay.LoopColor, "looping: recomposes every frame")
    Spacer(Modifier.height(10.dp))
    BasicText(
        "RecomposeLoop: ran in N consecutive frames and was invalidated from inside composition " +
            "(composition writes state it reads). RecomposesEveryFrame: ran every frame without an " +
            "in-composition write; animations do this legitimately, effects feeding back do not. " +
            "ParentDriven: mostly runs because the parent ran; look for unstable parameters.",
        style = DimStyle,
    )
}

@Composable
private fun LegendRow(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 1.dp)) {
        Box(Modifier.width(10.dp).height(10.dp).background(color))
        Spacer(Modifier.width(6.dp))
        BasicText(text, style = BodyStyle)
    }
}
