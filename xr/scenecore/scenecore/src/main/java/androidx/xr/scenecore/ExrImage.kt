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

package androidx.xr.scenecore

import androidx.annotation.MainThread
import androidx.annotation.RestrictTo
import androidx.concurrent.futures.ResolvableFuture
import androidx.xr.runtime.Session
import androidx.xr.runtime.internal.ExrImageResource as RtExrImage
import androidx.xr.runtime.internal.JxrPlatformAdapter
import com.google.common.util.concurrent.ListenableFuture

/** Interface for image formats in SceneCore. */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX) public interface Image

/**
 * ExrImage represents an EXR Image resource in SceneCore. EXR images are used by the [Environment]
 * for drawing skyboxes.
 */
// TODO(b/319269278): Make this and GltfModel derive from a common Resource base class which has
//                    async helpers.
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
public class ExrImage
internal constructor(internal val image: RtExrImage, internal val session: Session? = null) :
    Image {

    /**
     * Returns the reflection texture from a preprocessed EXR image.
     *
     * This method must be called from the main thread.
     * https://developer.android.com/guide/components/processes-and-threads
     *
     * @return a CubeMapTexture.
     * @throws IllegalStateException if the reflection texture couldn't be retrieved or if the EXR
     *   image was not preprocessed.
     */
    @MainThread
    public fun getReflectionTexture(): CubeMapTexture {
        if (session == null) {
            throw IllegalStateException(
                "Can only retrieve reflection texture from preprocessed EXR images."
            )
        }
        val reflectionTexture = session.platformAdapter.getReflectionTextureFromIbl(image)
        if (reflectionTexture == null) {
            throw IllegalStateException(
                "Failed to retrieve reflection texture from the preprocessed EXR image."
            )
        }
        return CubeMapTexture(reflectionTexture, session)
    }

    public companion object {
        // ResolvableFuture is marked as RestrictTo(LIBRARY_GROUP_PREFIX), which is intended for
        // classes
        // within AndroidX. We're in the process of migrating to AndroidX. Without suppressing this
        // warning, however, we get a build error - go/bugpattern/RestrictTo.
        @SuppressWarnings("RestrictTo")
        internal fun createAsync(
            platformAdapter: JxrPlatformAdapter,
            name: String,
            session: Session,
        ): ListenableFuture<ExrImage> {
            return createExrImageFuture(platformAdapter.loadExrImageByAssetName(name), session)
        }

        @SuppressWarnings("RestrictTo")
        internal fun createAsync(
            platformAdapter: JxrPlatformAdapter,
            byteArray: ByteArray,
            assetKey: String,
            session: Session,
        ): ListenableFuture<ExrImage> {
            return createExrImageFuture(
                platformAdapter.loadExrImageByByteArray(byteArray, assetKey),
                session,
            )
        }

        /**
         * Public factory function for a preprocessed EXRImage, where the preprocessed EXRImage is
         * asynchronously loaded.
         *
         * This method must be called from the main thread.
         * https://developer.android.com/guide/components/processes-and-threads
         *
         * @param session The [Session] to use for loading the asset.
         * @param name The URL or asset-relative path of a the preprocessed EXR image to be loaded
         * @return a ListenableFuture<ExrImage>. Listeners will be called on the main thread if
         *   Runnable::run is supplied.
         */
        @MainThread
        @JvmStatic
        public fun create(session: Session, name: String): ListenableFuture<ExrImage> {
            return ExrImage.createAsync(session.platformAdapter, name, session)
        }

        /**
         * Public factory function for a preprocessed EXRImage, where the preprocessed EXRImage is
         * asynchronously loaded.
         *
         * This method must be called from the main thread.
         * https://developer.android.com/guide/components/processes-and-threads
         *
         * @param session The [Session] to use for loading the asset.
         * @param assetData The byte array of the preprocessed EXR image to be loaded.
         * @param assetKey The key of the preprocessed EXR image to be loaded. This is used to
         *   identify the asset in the [SceneCore] cache.
         * @return a ListenableFuture<ExrImage>. Listeners will be called on the main thread if
         *   Runnable::run is supplied.
         */
        @MainThread
        @JvmStatic
        public fun create(
            session: Session,
            assetData: ByteArray,
            assetKey: String,
        ): ListenableFuture<ExrImage> {
            return ExrImage.createAsync(session.platformAdapter, assetData, assetKey, session)
        }

        private fun createExrImageFuture(
            exrImageResourceFuture: ListenableFuture<RtExrImage>,
            session: Session,
        ): ListenableFuture<ExrImage> {
            val exrImageFuture = ResolvableFuture.create<ExrImage>()

            exrImageResourceFuture.addListener(
                {
                    try {
                        exrImageFuture.set(ExrImage(exrImageResourceFuture.get(), session))
                    } catch (e: Exception) {
                        if (e is InterruptedException) Thread.currentThread().interrupt()
                        exrImageFuture.setException(e)
                    }
                },
                Runnable::run,
            )

            return exrImageFuture
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ExrImage

        // Perform a structural equality check on the underlying image.
        if (image != other.image) return false

        return true
    }

    override fun hashCode(): Int {
        return image.hashCode()
    }
}
