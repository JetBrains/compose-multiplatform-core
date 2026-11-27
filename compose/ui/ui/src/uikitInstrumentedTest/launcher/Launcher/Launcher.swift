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
 
import XCTest
import InstrumentedTest

class TestLauncher: XCTestCase {
    override class var defaultTestSuite: XCTestSuite {
        // Set the FULL_KEYBOARD_ACCESS_TESTS environment variable to "1" to run only the tests
        // that require Full Keyboard Access on the simulator. With `xcodebuild test` it is
        // passed through the TEST_RUNNER_ prefix:
        //   TEST_RUNNER_FULL_KEYBOARD_ACCESS_TESTS=1 xcodebuild test ...
        if ProcessInfo.processInfo.environment["FULL_KEYBOARD_ACCESS_TESTS"] == "1" {
            return ConfigurationKt.fullKeyboardAccessTestSuite()
        }
        return ConfigurationKt.testSuite()
    }
}
