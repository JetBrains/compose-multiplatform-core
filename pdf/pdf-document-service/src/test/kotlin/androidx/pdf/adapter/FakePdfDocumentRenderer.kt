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

package androidx.pdf.adapter

import android.os.ParcelFileDescriptor

class FakePdfDocumentRenderer(
    override val isLinearized: Boolean,
    override val pageCount: Int,
    override val formType: Int,
    private val pdfPageProvider: (Int) -> PdfPage,
) : PdfDocumentRenderer {
    override fun openPage(pageNum: Int, useCache: Boolean): PdfPage =
        pdfPageProvider.invoke(pageNum)

    override fun releasePage(page: PdfPage?, pageNum: Int) {}

    override fun write(destination: ParcelFileDescriptor, removePasswordProtection: Boolean) {
        TODO("Not yet implemented")
    }

    override fun close() {
        TODO("Not yet implemented")
    }
}
