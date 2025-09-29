/*
 * Copyright 2018 The Android Open Source Project
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
import androidx.room3.ColumnInfo;
import androidx.room3.Dao;
import androidx.room3.RawQuery;
import androidx.room3.integration.testapp.vo.NameAndLastName;
import androidx.room3.integration.testapp.vo.User;
import androidx.room3.integration.testapp.vo.UserAndAllPets;
import androidx.room3.integration.testapp.vo.UserAndPet;
import androidx.sqlite.db.SupportSQLiteQuery;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.Date;
import java.util.List;

@Dao
public interface RawDao {
    @RawQuery
    UserAndAllPets getUserAndAllPets(SupportSQLiteQuery query);

    @RawQuery(observedEntities = UserAndAllPets.class)
    LiveData<UserAndAllPets> getUserAndAllPetsObservable(SupportSQLiteQuery query);

    @RawQuery
    User getUser(SupportSQLiteQuery query);

    @RawQuery
    ListenableFuture<User> getUserListenableFuture(SupportSQLiteQuery query);

    @RawQuery
    UserAndPet getUserAndPet(SupportSQLiteQuery query);

    @RawQuery
    NameAndLastName getUserNameAndLastName(SupportSQLiteQuery query);

    @RawQuery(observedEntities = User.class)
    NameAndLastName getUserNameAndLastNameWithObserved(SupportSQLiteQuery query);

    @RawQuery
    int count(SupportSQLiteQuery query);

    @RawQuery
    List<User> getUserList(SupportSQLiteQuery query);

    @RawQuery
    List<UserAndPet> getUserAndPetList(SupportSQLiteQuery query);

    @RawQuery(observedEntities = UserAndPet.class)
    LiveData<List<UserAndPet>> getUserAndPetListObservable(SupportSQLiteQuery query);

    @RawQuery(observedEntities = User.class)
    LiveData<User> getUserLiveData(SupportSQLiteQuery query);

    @RawQuery
    UserNameAndBirthday getUserAndBirthday(SupportSQLiteQuery query);

    class UserNameAndBirthday {
        @ColumnInfo(name = "mName")
        public final String name;
        @ColumnInfo(name = "mBirthday")
        public final Date birthday;

        public UserNameAndBirthday(String name, Date birthday) {
            this.name = name;
            this.birthday = birthday;
        }
    }
}
