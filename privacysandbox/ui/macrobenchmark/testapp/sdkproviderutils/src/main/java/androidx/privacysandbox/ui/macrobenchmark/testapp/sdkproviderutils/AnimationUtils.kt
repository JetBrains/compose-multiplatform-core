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

package androidx.privacysandbox.ui.macrobenchmark.testapp.sdkproviderutils

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import android.view.ViewGroup
import kotlin.random.Random

/** Utils class for running text-view animations. */
object AnimationUtils {
    fun startAnimations(
        animationContainer: ViewGroup,
        height: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
    ) {
        animationContainer.removeAllViews()
        animationContainer.addView(
            ContinuousDrawingView(animationContainer.context, height = height)
        )
    }

    private class ContinuousDrawingView(context: Context?, height: Int) : View(context) {
        private var backgroundColor: Int = getRandomColor()
        private var paint: Paint? = null
        private var startX = 0f

        init {
            paint = Paint()
            paint!!.color = getRandomColor()
            paint!!.style = Paint.Style.FILL
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height)
        }

        private fun getRandomColor(): Int {
            return Color.rgb(Random.nextInt(256), Random.nextInt(256), Random.nextInt(256))
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            // Clear the canvas
            canvas.drawColor(backgroundColor)

            // Draw a circle
            canvas.drawCircle(startX, this.height.toFloat() / 2, 25f, paint!!)

            // Update the position of the circle
            startX += 10f
            if (startX > width) startX = 0f

            // Request to draw a new frame
            invalidate() // This causes the view to be redrawn constantly
        }
    }
}
