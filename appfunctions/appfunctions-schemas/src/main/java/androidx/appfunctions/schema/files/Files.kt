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

package androidx.appfunctions.schema.files

import android.net.Uri
import androidx.annotation.StringDef
import androidx.appfunctions.AppFunctionContext
import androidx.appfunctions.AppFunctionSchemaDefinition

// TODO(b/401518165): Add ShowFile app function.
/**
 * The category name of Files-related app functions.
 *
 * Example of apps that can support this schema category include file management apps.
 *
 * The category is used to search app functions related to files, using
 * [androidx.appfunctions.AppFunctionSearchSpec.schemaCategory].
 */
public const val APP_FUNCTION_SCHEMA_CATEGORY_FILES: String = "files"

/** Searches for files based on the specified criteria. */
@AppFunctionSchemaDefinition(
    name = "findFiles",
    version = FindFilesAppFunction.SCHEMA_VERSION,
    category = APP_FUNCTION_SCHEMA_CATEGORY_FILES
)
public interface FindFilesAppFunction<
    Parameters : FindFilesAppFunction.Parameters,
    Response : FindFilesAppFunction.Response
> {
    /**
     * Searches for files based on the specified criteria.
     *
     * @param appFunctionContext The AppFunction execution context.
     * @param params The parameters defining the search criteria for files.
     * @return The response containing a list of files matching the search criteria.
     * @throws androidx.appfunctions.AppFunctionPermissionRequiredException if the target
     *   application lacks the necessary permission to access the content, e.g.
     *   [android.Manifest.permission.READ_EXTERNAL_STORAGE] for accessing external storage.
     */
    public suspend fun findFiles(
        appFunctionContext: AppFunctionContext,
        params: Parameters,
    ): Response

    /** The parameters for [findFiles]. */
    // TODO(b/401518165): Add pagination
    public interface Parameters {
        /**
         * The search query to be processed. A null value means to query all files with the
         * remaining params.
         *
         * This parameter is analogous to the caller typing a query into a search box. The app
         * providing the app function has full control over how the query is interpreted and
         * processed; for example by name matching or semantic analysis.
         */
        public val query: String?
            get() = null

        /** The types of the files to return. An empty list means all types are allowed. */
        @FileType
        public val fileTypes: List<String>
            get() = listOf<String>()
    }

    /** The response of [findFiles]. */
    public interface Response {
        /** The files matching the query. */
        public val files: List<AppFunctionFile>
    }

    public companion object {
        /** Current schema version. */
        public const val SCHEMA_VERSION: Int = 2
    }
}

/** Retrieves content URIs for the requested files. */
@AppFunctionSchemaDefinition(
    name = "getFileContentUris",
    version = GetFileContentUrisAppFunction.SCHEMA_VERSION,
    category = APP_FUNCTION_SCHEMA_CATEGORY_FILES
)
public interface GetFileContentUrisAppFunction<
    Parameters : GetFileContentUrisAppFunction.Parameters,
    Response : GetFileContentUrisAppFunction.Response
> {
    /**
     * Retrieves content URIs for the requested files.
     *
     * Implementing apps must grant URI permission using
     * [android.content.Context.grantUriPermission] to the caller. The caller can be obtained by
     * [AppFunctionContext.callingPackageName].
     *
     * The app should also consider revoking the URI permission by using
     * [android.content.Context.revokeUriPermission] after a certain time period (e.g. 10 mins). See
     * [androidx.work.WorkManager] for scheduling persistent work.
     *
     * @param appFunctionContext The AppFunction execution context.
     * @param params The parameters specifying the files for which content URIs are requested.
     * @return The response including URIs of the files that successfully matched the query.
     * @throws androidx.appfunctions.AppFunctionPermissionRequiredException if the target
     *   application lacks the necessary permission to access the content.
     */
    public suspend fun getFileContentUris(
        appFunctionContext: AppFunctionContext,
        params: Parameters,
    ): Response

    /** The parameters for [getFileContentUris]. */
    public interface Parameters {
        /**
         * The [AppFunctionFile.id]s of files to find the content URIs for.
         *
         * This should only include files that will be opened, as each file may require generating a
         * unique permission resource.
         */
        public val fileIds: List<String>
    }

    /**
     * The response of [getFileContentUris] including [FileContentUri]s of files successfully
     * retrieved.
     */
    public interface Response {
        /** The list of [FileContentUri]s of the successfully retrieved files. */
        public val fileContentUris: List<FileContentUri>
    }

    /** A mapping of [AppFunctionFile.id] to its content URI. */
    public interface FileContentUri {
        /** The [AppFunctionFile.id] of the file. */
        public val id: String
        /**
         * The URI of the file's content.
         *
         * When providing an [Uri] to another app, that app must be granted URI permission using
         * [android.content.Context.grantUriPermission] to the receiving app.
         *
         * The providing app should also consider revoking the URI permission by using
         * [android.content.Context.revokeUriPermission] after a certain time period.
         */
        public val uri: Uri
    }

    public companion object {
        /** Current schema version. */
        public const val SCHEMA_VERSION: Int = 2
    }
}

/** A file entity. This can be retrieved from [FindFilesAppFunction.findFiles]. */
// TODO(b/401518165): Add dateCreated when DateTime is supported.
public interface AppFunctionFile {
    /** The ID of the file. */
    public val id: String
    /** The name of the file. */
    public val name: String
}

/** The type of the file. */
@StringDef(FileType.IMAGE, FileType.VIDEO, FileType.AUDIO, FileType.DOCUMENT)
@Retention(AnnotationRetention.SOURCE)
private annotation class FileType {
    companion object {
        /** The file type is image. */
        const val IMAGE: String = "IMAGE"

        /** The file type is video. */
        const val VIDEO: String = "VIDEO"

        /** The file type is audio. */
        const val AUDIO: String = "AUDIO"

        /** The file type is document. */
        const val DOCUMENT: String = "DOCUMENT"
    }
}
