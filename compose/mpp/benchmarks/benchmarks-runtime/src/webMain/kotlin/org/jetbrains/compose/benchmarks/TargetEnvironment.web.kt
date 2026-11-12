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


@file:OptIn(ExperimentalWasmJsInterop::class)

package org.jetbrains.compose.benchmarks

import kotlinx.serialization.json.Json
import org.jetbrains.compose.benchmarks.report.TargetEnvironment

internal actual fun collectTargetEnvironment(id: String): TargetEnvironment =
    Json.decodeFromString<TargetEnvironment>(browserEnvironment()).copy(id = id)

private fun browserEnvironment(): String = js("""
    (function() {
        const result = { id: '', runtime: 'browser', warnings: [] };
        function read(key, getter) {
            try {
                const value = getter();
                if (value !== undefined && value !== null && value !== '') result[key] = value;
            } catch (_) { result.warnings.push('Cannot read ' + key); }
        }
        read('userAgent', () => navigator.userAgent);
        read('browserPlatform', () => navigator.platform);
        read('availableProcessors', () => navigator.hardwareConcurrency > 0 ? navigator.hardwareConcurrency : null);
        read('deviceMemoryGiB', () => navigator.deviceMemory > 0 ? navigator.deviceMemory : null);
        read('screenWidth', () => screen.width);
        read('screenHeight', () => screen.height);
        read('devicePixelRatio', () => window.devicePixelRatio);
        read('gpuName', () => {
            const canvas = document.createElement('canvas');
            const gl = canvas.getContext('webgl');
            if (!gl) return null;
            try {
                const debug = gl.getExtension('WEBGL_debug_renderer_info');
                return gl.getParameter(debug ? debug.UNMASKED_RENDERER_WEBGL : gl.RENDERER);
            } finally {
                const lose = gl.getExtension('WEBGL_lose_context');
                if (lose) lose.loseContext();
            }
        });
        return JSON.stringify(result);
    })()
""")
