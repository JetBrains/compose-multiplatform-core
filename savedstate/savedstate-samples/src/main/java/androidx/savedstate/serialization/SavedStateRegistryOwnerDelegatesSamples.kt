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

package androidx.savedstate.serialization

import androidx.activity.ComponentActivity
import androidx.annotation.Sampled
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer

@Sampled
fun savedStateRegistryOwner_saved() {
    @Serializable data class User(val id: Int, val name: String)
    class MyActivity : ComponentActivity() {
        val user by saved { User(id = 123, name = "John Doe") }
    }
}

@Sampled
fun savedStateRegistryOwner_saved_withKey_withSerializer() {
    @Serializable data class User(val id: Int, val name: String)
    class MyActivity : ComponentActivity() {
        val user by
            saved(key = "myKey", serializer = serializer()) { User(id = 123, name = "John Doe") }
    }
}

@Sampled
fun savedStateRegistryOwner_saved_withKey() {
    @Serializable data class User(val id: Int, val name: String)
    class MyActivity : ComponentActivity() {
        val user by saved(key = "myKey") { User(id = 123, name = "John Doe") }
    }
}

@Sampled
fun savedStateRegistryOwner_saved_withSerializer() {
    @Serializable data class User(val id: Int, val name: String)
    class MyActivity : ComponentActivity() {
        val user by saved(serializer = serializer()) { User(id = 123, name = "John Doe") }
    }
}
