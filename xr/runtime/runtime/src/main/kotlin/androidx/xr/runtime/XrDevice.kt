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

package androidx.xr.runtime

import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.xr.runtime.internal.XrDeviceCapabilityProvider
import androidx.xr.runtime.internal.XrDeviceCapabilityProviderFactory
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/** Provides hardware capabilities of the device. */
public class XrDevice
private constructor(
    private val session: Session,
    private val xrDeviceCapabilityProvider: XrDeviceCapabilityProvider?,
) {

    /**
     * Returns this XrDevice's [Lifecycle].
     *
     * The value will be the Projected device's lifecycle if its [Context] was used when calling
     * [getCurrentDevice]. Otherwise, the [Session's][Session] lifecycle will be returned.
     */
    @ExperimentalXrDeviceLifecycleApi
    public fun getLifecycle(): Lifecycle =
        xrDeviceCapabilityProvider?.lifecycle ?: (session.activity as LifecycleOwner).lifecycle

    /** A device capability that determines how virtual content is added to the real world. */
    public class DisplayBlendMode private constructor(private val value: Int) {

        public companion object {
            /** Blending is not supported. */
            @JvmField public val NO_DISPLAY: DisplayBlendMode = DisplayBlendMode(0)
            /**
             * Virtual content is added to the real world by adding the pixel values for each of
             * Red, Green, and Blue components. Alpha is ignored. Black pixels will appear
             * transparent.
             */
            @JvmField public val ADDITIVE: DisplayBlendMode = DisplayBlendMode(1)
            /**
             * Virtual content is added to the real world by alpha blending the pixel values based
             * on the Alpha component.
             */
            @JvmField public val ALPHA_BLEND: DisplayBlendMode = DisplayBlendMode(2)
        }

        public override fun toString(): String =
            when (this) {
                NO_DISPLAY -> "NOT_APPLICABLE"
                ADDITIVE -> "ADDITIVE"
                ALPHA_BLEND -> "ALPHA_BLEND"
                else -> "UNKNOWN"
            }
    }

    public companion object {

        private val CAPABILITY_FACTORY_PROVIDERS =
            listOf("androidx.xr.projected.ProjectedDeviceCapabilityProviderFactory")

        /**
         * Get the current [XrDevice] for the provided [Session].
         *
         * @param session the [Session] connected to the device.
         */
        @JvmStatic
        public fun getCurrentDevice(session: Session): XrDevice =
            XrDevice(session, xrDeviceCapabilityProvider = null)

        /**
         * Get the current [XrDevice] for the provided [Context].
         *
         * @param context the [Context] associated with the device
         * @param session the [Session] connected to the device
         * @param coroutineContext the [CoroutineContext] to use for the XrDevice operations
         * @throws IllegalArgumentException if the provided [Context] is not supported
         */
        @JvmStatic
        @JvmOverloads
        @ExperimentalXrDeviceLifecycleApi
        public fun getCurrentDevice(
            context: Context,
            session: Session,
            coroutineContext: CoroutineContext = EmptyCoroutineContext,
        ): XrDevice {
            val features = getDeviceContextFeatures(context)
            val xrDeviceCapabilityProviderFactory: XrDeviceCapabilityProviderFactory? =
                selectProvider(
                    loadProviders(
                        XrDeviceCapabilityProviderFactory::class.java,
                        CAPABILITY_FACTORY_PROVIDERS,
                    ),
                    features,
                )

            return XrDevice(
                session,
                xrDeviceCapabilityProviderFactory?.create(context, coroutineContext),
            )
        }
    }

    /**
     * Returns the preferred display blend mode for this session.
     *
     * @return The [DisplayBlendMode] that is preferred by the [Session] for rendering.
     *   [DisplayBlendMode.NO_DISPLAY] will be returned if there are no supported blend modes
     *   available.
     * @throws IllegalStateException if the [Session] has been destroyed.
     */
    public fun getPreferredDisplayBlendMode(): DisplayBlendMode {
        return if (session.runtimes.isEmpty()) {
            DisplayBlendMode.NO_DISPLAY
        } else {
            session.runtimes.firstNotNullOf { it.getPreferredDisplayBlendMode() }
        }
    }
}
