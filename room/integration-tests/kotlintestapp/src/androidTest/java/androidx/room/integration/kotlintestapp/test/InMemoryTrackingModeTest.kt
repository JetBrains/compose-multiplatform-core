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

package androidx.room.integration.kotlintestapp.test

import androidx.kruth.assertThat
import androidx.room.ExperimentalRoomApi
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.integration.kotlintestapp.TestDatabase
import androidx.room.util.useCursor
import androidx.test.platform.app.InstrumentationRegistry
import kotlin.test.Test
import org.junit.Before

class InMemoryTrackingModeTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setup() {
        context.deleteDatabase("test.db")
    }

    @Test
    @OptIn(ExperimentalRoomApi::class)
    fun persistedTrackingTable() {
        val database =
            Room.databaseBuilder<TestDatabase>(context, "test.db")
                .setInMemoryTrackingMode(false)
                .build()

        assertThat(findCreateSql(database, "sqlite_master")).isNotEmpty()
        assertThat(findCreateSql(database, "sqlite_temp_master")).isNull()
        database.close()
    }

    @Test
    @OptIn(ExperimentalRoomApi::class)
    fun temporaryTrackingTable() {
        val database =
            Room.databaseBuilder<TestDatabase>(context, "test.db")
                .setInMemoryTrackingMode(true)
                .build()

        assertThat(findCreateSql(database, "sqlite_master")).isNull()
        assertThat(findCreateSql(database, "sqlite_temp_master")).isNotEmpty()
        database.close()
    }

    private fun findCreateSql(database: RoomDatabase, masterTable: String) =
        database.runInTransaction<String?> {
            database.openHelper.writableDatabase
                .query("SELECT name, sql FROM $masterTable")
                .useCursor { c ->
                    while (c.moveToNext()) {
                        if (c.getString(0) == TRACKING_TABLE_NAME) {
                            return@runInTransaction c.getString(1)
                        }
                    }
                }
            null
        }

    private companion object {
        private const val TRACKING_TABLE_NAME = "room_table_modification_log"
    }
}
