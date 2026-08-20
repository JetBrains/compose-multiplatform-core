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

package org.jetbrains.androidx.build.util

internal class ParsedBuildScript(val text: String) {
    private val nameToSourceSet: Map<String, SourceSet> by lazy {
        val sourceSetsBlock = Block(text).subblock("sourceSets {") ?: return@lazy emptyMap()
        val sourceSetsText = sourceSetsBlock.text
        MAIN_SOURCE_SET_REFERENCE.findAll(sourceSetsText).mapNotNull { match ->
            val sourceSetBlock =
                sourceSetsBlock.subblockAt(match.range.last) ?: return@mapNotNull null
            val dependenciesBlock = if (".dependencies" in match.value) {
                sourceSetBlock
            } else {
                sourceSetBlock.subblock("dependencies {") ?: return@mapNotNull null
            }
            match.groupValues[1] to SourceSet(
                name = match.groupValues[1],
                lineBefore = sourceSetsText
                    .substring(0, match.range.first)
                    .trimEnd()
                    .substringAfterLast('\n'),
                dependencies = dependenciesBlock,
            )
        }.toMap()
    }

    fun sourceSetOf(name: String): SourceSet? = nameToSourceSet[name]

    fun withSourceSets(update: (SourceSet) -> List<Line>): ParsedBuildScript {
        val text = StringBuilder(this@ParsedBuildScript.text)
        for (sourceSet in nameToSourceSet.values.reversed()) {
            val lines = update(sourceSet)
            text.replace(
                sourceSet.dependencies.interiorStart,
                sourceSet.dependencies.interiorEnd,
                sourceSet.textFor(lines),
            )
        }
        return ParsedBuildScript(text.toString())
    }

    internal class SourceSet(
        val name: String,
        internal val dependencies: Block,
        private val lineBefore: String,
    ) {
        val lines: List<Line> by lazy {
            buildList {
                var nesting = 0
                val comments = mutableListOf<String>()
                for (lineText in dependencies.textLines) {
                    if (nesting == 0) {
                        when {
                            lineText.isBlank() -> add(Line.Blank)
                            lineText.trimStart().startsWith("//") -> comments += lineText.trimStart()
                            else -> parseLine(lineText)?.let { line ->
                                add(line.copy(comments = comments.toList()))
                                comments.clear()
                            }
                        }
                    }
                    nesting += lineText.count { it == '{' } - lineText.count { it == '}' }
                }
            }
        }

        fun hasMarker(marker: String): Boolean = lineBefore.contains(marker)

        internal fun textFor(lines: List<Line>): String {
            val closingIndentation = dependencies.closingIndentation
            val declarationIndentation = "$closingIndentation    "
            return buildString {
                appendLine()
                lines.forEach { line ->
                    appendFormattedLine(line, declarationIndentation)
                }
                append(closingIndentation)
            }
        }

        private fun StringBuilder.appendFormattedLine(
            line: Line, indentation: String
        ) = when (line) {
            Line.Blank -> appendLine()
            is Line.Dependency -> with(line) {
                comments.forEach { comment ->
                    appendLine("$indentation$comment")
                }
                append("$indentation$type(${dependency.formatted})")
                if (inlineComment != null) {
                    append(" ").append(inlineComment)
                }
                appendLine()
            }
        }

        private fun parseLine(text: String): Line.Dependency? {
            val (type, argument, inlineComment) =
                DEPENDENCY_CALL.matchEntire(text)?.destructured
                    ?: return null
            val dependency = parseDependency(argument.trim()) ?: return null
            return Line.Dependency(
                type = type,
                dependency = dependency,
                inlineComment = inlineComment.takeIf { it.isNotEmpty() },
            )
        }

        private fun parseDependency(argument: String): Dependency? = when {
            argument.startsWith("project(") && argument.endsWith(")") ->
                Dependency.Project(
                    argument.removeSurrounding("project(", ")").trim().trim('"', '\'')
                )

            argument.startsWith("libs.") -> Dependency.LibsReference(argument)
            else -> argument.trim('"', '\'').split(":")
                .takeIf { it.size == 3 }
                ?.let { Dependency.Artifact(it[0], it[1], it[2]) }
        }
    }

    internal sealed interface Line {
        data object Blank : Line

        data class Dependency(
            val comments: List<String> = emptyList(),
            val type: String,
            val dependency: ParsedBuildScript.Dependency,
            val inlineComment: String? = null,
        ) : Line {
            fun hasMarker(marker: String) = comments.any { it.contains(marker) }
        }
    }

    internal sealed interface Dependency {
        val formatted: String

        data class Artifact(
            val group: String,
            val module: String,
            val version: String,
        ) : Dependency {
            override val formatted: String get() = "\"$group:$module:$version\""
        }

        data class Project(
            val path: String,
        ) : Dependency {
            override val formatted: String get() = "project(\"$path\")"
        }

        data class LibsReference(
            val notation: String,
        ) : Dependency {
            override val formatted: String get() = notation
        }
    }
}

private val MAIN_SOURCE_SET_REFERENCE =
    Regex("""(?:val\s+)?(\w+Main)(?:\.dependencies|\s+by\s+\w+)?\s*\{""")
private val DEPENDENCY_CALL = Regex("""\s*(\w+)\((.*)\)(?:\s*\{)?\s*(//.*)?""")

internal data class Block(
    private val source: String,
    /**
     * The offsets spanned by the lines of this block, which exclude the line holding its `{` and
     * the line holding its `}`.
     */
    val start: Int = 0,
    val end: Int = source.length,
    /** The offsets between the braces of this block, which a rewrite of its lines replaces. */
    val interiorStart: Int = start,
    val interiorEnd: Int = end,
    /** The indentation of the line that holds the closing brace of this block. */
    val closingIndentation: String = "",
) {
    val text: String get() = source.substring(start, end)

    /** The lines of this block, empty when it declares nothing. */
    val textLines: List<String> get() = if (start == end) emptyList() else text.split('\n')

    fun subblock(marker: String): Block? {
        val markerStart = source.indexOf(marker, start)
        if (markerStart < start || markerStart + marker.length > end) return null
        return subblockAt(markerStart + marker.length - 1 - start)
    }

    fun subblockAt(relativeOpeningBrace: Int): Block? {
        val openingBrace = start + relativeOpeningBrace
        val closingBrace = source.blockEnd(openingBrace) ?: return null
        if (closingBrace >= end) return null
        // A block written over several lines begins on the line after its `{` and ends at the end
        // of the line before its `}`. Those two lines hold the braces rather than a declaration,
        // so no line of the block is blank because of them.
        val firstLineStart = source.lineStartAfter(openingBrace + 1, closingBrace)
        return Block(
            source = source,
            start = firstLineStart,
            end = source.lineEndBefore(closingBrace, firstLineStart),
            interiorStart = openingBrace + 1,
            interiorEnd = closingBrace,
            closingIndentation = source.substring(
                source.lastIndexOf('\n', closingBrace - 1) + 1,
                closingBrace,
            ),
        )
    }
}

/**
 * The offset starting the line that follows [from], when everything between the two is blank.
 * Otherwise [from] itself, because a declaration already starts on its line.
 */
private fun String.lineStartAfter(from: Int, bound: Int): Int {
    val newline = indexOf('\n', from)
    if (newline < 0 || newline >= bound) return from
    return if (substring(from, newline).isBlank()) newline + 1 else from
}

/**
 * The offset ending the line that precedes [until], when everything between the two is blank.
 * Otherwise [until] itself, because a declaration still ends on its line. [bound] is returned when
 * everything up to [until] is blank, so that a block declaring nothing spans no lines at all.
 */
private fun String.lineEndBefore(until: Int, bound: Int): Int {
    val newline = lastIndexOf('\n', until - 1)
    if (newline < bound) return if (substring(bound, until).isBlank()) bound else until
    return if (substring(newline + 1, until).isBlank()) newline else until
}

private fun String.blockEnd(openingBrace: Int): Int? {
    var depth = 1
    for (index in openingBrace + 1 until length) {
        when (this[index]) {
            '{' -> depth++
            '}' -> depth--
        }
        if (depth == 0) return index
    }
    return null
}
