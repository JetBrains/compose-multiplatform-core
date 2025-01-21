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

package androidx.navigation3

import androidx.kruth.assertThat
import kotlin.test.Test
import kotlin.test.fail

class RecordProviderTest {

    @Test
    fun recordProvider_withUniqueInitializers_returnsRecords() {
        val provider = recordProvider {
            record("first") {}
            record("second") {}
        }

        val record1 = provider.invoke("first")
        val record2 = provider.invoke("second")

        assertThat(record1.key).isEqualTo("first")
        assertThat(record2.key).isEqualTo("second")
    }

    @Test
    fun recordProvider_withDuplicatedInitializers_throwsException() {
        try {
            recordProvider {
                record("first") {}
                record("first") {}
            }
            fail("Expected `IllegalArgumentException` but no exception has been throw.")
        } catch (e: IllegalArgumentException) {
            assertThat(e)
                .hasMessageThat()
                .isEqualTo("A `record` with the key `key` has already been added: first.")
        }
    }

    @Test
    fun recordProvider_noInitializers_getsInvalidRecord() {
        val provider = recordProvider {}
        try {
            provider.invoke("something")
            fail("Expected `IllegalStateException` but no exception has been throw.")
        } catch (e: IllegalStateException) {
            assertThat(e).hasMessageThat().isEqualTo("Unknown screen something")
        }
    }
}
