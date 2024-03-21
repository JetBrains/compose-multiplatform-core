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

data class ActionContext(val attrsMap: Map<FocusDataKey<*, *>, List<Data<Any?>>> = emptyMap()) {
    operator fun <FocusDataType, AggregateType> get(key: FocusDataKey<FocusDataType, AggregateType>): AggregateType? {
        return key.get(attrsMap)
    }

    fun <FocusDataType, AggregateType> getWeighted(key: FocusDataKey<FocusDataType, AggregateType>): Data<AggregateType>? {
        return key.getWeighted(attrsMap)
    }

    fun <FocusDataType, AggregateType> getAll(key: FocusDataKey<FocusDataType, AggregateType>): List<FocusDataType> {
        return key.getAll(attrsMap)
    }

    fun <FocusDataType, AggregateType> getAllWeighted(key: FocusDataKey<FocusDataType, AggregateType>): List<Data<FocusDataType>> {
        return key.getAllWeighted(attrsMap)
    }

//    fun <FocusDataType> put(
//        uniqueDataKey: UniqueDataKey<FocusDataType>,
//        value: FocusDataType
//    ): ActionContext {
//        return putWeighted(uniqueDataKey, value, focusLayerIndex = 0, weight = 0)
//    }
//
//    fun <FocusDataType> replace(
//        uniqueDataKey: UniqueDataKey<FocusDataType>,
//        value: FocusDataType
//    ): ActionContext {
//        return remove(uniqueDataKey).put(uniqueDataKey, value)
//    }
//
//    fun <FocusDataType> put(
//        lastAppearedDataKey: LastAppearedDataKey<FocusDataType>,
//        value: FocusDataType,
//        focusLayerIndex: Int,
//        weight: Int = 0
//    ): ActionContext {
//        return putWeighted(lastAppearedDataKey, value, focusLayerIndex, weight)
//    }

    fun <FocusDataType, AggregateType> put(
        key: FocusDataKey<FocusDataType, AggregateType>,
        value: FocusDataType,
        anchor: FocusDataKey<*, *>
    ): ActionContext {
        val anchorData = anchor.getWeighted(attrsMap)
        val focusLayerIndex = anchorData?.focusLayerIndex ?: 0
        val weight = anchorData?.weight ?: 0
        return putWeighted(key, value, focusLayerIndex, weight)
    }

    private fun <FocusDataType, AggregateType> putWeighted(
        key: FocusDataKey<FocusDataType, AggregateType>,
        value: FocusDataType,
        focusLayerIndex: Int,
        weight: Int
    ): ActionContext {
        val newMap: MutableMap<FocusDataKey<*, *>, List<Data<Any?>>> = attrsMap.toMutableMap()
        newMap.putWeighted(key, value, focusLayerIndex, weight)
        return ActionContext(newMap)
    }

    fun <FocusDataType, AggregateType> remove(key: FocusDataKey<FocusDataType, AggregateType>): ActionContext {
        val newMap: MutableMap<FocusDataKey<*, *>, List<Data<Any?>>> = attrsMap.toMutableMap()
        newMap.remove(key)
        return ActionContext(newMap)
    }

    override fun toString(): String {
        return attrsMap.keys.toString()
    }

    data class Data<ValueType>(val value: ValueType, val focusLayerIndex: Int, val weight: Int) :
        Comparable<Data<*>> {
        override fun compareTo(other: Data<*>): Int {
            return when {
                focusLayerIndex < other.focusLayerIndex -> -1
                focusLayerIndex > other.focusLayerIndex -> 1
                weight < other.weight -> -1
                weight > other.weight -> 1
                else -> 0
            }
        }
    }
}


interface FocusDataKey<FocusDataType, AggregateType> {
    val identifier: String
    fun init(data: ActionContext.Data<FocusDataType>): ActionContext.Data<AggregateType>
    fun reduce(
        existingAggregateData: ActionContext.Data<AggregateType>,
        newFocusData: ActionContext.Data<FocusDataType>
    ): ActionContext.Data<AggregateType>

    fun get(map: Map<FocusDataKey<*, *>, List<ActionContext.Data<Any?>>>): AggregateType? {
        return getWeighted(map)?.value
    }

    fun getWeighted(map: Map<FocusDataKey<*, *>, List<ActionContext.Data<Any?>>>): ActionContext.Data<AggregateType>? {
        return getAllWeighted(map).fold(null as ActionContext.Data<AggregateType>?) { existingAggregateData, newFocusData ->
            if (existingAggregateData != null) {
                reduce(existingAggregateData, newFocusData)
            } else {
                init(newFocusData)
            }
        }
    }

    fun getAll(map: Map<FocusDataKey<*, *>, List<ActionContext.Data<Any?>>>): List<FocusDataType> {
        return getAllWeighted(map).map { it.value }
    }

    fun getAllWeighted(map: Map<FocusDataKey<*, *>, List<ActionContext.Data<Any?>>>): List<ActionContext.Data<FocusDataType>> {
        @Suppress("UNCHECKED_CAST")
        return (map[this] as? List<ActionContext.Data<FocusDataType>>)?.toList() ?: emptyList()
    }
}

fun <FocusDataType, AggregateType> MutableMap<FocusDataKey<*, *>, List<ActionContext.Data<Any?>>>.putWeighted(
    key: FocusDataKey<FocusDataType, AggregateType>,
    value: FocusDataType,
    focusLayerIndex: Int,
    weight: Int
) {
    @Suppress("UNCHECKED_CAST")
    val map =
        (this as MutableMap<FocusDataKey<FocusDataType, AggregateType>, MutableList<ActionContext.Data<FocusDataType>>>)
    val data = ActionContext.Data(value, focusLayerIndex, weight)
    if (key !in map) {
        map[key] = mutableListOf(data)
    } else {
        map[key]!!.add(data)
    }
}
