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

package androidx.credentials.providerevents.playservices

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.SigningInfo
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.annotation.RestrictTo
import androidx.credentials.provider.CallingAppInfo
import androidx.credentials.providerevents.exception.ImportCredentialsException
import androidx.credentials.providerevents.playservices.UriUtils.Companion.readFromUri
import androidx.credentials.providerevents.playservices.UriUtils.Companion.writeToUri
import androidx.credentials.providerevents.playservices.controller.ProviderEventsBaseController.Companion.EXTRA_PACKAGE_NAME_KEY
import androidx.credentials.providerevents.playservices.controller.ProviderEventsBaseController.Companion.EXTRA_REQUEST_JSON
import androidx.credentials.providerevents.playservices.controller.ProviderEventsBaseController.Companion.EXTRA_SIGNING_INFO_KEY
import androidx.credentials.providerevents.transfer.ExportEntry
import androidx.credentials.providerevents.transfer.ImportCredentialsRequest
import androidx.credentials.providerevents.transfer.ImportCredentialsResponse
import androidx.credentials.providerevents.transfer.ProviderImportCredentialsRequest
import androidx.credentials.providerevents.transfer.ProviderImportCredentialsResponse

/**
 * IntentHandler to be used by credential providers to extract requests from a given intent, or to
 * set back a response or an exception to a given intent while dealing with activities invoked by
 * pending intents set on a [ExportEntry] for the import flow.
 *
 * When user selects one of the entries, the credential provider's corresponding activity is
 * invoked. The intent associated with this activity must be extracted and passed into the utils in
 * this class to extract the required requests.
 *
 * When user interaction is complete, credential providers must set the activity result by calling
 * [android.app.Activity.setResult] by setting an appropriate result code and data of type [Intent].
 * This data should also be prepared by using the utils in this class to populate the required
 * response/exception.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class IntentHandler {
    public companion object {
        private const val TAG = "IntentHelper"
        private const val EXTRA_IMPORT_CREDENTIALS_EXCEPTION =
            "androidx.credentials.providerevents.EXTRA_IMPORT_CREDENTIALS_EXCEPTION"

        /**
         * Extracts the [ImportCredentialsRequest] from the provider's [PendingIntent] invoked by
         * the Android system, when the user selects an entry
         *
         * @param intent the intent associated with the [Activity] invoked through the
         *   [PendingIntent]
         */
        @Suppress("RestrictedApiAndroidX")
        @JvmStatic
        public fun retrieveProviderImportCredentialsRequest(
            intent: Intent
        ): ProviderImportCredentialsRequest? {
            val extras = intent.extras
            if (extras == null) {
                Log.i(TAG, "Intent extras are null")
                return null
            }

            val reqJson = extras.getString(EXTRA_REQUEST_JSON)
            if (reqJson == null) {
                Log.e(TAG, "import request json is null")
                return null
            }

            val callingPackageName = intent.getStringExtra(EXTRA_PACKAGE_NAME_KEY)
            if (callingPackageName.isNullOrEmpty()) {
                Log.e(TAG, "Calling package is null or empty")
                return null
            }
            val uri = intent.data
            if (uri == null) {
                Log.e(TAG, "Uri is null")
                return null
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                @Suppress("DEPRECATION")
                val signingInfo: SigningInfo? = extras.getParcelable(EXTRA_SIGNING_INFO_KEY)
                if (signingInfo == null) {
                    Log.e(TAG, "Calling package is null or empty")
                    return null
                }
                return ProviderImportCredentialsRequest(
                    ImportCredentialsRequest(reqJson),
                    CallingAppInfo.create(callingPackageName, signingInfo, null),
                    uri,
                )
            } else {
                // TODO(b/436712597): handle for < P
                return null
            }
        }

        @Suppress("RestrictedApiAndroidX")
        @JvmStatic
        public fun retrieveProviderImportCredentialsResponse(
            intent: Intent,
            uri: Uri,
            context: Context,
        ): ProviderImportCredentialsResponse? {
            if (Build.VERSION.SDK_INT >= 28) {
                @Suppress("DEPRECATION")
                val signingInfo: SigningInfo? = intent.getParcelableExtra(EXTRA_SIGNING_INFO_KEY)
                val pckName = intent.getStringExtra(EXTRA_PACKAGE_NAME_KEY)
                val credentialsJson = readFromUri(uri, context)
                return ProviderImportCredentialsResponse(
                    ImportCredentialsResponse(credentialsJson),
                    CallingAppInfo.create(pckName!!, signingInfo!!, null),
                )
            } else {
                // TODO(b/436712597): handle for < P
                return null
            }
        }

        /**
         * Sets the [ImportCredentialsResponse] on the uri passed in. This response is written to
         * the uri associated with the [PendingIntent] set on a [ExportEntry].
         *
         * A credential provider must set the result code to [Activity.RESULT_OK] if a valid
         * response, or a valid exception is being set as the data to the result.
         *
         * @param context the activity context
         * @param uri the uri that was provided by the importer
         * @param response the response to be passed to the importer
         */
        @JvmStatic
        public fun setImportCredentialsResponse(
            context: Context,
            uri: Uri,
            response: ImportCredentialsResponse,
        ) {
            writeToUri(uri, response.responseJson, context)
        }

        /**
         * Sets the [androidx.credentials.providerevents.exception.ImportCredentialsException] if an
         * error is encountered during the final phase of the import credential flow.
         *
         * A provider manager service returns a list of [ExportEntry] as part of the query phase of
         * the import-credential flow. If the user selects one of these entries, the corresponding
         * [PendingIntent] is fired and the provider's activity is invoked. If there is an error
         * encountered during the lifetime of that activity, the provider must use this API to set
         * an exception on the given intent before finishing the activity in question.
         *
         * The intent is set using the [Activity.setResult] method that takes in the intent, as well
         * as a result code. A credential provider must set the result code to [Activity.RESULT_OK]
         * if successful, or a valid exception is being set as the data to the result.
         *
         * @param intent the intent to be set on the result of the [Activity] invoked through the
         *   [PendingIntent]
         * @param exception the exception to be set as an extra to the [intent]
         */
        @JvmStatic
        public fun setImportCredentialsException(
            intent: Intent,
            exception: ImportCredentialsException,
        ) {
            intent.putExtra(
                EXTRA_IMPORT_CREDENTIALS_EXCEPTION,
                ImportCredentialsException.asBundle(exception),
            )
        }

        @JvmStatic
        public fun retrieveImportCredentialsException(intent: Intent): ImportCredentialsException? {
            return ImportCredentialsException.fromBundle(
                intent.getBundleExtra(EXTRA_IMPORT_CREDENTIALS_EXCEPTION) ?: return null
            )
        }
    }
}
