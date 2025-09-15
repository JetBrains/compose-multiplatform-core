/*
 * Copyright 2022 The Android Open Source Project
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

package androidx.credentials.providerevents

import android.content.Context
import android.os.CancellationSignal
import androidx.annotation.RestrictTo
import androidx.credentials.CredentialManagerCallback
import androidx.credentials.providerevents.exception.ImportCredentialsException
import androidx.credentials.providerevents.exception.RegisterExportException
import androidx.credentials.providerevents.transfer.ExportEntry
import androidx.credentials.providerevents.transfer.ImportCredentialsRequest
import androidx.credentials.providerevents.transfer.ProviderImportCredentialsResponse
import androidx.credentials.providerevents.transfer.RegisterExportRequest
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Manages provider event flows.
 *
 * APIs in [ProviderEventsManager] are API endpoints supported by credential providers. One example
 * of the supported provider API is credential transfer. In order to engage in exporting
 * credentials, each provider must register first its [ExportEntry]. Then calling
 * [importCredentials] will launch the transfer flow of importing credentials from the registered
 * providers.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public interface ProviderEventsManager {
    public companion object {
        /**
         * Creates a [ProviderEventsManager] based on the given [context].
         *
         * @param context the context with which the ProviderEventsManager should be associated
         */
        @JvmStatic
        public fun create(context: Context): ProviderEventsManager =
            ProviderEventsManagerImpl(context)
    }

    /**
     * Starts a provider selector UI for the user to import credentials from. After the user selects
     * an [ExportEntry] from the list, the request will be forwarded to the exporting provider. The
     * exporting provider will return the credentials which will be returned as
     * [ProviderImportCredentialsResponse].
     *
     * @param context the activity context
     * @param request the information needed to import credentials from another provider
     */
    public suspend fun importCredentials(
        context: Context,
        request: ImportCredentialsRequest,
    ): ProviderImportCredentialsResponse = suspendCancellableCoroutine { continuation ->
        // Any Android API that supports cancellation should be configured to propagate
        // coroutine cancellation as follows:
        val canceller = CancellationSignal()
        continuation.invokeOnCancellation { canceller.cancel() }
        val callback =
            object :
                CredentialManagerCallback<
                    ProviderImportCredentialsResponse,
                    ImportCredentialsException,
                > {
                override fun onResult(result: ProviderImportCredentialsResponse) {
                    if (continuation.isActive) {
                        continuation.resume(result)
                    }
                }

                override fun onError(e: ImportCredentialsException) {
                    if (continuation.isActive) {
                        continuation.resumeWithException(e)
                    }
                }
            }

        importCredentialsAsync(
            context,
            request,
            canceller,
            // Use a direct executor to avoid extra dispatch. Resuming the continuation will
            // handle getting to the right thread or pool via the ContinuationInterceptor.
            Runnable::run,
            callback,
        )
    }

    /**
     * Starts a provider selector UI for the user to import credentials from. After the user selects
     * an [ExportEntry] from the list, the request will be forwarded to the exporting provider. The
     * exporting provider will return the credentials which will be returned as
     * [ProviderImportCredentialsResponse].
     *
     * @param context the activity context
     * @param request the information needed to import credentials from another provider
     * @param cancellationSignal an optional signal that allows for cancelling this call
     * @param executor the callback will take place on this executor
     * @param callback the callback invoked when the request succeeds or fails
     */
    public fun importCredentialsAsync(
        context: Context,
        request: ImportCredentialsRequest,
        cancellationSignal: CancellationSignal?,
        executor: Executor,
        callback:
            CredentialManagerCallback<ProviderImportCredentialsResponse, ImportCredentialsException>,
    )

    /**
     * Registers the provider with the Provider Events Manager.
     *
     * The registered provider info will be used by the provider events manager when handling an
     * import request by another provider. The provider events manager will determine if the
     * registry contains qualified providers capable of fulfilling the import request, and if so
     * will surface a provider selector UI to proceed with the import request. The [ExportEntry]
     * registered through this API will show up in the selector UI.
     *
     * @param request the request containing the provider data to register
     */
    public suspend fun registerExport(request: RegisterExportRequest): Boolean =
        suspendCancellableCoroutine { continuation ->
            // Any Android API that supports cancellation should be configured to propagate
            // coroutine cancellation as follows:
            val canceller = CancellationSignal()
            continuation.invokeOnCancellation { canceller.cancel() }

            val callback =
                object : CredentialManagerCallback<Boolean, RegisterExportException> {
                    override fun onResult(result: Boolean) {
                        if (continuation.isActive) {
                            continuation.resume(result)
                        }
                    }

                    override fun onError(e: RegisterExportException) {
                        if (continuation.isActive) {
                            continuation.resumeWithException(e)
                        }
                    }
                }

            registerExportAsync(
                request,
                canceller,
                // Use a direct executor to avoid extra dispatch. Resuming the continuation will
                // handle getting to the right thread or pool via the ContinuationInterceptor.
                Runnable::run,
                callback,
            )
        }

    /**
     * Registers the provider with the Provider Events Manager.
     *
     * The registered provider info will be used by the provider events manager when handling an
     * import request by another provider. The provider events manager will determine if the
     * registry contains qualified providers capable of fulfilling the import request, and if so
     * will surface a provider selector UI to proceed with the import request.
     *
     * @param request the request containing the provider data to register
     * @param cancellationSignal an optional signal that allows for cancelling this call
     * @param executor the callback will take place on this executor
     * @param callback the callback invoked when the request succeeds or fails
     */
    public fun registerExportAsync(
        request: RegisterExportRequest,
        cancellationSignal: CancellationSignal?,
        executor: Executor,
        callback: CredentialManagerCallback<Boolean, RegisterExportException>,
    )
}
