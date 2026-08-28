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

import androidx.compose.runtime.Immutable
import androidx.compose.ui.input.InputMode
import kotlin.coroutines.CoroutineContext
import kotlin.jvm.JvmInline
import kotlin.time.Duration

@Immutable
actual class ComposeUiTestConfig
actual constructor(
    actual val effectContext: CoroutineContext,
    actual val runTestContext: CoroutineContext,
    actual val testTimeout: Duration,
    actual val inputMode: InputMode,
    actual val failurePolicy: TestFailurePolicy,
) {
    @Deprecated("Kept for binary compatibility", level = DeprecationLevel.HIDDEN)
    actual constructor(
        effectContext: CoroutineContext,
        runTestContext: CoroutineContext,
        testTimeout: Duration,
        inputMode: InputMode,
    ) : this(
        effectContext = effectContext,
        runTestContext = runTestContext,
        testTimeout = testTimeout,
        inputMode = inputMode,
        failurePolicy = TestFailurePolicy(),
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ComposeUiTestConfig) return false

        if (effectContext != other.effectContext) return false
        if (runTestContext != other.runTestContext) return false
        if (testTimeout != other.testTimeout) return false
        if (inputMode != other.inputMode) return false
        if (failurePolicy != other.failurePolicy) return false

        return true
    }

    override fun hashCode(): Int {
        var result = effectContext.hashCode()
        result = 31 * result + runTestContext.hashCode()
        result = 31 * result + testTimeout.hashCode()
        result = 31 * result + inputMode.hashCode()
        result = 31 * result + failurePolicy.hashCode()
        return result
    }
}

@Immutable
public actual class TestFailurePolicy
public actual constructor(
    public actual val screenshotCaptureMode: CaptureMode,
    public actual val uiHierarchyCaptureMode: CaptureMode,
    public actual val failureHandlers: List<TestFailureHandler>,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TestFailurePolicy) return false

        if (screenshotCaptureMode != other.screenshotCaptureMode) return false
        if (uiHierarchyCaptureMode != other.uiHierarchyCaptureMode) return false
        if (failureHandlers != other.failureHandlers) return false

        return true
    }

    override fun hashCode(): Int {
        var result = screenshotCaptureMode.hashCode()
        result = 31 * result + uiHierarchyCaptureMode.hashCode()
        result = 31 * result + failureHandlers.hashCode()
        return result
    }

    /**
     * Represents a tri-state flag for failure artifact captures, allowing individual test
     * configurations to explicitly override or fall back to suite-level runner arguments.
     *
     * This is used within [TestFailurePolicy] to dictate whether the test framework should capture
     * diagnostic artifacts (like screenshots or UI hierarchy dumps) when a test fails.
     */
    @JvmInline
    public actual value class CaptureMode private actual constructor(private val value: Int) {
        public actual companion object {
            /** Fall back to the suite-level runner configuration. */
            public actual val Unspecified: CaptureMode = CaptureMode(0)

            /** Explicitly enable the capture for this test, overriding runner configuration. */
            public actual val Enabled: CaptureMode = CaptureMode(1)

            /** Explicitly disable the capture for this test, overriding runner configuration. */
            public actual val Disabled: CaptureMode = CaptureMode(2)
        }

        override fun toString(): String =
            when (this) {
                Unspecified -> "CaptureMode.Unspecified"
                Enabled -> "CaptureMode.Enabled"
                Disabled -> "CaptureMode.Disabled"
                else -> "CaptureMode(value=$value)"
            }
    }
}
