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

import com.google.common.truth.Truth.assertThat
import org.junit.Assert
import org.junit.Test

@OptIn(ExperimentalDigitalCredentialApi::class)
class CreateDigitalCredentialRequestTest {

    @Test
    fun constructor_emptyJson_throws() {
        Assert.assertThrows(
            "requestJson must not be empty, and must be a valid JSON",
            IllegalArgumentException::class.java
        ) {
            CreateDigitalCredentialRequest(requestJson = "")
        }
    }

    @Test
    fun constructor_invalidJson_throws() {
        Assert.assertThrows(
            "requestJson must not be empty, and must be a valid JSON",
            IllegalArgumentException::class.java
        ) {
            CreateDigitalCredentialRequest(requestJson = "dsfliuh4akdsjhbf")
        }
    }

    @Test
    fun constructor_valid() {
        val json = "{key: value}"

        val request = CreateDigitalCredentialRequest(json)

        assertThat(request.type).isEqualTo(DigitalCredential.TYPE_DIGITAL_CREDENTIAL)
        assertThat(request.requestJson).isEqualTo(json)
        assertThat(request.credentialData.getString("androidx.credentials.BUNDLE_KEY_REQUEST_JSON"))
            .isEqualTo(json)
        assertThat(request.displayInfo.userId).isEqualTo("unused")
        assertThat(
                request.candidateQueryData.getBoolean(
                    "androidx.credentials.BUNDLE_KEY_IS_AUTO_SELECT_ALLOWED"
                )
            )
            .isFalse()
        assertThat(request.isSystemProviderRequired).isFalse()
        assertThat(request.isAutoSelectAllowed).isFalse()
        assertThat(request.origin).isNull()
        assertThat(request.preferImmediatelyAvailableCredentials).isFalse()
    }
}
