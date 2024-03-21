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

package androidx.compose.ui.actionContext

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.semantics

fun Modifier.focusData(block: FocusDataContext.() -> Unit): Modifier {
    return this.semantics {
        val focusDataContext = SemanticsFocusDataContext(this)
        focusDataContext.block()
    }
}

object MetaData
interface MetaDataKey<T : Any>
interface FocusDataContext {
    fun <FocusDataType, AggregateType> get(key: FocusDataKey<FocusDataType, AggregateType>): AggregateType?
    fun <FocusDataType, AggregateType> put(
        key: FocusDataKey<FocusDataType, AggregateType>,
        value: FocusDataType
    )
}

internal data class FocusDataSemanticsValue<FocusDataType, AggregateType>(
    val key: FocusDataKey<FocusDataType, AggregateType>,
    val value: FocusDataType
)

private class SemanticsFocusDataContext(private val propertyReceiver: SemanticsPropertyReceiver) :
    FocusDataContext {
    override fun <FocusDataType, AggregateType> get(key: FocusDataKey<FocusDataType, AggregateType>): AggregateType? {
        TODO("Not yet implemented")
    }

    override fun <FocusDataType, AggregateType> put(
        key: FocusDataKey<FocusDataType, AggregateType>,
        value: FocusDataType
    ) {
        val semanticsKey =
            SemanticsPropertyKey<FocusDataSemanticsValue<FocusDataType, AggregateType>>(
                key.identifier
            )
        propertyReceiver[semanticsKey] = FocusDataSemanticsValue(key, value)
    }
}

data class UniqueDataKey<T>(override val identifier: String, val errorHint: String? = null) :
    FocusDataKey<T, T> {
    override fun init(data: ActionContext.Data<T>): ActionContext.Data<T> = data
    override fun reduce(
        existingAggregateData: ActionContext.Data<T>,
        newFocusData: ActionContext.Data<T>
    ): ActionContext.Data<T> =
        error("Unique DataKey ${identifier} is provided more than one time. " + errorHint)
}