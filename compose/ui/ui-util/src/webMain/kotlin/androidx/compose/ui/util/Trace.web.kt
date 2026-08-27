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

@file:OptIn(ExperimentalWasmJsInterop::class)

package androidx.compose.ui.util

import kotlin.js.ExperimentalWasmJsInterop

/**
 * Emits Compose sections to the browser User Timing track when Web tracing is enabled.
 *
 * Tracing is opt-in to keep the disabled path cheap. Enable it with `?composeTrace=true` or by
 * setting `globalThis.__composeWebTraceEnabled = true` before Compose starts. A summary is printed
 * with `console.table` approximately every five seconds while events are being recorded. Set
 * `composeTraceConsole=false` to disable the summary.
 */
actual inline fun <T> trace(sectionName: String, block: () -> T): T {
    if (!isWebTraceEnabled()) return block()
    val startTime = webTraceStart()
    try {
        return block()
    } finally {
        // Tracing must never change application behavior or replace an exception from [block].
        webTraceEnd(sectionName, startTime)
    }
}

actual fun traceValue(tag: String, value: Long) {
    if (isWebTraceEnabled()) {
        webTraceValue(tag, value)
    }
}

@PublishedApi
internal fun isWebTraceEnabled(): Boolean = js(
    """
    (function() {
        var root = globalThis;
        if (root.__composeWebTraceState === undefined) {
            var queryEnabled = false;
            var consoleEnabled = true;
            try {
                var params = new URLSearchParams(root.location ? root.location.search : "");
                queryEnabled = params.get("composeTrace") === "true";
                consoleEnabled = params.get("composeTraceConsole") !== "false";
            } catch (ignored) {}
            var state = root.__composeWebTraceState = {
                enabled: root.__composeWebTraceEnabled === true || queryEnabled,
                consoleEnabled: consoleEnabled,
                lastReport: performance.now(),
                stats: new Map()
            };
            root.__composeWebTraceReport = function() {
                var rows = [];
                state.stats.forEach(function(stat, name) {
                    if (stat.samples.length === 0) return;
                    var sorted = stat.samples.slice().sort(function(a, b) { return a - b; });
                    var percentile = function(p) {
                        return sorted[Math.max(0, Math.ceil(sorted.length * p) - 1)];
                    };
                    var total = sorted.reduce(function(sum, sample) { return sum + sample; }, 0);
                    rows.push({
                        phase: name,
                        count: sorted.length,
                        average_ms: +(total / sorted.length).toFixed(3),
                        p50_ms: +percentile(0.50).toFixed(3),
                        p95_ms: +percentile(0.95).toFixed(3),
                        max_ms: +sorted[sorted.length - 1].toFixed(3)
                    });
                });
                rows.sort(function(a, b) { return b.average_ms - a.average_ms; });
                if (rows.length !== 0) console.table(rows);
                state.stats.clear();
                state.lastReport = performance.now();
            };
        }
        return root.__composeWebTraceState.enabled;
    })()
    """
)

@PublishedApi
internal fun webTraceStart(): Double = js("performance.now()")

@PublishedApi
internal fun webTraceEnd(sectionName: String, startTime: Double): Unit = js(
    """
    (function() {
        try {
            var endTime = performance.now();
            var duration = endTime - startTime;
            // Keep only the latest stored entry per phase. DevTools records each call while a
            // Performance panel recording is active.
            performance.clearMeasures(sectionName);
            performance.measure(sectionName, { start: startTime, duration: duration });

            var state = globalThis.__composeWebTraceState;
            var stat = state.stats.get(sectionName);
            if (stat === undefined) {
                stat = { samples: [] };
                state.stats.set(sectionName, stat);
            }
            // Console percentiles describe a bounded population of the latest 600 samples.
            if (stat.samples.length === 600) stat.samples.shift();
            stat.samples.push(duration);

            if (state.consoleEnabled && endTime - state.lastReport >= 5000 && !state.reportScheduled) {
                state.reportScheduled = true;
                setTimeout(function() {
                    state.reportScheduled = false;
                    try { globalThis.__composeWebTraceReport(); } catch (ignored) {}
                }, 0);
            }
        } catch (ignored) {
            // User Timing is diagnostic only and must never affect the traced operation.
        }
    })()
    """
)

private fun webTraceValue(tag: String, value: Long): Unit = js(
    """
    (function() {
        try {
            performance.clearMarks(tag);
            performance.mark(tag, { detail: Number(value) });
        } catch (ignored) {
            // User Timing is diagnostic only.
        }
    })()
    """
)
