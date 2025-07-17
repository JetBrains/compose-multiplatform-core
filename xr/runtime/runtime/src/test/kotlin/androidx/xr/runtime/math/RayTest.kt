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

package androidx.xr.runtime.math

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class RayTest {
    @Test
    fun constructor_noArguments_returnsDefaultVector() {
        val underTest = Ray()

        assertThat(underTest.origin).isEqualTo(Vector3())
        assertThat(underTest.direction).isEqualTo(Vector3())
    }

    @Test
    fun equals_sameValues_returnsTrue() {
        val underTest = Ray(Vector3(1f, 2f, 3f), Vector3(4f, 5f, 6f))
        val underTest2 = Ray(Vector3(1f, 2f, 3f), Vector3(4f, 5f, 6f))

        assertThat(underTest).isEqualTo(underTest2)
    }

    @Test
    fun equals_differentValues_returnsFalse() {
        val underTest = Ray(Vector3(1f, 2f, 3f), Vector3(4f, 5f, 6f))
        val underTest2 = Ray(Vector3(3f, 4f, 5f), Vector3(6f, 7f, 8f))
        val underTest3 = Ray()

        assertThat(underTest).isNotEqualTo(underTest2)
        assertThat(underTest).isNotEqualTo(underTest3)
    }

    @Test
    fun hashCodeEquals_sameValues_returnsTrue() {
        val underTest = Ray(Vector3(1f, 2f, 3f), Vector3(4f, 5f, 6f))
        val underTest2 = Ray(Vector3(1f, 2f, 3f), Vector3(4f, 5f, 6f))

        assertThat(underTest.hashCode()).isEqualTo(underTest2.hashCode())
    }

    @Test
    fun hashCodeEquals_differentValues_returnsFalse() {
        val underTest = Ray(Vector3(1f, 2f, 3f), Vector3(4f, 5f, 6f))
        val underTest2 = Ray(Vector3(3f, 4f, 5f), Vector3(6f, 7f, 8f))
        val underTest3 = Ray()

        assertThat(underTest.hashCode()).isNotEqualTo(underTest2.hashCode())
        assertThat(underTest.hashCode()).isNotEqualTo(underTest3.hashCode())
    }

    @Test
    fun constructorEquals_expectedToString_returnsTrue() {
        val underTest = Ray(Vector3(1f, 2f, 3f), Vector3(4f, 5f, 6f))
        val underTest2 = Ray(Vector3(3f, 4f, 5f), Vector3(6f, 7f, 8f))

        assertThat(underTest.toString())
            .isEqualTo("[origin=[x=1.0, y=2.0, z=3.0], direction=[x=4.0, y=5.0, z=6.0]]")
        assertThat(underTest2.toString())
            .isEqualTo("[origin=[x=3.0, y=4.0, z=5.0], direction=[x=6.0, y=7.0, z=8.0]]")
    }

    @Test
    fun constructor_fromRay_returnsSameValues() {
        val underTest = Ray(Vector3(1f, 2f, 3f), Vector3(4f, 5f, 6f))
        val underTest2 = Ray(underTest)

        assertThat(underTest).isEqualTo(underTest2)
    }
}
