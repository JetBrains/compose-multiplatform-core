/*
 * Copyright 2026 The Android Open Source Project
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

package androidx.build

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MaxDepVersionsTest {
    @Test
    fun compatibleVersionIsNotSubstituted() {
        assertThat(shouldSubstituteDependency("1.2.3", "1.2.3+fork")).isFalse()
    }

    @Test
    fun incompatibleVersionIsSubstituted() {
        assertThat(shouldSubstituteDependency("1.2.2", "1.2.3+fork")).isTrue()
    }
}
