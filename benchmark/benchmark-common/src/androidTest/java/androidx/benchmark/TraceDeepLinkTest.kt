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
    fun benchmarkParamLink() {
        val selectionParams =
            TraceDeepLink.BenchmarkSelectionParams(
                packageName = Packages.FAKE,
                tid = null,
                selectionStart = 0,
                selectionEnd = 100,
                query = "select * from slices"
            )
        assertThat(selectionParams.buildInnerParamString())
            .isEqualTo(
                "packageName=com.notalive.package.notarealapp&selection_start=0&selection_end=100&query=select+*+from+slices"
            )

        val link =
            TraceDeepLink(
                outputRelativePath = "foo.perfetto-trace",
                selectionParams = selectionParams
            )
        assertThat(link.createMarkdownLink("bar", LinkFormat.V2))
            .isEqualTo("[bar](file://foo.perfetto-trace)")
        assertThat(link.createMarkdownLink("bar", LinkFormat.V3))
            .isEqualTo(
                "[bar](uri://foo.perfetto-trace?enablePlugins=androidx.benchmark&selectionParams=eNpNyUEKgCAQBdDbuEgQO4BX6AoxTL-Q1DG1oNsXuWn7XibeacNEEY4lmiSNgr9gco8PCihQzqoigJuXNNcXm7M_QVrcaK06TpTbddeDXotEXYNn1AegNihK)"
            )
    }

    @Test
    fun startupParamLink() {
        val selectionParams =
            TraceDeepLink.StartupSelectionParams(
                packageName = Packages.FAKE,
                tid = null,
                selectionStart = null,
                selectionEnd = null,
                reasonId = "REASON_SAMPLE"
            )
        assertThat(selectionParams.buildInnerParamString())
            .isEqualTo("packageName=com.notalive.package.notarealapp&reason_id=REASON_SAMPLE")

        val link =
            TraceDeepLink(
                outputRelativePath = "foo.perfetto-trace",
                selectionParams = selectionParams
            )
        assertThat(link.createMarkdownLink("bar", LinkFormat.V2))
            .isEqualTo("[bar](file://foo.perfetto-trace)")
        assertThat(link.createMarkdownLink("bar", LinkFormat.V3))
            .isEqualTo(
                "[bar](uri://foo.perfetto-trace?enablePlugins=android_startup&selectionParams=eNorSEzOTkxP9UvMTbVNzs_Vy8svSczJLEvVK4BIgAWKUhNzEgsK1IB0cX5efGaKbZCrY7C_X3ywo2-AjysAgIgZGQ==)"
            )
    }
}
