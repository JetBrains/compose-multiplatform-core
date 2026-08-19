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


internal class LazyParsedBuildScript(val text: String) {
    private val nameToSourceSet: Map<String, SourceSet> by lazy {
        val sourceSetsBlock =
            Block(text, 0, text.length).subblock("sourceSets {") ?: return@lazy emptyMap()
        MAIN_SOURCE_SET_REFERENCE.findAll(sourceSetsBlock.text).mapNotNull { match ->
            val dependencyBlock =
                sourceSetsBlock.dependencyBlock(match.value) ?: return@mapNotNull null
            val name = match.groupValues[1]
            name to SourceSet(
                name = name,
                lineBefore = sourceSetsBlock.text.substring(0, match.range.first)
                    .trimEnd()
                    .substringAfterLast('\n'),
                source = dependencyBlock.source,
                dependenciesStart = dependencyBlock.start,
                dependenciesEnd = dependencyBlock.end,
            )
        }.toMap()
    }

    fun sourceSetOf(name: String): SourceSet? = nameToSourceSet[name]

    fun withSourceSets(update: (SourceSet) -> List<Line>): LazyParsedBuildScript {
        val text = StringBuilder(text)
        for (sourceSet in nameToSourceSet.values.reversed()) {
            val lines = update(sourceSet)
            if (lines != sourceSet.lines) {
                text.replace(
                    sourceSet.dependenciesStart,
                    sourceSet.dependenciesEnd,
                    sourceSet.textFor(lines),
                )
            }
        }
        return LazyParsedBuildScript(text.toString())
    }

    internal class SourceSet(
        val name: String,
        private val source: String,
        internal val dependenciesStart: Int,
        internal val dependenciesEnd: Int,
        private val lineBefore: String,
    ) {
        private val dependenciesText: String
            get() = source.substring(
                dependenciesStart,
                dependenciesEnd
            )

        val lines: List<Line> by lazy {
            buildList {
                var nesting = 0
                var comment: String? = null
                for (lineText in dependenciesText.lineSequence()) {
                    if (nesting == 0) {
                        when {
                            lineText.isBlank() -> add(Line.Blank)
                            lineText.trimStart().startsWith("//") -> comment = lineText.trimStart()
                            else -> parseLine(lineText)?.let { line ->
                                add(line.copy(comment = comment))
                                comment = null
                            }
                        }
                    }
                    nesting += lineText.count { it == '{' } - lineText.count { it == '}' }
                }
            }
        }

        fun hasMarker(marker: String): Boolean = lineBefore.contains(marker)

        internal fun textFor(lines: List<Line>): String {
            val closingIndentation = source
                .substring(dependenciesStart, dependenciesEnd)
                .substringAfterLast('\n')
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
            is Line.Dependency -> {
                line.comment?.let {
                    append(indentation)
                    appendLine(it)
                }
                append(indentation)
                append(line.type)
                append("(")
                append(line.dependency.formatted)
                append(")")
                line.inlineComment?.let { append(" ").append(it) }
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
            val comment: String? = null,
            val type: String,
            val dependency: LazyParsedBuildScript.Dependency,
            val inlineComment: String? = null,
        ) : Line {
            fun hasMarker(marker: String) = comment?.contains(marker) == true
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

private fun Block.dependencyBlock(sourceSetReference: String): Block? {
    val sourceSetBlock = subblock(sourceSetReference) ?: return null
    return if (".dependencies" in sourceSetReference) {
        sourceSetBlock
    } else {
        sourceSetBlock.subblock("dependencies {")
    }
}

private data class Block(
    val source: String,
    val start: Int,
    val end: Int,
) {
    val text: String get() = source.substring(start, end)

    fun subblock(textToFind: String): Block? {
        val markerStart = text.indexOf(textToFind)
        if (markerStart < 0) return null
        val contentStart = start + markerStart + textToFind.length
        val contentEnd = source.blockEnd(contentStart - 1) ?: return null
        return Block(source, contentStart, contentEnd)
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
