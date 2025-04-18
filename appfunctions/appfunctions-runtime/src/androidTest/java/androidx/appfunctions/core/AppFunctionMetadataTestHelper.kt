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

package androidx.appfunctions.core

import android.content.Context
import android.os.Build
import androidx.`annotation`.RequiresApi
import androidx.appfunctions.core.AppFunctionMetadataTestHelper.FunctionIds.NOTES_SCHEMA_PRINT
import androidx.appfunctions.`internal`.readAll
import androidx.appfunctions.metadata.AppFunctionComponentsMetadata
import androidx.appfunctions.metadata.AppFunctionMetadata
import androidx.appfunctions.metadata.AppFunctionMetadataDocument
import androidx.appfunctions.metadata.AppFunctionPrimitiveTypeMetadata
import androidx.appfunctions.metadata.AppFunctionResponseMetadata
import androidx.appfunctions.metadata.AppFunctionSchemaMetadata
import androidx.appsearch.app.Features
import androidx.appsearch.app.GlobalSearchSession
import androidx.appsearch.app.SearchSpec
import androidx.appsearch.platformstorage.PlatformStorage
import androidx.concurrent.futures.await
import kotlinx.coroutines.delay

@RequiresApi(Build.VERSION_CODES.S)
internal class AppFunctionMetadataTestHelper(private val context: Context) {
    suspend fun awaitAppFunctionIndexed(functionIds: Set<String>) {
        awaitRuntimeMetadataAvailable(functionIds)
    }

    private suspend fun awaitRuntimeMetadataAvailable(functionIds: Set<String>) {
        val notFoundIds = mutableSetOf<String>().apply { addAll(functionIds) }

        createSearchSession().use { session ->
            var retry = 0
            while (retry < RETRY_LIMIT) {
                val searchResults =
                    session.search(
                        "",
                        SearchSpec.Builder()
                            .addFilterNamespaces("app_functions_runtime")
                            .addFilterPackageNames("android")
                            .addFilterSchemas("AppFunctionRuntimeMetadata")
                            .build(),
                    )
                var nextPage = searchResults.nextPageAsync.await()
                while (notFoundIds.isNotEmpty() && nextPage.isNotEmpty()) {
                    for (result in nextPage) {
                        val functionId = result.genericDocument.getPropertyString("functionId")
                        if (notFoundIds.contains(functionId)) {
                            notFoundIds.remove(functionId)
                        }
                    }
                    nextPage = searchResults.nextPageAsync.await()
                }
                if (notFoundIds.isEmpty()) {
                    return
                }

                delay(RETRY_DELAY_MS)
                retry += 1
            }
        }
        throw IllegalStateException("AppSearch indexer fail")
    }

    /** Checks if the legacy AppFunction indexer is available on the device. */
    suspend fun isLegacyAppFunctionIndexerAvailable(): Boolean {
        return createSearchSession().use {
            // AppFunctions indexer was shipped with Mobile applications indexer.
            it.features.isFeatureSupported(Features.INDEXER_MOBILE_APPLICATIONS)
        }
    }

    /**
     * Checks if the dynamic indexer is available.
     *
     * Only works if the functions are already indexed. Use [awaitAppFunctionIndexed] for waiting.
     */
    suspend fun isDynamicIndexerAvailable(): Boolean =
        // TODO - Check AppSearch version when new indexer is available in AppSearch.
        createSearchSession().use { session ->
            // Only check for current package functions.
            val metadataDocument =
                session
                    .search(
                        "packageName:\"${context.packageName}\"",
                        SearchSpec.Builder()
                            .addFilterNamespaces("app_functions")
                            .addFilterDocumentClasses(AppFunctionMetadataDocument::class.java)
                            .addFilterPackageNames("android")
                            .setVerbatimSearchEnabled(true)
                            .setNumericSearchEnabled(true)
                            .build()
                    )
                    .readAll { it.genericDocument }
                    .filterNotNull()
                    .first()

            // Check if one of the additional property i.e. response is available. Checking for
            // response as all app functions will always have a response.
            return metadataDocument.getPropertyDocument("response") != null
        }

    private suspend fun createSearchSession(): GlobalSearchSession {
        return PlatformStorage.createGlobalSearchSessionAsync(
                PlatformStorage.GlobalSearchContext.Builder(context).build()
            )
            .await()
    }

    /** List of function ids defined in androidTest/res/xml/app_functions.xml */
    object FunctionIds {
        const val NO_SCHEMA_ENABLED_BY_DEFAULT =
            "androidx.appfunctions.test#noSchema_enabledByDefault"
        const val NO_SCHEMA_DISABLED_BY_DEFAULT =
            "androidx.appfunctions.test#noSchema_disabledByDefault"
        const val NO_SCHEMA_EXECUTION_SUCCEED =
            "androidx.appfunctions.test#noSchema_executionSucceed"
        const val NO_SCHEMA_EXECUTION_FAIL = "androidx.appfunctions.test#noSchema_executionFail"
        const val NOTES_SCHEMA_PRINT = "androidx.appfunctions.test#notesSchema_print"
        const val MEDIA_SCHEMA_PRINT = "androidx.appfunctions.test#mediaSchema_print"
        const val MEDIA_SCHEMA2_PRINT = "androidx.appfunctions.test#mediaSchema2_print"
    }

    object FunctionMetadata {
        val NO_SCHEMA_EXECUTION_SUCCEED =
            AppFunctionMetadata(
                id = FunctionIds.NO_SCHEMA_EXECUTION_SUCCEED,
                packageName = "androidx.appfunctions.runtime.test",
                isEnabled = true,
                schema = null,
                parameters = emptyList(),
                response =
                    AppFunctionResponseMetadata(
                        valueType =
                            AppFunctionPrimitiveTypeMetadata(
                                type = AppFunctionPrimitiveTypeMetadata.TYPE_STRING,
                                isNullable = false
                            )
                    ),
                components = AppFunctionComponentsMetadata()
            )

        val NO_SCHEMA_ENABLED_BY_DEFAULT =
            AppFunctionMetadata(
                id = FunctionIds.NO_SCHEMA_ENABLED_BY_DEFAULT,
                packageName = "androidx.appfunctions.runtime.test",
                isEnabled = true,
                schema = null,
                parameters = emptyList(),
                response =
                    AppFunctionResponseMetadata(
                        valueType =
                            AppFunctionPrimitiveTypeMetadata(
                                type = AppFunctionPrimitiveTypeMetadata.TYPE_UNIT,
                                isNullable = false
                            )
                    ),
                components = AppFunctionComponentsMetadata()
            )

        val NO_SCHEMA_DISABLED_BY_DEFAULT =
            AppFunctionMetadata(
                id = FunctionIds.NO_SCHEMA_DISABLED_BY_DEFAULT,
                packageName = "androidx.appfunctions.runtime.test",
                isEnabled = false,
                schema = null,
                parameters = emptyList(),
                response =
                    AppFunctionResponseMetadata(
                        valueType =
                            AppFunctionPrimitiveTypeMetadata(
                                type = AppFunctionPrimitiveTypeMetadata.TYPE_UNIT,
                                isNullable = false
                            )
                    ),
                components = AppFunctionComponentsMetadata()
            )

        val MEDIA_SCHEMA2_PRINT =
            AppFunctionMetadata(
                id = FunctionIds.MEDIA_SCHEMA2_PRINT,
                packageName = "androidx.appfunctions.runtime.test",
                isEnabled = true,
                schema = AppFunctionSchemaMetadata(category = "media", name = "print", version = 2),
                parameters = emptyList(),
                response =
                    AppFunctionResponseMetadata(
                        valueType =
                            AppFunctionPrimitiveTypeMetadata(
                                type = AppFunctionPrimitiveTypeMetadata.TYPE_UNIT,
                                isNullable = false
                            )
                    ),
                components = AppFunctionComponentsMetadata()
            )

        val MEDIA_SCHEMA_PRINT =
            AppFunctionMetadata(
                id = FunctionIds.MEDIA_SCHEMA_PRINT,
                packageName = "androidx.appfunctions.runtime.test",
                isEnabled = true,
                schema = AppFunctionSchemaMetadata(category = "media", name = "print", version = 1),
                parameters = emptyList(),
                response =
                    AppFunctionResponseMetadata(
                        valueType =
                            AppFunctionPrimitiveTypeMetadata(
                                type = AppFunctionPrimitiveTypeMetadata.TYPE_UNIT,
                                isNullable = false
                            )
                    ),
                components = AppFunctionComponentsMetadata()
            )

        val NOTES_SCHEMA_PRINT =
            AppFunctionMetadata(
                id = FunctionIds.NOTES_SCHEMA_PRINT,
                packageName = "androidx.appfunctions.runtime.test",
                isEnabled = true,
                schema = AppFunctionSchemaMetadata(category = "notes", name = "print", version = 1),
                parameters = emptyList(),
                response =
                    AppFunctionResponseMetadata(
                        valueType =
                            AppFunctionPrimitiveTypeMetadata(
                                type = AppFunctionPrimitiveTypeMetadata.TYPE_UNIT,
                                isNullable = false
                            )
                    ),
                components = AppFunctionComponentsMetadata()
            )

        val NO_SCHEMA_EXECUTION_FAIL =
            AppFunctionMetadata(
                id = FunctionIds.NO_SCHEMA_EXECUTION_FAIL,
                packageName = "androidx.appfunctions.runtime.test",
                isEnabled = true,
                schema = null,
                parameters = emptyList(),
                response =
                    AppFunctionResponseMetadata(
                        valueType =
                            AppFunctionPrimitiveTypeMetadata(
                                type = AppFunctionPrimitiveTypeMetadata.TYPE_UNIT,
                                isNullable = false
                            )
                    ),
                components = AppFunctionComponentsMetadata()
            )
    }

    companion object {
        private const val RETRY_LIMIT = 5
        private const val RETRY_DELAY_MS = 500L
    }
}
