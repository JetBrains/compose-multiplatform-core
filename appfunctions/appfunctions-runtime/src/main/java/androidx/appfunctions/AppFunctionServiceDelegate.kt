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

package androidx.appfunctions

import android.content.Context
import android.os.Build
import android.os.OutcomeReceiver
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.appfunctions.internal.AggregatedAppFunctionInventory
import androidx.appfunctions.internal.AggregatedAppFunctionInvoker
import androidx.appfunctions.internal.Constants.APP_FUNCTIONS_TAG
import androidx.appfunctions.internal.Translator
import androidx.appfunctions.internal.TranslatorSelector
import androidx.appfunctions.internal.unsafeBuildReturnValue
import androidx.appfunctions.internal.unsafeGetParameterValue
import androidx.appfunctions.metadata.AppFunctionSchemaMetadata
import androidx.appfunctions.metadata.CompileTimeAppFunctionMetadata
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal class AppFunctionServiceDelegate(
    context: Context,
    workerCoroutineContext: CoroutineContext,
    private val mainCoroutineContext: CoroutineContext,
    private val aggregatedInventory: AggregatedAppFunctionInventory,
    private val aggregatedInvoker: AggregatedAppFunctionInvoker,
    private val translatorSelector: TranslatorSelector
) {

    private val job = Job()
    private val workerCoroutineScope = CoroutineScope(workerCoroutineContext + job)
    private val appContext = context.applicationContext

    internal fun onExecuteFunction(
        executeAppFunctionRequest: ExecuteAppFunctionRequest,
        callingPackageName: String,
        callback: OutcomeReceiver<ExecuteAppFunctionResponse, AppFunctionException>,
    ): Job =
        workerCoroutineScope.launch {
            try {
                val appFunctionMetadata =
                    aggregatedInventory.functionIdToMetadataMap[
                            executeAppFunctionRequest.functionIdentifier]
                if (appFunctionMetadata == null) {
                    Log.d(
                        APP_FUNCTIONS_TAG,
                        "${executeAppFunctionRequest.functionIdentifier} is not available"
                    )
                    callback.onError(
                        AppFunctionFunctionNotFoundException(
                            "${executeAppFunctionRequest.functionIdentifier} is not available"
                        )
                    )
                    return@launch
                }
                val translator =
                    getTranslator(executeAppFunctionRequest, appFunctionMetadata.schema)

                val parameters =
                    extractParameters(executeAppFunctionRequest, appFunctionMetadata, translator)
                callback.onResult(
                    unsafeInvokeFunction(
                        executeAppFunctionRequest,
                        callingPackageName,
                        appFunctionMetadata,
                        parameters,
                        translator
                    )
                )
            } catch (e: CancellationException) {
                callback.onError(AppFunctionCancelledException(e.message))
            } catch (e: AppFunctionException) {
                callback.onError(e)
            } catch (e: Exception) {
                callback.onError(AppFunctionAppUnknownException(e.message))
            }
        }

    internal fun onDestroy() {
        job.cancel()
    }

    private fun getTranslator(
        request: ExecuteAppFunctionRequest,
        schemaMetadata: AppFunctionSchemaMetadata?
    ): Translator? {
        if (request.useJetpackSchema) {
            return null
        }
        return schemaMetadata?.let { translatorSelector.getTranslator(it) }
    }

    private fun extractParameters(
        request: ExecuteAppFunctionRequest,
        appFunctionMetadata: CompileTimeAppFunctionMetadata,
        translator: Translator?
    ): Map<String, Any?> {
        // Upgrade the parameters from the agents, if they are using the old format.
        val translatedParameters =
            translator?.upgradeRequest(request.functionParameters) ?: request.functionParameters

        return buildMap {
            for (parameterMetadata in appFunctionMetadata.parameters) {
                this[parameterMetadata.name] =
                    translatedParameters.unsafeGetParameterValue(parameterMetadata)
            }
        }
    }

    private suspend fun unsafeInvokeFunction(
        request: ExecuteAppFunctionRequest,
        callingPackageName: String,
        appFunctionMetadata: CompileTimeAppFunctionMetadata,
        parameters: Map<String, Any?>,
        translator: Translator?
    ): ExecuteAppFunctionResponse {
        val result =
            withContext(mainCoroutineContext) {
                aggregatedInvoker.unsafeInvoke(
                    buildAppFunctionContext(callingPackageName),
                    request.functionIdentifier,
                    parameters
                )
            }
        val returnValue = appFunctionMetadata.response.unsafeBuildReturnValue(result)
        // Downgrade the return value from the agents, if they are using the old format.
        val translatedReturnValue = translator?.downgradeResponse(returnValue) ?: returnValue
        return ExecuteAppFunctionResponse.Success(translatedReturnValue)
    }

    private fun buildAppFunctionContext(callingPackageName: String): AppFunctionContext {
        return object : AppFunctionContext {
            override val context: Context
                get() = appContext

            override val callingPackageName: String
                get() = callingPackageName
        }
    }
}
