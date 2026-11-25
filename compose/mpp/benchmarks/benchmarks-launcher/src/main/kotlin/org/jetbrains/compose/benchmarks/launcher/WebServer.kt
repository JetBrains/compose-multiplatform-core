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

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Executors
import kotlin.io.path.extension

// server to serve static benchmark apps for web targets
internal class WebServer(directory: Path, port: Int, log: Path) : AutoCloseable {
    private val root = directory.toRealPath()
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", port), 0)
    private val executor = Executors.newCachedThreadPool { task ->
        Thread(task, "benchmark-static-server").apply { isDaemon = true }
    }
    val url: String get() = "http://127.0.0.1:${server.address.port}"

    init {
        server.executor = executor
        server.createContext("/") { exchange ->
            exchange.use {
                val method = exchange.requestMethod
                val requested = root.resolve(exchange.requestURI.path.removePrefix("/")).normalize()
                val file =
                    if (Files.isDirectory(requested)) requested.resolve("index.html") else requested
                val safeFile = runCatching { file.toRealPath() }.getOrNull()
                val status = when {
                    method !in listOf("GET", "HEAD") -> 405
                    safeFile == null || !safeFile.startsWith(root) || !Files.isRegularFile(safeFile) -> 404
                    else -> 200
                }
                appendLog(log, "$method ${exchange.requestURI} $status\n")
                if (status != 200) {
                    exchange.sendResponseHeaders(status, -1)
                } else {
                    val contentType = when (file.extension) {
                        "wasm" -> "application/wasm"
                        "js", "mjs" -> "text/javascript"
                        "html" -> "text/html; charset=utf-8"
                        "json", "map" -> "application/json"
                        else -> Files.probeContentType(file) ?: "application/octet-stream"
                    }
                    exchange.responseHeaders.set("Content-Type", contentType)
                    // A browser profile is scoped to one configuration with unchanged artifacts.
                    exchange.responseHeaders.set(
                        "Cache-Control",
                        if (file.extension == "html") "no-store" else "public, max-age=3600",
                    )
                    val length = Files.size(file)
                    exchange.responseHeaders.set("Content-Length", length.toString())
                    exchange.sendResponseHeaders(200, if (method == "HEAD") -1 else length)
                    if (method == "GET") Files.copy(file, exchange.responseBody)
                }
            }
        }
        server.start()
    }

    override fun close() {
        server.stop(0)
        executor.shutdownNow()
    }
}
