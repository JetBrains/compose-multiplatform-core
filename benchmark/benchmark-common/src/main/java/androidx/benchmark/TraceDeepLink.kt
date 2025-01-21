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

package androidx.benchmark

import android.util.Base64
import androidx.annotation.RestrictTo
import java.io.ByteArrayOutputStream
import java.net.URLEncoder
import java.util.zip.Deflater

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
data class TraceDeepLink(
    /** Output relative path of trace file */
    private val outputRelativePath: String,
    private val selectionParams: SelectionParams?,
) {
    fun createMarkdownLink(label: String, linkFormat: LinkFormat) =
        when (linkFormat) {
            LinkFormat.V2 -> {
                Markdown.createFileLink(label = label, path = outputRelativePath)
            }
            LinkFormat.V3 -> {
                Markdown.createLink(
                    label = label,
                    uri =
                        if (selectionParams != null) {
                            "uri://$outputRelativePath?enablePlugins=${selectionParams.pluginId}&selectionParams=${selectionParams.encodeParamString()}"
                        } else {
                            "uri://$outputRelativePath"
                        }
                )
            }
        }

    /**
     * Generic representation of a set of deep link parameters, specific to a certain
     * ui.perfetto.dev plugin
     */
    abstract class SelectionParams(val pluginId: String) {
        internal abstract fun buildInnerParamString(): String

        internal fun encodeParamString(): String {
            return base64Encode(deflate(buildInnerParamString().toByteArray()))
        }

        private fun base64Encode(data: ByteArray): String =
            Base64.encodeToString(data, Base64.URL_SAFE or Base64.NO_WRAP)

        private fun deflate(data: ByteArray): ByteArray {
            // Raw deflate is better than GZIPOutputStream as it omits headers (saving characters)
            // and allows for setting the compression level.
            val deflater = Deflater(Deflater.BEST_COMPRESSION) // TODO: compare with HUFFMAN_ONLY
            deflater.setInput(data)
            deflater.finish()
            val outputStream = ByteArrayOutputStream()
            val buffer = ByteArray(1024)
            while (!deflater.finished()) {
                val count = deflater.deflate(buffer)
                outputStream.write(buffer, 0, count)
            }
            return outputStream.toByteArray()
        }
    }

    /**
     * Parameters for startup deep link, given a specific time range, process, thread, and reasonId
     */
    data class StartupSelectionParams(
        val packageName: String,
        val tid: Int?,
        val selectionStart: Long?, // only needed for Studio
        val selectionEnd: Long?, // only needed for Studio
        val reasonId: String?
    ) : SelectionParams("android_startup") {
        override fun buildInnerParamString() = buildString {
            append("packageName=$packageName")
            if (tid != null) append("&tid=$tid")
            if (selectionStart != null) append("&selection_start=$selectionStart")
            if (selectionEnd != null) append("&selection_end=$selectionEnd")
            if (reasonId != null) {
                append("&reason_id=$reasonId")
            }
        }
    }

    /** General benchmark deep link, given a specific time range, process, thread, and query */
    data class BenchmarkSelectionParams(
        val packageName: String,
        val tid: Int?,
        val selectionStart: Long,
        val selectionEnd: Long,
        val query: String?
    ) : SelectionParams("androidx.benchmark") {
        override fun buildInnerParamString() = buildString {
            append("packageName=$packageName")
            if (tid != null) append("&tid=$tid")
            append("&selection_start=$selectionStart")
            append("&selection_end=$selectionEnd")
            if (query != null) {
                append("&query=")
                append(URLEncoder.encode(query, Charsets.UTF_8.name()))
            }
        }
    }
}
