/*
 * Copyright 2024 The Android Open Source Project
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

package androidx.build.binarycompatibilityvalidator

import androidx.binarycompatibilityvalidator.BinaryCompatibilityChecker
import androidx.binarycompatibilityvalidator.KlibDumpParser
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.jetbrains.kotlin.library.abi.ExperimentalLibraryAbiReader

@OptIn(ExperimentalLibraryAbiReader::class)
@CacheableTask
abstract class IgnoreAbiChangesTask : DefaultTask() {

    /** Text file from which API signatures will be read. */
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:InputFile
    abstract val previousApiDump: RegularFileProperty

    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:InputFile
    abstract val currentApiDump: RegularFileProperty

    @get:OutputFile abstract val ignoreFile: RegularFileProperty

    @TaskAction
    fun execute() {
        val previousDump = KlibDumpParser(previousApiDump.get().asFile).parse()
        val currentDump = KlibDumpParser(currentApiDump.get().asFile).parse()
        val ignoredErrors =
            BinaryCompatibilityChecker.checkAllBinariesAreCompatible(
                    currentDump,
                    previousDump,
                    null,
                    validate = false
                )
                .map { it.toString() }
                .toSet()
        ignoreFile.get().asFile.apply {
            if (!exists()) {
                createNewFile()
            }
            writeText(formatString + "\n" + ignoredErrors.joinToString("\n"))
        }
    }

    private companion object {
        const val BASELINE_FORMAT_VERSION = "1.0"
        const val formatString = "// Baseline format: $BASELINE_FORMAT_VERSION"
    }
}
