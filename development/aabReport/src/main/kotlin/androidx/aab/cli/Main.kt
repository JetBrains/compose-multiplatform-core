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

package androidx.aab.cli

import androidx.aab.BundleInfo
import androidx.aab.analysis.AnalyzedBundleInfo
import java.io.File
import kotlin.system.exitProcess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.runBlocking

var VERBOSE = false
const val CSV_PATH_PREFIX = "--csv="

@OptIn(ExperimentalCoroutinesApi::class)
fun main(args: Array<String>) = runBlocking {
    val csvPath =
        args
            .singleOrNull { it.startsWith(CSV_PATH_PREFIX) }
            ?.substringAfter(CSV_PATH_PREFIX)
            ?.replaceFirst("~", System.getProperty("user.home"))
    VERBOSE = args.contains("--verbose") || args.contains("-v")

    val csvFile = if (csvPath != null) File(csvPath) else null

    val pathArgs = args.filter { !it.startsWith("--") }

    if (pathArgs.isEmpty()) {
        println(
            """
            Expected one or more android app bundle files, or directories of bundles to be passed.
            Report Usage:
                 java -jar <path-to-jar> [--verbose] <path-to-aab> [<path-to-aab2>...]
            CSV Usage:
                 java -jar <path-to-jar> [--verbose] --csv=<output.csv> <path-to-aab> [<path-to-aab2>...]
            """
                .trimIndent()
        )
        exitProcess(1)
    }

    val files =
        pathArgs
            .flatMap { path ->
                val f = File(path)
                if (f.isDirectory) {
                    (f.listFiles()?.toList()) ?: emptyList()
                } else {
                    listOf(f)
                }
            }
            .filter { it.name != ".DS_Store" }

    println("Analyzing ${files.size} bundles...")
    val analyzedBundleInfoList =
        files
            .asFlow()
            .flatMapMerge { bundleFile ->
                flow {
                        try {
                            emit(AnalyzedBundleInfo(BundleInfo.Companion.from(bundleFile)))
                        } catch (t: Throwable) {
                            println(
                                "ERROR during parsing ${bundleFile.absolutePath}, skipping bundle"
                            )
                            t.printStackTrace()
                        }
                    }
                    .flowOn(Dispatchers.IO)
            }
            .toList()

    if (csvFile != null) {
        println("Bundle parsing complete, constructing CSV...")
        csvFile.writeText(AnalyzedBundleInfo.CSV_HEADER + "\n")
        analyzedBundleInfoList.forEach { csvFile.appendText(it.toCsvLine() + "\n") }
        println("Analysis complete, CSV saved to ${csvFile.absolutePath}")
    } else {
        println("Bundle parsing complete, reporting problems...")
        analyzedBundleInfoList.forEach { it.printAnalysis() }
    }
}
