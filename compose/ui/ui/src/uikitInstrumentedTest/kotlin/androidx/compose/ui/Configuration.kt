/*
 * Copyright 2025 The Android Open Source Project
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

package androidx.compose.ui

import androidx.compose.xctest.setupXCTestSuite
import kotlin.experimental.ExperimentalNativeApi
import kotlinx.cinterop.ExperimentalForeignApi
import platform.XCTest.XCTestSuite

/**
 * Qualified name prefix of the test classes marked with
 * [androidx.compose.ui.test.FullKeyboardAccessTest]. Such tests are placed in the
 * `androidx.compose.ui.accessibility.fullkeyboardaccess` package and are run by
 * [fullKeyboardAccessTestSuite] only — see the annotation for why the package is the runtime
 * marker of the tests that require Full Keyboard Access.
 */
private const val FULL_KEYBOARD_ACCESS_TESTS_PACKAGE =
    "androidx.compose.ui.accessibility.fullkeyboardaccess."

/**
 * All instrumented tests except the ones that require Full Keyboard Access on the simulator.
 *
 * To run only specific test classes or test cases instead of everything, call [setupXCTestSuite]
 * with them explicitly:
 * ```
 * setupXCTestSuite(tests = setOf(BasicInteractionTest::class))
 * setupXCTestSuite(
 *     testCases = mapOf(
 *         BasicInteractionTest::class.qualifiedName to setOf(BasicInteractionTest::testTextFieldCallout)
 *     )
 * )
 * ```
 */
@Suppress("unused")
@OptIn(ExperimentalForeignApi::class, ExperimentalNativeApi::class)
fun testSuite(): XCTestSuite = setupXCTestSuite(
    suiteFilter = { !it.name.startsWith(FULL_KEYBOARD_ACCESS_TESTS_PACKAGE) }
).also { suite ->
    @Suppress("UNCHECKED_CAST")
    check((suite.tests as List<*>).isNotEmpty()) {
        "The general test suite is empty: all tests appear to require Full Keyboard Access"
    }
}

/**
 * Only the tests that require Full Keyboard Access on the simulator, marked with
 * [androidx.compose.ui.test.FullKeyboardAccessTest].
 */
@Suppress("unused")
@OptIn(ExperimentalForeignApi::class, ExperimentalNativeApi::class)
fun fullKeyboardAccessTestSuite(): XCTestSuite = setupXCTestSuite(
    suiteFilter = { it.name.startsWith(FULL_KEYBOARD_ACCESS_TESTS_PACKAGE) }
).also { suite ->
    @Suppress("UNCHECKED_CAST")
    check((suite.tests as List<*>).isNotEmpty()) {
        "The Full Keyboard Access test suite is empty: expected tests marked with " +
            "@FullKeyboardAccessTest in the '$FULL_KEYBOARD_ACCESS_TESTS_PACKAGE' package"
    }
}
