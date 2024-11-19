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

package androidx.build

import androidx.build.docs.rewriteLinks
import junit.framework.TestCase.assertEquals
import org.junit.Test

class MarkdownLinkRewriterTest {

    @Test
    fun testStandardLink() {
        val input = "[Progress Indicator doc](https://developer.android.com/progress)"
        val expected = "[Progress Indicator doc](https://developer.android.com/progress)"
        assertEquals(expected, rewriteLinks(input))
    }

    @Test
    fun testImageLink() {
        val input = "![Progress Image](https://developer.android.com/image.png)"
        val expected = "![Progress Image](https://developer.android.com/image.png)"
        assertEquals(expected, rewriteLinks(input))
    }

    @Test
    fun testMultiLineLink() {
        val input = """
            [Progress
            Indicator doc](https://developer.android.com/progress)
        """.trimIndent()
        val expected = "[Progress Indicator doc](https://developer.android.com/progress)"
        assertEquals(expected, rewriteLinks(input))
    }

    @Test
    fun testMultiLineImageLink() {
        val input = """
            ![Progress
            *
            
            Image](https://developer.android.com/image.png)
        """.trimIndent()
        val expected = "![Progress Image](https://developer.android.com/image.png)"
        assertEquals(expected, rewriteLinks(input))
    }

    @Test
    fun testNonLinkWithOnlyBrackets() {
        val input = "[Progress Indicator doc]"
        val expected = "[Progress Indicator doc]"
        assertEquals(expected, rewriteLinks(input))
    }

    @Test
    fun testNonLinkWithBracketsAndParenthesesSeparately() {
        val input = "[Progress Indicator doc] (https://developer.android.com/progress)"
        val expected = "[Progress Indicator doc] (https://developer.android.com/progress)"
        assertEquals(expected, rewriteLinks(input))
    }

    @Test
    fun testNonLinkWithOnlyParentheses() {
        val input = "(https://developer.android.com/progress)"
        val expected = "(https://developer.android.com/progress)"
        assertEquals(expected, rewriteLinks(input))
    }

    @Test
    fun testMultipleLinksWithMixedContent() {
        val input = """
            Here is a regular link: [Progress doc](https://developer.android.com/progress).
            And here is an image link: ![Image](https://developer.android.com/image.png).
            But this should not change: [Android doc]
            And neither should this: [https://developer.android.com]
        """.trimIndent()

        val expected = """
            Here is a regular link: [Progress doc](https://developer.android.com/progress).
            And here is an image link: ![Image](https://developer.android.com/image.png).
            But this should not change: [Android doc]
            And neither should this: [https://developer.android.com]
        """.trimIndent()

        assertEquals(expected, rewriteLinks(input))
    }
}
