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

package androidx.xr.scenecore

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TypesTest {
    @Test
    fun fov_primaryConstructor_setsValuesCorrectly() {
        val fov = Fov(1f, 2f, 3f, 4f)
        assertThat(fov.angleLeft).isEqualTo(1f)
        assertThat(fov.angleRight).isEqualTo(2f)
        assertThat(fov.angleUp).isEqualTo(3f)
        assertThat(fov.angleDown).isEqualTo(4f)
    }

    @Test
    fun fov_equals_sameInstance_returnsTrue() {
        val fov = Fov(1f, 2f, 3f, 4f)
        assertThat(fov).isEqualTo(fov)
    }

    @Test
    fun fov_equals_differentType_returnsFalse() {
        val fov = Fov(1f, 2f, 3f, 4f)
        val other = "Not an Fov"
        assertThat(fov).isNotEqualTo(other)
    }

    @Test
    fun fov_equals_identicalValues_returnsTrue() {
        val fov1 = Fov(1f, 2f, 3f, 4f)
        val fov2 = Fov(1f, 2f, 3f, 4f)
        assertThat(fov1).isEqualTo(fov2)
    }

    @Test
    fun fov_equals_differentValue_returnsFalse() {
        val fov1 = Fov(1f, 2f, 3f, 4f)
        val fov2 = Fov(0f, 2f, 3f, 4f)
        assertThat(fov1).isNotEqualTo(fov2)
    }

    @Test
    fun fov_equals_null_returnsFalse() {
        val fov = Fov(1f, 2f, 3f, 4f)
        assertThat(fov).isNotEqualTo(null)
    }

    @Test
    fun fov_hashCode_identical_same_hash_code() {
        val fov1 = Fov(1f, 2f, 3f, 4f)
        val fov2 = Fov(1f, 2f, 3f, 4f)
        assertThat(fov1.hashCode()).isEqualTo(fov2.hashCode())
    }

    @Test
    fun fov_hashCode_differentValues_differentHashCode() {
        val fov1 = Fov(1f, 2f, 3f, 4f)
        val fov2 = Fov(0f, 2f, 3f, 4f)
        assertThat(fov1.hashCode()).isNotEqualTo(fov2.hashCode())
    }

    @Test
    fun fov_toString_returnsExpectedString() {
        val fov = Fov(1.5f, -2.5f, 3.14f, 0f)
        val expected = "Fov(angleLeft=1.5, angleRight=-2.5, angleUp=3.14, angleDown=0.0)"
        assertThat(fov.toString()).isEqualTo(expected)
    }

    @Test
    fun fov_copy_noChanges_returnsIdenticalObject() {
        val original = Fov(1f, 2f, 3f, 4f)
        val copied = original.copy()
        assertThat(copied).isEqualTo(original)
        assertThat(copied).isNotSameInstanceAs(original)
    }

    @Test
    fun fov_copy_changeValue_returnsNewObjectWithChangedValue() {
        val original = Fov(1f, 2f, 3f, 4f)
        val copied = original.copy(angleLeft = 5f)
        assertThat(copied.angleLeft).isEqualTo(5f)
        assertThat(copied.angleRight).isEqualTo(2f)
        assertThat(copied.angleUp).isEqualTo(3f)
        assertThat(copied.angleDown).isEqualTo(4f)
    }
}
