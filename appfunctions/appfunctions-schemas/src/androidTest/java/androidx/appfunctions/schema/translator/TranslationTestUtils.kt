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

package androidx.appfunctions.testing

import android.app.appfunctions.ExecuteAppFunctionResponse.PROPERTY_RETURN_VALUE
import android.os.Bundle
import androidx.annotation.RequiresApi
import androidx.appfunctions.AppFunctionData
import androidx.appfunctions.internal.Translator
import androidx.appfunctions.schema.translator.toJetpackGenericDocument
import androidx.appfunctions.schema.translator.toPlatformGenericDocument
import androidx.appsearch.app.GenericDocument
import com.google.common.truth.Truth.assertThat

@RequiresApi(33)
internal class TranslatorTestUtils(private val translator: Translator) {
    internal fun assertUpgradeRequestTranslation(
        legacyParameterName: String,
        legacyInput: Any,
        expectedJetpackOutput: Any
    ) {
        val legacyGenericDocument =
            GenericDocument.Builder<GenericDocument.Builder<*>>("", "", "")
                .setPropertyDocument(
                    legacyParameterName,
                    GenericDocument.fromDocumentClass(legacyInput)
                )
                .build()
        val legacyData =
            AppFunctionData(
                genericDocument = legacyGenericDocument.toPlatformGenericDocument(),
                extras = Bundle.EMPTY
            )
        val upgradedRequest = translator.upgradeRequest(legacyData)
        val upgradedParams =
            checkNotNull(upgradedRequest.getAppFunctionData(JETPACK_PARAMETERS_NAME))
                .deserialize(expectedJetpackOutput::class.java)
        assertThat(upgradedParams).isEqualTo(expectedJetpackOutput)
    }

    internal fun assertDowngradeResponseTranslation(jetpackInput: Any, expectedLegacyOutput: Any) {
        val jetpackData =
            AppFunctionData.Builder(
                    qualifiedName = "",
                )
                .setAppFunctionData(
                    PROPERTY_RETURN_VALUE,
                    AppFunctionData.serialize(jetpackInput, jetpackInput.javaClass)
                )
                .build()
        val downgradedResponse =
            translator
                .downgradeResponse(jetpackData)
                .getAppFunctionData(PROPERTY_RETURN_VALUE)!!
                .genericDocument
                .toJetpackGenericDocument()
                .toDocumentClass(expectedLegacyOutput::class.java)
        assertThat(downgradedResponse).isEqualTo(expectedLegacyOutput)
    }

    internal fun assertUpgradeResponseTranslation(legacyInput: Any, expectedJetpackOutput: Any) {
        val legacyGenericDocument =
            GenericDocument.Builder<GenericDocument.Builder<*>>("", "", "")
                .setPropertyDocument(
                    PROPERTY_RETURN_VALUE,
                    GenericDocument.fromDocumentClass(legacyInput)
                )
                .build()
        val legacyData =
            AppFunctionData(
                genericDocument = legacyGenericDocument.toPlatformGenericDocument(),
                extras = Bundle.EMPTY
            )
        val upgradedResponse =
            translator
                .upgradeResponse(legacyData)
                .getAppFunctionData(PROPERTY_RETURN_VALUE)!!
                .deserialize(expectedJetpackOutput::class.java)
        assertThat(upgradedResponse).isEqualTo(expectedJetpackOutput)
    }

    internal fun assertDowngradeRequestTranslation(
        legacyParameterName: String,
        jetpackInput: Any,
        expectedLegacyOutput: Any
    ) {
        val jetpackData =
            AppFunctionData.Builder(qualifiedName = "")
                .setAppFunctionData(
                    JETPACK_PARAMETERS_NAME,
                    AppFunctionData.serialize(jetpackInput, jetpackInput.javaClass)
                )
                .build()
        val downgradedParams =
            translator
                .downgradeRequest(jetpackData)
                .getAppFunctionData(legacyParameterName)!!
                .genericDocument
                .toJetpackGenericDocument()
                .toDocumentClass(expectedLegacyOutput::class.java)
        assertThat(downgradedParams).isEqualTo(expectedLegacyOutput)
    }

    private companion object {
        const val JETPACK_PARAMETERS_NAME = "parameters"
    }
}
