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

package androidx.appfunctions.integration.tests

import android.net.Uri
import androidx.appfunctions.AppFunctionData
import androidx.appfunctions.AppFunctionFunctionNotFoundException
import androidx.appfunctions.AppFunctionInvalidArgumentException
import androidx.appfunctions.AppFunctionManagerCompat
import androidx.appfunctions.AppFunctionSearchSpec
import androidx.appfunctions.ExecuteAppFunctionRequest
import androidx.appfunctions.ExecuteAppFunctionResponse
import androidx.appfunctions.integration.tests.AppSearchMetadataHelper.isDynamicIndexerAvailable
import androidx.appfunctions.integration.tests.TestUtil.doBlocking
import androidx.appfunctions.integration.tests.TestUtil.retryAssert
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import java.time.LocalDateTime
import kotlin.test.assertIs
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assume.assumeNotNull
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

@LargeTest
class IntegrationTest {
    private val context = InstrumentationRegistry.getInstrumentation().context
    private val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var appFunctionManager: AppFunctionManagerCompat
    private val uiAutomation = InstrumentationRegistry.getInstrumentation().uiAutomation

    @Before
    fun setup() = doBlocking {
        val appFunctionManagerCompatOrNull = AppFunctionManagerCompat.getInstance(targetContext)
        assumeNotNull(appFunctionManagerCompatOrNull)
        appFunctionManager = checkNotNull(appFunctionManagerCompatOrNull)

        uiAutomation.apply {
            // This is needed because the test is running under the UID of
            // "androidx.appfunctions.integration.testapp",
            // while the app functions are defined under
            // "androidx.appfunctions.integration.testapp.test"
            adoptShellPermissionIdentity("android.permission.EXECUTE_APP_FUNCTIONS")
            executeShellCommand(
                "device_config put appsearch max_allowed_app_function_doc_size_in_bytes $TEST_APP_FUNCTION_DOC_SIZE_LIMIT"
            )
        }
        awaitAppFunctionsIndexed(FUNCTION_IDS)
    }

    @After
    fun tearDown() {
        uiAutomation.dropShellPermissionIdentity()
    }

    @Test
    fun executeAppFunction_success() = doBlocking {
        val response =
            appFunctionManager.executeAppFunction(
                request =
                    ExecuteAppFunctionRequest(
                        context.packageName,
                        "androidx.appfunctions.integration.tests.TestFunctions#add",
                        AppFunctionData.Builder("").setLong("num1", 1).setLong("num2", 2).build()
                    )
            )

        val successResponse = assertIs<ExecuteAppFunctionResponse.Success>(response)
        assertThat(
                successResponse.returnValue.getLong(
                    ExecuteAppFunctionResponse.Success.PROPERTY_RETURN_VALUE
                )
            )
            .isEqualTo(3)
    }

    // TODO: b/410548653 - Add proper tests for query API
    @Test
    fun searchAllAppFunctions_success() = doBlocking {
        assumeTrue(isDynamicIndexerAvailable(context))
        val searchFunctionSpec = AppFunctionSearchSpec(packageNames = setOf(context.packageName))
        // Total number of serializable types used as an appfunction parameter or return value in
        // the set of appfunctions defined by this integration test.
        val expectedSize = 13

        val appFunctions = appFunctionManager.observeAppFunctions(searchFunctionSpec).first()

        assertThat(appFunctions.first().components.dataTypes.size).isEqualTo(expectedSize)
    }

    @Test
    fun executeAppFunction_voidReturnType_success() = doBlocking {
        val response =
            appFunctionManager.executeAppFunction(
                request =
                    ExecuteAppFunctionRequest(
                        context.packageName,
                        "androidx.appfunctions.integration.tests.TestFunctions#voidFunction",
                        AppFunctionData.Builder("").build()
                    )
            )

        assertThat(response).isInstanceOf(ExecuteAppFunctionResponse.Success::class.java)
    }

    @Test
    fun executeAppFunction_setFactory_success() = doBlocking {
        // A factory is set to create the enclosing class of the function.
        // See [TestApplication.appFunctionConfiguration].
        var response =
            appFunctionManager.executeAppFunction(
                request =
                    ExecuteAppFunctionRequest(
                        context.packageName,
                        "androidx.appfunctions.integration.tests.TestFactory#isCreatedByFactory",
                        AppFunctionData.Builder("").build()
                    )
            )

        // If the enclosing class was created by the provided factory, the secondary constructor
        // should be called and so the return value would be `true`.
        val successResponse = assertIs<ExecuteAppFunctionResponse.Success>(response)
        assertThat(
                successResponse.returnValue.getBoolean(
                    ExecuteAppFunctionResponse.Success.PROPERTY_RETURN_VALUE
                )
            )
            .isEqualTo(true)
    }

    @Test
    fun executeAppFunction_functionInLibraryModule_success() = doBlocking {
        var response =
            appFunctionManager.executeAppFunction(
                request =
                    ExecuteAppFunctionRequest(
                        context.packageName,
                        "androidx.appfunctions.integration.testapp.library.TestFunctions2#concat",
                        AppFunctionData.Builder("")
                            .setString("str1", "log")
                            .setString("str2", "cat")
                            .build()
                    )
            )

        val successResponse = assertIs<ExecuteAppFunctionResponse.Success>(response)
        assertThat(
                successResponse.returnValue.getString(
                    ExecuteAppFunctionResponse.Success.PROPERTY_RETURN_VALUE
                )
            )
            .isEqualTo("logcat")
    }

    @Test
    fun executeAppFunction_functionNotFound_fail() = doBlocking {
        val response =
            appFunctionManager.executeAppFunction(
                request =
                    ExecuteAppFunctionRequest(
                        context.packageName,
                        "androidx.appfunctions.integration.tests.TestFunctions#notExist",
                        AppFunctionData.Builder("").build()
                    )
            )

        val errorResponse = assertIs<ExecuteAppFunctionResponse.Error>(response)
        assertThat(errorResponse.error)
            .isInstanceOf(AppFunctionFunctionNotFoundException::class.java)
    }

    @Test
    fun executeAppFunction_appThrows_fail() = doBlocking {
        val response =
            appFunctionManager.executeAppFunction(
                request =
                    ExecuteAppFunctionRequest(
                        context.packageName,
                        "androidx.appfunctions.integration.tests.TestFunctions#doThrow",
                        AppFunctionData.Builder("").build()
                    )
            )

        assertThat(response).isInstanceOf(ExecuteAppFunctionResponse.Error::class.java)
        val errorResponse = response as ExecuteAppFunctionResponse.Error
        assertThat(errorResponse.error)
            .isInstanceOf(AppFunctionInvalidArgumentException::class.java)
    }

    @Test
    fun executeAppFunction_createNote() = doBlocking {
        val response =
            appFunctionManager.executeAppFunction(
                request =
                    ExecuteAppFunctionRequest(
                        context.packageName,
                        "androidx.appfunctions.integration.tests.TestFunctions#createNote",
                        AppFunctionData.Builder("")
                            .setAppFunctionData(
                                "createNoteParams",
                                AppFunctionData.serialize(
                                    CreateNoteParams(
                                        title = "Test Title",
                                        content = listOf("1", "2"),
                                        owner = Owner("test"),
                                        attachments =
                                            listOf(Attachment("Uri1", Attachment("nested")))
                                    ),
                                    CreateNoteParams::class.java
                                )
                            )
                            .build()
                    )
            )

        val successResponse = assertIs<ExecuteAppFunctionResponse.Success>(response)
        val expectedNote =
            Note(
                title = "Test Title",
                content = listOf("1", "2"),
                owner = Owner("test"),
                attachments = listOf(Attachment("Uri1", Attachment("nested")))
            )
        assertThat(
                successResponse.returnValue
                    .getAppFunctionData(ExecuteAppFunctionResponse.Success.PROPERTY_RETURN_VALUE)
                    ?.deserialize(Note::class.java)
            )
            .isEqualTo(expectedNote)
    }

    @Test
    fun executeAppFunction_createNote_withOpenableCapability_returnsNote() = doBlocking {
        val response =
            appFunctionManager.executeAppFunction(
                request =
                    ExecuteAppFunctionRequest(
                        context.packageName,
                        "androidx.appfunctions.integration.tests.TestFunctions#getOpenableNote",
                        AppFunctionData.Builder("")
                            .setAppFunctionData(
                                "createNoteParams",
                                AppFunctionData.serialize(
                                    CreateNoteParams(
                                        title = "Test Title",
                                        content = listOf("1", "2"),
                                        owner = Owner("test"),
                                        attachments =
                                            listOf(Attachment("Uri1", Attachment("nested")))
                                    ),
                                    CreateNoteParams::class.java
                                )
                            )
                            .build()
                    )
            )

        val successResponse = assertIs<ExecuteAppFunctionResponse.Success>(response)
        val expectedNote =
            Note(
                title = "Test Title",
                content = listOf("1", "2"),
                owner = Owner("test"),
                attachments = listOf(Attachment("Uri1", Attachment("nested")))
            )
        assertThat(
                successResponse.returnValue
                    .getAppFunctionData(ExecuteAppFunctionResponse.Success.PROPERTY_RETURN_VALUE)
                    ?.deserialize(Note::class.java)
            )
            .isEqualTo(expectedNote)
    }

    @Test
    fun executeAppFunction_createNote_withOpenableCapability_returnsOpenableNote() = doBlocking {
        val response =
            appFunctionManager.executeAppFunction(
                request =
                    ExecuteAppFunctionRequest(
                        context.packageName,
                        "androidx.appfunctions.integration.tests.TestFunctions#getOpenableNote",
                        AppFunctionData.Builder("")
                            .setAppFunctionData(
                                "createNoteParams",
                                AppFunctionData.serialize(
                                    CreateNoteParams(
                                        title = "Test Title",
                                        content = listOf("1", "2"),
                                        owner = Owner("test"),
                                        attachments =
                                            listOf(Attachment("Uri1", Attachment("nested")))
                                    ),
                                    CreateNoteParams::class.java
                                )
                            )
                            .build()
                    )
            )

        val successResponse = assertIs<ExecuteAppFunctionResponse.Success>(response)
        val expectedNote =
            Note(
                title = "Test Title",
                content = listOf("1", "2"),
                owner = Owner("test"),
                attachments = listOf(Attachment("Uri1", Attachment("nested"))),
            )
        val openableNoteResult =
            assertIs<OpenableNote>(
                successResponse.returnValue
                    .getAppFunctionData(ExecuteAppFunctionResponse.Success.PROPERTY_RETURN_VALUE)
                    ?.deserialize(OpenableNote::class.java)
            )

        assertThat(openableNoteResult.title).isEqualTo(expectedNote.title)
        assertThat(openableNoteResult.content).isEqualTo(expectedNote.content)
        assertThat(openableNoteResult.owner).isEqualTo(expectedNote.owner)
        assertThat(openableNoteResult.attachments).isEqualTo(expectedNote.attachments)
        assertThat(openableNoteResult.intentToOpen).isNotNull()
    }

    @Test
    fun executeAppFunction_serializableProxyParam_dateTime_success() = doBlocking {
        val localDateTimeClass = DateTime(LocalDateTime.now())
        val response =
            appFunctionManager.executeAppFunction(
                request =
                    ExecuteAppFunctionRequest(
                        targetPackageName = context.packageName,
                        functionIdentifier =
                            "androidx.appfunctions.integration.tests.TestFunctions#logLocalDateTime",
                        functionParameters =
                            AppFunctionData.Builder("")
                                .setAppFunctionData(
                                    "dateTime",
                                    AppFunctionData.serialize(
                                        localDateTimeClass,
                                        DateTime::class.java
                                    )
                                )
                                .build()
                    )
            )

        assertIs<ExecuteAppFunctionResponse.Success>(response)
    }

    @Test
    fun executeAppFunction_serializableProxyParam_androidUri_success() = doBlocking {
        val androidUri = Uri.parse("https://www.google.com/")
        val response =
            appFunctionManager.executeAppFunction(
                request =
                    ExecuteAppFunctionRequest(
                        targetPackageName = context.packageName,
                        functionIdentifier =
                            "androidx.appfunctions.integration.testapp.library.TestFunctions2#logUri",
                        functionParameters =
                            AppFunctionData.Builder("")
                                .setAppFunctionData(
                                    "androidUri",
                                    AppFunctionData.serialize(androidUri, Uri::class.java)
                                )
                                .build()
                    )
            )

        assertIs<ExecuteAppFunctionResponse.Success>(response)
    }

    @Test
    fun executeAppFunction_serializableProxyResponse_dateTime_success() = doBlocking {
        val response =
            appFunctionManager.executeAppFunction(
                request =
                    ExecuteAppFunctionRequest(
                        targetPackageName = context.packageName,
                        functionIdentifier =
                            "androidx.appfunctions.integration.tests.TestFunctions#getLocalDate",
                        functionParameters = AppFunctionData.Builder("").build()
                    )
            )

        val successResponse = assertIs<ExecuteAppFunctionResponse.Success>(response)

        assertIs<LocalDateTime>(
            successResponse.returnValue
                .getAppFunctionData(ExecuteAppFunctionResponse.Success.PROPERTY_RETURN_VALUE)
                ?.deserialize(DateTime::class.java)
                ?.localDateTime
        )
    }

    @Test
    fun executeAppFunction_serializableProxyResponse_androidUri_success() = doBlocking {
        val response =
            appFunctionManager.executeAppFunction(
                request =
                    ExecuteAppFunctionRequest(
                        targetPackageName = context.packageName,
                        functionIdentifier =
                            "androidx.appfunctions.integration.testapp.library.TestFunctions2#getUri",
                        functionParameters = AppFunctionData.Builder("").build()
                    )
            )

        val successResponse = assertIs<ExecuteAppFunctionResponse.Success>(response)

        val androidUriResult =
            assertIs<Uri>(
                successResponse.returnValue
                    .getAppFunctionData(ExecuteAppFunctionResponse.Success.PROPERTY_RETURN_VALUE)
                    ?.deserialize(Uri::class.java)
            )
        assertThat(androidUriResult.toString()).isEqualTo("https://www.google.com/")
    }

    @Test
    fun executeAppFunction_updateNote_success() = doBlocking {
        val response =
            appFunctionManager.executeAppFunction(
                request =
                    ExecuteAppFunctionRequest(
                        context.packageName,
                        "androidx.appfunctions.integration.tests.TestFunctions#updateNote",
                        AppFunctionData.Builder("")
                            .setAppFunctionData(
                                "updateNoteParams",
                                AppFunctionData.serialize(
                                    UpdateNoteParams(
                                        title = SetField("NewTitle1"),
                                        nullableTitle = SetField("NewTitle2"),
                                        content = SetField(listOf("NewContent1")),
                                        nullableContent = SetField(listOf("NewContent2"))
                                    ),
                                    UpdateNoteParams::class.java
                                )
                            )
                            .build()
                    )
            )

        val successResponse = assertIs<ExecuteAppFunctionResponse.Success>(response)
        val expectedNote =
            Note(
                title = "NewTitle1_NewTitle2",
                content = listOf("NewContent1", "NewContent2"),
                owner = Owner("test"),
                attachments = listOf()
            )
        assertThat(
                successResponse.returnValue
                    .getAppFunctionData(ExecuteAppFunctionResponse.Success.PROPERTY_RETURN_VALUE)
                    ?.deserialize(Note::class.java)
            )
            .isEqualTo(expectedNote)
    }

    @Test
    fun executeAppFunction_updateNoteSetFieldNullContent_success() = doBlocking {
        val response =
            appFunctionManager.executeAppFunction(
                request =
                    ExecuteAppFunctionRequest(
                        context.packageName,
                        "androidx.appfunctions.integration.tests.TestFunctions#updateNote",
                        AppFunctionData.Builder("")
                            .setAppFunctionData(
                                "updateNoteParams",
                                AppFunctionData.serialize(
                                    UpdateNoteParams(
                                        title = SetField("NewTitle1"),
                                        nullableTitle = SetField(null),
                                        content = SetField(listOf("NewContent1")),
                                        nullableContent = SetField(null)
                                    ),
                                    UpdateNoteParams::class.java
                                )
                            )
                            .build()
                    )
            )

        val successResponse = assertIs<ExecuteAppFunctionResponse.Success>(response)
        val expectedNote =
            Note(
                title = "NewTitle1_DefaultTitle",
                content = listOf("NewContent1", "DefaultContent"),
                owner = Owner("test"),
                attachments = listOf()
            )
        assertThat(
                successResponse.returnValue
                    .getAppFunctionData(ExecuteAppFunctionResponse.Success.PROPERTY_RETURN_VALUE)
                    ?.deserialize(Note::class.java)
            )
            .isEqualTo(expectedNote)
    }

    @Test
    fun executeAppFunction_updateNoteNullSetFields_success() = doBlocking {
        val response =
            appFunctionManager.executeAppFunction(
                request =
                    ExecuteAppFunctionRequest(
                        context.packageName,
                        "androidx.appfunctions.integration.tests.TestFunctions#updateNote",
                        AppFunctionData.Builder("")
                            .setAppFunctionData(
                                "updateNoteParams",
                                AppFunctionData.serialize(
                                    UpdateNoteParams(),
                                    UpdateNoteParams::class.java
                                )
                            )
                            .build()
                    )
            )

        val successResponse = assertIs<ExecuteAppFunctionResponse.Success>(response)
        val expectedNote =
            Note(
                title = "DefaultTitle_DefaultTitle",
                content = listOf("DefaultContent", "DefaultContent"),
                owner = Owner("test"),
                attachments = listOf()
            )
        assertThat(
                successResponse.returnValue
                    .getAppFunctionData(ExecuteAppFunctionResponse.Success.PROPERTY_RETURN_VALUE)
                    ?.deserialize(Note::class.java)
            )
            .isEqualTo(expectedNote)
    }

    private suspend fun awaitAppFunctionsIndexed(expectedFunctionIds: Set<String>) {
        retryAssert {
            val functionIds = AppSearchMetadataHelper.collectSelfFunctionIds(context)
            assertThat(functionIds).containsAtLeastElementsIn(expectedFunctionIds)
        }
    }

    private companion object {
        const val TEST_APP_FUNCTION_DOC_SIZE_LIMIT = 512 * 1024 // 512kb

        val FUNCTION_IDS =
            setOf<String>(
                "androidx.appfunctions.integration.tests.TestFunctions#add",
                "androidx.appfunctions.integration.tests.TestFunctions#doThrow",
                "androidx.appfunctions.integration.tests.TestFunctions#voidFunction",
                "androidx.appfunctions.integration.tests.TestFunctions#createNote",
                "androidx.appfunctions.integration.tests.TestFunctions#updateNote",
                "androidx.appfunctions.integration.tests.TestFunctions#logLocalDateTime",
                "androidx.appfunctions.integration.tests.TestFunctions#getLocalDate",
                "androidx.appfunctions.integration.tests.TestFunctions#getOpenableNote",
                "androidx.appfunctions.integration.tests.TestFactory#isCreatedByFactory",
                "androidx.appfunctions.integration.testapp.library.TestFunctions2#concat",
                "androidx.appfunctions.integration.testapp.library.TestFunctions2#logUri",
                "androidx.appfunctions.integration.testapp.library.TestFunctions2#getUri",
            )
    }
}
