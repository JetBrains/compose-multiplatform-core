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
import androidx.room3.Delete;
import androidx.room3.Insert;
import androidx.room3.Query;
import androidx.room3.RoomWarnings;
import androidx.room3.Transaction;
import androidx.room3.Update;
import androidx.room3.integration.testapp.vo.EmbeddedUserAndAllPets;
import androidx.room3.integration.testapp.vo.Pet;
import androidx.room3.integration.testapp.vo.User;
import androidx.room3.integration.testapp.vo.UserAndAllPets;
import androidx.room3.integration.testapp.vo.UserAndAllPetsViaJunction;
import androidx.room3.integration.testapp.vo.UserAndGenericPet;
import androidx.room3.integration.testapp.vo.UserAndPet;
import androidx.room3.integration.testapp.vo.UserAndPetAdoptionDates;
import androidx.room3.integration.testapp.vo.UserAndPetNonNull;
import androidx.room3.integration.testapp.vo.UserIdAndPetIds;
import androidx.room3.integration.testapp.vo.UserIdAndPetNames;
import androidx.room3.integration.testapp.vo.UserWithPetsAndToys;

import org.jspecify.annotations.NonNull;

import java.util.List;

@Dao
public interface UserPetDao {
    @Query("SELECT * FROM User u, Pet p WHERE u.mId = p.mUserId")
    List<UserAndPet> loadAll();

    @Query("SELECT * FROM User u, Pet p WHERE u.mId = p.mUserId")
    List<UserAndGenericPet> loadAllGeneric();

    @Query("SELECT * FROM User u LEFT OUTER JOIN Pet p ON u.mId = p.mUserId")
    List<UserAndPet> loadUsers();

    @Query("SELECT * FROM User u LEFT OUTER JOIN Pet p ON u.mId = p.mUserId")
    List<UserAndPetNonNull> loadUsersWithNonNullPet();

    @Query("SELECT * FROM Pet p LEFT OUTER JOIN User u ON u.mId = p.mUserId")
    List<UserAndPet> loadPets();

    @Transaction
    @Query("SELECT * FROM User u")
    List<UserAndAllPets> loadAllUsersWithTheirPets();

    @Transaction
    @Query("SELECT * FROM User u")
    List<UserAndAllPetsViaJunction> loadAllUsersWithTheirPetsViaJunction();

    @SuppressWarnings({RoomWarnings.QUERY_MISMATCH, RoomWarnings.CURSOR_MISMATCH})
    @Transaction
    @Query("SELECT * FROM User u")
    List<UserIdAndPetIds> loadUserIdAndPetids();

    @SuppressWarnings({RoomWarnings.QUERY_MISMATCH, RoomWarnings.CURSOR_MISMATCH})
    @Transaction
    @Query("SELECT * FROM User u")
    List<UserIdAndPetNames> loadUserAndPetNames();

    @Transaction
    @Query("SELECT * FROM User u")
    List<UserWithPetsAndToys> loadUserWithPetsAndToys();

    @Transaction
    @Query("SELECT * FROM User UNION ALL SELECT * FROM USER")
    List<UserAndAllPets> unionByItself();

    @Transaction
    @Query("SELECT * FROM User")
    List<UserAndPetAdoptionDates> loadUserWithPetAdoptionDates();

    @Transaction
    @Query("SELECT * FROM User u where u.mId = :userId")
    LiveData<UserAndAllPets> liveUserWithPets(int userId);

    @Transaction
    @Query("SELECT * FROM User u where u.mId = :userId")
    io.reactivex.Flowable<@NonNull UserAndAllPets> rx2_flowableUserWithPets(int userId);

    @Transaction
    @Query("SELECT * FROM User u where u.mId = :userId")
    io.reactivex.rxjava3.core.Flowable<@NonNull UserAndAllPets> rx3_flowableUserWithPets(int userId);

    @Transaction
    @Query("SELECT * FROM User u where u.mId = :userId")
    io.reactivex.Observable<@NonNull UserAndAllPets> rx2_observableUserWithPets(int userId);

    @Transaction
    @Query("SELECT * FROM User u where u.mId = :userId")
    io.reactivex.rxjava3.core.Observable<@NonNull UserAndAllPets> rx3_observableUserWithPets(int userId);

    @Transaction
    @Query("SELECT * FROM User u where u.mId = :uid")
    EmbeddedUserAndAllPets loadUserAndPetsAsEmbedded(int uid);

    @Transaction
    @Query("SELECT mId FROM user")
    List<UserIdAndPetIds> getUserIdsAndPetsIds();

    @Insert
    void insertUserAndPet(User user, Pet pet);

    @Update
    void updateUsersAndPets(User[] users, Pet[] pets);

    @Delete
    void delete2UsersAndPets(User user1, User user2, Pet[] pets);
}
