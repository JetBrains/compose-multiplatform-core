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

import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import kotlin.io.path.isRegularFile
import kotlin.io.path.readLines

internal fun catalogVersion(root: Path, key: String): String {
    // These defaults are simple string entries in the catalog's [versions] table.
    val entry = Regex("""\s*${Regex.escape(key)}\s*=\s*["']([^"']+)["']\s*(?:#.*)?""")
    return root.resolve("gradle/libs.versions.toml").readLines()
        .dropWhile { it.trim() != "[versions]" }.drop(1)
        .takeWhile { !it.trim().startsWith("[") }
        .mapNotNull { entry.matchEntire(it)?.groupValues?.get(1) }.singleOrNull()
        ?: error("missing string version '$key' in gradle/libs.versions.toml")
}

internal fun validateVersion(value: String): String {
    require(value.matches(Regex("[A-Za-z0-9][A-Za-z0-9._+\\-]*"))) { "invalid version: $value" }
    return value
}

internal fun configuredCorePath(root: Path): Path? {
    val properties = Properties()
    val file = root.resolve("gradle.properties")
    if (file.isRegularFile()) Files.newBufferedReader(file).use { properties.load(it) }
    val value = properties.getProperty("composeCorePath")
        ?: System.getenv("ORG_GRADLE_PROJECT_composeCorePath") ?: return null
    if (value.isBlank()) return null
    return path(value).let { if (it.isAbsolute) it else root.resolve(it) }.normalize()
}

internal fun selectVersions(values: List<String>, default: String): List<String> =
    (if (values.isEmpty()) listOf(default) else values.flatMap { it.split(",") })
        .map { validateVersion(it) }.distinct()
