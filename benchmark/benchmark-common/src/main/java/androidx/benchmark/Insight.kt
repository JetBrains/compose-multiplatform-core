/*
 * Copyright (C) 2024 The Android Open Source Project
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

import androidx.annotation.RestrictTo

/**
 * Individual case of a problem identified in a given trace (from a specific iteration).
 *
 * Benchmark output will summarize multiple insights, for example:
 * ```Markdown
 * [JIT Activity](https://d.android.com/test#JIT_ACTIVITY) (expected: < 100000000ns)
 * seen in iterations: [6](file:///path/to/trace.perfetto-trace)(328462261ns),
 * ```
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
data class Insight(
    /**
     * Category of the Insight, representing the type of problem.
     *
     * Every similar insight must identify equal [Category]s to enable macrobenchmark to group them
     * across iterations.
     */
    val category: Category,

    /** suffix after the deeplink to convey problem severity, e.g. "(100000000ns)" */
    val observedLabel: String,

    /**
     * Link to content within a trace to highlight for the given problem.
     *
     * Insights should specify [TraceDeepLink.SelectionParams] so that the correct process, thread,
     * and time range is highlighted. In addition, the optional
     * [TraceDeepLink.SelectionParams.query] can be specified to highlight specific slices or other
     * events of interest in the trace.
     */
    val deepLink: TraceDeepLink,

    /** Macrobenchmark iteration in which this insight was observed. */
    val iterationIndex: Int,
) {
    /**
     * Category represents general expectations that have been violated by an insight - e.g. JIT
     * shouldn't take longer than XX ms, or trampoline activities shouldn't be used.
     *
     * Every Insight that shares the same conceptual category must share an equal [Category] so that
     * macrobenchmark can group them when summarizing observed patterns across multiple iterations.
     *
     * In Studio versions supporting web links, will produce an output like the following:
     * ```Markdown
     * [JIT compiled methods](https://d.android.com/test#JIT_COMPILED_METHODS) (expected: < 65 count)
     * ```
     *
     * In the above example:
     * - [title] is `"JIT compiled methods"`
     * - [titleUrl] is `"https://d.android.com/test#JIT_COMPILED_METHODS"`
     * - [postTitleLabel] is " (expected: < 65 count)"
     *
     * In Studio versions not supporting web links, [titleUrl] is ignored.
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    data class Category(
        /** Title of the Insight category, for example "JIT compiled methods" */
        val title: String,
        /** Optional web URL which, if non-null, will make the [title] a link. */
        val titleUrl: String?,
        /**
         * Suffix after the title, generally specifying expected values.
         *
         * For example, " (expected: < 65 count)"
         */
        val postTitleLabel: String,
    ) {
        fun header(linkFormat: LinkFormat): String =
            if (linkFormat == LinkFormat.V3 && titleUrl != null) {
                Markdown.createLink(title, titleUrl)
            } else {
                title
            } + postTitleLabel
    }
}
