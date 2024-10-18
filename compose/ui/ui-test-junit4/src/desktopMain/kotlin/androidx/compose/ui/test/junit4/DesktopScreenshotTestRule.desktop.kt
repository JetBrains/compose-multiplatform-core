/*
 * Copyright 2020 The Android Open Source Project
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
package androidx.compose.ui.test.junit4

import androidx.annotation.FloatRange
import androidx.compose.ui.test.InternalTestApi
import androidx.compose.ui.test.junit4.ScreenshotResultProto.Status
import androidx.compose.ui.test.junit4.matchers.BitmapMatcher
import androidx.compose.ui.test.junit4.matchers.MSSIMMatcher
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.text.matches
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorInfo
import org.jetbrains.skia.ColorSpace
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Surface
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

// TODO: replace with androidx.test.screenshot.proto.ScreenshotResultProto after MPP
private data class ScreenshotResultProto(
    val result: Status,
    val comparisonStatistics: String,
    val repoRootPath: String,
    val locationOfGoldenInRepo: String,
    val currentScreenshotFileName: String,
    val diffImageFileName: String?,
    val expectedImageFileName: String
) {
    enum class Status {
        UNSPECIFIED,
        PASSED,
        FAILED,
        MISSING_GOLDEN,
        SIZE_MISMATCH
    }
}

@InternalTestApi
fun DesktopScreenshotTestRule(
    modulePath: String,
    fsGoldenPath: String = System.getProperty("GOLDEN_PATH")
): ScreenshotTestRule {
    return ScreenshotTestRule(fsGoldenPath, modulePath)
}

@InternalTestApi
class ScreenshotTestRule internal constructor(
    private val fsGoldenPath: String,
    private val modulePath: String
) : TestRule {
    private val imageExtension = ".png"
    private lateinit var testIdentifier: String

    override fun apply(base: Statement, description: Description?): Statement {
        return object : Statement() {
            override fun evaluate() {
                testIdentifier = "${description!!.className}_${description.methodName}"
                    .replace(".", "_").replace(",", "_").replace(" ", "_").replace("__", "_")
                base.evaluate()
            }
        }
    }

    /**
     * Asserts the given image against the golden identified by the given name.
     *
     * @param actual The image captured during the test.
     * @param idSuffix Suffix of the golden. Allowed characters: 'A-Za-z0-9_-'
     * @param threshold Matching .
     * @throws IllegalArgumentException If the golden identifier contains forbidden characters or is
     *   empty.
     */
    fun assertImageAgainstGolden(
        actual: Image,
        idSuffix: String? = null,
        @FloatRange(from = 0.0, to = 1.0) threshold: Double = 0.96
    ) {
        val goldenIdentifier = testIdentifier + if (idSuffix != null) "_$idSuffix" else ""
        val matcher = MSSIMMatcher(threshold)
        assertImageAgainstGolden(actual, goldenIdentifier, matcher)
    }

    private fun assertImageAgainstGolden(
        actual: Image,
        goldenIdentifier: String,
        matcher: BitmapMatcher
    ) {
        if (!goldenIdentifier.matches("^[A-Za-z0-9_-]+$".toRegex())) {
            throw IllegalArgumentException(
                "The given golden identifier '$goldenIdentifier' does not satisfy the naming " +
                    "requirement. Allowed characters are: '[A-Za-z0-9_-]'"
            )
        }

        val expected = fetchExpectedImage(goldenIdentifier)
        if (expected == null) {
            reportResult(
                status = Status.MISSING_GOLDEN,
                goldenIdentifier = goldenIdentifier,
                actual = actual
            )
            throw AssertionError(
                "Missing golden image " +
                    "'${goldenIdentifierResolver(goldenIdentifier)}'. " +
                    "Did you mean to check in a new image?"
            )
        }

        if (actual.width != expected.width || actual.height != expected.height) {
            reportResult(
                status = Status.SIZE_MISMATCH,
                goldenIdentifier = goldenIdentifier,
                actual = actual,
                expected = expected
            )
            throw AssertionError(
                "Sizes are different! Expected: [${expected.width}, ${expected
                    .height}], Actual: [${actual.width}, ${actual.height}]"
            )
        }

        val comparisonResult =
            matcher.compareBitmaps(
                expected = expected.toIntArray(),
                given = actual.toIntArray(),
                width = actual.width,
                height = actual.height
            )

        val status =
            if (comparisonResult.matches) {
                Status.PASSED
            } else {
                Status.FAILED
            }

        reportResult(
            status = status,
            goldenIdentifier = goldenIdentifier,
            actual = actual,
            comparisonStatistics = comparisonResult.comparisonStatistics,
            expected = expected,
            diff = comparisonResult.diff?.toImage(actual.width, actual.height)
        )

        if (!comparisonResult.matches) {
            throw AssertionError(
                "Image mismatch! Comparison stats: '${comparisonResult
                    .comparisonStatistics}'"
            )
        }
    }

    private fun dumpImage(path: String, data: ByteArray) {
        val file = File(fsGoldenPath, path)
        file.writeBytes(data)
    }

    private fun goldenIdentifierResolver(id: String, suffix: String? = null) =
        if (suffix == null) {
            "$modulePath/$id$imageExtension"
        } else {
            "$modulePath/${id}_$suffix$imageExtension"
        }

    private fun fetchExpectedImage(goldenIdentifier: String): Image? {
        val file = File(fsGoldenPath, goldenIdentifierResolver(goldenIdentifier))
        if (!file.exists()) {
            return null
        }
        return Image.makeFromEncoded(file.readBytes())
    }

    private fun ensureDir() {
        File(fsGoldenPath, modulePath).mkdirs()
    }

    private fun reportResult(
        status: Status,
        goldenIdentifier: String,
        actual: Image,
        comparisonStatistics: String? = null,
        expected: Image? = null,
        diff: Image? = null
    ) {
        if (status == Status.PASSED) return
        val currentScreenshotFileName = goldenIdentifierResolver(goldenIdentifier, "actual")
        ensureDir()
        val encodedActual = actual.encodeToData()!!.bytes
        dumpImage(currentScreenshotFileName, encodedActual)
        if (diff != null) {
            val diffImageFileName = goldenIdentifierResolver(goldenIdentifier, "diff")
            val encodedDiff = diff.encodeToData()!!.bytes
            dumpImage(diffImageFileName, encodedDiff)
        }
    }
}

private fun Image.toIntArray(): IntArray {
    val bitmap = Bitmap()
    bitmap.allocPixels(ImageInfo.makeN32(width, height, ColorAlphaType.PREMUL))

    val canvas = Canvas(bitmap)
    canvas.drawImage(this, 0f, 0f)
    bitmap.setImmutable()
    canvas.close()

    val colorInfo = ColorInfo(ColorType.BGRA_8888, ColorAlphaType.UNPREMUL, ColorSpace.sRGB)
    val imageInfo = ImageInfo(colorInfo, width, height)
    val pixels = bitmap.readPixels(imageInfo)!!.toIntArray(width, height)
    bitmap.close()
    return pixels
}

private fun ByteArray.toIntArray(width: Int, height: Int): IntArray {
    val buffer = IntArray(width * height)
    ByteBuffer.wrap(this)
        .order(ByteOrder.LITTLE_ENDIAN) // to return ARGB
        .asIntBuffer()
        .get(buffer, 0, size / 4)
    return buffer
}

private fun IntArray.toImage(width: Int, height: Int): Image {
    val colorInfo = ColorInfo(ColorType.BGRA_8888, ColorAlphaType.UNPREMUL, ColorSpace.sRGB)
    val imageInfo = ImageInfo(colorInfo, width, height)
    val rowBytes = width * 4
    val bytes = ByteBuffer.allocate(rowBytes * height)
        .order(ByteOrder.LITTLE_ENDIAN) // to return ARGB
        .apply { asIntBuffer().put(this@toImage) }
        .array()
    return Image.makeRaster(imageInfo, bytes, rowBytes)
}
