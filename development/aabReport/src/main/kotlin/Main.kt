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

package androidx.bundle

import kotlin.system.exitProcess

val VERBOSE = false

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        println("Expected one or more android app bundle files to be passed.")
        println("Usage:")
        println("     ./<path-to-script> <path-to-aab> [<path-to-aab2>...]")
        exitProcess(1)
    }

    args.forEach {
        val bundleInfo = BundleInfo.from(it)
        bundleInfo.reportProblems()
    }
}
