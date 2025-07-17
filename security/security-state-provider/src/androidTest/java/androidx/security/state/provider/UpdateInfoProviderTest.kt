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

package androidx.security.state.provider

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@MediumTest
@RunWith(AndroidJUnit4::class)
class UpdateInfoProviderTest {

    private val authority = "com.example.updateinfoprovider"
    private val contentUri = Uri.parse("content://$authority/updateinfo")
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var provider: UpdateInfoProvider

    @Before
    fun setup() {
        // simulate the provider being instantiated by the system from the manifest
        provider = UpdateInfoProvider()
        provider.attachInfo(context, null)
    }

    @Test
    fun query_WithCorrectUri_ReturnsExpectedCursor() {
        val cursor = provider.query(contentUri, null, null, null, null)

        assertNotNull(cursor)
        assertEquals(0, cursor.count)
        cursor.close()
    }

    @Test(expected = IllegalArgumentException::class)
    fun query_WithIncorrectUri_ThrowsException() {
        provider.query(Uri.parse("content://$authority/invalid"), null, null, null, null)
    }

    @Test
    fun getType_CorrectUri_ReturnsCorrectType() {
        val type = provider.getType(contentUri)

        assertEquals("vnd.android.cursor.dir/vnd.$authority.updateinfo", type)
    }
}
