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

package androidx.pdf

import android.content.Context
import android.os.Build
import androidx.pdf.exceptions.PdfPasswordException
import androidx.pdf.service.connect.FakePdfServiceConnection
import androidx.pdf.utils.TestUtils
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.filters.SmallTest
import com.google.common.truth.Truth.assertThat
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.VANILLA_ICE_CREAM, codeName = "VanillaIceCream")
@RunWith(AndroidJUnit4::class)
class SandboxedPdfLoaderTest {
    @Test
    fun openDocument_notConnected_connectsAndLoadsDocument() = runTest {
        var isServiceConnected = false
        val context = ApplicationProvider.getApplicationContext<Context>()
        val loader =
            SandboxedPdfLoader(
                context,
                Dispatchers.Main,
                FakePdfServiceConnection(context, isConnected = false) { isServiceConnected = true }
            )
        val uri = TestUtils.openFile(context, PDF_DOCUMENT)

        val document = loader.openDocument(uri)

        val expectedPageCount = 3
        assertTrue(isServiceConnected)
        assertThat(document.uri == uri).isTrue()
        assertThat(document.pageCount == expectedPageCount).isTrue()
        assertThat(!document.isLinearized).isTrue()
        document.close()
    }

    @Test(expected = IllegalStateException::class)
    fun openDocument_connectedAndNullBinder_throwsIllegalStateException() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val loader =
            SandboxedPdfLoader(
                context,
                Dispatchers.Main,
                FakePdfServiceConnection(context, isConnected = true)
            )
        val uri = TestUtils.openFile(context, PDF_DOCUMENT)
        runTest { loader.openDocument(uri) }
    }

    @Test(expected = PdfPasswordException::class)
    fun openDocument_passwordProtected_throwsPdfPasswordException() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val loader =
            SandboxedPdfLoader(
                context,
                Dispatchers.Main,
                FakePdfServiceConnection(context, isConnected = false)
            )

        val uri = TestUtils.openFile(context, PASSWORD_PROTECTED_DOCUMENT)

        runTest { loader.openDocument(uri) }
    }

    @Test(expected = IllegalStateException::class)
    fun openDocument_corruptedDocument_throwsIllegalStateException() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val loader =
            SandboxedPdfLoader(
                context,
                Dispatchers.Main,
                FakePdfServiceConnection(context, isConnected = false)
            )

        val uri = TestUtils.openFile(context, CORRUPTED_DOCUMENT)

        runTest { loader.openDocument(uri) }
    }

    companion object {
        private const val PDF_DOCUMENT = "sample.pdf"
        private const val PASSWORD_PROTECTED_DOCUMENT = "sample-protected.pdf"
        private const val CORRUPTED_DOCUMENT = "corrupted.pdf"
    }
}
