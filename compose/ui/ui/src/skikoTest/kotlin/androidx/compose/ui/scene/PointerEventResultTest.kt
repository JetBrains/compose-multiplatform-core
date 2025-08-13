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

package androidx.compose.ui.scene

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PointerEventResultTest {

    @Test
    fun testSecondaryConstructor() {
        PointerEventResult().let {
            assertEquals(0, it.value)
            assertFalse(it.anyMovementConsumed)
            assertFalse(it.anyChangeConsumed)
        }

        PointerEventResult(anyMovementConsumed = true).let {
            assertEquals(2, it.value)
            assertTrue(it.anyMovementConsumed)
            assertFalse(it.anyChangeConsumed)
        }

        PointerEventResult(anyChangeConsumed = true).let {
            assertEquals(4, it.value)
            assertFalse(it.anyMovementConsumed)
            assertTrue(it.anyChangeConsumed)
        }

        PointerEventResult(anyMovementConsumed = true, anyChangeConsumed = true).let {
            assertEquals(6, it.value)
            assertTrue(it.anyMovementConsumed)
            assertTrue(it.anyChangeConsumed)
        }
    }

    @Test
    fun testMapping() {
        androidx.compose.ui.input.pointer.ProcessResult(
            dispatchedToAPointerInputModifier = false,
            anyMovementConsumed = false,
            anyChangeConsumed = false
        ).let {
            PointerEventResult(it.value)
        }.let { result ->
            assertEquals(0, result.value)
            assertFalse(result.anyMovementConsumed)
            assertFalse(result.anyChangeConsumed)
        }

        androidx.compose.ui.input.pointer.ProcessResult(
            dispatchedToAPointerInputModifier = true,
            anyMovementConsumed = false,
            anyChangeConsumed = false
        ).let {
            PointerEventResult(it.value)
        }.let { result ->
            assertEquals(1, result.value)
            assertFalse(result.anyMovementConsumed)
            assertFalse(result.anyChangeConsumed)
        }

        androidx.compose.ui.input.pointer.ProcessResult(
            dispatchedToAPointerInputModifier = true,
            anyMovementConsumed = true,
            anyChangeConsumed = false
        ).let {
            PointerEventResult(it.value)
        }.let { result ->
            assertEquals(3, result.value)
            assertTrue(result.anyMovementConsumed)
            assertFalse(result.anyChangeConsumed)
        }

        androidx.compose.ui.input.pointer.ProcessResult(
            dispatchedToAPointerInputModifier = true,
            anyMovementConsumed = false,
            anyChangeConsumed = true
        ).let {
            PointerEventResult(it.value)
        }.let { result ->
            assertEquals(5, result.value)
            assertFalse(result.anyMovementConsumed)
            assertTrue(result.anyChangeConsumed)
        }

        androidx.compose.ui.input.pointer.ProcessResult(
            dispatchedToAPointerInputModifier = true,
            anyMovementConsumed = true,
            anyChangeConsumed = true
        ).let {
            PointerEventResult(it.value)
        }.let { result ->
            assertEquals(7, result.value)
            assertTrue(result.anyMovementConsumed)
            assertTrue(result.anyChangeConsumed)
        }
    }

    @Test
    fun testMergingWithMultipleParams() {
        // Test with two parameters (this, result1)
        PointerEventResult(value = 1).merging(
            result1 = PointerEventResult(value = 2)
        ).let { result ->
            assertEquals(3, result.value)
        }

        // Test with three parameters (this, result1, result2)
        PointerEventResult(value = 1).merging(
            result1 = PointerEventResult(value = 2),
            result2 = PointerEventResult(value = 4)
        ).let { result ->
            assertEquals(7, result.value)
        }

        // Test with four parameters (this, result1, result2, result3)
        PointerEventResult(value = 1).merging(
            result1 = PointerEventResult(value = 2),
            result2 = PointerEventResult(value = 4),
            result3 = PointerEventResult(value = 7)
        ).let { result ->
            assertEquals(7, result.value)
        }

        // Test with null parameters
        PointerEventResult(value = 1).merging(
            result1 = PointerEventResult(value = 2),
            result2 = null,
            result3 = null
        ).let { result ->
            assertEquals(3, result.value)
        }

        PointerEventResult(value = 1).merging(
            result1 = PointerEventResult(value = 2),
            result2 = PointerEventResult(value = 4),
            result3 = null
        ).let { result ->
            assertEquals(7, result.value)
        }

        // Test with zero values
        PointerEventResult(value = 0).merging(
            result1 = PointerEventResult(value = 0),
            result2 = PointerEventResult(value = 0),
            result3 = PointerEventResult(value = 0)
        ).let { result ->
            assertEquals(0, result.value)
        }

        // Test with a mix of values
        PointerEventResult(value = 1).merging(
            result1 = PointerEventResult(value = 0),
            result2 = PointerEventResult(value = 2),
            result3 = PointerEventResult(value = 1)
        ).let { result ->
            assertEquals(3, result.value)
        }
    }

}