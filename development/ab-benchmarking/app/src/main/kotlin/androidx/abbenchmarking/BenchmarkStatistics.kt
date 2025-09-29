/*
 * Copyright 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package androidx.abbenchmarking

import kotlin.math.cbrt
import kotlin.random.Random
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics
import org.apache.commons.math3.stat.inference.MannWhitneyUTest
import org.jetbrains.letsPlot.export.ggsave
import org.jetbrains.letsPlot.geom.geomHistogram
import org.jetbrains.letsPlot.ggsize
import org.jetbrains.letsPlot.label.ggtitle
import org.jetbrains.letsPlot.letsPlot
import org.jetbrains.letsPlot.pos.positionIdentity

/**
 * Contains the data class for storing statistical results and all functions related to performing
 * and printing the statistical analysis.
 */
data class StatisticsResult(
    val count1: Int,
    val min1: Double,
    val mean1: Double,
    val median1: Double,
    val std1: Double,
    val count2: Int,
    val min2: Double,
    val mean2: Double,
    val median2: Double,
    val std2: Double,
    val minDiff: Double,
    val minDiffPercent: Double,
    val meanDiff: Double,
    val meanDiffPercent: Double,
    val medianDiff: Double,
    val medianDiffPercent: Double,
    val pValue: Double,
    val medianDiffCI: Pair<Double, Double>,
    val medianDiffCIPercent: Pair<Double, Double>,
)

/**
 * Calculates the bootstrap confidence interval for the difference in medians between two datasets.
 *
 * This method uses bootstrap resampling to estimate the 95% confidence interval for the difference
 * between the medians of two samples (`data1` and `data2`). It repeatedly draws random samples with
 * replacement from each dataset, calculates the difference in medians for each pair of resampled
 * datasets, and then determines the range that contains the central 95% of these differences.
 *
 * How to interpret the result: The returned `Pair` represents the lower and upper bounds of the 95%
 * confidence interval.
 * - If the interval (e.g., `[2.5, 5.0]`) does not contain zero, it suggests that there is a
 *   statistically significant difference between the medians of the underlying populations from
 *   which the samples were drawn.
 * - If the interval (e.g., `[-1.2, 3.4]`) contains zero, it means that there is no statistically
 *   significant evidence of a difference between the medians.
 * - The width of the interval provides a sense of the precision of the estimate. A narrower
 *   interval indicates a more precise estimate of the median difference.
 *
 * @param data1 The first dataset.
 * @param data2 The second dataset.
 * @param numSamples The number of bootstrap samples to generate.
 * @param confidenceLevel The desired confidence level.
 * @return A Pair containing the lower and upper bounds of the confidence interval.
 */
internal fun calculateBootstrapCIMedianDifference(
    data1: DoubleArray,
    data2: DoubleArray,
    numSamples: Int = 10000,
    confidenceLevel: Double = 0.95,
): Pair<Double, Double> {
    if (data1.isEmpty() || data2.isEmpty()) {
        return Pair(Double.NaN, Double.NaN)
    }
    val random = Random(42) // Use a fixed seed for deterministic results
    val medianDiffs = DoubleArray(numSamples)
    val n1 = data1.size
    val n2 = data2.size
    val resample1 = DoubleArray(n1)
    val resample2 = DoubleArray(n2)
    for (i in 0 until numSamples) {
        for (j in 0 until n1) {
            resample1[j] = data1[random.nextInt(n1)]
        }
        val median1 = DescriptiveStatistics(resample1).getPercentile(50.0)
        for (j in 0 until n2) {
            resample2[j] = data2[random.nextInt(n2)]
        }
        val median2 = DescriptiveStatistics(resample2).getPercentile(50.0)
        medianDiffs[i] = median2 - median1
    }
    medianDiffs.sort()
    val lowerIndex = ((1.0 - confidenceLevel) / 2.0 * numSamples).toInt()
    val upperIndex = (numSamples - lowerIndex - 1)
    return Pair(medianDiffs[lowerIndex], medianDiffs[upperIndex])
}

fun calculateStatistics(data1: DoubleArray, data2: DoubleArray): StatisticsResult {
    val stats1 = DescriptiveStatistics(data1)
    val stats2 = DescriptiveStatistics(data2)
    // Counts
    val n1 = stats1.n
    val n2 = stats2.n
    // Means
    val mean1 = stats1.mean
    val mean2 = stats2.mean
    val meanDiff = mean2 - mean1
    val meanDiffPercent = if (mean1 != 0.0) (meanDiff / mean1) * 100 else 0.0
    // Median
    val median1 = stats1.getPercentile(50.0)
    val median2 = stats2.getPercentile(50.0)
    val medianDiff = median2 - median1
    val medianDiffPercent = if (median1 != 0.0) (medianDiff / median1) * 100 else 0.0
    // Mins
    val min1 = stats1.min
    val min2 = stats2.min
    val minDiff = min2 - min1
    val minDiffPercent = if (min1 != 0.0) (minDiff / min1) * 100 else 0.0
    // Standard Deviation
    val std1 = stats1.standardDeviation
    val std2 = stats2.standardDeviation
    // Perform Mann-Whitney U test.
    // This is a non-parametric test that checks if there's a significant difference
    // between two independent samples. It's used here to compare the distributions of the
    // two benchmark datasets.
    //
    // How to interpret the p-value:
    // - A small p-value (typically < 0.05) suggests that the observed difference between
    //   the datasets is statistically significant, meaning it's unlikely to have occurred
    //   by chance.
    // - A large p-value (>= 0.05) suggests that there is no statistically significant
    //   difference between the datasets.
    val mannWhitneyUTest = MannWhitneyUTest()
    val pValue = mannWhitneyUTest.mannWhitneyUTest(data1, data2)

    val medianDiffCI = calculateBootstrapCIMedianDifference(data1, data2)
    val ciLowerPercent = if (median1 != 0.0) (medianDiffCI.first / median1) * 100 else 0.0
    val ciUpperPercent = if (median1 != 0.0) (medianDiffCI.second / median1) * 100 else 0.0
    val medianDiffCIPercent = Pair(ciLowerPercent, ciUpperPercent)

    return StatisticsResult(
        count1 = n1.toInt(),
        min1 = min1,
        mean1 = mean1,
        median1 = median1,
        std1 = std1,
        count2 = n2.toInt(),
        min2 = min2,
        mean2 = mean2,
        median2 = median2,
        std2 = std2,
        minDiff = minDiff,
        minDiffPercent = minDiffPercent,
        meanDiff = meanDiff,
        meanDiffPercent = meanDiffPercent,
        medianDiff = medianDiff,
        medianDiffPercent = medianDiffPercent,
        pValue = pValue,
        medianDiffCI = medianDiffCI,
        medianDiffCIPercent = medianDiffCIPercent,
    )
}

/**
 * Prints a human-readable statistical summary to the console.
 *
 * @param benchmarkName The name of the test being summarized.
 * @param stats The calculated statistical results.
 */
internal fun printSummary(benchmarkName: String, stats: StatisticsResult) {
    println("\n--- Comparison for: $benchmarkName ---")
    println("                       Dataset 1 (Branch A) | Dataset 2 (Branch B)")
    println("----------------------------------------------------------------")
    println("Count                | ${stats.count1.toString().padEnd(20)} | ${stats.count2}")
    println(
        "Min (ns)             | ${"%.2f".format(stats.min1).padEnd(20)} | ${"%.2f".format(stats.min2)}"
    )
    println(
        "Mean (ns)            | ${"%.2f".format(stats.mean1).padEnd(20)} | ${"%.2f".format(stats.mean2)}"
    )
    println(
        "Median (ns)          | ${"%.2f".format(stats.median1).padEnd(20)} | ${"%.2f".format(stats.median2)}"
    )
    println(
        "Std. Dev. (ns)       | ${"%.2f".format(stats.std1).padEnd(20)} | ${"%.2f".format(stats.std2)}"
    )
    println(
        "Min Difference:      | ${"%.2f".format(stats.minDiff)} ns (${"%.2f".format(stats.minDiffPercent)}%)"
    )
    println(
        "Mean Difference:     | ${"%.2f".format(stats.meanDiff)} ns (${"%.2f".format(stats.meanDiffPercent)}%)"
    )
    println(
        "Median Difference:   | ${"%.2f".format(stats.medianDiff)} ns (${"%.2f".format(stats.medianDiffPercent)}%)"
    )
    println(
        "95% CI of Diff:      | [${"%.2f".format(stats.medianDiffCI.first)}, ${"%.2f".format(stats.medianDiffCI.second)}] ns ([${"%.2f".format(stats.medianDiffCIPercent.first)}%, ${"%.2f".format(stats.medianDiffCIPercent.second)}%])"
    )
    // Check if the interval contains zero.
    if (stats.medianDiffCI.first < 0 && stats.medianDiffCI.second > 0) {
        println(
            "\nThe confidence interval contains zero, suggesting no statistically significant difference between the medians."
        )
    } else {
        println(
            "\nThe confidence interval does not contain zero, suggesting a statistically significant difference exists between the medians."
        )
    }
    println("\n--- MannWhitneyUTest Results (Branch B vs. Branch A) ---")
    println("P-value:                 ${"%.4f".format(stats.pValue)}")
    if (stats.pValue < 0.05) {
        println("Result:                  Statistically significant difference.")
    } else {
        println("Result:                  No statistically significant difference.")
    }
    println("\n-------------------------------------------------------\n")
}

internal fun printCsvOutput(benchmarkName: String, stats: StatisticsResult) {
    // Define columns and their formatted values in one place
    val columns =
        with(stats) {
            listOf(
                "count" to "$count1",
                "min1" to "%.2f".format(min1),
                "min2" to "%.2f".format(min2),
                "min_diff" to "%.2f".format(minDiff),
                "min_diff_%" to "%.2f%%".format(minDiffPercent),
                "mean1" to "%.2f".format(mean1),
                "mean2" to "%.2f".format(mean2),
                "mean_diff_%" to "%.2f%%".format(meanDiffPercent),
                "median1" to "%.2f".format(median1),
                "median2" to "%.2f".format(median2),
                "p-value" to "%.4f".format(pValue),
                "median_diff_%" to "%.2f%%".format(medianDiffPercent),
                "median_diff" to "%.2f".format(medianDiff),
                "median_diff_ci_lower" to "%.2f".format(medianDiffCI.first),
                "median_diff_ci_upper" to "%.2f".format(medianDiffCI.second),
                "median_diff_ci_lower_%" to "%.2f%%".format(medianDiffCIPercent.first),
                "median_diff_ci_upper_%" to "%.2f%%".format(medianDiffCIPercent.second),
            )
        }
    // Generate the header and data row from the single source of truth
    val header = "benchmarkName," + columns.joinToString(",") { it.first }
    val dataRow = "$benchmarkName," + columns.joinToString(",") { it.second }
    println("--- Machine-Readable CSV ---")
    println(header)
    println(dataRow)
}

/**
 * Creates and saves a graphical histogram comparing two datasets using Lets-Plot.
 *
 * The resulting plot is saved as an HTML file in the output directory.
 *
 * @param benchmarkName The name of the test, used for the plot title and filename.
 * @param data1 Benchmark data from rev A.
 * @param data2 Benchmark data from rev B.
 * @param outputPath The path where the plot file will be saved.
 */
fun createHistogramPlot(
    benchmarkName: String,
    data1: DoubleArray,
    data2: DoubleArray,
    outputPath: java.nio.file.Path,
) {
    val combinedTimings = data1.toList() + data2.toList()
    val combinedData =
        mapOf<String, Any>(
            "timings" to combinedTimings,
            "branch" to List(data1.size) { "Branch A" } + List(data2.size) { "Branch B" },
        )

    val stats = DescriptiveStatistics(combinedTimings.toDoubleArray())
    val n = stats.n.toDouble()
    val q1 = stats.getPercentile(25.0)
    val q3 = stats.getPercentile(75.0)
    val iqr = q3 - q1

    val optimalBins =
        if (iqr > 0) {
            val binWidth = 2 * iqr * (1 / cbrt(n))
            ((stats.max - stats.min) / binWidth).toInt()
        } else {
            20
        }

    val plot =
        letsPlot(combinedData) {
            // This maps timings to the X-axis for a vertical plot
            x = "timings"
            fill = "branch"
        } +
            geomHistogram(alpha = 0.3, position = positionIdentity, bins = optimalBins) +
            ggsize(1000, 600) +
            ggtitle("Benchmark Comparison: $benchmarkName")

    val plotFile = ggsave(plot, "${benchmarkName}_histogram.png", path = outputPath.toString())
    println("\n--- Graphical Plot ---")
    println("Saved histogram to: $plotFile")
}
