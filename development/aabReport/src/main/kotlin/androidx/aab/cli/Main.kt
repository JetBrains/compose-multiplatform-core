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

import androidx.aab.ApkInfo
import androidx.aab.BundleInfo
import androidx.aab.analysis.AnalyzedApkInfo
import androidx.aab.analysis.AnalyzedBundleInfo
import java.io.File
import kotlin.system.exitProcess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.runBlocking

var VERBOSE = false
const val CSV_PATH_PREFIX = "--csv="

// this is a simple way to abstract the difference between the two, but should probably define
// interfaces
internal abstract class PackageProcessor<T>(val typeLabel: String) {
    abstract fun getCsvHeader(): String

    abstract fun getCsvLine(item: T): String

    abstract fun transform(file: File): T

    abstract fun printAnalysis(item: T)

    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun process(files: List<File>, csvFile: File?) {
        println("Analyzing ${files.size} ${typeLabel}s...")
        val items =
            files
                .asFlow()
                .flatMapMerge { file ->
                    flow {
                            try {
                                emit(transform(file))
                            } catch (t: Throwable) {
                                println("ERROR during parsing ${file.absolutePath}, skipping")
                                t.printStackTrace()
                            }
                        }
                        .flowOn(Dispatchers.IO)
                }
                .toList()
        if (csvFile != null) {
            println("$typeLabel parsing complete, constructing CSV...")
            csvFile.writeText(getCsvHeader() + "\n")
            items.forEach { csvFile.appendText(getCsvLine(it) + "\n") }
            println("Analysis complete, CSV saved to ${csvFile.absolutePath}")
        } else {
            println("$typeLabel parsing complete, reporting problems...")
            items.forEach { printAnalysis(it) }
        }
    }
}

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

    val processor =
        if (files.any { it.name.endsWith(".apk") }) {
            object : PackageProcessor<AnalyzedApkInfo>("APK") {
                override fun getCsvHeader(): String = AnalyzedApkInfo.CSV_HEADER

                override fun getCsvLine(item: AnalyzedApkInfo): String = item.toCsvLine()

                override fun transform(file: File): AnalyzedApkInfo =
                    AnalyzedApkInfo(ApkInfo.from(file))

                override fun printAnalysis(item: AnalyzedApkInfo) = item.printAnalysis()
            }
        } else {
            object : PackageProcessor<AnalyzedBundleInfo>("Bundle") {
                override fun getCsvHeader(): String = AnalyzedBundleInfo.CSV_HEADER

                override fun getCsvLine(item: AnalyzedBundleInfo): String = item.toCsvLine()

                override fun transform(file: File): AnalyzedBundleInfo =
                    AnalyzedBundleInfo(BundleInfo.from(file))

                override fun printAnalysis(item: AnalyzedBundleInfo) = item.printAnalysis()
            }
        }

    processor.process(files, csvFile)
}
