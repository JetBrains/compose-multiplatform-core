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

import org.jetbrains.androidx.build.util.ScriptToken.Kind

internal class ParsedBuildScript(val text: String) {
    private val tokens: List<ScriptToken> by lazy { tokenizeScript(text) }

    private val nameToSourceSet: Map<String, SourceSet> by lazy {
        val sourceSets = blockAfter("sourceSets", tokens.indices) ?: return@lazy emptyMap()
        buildMap {
            for (index in sourceSets.contentIndices) {
                val token = tokens[index]
                if (token.kind != Kind.IDENTIFIER) continue
                if (!SOURCE_SET_NAME.matches(token.text)) continue
                val dependencies = dependenciesBlockAt(index, sourceSets.closingBrace) ?: continue
                put(
                    token.text,
                    SourceSet(
                        name = token.text,
                        dependencies = dependencies,
                        lineBefore = lineBefore(token.start),
                    ),
                )
            }
        }
    }

    fun sourceSetOf(name: String): SourceSet? = nameToSourceSet[name]

    fun withSourceSets(update: (SourceSet) -> List<Line>): ParsedBuildScript {
        val updated = StringBuilder(text)
        // Later blocks first, so that replacing one doesn't shift the offsets of the others.
        val sourceSets = nameToSourceSet.values.sortedByDescending { it.dependencies.textStart }
        for (sourceSet in sourceSets) {
            updated.replace(
                sourceSet.dependencies.textStart,
                sourceSet.dependencies.textEnd,
                sourceSet.textFor(update(sourceSet)),
            )
        }
        return ParsedBuildScript(updated.toString())
    }

    /**
     * Finds the block opened by `<name> {` within [indices], and returns it if it is closed within
     * [indices] as well.
     */
    private fun blockAfter(name: String, indices: IntRange): Block? {
        for (index in indices) {
            val token = tokens[index]
            if (token.kind == Kind.IDENTIFIER &&
                token.text == name &&
                tokens.getOrNull(index + 1)?.kind == Kind.OPENING_BRACE
            ) {
                return blockAt(index + 1, indices.last)
            }
        }
        return null
    }

    /** Matches the brace at [openingBrace] with its closing one, searching no further than [bound]. */
    private fun blockAt(openingBrace: Int, bound: Int): Block? {
        val nestedBraces = mutableListOf<Pair<Int, Int>>()
        var depth = 0
        for (index in openingBrace..bound) {
            val delta = when (tokens[index].kind) {
                Kind.OPENING_BRACE -> 1
                Kind.CLOSING_BRACE -> -1
                else -> continue
            }
            depth += delta
            if (depth == 0) {
                return Block(
                    openingBrace = openingBrace,
                    closingBrace = index,
                    textStart = tokens[openingBrace].end,
                    textEnd = tokens[index].start,
                    text = text.substring(tokens[openingBrace].end, tokens[index].start),
                    nestedBraces = nestedBraces,
                )
            }
            if (index != openingBrace) nestedBraces += tokens[index].start to delta
        }
        return null
    }

    /**
     * Finds the dependencies block declared for the source set named by the token at [nameIndex],
     * covering the three shapes the DSL allows.
     */
    private fun dependenciesBlockAt(nameIndex: Int, bound: Int): Block? {
        fun kindAt(offset: Int) = tokens.getOrNull(nameIndex + offset)?.kind
        fun textAt(offset: Int) = tokens.getOrNull(nameIndex + offset)?.text

        return when {
            // commonMain.dependencies { ... }
            kindAt(1) == Kind.DOT &&
                kindAt(2) == Kind.IDENTIFIER &&
                textAt(2) == DEPENDENCIES &&
                kindAt(3) == Kind.OPENING_BRACE ->
                blockAt(nameIndex + 3, bound)

            // commonMain { dependencies { ... } }
            kindAt(1) == Kind.OPENING_BRACE ->
                blockAt(nameIndex + 1, bound)?.let { blockAfter(DEPENDENCIES, it.contentIndices) }

            // val commonMain by getting { dependencies { ... } }
            kindAt(1) == Kind.IDENTIFIER &&
                kindAt(2) == Kind.IDENTIFIER &&
                kindAt(3) == Kind.OPENING_BRACE ->
                blockAt(nameIndex + 3, bound)?.let { blockAfter(DEPENDENCIES, it.contentIndices) }

            else -> null
        }
    }

    /** The last non-blank line preceding the line that [offset] is on. */
    private fun lineBefore(offset: Int): String {
        val lineStart = text.lastIndexOf('\n', offset - 1) + 1
        return text.substring(0, lineStart).trimEnd().substringAfterLast('\n')
    }

    /** A brace-delimited block, addressed both by token index and by text offset. */
    internal class Block(
        val openingBrace: Int,
        val closingBrace: Int,
        val textStart: Int,
        val textEnd: Int,
        val text: String,
        /** Offsets of the braces nested in this block, each paired with its change of depth. */
        val nestedBraces: List<Pair<Int, Int>>,
    ) {
        val contentIndices: IntRange get() = (openingBrace + 1) until closingBrace
    }

    internal class SourceSet(
        val name: String,
        internal val dependencies: Block,
        private val lineBefore: String,
    ) {
        val lines: List<Line> by lazy {
            buildList {
                val comments = mutableListOf<String>()
                var offset = dependencies.textStart
                var depth = 0
                var braceIndex = 0
                for (lineText in dependencies.text.lineSequence()) {
                    val lineEnd = offset + lineText.length
                    if (depth == 0) {
                        when {
                            lineText.isBlank() -> add(Line.Blank)
                            lineText.trimStart().startsWith("//") -> comments += lineText.trimStart()
                            else -> parseLine(lineText)?.let { line ->
                                add(line.copy(comments = comments.toList()))
                                comments.clear()
                            }
                        }
                    }
                    // Braces on this line take effect on the lines that follow it.
                    while (braceIndex < dependencies.nestedBraces.size &&
                        dependencies.nestedBraces[braceIndex].first < lineEnd
                    ) {
                        depth += dependencies.nestedBraces[braceIndex].second
                        braceIndex++
                    }
                    offset = lineEnd + 1
                }
            }
                // The first and the last line of a block hold its braces rather than a
                // declaration, so they are not blank lines of the dependency list.
                .dropWhile { it is Line.Blank }
                .dropLastWhile { it is Line.Blank }
        }

        fun hasMarker(marker: String): Boolean = lineBefore.contains(marker)

        internal fun textFor(lines: List<Line>): String {
            val closingIndentation = dependencies.text.substringAfterLast('\n')
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

private const val DEPENDENCIES = "dependencies"
private val SOURCE_SET_NAME = Regex("""\w+Main""")
private val DEPENDENCY_CALL = Regex("""\s*(\w+)\((.*)\)(?:\s*\{)?\s*(//.*)?""")
