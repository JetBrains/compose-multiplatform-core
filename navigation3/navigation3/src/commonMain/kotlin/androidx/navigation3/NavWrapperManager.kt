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

package androidx.navigation3

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/** Creates a [NavContentWrapper]. */
@Composable
public fun rememberNavWrapperManager(
    navContentWrappers: List<NavContentWrapper>
): NavWrapperManager {
    return remember { NavWrapperManager(navContentWrappers) }
}

/**
 * Class that manages all of the provided [NavContentWrapper]. It is responsible for executing the
 * functions provided by each [NavContentWrapper] appropriately.
 *
 * Note: the order in which the [NavContentWrapper]s are added to the list determines their scope,
 * i.e. a [NavContentWrapper] added earlier in a list has its data available to those added later.
 *
 * @param navContentWrappers the [NavContentWrapper]s that are providing data to the content
 */
public class NavWrapperManager(navContentWrappers: List<NavContentWrapper>) {
    /**
     * Final list of wrappers. This always adds a [SaveableStateNavContentWrapper] by default, as it
     * is required. It then filters out any duplicates to ensure there is always one instance of any
     * wrapper at a given time.
     */
    private val finalWrappers =
        (navContentWrappers + listOf(SaveableStateNavContentWrapper())).distinct()

    /**
     * Calls the [NavContentWrapper.WrapBackStack] functions on each wrapper
     *
     * This function is called by the [NavDisplay](reference/androidx/navigation/NavDisplay) and
     * should not be called directly.
     */
    @Composable
    public fun PrepareBackStack(backStack: List<Any>) {
        finalWrappers.distinct().forEach { it.WrapBackStack(backStack = backStack) }
    }

    /**
     * Calls the [NavContentWrapper.WrapContent] functions on each wrapper.
     *
     * This function is called by the [NavDisplay](reference/androidx/navigation/NavDisplay) and
     * should not be called directly.
     */
    @Composable
    public fun <T : Any> ContentForRecord(record: NavRecord<T>) {
        val key = record.key
        finalWrappers
            .distinct()
            .foldRight(record.content) { wrapper, contentLambda ->
                { wrapper.WrapContent(NavRecord(key, record.featureMap, content = contentLambda)) }
            }
            .invoke(key)
    }
}
