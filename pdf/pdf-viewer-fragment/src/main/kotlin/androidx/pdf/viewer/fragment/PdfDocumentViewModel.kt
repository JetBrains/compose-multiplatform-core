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
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.pdf.PdfDocument
import androidx.pdf.PdfLoader
import androidx.pdf.SandboxedPdfLoader
import androidx.pdf.exceptions.PdfPasswordException
import androidx.pdf.viewer.fragment.model.PdfFragmentUiState
import java.util.concurrent.Executors
import kotlinx.coroutines.Job
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
internal class PdfDocumentViewModel(
    private val state: SavedStateHandle,
    private val loader: PdfLoader
) : ViewModel() {

    /** A Coroutine [Job] that manages the PDF loading task. */
    private var documentLoadJob: Job? = null

    private val _fragmentUiScreenState =
        MutableStateFlow<PdfFragmentUiState>(PdfFragmentUiState.Loading)

    /**
     * Represents the UI state of the fragment.
     *
     * Exposes the UI state as a StateFlow to enable reactive consumption and ensure that consumers
     * always receive the latest state.
     */
    internal val fragmentUiScreenState: StateFlow<PdfFragmentUiState>
        get() = _fragmentUiScreenState.asStateFlow()

    /**
     * Indicates whether the user is entering their password for the first time or making a repeated
     * attempt.
     *
     * This state is used to determine the appropriate error message to display in the password
     * dialog.
     */
    private var passwordFailed = false

    /** DocumentUri as set in [state] */
    val documentUriFromState: Uri?
        get() = state[DOCUMENT_URI_KEY]

    /** isTextSearchActive as set in [state] */
    val isTextSearchActiveFromState: Boolean
        get() = state[TEXT_SEARCH_STATE_KEY] ?: false

    /** isToolboxVisibleFromState as set in [state] */
    val isToolboxVisibleFromState: Boolean
        get() = state[TOOLBOX_STATE_KEY] ?: false

    init {
        /**
         * Open PDF if documentUri was previously set in state. This will be required in events like
         * process death
         */
        state.get<Uri>(DOCUMENT_URI_KEY)?.let { uri ->
            documentLoadJob = viewModelScope.launch { openDocument(uri) }
        }
    }

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
    fun loadDocument(uri: Uri?, password: String?) {
        uri?.let {
            /*
            Triggers the document loading process only under the following conditions:
            1. **New Document URI:** The URI of the document to be loaded is different
            `from the URI of the previously loaded document.
            2. **Previous Load Failure or No Previous Load:** This is required when a
             reload of document is required like document loading failed previous time or opened
             using an incorrect password.
             */
            if (
                (uri != state[DOCUMENT_URI_KEY] ||
                    fragmentUiScreenState.value !is PdfFragmentUiState.DocumentLoaded)
            ) {
                state[DOCUMENT_URI_KEY] = uri

                // Ensure we don't schedule duplicate loading by canceling previous one.
                if (documentLoadJob?.isActive == true) documentLoadJob?.cancel()

                documentLoadJob = viewModelScope.launch { openDocument(uri, password) }
            }
        }
    }

    /**
     * Handles user interaction related to enabling the search view.
     *
     * This function ensures that the search view is properly displayed and ready for user input
     * when triggered.
     */
    fun onSearchViewToggle(isTextSearchActive: Boolean) {
        state[TEXT_SEARCH_STATE_KEY] = isTextSearchActive
        // TODO: add implementation after integrating PdfSearchView b/379054326
    }

    /**
     * Handles user interaction related to enabling the toolbox view.
     *
     * This function ensures that the toolbox view is properly displayed and ready for user input
     * when triggered.
     */
    fun onToolboxViewToggle(isToolboxActive: Boolean) {
        state[TOOLBOX_STATE_KEY] = isToolboxActive
        // TODO: add implementation after integrating Toolbox view b/379052981
    }

    private suspend fun openDocument(uri: Uri, password: String? = null) {
        /** Move to [PdfFragmentUiState.Loading] state before we begin load operation. */
        _fragmentUiScreenState.update { PdfFragmentUiState.Loading }

        try {

            // Try opening pdf with provided params
            val document = loader.openDocument(uri, password)

            /** Successful load, move to [PdfFragmentUiState.DocumentLoaded] state. */
            _fragmentUiScreenState.update { PdfFragmentUiState.DocumentLoaded(document) }

            /** Resets the [passwordFailed] state after a document is successfully loaded. */
            passwordFailed = false
        } catch (passwordException: PdfPasswordException) {
            /** Move to [PdfFragmentUiState.PasswordRequested] for password protected pdf. */
            _fragmentUiScreenState.update { PdfFragmentUiState.PasswordRequested(passwordFailed) }

            /** Enable [passwordFailed] for subsequent password attempts. */
            if (!passwordFailed) passwordFailed = true
        } catch (exception: Exception) {
            /** Generic exception handling, move to [PdfFragmentUiState.DocumentError] state. */
            _fragmentUiScreenState.update { PdfFragmentUiState.DocumentError(exception) }

            /** Resets the [passwordFailed] state after a document failed to load. */
            passwordFailed = false
        }
    }

    @Suppress("UNCHECKED_CAST")
    companion object {

        private const val DOCUMENT_URI_KEY = "documentUri"
        private const val TEXT_SEARCH_STATE_KEY = "textSearchState"
        private const val TOOLBOX_STATE_KEY = "toolboxState"

        val Factory: ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(
                    modelClass: Class<T>,
                    extras: CreationExtras
                ): T {
                    // Get the Application object from extras
                    val application = checkNotNull(extras[APPLICATION_KEY])
                    // Create a SavedStateHandle for this ViewModel from extras
                    val savedStateHandle = extras.createSavedStateHandle()

                    val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
                    return (PdfDocumentViewModel(
                        savedStateHandle,
                        SandboxedPdfLoader(application, dispatcher)
                    ))
                        as T
                }
            }
    }
}
