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

package androidx.lifecycle

import androidx.annotation.Sampled
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer

@Sampled
fun delegate() {
    @Serializable data class User(val id: Int, val name: String)
    class ProfileViewModel(savedStateHandle: SavedStateHandle) : ViewModel() {
        val user by savedStateHandle.saved { User(123, "foo") }
    }
}

@Sampled
fun delegateExplicitKey() {
    @Serializable data class User(val id: Int, val name: String)
    class ProfileViewModel(savedStateHandle: SavedStateHandle) : ViewModel() {
        val user by savedStateHandle.saved(key = "bar") { User(123, "foo") }
    }
}

@OptIn(InternalSerializationApi::class)
@Sampled
fun delegateExplicitSerializer() {
    @Serializable data class User(val id: Int, val name: String)
    class ProfileViewModel(savedStateHandle: SavedStateHandle) : ViewModel() {
        val user by savedStateHandle.saved(User::class.serializer()) { User(123, "foo") }
    }
}

@OptIn(InternalSerializationApi::class)
@Sampled
fun delegateExplicitKeyAndSerializer() {
    @Serializable data class User(val id: Int, val name: String)
    class ProfileViewModel(savedStateHandle: SavedStateHandle) : ViewModel() {
        val user by
            savedStateHandle.saved(key = "bar", serializer = User::class.serializer()) {
                User(123, "foo")
            }
    }
}
