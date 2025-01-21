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

package androidx.wear.protolayout.material3

import android.content.Context
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DeviceParametersBuilders
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.testing.LayoutElementAssertionsProvider
import androidx.wear.protolayout.testing.hasClickable
import androidx.wear.protolayout.testing.hasColor
import androidx.wear.protolayout.testing.hasContentDescription
import androidx.wear.protolayout.testing.hasHeight
import androidx.wear.protolayout.testing.hasImage
import androidx.wear.protolayout.testing.hasTag
import androidx.wear.protolayout.testing.hasText
import androidx.wear.protolayout.testing.hasWidth
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.internal.DoNotInstrument

@RunWith(AndroidJUnit4::class)
@DoNotInstrument
class CardTest {
    @Test
    fun containerCard_size_default() {
        LayoutElementAssertionsProvider(DEFAULT_CONTAINER_CARD_WITH_TEXT)
            .onRoot()
            .assert(hasWidth(wrapWithMinTapTargetDimension()))
            .assert(hasHeight(wrapWithMinTapTargetDimension()))
            .assert(hasTag(CardDefaults.METADATA_TAG))
    }

    @Test
    fun containerCard_hasContentDescription() {
        LayoutElementAssertionsProvider(DEFAULT_CONTAINER_CARD_WITH_TEXT)
            .onRoot()
            .assert(hasContentDescription(CONTENT_DESCRIPTION))
            .assert(hasTag(CardDefaults.METADATA_TAG))
    }

    @Test
    fun containerCard_hasClickable() {
        LayoutElementAssertionsProvider(DEFAULT_CONTAINER_CARD_WITH_TEXT)
            .onRoot()
            .assert(hasClickable(CLICKABLE))
            .assert(hasTag(CardDefaults.METADATA_TAG))
    }

    @Test
    fun containerCard_hasContent_asText() {
        LayoutElementAssertionsProvider(DEFAULT_CONTAINER_CARD_WITH_TEXT)
            .onElement(hasText(TEXT))
            .assertExists()
    }

    @Test
    fun containerCard_hasBackgroundImage() {
        val card =
            materialScope(CONTEXT, DEVICE_CONFIGURATION) {
                card(
                    onClick = CLICKABLE,
                    contentDescription = CONTENT_DESCRIPTION.prop(),
                    background = { backgroundImage(IMAGE_ID) }
                ) {
                    text(TEXT.prop())
                }
            }

        LayoutElementAssertionsProvider(card).onElement(hasImage(IMAGE_ID)).assertExists()
        LayoutElementAssertionsProvider(card).onRoot().assert(hasTag(CardDefaults.METADATA_TAG))
    }

    @Test
    fun containerCard_hasBackgroundColor() {
        val color = Color.YELLOW
        val card =
            materialScope(CONTEXT, DEVICE_CONFIGURATION) {
                card(
                    onClick = CLICKABLE,
                    contentDescription = CONTENT_DESCRIPTION.prop(),
                    backgroundColor = argb(color)
                ) {
                    text(TEXT.prop())
                }
            }

        LayoutElementAssertionsProvider(card).onRoot().assert(hasColor(color))
        LayoutElementAssertionsProvider(card).onRoot().assert(hasTag(CardDefaults.METADATA_TAG))
    }

    // TODO: b/381518061 - Add test for corner shape.

    @Test
    fun containerCard_hasGivenSize() {
        val height = 12
        val card =
            materialScope(CONTEXT, DEVICE_CONFIGURATION) {
                card(
                    onClick = CLICKABLE,
                    contentDescription = CONTENT_DESCRIPTION.prop(),
                    width = expand(),
                    height = height.toDp()
                ) {
                    text(TEXT.prop())
                }
            }

        LayoutElementAssertionsProvider(card)
            .onRoot()
            .assert(hasWidth(expand()))
            .assert(hasHeight((height.toDp())))
            .assert(hasTag(CardDefaults.METADATA_TAG))
    }

    companion object {
        private val CONTEXT = getApplicationContext() as Context

        private val DEVICE_CONFIGURATION =
            DeviceParametersBuilders.DeviceParameters.Builder()
                .setScreenWidthDp(192)
                .setScreenHeightDp(192)
                .build()

        private val CLICKABLE = clickable("id")

        private const val CONTENT_DESCRIPTION = "This is a card"

        private const val IMAGE_ID = "image"

        private const val TEXT = "Container card"

        private val DEFAULT_CONTAINER_CARD_WITH_TEXT =
            materialScope(CONTEXT, DEVICE_CONFIGURATION) {
                card(onClick = CLICKABLE, contentDescription = CONTENT_DESCRIPTION.prop()) {
                    text(TEXT.prop())
                }
            }
    }
}
