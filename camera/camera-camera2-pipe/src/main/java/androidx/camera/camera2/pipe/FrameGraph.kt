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

package androidx.camera.camera2.pipe

import androidx.annotation.RestrictTo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred

/** [FrameGraph] extends the capabilities of [CameraGraph] to provide stream controls. */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public interface FrameGraph : BaseGraph {
    public class Config

    /** A [FrameBuffer] is a handle to a set of streams that are attached to a [FrameGraph]. */
    public interface FrameBuffer : AutoCloseable {
        public val streams: Set<StreamId>

        public val parameters: Map<Any, Any?>
    }

    /**
     * Attach a stream(s) and associated parameters. Closing the return value will detach the
     * stream(s).
     */
    public fun captureWith(
        streamIds: Set<StreamId> = emptySet(),
        parameters: Map<Any, Any?> = emptyMap()
    ): FrameBuffer

    /**
     * Used exclusively interact with the camera via a [Session] from within an existing suspending
     * function. This function will suspend until the internal mutex lock can be acquired and
     * returned. When possible, prefer [useSession] when possible as it will guarantee that the
     * session will be closed.
     *
     * The returned [Session] **must** be closed.
     *
     * @return A [Session] instance for interaction.
     */
    public suspend fun acquireSession(): Session

    /**
     * Immediately try to acquire access to the internal mutex lock, and return null if it is not
     * currently available.
     *
     * The returned [Session] **must** be closed.
     *
     * @return A [FrameGraph.Session] instance if the lock was acquired immediately, null otherwise.
     */
    public fun acquireSessionOrNull(): Session?

    /**
     * Used exclusively interact with the camera via a [Session] from within an existing suspending
     * function. This method will suspend until the internal mutex lock can be acquired. This is
     * similar to [acquireSession] an [use] with the additional guarantee that all launch and async
     * calls will complete before the lock is released (unless the [Session] is closed early). The
     * [action] will always execute unless parent scope has been canceled.
     *
     * Example:
     * ```
     * suspend fun process(frameGraph: FrameGraph, analysisStream: CameraStream) {
     *     frameGraph.useSession { session ->
     *         val result = session.capture(
     *             Request(streams = listOf(jpegStream.id))
     *         )
     *         val frame = result.awaitFrame()
     *         val image = frame?.awaitImage(analysisStream.id)
     *         // process image if not null
     *     }
     * }
     * ```
     *
     * @param action The suspending lambda block to execute with the acquired [Session].
     * @return The result of the [action] lambda.
     */
    public suspend fun <T> useSession(action: suspend CoroutineScope.(Session) -> T): T

    /**
     * Used to exclusively interact with the camera from a normal function with a
     * [FrameGraph.Session] by acquiring a lock to the internal mutex and running the [action] in
     * the provided [scope]. This is similar to [useSession] with the additional guarantee that
     * multiple calls to [useSessionIn] will be executed in the same order they are invoked in,
     * which is not the case for `scope.launch` or `scope.async`. When possible, prefer using this
     * function when interacting with a [Session] from non-suspending code. The [action] will always
     * execute unless parent scope has been canceled.
     *
     * Example:
     * ```
     * fun capture(
     *     frameGraph: frameGraph, jpegStream: CameraStream, scope: CoroutineScope
     * ) {
     *     frameGraph.useSessionIn(scope) { session ->
     *         val result = session.capture(
     *             Request(streams = listOf(jpegStream.id))
     *         )
     *         val frame = result.awaitFrame()
     *         val jpeg = frame?.awaitImage(jpegStream.id)
     *         // Save jpeg
     *     }
     * }
     * ```
     *
     * @param scope The CoroutineScope in which to execute the action.
     * @param action The suspending lambda block to execute with the acquired [FrameGraph.Session].
     * @return A Deferred representing the result of the asynchronous operation.
     */
    public fun <T> useSessionIn(
        scope: CoroutineScope,
        action: suspend CoroutineScope.(Session) -> T
    ): Deferred<T>

    /**
     * A [Session] is an interactive lock for [FrameGraph].
     *
     * Holding this object prevents other systems from acquiring a [Session] until the currently
     * held session is released. Because of its exclusive nature, [Session]s are intended for fast,
     * short-lived state updates, or for interactive capture sequences that must not be altered.
     * (Flash photo sequences, for example).
     *
     * While this object is thread-safe, it should not shared or held for long periods of time.
     * Example: A [Session] should *not* be held during video recording.
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP) public interface Session : CameraGraph.Session
}
