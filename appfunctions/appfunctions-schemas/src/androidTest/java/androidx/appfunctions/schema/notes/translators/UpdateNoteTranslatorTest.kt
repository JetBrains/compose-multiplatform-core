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
import androidx.appfunctions.LegacyNote
import androidx.appfunctions.LegacySetAttachmentListField
import androidx.appfunctions.LegacySetStringField
import androidx.appfunctions.LegacySetStringNullableField
import androidx.appfunctions.LegacyUpdateNoteParams
import androidx.appfunctions.LegacyUri
import androidx.appfunctions.schema.TranslatorTestUtils
import androidx.appfunctions.schema.types.SetField
import androidx.test.filters.SdkSuppress
import org.junit.Test

@SdkSuppress(minSdkVersion = 33)
class UpdateNoteTranslatorTest {

    private val translatorTestUtils = TranslatorTestUtils(UpdateNoteTranslator())

    @Test
    fun upgradeRequest_allFields() {
        val legacyParams =
            LegacyUpdateNoteParams(
                noteId = "noteId",
                title = LegacySetStringField(value = "newTitle"),
                content = LegacySetStringNullableField(value = "newContent"),
                attachments =
                    LegacySetAttachmentListField(
                        value =
                            listOf(
                                LegacyAttachment(
                                    displayName = "name",
                                    mimeType = "text/html",
                                    uri = LegacyUri(uri = "content://xxx")
                                )
                            ),
                    ),
            )

        val expectedUpgradedParams =
            UpdateNoteAppFunctionParams(
                noteId = "noteId",
                title = SetField("newTitle"),
                content = SetField("newContent"),
                attachments =
                    SetField(
                        listOf(
                            AttachmentImpl(
                                displayName = "name",
                                mimeType = "text/html",
                                uri = Uri.parse("content://xxx")
                            )
                        )
                    ),
            )
        translatorTestUtils.assertUpgradeRequestTranslation(
            "updateNoteParams",
            legacyParams,
            expectedUpgradedParams
        )
    }

    @Test
    fun upgradeRequest_optionalFieldsNotSet() {
        val legacyParams = LegacyUpdateNoteParams(noteId = "noteId")

        val expectedUpgradedParams =
            UpdateNoteAppFunctionParams(
                noteId = "noteId",
                title = null,
                content = null,
                attachments = null,
            )
        translatorTestUtils.assertUpgradeRequestTranslation(
            "updateNoteParams",
            legacyParams,
            expectedUpgradedParams
        )
    }

    @Test
    fun upgradeRequest_nullableSetFieldType_setToNull() {
        val legacyParams =
            LegacyUpdateNoteParams(
                noteId = "noteId",
                content = LegacySetStringNullableField(value = null)
            )

        val expectedUpgradedParams =
            UpdateNoteAppFunctionParams(
                noteId = "noteId",
                content = SetField(null),
            )
        translatorTestUtils.assertUpgradeRequestTranslation(
            "updateNoteParams",
            legacyParams,
            expectedUpgradedParams
        )
    }

    @Test
    fun downgradeRequest_allFields() {
        val jetpackParams =
            UpdateNoteAppFunctionParams(
                noteId = "noteId",
                title = SetField("newTitle"),
                content = SetField("newContent"),
                attachments =
                    SetField(
                        listOf(
                            AttachmentImpl(
                                displayName = "name",
                                mimeType = "text/html",
                                uri = Uri.parse("content://xxx")
                            )
                        )
                    ),
            )

        val expectedDowngradedParams =
            LegacyUpdateNoteParams(
                noteId = "noteId",
                title = LegacySetStringField(value = "newTitle"),
                content = LegacySetStringNullableField(value = "newContent"),
                attachments =
                    LegacySetAttachmentListField(
                        value =
                            listOf(
                                LegacyAttachment(
                                    displayName = "name",
                                    mimeType = "text/html",
                                    uri = LegacyUri(uri = "content://xxx")
                                )
                            )
                    ),
            )
        translatorTestUtils.assertDowngradeRequestTranslation(
            "updateNoteParams",
            jetpackParams,
            expectedDowngradedParams
        )
    }

    @Test
    fun downgradeRequest_nullableFieldsNotSet() {
        val jetpackParams =
            UpdateNoteAppFunctionParams(
                noteId = "noteId",
                title = null,
                content = null,
                attachments = null,
            )

        val expectedDowngradedParams = LegacyUpdateNoteParams(noteId = "noteId")
        translatorTestUtils.assertDowngradeRequestTranslation(
            "updateNoteParams",
            jetpackParams,
            expectedDowngradedParams
        )
    }

    @Test
    fun downgradeRequest_nullableSetFieldType_setToNull() {
        val jetpackParams =
            UpdateNoteAppFunctionParams(
                noteId = "noteId",
                content = SetField(null),
            )

        val expectedDowngradedParams =
            LegacyUpdateNoteParams(
                noteId = "noteId",
                content = LegacySetStringNullableField(value = null)
            )
        translatorTestUtils.assertDowngradeRequestTranslation(
            "updateNoteParams",
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
            UpdateNoteAppFunctionResponse(
                updatedNote =
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
            UpdateNoteAppFunctionResponse(
                updatedNote =
                    AppFunctionNoteImpl(
                        id = "id",
                        title = "title",
                        content = null,
                        attachments = emptyList(),
                    )
            )
        translatorTestUtils.assertUpgradeResponseTranslation(legacyNote, expectedUpgradedResponse)
    }

    @Test
    fun downgradeResponse_allFields() {
        val jetpackResponse =
            UpdateNoteAppFunctionResponse(
                updatedNote =
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
            UpdateNoteAppFunctionResponse(
                updatedNote =
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
