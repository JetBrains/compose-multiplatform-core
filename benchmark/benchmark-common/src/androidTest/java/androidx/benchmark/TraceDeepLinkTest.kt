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

package androidx.benchmark

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class TraceDeepLinkTest {
    @Test
    fun noParamLink() {
        val emptyLink =
            TraceDeepLink(outputRelativePath = "foo.perfetto-trace", selectionParams = null)
        assertThat(emptyLink.createMarkdownLink("bar", LinkFormat.V2))
            .isEqualTo("[bar](file://foo.perfetto-trace)")
        assertThat(emptyLink.createMarkdownLink("bar", LinkFormat.V3))
            .isEqualTo("[bar](uri://foo.perfetto-trace)")
    }

    @Test
    fun paramLink() {
        val emptyLink =
            TraceDeepLink(
                outputRelativePath = "foo.perfetto-trace",
                selectionParams =
                    TraceDeepLink.SelectionParams(
                        pid = 1,
                        tid = null,
                        ts = 0,
                        dur = 100,
                        query = null
                    )
            )
        assertThat(emptyLink.createMarkdownLink("bar", LinkFormat.V2))
            .isEqualTo("[bar](file://foo.perfetto-trace)")
        assertThat(emptyLink.createMarkdownLink("bar", LinkFormat.V3))
            .isEqualTo(
                "[bar](uri://foo.perfetto-trace?selectionParams=eNoryEyxNVQrKbY1UEspLbI1NDAAADbIBWU=)"
            )
    }
}
