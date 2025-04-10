/*
 * Copyright 2023 The Android Open Source Project
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

@file:JvmName("AndroidSQLite")

package androidx.sqlite.driver

import androidx.annotation.RestrictTo
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteDatabase

internal object ResultCode {
    const val SQLITE_MISUSE = 21
    const val SQLITE_RANGE = 25
}

/**
 * Gets a new instance of a [SupportSQLiteDatabase] that wraps this connection's
 * [android.database.sqlite.SQLiteDatabase].
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
public fun AndroidSQLiteConnection.getSupportSQLiteDatabase(): SupportSQLiteDatabase =
    FrameworkSQLiteDatabase(db)
