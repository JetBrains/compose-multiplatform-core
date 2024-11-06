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

import androidx.kruth.assertThat
import androidx.kruth.assertThrows
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.Lifecycle.State
import androidx.lifecycle.LifecycleRegistry
import androidx.savedstate.RobolectricTest
import androidx.savedstate.SavedState
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.read
import androidx.savedstate.savedState
import kotlin.test.Test
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.serializer

internal class SavedStateRegistryOwnerDelegatesTest : RobolectricTest() {

    @Test
    fun saved_defaultKey_performSave() {
        val owner =
            FakeSavedStateRegistryOwner().apply {
                savedStateRegistryController.performAttach()
                savedStateRegistryController.performRestore(savedState = null)
                lifecycleRegistry.currentState = State.CREATED
            }

        var property by owner.saved(Int.serializer()) { Int.MIN_VALUE }
        property = Int.MAX_VALUE

        val actualState = savedState()
        owner.savedStateRegistryController.performSave(actualState)

        val expectedState =
            createRestoredState(DEFAULT_KEY_PROPERTY, Int.serializer(), Int.MAX_VALUE)
        assertThat(actualState.read { contentDeepEquals(expectedState) }).isTrue()
    }

    @Test
    fun saved_defaultKey_accessedBeforeRestore_throwsIllegalStateException() {
        val owner =
            FakeSavedStateRegistryOwner().apply { savedStateRegistryController.performAttach() }

        val property by owner.saved { Int.MAX_VALUE }

        assertThrows<IllegalStateException> { @Suppress("UNUSED_EXPRESSION") property }
            .hasMessageThat()
            .contains(CONSUME_RESTORED_STATE_FOR_KEY_BEFORE_CREATED_ERROR_MESSAGE)
    }

    @Test
    fun saved_defaultKey_noSerializer_throwsSerializationException() {
        val owner =
            FakeSavedStateRegistryOwner().apply {
                savedStateRegistryController.performAttach()
                savedStateRegistryController.performRestore(savedState = null)
                lifecycleRegistry.currentState = State.CREATED
            }

        assertThrows<SerializationException> {
                @Suppress("UNUSED_VARIABLE") val property by owner.saved { NotSerializable() }
            }
            .hasMessageThat()
            // The error message varies across platforms, so the check targets a consistent element.
            .contains(SERIALIZER_NOT_FOUND_ERROR_MESSAGE)
    }

    @Test
    fun saved_defaultKey_accessedAfterRestore_nullSavedState_returnsInitialValue() {
        val owner =
            FakeSavedStateRegistryOwner().apply {
                savedStateRegistryController.performAttach()
                savedStateRegistryController.performRestore(savedState = null)
                lifecycleRegistry.currentState = State.CREATED
            }

        val property by owner.saved { Int.MAX_VALUE }

        assertThat(property).isEqualTo(Int.MAX_VALUE)
    }

    @Test
    fun saved_defaultKey_accessedAfterRestore_emptySavedState_returnsInitialValue() {
        val owner =
            FakeSavedStateRegistryOwner().apply {
                savedStateRegistryController.performAttach()
                savedStateRegistryController.performRestore(savedState = savedState())
                lifecycleRegistry.currentState = State.CREATED
            }

        val property by owner.saved { Int.MAX_VALUE }

        assertThat(property).isEqualTo(Int.MAX_VALUE)
    }

    @Test
    fun saved_defaultKey_accessedAfterRestore_filledSavedState_returnsRestoredValue() {
        val restoredState =
            createRestoredState(DEFAULT_KEY_PROPERTY_1, Int.serializer(), Int.MIN_VALUE)
        val owner =
            FakeSavedStateRegistryOwner().apply {
                savedStateRegistryController.performAttach()
                savedStateRegistryController.performRestore(restoredState)
                lifecycleRegistry.currentState = State.CREATED
            }

        val property1 by owner.saved { Int.MAX_VALUE }
        val property2 by owner.saved { Float.MAX_VALUE }

        assertThat(property1).isEqualTo(Int.MIN_VALUE)
        assertThat(property2).isEqualTo(Float.MAX_VALUE)
    }

    @Test
    fun saved_customKey_performSave() {
        val owner =
            FakeSavedStateRegistryOwner().apply {
                savedStateRegistryController.performAttach()
                savedStateRegistryController.performRestore(savedState = null)
                lifecycleRegistry.currentState = State.CREATED
            }

        var property by owner.saved(CUSTOM_KEY) { Int.MIN_VALUE }
        property = Int.MAX_VALUE

        val actualState = savedState()
        owner.savedStateRegistryController.performSave(actualState)

        val expectedState = createRestoredState(CUSTOM_KEY, Int.serializer(), Int.MAX_VALUE)
        assertThat(actualState.read { contentDeepEquals(expectedState) }).isTrue()
    }

    @Test
    fun saved_customKey_accessedBeforeRestore_throwsIllegalStateException() {
        val owner =
            FakeSavedStateRegistryOwner().apply { savedStateRegistryController.performAttach() }

        val property by owner.saved(CUSTOM_KEY) { Int.MAX_VALUE }

        assertThrows<IllegalStateException> { @Suppress("UNUSED_EXPRESSION") property }
            .hasMessageThat()
            .contains(CONSUME_RESTORED_STATE_FOR_KEY_BEFORE_CREATED_ERROR_MESSAGE)
    }

    @Test
    fun saved_customKey_noSerializer_throwsSerializationException() {
        val owner =
            FakeSavedStateRegistryOwner().apply {
                savedStateRegistryController.performAttach()
                savedStateRegistryController.performRestore(savedState = null)
                lifecycleRegistry.currentState = State.CREATED
            }

        assertThrows<SerializationException> {
                @Suppress("UNUSED_VARIABLE") val property by owner.saved { NotSerializable() }
            }
            .hasMessageThat()
            // The error message varies across platforms, so the check targets a consistent element.
            .contains(SERIALIZER_NOT_FOUND_ERROR_MESSAGE)
    }

    @Test
    fun saved_customKey_accessedAfterRestore_nullSavedState_returnsInitialValue() {
        val owner =
            FakeSavedStateRegistryOwner().apply {
                savedStateRegistryController.performAttach()
                savedStateRegistryController.performRestore(savedState = null)
                lifecycleRegistry.currentState = State.CREATED
            }

        val property by owner.saved(CUSTOM_KEY) { Int.MAX_VALUE }

        assertThat(property).isEqualTo(Int.MAX_VALUE)
    }

    @Test
    fun saved_customKey_accessedAfterRestore_emptySavedState_returnsInitialValue() {
        val owner =
            FakeSavedStateRegistryOwner().apply {
                savedStateRegistryController.performAttach()
                savedStateRegistryController.performRestore(savedState = savedState())
                lifecycleRegistry.currentState = State.CREATED
            }

        val property by owner.saved(CUSTOM_KEY) { Int.MAX_VALUE }

        assertThat(property).isEqualTo(Int.MAX_VALUE)
    }

    @Test
    fun saved_customKey_accessedAfterRestore_filledSavedState_returnsRestoredValue() {
        val restoredState = createRestoredState(CUSTOM_KEY_1, Int.serializer(), Int.MIN_VALUE)
        val owner =
            FakeSavedStateRegistryOwner().apply {
                savedStateRegistryController.performAttach()
                savedStateRegistryController.performRestore(restoredState)
                lifecycleRegistry.currentState = State.CREATED
            }

        val property1 by owner.saved(CUSTOM_KEY_1) { Int.MAX_VALUE }
        val property2 by owner.saved(CUSTOM_KEY_2) { Float.MAX_VALUE }

        assertThat(property1).isEqualTo(Int.MIN_VALUE)
        assertThat(property2).isEqualTo(Float.MAX_VALUE)
    }

    private companion object TestUtils {

        private const val CONSUME_RESTORED_STATE_FOR_KEY_BEFORE_CREATED_ERROR_MESSAGE =
            "You can 'consumeRestoredStateForKey' only after the corresponding component has moved to the 'CREATED' state"

        // The error message varies across platforms, so the check targets a consistent element.
        private const val SERIALIZER_NOT_FOUND_ERROR_MESSAGE =
            "Please ensure that class is marked as '@Serializable' and that the serialization compiler plugin is applied."

        private const val SAVED_COMPONENTS_KEY =
            "androidx.lifecycle.BundlableSavedStateRegistry.key"

        private const val CUSTOM_KEY = "CUSTOM_KEY"
        private const val CUSTOM_KEY_1 = "CUSTOM_KEY_1"
        private const val CUSTOM_KEY_2 = "CUSTOM_KEY_2"
        private const val DEFAULT_KEY_PROPERTY = "property"
        private const val DEFAULT_KEY_PROPERTY_1 = "property1"

        private fun <T : Any> createRestoredState(
            key: String,
            serializer: KSerializer<T>,
            value: T
        ): SavedState {
            val components = savedState {
                putSavedState(key, encodeToSavedState(serializer, value))
            }
            return savedState { putSavedState(SAVED_COMPONENTS_KEY, components) }
        }

        data class NotSerializable(val placeholder: Int = Int.MIN_VALUE)

        private class FakeSavedStateRegistryOwner : SavedStateRegistryOwner {
            val lifecycleRegistry = LifecycleRegistry.createUnsafe(owner = this)
            val savedStateRegistryController = SavedStateRegistryController.create(owner = this)
            override val lifecycle: Lifecycle = lifecycleRegistry
            override val savedStateRegistry: SavedStateRegistry =
                savedStateRegistryController.savedStateRegistry
        }
    }
}
