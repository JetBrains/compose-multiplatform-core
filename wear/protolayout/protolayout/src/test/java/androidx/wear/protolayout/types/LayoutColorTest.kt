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

package androidx.wear.protolayout.types

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.wear.protolayout.ColorBuilders.ColorProp
import androidx.wear.protolayout.expression.DynamicBuilders.DynamicColor
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LayoutColorTest {
    @Test
    fun staticColor_asLayoutColor() {
        val prop: ColorProp = STATIC_COLOR.argb.prop

        assertThat(prop.argb).isEqualTo(STATIC_COLOR)
        assertThat(prop.dynamicValue).isNull()
    }

    @Test
    fun dynamicColor_asLayoutColor() {
        val layoutColor: LayoutColor = DYNAMIC_COLOR.asLayoutColor(staticArgb = STATIC_COLOR)
        val prop: ColorProp = layoutColor.prop

        assertThat(layoutColor.staticArgb).isEqualTo(STATIC_COLOR)
        assertThat(prop.argb).isEqualTo(STATIC_COLOR)
        assertThat(layoutColor.dynamicArgb?.toDynamicColorProto())
            .isEqualTo(DYNAMIC_COLOR.toDynamicColorProto())
        assertThat(prop.dynamicValue?.toDynamicColorProto())
            .isEqualTo(DYNAMIC_COLOR.toDynamicColorProto())
    }

    companion object {
        private const val STATIC_COLOR = 0xFF123456.toInt()
        private val DYNAMIC_COLOR = DynamicColor.constant(0x12FF34FF)
    }
}
