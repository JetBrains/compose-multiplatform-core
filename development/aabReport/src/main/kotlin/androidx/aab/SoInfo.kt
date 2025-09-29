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

package androidx.aab

/**
 * Information captured from .so files.
 *
 * Currently, this is just used to measure native code size.
 */
data class SoInfo(val bundlePath: String, val abi: Abi?, val size: Long) {
    init {
        require(size >= 0)
        require(bundlePath.endsWith(".so"))
    }

    enum class Abi(val pattern: String) {
        Arm("/armeabi/"),
        ArmV7("/armeabi-v7a/"),
        Arm64("/arm64-v8a/"),
        X86("/x86/"),
        X86_64("/x86_64/"),
        Mips("/mips/"),
        Mips64("/mips64/");

        companion object {
            fun from(bundlePath: String): Abi? {
                return entries.firstOrNull { bundlePath.contains(it.pattern) }
            }
        }
    }

    constructor(bundlePath: String, size: Long) : this(bundlePath, Abi.from(bundlePath), size)

    companion object {
        val CSV_TITLES = listOf("so_totalSizeMb")

        fun List<SoInfo>.csvEntries(): List<String> {
            if (isEmpty()) {
                return listOf("0")
            }

            // create lst of sizes per ABI
            val abiSizes =
                Abi.entries.map { targetAbi ->
                    this.filter { it.abi == targetAbi }.sumOf { it.size }
                }

            // return max of these sizes
            return listOf((abiSizes.max() / (1024.0 * 1024)).toString())
        }
    }
}
