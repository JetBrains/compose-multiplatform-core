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

package androidx.pdf.viewer.fragment

import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresExtension
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RequiresExtension(extension = Build.VERSION_CODES.S, version = 13)
@RunWith(RobolectricTestRunner::class)
class PdfViewerFragmentTest {
    @Test
    fun test_setDocumentUri_withInvalidUri_throwsIllegalArgumentException() {
        val fragment = PdfViewerFragment()
        val customUriString = "customscheme://some/path/to/resource?param1=value1&param2=value2"
        val customUri: Uri = Uri.parse(customUriString)

        assertThrows(IllegalArgumentException::class.java) { fragment.documentUri = customUri }
    }
}
