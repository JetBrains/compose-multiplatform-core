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

@file:JvmName("TestNavigatorDestinationBuilderKt")
@file:JvmMultifileClass

package androidx.testutils

import androidx.navigation.NavDestinationBuilder
import androidx.navigation.NavDestinationDsl
import androidx.navigation.NavGraphBuilder
import androidx.navigation.get
import kotlin.jvm.JvmMultifileClass
import kotlin.jvm.JvmName

/**
 * Construct a new [TestNavigator.Destination]
 */
inline fun NavGraphBuilder.test(route: String) = test(route) {}

/**
 * Construct a new [TestNavigator.Destination]
 */
inline fun NavGraphBuilder.test(
    route: String,
    builder: TestNavigatorDestinationBuilder.() -> Unit
) = destination(
    TestNavigatorDestinationBuilder(provider["test"/* TEST_NAVIGATOR_NAME */], route).apply(builder)
)

/**
 * DSL for constructing a new [TestNavigator.Destination]
 */
@NavDestinationDsl
expect class TestNavigatorDestinationBuilder : NavDestinationBuilder<TestNavigator.Destination> {
    @Suppress("ConvertSecondaryConstructorToPrimary")
    constructor(navigator: TestNavigator, route: String)
}
