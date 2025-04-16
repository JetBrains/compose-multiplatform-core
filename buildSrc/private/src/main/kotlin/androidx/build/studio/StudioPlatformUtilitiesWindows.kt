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

package androidx.build.studio

import java.io.File
import java.nio.file.Files
import java.util.zip.ZipFile
import org.gradle.api.GradleException
import org.gradle.process.ExecOperations

internal class WindowsUtilities(projectRoot: File, studioInstallationDir: File) :
    StudioPlatformUtilities(projectRoot, studioInstallationDir) {
    override val archiveExtension: String
        get() = ".zip"

    override val StudioTask.binaryDirectory: File
        get() = File(studioInstallationDir, "android-studio")

    override val StudioTask.launchCommandArguments: List<String>
        get() {
            val studioBinary = File(binaryDirectory, "bin/studio64.exe")
            return listOf(studioBinary.absolutePath, projectRoot.absolutePath)
        }

    override val StudioTask.pluginsDirectory: File
        get() = File(binaryDirectory, "plugins")

    override val StudioTask.libDirectory: File
        get() = File(binaryDirectory, "lib")

    override val StudioTask.licensePath: String
        get() = File(binaryDirectory, "LICENSE.txt").absolutePath

    override fun extractArchive(
        fromPath: String,
        toPath: String,
        @Suppress("UNUSED_PARAMETER") execOperations: ExecOperations,
    ) {
        extractZipArchive(fromPath, toPath)
    }

    override fun findProcess(): Int? {
        println("Detecting active managed Studio instances...")
        val escapedProjectRoot = projectRoot.absolutePath.replace("'", "''")
        val command =
            "${'$'}projectRoot = '$escapedProjectRoot'; " +
                "Get-CimInstance Win32_Process -Filter \"name = 'studio64.exe'\" " +
                "| Where-Object { \$_.CommandLine -and \$_.CommandLine.Contains(${ '$'}projectRoot) } " +
                "| Select-Object -First 1 -ExpandProperty ProcessId"
        val process =
            ProcessBuilder().let {
                it.command(listOf("powershell", "-NoProfile", "-Command", command))
                it.redirectError(ProcessBuilder.Redirect.INHERIT)
                it.start()
            }
        val stdout = process.inputStream.bufferedReader().use { it.readLines() }
        process.waitFor()
        return stdout.firstOrNull { it.isNotBlank() }?.trim()?.toIntOrNull()
    }
}

private fun extractZipArchive(fromPath: String, toPath: String) {
    val destinationDir = File(toPath).canonicalFile
    ZipFile(fromPath).use { zipFile ->
        val entries = zipFile.entries()
        while (entries.hasMoreElements()) {
            val entry = entries.nextElement()
            val outputFile = File(destinationDir, entry.name).canonicalFile
            if (!outputFile.path.startsWith(destinationDir.path + File.separator)) {
                throw GradleException("Refusing to extract outside destination: ${entry.name}")
            }

            if (entry.isDirectory) {
                outputFile.mkdirs()
                continue
            }

            outputFile.parentFile?.mkdirs()
            zipFile.getInputStream(entry).use { input ->
                Files.newOutputStream(outputFile.toPath()).use { output -> input.copyTo(output) }
            }
        }
    }
}
