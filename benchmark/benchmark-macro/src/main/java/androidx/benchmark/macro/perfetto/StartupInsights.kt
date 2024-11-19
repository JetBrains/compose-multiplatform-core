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

package androidx.benchmark.macro.perfetto

import androidx.benchmark.Insight
import androidx.benchmark.TraceDeepLink
import androidx.benchmark.inMemoryTrace
import androidx.benchmark.perfetto.PerfettoTraceProcessor
import androidx.benchmark.perfetto.Row
import perfetto.protos.AndroidStartupMetric.SlowStartReason
import perfetto.protos.AndroidStartupMetric.ThresholdValue.ThresholdUnit
import perfetto.protos.TraceMetrics

/**
 * Convert the SlowStartReason concept from Perfetto's android_startup metric to the Macrobenchmark
 * generic Insight format
 */
private fun SlowStartReason.toInsight(
    helpUrlBase: String?,
    traceOutputRelativePath: String,
    iterationIndex: Int,
    defaultStartupInsightSelectionParams: TraceDeepLink.SelectionParams?
): Insight {
    val thresholdUnit = expected_value!!.unit!!
    val unitSuffix =
        when (thresholdUnit) {
            ThresholdUnit.NS -> "ns"
            ThresholdUnit.PERCENTAGE -> "%"
            ThresholdUnit.COUNT -> " count"
            ThresholdUnit.TRUE_OR_FALSE -> ""
            else -> " ${thresholdUnit.toString().lowercase()}"
        }

    val thresholdValue = expected_value.value_!!

    val thresholdString =
        StringBuilder()
            .apply {
                append(" (expected: ")
                if (thresholdUnit == ThresholdUnit.TRUE_OR_FALSE) {
                    when (thresholdValue) {
                        0L -> append("false")
                        1L -> append("true")
                        else ->
                            throw IllegalArgumentException(
                                "Unexpected boolean value $thresholdValue"
                            )
                    }
                } else {
                    if (expected_value.higher_expected == true) append("> ")
                    if (expected_value.higher_expected == false) append("< ")
                    append(thresholdValue)
                    append(unitSuffix)
                }
                append(")")
            }
            .toString()

    val category =
        Insight.Category(
            titleUrl = helpUrlBase?.plus(reason_id!!.name),
            title = reason!!,
            postTitleLabel = thresholdString
        )

    val observedValue = requireNotNull(actual_value?.value_)
    return Insight(
        observedLabel =
            if (thresholdUnit == ThresholdUnit.TRUE_OR_FALSE) {
                require(observedValue in 0L..1L)
                if (observedValue == 0L) "false" else "true"
            } else {
                "$observedValue$unitSuffix"
            },
        deepLink =
            TraceDeepLink(
                outputRelativePath = traceOutputRelativePath,
                selectionParams = defaultStartupInsightSelectionParams
            ),
        iterationIndex = iterationIndex,
        category = category,
    )
}

internal fun PerfettoTraceProcessor.Session.queryStartupInsights(
    helpUrlBase: String?,
    traceOutputRelativePath: String,
    iteration: Int,
    packageName: String
): List<Insight> =
    inMemoryTrace("extract insights") {
        val defaultStartupInsightSelectionParams =
            extractStartupSliceSelectionParams(packageName = packageName)
        TraceMetrics.ADAPTER.decode(queryMetricsProtoBinary(listOf("android_startup")))
            .android_startup
            ?.startup
            ?.filter { it.package_name == packageName } // TODO: fuzzy match?
            ?.flatMap { it.slow_start_reason_with_details }
            ?.map {
                it.toInsight(
                    helpUrlBase = helpUrlBase,
                    traceOutputRelativePath = traceOutputRelativePath,
                    defaultStartupInsightSelectionParams = defaultStartupInsightSelectionParams,
                    iterationIndex = iteration
                )
            } ?: emptyList()
    }

/**
 * Construct's [TraceDeepLink.SelectionParams] based on the last `Startup` slice. Temporary hack
 * until we get this information directly from [SlowStartReason].
 *
 * TODO (b/377581661) remove, and instead construct deeplink from [SlowStartReason]
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
