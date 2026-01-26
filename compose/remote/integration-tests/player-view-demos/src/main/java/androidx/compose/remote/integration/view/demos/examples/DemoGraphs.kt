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

package androidx.compose.remote.integration.view.demos.examples

import androidx.compose.remote.core.operations.Header
import androidx.compose.remote.core.operations.layout.managers.BoxLayout
import androidx.compose.remote.creation.RFloat
import androidx.compose.remote.creation.RemoteComposeContextAndroid
import androidx.compose.remote.creation.RemoteComposeWriter
import androidx.compose.remote.creation.abs
import androidx.compose.remote.creation.cos
import androidx.compose.remote.creation.min
import androidx.compose.remote.creation.modifiers.RecordingModifier
import androidx.compose.remote.creation.platform.AndroidxRcPlatformServices
import androidx.compose.remote.creation.plus
import androidx.compose.remote.creation.sin
import androidx.compose.remote.creation.times
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import kotlin.math.sin

@Suppress("RestrictedApiAndroidX")
fun demoGraphs(): RemoteComposeWriter {
    val rc =
        RemoteComposeContextAndroid(
            platform = AndroidxRcPlatformServices(),
            apiLevel = 6,
            RemoteComposeWriter.hTag(Header.DOC_WIDTH, 500),
            RemoteComposeWriter.hTag(Header.DOC_HEIGHT, 500),
            RemoteComposeWriter.hTag(Header.DOC_CONTENT_DESCRIPTION, "Simple Timer"),
            RemoteComposeWriter.hTag(Header.DOC_PROFILES, 0),
            RemoteComposeWriter.hTag(Header.DEBUG, 1),
        ) {
            root {
                box(RecordingModifier().fillMaxSize(), BoxLayout.START, BoxLayout.START) {
                    canvas(RecordingModifier().fillMaxSize().background(0xFF112244.toInt())) {
                        val w = ComponentWidth() // component.width()
                        val h = ComponentHeight()
                        val cx = w / 2f
                        val cy = h / 2f
                        val data: FloatArray = FloatArray(32) { x -> sin(x / 3.14f) + 0.5f }

                        val values = RFloat(writer, addFloatArray(data))
                        rcPlotXY(100f + 40f * cos(ContinuousSec() * 2f), 100, w, h, plot = values)
                    }
                }
            }
        }
    return rc.writer
}

@Suppress("RestrictedApiAndroidX")
fun demoGraphs2(): RemoteComposeWriter {
    val rc =
        RemoteComposeContextAndroid(
            platform = AndroidxRcPlatformServices(),
            apiLevel = 6,
            RemoteComposeWriter.hTag(Header.DOC_WIDTH, 500),
            RemoteComposeWriter.hTag(Header.DOC_HEIGHT, 500),
            RemoteComposeWriter.hTag(Header.DOC_CONTENT_DESCRIPTION, "Simple Timer"),
            RemoteComposeWriter.hTag(Header.DOC_PROFILES, 0),
            RemoteComposeWriter.hTag(Header.DEBUG, 1),
        ) {
            root {
                box(RecordingModifier().fillMaxSize(), BoxLayout.START, BoxLayout.START) {
                    canvas(RecordingModifier().fillMaxSize().background(0xFF112244.toInt())) {
                        val w = ComponentWidth() // component.width()
                        val h = ComponentHeight()
                        val cx = w / 2f
                        val cy = h / 2f
                        val scale = abs((sin(ContinuousSec()) + 1.5) * 10f).flush()
                        val equ = rFun { x -> min(scale, 15f) * sin(x + ContinuousSec() * 3f) }

                        val function = FunctionPlot(equ, rf(-10f), rf(10f), -1f * scale, scale)
                        rcPlotXY(100f + 40f * cos(ContinuousSec() * 2f), 100, w, h, plot = function)
                    }
                }
            }
        }
    return rc.writer
}

@Preview @Composable private fun DemoGraphsPreview() = RemoteDocPreview(demoGraphs())

@Preview @Composable private fun DemoGraphs2Preview() = RemoteDocPreview(demoGraphs2())
