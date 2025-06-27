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

import androidx.credentials.CreatePublicKeyCredentialRequest
import androidx.credentials.ExperimentalDigitalCredentialApi
import androidx.credentials.playservices.TestCredentialsActivity
import androidx.credentials.playservices.TestUtils
import androidx.credentials.playservices.controllers.utils.CreatePublicKeyCredentialControllerTestUtils
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.filters.SmallTest
import com.google.common.truth.Truth
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SmallTest
@OptIn(ExperimentalDigitalCredentialApi::class)
@SdkSuppress(minSdkVersion = 23)
class CreatePublicKeyCredentialControllerTest {
    @Test
    fun convertRequestToPlayServices_success() {
        val request =
            CreatePublicKeyCredentialRequest(
                requestJson =
                    CreatePublicKeyCredentialControllerTestUtils
                        .MAIN_CREATE_JSON_ALL_REQUIRED_AND_OPTIONAL_FIELDS_PRESENT,
                isConditional = true
            )
        val activityScenario = ActivityScenario.launch(TestCredentialsActivity::class.java)

        activityScenario.onActivity { activity: TestCredentialsActivity? ->
            val convertedRequest =
                CreatePublicKeyCredentialController(activity!!)
                    .convertRequestToPlayServices(request)

            Truth.assertThat(convertedRequest.origin).isEqualTo(request.origin)
            Truth.assertThat(convertedRequest.requestJson).isEqualTo(request.requestJson)
            Truth.assertThat(convertedRequest.type).isEqualTo(request.type)
            TestUtils.Companion.equals(
                convertedRequest.candidateQueryData,
                request.candidateQueryData
            )
            TestUtils.Companion.equals(convertedRequest.credentialData, request.credentialData)
            // TODO(b/359049355): Replace with API in CreateCredentialRequest while exposing
            // Companion
            // object in that class
            Truth.assertThat(
                    convertedRequest.candidateQueryData.getBoolean(
                        "androidx.credentials.BUNDLE_KEY_IS_CONDITIONAL_REQUEST"
                    )
                )
                .isTrue()
        }
    }
}
