/*
 * Copyright 2022 The Android Open Source Project
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

package androidx.core.location.altitude.impl.db;

import androidx.room.Dao;
import androidx.room.Query;

import org.jspecify.annotations.Nullable;

/** Provides data access for entities within the MapParams table. */
@Dao
public interface MapParamsDao {

    /** Returns the most current map parameters. */
    @Query("SELECT * FROM MapParams ORDER BY id DESC LIMIT 1")
    @Nullable MapParamsEntity getCurrent();
}
