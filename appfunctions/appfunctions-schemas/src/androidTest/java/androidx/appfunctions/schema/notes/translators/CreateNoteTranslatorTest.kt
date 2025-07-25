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

import android.net.Uri
import androidx.appfunctions.LegacyAttachment
import androidx.appfunctions.LegacyCreateNoteParams
import androidx.appfunctions.LegacyNote
import androidx.appfunctions.LegacyUri
import androidx.appfunctions.schema.TranslatorTestUtils
import androidx.test.filters.SdkSuppress
import org.junit.Test

@SdkSuppress(minSdkVersion = 33)
class CreateNoteTranslatorTest {

    private val translatorTestUtils = TranslatorTestUtils(CreateNoteTranslator())

    @Test
    fun upgradeRequest_allFields() {
        val legacyParams =
            LegacyCreateNoteParams(
                title = "Title",
                content = "Content",
                externalId = "legacyExternalId",
                attachments =
                    listOf(
                        LegacyAttachment(
                            displayName = "name",
                            mimeType = "text/html",
                            uri = LegacyUri(uri = "content://xxx")
                        )
                    ),
                folderId = "folderId",
            )

        val expectedUpgradedParams =
            CreateNoteAppFunctionParams(
                title = "Title",
                content = "Content",
                externalUuid = "legacyExternalId",
                attachments =
                    listOf(
                        AttachmentImpl(
                            displayName = "name",
                            mimeType = "text/html",
                            uri = Uri.parse("content://xxx")
                        )
                    ),
                groupId = "folderId",
            )
        translatorTestUtils.assertUpgradeRequestTranslation(
            "createNoteParams",
            legacyParams,
            expectedUpgradedParams
        )
    }

    @Test
    fun upgradeRequest_optionalFieldsNotSet() {
        val legacyParams =
            LegacyCreateNoteParams(
                title = "Title",
            )

        val expectedUpgradedParams =
            CreateNoteAppFunctionParams(
                title = "Title",
                content = null,
                externalUuid = null,
                attachments = emptyList()
            )
        translatorTestUtils.assertUpgradeRequestTranslation(
            "createNoteParams",
            legacyParams,
            expectedUpgradedParams
        )
    }

    @Test
    fun downgradeRequest_allFields() {
        val jetpackParams =
            CreateNoteAppFunctionParams(
                title = "Title",
                content = "Content",
                externalUuid = "externalId",
                attachments =
                    listOf(
                        AttachmentImpl(
                            displayName = "name",
                            mimeType = "text/html",
                            uri = Uri.parse("content://xxx")
                        )
                    ),
                groupId = "groupId"
            )

        val expectedDowngradedParams =
            LegacyCreateNoteParams(
                title = "Title",
                content = "Content",
                externalId = "externalId",
                attachments =
                    listOf(
                        LegacyAttachment(
                            displayName = "name",
                            mimeType = "text/html",
                            uri = LegacyUri(uri = "content://xxx")
                        )
                    ),
                folderId = "groupId",
            )
        translatorTestUtils.assertDowngradeRequestTranslation(
            "createNoteParams",
            jetpackParams,
            expectedDowngradedParams
        )
    }

    @Test
    fun downgradeRequest_nullableFieldsNotSet() {
        val jetpackParams =
            CreateNoteAppFunctionParams(
                title = "Title",
                content = null,
                externalUuid = null,
                attachments = emptyList(),
                groupId = null,
            )

        val expectedDowngradedParams = LegacyCreateNoteParams(title = "Title")
        translatorTestUtils.assertDowngradeRequestTranslation(
            "createNoteParams",
            jetpackParams,
            expectedDowngradedParams
        )
    }

    @Test
    fun upgradeResponse_allFields() {
        val legacyNote =
            LegacyNote(
                id = "id",
                title = "title",
                content = "content",
                attachments =
                    listOf(
                        LegacyAttachment(
                            displayName = "name",
                            mimeType = "text/html",
                            uri = LegacyUri(uri = "content://xxx")
                        )
                    ),
                folderId = "folderId"
            )

        val expectedUpgradedResponse =
            CreateNoteAppFunctionResponse(
                createdNote =
                    AppFunctionNoteImpl(
                        id = "id",
                        title = "title",
                        content = "content",
                        attachments =
                            listOf(
                                AttachmentImpl(
                                    displayName = "name",
                                    mimeType = "text/html",
                                    uri = Uri.parse("content://xxx")
                                )
                            ),
                        groupId = "folderId"
                    )
            )
        translatorTestUtils.assertUpgradeResponseTranslation(legacyNote, expectedUpgradedResponse)
    }

    @Test
    fun upgradeResponse_optionalFieldsNotSet() {
        val legacyNote =
            LegacyNote(
                id = "id",
                title = "title",
            )

        val expectedUpgradedResponse =
            CreateNoteAppFunctionResponse(
                createdNote =
                    AppFunctionNoteImpl(
                        id = "id",
                        title = "title",
                        content = null,
                        attachments = emptyList()
                    )
            )
        translatorTestUtils.assertUpgradeResponseTranslation(legacyNote, expectedUpgradedResponse)
    }

    @Test
    fun downgradeResponse_allFields() {
        val jetpackResponse =
            CreateNoteAppFunctionResponse(
                createdNote =
                    AppFunctionNoteImpl(
                        id = "id",
                        title = "title",
                        content = "content",
                        attachments =
                            listOf(
                                AttachmentImpl(
                                    displayName = "name",
                                    mimeType = "text/html",
                                    uri = Uri.parse("content://xxx")
                                )
                            ),
                        groupId = "groupId",
                    )
            )

        val expectedDowngradedResponse =
            LegacyNote(
                id = "id",
                title = "title",
                content = "content",
                attachments =
                    listOf(
                        LegacyAttachment(
                            displayName = "name",
                            mimeType = "text/html",
                            uri = LegacyUri(uri = "content://xxx")
                        )
                    ),
                folderId = "groupId",
            )
        translatorTestUtils.assertDowngradeResponseTranslation(
            jetpackResponse,
            expectedDowngradedResponse
        )
    }

    @Test
    fun downgradeResponse_optionalFieldsNotSet() {
        val jetpackResponse =
            CreateNoteAppFunctionResponse(
                createdNote =
                    AppFunctionNoteImpl(
                        id = "id",
                        title = "title",
                    )
            )

        val expectedDowngradedResponse = LegacyNote(id = "id", title = "title")
        translatorTestUtils.assertDowngradeResponseTranslation(
            jetpackResponse,
            expectedDowngradedResponse
        )
    }
}
