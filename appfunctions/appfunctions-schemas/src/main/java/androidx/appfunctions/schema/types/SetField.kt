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

package androidx.appfunctions.schema.types

import androidx.appfunctions.AppFunctionSerializable
import java.util.Objects

/**
 * Class for representing an explicit field-setting request.
 *
 * Used in parameter objects for update APIs. Properties of type `SetField<T>?` signal whether a
 * field of type `T` should be set.
 *
 * A `null` `SetField` property indicates **no update** to the corresponding field. A non-`null`
 * `SetField` instance signifies an **intentional update**, with [value] providing the new field
 * value. This distinction is crucial for optional or nullable fields, allowing explicit null
 * setting versus no-op.
 *
 * **Usage:**
 *
 * Consider an AppFunction for updating a note. To optionally update a setting value:
 * ```kotlin
 * @AppFunctionSerializable
 * data class UpdateNoteParams(
 *     val title: SetField<String?>? = null,
 * )
 *
 * // To update the title to a new value
 * val updateParams = UpdateNoteParams(
 *     title = SetField<String?>("New Title")
 * )
 *
 * // To leave the setting value unchanged:
 * val noUpdateParams = UpdateNoteParams(title = null)
 * ```
 *
 * @param T The type of the field being set.
 * @param value The new field value.
 */
@AppFunctionSerializable
public class SetField<out T>(public val value: T) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SetField<*>) return false
        return value == other.value
    }

    override fun hashCode(): Int {
        return Objects.hash(value)
    }

    override fun toString(): String {
        return "SetField(value=${value})"
    }
}
