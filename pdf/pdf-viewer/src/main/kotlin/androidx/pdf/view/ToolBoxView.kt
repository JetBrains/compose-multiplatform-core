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

package androidx.pdf.view

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import androidx.annotation.RestrictTo
import androidx.pdf.PdfDocument
import androidx.pdf.R
import androidx.pdf.util.AnnotationUtils
import androidx.pdf.util.Intents.startActivity
import androidx.pdf.util.Uris
import com.google.android.material.floatingactionbutton.FloatingActionButton

@RestrictTo(RestrictTo.Scope.LIBRARY)
public open class ToolBoxView
@JvmOverloads
constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) :
    View(context, attrs, defStyleAttr) {

    private val editButton: FloatingActionButton
    private var pdfDocument: PdfDocument? = null
    private var editClickListener: OnClickListener? = null

    init {
        LayoutInflater.from(context).inflate(R.layout.toolbox_view, null, false)
        editButton = findViewById(R.id.edit_fab)

        editButton.setOnClickListener {
            handleEditFabClick()
            editClickListener?.onClick(this)
        }
    }

    public fun setPdfDocument(pdfDocument: PdfDocument?) {
        this.pdfDocument = pdfDocument
    }

    public fun setEditIconDrawable(drawable: Drawable?) {
        editButton.setImageDrawable(drawable)
    }

    public fun setOnEditClickListener(listener: OnClickListener) {
        editClickListener = listener
    }

    private fun handleEditFabClick() {

        pdfDocument?.let {
            val uri = it.uri

            val intent =
                AnnotationUtils.getAnnotationIntent(uri).apply {
                    setData(uri)
                    putExtra(EXTRA_PDF_FILE_NAME, Uris.extractName(uri, context.contentResolver))
                    putExtra(EXTRA_STARTING_PAGE, 0)
                }
            startActivity(context, "", intent)
        }
    }

    public companion object {
        public const val EXTRA_PDF_FILE_NAME: String =
            "androidx.pdf.viewer.fragment.extra.PDF_FILE_NAME"
        public const val EXTRA_STARTING_PAGE: String =
            "androidx.pdf.viewer.fragment.extra.STARTING_PAGE"
    }
}
