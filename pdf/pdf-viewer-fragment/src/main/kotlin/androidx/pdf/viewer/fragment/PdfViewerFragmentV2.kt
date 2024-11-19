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

package androidx.pdf.viewer.fragment

import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams
import androidx.annotation.RestrictTo
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.pdf.exceptions.PdfPasswordException
import androidx.pdf.view.PdfView
import kotlinx.coroutines.launch

@RestrictTo(RestrictTo.Scope.LIBRARY)
public class PdfViewerFragmentV2 : Fragment() {
    private val documentViewModel: PdfDocumentViewModel by
        viewModels() { PdfDocumentViewModel.Factory }
    private lateinit var pdfView: PdfView

    public var documentUri: Uri? = null
        set(value) {
            field = value
            documentViewModel.loadDocument(value, /* password= */ null)
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        pdfView =
            PdfView(requireContext()).apply {
                layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            }
        return pdfView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewLifecycleOwner.lifecycleScope.launch {
            documentViewModel.pdfDocumentStateFlow.collect { result ->
                if (result != null) {
                    if (result.isSuccess) {
                        // Document loaded
                        pdfView.pdfDocument = result.getOrNull()
                        Log.i("DDDDD", "Document has been loaded successfully")
                    } else if (result.exceptionOrNull() is PdfPasswordException) {
                        // Display password prompt
                        Log.i("DDDDD", "Document requires a password")
                    } else {
                        // Handle generic error
                        Log.i("DDDDD", "Error occurred while loading pdf")
                    }
                } else {
                    // Display loading view
                    Log.i("DDDDD", "Null result")
                }
            }
        }
    }
}
