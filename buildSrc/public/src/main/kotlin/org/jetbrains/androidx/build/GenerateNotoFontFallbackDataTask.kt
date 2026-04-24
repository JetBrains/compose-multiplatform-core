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

package org.jetbrains.androidx.build

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.net.HttpURLConnection
import java.net.URI

private const val MAX_CODE_POINT = 0x10ffff

// Font index digits: 'a'..'z', radix 26
private const val FONT_INDEX_DIGIT0 = 'a'.code
private const val FONT_INDEX_RADIX = 26

// Range size digits: 'a'..'z', radix 26
private const val RANGE_SIZE_DIGIT0 = 'a'.code
private const val RANGE_SIZE_RADIX = 26

// Range value digits: 'A'..'Z', radix 26
private const val RANGE_VALUE_DIGIT0 = 'A'.code
private const val RANGE_VALUE_RADIX = 26

private const val FONTS_GSTATIC_URL_PREFIX = "https://fonts.gstatic.com/s/"

// User-Agent to spoof so that Google Fonts serves WOFF2 fonts.
private const val WOFF2_USER_AGENT =
    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/112.0.0.0 Safari/537.36"

// Maximum characters per line in the generated string concatenation.
private const val LINE_WIDTH = 120

/**
 * Fonts that are split into multiple subsets served from separate files.
 * CSS is fetched and each @font-face block becomes a separate NotoFont entry.
 */
private val FALLBACK_FONTS = setOf(
    "Noto Color Emoji",
    "Noto Sans Symbols 2",
    "Noto Sans Cuneiform",
    "Noto Sans Duployan",
    "Noto Sans Egyptian Hieroglyphs",
    "Noto Sans HK",
    "Noto Sans JP",
    "Noto Sans KR",
    "Noto Sans SC",
    "Noto Sans TC",
    "Noto Sans",
    "Noto Music",
    "Noto Sans Symbols",
    "Noto Sans Adlam",
    "Noto Sans Anatolian Hieroglyphs",
    "Noto Sans Arabic",
    "Noto Sans Armenian",
    "Noto Sans Avestan",
    "Noto Sans Balinese",
    "Noto Sans Bamum",
    "Noto Sans Bassa Vah",
    "Noto Sans Batak",
    "Noto Sans Bengali",
    "Noto Sans Bhaiksuki",
    "Noto Sans Brahmi",
    "Noto Sans Buginese",
    "Noto Sans Buhid",
    "Noto Sans Canadian Aboriginal",
    "Noto Sans Carian",
    "Noto Sans Caucasian Albanian",
    "Noto Sans Chakma",
    "Noto Sans Cham",
    "Noto Sans Cherokee",
    "Noto Sans Coptic",
    "Noto Sans Cypriot",
    "Noto Sans Deseret",
    "Noto Sans Devanagari",
    "Noto Sans Elbasan",
    "Noto Sans Elymaic",
    "Noto Sans Ethiopic",
    "Noto Sans Georgian",
    "Noto Sans Glagolitic",
    "Noto Sans Gothic",
    "Noto Sans Grantha",
    "Noto Sans Gujarati",
    "Noto Sans Gunjala Gondi",
    "Noto Sans Gurmukhi",
    "Noto Sans Hanunoo",
    "Noto Sans Hatran",
    "Noto Sans Hebrew",
    "Noto Sans Imperial Aramaic",
    "Noto Sans Indic Siyaq Numbers",
    "Noto Sans Inscriptional Pahlavi",
    "Noto Sans Inscriptional Parthian",
    "Noto Sans Javanese",
    "Noto Sans Kaithi",
    "Noto Sans Kannada",
    "Noto Sans Kayah Li",
    "Noto Sans Kharoshthi",
    "Noto Sans Khmer",
    "Noto Sans Khojki",
    "Noto Sans Khudawadi",
    "Noto Sans Lao",
    "Noto Sans Lepcha",
    "Noto Sans Limbu",
    "Noto Sans Linear A",
    "Noto Sans Linear B",
    "Noto Sans Lisu",
    "Noto Sans Lycian",
    "Noto Sans Lydian",
    "Noto Sans Mahajani",
    "Noto Sans Malayalam",
    "Noto Sans Mandaic",
    "Noto Sans Manichaean",
    "Noto Sans Marchen",
    "Noto Sans Masaram Gondi",
    "Noto Sans Math",
    "Noto Sans Mayan Numerals",
    "Noto Sans Medefaidrin",
    "Noto Sans Meetei Mayek",
    "Noto Sans Meroitic",
    "Noto Sans Miao",
    "Noto Sans Modi",
    "Noto Sans Mongolian",
    "Noto Sans Mro",
    "Noto Sans Multani",
    "Noto Sans Myanmar",
    "Noto Sans NKo",
    "Noto Sans Nabataean",
    "Noto Sans New Tai Lue",
    "Noto Sans Newa",
    "Noto Sans Nushu",
    "Noto Sans Ogham",
    "Noto Sans Ol Chiki",
    "Noto Sans Old Hungarian",
    "Noto Sans Old Italic",
    "Noto Sans Old North Arabian",
    "Noto Sans Old Permic",
    "Noto Sans Old Persian",
    "Noto Sans Old Sogdian",
    "Noto Sans Old South Arabian",
    "Noto Sans Old Turkic",
    "Noto Sans Oriya",
    "Noto Sans Osage",
    "Noto Sans Osmanya",
    "Noto Sans Pahawh Hmong",
    "Noto Sans Palmyrene",
    "Noto Sans Pau Cin Hau",
    "Noto Sans Phags Pa",
    "Noto Sans Phoenician",
    "Noto Sans Psalter Pahlavi",
    "Noto Sans Rejang",
    "Noto Sans Runic",
    "Noto Sans Saurashtra",
    "Noto Sans Sharada",
    "Noto Sans Shavian",
    "Noto Sans Siddham",
    "Noto Sans Sinhala",
    "Noto Sans Sogdian",
    "Noto Sans Sora Sompeng",
    "Noto Sans Soyombo",
    "Noto Sans Sundanese",
    "Noto Sans Syloti Nagri",
    "Noto Sans Syriac",
    "Noto Sans Tagalog",
    "Noto Sans Tagbanwa",
    "Noto Sans Tai Le",
    "Noto Sans Tai Tham",
    "Noto Sans Tai Viet",
    "Noto Sans Takri",
    "Noto Sans Tamil",
    "Noto Sans Tamil Supplement",
    "Noto Sans Telugu",
    "Noto Sans Thaana",
    "Noto Sans Thai",
    "Noto Sans Tifinagh",
    "Noto Sans Tirhuta",
    "Noto Sans Ugaritic",
    "Noto Sans Vai",
    "Noto Sans Wancho",
    "Noto Sans Warang Citi",
    "Noto Sans Yi",
    "Noto Sans Zanabazar Square",
    "Noto Serif Tibetan",
)

/** A single Noto font entry: its display name and the URL suffix used to download it. */
private data class FontEntry(
    val name: String,
    val urlSuffix: String,  // path after FONTS_GSTATIC_URL_PREFIX
    val starts: List<Int>,  // inclusive start of each supported codepoint range
    val ends: List<Int>,    // inclusive end of each supported codepoint range
)

private data class IndexedFont(val index: Int, val entry: FontEntry)

/** A boundary event for the range-intersection algorithm. */
private data class Boundary(val value: Int, val isStart: Boolean, val font: IndexedFont)

/** A canonical set of fonts that all support the same set of codepoints. */
private class FontSet(val fonts: List<IndexedFont>) {
    var rangeCount: Int = 0
    var index: Int = 0
}

/** A range of codepoints all covered by the same FontSet. */
private data class Range(val start: Int, val end: Int, val fontSet: FontSet)

/** Trie node for canonicalizing FontSets. */
private class TrieNode {
    val children: MutableMap<Int, TrieNode> = mutableMapOf()
    var fontSet: FontSet? = null

    fun insert(fontIndices: List<Int>): TrieNode {
        var node = this
        for (idx in fontIndices) {
            node = node.children.getOrPut(idx) { TrieNode() }
        }
        return node
    }
}

// ---------------- Gradle task ----------------

/**
 * Generates [NotoFontFallbackData.web.kt] by fetching font metadata from Google Fonts.
 *
 * Idea and some implementation details are adapted from
 * https://github.com/flutter/flutter/blob/master/engine/src/flutter/lib/web_ui/lib/src/engine/font_fallbacks.dart
 *
 */
abstract class GenerateNotoFontFallbackDataTask : DefaultTask() {

    /** The Kotlin source file to generate. */
    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun execute() {
        val allEntries = FALLBACK_FONTS.sorted().flatMap { processCssFont(it) }
        val (encodedSets, encodedRanges) = computeEncodedFontSets(allEntries)
        outputFile.get().asFile.apply {
            parentFile.mkdirs()
            writeText(generateKotlinSource(allEntries, encodedSets, encodedRanges))
        }
        logger.lifecycle("Written: ${outputFile.get().asFile.absolutePath}")
    }

    // ---------------- Font fetching ----------------

    /**
     * Fetches the Google Fonts CSS for [fontFamily] and parses each `@font-face` block into a
     * [FontEntry]. The User-Agent is spoofed so the server returns WOFF2 URLs.
     */
    private fun processCssFont(fontFamily: String): List<FontEntry> {
        val familyParam = fontFamily.replace(" ", "+")
        val cssUrl = "https://fonts.googleapis.com/css2?family=$familyParam"
        logger.lifecycle("  Fetching CSS: $cssUrl")
        val css = fetchText(cssUrl, mapOf("User-Agent" to WOFF2_USER_AGENT))
        return parseCssFontFaces(css, fontFamily)
    }

    // ---------------- CSS parsing ----------------

    /**
     * Parses all `@font-face` blocks in [css] that contain a WOFF2 `src: url(...)` and a
     * `unicode-range` declaration.
     *
     * Each block becomes a separate [FontEntry] named `"$familyName $index"`.
     */
    private fun parseCssFontFaces(css: String, familyName: String): List<FontEntry> {
        val urlRegex = Regex("""src:\s*url\((https?://[^)]+?\.woff2)\)""")
        val rangeRegex = Regex("""unicode-range:\s*([^;]+);""")

        val result = mutableListOf<FontEntry>()
        // Split on @font-face to isolate blocks. The first split is the CSS preamble (ignored).
        val blocks = css.split("@font-face")
        var counter = 0
        for (block in blocks.drop(1)) {
            val urlMatch = urlRegex.find(block) ?: continue
            val rangeMatch = rangeRegex.find(block) ?: continue

            val woff2Url = urlMatch.groupValues[1]
            if (!woff2Url.startsWith(FONTS_GSTATIC_URL_PREFIX)) {
                logger.warn("Unexpected URL in CSS for $familyName: $woff2Url — skipping block.")
                continue
            }
            val urlSuffix = woff2Url.removePrefix(FONTS_GSTATIC_URL_PREFIX)

            val (starts, ends) = parseUnicodeRangeList(rangeMatch.groupValues[1])
            if (starts.isEmpty()) continue

            result += FontEntry(
                name = "$familyName $counter",
                urlSuffix = urlSuffix,
                starts = starts,
                ends = ends,
            )
            counter++
        }
        return result
    }

    // ---------------- Unicode range parsing ----------------

    /**
     * Parses a comma-separated list of unicode ranges such as
     * `U+0000-00FF, U+0131, U+1E00-1EFF`.
     *
     * Supports:
     *  - Single code points: `U+XXXX`
     *  - Ranges: `U+XXXX-YYYY`
     *  - Wildcard ranges: `U+1???` (expanded to `U+1000-U+1FFF`)
     *
     * Returns a pair of (starts, ends) lists.
     */
    private fun parseUnicodeRangeList(rangeList: String): Pair<List<Int>, List<Int>> {
        val starts = mutableListOf<Int>()
        val ends = mutableListOf<Int>()
        for (part in rangeList.split(",")) {
            val token = part.trim().uppercase()
            if (token.isEmpty()) continue

            val hex = token.removePrefix("U+")
            if (hex.contains('?')) {
                // Wildcard: replace '?' with '0' for start, 'F' for end.
                val start = hex.replace('?', '0').toInt(16)
                val end = hex.replace('?', 'F').toInt(16)
                starts += start
                ends += end
            } else {
                val parts = hex.split("-")
                val start = parts[0].toInt(16)
                val end = if (parts.size > 1) parts[1].toInt(16) else start
                starts += start
                ends += end
            }
        }
        return starts to ends
    }

    // ---------------- STMR encoding ----------------

    /**
     * Computes the STMR-encoded font set and range data from [entries].
     *
     * The algorithm is a direct port of `_computeEncodedFontSets()` from Flutter's
     * `roll_fallback_fonts.dart`.  The encoded strings are returned as a pair:
     *  - first: `encodedFontSets` (comma-separated font-set encodings)
     *  - second: `encodedFontSetRanges` (concatenated range encodings)
     */
    private fun computeEncodedFontSets(entries: List<FontEntry>): Pair<String, String> {
        val indexedFonts = entries.mapIndexed { i, e -> IndexedFont(i, e) }

        // Build boundary list.
        val boundaries = mutableListOf<Boundary>()
        for (font in indexedFonts) {
            for (start in font.entry.starts) boundaries += Boundary(start, true, font)
            for (end in font.entry.ends) boundaries += Boundary(end + 1, false, font)
        }
        boundaries.sortWith(compareBy { it.value })

        // Walk boundaries and collect ranges with their canonical FontSets.
        val trieRoot = TrieNode()
        val current = mutableSetOf<IndexedFont>()
        val ranges = mutableListOf<Range>()
        val allSets = mutableListOf<FontSet>()

        fun recordRange(start: Int, end: Int) {
            val sortedFonts = current.sortedBy { it.index }
            val node = trieRoot.insert(sortedFonts.map { it.index })
            val fontSet = node.fontSet ?: FontSet(sortedFonts).also {
                node.fontSet = it
                allSets += it
            }
            fontSet.rangeCount++
            ranges += Range(start, end, fontSet)
        }

        var start = 0
        for (b in boundaries) {
            if (b.value > start) {
                recordRange(start, b.value - 1)
                start = b.value
            }
            if (b.isStart) current += b.font else current -= b.font
        }
        check(current.isEmpty()) { "Boundary walk ended with non-empty current set." }
        if (start <= MAX_CODE_POINT) recordRange(start, MAX_CODE_POINT)

        logger.lifecycle("  ${allSets.size} font sets, ${ranges.size} ranges.")

        // Sort font sets: most-referenced sets get the smallest indices (smaller encoded values).
        allSets.sortWith(
            compareByDescending<FontSet> { it.rangeCount }
                .thenComparator { a, b ->
                    for (i in 0 until minOf(a.fonts.size, b.fonts.size)) {
                        val cmp = a.fonts[i].index.compareTo(b.fonts[i].index)
                        if (cmp != 0) return@thenComparator cmp
                    }
                    a.fonts.size - b.fonts.size
                }
        )
        allSets.forEachIndexed { i, s -> s.index = i }

        // Encode font sets.
        val setsSb = StringBuilder()
        for ((i, fontSet) in allSets.withIndex()) {
            var prevIndex = -1
            for (font in fontSet.fonts) {
                val delta = font.index - prevIndex  // always >= 1
                prevIndex = font.index
                stmrEncode(delta - 1, FONT_INDEX_RADIX, FONT_INDEX_DIGIT0, setsSb)
            }
            if (i < allSets.lastIndex) setsSb.append(',')
        }

        // Encode ranges.
        val rangesSb = StringBuilder()
        for (range in ranges) {
            val size = range.end - range.start + 1
            if (size >= 2) stmrEncode(size - 2, RANGE_SIZE_RADIX, RANGE_SIZE_DIGIT0, rangesSb)
            stmrEncode(range.fontSet.index, RANGE_VALUE_RADIX, RANGE_VALUE_DIGIT0, rangesSb)
        }

        return setsSb.toString() to rangesSb.toString()
    }

    /**
     * STMR (Self-Terminating Multiple Radix) encoding.
     *
     * Encodes [value] into [sb] using decimal prefix digits followed by a single terminating
     * digit in the range `[firstDigitCode, firstDigitCode + radix)`.
     *
     * Example (radix=26, firstDigitCode='A'.code):
     *   encode(12)   → "M"     (0*26 + 12 = 12)
     *   encode(1000) → "38M"   (38*26 + 12 = 1000, prefix written as decimal "38")
     */
    private fun stmrEncode(value: Int, radix: Int, firstDigitCode: Int, sb: StringBuilder) {
        val prefix = value / radix
        if (prefix != 0) sb.append(prefix)          // decimal prefix (may be > 9)
        sb.append((firstDigitCode + value % radix).toChar())
    }

    // ---------------- Kotlin source generation ----------------

    private fun generateKotlinSource(
        entries: List<FontEntry>,
        encodedSets: String,
        encodedRanges: String,
    ): String {
        return buildString {
            // File header.
            append(
                """
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

                package androidx.compose.ui.platform
                
                // !!! DO NOT EDIT THIS FILE MANUALLY !!!
                // the code is auto-generated by GenerateNotoFontFallbackDataTask.kt

                internal class NotoFont(val name: String, val url: String)

                internal fun getNotoFonts(): List<NotoFont> = listOf(

                """.trimIndent()
            )

            // Font list.
            for (entry in entries) {
                appendLine("""    NotoFont(name = "${entry.name}", url = "${entry.urlSuffix}"),""")
            }
            // Remove the trailing comma from the last entry.
            val trailingComma = lastIndexOf(",\n")
            if (trailingComma >= 0) {
                deleteRange(trailingComma, trailingComma + 1)  // remove the ','
            }

            appendLine(")")
            appendLine()

            // encodedNotoFontSets.
            append("internal val encodedNotoFontSets: String =\n")
            appendMultilineString(encodedSets)
            appendLine()

            // encodedNotoFontSetRanges.
            append("internal val encodedNotoFontSetRanges: String =\n")
            appendMultilineString(encodedRanges)
        }
    }

    /**
     * Appends [data] as a multi-line Kotlin string concatenation where each line is at most
     * [LINE_WIDTH] characters wide. Lines are formatted as `    "..." +` except the last which
     * omits the `+`.
     */
    private fun StringBuilder.appendMultilineString(data: String) {
        var pos = 0
        while (pos < data.length) {
            val end = minOf(pos + LINE_WIDTH, data.length)
            val chunk = data.substring(pos, end)
            val isLast = end >= data.length
            if (isLast) {
                appendLine("""    "$chunk"""")
            } else {
                appendLine("""    "$chunk" +""")
            }
            pos = end
        }
    }

    // ---------------- HTTP utilities ----------------

    private fun fetchText(url: String, headers: Map<String, String> = emptyMap()): String {
        val conn = URI(url).toURL().openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "GET"
            headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
            conn.connectTimeout = 30_000
            conn.readTimeout   = 60_000
            conn.connect()
            if (conn.responseCode != 200) {
                error("HTTP ${conn.responseCode} for $url: ${conn.responseMessage}")
            }
            return conn.inputStream.bufferedReader().readText()
        } finally {
            conn.disconnect()
        }
    }
}
