/*
 * Copyright 2026 The Android Open Source Project
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

package androidx.compose.ui.test

/**
 * Marks an instrumented test class as requiring Full Keyboard Access enabled on the iOS
 * Simulator: without it the iPhone has no UIKit focus system and the test cannot run.
 *
 * A test class marked with this annotation must be placed in the
 * `androidx.compose.ui.accessibility.fullkeyboardaccess` package. The test suites are selected
 * by the class qualified name at runtime (Kotlin/Native does not support runtime annotation
 * reflection):
 * - [androidx.compose.ui.fullKeyboardAccessTestSuite] runs only the tests marked with this
 *   annotation;
 * - [androidx.compose.ui.testSuite] runs all the other tests.
 *
 * Full Keyboard Access is activated on a simulator by setting the
 * `FullKeyboardAccessEnabled` preference in `com.apple.Accessibility` **and** attaching a
 * hardware keyboard to the simulator, which is done by opening the booted device in
 * Simulator.app. See the `Configure Simulator` step of
 * `.github/actions/setup-ios-instrumented-test-environment/action.yml` and the
 * `Enable Full Keyboard Access` step of `.github/workflows/tests.yml` for how CI does it. The
 * activated state is lost when the simulator is shut down.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class FullKeyboardAccessTest
