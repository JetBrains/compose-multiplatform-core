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

package org.jetbrains.compose.benchmarks.launcher

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.core.terminal
import com.github.ajalt.clikt.output.MordantHelpFormatter
import com.github.ajalt.mordant.terminal.Terminal
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.defaultLazy
import com.github.ajalt.clikt.parameters.options.option
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import org.jetbrains.compose.benchmarks.report.generateHtmlReport

private fun defaultReportOutput(): Path = Path.of(
    "report-${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss-SSS"))}.html"
)

internal class Report : CliktCommand(name = "benchmarks-launcher report") {
    init {
        context {
            readArgumentFile = null
            // Match the launcher's workaround for Mordant terminal detection on Windows.
            if (isWindows) terminal = Terminal(width = 79, height = 24)
            helpFormatter = {
                MordantHelpFormatter(it, showDefaultValues = true, showRequiredTag = true)
            }
        }
    }

    private val sessions by argument(
        "SESSION",
        help = "Paths to existing session directories, space separated; comparison order is preserved, with the first session as the baseline"
    ).multiple(required = true)
    private val output by option(
        "--output",
        help = "Output HTML file; relative paths are resolved from the working directory",
        metavar = "FILE"
    ).convert { Path.of(it) }.defaultLazy(defaultForHelp = "./report-<date-time>.html") { defaultReportOutput() }

    override fun help(context: Context) = """
        Generate an HTML report from existing benchmark sessions without building or running benchmarks.
        Supply one session to regenerate its report, or multiple sessions to compare their results.
        Sessions are compared in the supplied order; the first session is the baseline.
        The report is saved to a timestamped HTML file in the working directory unless --output is set.
    """.trimIndent()

    override fun helpEpilog(context: Context) = """
        Examples:

        benchmarks-launcher report /path/to/session

        benchmarks-launcher report /path/to/baseline /path/to/current --output comparison.html

        Use the timestamped session directories printed by the launcher, not individual target or reports directories.
        Session names in the report come from directory names. Quote paths that contain spaces.
    """.trimIndent()

    override fun run() {
        try {
            val html = generateHtmlReport(sessions.map { Path.of(it) }, output)
            echo("HTML report: $html")
        } catch (error: Exception) {
            echo("Cannot generate HTML report: ${error.message}", err = true)
            throw ProgramResult(1)
        }
    }
}
