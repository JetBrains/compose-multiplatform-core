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

package androidx.pdf.testapp.ui.v2

import android.app.AlertDialog
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.annotation.RequiresExtension
import androidx.core.os.OperationCanceledException
import androidx.pdf.content.ExternalLink
import androidx.pdf.testapp.R
import androidx.pdf.testapp.ui.OpCancellationHandler
import androidx.pdf.testapp.util.BehaviorFlags
import androidx.pdf.viewer.fragment.PdfStylingOptions
import androidx.pdf.viewer.fragment.PdfViewerFragment

@Suppress("RestrictedApiAndroidX")
@RequiresExtension(extension = Build.VERSION_CODES.S, version = 13)
class StyledPdfViewerFragment : PdfViewerFragment {

    private lateinit var behaviorFlags: BehaviorFlags

    constructor() : super()

    private constructor(pdfStylingOptions: PdfStylingOptions) : super(pdfStylingOptions)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        behaviorFlags = BehaviorFlags.fromBundle(arguments)
    }

    override fun onLoadDocumentError(error: Throwable) {
        super.onLoadDocumentError(error)
        when (error) {
            is OperationCanceledException ->
                (activity as? OpCancellationHandler)?.handleCancelOperation()
        }
    }

    override fun onLinkClicked(externalLink: ExternalLink): Boolean {
        return if (behaviorFlags.isCustomLinkHandlingEnabled()) {
            AlertDialog.Builder(requireContext())
                .setTitle("Custom Link Handler")
                .setMessage("Intercepted link:\n${externalLink.uri}")
                .setPositiveButton("OK", null)
                .show()
            true
        } else {
            false
        }
    }

    companion object {
        fun newInstance(): StyledPdfViewerFragment {
            val stylingOptions = PdfStylingOptions(R.style.PdfViewCustomization)

            return StyledPdfViewerFragment(stylingOptions)
        }

        fun newInstance(flags: BehaviorFlags): StyledPdfViewerFragment {
            val stylingOptions = PdfStylingOptions(R.style.PdfViewCustomization)
            return StyledPdfViewerFragment(stylingOptions).apply { arguments = flags.toBundle() }
        }
    }
}
