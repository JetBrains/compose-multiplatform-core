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

package androidx.appfunctions

import android.app.PendingIntent

/**
 * Represents an entity that can be opened.
 *
 * This is an actionable [PendingIntent] resource provided as part of an [AppFunctionSerializable].
 * e.g., a Note serializable implementing this [AppFunctionOpenable] signifies that the note is
 * openable by launching the corresponding provided pending Intent.
 *
 * The intent is typically processed separately from the rest of the data in the corresponding
 * [androidx.appfunctions.AppFunctionSerializable] that implements it.
 */
@AppFunctionSchemaCapability
public interface AppFunctionOpenable {
    /** The [PendingIntent] that can be launched/started to open the entity. */
    public val intentToOpen: PendingIntent
}
