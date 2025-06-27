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

package androidx.camera.viewfinder.core.impl

import android.os.Build
import android.view.Surface
import android.view.SurfaceControl
import android.view.SurfaceView
import androidx.annotation.RequiresApi

/**
 * Compat class to avoid [VerifyError] when using [SurfaceControl] on API < 29.
 *
 * This wraps [SurfaceControl] on API >= 29 and is a no-op stub on API < 29.
 */
sealed interface SurfaceControlCompat {

    /** Create a new Surface from this SurfaceControl, or return null if this is a stub. */
    fun newSurface(): Surface?

    /** Sets the buffer size of this surface control. */
    fun setBufferSize(width: Int, height: Int)

    /** Release this surface control. */
    fun release()

    /** Reparent the surface control to null. */
    fun detach()

    companion object {
        /**
         * Creates a SurfaceControl or a stub implementation.
         *
         * @param parent The SurfaceView to use as a parent.
         * @param format The format to use for the SurfaceControl (on newer APIs).
         * @param width The width to set on the SurfaceControl or the Surface.
         * @param height The height to set on the SurfaceControl or the Surface.
         * @param name The name of the SurfaceControl to create.
         * @return a compat implementation of [SurfaceControlCompat].
         */
        @JvmStatic
        fun create(
            parent: SurfaceView,
            format: Int,
            width: Int,
            height: Int,
            name: String
        ): SurfaceControlCompat =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                SurfaceControlApi29Impl(parent, format, width, height, name)
            } else {
                SurfaceControlStub
            }
    }

    /** API 29+ implementation of [SurfaceControlCompat]. */
    @RequiresApi(Build.VERSION_CODES.Q)
    private class SurfaceControlApi29Impl(
        parent: SurfaceView,
        format: Int,
        width: Int,
        height: Int,
        name: String
    ) : SurfaceControlCompat {
        private val surfaceControl: SurfaceControl =
            SurfaceControl.Builder()
                .setName(name)
                .setFormat(format)
                .setBufferSize(width, height)
                .setParent(parent.surfaceControl)
                .build()

        init {
            SurfaceControl.Transaction().use { transaction ->
                transaction.setVisibility(surfaceControl, true).apply()
            }
        }

        override fun newSurface(): Surface? {
            return Surface(surfaceControl)
        }

        override fun setBufferSize(width: Int, height: Int) {
            SurfaceControl.Transaction().use { transaction ->
                transaction.setBufferSize(surfaceControl, width, height).apply()
            }
        }

        override fun release() {
            surfaceControl.release()
        }

        override fun detach() {
            SurfaceControl.Transaction().use { transaction ->
                transaction.reparent(surfaceControl, null).apply()
            }
        }
    }

    /** Stub implementation of [SurfaceControlCompat] for older APIs. */
    private object SurfaceControlStub : SurfaceControlCompat {
        override fun newSurface(): Surface? = null

        override fun setBufferSize(width: Int, height: Int) {
            // No-op for older APIs
        }

        override fun release() {
            // No-op for older APIs
        }

        override fun detach() {
            // No-op for older APIs
        }
    }
}
