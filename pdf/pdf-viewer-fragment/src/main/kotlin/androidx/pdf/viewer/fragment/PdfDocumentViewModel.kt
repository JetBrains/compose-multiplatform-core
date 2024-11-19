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
import androidx.annotation.RestrictTo
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.pdf.PdfDocument
import androidx.pdf.PdfLoader
import androidx.pdf.SandboxedPdfLoader
import java.util.concurrent.Executors
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * A ViewModel class responsible for managing the loading and state of a PDF document.
 *
 * This ViewModel uses a [PdfLoader] to asynchronously open a PDF document from a given Uri. The
 * loading result, which can be either a success with a [PdfDocument] or a failure with an
 * exception, is exposed through the `pdfDocumentStateFlow`.
 *
 * The `loadDocument` function initiates the loading process within the `viewModelScope`, ensuring
 * that the operation is properly managed and not cancelled by configuration changes.
 *
 * @constructor Creates a new [PdfDocumentViewModel] instance.
 * @property loader The [PdfLoader] used to open the PDF document.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY)
public class PdfDocumentViewModel(private val loader: PdfLoader) : ViewModel() {
    private val _pdfDocumentStateFlow = MutableStateFlow<Result<PdfDocument>?>(null)
    public val pdfDocumentStateFlow: StateFlow<Result<PdfDocument>?> =
        _pdfDocumentStateFlow.asStateFlow()

    /**
     * Initiates the loading of a PDF document from the provided Uri.
     *
     * This function uses the provided [PdfLoader] to asynchronously open the PDF document. The
     * loading result is then posted to the `pdfDocumentStateFlow` as a [Result] object, indicating
     * either success with a [PdfDocument] or failure with an exception.
     *
     * The loading operation is executed within the `viewModelScope` to ensure that it continues
     * even if a configuration change occurs.
     *
     * @param uri The Uri of the PDF document to load.
     * @param password The optional password to use if the document is encrypted.
     */
    public fun loadDocument(uri: Uri?, password: String?) {
        viewModelScope.launch {
            _pdfDocumentStateFlow.update {
                uri?.let {
                    try {
                        val document = loader.openDocument(uri, password)
                        Result.success(document)
                    } catch (exception: Exception) {
                        Result.failure(exception)
                    }
                }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    public companion object {
        public val Factory: ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(
                    modelClass: Class<T>,
                    extras: CreationExtras
                ): T {
                    val application = checkNotNull(extras[APPLICATION_KEY])
                    val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
                    return (PdfDocumentViewModel(SandboxedPdfLoader(application, dispatcher))) as T
                }
            }
    }
}
