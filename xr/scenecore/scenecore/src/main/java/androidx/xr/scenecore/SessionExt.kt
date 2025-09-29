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

@file:JvmName("SessionExt")

package androidx.xr.scenecore

import android.app.Activity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.xr.arcore.internal.PerceptionRuntime
import androidx.xr.runtime.Session
import androidx.xr.scenecore.internal.RenderingRuntime
import androidx.xr.scenecore.internal.SceneRuntime
import java.util.Collections
import java.util.WeakHashMap

/**
 * A generic extension function for Session to get or create an instance from a provided cache.
 *
 * @param T The type of the instance to retrieve.
 * @param cache The thread-safe cache map to use.
 * @param finder The lambda function to find the instance if it's not in the cache.
 * @return The cached or newly created instance of type T.
 */
private inline fun <reified T> Session.getOrPutFromCache(
    cache: MutableMap<Session, T>,
    crossinline finder: Session.() -> T,
): T {
    check(this.activity.lifecycle.currentState != Lifecycle.State.DESTROYED) {
        "Session has been destroyed."
    }
    return cache.getOrPut(this) { this.finder() }
}

/**
 * A thread-safe, memory-safe cache to store the Scene for each Session instance.
 *
 * A [WeakHashMap] is used to prevent memory leaks. It allows the garbage collector to remove
 * entries when the [Session] key is no longer in use elsewhere. This is wrapped in a
 * [Collections.synchronizedMap] to ensure thread safety.
 */
// TODO: b/437204809 - Change sceneCache to be an AtomicReference.
private val sceneCache = Collections.synchronizedMap(WeakHashMap<Session, Scene>())

/** Get the Lifecycle associated with the [Activity] attached to the [Session]. */
private val Activity.lifecycle: Lifecycle
    get() = (this as LifecycleOwner).lifecycle

/**
 * Gets the [Scene] associated with this Session.
 *
 * Accessing the scene in a destroyed activity can be dangerous.
 *
 * The `Scene` is the primary interface for creating and managing spatial content. There is a single
 * `Scene` instance for each `Session`.
 *
 * @see Scene
 */
public val Session.scene: Scene
    get() = checkAndGetScene(this)

/** Gets the [Scene] associated with the given [Session], using a cache. */
private fun checkAndGetScene(session: Session): Scene {
    return sceneCache.getOrPut(session) {
        // This lambda is executed only once per session instance.
        session.sessionConnectors.filterIsInstance<Scene>().single()
    }
}

internal fun removeSceneFromCache(scene: Scene) {
    synchronized(sceneCache) {
        val iterator = sceneCache.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.value == scene) {
                iterator.remove()
                // Assuming a one-to-one mapping, we can stop after finding the match.
                break
            }
        }
    }
}

private val sceneRuntimeCache = Collections.synchronizedMap(WeakHashMap<Session, SceneRuntime>())

internal val Session.sceneRuntime: SceneRuntime
    get() =
        getOrPutFromCache(sceneRuntimeCache) {
            // This lambda is executed only once per session instance.
            runtimes.filterIsInstance<SceneRuntime>().single()
        }

private val renderingRuntimeCache =
    Collections.synchronizedMap(WeakHashMap<Session, RenderingRuntime>())

internal val Session.renderingRuntime: RenderingRuntime
    get() =
        getOrPutFromCache(renderingRuntimeCache) {
            // This lambda is executed only once per session instance.
            runtimes.filterIsInstance<RenderingRuntime>().single()
        }

private val perceptionRuntimeCache =
    Collections.synchronizedMap(WeakHashMap<Session, PerceptionRuntime>())

internal val Session.perceptionRuntime: PerceptionRuntime
    get() =
        getOrPutFromCache(perceptionRuntimeCache) {
            // This lambda is executed only once per session instance.
            runtimes.filterIsInstance<PerceptionRuntime>().single()
        }
