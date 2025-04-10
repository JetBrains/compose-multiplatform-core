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

package androidx.pdf

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.annotation.RestrictTo
import androidx.pdf.exceptions.PdfPasswordException
import androidx.pdf.service.connect.PdfServiceConnection
import androidx.pdf.service.connect.PdfServiceConnectionImpl
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * A [PdfLoader] implementation that opens PDF documents through a sandboxed Android service.
 *
 * This class establishes a connection with the [PdfDocumentServiceImpl] bound service and attempts
 * to load a PDF document using the [PdfRenderer]. If successful, it returns a
 * [SandboxedPdfDocument] instance, which provides a remote interface for interacting with the
 * document.
 *
 * The loading process involves:
 * 1. Establishing a connection with the service using a [PdfServiceConnection].
 * 2. Opening a [ParcelFileDescriptor] for the PDF document.
 * 3. Passing the file descriptor to the service for loading and rendering.
 * 4. Creating a [SandboxedPdfDocument] instance upon successful loading.
 *
 * @param context The [Context] required for accessing system services.
 * @param dispatcher The [CoroutineDispatcher] used for asynchronous operations.
 * @constructor Creates a new [SandboxedPdfLoader] instance.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY)
public class SandboxedPdfLoader(
    context: Context,
    private val dispatcher: CoroutineDispatcher,
) : PdfLoader {
    private val context = context.applicationContext

    internal var testingConnection: PdfServiceConnection? = null

    override suspend fun openDocument(uri: Uri, password: String?): PdfDocument {
        val connection: PdfServiceConnection =
            testingConnection ?: PdfServiceConnectionImpl(context)
        if (!connection.isConnected) {
            connection.connect(uri)
        }

        return withContext(dispatcher) { openDocumentUri(uri, password, connection) }
    }

    private fun openDocumentUri(
        uri: Uri,
        password: String?,
        connection: PdfServiceConnection,
    ): PdfDocument {
        val binder =
            connection.documentBinder
                ?: throw IllegalStateException(
                    "Binder interface not available for loading the document!"
                )
        val pfd = openFileDescriptor(uri)
        val status = PdfLoadingStatus.values()[binder.openPdfDocument(pfd, password)]

        if (status != PdfLoadingStatus.SUCCESS) {
            handlePdfLoadingError(pfd, status)
        }

        return SandboxedPdfDocument(
            uri,
            connection,
            password,
            pfd,
            dispatcher,
            binder.numPages(),
            binder.isPdfLinearized(),
            binder.getFormType()
        )
    }

    private fun handlePdfLoadingError(
        pfd: ParcelFileDescriptor,
        status: PdfLoadingStatus
    ): Exception {
        // The PdfDocument is not created in case of any error, so close the file descriptor
        // here only to release resources and prevent leaks.
        pfd.close()

        when (status) {
            PdfLoadingStatus.WRONG_PASSWORD -> throw PdfPasswordException("Incorrect password")
            PdfLoadingStatus.PDF_ERROR -> throw IOException("Unable to process the PDF document")
            PdfLoadingStatus.LOADING_ERROR -> throw RuntimeException("Loading failed")
            else -> throw IllegalStateException("Unknown loading status: $status")
        }
    }

    private fun openFileDescriptor(uri: Uri): ParcelFileDescriptor {
        return context.contentResolver.openFileDescriptor(uri, "r")
            ?: throw IOException("Failed to open PDF file")
    }
}

/** Represents the loading status of a PDF file. */
@RestrictTo(RestrictTo.Scope.LIBRARY)
internal enum class PdfLoadingStatus {
    SUCCESS, // The PDF was loaded successfully.
    WRONG_PASSWORD, // Incorrect password was provided for a password-protected PDF.
    PDF_ERROR, // Invalid or Corrupt pdf file was provided
    LOADING_ERROR, // A general error occurred while trying to load the PDF
    UNKNOWN
}
