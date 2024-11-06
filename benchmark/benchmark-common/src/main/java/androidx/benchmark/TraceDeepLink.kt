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
class TraceDeepLink(
    /** Output relative path of trace file */
    private val outputRelativePath: String,
    private val selectionParams: SelectionParams?
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
                            "uri://$outputRelativePath?selectionParams=${selectionParams.encodeParamString()}"
                        } else {
                            "uri://$outputRelativePath"
                        }
                )
            }
        }

    class SelectionParams(
        val pid: Long,
        val tid: Long?,
        val ts: Long,
        val dur: Long,
        val query: String?
    ) {
        private fun buildParamString() = buildString {
            append("pid=${pid}")
            if (tid != null) append("&tid=${tid}")
            append("&ts=${ts}")
            append("&dur=${dur}")
            if (query != null) {
                append("&query=")
                append(URLEncoder.encode(query, Charsets.UTF_8.name()))
            }
        }

        internal fun encodeParamString(): String {
            return base64Encode(deflate(buildParamString().toByteArray()))
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
}
