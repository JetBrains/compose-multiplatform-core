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

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.openqa.selenium.PageLoadStrategy
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.chrome.ChromeDriverService
import org.openqa.selenium.chrome.ChromeOptions
import org.openqa.selenium.firefox.FirefoxDriver
import org.openqa.selenium.firefox.FirefoxDriverService
import org.openqa.selenium.firefox.FirefoxOptions
import org.openqa.selenium.firefox.GeckoDriverService
import org.openqa.selenium.remote.RemoteWebDriver
import org.openqa.selenium.remote.http.ClientConfig
import org.openqa.selenium.remote.service.DriverService
import org.openqa.selenium.safari.SafariDriver
import org.openqa.selenium.safari.SafariDriverService
import org.openqa.selenium.safari.SafariOptions
import org.openqa.selenium.safari.SafariTechPreviewDriverService
import java.net.URLEncoder
import java.nio.file.Path
import java.time.Duration
import java.util.UUID
import kotlin.io.path.name

internal interface WebSession : AutoCloseable {
    val isAlive: Boolean
    fun navigate(url: String, log: Path)
}

/** One rendering session per configuration; startup always gets a fresh session. */
internal class WebApplications(
    private val createSession: (Path) -> WebSession,
) : AutoCloseable {
    private var session: WebSession? = null
    private var closed = false

    fun run(
        webUrl: String,
        parameters: List<String>,
        log: Path,
        waitForReport: (() -> Boolean) -> Unit,
    ) {
        val startup = parameters.first() == "startup"
        if (startup) reset()
        val current = synchronized(this) {
            check(!closed) { "launcher is stopping" }
            session ?: createSession(log).also { session = it }
        }
        try {
            current.navigate(benchmarkUrl(webUrl, parameters), log)
            waitForReport { current.isAlive }
            // Stop animations and other work while the launcher saves results or prepares another run.
            if (!startup) current.navigate("about:blank", log)
        } catch (error: Exception) {
            runCatching { reset() }.onFailure { error.addSuppressed(it) }
            throw error
        } finally {
            if (startup) reset()
        }
    }

    @Synchronized
    private fun reset() {
        // Keep the session owned until cleanup finishes, including during a concurrent shutdown.
        try {
            session?.close()
        } finally {
            session = null
        }
    }

    @Synchronized
    override fun close() {
        closed = true
        reset()
    }
}

internal fun benchmarkUrl(webUrl: String, parameters: List<String>): String {
    val query = listOf("type", "id", "benchmarkCollectorUrl", "test").zip(parameters)
        .joinToString("&") { (key, value) -> "$key=${URLEncoder.encode(value, Charsets.UTF_8)}" }
    return "$webUrl/?$query"
}

internal class SeleniumSession(
    private val browser: Path,
    private val kind: String,
    private val processes: Processes,
    private val log: Path,
    timeout: Double,
) : WebSession {
    private val chromeArguments = if (kind == "chromium") {
        System.getenv("BENCHMARK_CHROME_ARGUMENTS")?.let { value ->
            Json.parseToJsonElement(value).jsonArray.map { it.jsonPrimitive.content }
        }.orEmpty()
    } else emptyList()
    private val commandTimeout = Duration.ofMillis((timeout * 1000).toLong().coerceAtLeast(1))
    private val clientConfig = ClientConfig.defaultConfig()
        .connectionTimeout(commandTimeout)
        .readTimeout(commandTimeout)
    private val driverLog = log.resolveSibling("webdriver-${UUID.randomUUID()}.log")
    private val preview = kind == "safari" && browser.name == "Safari Technology Preview"
    private val service: DriverService = when (kind) {
        "chromium" -> ChromeDriverService.Builder()
            .withLogFile(driverLog.toFile()).withTimeout(commandTimeout).build()
        "firefox" -> GeckoDriverService.Builder()
            .withLogFile(driverLog.toFile()).withTimeout(commandTimeout).build()
        "safari" -> if (preview) {
            SafariTechPreviewDriverService.Builder().withTimeout(commandTimeout).build()
        } else {
            SafariDriverService.Builder().withTimeout(commandTimeout).build()
        }
        else -> error("unsupported browser family: $kind")
    }
    private var driver: RemoteWebDriver? = null
    private var ownsSafari = false
    private var closed = false

    override val isAlive: Boolean get() = service.isRunning

    @Synchronized
    override fun navigate(url: String, log: Path) {
        check(!closed) { "browser session is closed" }
        val current = driver ?: start().also {
            // Retain ownership before validation so failure cleanup also closes this browser.
            driver = it
            checkWebGL(it)
        }
        appendLog(log, "WebDriver session ${current.sessionId}: $url\n")
        current.get(url)
    }

    private fun checkWebGL(current: RemoteWebDriver) {
        val renderer = current.executeScript(
            """
            const canvas = document.createElement('canvas');
            const gl = canvas.getContext('webgl2');
            if (!gl) return null;
            const debug = gl.getExtension('WEBGL_debug_renderer_info');
            const renderer = gl.getParameter(debug ? debug.UNMASKED_RENDERER_WEBGL : gl.RENDERER);
            const loseContext = gl.getExtension('WEBGL_lose_context');
            if (loseContext) loseContext.loseContext();
            return renderer;
            """.trimIndent()
        )
        check(renderer != null) {
            "WebGL2 context is unavailable in $browser; see $driverLog. " +
                "On GPU-less Chromium CI, configure SwiftShader via BENCHMARK_CHROME_ARGUMENTS."
        }
        appendLog(log, "WebGL2 renderer: $renderer\n")
    }

    private fun start(): RemoteWebDriver {
        appendLog(log, "Starting WebDriver: $browser\n")
        if (kind == "chromium") appendLog(log, "Additional Chrome arguments: $chromeArguments\n")
        if (kind != "safari") appendLog(log, "Driver log: $driverLog\n")
        return when (kind) {
            "chromium" -> ChromeDriver(
                service as ChromeDriverService,
                ChromeOptions().apply {
                    setBinary(browser.toFile())
                    setPageLoadStrategy(PageLoadStrategy.NONE)
                    addArguments("--no-first-run", "--no-default-browser-check")
                    addArguments(chromeArguments)
                },
                clientConfig,
            )
            "firefox" -> FirefoxDriver(
                service as FirefoxDriverService,
                FirefoxOptions().apply {
                    setBinary(browser)
                    setPageLoadStrategy(PageLoadStrategy.NONE)
                    addPreference("browser.shell.checkDefaultBrowser", false)
                },
                clientConfig,
            )
            "safari" -> {
                requireSafariClosed(browser, processes)
                ownsSafari = true
                SafariDriver(
                    service,
                    SafariOptions().apply {
                        setUseTechnologyPreview(preview)
                        setPageLoadStrategy(PageLoadStrategy.NONE)
                    },
                    clientConfig,
                )
            }
            else -> error("unsupported browser family: $kind")
        }
    }

    @Synchronized
    override fun close() {
        if (closed) return
        closed = true
        val interrupted = Thread.interrupted()
        try {
            try {
                driver?.quit()
            } catch (error: Exception) {
                appendLog(log, "WebDriver quit failed: ${error.message}\n")
            } finally {
                try {
                    service.stop()
                } catch (error: Exception) {
                    appendLog(log, "WebDriver service cleanup failed: ${error.message}\n")
                } finally {
                    service.close()
                    // Safari can keep its application process alive after the automation session ends.
                    if (ownsSafari) quitSafari(browser, processes, log)
                }
            }
        } finally {
            if (interrupted) Thread.currentThread().interrupt()
        }
    }
}
