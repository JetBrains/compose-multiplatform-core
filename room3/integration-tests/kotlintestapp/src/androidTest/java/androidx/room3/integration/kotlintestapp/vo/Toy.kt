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
package androidx.room3.integration.kotlintestapp.vo

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey

/** The toys of a pet. */
@Entity(
    indices = [Index(value = ["mName"], unique = true), Index(value = ["mPetId"])],
    foreignKeys =
        [
            ForeignKey(
                entity = Pet::class,
                parentColumns = ["mPetId"],
                childColumns = ["mPetId"],
                deferred = true,
            )
        ],
)
data class Toy(@PrimaryKey(autoGenerate = true) val mId: Int, var mName: String?, var mPetId: Int) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val toy = other as Toy
        if (mId != toy.mId) return false
        if (mPetId != toy.mPetId) return false
        return if (mName != null) mName == toy.mName else toy.mName == null
    }

    override fun hashCode(): Int {
        var result = mId
        result = 31 * result + if (mName != null) mName.hashCode() else 0
        result = 31 * result + mPetId
        return result
    }
}
