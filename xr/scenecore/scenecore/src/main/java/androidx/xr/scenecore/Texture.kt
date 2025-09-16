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
import androidx.concurrent.futures.ResolvableFuture
import androidx.xr.runtime.Session
import androidx.xr.scenecore.internal.JxrPlatformAdapter
import androidx.xr.scenecore.internal.TextureResource as RtTextureResource
import com.google.common.util.concurrent.ListenableFuture
import java.io.File
import java.nio.file.Path
import java.util.concurrent.CancellationException

/**
 * Represents a [Texture] in SceneCore.
 *
 * A texture is an image that can be applied to a 3D model to give it color, detail, and realism. It
 * can also be used as an alpha mask for a [StereoSurfaceEntity].
 *
 * It's important to dispose of the [Texture] when it's no longer needed to free up resources. This
 * can be done by calling the [dispose] method.
 */
@Suppress("NotCloseable")
public open class Texture
internal constructor(internal val texture: RtTextureResource, internal val session: Session) {

    /**
     * Disposes the given [Texture].
     *
     * Currently, a glTF model (which this texture will be used with) can't be disposed. This means
     * that calling dispose on the texture will lead to a crash if the call is made out of order,
     * that is, if the texture is disposed before the glTF model that uses it.
     *
     * When using a texture as an alpha mask for stereoscopic content, the [StereoSurfaceEntity]
     * should be disposed before the texture is disposed.
     */
    // TODO(b/376277201): Provide Session.GltfModel.dispose().
    @MainThread
    public open fun dispose() {
        session.runtimes.filterIsInstance<JxrPlatformAdapter>().single().destroyTexture(texture)
    }

    public companion object {
        @SuppressWarnings("RestrictTo")
        internal fun createAsync(
            platformAdapter: JxrPlatformAdapter,
            name: String,
            session: Session,
        ): ListenableFuture<Texture> {
            val textureResourceFuture = platformAdapter.loadTexture(name)
            val textureFuture = ResolvableFuture.create<Texture>()
            textureResourceFuture!!.addListener(
                {
                    try {
                        val texture = textureResourceFuture.get()
                        textureFuture.set(Texture(texture, session))
                    } catch (e: Exception) {
                        if (e is InterruptedException) {
                            Thread.currentThread().interrupt()
                        }
                        if (e is CancellationException) {
                            textureFuture.cancel(false)
                        } else {
                            textureFuture.setException(e)
                        }
                    }
                },
                Runnable::run,
            )
            return textureFuture
        }

        /**
         * Public factory for a Texture, asynchronously loading a preprocessed texture from a [Path]
         * relative to the application's `assets/` folder.
         *
         * Currently, only URLs and relative paths from the `assets/` directory are supported.
         *
         * @param session The [Session] to use for loading the [Texture].
         * @param path The Path of the `.png` texture file to be loaded, relative to the
         *   application's `assets/` folder.
         * @return a [Texture] upon completion.
         * @throws IllegalArgumentException if [Path.isAbsolute] is true, as this method requires a
         *   relative path.
         */
        @MainThread
        @JvmStatic
        public suspend fun create(session: Session, path: Path): Texture {
            require(!File(path.toString()).isAbsolute) {
                "Texture.create() expects a path relative to `assets/`, received absolute path $path."
            }
            return createAsync(session.platformAdapter, path.toString(), session).awaitSuspending()
        }
    }
}
