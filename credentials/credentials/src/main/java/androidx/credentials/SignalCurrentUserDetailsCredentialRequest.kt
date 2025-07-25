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

package androidx.credentials

import androidx.annotation.RestrictTo

/**
 * A request to signal the user's current name and display name.
 *
 * @param requestJson the request in JSON format. The format of the JSON should follow the
 *   [WebAuthn Spec](https://w3c.github.io/webauthn/#sctn-signalCurrentUserDetails). Throws
 *   SignalCredentialStateException if base64Url decoding fails for the user id.
 * @param origin the origin of a different application if the request is being made on behalf of
 *   that application (Note: for API level >=34, setting a non-null value for this parameter will
 *   throw a SecurityException if android.permission.CREDENTIAL_MANAGER_SET_ORIGIN is not present)
 */
@RestrictTo(RestrictTo.Scope.LIBRARY)
class SignalCurrentUserDetailsCredentialRequest(requestJson: String, origin: String? = null) :
    SignalCredentialStateRequest(
        SIGNAL_CURRENT_USER_DETAILS_STATE_REQUEST_TYPE,
        requestJson,
        origin,
    ) {
    internal companion object {
        internal const val SIGNAL_CURRENT_USER_DETAILS_STATE_REQUEST_TYPE =
            "androidx.credentials.SIGNAL_CURRENT_USER_DETAILS_STATE_REQUEST_TYPE"
    }
}
