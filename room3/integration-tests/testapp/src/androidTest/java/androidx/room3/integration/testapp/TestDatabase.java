/*
 * Copyright (C) 2016 The Android Open Source Project
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

package androidx.room3.integration.testapp;

import androidx.room3.Database;
import androidx.room3.RoomDatabase;
import androidx.room3.TypeConverter;
import androidx.room3.TypeConverters;
import androidx.room3.integration.testapp.dao.BlobEntityDao;
import androidx.room3.integration.testapp.dao.LibraryItemDao;
import androidx.room3.integration.testapp.dao.PagingSourceOnlyUserDao;
import androidx.room3.integration.testapp.dao.PetCoupleDao;
import androidx.room3.integration.testapp.dao.PetDao;
import androidx.room3.integration.testapp.dao.ProductDao;
import androidx.room3.integration.testapp.dao.RawDao;
import androidx.room3.integration.testapp.dao.RobotsDao;
import androidx.room3.integration.testapp.dao.SchoolDao;
import androidx.room3.integration.testapp.dao.SpecificDogDao;
import androidx.room3.integration.testapp.dao.ToyDao;
import androidx.room3.integration.testapp.dao.UserDao;
import androidx.room3.integration.testapp.dao.UserHouseDao;
import androidx.room3.integration.testapp.dao.UserPetDao;
import androidx.room3.integration.testapp.dao.WithClauseDao;
import androidx.room3.integration.testapp.vo.BlobEntity;
import androidx.room3.integration.testapp.vo.Day;
import androidx.room3.integration.testapp.vo.FriendsJunction;
import androidx.room3.integration.testapp.vo.Hivemind;
import androidx.room3.integration.testapp.vo.House;
import androidx.room3.integration.testapp.vo.Pet;
import androidx.room3.integration.testapp.vo.PetCouple;
import androidx.room3.integration.testapp.vo.PetWithUser;
import androidx.room3.integration.testapp.vo.Product;
import androidx.room3.integration.testapp.vo.Robot;
import androidx.room3.integration.testapp.vo.RoomLibraryPojo;
import androidx.room3.integration.testapp.vo.School;
import androidx.room3.integration.testapp.vo.Toy;
import androidx.room3.integration.testapp.vo.User;

import java.nio.ByteBuffer;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Database(entities = {User.class, Pet.class, School.class, PetCouple.class, Toy.class,
        BlobEntity.class, Product.class, House.class,
        FriendsJunction.class, Hivemind.class, Robot.class, RoomLibraryPojo.class},
        views = {PetWithUser.class},
        version = 1, exportSchema = false)
@TypeConverters(TestDatabase.Converters.class)
public abstract class TestDatabase extends RoomDatabase {
    public abstract UserDao getUserDao();
    public abstract PagingSourceOnlyUserDao getPagingSourceOnlyUserDao();
    public abstract PetDao getPetDao();
    public abstract UserPetDao getUserPetDao();
    public abstract SchoolDao getSchoolDao();
    public abstract PetCoupleDao getPetCoupleDao();
    public abstract ToyDao getToyDao();
    public abstract BlobEntityDao getBlobEntityDao();
    public abstract ProductDao getProductDao();
    public abstract SpecificDogDao getSpecificDogDao();
    public abstract WithClauseDao getWithClauseDao();
    public abstract RawDao getRawDao();
    public abstract UserHouseDao getUserHouseDao();
    public abstract RobotsDao getRobotsDao();
    public abstract LibraryItemDao getLibraryItemDao();
//    public abstract RecordEntityDao getRecordEntityDao();

    @SuppressWarnings("unused")
    public static class Converters {
        @TypeConverter
        public Date fromTimestamp(Long value) {
            return value == null ? null : new Date(value);
        }

        @TypeConverter
        public Long dateToTimestamp(Date date) {
            if (date == null) {
                return null;
            } else {
                return date.getTime();
            }
        }

        @TypeConverter
        public Set<Day> decomposeDays(int flags) {
            Set<Day> result = new HashSet<>();
            for (Day day : Day.values()) {
                if ((flags & (1 << day.ordinal())) != 0) {
                    result.add(day);
                }
            }
            return result;
        }

        @TypeConverter
        public int composeDays(Set<Day> days) {
            int result = 0;
            for (Day day : days) {
                result |= 1 << day.ordinal();
            }
            return result;
        }

        @TypeConverter
        public UUID asUuid(byte[] bytes) {
            ByteBuffer bb = ByteBuffer.wrap(bytes);
            long firstLong = bb.getLong();
            long secondLong = bb.getLong();
            return new UUID(firstLong, secondLong);
        }

        @TypeConverter
        public byte[] asBytes(UUID uuid) {
            ByteBuffer bb = ByteBuffer.wrap(new byte[16]);
            bb.putLong(uuid.getMostSignificantBits());
            bb.putLong(uuid.getLeastSignificantBits());
            return bb.array();
        }
    }
}
