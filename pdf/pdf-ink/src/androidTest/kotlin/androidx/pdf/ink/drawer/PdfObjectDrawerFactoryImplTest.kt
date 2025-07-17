/*
 * Copyright 2025 The Android Open Source Project
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

package androidx.pdf.ink.drawer

import android.graphics.Color
import androidx.pdf.annotation.models.PathPdfObject
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class PdfObjectDrawerFactoryImplTest {

    @Test
    fun create_withPathPdfObject_returnsInkPathPdfObjectDrawer() {
        val pathPdfObject =
            PathPdfObject(
                brushColor = DEFAULT_BRUSH_COLOR,
                brushWidth = DEFAULT_BRUSH_WIDTH,
                inputs = emptyList(),
            )

        val drawer = PdfObjectDrawerFactoryImpl.create(pathPdfObject)

        assertThat(drawer).isInstanceOf(PathPdfObjectDrawer::class.java)
    }

    private companion object {
        private const val DEFAULT_BRUSH_COLOR = Color.RED
        private const val DEFAULT_BRUSH_WIDTH = 10f
    }
}
