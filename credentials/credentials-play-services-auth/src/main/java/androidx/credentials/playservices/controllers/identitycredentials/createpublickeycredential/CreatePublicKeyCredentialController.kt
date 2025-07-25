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

package androidx.credentials.playservices.controllers.identitycredentials.createpublickeycredential

import android.content.Context
import android.os.CancellationSignal
import androidx.credentials.CreatePublicKeyCredentialRequest
import androidx.credentials.CreatePublicKeyCredentialResponse
import androidx.credentials.CredentialManagerCallback
import androidx.credentials.PublicKeyCredential
import androidx.credentials.exceptions.CreateCredentialCancellationException
import androidx.credentials.exceptions.CreateCredentialException
import androidx.credentials.exceptions.CreateCredentialInterruptedException
import androidx.credentials.exceptions.CreateCredentialUnknownException
import androidx.credentials.internal.toJetpackCreateException
import androidx.credentials.playservices.CredentialProviderPlayServicesImpl
import androidx.credentials.playservices.controllers.CredentialProviderController
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.identitycredentials.CreateCredentialRequest
import com.google.android.gms.identitycredentials.CreateCredentialResponse
import com.google.android.gms.identitycredentials.IdentityCredentialManager
import java.util.concurrent.Executor

/** A controller to handle the CreateCredential flow with play services. */
internal class CreatePublicKeyCredentialController(private val context: Context) :
    CredentialProviderController<
        CreatePublicKeyCredentialRequest,
        CreateCredentialRequest,
        CreateCredentialResponse,
        androidx.credentials.CreateCredentialResponse,
        CreateCredentialException
    >(context) {

    override fun invokePlayServices(
        request: CreatePublicKeyCredentialRequest,
        callback:
            CredentialManagerCallback<
                androidx.credentials.CreateCredentialResponse,
                CreateCredentialException
            >,
        executor: Executor,
        cancellationSignal: CancellationSignal?
    ) {
        if (CredentialProviderPlayServicesImpl.Companion.cancellationReviewer(cancellationSignal)) {
            return
        }

        val convertedRequest = this.convertRequestToPlayServices(request)
        IdentityCredentialManager.Companion.getClient(context)
            .createCredential(convertedRequest)
            .addOnSuccessListener {
                val createCredentialResponse: CreateCredentialResponse? =
                    it.createCredentialResponse
                if (createCredentialResponse == null) {
                    cancelOrCallbackExceptionOrResult(cancellationSignal) {
                        executor.execute { callback.onError(CreateCredentialUnknownException()) }
                    }
                }
                if (createCredentialResponse != null) {
                    try {
                        val response =
                            this.convertResponseToCredentialManager(createCredentialResponse)
                        cancelOrCallbackExceptionOrResult(cancellationSignal) {
                            executor.execute { callback.onResult(response) }
                        }
                    } catch (e: Exception) {
                        cancelOrCallbackExceptionOrResult(cancellationSignal) {
                            if (e is CreateCredentialException) {
                                executor.execute { callback.onError(e) }
                            } else {
                                executor.execute {
                                    callback.onError(CreateCredentialUnknownException(e.message))
                                }
                            }
                        }
                    }
                }
            }
            .addOnFailureListener { e ->
                cancelOrCallbackExceptionOrResult(cancellationSignal) {
                    val exception = fromGmsException(e)
                    executor.execute { callback.onError(exception) }
                }
            }
    }

    private fun fromGmsException(e: Throwable): CreateCredentialException {
        return when (e) {
            is com.google.android.gms.identitycredentials.CreateCredentialException ->
                toJetpackCreateException(e.type, e.message)
            is ApiException ->
                when (e.statusCode) {
                    CommonStatusCodes.CANCELED -> {
                        CreateCredentialCancellationException(e.message)
                    }
                    in retryables -> {
                        CreateCredentialInterruptedException(e.message)
                    }
                    else -> {
                        CreateCredentialUnknownException("Conditional create failed, failure: $e")
                    }
                }
            else -> CreateCredentialUnknownException("Conditional create failed, failure: $e")
        }
    }

    public override fun convertRequestToPlayServices(
        request: CreatePublicKeyCredentialRequest
    ): CreateCredentialRequest {
        return CreateCredentialRequest(
            type = request.type,
            credentialData = request.credentialData,
            candidateQueryData = request.candidateQueryData,
            origin = request.origin,
            requestJson = request.requestJson,
            resultReceiver = null
        )
    }

    override fun convertResponseToCredentialManager(
        response: CreateCredentialResponse
    ): androidx.credentials.CreateCredentialResponse {
        when (response.type) {
            PublicKeyCredential.TYPE_PUBLIC_KEY_CREDENTIAL -> {
                // TODO(359049355): Replace with
                // CreatePublicKeyCredentialResponse.createFrom(response.data) after
                // making this API public
                try {
                    val registrationResponseJson =
                        response.data.getString(
                            "androidx.credentials.BUNDLE_KEY_REGISTRATION_RESPONSE_JSON"
                        )
                    return CreatePublicKeyCredentialResponse(registrationResponseJson!!)
                } catch (_: NullPointerException) {
                    throw CreateCredentialUnknownException()
                }
            }
            else -> throw CreateCredentialUnknownException()
        }
    }

    companion object {
        /**
         * Factory method for
         * [androidx.credentials.playservices.controllers.identityauth.createpublickeycredential.CredentialProviderCreatePublicKeyCredentialController].
         *
         * @param context the calling context for this controller
         * @return a credential provider controller for IdentityCredentialsCreatePublicKeyCredential
         */
        @JvmStatic
        fun getInstance(context: Context): CreatePublicKeyCredentialController {
            return CreatePublicKeyCredentialController(context)
        }
    }
}
