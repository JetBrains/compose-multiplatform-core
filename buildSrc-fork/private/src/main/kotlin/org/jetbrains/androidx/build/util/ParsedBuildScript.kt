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
                sourceSet.dependencies.start,
                sourceSet.dependencies.end,
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
                for (lineText in dependencies.lines) {
                    if (nesting == 0) {
                        when {
                            lineText.isBlank() -> add(Line.Blank)
                            lineText.trimStart().startsWith("//") -> comments += lineText.trimStart()
                            // By design, only simple dependency declarations are managed;
                            // other lines are not supported at the moment.
                            else -> parseLine(lineText)?.let { line ->
                                add(line.copy(comments = comments.toList()))
                                comments.clear()
                            }
                        }
                    }
                    val nestingChange =
                        lineText.count { it == '{' } - lineText.count { it == '}' }
                    if (nesting == 0 && nestingChange > 0) {
                        println(
                            "Warning: jbVerifyForkDependencies ignores unsupported nested blocks: " +
                                lineText
                        )
                    }
                    nesting += nestingChange
                }
            }
        }

        fun hasMarker(marker: String): Boolean = lineBefore.contains(marker)

        internal fun textFor(lines: List<Line>): String =
            lines.joinToString("") { formattedLine(it, dependencies.declarationIndentation) }

        private fun formattedLine(line: Line, indentation: String): String = when (line) {
            Line.Blank -> "\n"
            is Line.Dependency -> buildString {
                line.comments.forEach { comment ->
                    appendLine("$indentation$comment")
                }
                append("$indentation${line.type}(${line.dependency.formatted})")
                if (line.inlineComment != null) {
                    append(" ").append(line.inlineComment)
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
    val start: Int = 0,
    val end: Int = source.length,
    val declarationIndentation: String = "",
) {
    val text: String get() = source.substring(start, end)

    val lines: List<String> get() =
        // if text doesn't contain \n, it means it is an empty block (with zero lines)
        // lineSequence won't help because it always returns one line
        text.split('\n').dropLast(1)

    fun subblock(marker: String): Block? {
        val markerStart = source.indexOf(marker, start)
        if (markerStart < start || markerStart + marker.length > end) return null
        return subblockAt(markerStart + marker.length - 1 - start)
    }

    fun subblockAt(relativeOpeningBrace: Int): Block? {
        val openingBrace = start + relativeOpeningBrace
        val closingBrace = source.blockEnd(openingBrace) ?: return null
        if (closingBrace >= end) return null
        val interiorStart = openingBrace + 1
        val interior = source.substring(interiorStart, closingBrace)
        val openingBraceLineEnd = interiorStart + interior.indexOf('\n')
        val lastLineEnd = interiorStart + interior.lastIndexOf('\n')
        return Block(
            source = source,
            start = openingBraceLineEnd + 1,
            end = lastLineEnd.coerceAtLeast(openingBraceLineEnd) + 1,
            declarationIndentation = source.substring(lastLineEnd + 1, closingBrace) + "    ",
        )
    }
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
