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

package androidx.pdf.ink.view

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import androidx.annotation.RestrictTo
import androidx.pdf.ink.R
import androidx.pdf.ink.view.tool.AnnotationToolView

/**
 * A toolbar that hosts a set of annotation tools for interacting with a PDF document.
 *
 * This custom [android.view.ViewGroup] contains a predefined set of [AnnotationToolView] buttons
 * such as pen, highlighter, eraser, etc. aligned based on the [orientation] set.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY)
public class AnnotationToolbar
@JvmOverloads
constructor(context: Context, attrs: AttributeSet? = null, defStyle: Int = 0) :
    LinearLayout(context, attrs, defStyle) {
    private val pen: AnnotationToolView
    private val highlighter: AnnotationToolView
    private val eraser: AnnotationToolView

    private val colorPaletteButton: AnnotationToolView
    private val undo: AnnotationToolView
    private val redo: AnnotationToolView
    private val toggleAnnotation: AnnotationToolView

    init {
        LayoutInflater.from(context).inflate(R.layout.annotation_tools, this, true)
        // default orientation
        orientation = HORIZONTAL
        background = context.getDrawable(R.drawable.annotation_toolbar_background)

        pen = findViewById<AnnotationToolView>(R.id.pen_button)
        highlighter = findViewById<AnnotationToolView>(R.id.highlighter_button)
        eraser = findViewById<AnnotationToolView>(R.id.eraser_button)
        colorPaletteButton = findViewById<AnnotationToolView>(R.id.color_palette_button)
        undo = findViewById<AnnotationToolView>(R.id.undo_button)
        redo = findViewById<AnnotationToolView>(R.id.redo_button)
        toggleAnnotation = findViewById<AnnotationToolView>(R.id.toggle_annotation_button)

        // TODO: Add click listeners for the tools
    }
}
