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


package org.jetbrains.compose.benchmarks

import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import org.jetbrains.compose.benchmarks.data.TargetEnvironment

internal expect fun collectTargetEnvironment(id: String): TargetEnvironment

suspend fun reportTargetEnvironment(collectorUrl: String, id: String) {
    val environment = collectTargetEnvironment(id)
    val client = createHttpClient()
    try {
        val response = client.post(collectorUrl) {
            contentType(ContentType.Application.Json)
            setBody(environment)
        }
        check(response.status.isSuccess()) { "Environment collector returned ${response.status}" }
    } finally {
        client.close()
    }
}

internal class EnvironmentProbe {
    val warnings = mutableListOf<String>()

    fun <T> read(field: String, block: () -> T): T? = try {
        block()
    } catch (_: Exception) {
        warnings.add("Cannot read $field")
        null
    }
}
