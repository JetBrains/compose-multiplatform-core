/*
 * Copyright (C) 2017 The Android Open Source Project
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

package androidx.room3.integration.testapp.dao;

import androidx.lifecycle.LiveData;
import androidx.room3.Dao;
import androidx.room3.Query;
import androidx.room3.RoomWarnings;
import androidx.room3.integration.testapp.vo.PetsToys;

@Dao
public interface SpecificDogDao {
    @SuppressWarnings(RoomWarnings.RELATION_QUERY_WITHOUT_TRANSACTION)
    @Query("SELECT 123 as petId")
    LiveData<PetsToys> getSpecificDogsToys();
}
