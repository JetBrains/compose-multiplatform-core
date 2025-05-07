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

package androidx.appfunctions.schema.notes.translators

import android.app.PendingIntent
import android.content.Intent
import androidx.appfunctions.schema.TranslatorTestUtils
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test

@SdkSuppress(minSdkVersion = 33)
class ShowNoteTranslatorTest {

    private val translatorTestUtils = TranslatorTestUtils(ShowNoteTranslator())

    @Test
    fun upgradeRequest_allFields() {
        val expectedUpgradedParams = ShowNoteAppFunctionParams(noteId = "noteId123")
        translatorTestUtils.assertUpgradeRequestTranslation(
            legacyParameterName = "noteId",
            legacyInput = "noteId123",
            expectedJetpackOutput = expectedUpgradedParams
        )
    }

    @Test
    fun downgradeRequest_allFields() {
        val jetpackParams = ShowNoteAppFunctionParams(noteId = "noteId123")

        translatorTestUtils.assertDowngradeRequestTranslation(
            legacyParameterName = "noteId",
            jetpackInput = jetpackParams,
            expectedLegacyOutput = "noteId123"
        )
    }

    @Test
    fun upgradeResponse_allFields() {
        val pendingIntent =
            PendingIntent.getActivity(
                /* context= */ InstrumentationRegistry.getInstrumentation().context,
                /* requestCode= */ 0,
                /* intent= */ Intent(),
                /* flags= */ PendingIntent.FLAG_IMMUTABLE,
            )

        val expectedUpgradedResponse = ShowNoteAppFunctionResponse(intentToOpen = pendingIntent)

        translatorTestUtils.assertUpgradeResponseTranslation(
            legacyInput = pendingIntent,
            expectedJetpackOutput = expectedUpgradedResponse
        )
    }

    @Test
    fun downgradeResponse_allFields() {
        val pendingIntent =
            PendingIntent.getActivity(
                /* context= */ InstrumentationRegistry.getInstrumentation().context,
                /* requestCode= */ 0,
                /* intent= */ Intent(),
                /* flags= */ PendingIntent.FLAG_IMMUTABLE,
            )
        val jetpackResponse = ShowNoteAppFunctionResponse(intentToOpen = pendingIntent)

        translatorTestUtils.assertDowngradeResponseTranslation(
            jetpackInput = jetpackResponse,
            expectedLegacyOutput = pendingIntent
        )
    }
}
