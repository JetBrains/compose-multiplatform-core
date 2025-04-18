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

package androidx.appfunctions.schema.notes

import android.net.Uri
import androidx.annotation.RestrictTo
import androidx.annotation.RestrictTo.Scope.LIBRARY_GROUP
import androidx.appfunctions.AppFunctionContext
import androidx.appfunctions.AppFunctionOpenable
import androidx.appfunctions.AppFunctionSchemaDefinition
import androidx.appfunctions.schema.types.SetField

// TODO(b/401517540): Add remaining APIs: folder APIs
/**
 * The category name of Notes related app functions.
 *
 * Example of apps that can support this category of schema include note taking apps.
 *
 * The category is used to search app functions related to notes, using
 * [androidx.appfunctions.AppFunctionSearchSpec.schemaCategory].
 */
public const val APP_FUNCTION_SCHEMA_CATEGORY_NOTES: String = "notes"

/** Finds [AppFunctionNote]s with the given search criteria specified in [Parameters]. */
@AppFunctionSchemaDefinition(
    name = "findNotes",
    version = FindNotesAppFunction.SCHEMA_VERSION,
    category = APP_FUNCTION_SCHEMA_CATEGORY_NOTES
)
public interface FindNotesAppFunction<
    Parameters : FindNotesAppFunction.Parameters,
    Response : FindNotesAppFunction.Response
> {
    /**
     * Finds notes with the given search criteria.
     *
     * The implementing app should throw an appropriate [androidx.appfunctions.AppFunctionException]
     * in exceptional cases.
     *
     * @param appFunctionContext The AppFunction execution context.
     * @param parameters The parameters for finding notes.
     * @return The response including the list of notes that match the parameters, or an empty list
     *   if no match is found.
     */
    public suspend fun findNotes(
        appFunctionContext: AppFunctionContext,
        parameters: Parameters,
    ): Response

    /** The parameters for finding notes. */
    // TODO(b/401517540): Add start and end dates when DateTime is supported.
    public interface Parameters {
        /**
         * The search query to be processed. A null value means to query all notes with the
         * remaining parameters.
         *
         * This parameter is analogous to the caller typing a query into a search box. The app
         * providing the app function has full control over how the query is interpreted and
         * processed; for example by name matching or semantic analysis.
         */
        public val query: String?
            get() = null
    }

    /** The response including the list of notes that match the parameters. */
    public interface Response {
        /** The list of notes that match the parameters, or an empty list if no match is found. */
        public val notes: List<AppFunctionNote>
    }

    public companion object {
        /** Current schema version. */
        @RestrictTo(LIBRARY_GROUP) internal const val SCHEMA_VERSION: Int = 2
    }
}

/** Creates an [AppFunctionNote] with the given parameters. */
@AppFunctionSchemaDefinition(
    name = "createNote",
    version = CreateNoteAppFunction.SCHEMA_VERSION,
    category = APP_FUNCTION_SCHEMA_CATEGORY_NOTES
)
public interface CreateNoteAppFunction<
    Parameters : CreateNoteAppFunction.Parameters,
    Response : CreateNoteAppFunction.Response
> {
    /**
     * Creates an [AppFunctionNote] with the given parameters.
     *
     * The implementing app should throw an appropriate subclass of
     * [androidx.appfunctions.AppFunctionException] in exceptional cases.
     *
     * @param appFunctionContext The AppFunction execution context.
     * @param parameters The parameters for creating a note.
     * @return The response including the created note.
     */
    public suspend fun createNote(
        appFunctionContext: AppFunctionContext,
        parameters: Parameters,
    ): Response

    /** The parameters for creating a note. */
    public interface Parameters {
        /** The title of the note. */
        public val title: String
        /** The text content of the note. */
        public val content: String?
            get() = null

        /** The attachments of the note. */
        public val attachments: List<AppFunctionNote.Attachment>
            get() = emptyList()

        /**
         * The ID of the group the note is in, if any else `null`.
         *
         * [androidx.appfunctions.AppFunctionElementNotFoundException] should be thrown when a group
         * with the specified groupId doesn't exist.
         */
        public val groupId: String?
            get() = null

        /**
         * An optional UUID for this note provided by the caller. If provided, the caller can use
         * this UUID as well as the returned [AppFunctionNote.id] to reference this specific note in
         * subsequent requests, such as a request to update the note that was just created.
         *
         * To support [externalUuid], the application should maintain a mapping between the
         * [externalUuid] and the internal id of this note. This allows the application to retrieve
         * the correct note when the caller references it using the provided `externalUuid` in
         * subsequent requests.
         *
         * If the `externalUuid` is not provided by the caller in the creation request, the
         * application should expect subsequent requests from the caller to reference the note using
         * the application generated [AppFunctionNote.id].
         */
        public val externalUuid: String?
            get() = null
    }

    /** The response including the created note. */
    public interface Response {
        /** The created note. */
        public val createdNote: AppFunctionNote
    }

    public companion object {
        /** Current schema version. */
        @RestrictTo(LIBRARY_GROUP) internal const val SCHEMA_VERSION: Int = 2
    }
}

/** Updates an existing [AppFunctionNote]. */
@AppFunctionSchemaDefinition(
    name = "updateNote",
    version = UpdateNoteAppFunction.SCHEMA_VERSION,
    category = APP_FUNCTION_SCHEMA_CATEGORY_NOTES
)
public interface UpdateNoteAppFunction<
    Parameters : UpdateNoteAppFunction.Parameters,
    Response : UpdateNoteAppFunction.Response
> {
    /**
     * Updates an existing [AppFunctionNote] with the given parameters.
     *
     * For each field in [Parameters], if the corresponding [SetField] is not null, the note's field
     * will be updated. The value within the [SetField] will be used to update the original value.
     * Fields with a null [SetField] will not be updated.
     *
     * The implementing app should throw an appropriate subclass of
     * [androidx.appfunctions.AppFunctionException] in exceptional cases.
     *
     * @param appFunctionContext The AppFunction execution context.
     * @param parameters The parameters defining the note to update and the new values.
     * @return The response including the updated note.
     */
    public suspend fun updateNote(
        appFunctionContext: AppFunctionContext,
        parameters: Parameters,
    ): Response

    /** The parameters for updating a note. */
    public interface Parameters {
        /**
         * The ID of the note to update. It can be the ID generated by the application
         * ([AppFunctionNote.id]) or an external UUID
         * ([CreateNoteAppFunction.Parameters.externalUuid]) provided by the caller during note
         * creation.
         */
        public val noteId: String

        /** The new title for the note, if it should be updated. */
        public val title: SetField<String>?
            get() = null

        /** The new content for the note, if it should be updated. */
        public val content: SetField<String?>?
            get() = null

        /** The new attachments for the note, if it should be updated. */
        public val attachments: SetField<List<AppFunctionNote.Attachment>>?
            get() = null
    }

    /** The response including the updated note. */
    public interface Response {
        /** The updated note. */
        public val updatedNote: AppFunctionNote
    }

    public companion object {
        /** Current schema version. */
        @RestrictTo(LIBRARY_GROUP) internal const val SCHEMA_VERSION: Int = 2
    }
}

/** Deletes the notes with the given search criteria, defined by {@code parameters}. */
@AppFunctionSchemaDefinition(
    name = "deleteNotes",
    version = DeleteNotesAppFunction.SCHEMA_VERSION,
    category = APP_FUNCTION_SCHEMA_CATEGORY_NOTES
)
public interface DeleteNotesAppFunction<
    Parameters : DeleteNotesAppFunction.Parameters,
    Response : DeleteNotesAppFunction.Response
> {
    /**
     * Deletes the notes with the given search criteria and returns the IDs of the notes that were
     * successfully deleted.
     *
     * The implementing app should throw an appropriate subclass of
     * [androidx.appfunctions.AppFunctionException] in exceptional cases, except for failures in
     * deleting individual noteId(s), which can be ignored when returning the
     * [DeleteNotesAppFunction.Response].
     *
     * @param appFunctionContext The AppFunction execution context.
     * @param parameters The parameters defining the criteria of notes to delete.
     * @return A list of successfully deleted note IDs.
     */
    public suspend fun deleteNotes(
        appFunctionContext: AppFunctionContext,
        parameters: Parameters,
    ): Response

    /** The parameters defining the criteria of notes to delete. */
    public interface Parameters {
        /** The IDs of the notes to delete. */
        public val noteIds: List<String>
    }

    /** The response of delete notes request. */
    public interface Response {
        /** A list of successfully deleted note IDs. */
        public val noteIds: List<String>
    }

    public companion object {
        /** Current schema version. */
        @RestrictTo(LIBRARY_GROUP) internal const val SCHEMA_VERSION: Int = 2
    }
}

/** Gets [AppFunctionNote]s with the given IDs. */
@AppFunctionSchemaDefinition(
    name = "getNotes",
    version = GetNotesAppFunction.SCHEMA_VERSION,
    category = APP_FUNCTION_SCHEMA_CATEGORY_NOTES
)
public interface GetNotesAppFunction<
    Parameters : GetNotesAppFunction.Parameters,
    Response : GetNotesAppFunction.Response
> {
    /**
     * Gets the notes with the given IDs. Returns only the notes found for the provided IDs. Does
     * not throw if some IDs are not found.
     *
     * The implementing app should throw an appropriate subclass of
     * [androidx.appfunctions.AppFunctionException] in exceptional cases.
     *
     * @param appFunctionContext The AppFunction execution context.
     * @param parameters The parameters defining which notes to get.
     * @return The response including the list of notes that match the given IDs.
     */
    public suspend fun getNotes(
        appFunctionContext: AppFunctionContext,
        parameters: Parameters,
    ): Response

    /** The parameters defining the IDs of the notes to get. */
    public interface Parameters {
        /** The IDs of the notes to get. */
        public val noteIds: List<String>
    }

    /** The response including the list of notes that match the given IDs. */
    public interface Response {
        /** The list of notes that match the given IDs. */
        public val notes: List<AppFunctionNote>
    }

    public companion object {
        /** Current schema version. */
        @RestrictTo(LIBRARY_GROUP) internal const val SCHEMA_VERSION: Int = 2
    }
}

/**
 * Shows the note with the given parameters.
 *
 * @param Parameters The parameters of the note to show.
 * @param Response The response including the [AppFunctionOpenable] to show the note.
 */
@AppFunctionSchemaDefinition(
    name = "showNote",
    version = ShowNoteAppFunction.SCHEMA_VERSION,
    category = APP_FUNCTION_SCHEMA_CATEGORY_NOTES
)
public interface ShowNoteAppFunction<
    Parameters : ShowNoteAppFunction.Parameters,
    Response : ShowNoteAppFunction.Response
> {
    /**
     * Shows the note with the given parameters.
     *
     * The implementing app should throw an appropriate subclass of
     * [androidx.appfunctions.AppFunctionException] in exceptional cases.
     *
     * @param appFunctionContext The AppFunction execution context.
     * @param parameters The params of the note to show.
     * @return The response including the intent to show the note.
     */
    public suspend fun showNote(
        appFunctionContext: AppFunctionContext,
        parameters: Parameters
    ): Response

    /** The parameters for [showNote]. */
    public interface Parameters {
        /**
         * The [AppFunctionNote.id] of the note to show.
         *
         * [androidx.appfunctions.AppFunctionElementNotFoundException] should be thrown when a note
         * with the specified noteId doesn't exist.
         */
        public val noteId: String
    }

    /** The [AppFunctionOpenable] response for [showNote]. */
    public interface Response : AppFunctionOpenable

    public companion object {
        /** Current schema version. */
        @RestrictTo(LIBRARY_GROUP) internal const val SCHEMA_VERSION: Int = 2
    }
}

/** Finds notes groups with the given search criteria. */
@AppFunctionSchemaDefinition(
    name = "findNotesGroups",
    version = FindNotesGroupsAppFunction.SCHEMA_VERSION,
    category = APP_FUNCTION_SCHEMA_CATEGORY_NOTES
)
public interface FindNotesGroupsAppFunction<
    Parameters : FindNotesGroupsAppFunction.Parameters,
    Response : FindNotesGroupsAppFunction.Response
> {
    /**
     * Finds notes groups with the given search criteria.
     *
     * The implementing app should throw an appropriate subclass of
     * [androidx.appfunctions.AppFunctionException] in exceptional cases.
     *
     * @param appFunctionContext The AppFunction execution context.
     * @param parameters The parameters for finding groups.
     * @return The response including the list of groups that match the parameters, or an empty list
     *   if no match is found.
     */
    public suspend fun findNotesGroups(
        appFunctionContext: AppFunctionContext,
        parameters: Parameters,
    ): Response

    /** The parameters for finding groups. */
    public interface Parameters {
        /**
         * The query to search for groups. A null value means to query all groups.
         *
         * This parameter is analogous to the caller typing a query into a search box. The app
         * providing the app function has full control over how the query is interpreted and
         * processed; for example by name matching or semantic analysis.
         */
        public val query: String?
            get() = null
    }

    /** The response including the list of groups that match the parameters. */
    public interface Response {
        /** The list of groups that match the parameters, or an empty list if no match is found. */
        public val groups: List<AppFunctionNotesGroup>
    }

    public companion object {

        /** Current schema version. */
        @RestrictTo(LIBRARY_GROUP) internal const val SCHEMA_VERSION: Int = 2
    }
}

/** Creates a group with the given parameters. */
@AppFunctionSchemaDefinition(
    name = "createNotesGroup",
    version = CreateNotesGroupAppFunction.SCHEMA_VERSION,
    category = APP_FUNCTION_SCHEMA_CATEGORY_NOTES
)
public interface CreateNotesGroupAppFunction<
    Parameters : CreateNotesGroupAppFunction.Parameters,
    Response : CreateNotesGroupAppFunction.Response
> {
    /**
     * Creates a notes group with the given parameters.
     *
     * The implementing app should throw an appropriate subclass of
     * [androidx.appfunctions.AppFunctionException] in exceptional cases.
     *
     * @param appFunctionContext The AppFunction execution context.
     * @param parameters The parameters for creating a group.
     * @return The created group.
     */
    public suspend fun createNotesGroup(
        appFunctionContext: AppFunctionContext,
        parameters: Parameters,
    ): Response

    /** The parameters for creating a group. */
    public interface Parameters {
        /** The name of the group. */
        public val name: String
    }

    /** The response including the created group. */
    public interface Response {
        /** The created group. */
        public val createdGroup: AppFunctionNotesGroup
    }

    public companion object {

        /** Current schema version. */
        @RestrictTo(LIBRARY_GROUP) internal const val SCHEMA_VERSION: Int = 2
    }
}

/** Deletes groups with the given parameters. */
@AppFunctionSchemaDefinition(
    name = "deleteNotesGroups",
    version = DeleteNotesGroupsAppFunction.SCHEMA_VERSION,
    category = APP_FUNCTION_SCHEMA_CATEGORY_NOTES
)
public interface DeleteNotesGroupsAppFunction<
    Parameters : DeleteNotesGroupsAppFunction.Parameters,
    Response : DeleteNotesGroupsAppFunction.Response
> {
    /**
     * Deletes notes groups with the given parameters and returns the IDs of the groups that were
     * successfully deleted.
     *
     * The implementing app should throw an appropriate subclass of
     * [androidx.appfunctions.AppFunctionException] in exceptional cases, except for failures in
     * deleting individual groupId(s), which can be ignored when returning the
     * [DeleteNotesGroupsAppFunction.Response].
     *
     * @param appFunctionContext The AppFunction execution context.
     * @param parameters The parameters of the groups to delete.
     * @return The response including the list of successfully deleted group IDs.
     */
    public suspend fun deleteNotesGroups(
        appFunctionContext: AppFunctionContext,
        parameters: Parameters,
    ): Response

    /** The parameters of the groups to delete. */
    public interface Parameters {
        /** The [AppFunctionNotesGroup.id] of the groups to delete. */
        public val groupIds: List<String>
    }

    /** The response including the list of successfully deleted group IDs. */
    public interface Response {
        /** The IDs of the deleted groups. */
        public val groupIds: List<String>
    }

    public companion object {

        /** Current schema version. */
        @RestrictTo(LIBRARY_GROUP) internal const val SCHEMA_VERSION: Int = 2
    }
}

/** A note entity. */
public interface AppFunctionNote {
    /** The ID of the note. */
    public val id: String

    /** The title of the note. */
    public val title: String

    /** The content of the note. */
    public val content: String?
        get() = null

    /** The attachments of the note. */
    public val attachments: List<Attachment>
        get() = emptyList()

    /** An attached file. */
    public interface Attachment {
        /**
         * The URI of the attached file.
         *
         * When providing an [Uri] to another app, that app must be granted URI permission using
         * [android.content.Context.grantUriPermission] to the receiving app.
         *
         * The providing app should also consider revoking the URI permission by using
         * [android.content.Context.revokeUriPermission] after a certain time period.
         */
        public val uri: Uri

        /** The display name of the attached file. */
        public val displayName: String

        /** The MIME type of the attached file. Format defined in RFC 6838. */
        public val mimeType: String?
            get() = null
    }

    /** The ID of the group the note is in, if any else `null`. */
    public val groupId: String?
        get() = null
}

/** Represents a group used to organize notes. */
public interface AppFunctionNotesGroup {
    /** The ID of the group. */
    public val id: String
    /** The name of the group. */
    public val name: String
}
