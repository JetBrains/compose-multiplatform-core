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


package org.jetbrains.compose.benchmarks.data

import kotlinx.serialization.Serializable

@Serializable
data class TargetEnvironment(
    val id: String,
    val schemaVersion: Int = 1,
    val runtime: String,
    val osName: String? = null,
    val osVersion: String? = null,
    val architecture: String? = null,
    val cpuModel: String? = null,
    val availableProcessors: Int? = null,
    val physicalMemoryBytes: Long? = null,
    val deviceModel: String? = null,
    val simulator: Boolean? = null,
    val simulatorModel: String? = null,
    val gpuName: String? = null,
    val jvmName: String? = null,
    val jvmVersion: String? = null,
    val jvmVendor: String? = null,
    val javaVersion: String? = null,
    val jvmArguments: List<String>? = null,
    val maxHeapBytes: Long? = null,
    val userAgent: String? = null,
    val browserPlatform: String? = null,
    val deviceMemoryGiB: Double? = null,
    val screenWidth: Int? = null,
    val screenHeight: Int? = null,
    val devicePixelRatio: Double? = null,
    val warnings: List<String> = emptyList(),
)
