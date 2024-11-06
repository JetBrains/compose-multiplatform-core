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

package androidx.benchmark.macro

import androidx.benchmark.Insight
import androidx.benchmark.LinkFormat
import androidx.benchmark.Markdown
import androidx.benchmark.Outputs
import androidx.benchmark.StartupInsightsConfig
import androidx.benchmark.TraceDeepLink
import androidx.benchmark.inMemoryTrace
import androidx.benchmark.perfetto.PerfettoTraceProcessor
import androidx.benchmark.perfetto.Row
import java.net.URLEncoder
import perfetto.protos.AndroidStartupMetric.SlowStartReason
import perfetto.protos.AndroidStartupMetric.ThresholdValue.ThresholdUnit

/**
 * Aggregates raw SlowStartReason results into a list of [Insight]s - in a format easier to display
 * in the IDE as a summary.
 *
 * TODO(353692849): add unit tests
 */
internal fun createInsightsIdeSummary(
    startupInsightsConfig: StartupInsightsConfig?,
    iterationResults: List<IterationResult>
): List<Insight> {
    fun createInsightString(
        criterion: SlowStartReason,
        observed: List<IndexedValue<SlowStartReason>>
    ): Insight {
        observed.forEach {
            require(it.value.reason_id == criterion.reason_id)
            require(it.value.expected_value == criterion.expected_value)
        }

        val expectedValue = requireNotNull(criterion.expected_value)
        val thresholdUnit = requireNotNull(expectedValue.unit)
        require(thresholdUnit != ThresholdUnit.THRESHOLD_UNIT_UNSPECIFIED)
        val unitSuffix =
            when (thresholdUnit) {
                ThresholdUnit.NS -> "ns"
                ThresholdUnit.PERCENTAGE -> "%"
                ThresholdUnit.COUNT -> " count"
                ThresholdUnit.TRUE_OR_FALSE -> ""
                else -> " ${thresholdUnit.toString().lowercase()}"
            }

        val criterionString = buildString {
            val reasonHelpUrlBase = startupInsightsConfig?.reasonHelpUrlBase
            if (reasonHelpUrlBase != null) {
                append("[")
                append(requireNotNull(criterion.reason).replace("]", "\\]"))
                append("]")
                append("(")
                append(reasonHelpUrlBase.replace(")", "\\)")) // base url
                val reasonId = requireNotNull(criterion.reason_id).name
                append(URLEncoder.encode(reasonId, Charsets.UTF_8.name())) // reason id as a suffix
                append(")")
            } else {
                append(requireNotNull(criterion.reason))
            }

            val thresholdValue = requireNotNull(expectedValue.value_)
            append(" (expected: ")
            if (thresholdUnit == ThresholdUnit.TRUE_OR_FALSE) {
                require(thresholdValue in 0L..1L)
                if (thresholdValue == 0L) append("false")
                if (thresholdValue == 1L) append("true")
            } else {
                if (expectedValue.higher_expected == true) append("> ")
                if (expectedValue.higher_expected == false) append("< ")
                append(thresholdValue)
                append(unitSuffix)
            }
            append(")")
        }

        val observedMap =
            listOf(LinkFormat.V2, LinkFormat.V3).associate { linkFormat ->
                val observedString =
                    observed.joinToString(" ", "seen in iterations: ") {
                        val observedValue = requireNotNull(it.value.actual_value?.value_)
                        val observedString: String =
                            if (thresholdUnit == ThresholdUnit.TRUE_OR_FALSE) {
                                require(observedValue in 0L..1L)
                                if (observedValue == 0L) "false" else "true"
                            } else {
                                "$observedValue$unitSuffix"
                            }

                        // TODO(364590575): implement zoom-in on relevant parts of the trace and
                        // then make
                        //  the 'actualString' also part of the link.
                        val tracePath = iterationResults.getOrNull(it.index)?.tracePath
                        if (tracePath == null) "${it.index}($observedString)"
                        else
                            when (linkFormat) {
                                LinkFormat.V2 -> {
                                    val relativePath = Outputs.relativePathFor(tracePath)
                                    val link = Markdown.createFileLink("${it.index}", relativePath)
                                    "$link($observedString)"
                                }
                                LinkFormat.V3 -> {
                                    TraceDeepLink(
                                            outputRelativePath = Outputs.relativePathFor(tracePath),
                                            selectionParams =
                                                iterationResults[it.index]
                                                    .defaultStartupInsightSelectionParams
                                        )
                                        .createMarkdownLink(
                                            label = "${it.index}($observedString)",
                                            linkFormat = LinkFormat.V3
                                        )
                                }
                            }
                    }
                Pair(linkFormat, observedString)
            }

        return Insight(
            criterion = criterionString,
            observedV2 = observedMap[LinkFormat.V2]!!,
            observedV3 = observedMap[LinkFormat.V3]!!
        )
    }

    // Pivot from List<iteration_id -> insight_list> to List<insight -> iteration_list>
    // and convert to a format expected in Studio text output.
    return iterationResults
        .map { it.insights }
        .flatMapIndexed { iterationId, insights -> insights.map { IndexedValue(iterationId, it) } }
        .groupBy { it.value.reason_id }
        .values
        .map { createInsightString(it.first().value, it) }
}

/**
 * Sets [TraceDeepLink.SelectionParams] based on the last `Startup` slice. Temporary hack until we
 * get this information directly from [SlowStartReason].
 */
internal fun PerfettoTraceProcessor.Session.extractStartupSliceSelectionParams(
    packageName: String
): TraceDeepLink.SelectionParams? {
    inMemoryTrace("extractStartupSliceSelectionParams") {
        // note: not using utid, upid as not stable between trace processor releases
        // also note: https://perfetto.dev/docs/analysis/sql-tables#process
        // also note: https://perfetto.dev/docs/analysis/sql-tables#thread
        val query =
            """
                    select
                        process.pid as pid,
                        thread.tid as tid,
                        slice.ts,
                        slice.dur,
                        slice.name, -- left for debugging, can be removed
                        process.name as process_name -- left for debugging, can be removed
                    from slice
                        join thread_track on thread_track.id = slice.track_id
                        join thread using(utid)
                        join process using(upid)
                    where slice.name = 'Startup' and process.name like '${packageName}%'
                    """
                .trimIndent()

        val events = query(query).toList()
        if (events.isEmpty()) {
            return null
        } else {
            val queryResult: Row = events.first()
            return TraceDeepLink.SelectionParams(
                pid = queryResult.long("pid"),
                tid = queryResult.long("tid"),
                ts = queryResult.long("ts"),
                dur = queryResult.long("dur"),
                // query belongs in the [SlowStartReason] object (to be added there)
                // we can't do anything here now, so setting it to something that will test
                // that we're handling Unicode correctly
                // see: http://shortn/_yWn9yR2OHr
                query = "SELECT 🐲\nFROM 🐉\nWHERE \ud83d\udc09.NAME = 'ハク'"
            )
        }
    }
}
