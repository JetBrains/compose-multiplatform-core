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

package androidx.camera.compose

import android.view.Surface
import androidx.camera.core.SurfaceRequest
import androidx.camera.core.SurfaceRequest.TransformationInfo as CXTransformationInfo
import androidx.camera.viewfinder.compose.MutableCoordinateTransformer
import androidx.camera.viewfinder.compose.Viewfinder
import androidx.camera.viewfinder.core.ImplementationMode
import androidx.camera.viewfinder.core.TransformationInfo
import androidx.camera.viewfinder.core.ViewfinderSurfaceRequest
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * An adapter composable that displays frames from CameraX by completing provided [SurfaceRequest]s.
 *
 * This is a wrapper around [Viewfinder] that will convert a CameraX [SurfaceRequest] internally
 * into a [ViewfinderSurfaceRequest]. Additionally, all interactions normally handled through the
 * [ViewfinderSurfaceRequest] will be derived from the [SurfaceRequest].
 *
 * If [implementationMode] is changed while the provided [surfaceRequest] has been fulfilled, the
 * surface request will be invalidated as if [SurfaceRequest.invalidate] has been called. This will
 * allow CameraX to know that a new surface request is required since the underlying viewfinder
 * implementation will be providing a new surface.
 *
 * Example usage:
 *
 * @sample androidx.camera.compose.samples.CameraXViewfinderSample
 * @param surfaceRequest The surface request from CameraX
 * @param modifier The [Modifier] to be applied to this viewfinder
 * @param implementationMode The [ImplementationMode] to be used by this viewfinder.
 * @param coordinateTransformer The [MutableCoordinateTransformer] used to map offsets of this
 *   viewfinder to the source coordinates of the data being provided to the surface that fulfills
 *   [surfaceRequest]
 * @param alignment Optional alignment parameter used to place the camera feed in the given bounds
 *   of the [CameraXViewfinder]. Defaults to [Alignment.Center].
 * @param contentScale Optional scale parameter used to determine the aspect ratio scaling to be
 *   used to fit the camera feed in the bounds of the [CameraXViewfinder]. Defaults to
 *   [ContentScale.Crop].
 */
@Composable
public fun CameraXViewfinder(
    surfaceRequest: SurfaceRequest,
    modifier: Modifier = Modifier,
    implementationMode: ImplementationMode = ImplementationMode.EXTERNAL,
    coordinateTransformer: MutableCoordinateTransformer? = null,
    alignment: Alignment = Alignment.Center,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val currentImplementationMode by rememberUpdatedState(implementationMode)

    val viewfinderArgs by
        produceState<ViewfinderArgs?>(initialValue = null, surfaceRequest) {
            // Cancel this produceScope in case we haven't yet produced a complete
            // ViewfinderArgs.
            surfaceRequest.addRequestCancellationListener(Runnable::run) {
                this@produceState.cancel()
            }

            // Convert the CameraX TransformationInfo callback into a StateFlow
            val transformationInfoFlow: StateFlow<CXTransformationInfo?> =
                MutableStateFlow<CXTransformationInfo?>(null)
                    .also { stateFlow ->
                        // Set a callback to update this state flow
                        surfaceRequest.setTransformationInfoListener(Runnable::run) { transformInfo
                            ->
                            // Set the next value of the flow
                            stateFlow.value = transformInfo
                        }
                    }
                    .asStateFlow()

            // The ImplementationMode that will be used for all TransformationInfo updates.
            // This is locked in once we have updated ViewfinderArgs and won't change until
            // this produceState block is cancelled and restarted.
            var snapshotImplementationMode: ImplementationMode? = null
            snapshotFlow { currentImplementationMode }
                .combine(transformationInfoFlow.filterNotNull()) { implMode, transformInfo ->
                    Pair(implMode, transformInfo)
                }
                .takeWhile { (implMode, _) ->
                    val shouldAbort =
                        snapshotImplementationMode != null && implMode != snapshotImplementationMode
                    if (shouldAbort) {
                        // Abort flow and invalidate SurfaceRequest so a new SurfaceRequest will
                        // be sent.
                        surfaceRequest.invalidate()
                    } else {
                        // Got the first ImplementationMode. This will be used until this
                        // produceState is cancelled.
                        snapshotImplementationMode = implMode
                    }
                    !shouldAbort
                }
                .collect { (implMode, transformInfo) ->
                    value =
                        ViewfinderArgs(
                            surfaceRequest,
                            implMode,
                            TransformationInfo(
                                sourceRotation = transformInfo.rotationDegrees,
                                isSourceMirroredHorizontally = transformInfo.isMirroring,
                                isSourceMirroredVertically = false,
                                cropRectLeft = transformInfo.cropRect.left.toFloat(),
                                cropRectTop = transformInfo.cropRect.top.toFloat(),
                                cropRectRight = transformInfo.cropRect.right.toFloat(),
                                cropRectBottom = transformInfo.cropRect.bottom.toFloat()
                            )
                        )
                }
        }

    viewfinderArgs?.let { args ->
        // Convert the CameraX SurfaceRequest to ViewfinderSurfaceRequest. There should
        // always be a 1:1 mapping of CameraX SurfaceRequest to ViewfinderSurfaceRequest.
        val viewFinderSurfaceRequest =
            remember(args.surfaceRequest, args.implementationMode) {
                ViewfinderSurfaceRequest(
                    width = args.surfaceRequest.resolution.width,
                    height = args.surfaceRequest.resolution.height,
                    implementationMode = args.implementationMode,
                    requestId = "CXSurfaceRequest-${"%x".format(surfaceRequest.hashCode())}"
                )
            }

        val surfaceRequestScope =
            remember(args.surfaceRequest) { SurfaceRequestScope(args.surfaceRequest) }
        DisposableEffect(surfaceRequestScope) { onDispose { surfaceRequestScope.complete() } }
        Viewfinder(
            surfaceRequest = viewFinderSurfaceRequest,
            transformationInfo = args.transformationInfo,
            modifier = modifier.fillMaxSize(),
            coordinateTransformer = coordinateTransformer,
            alignment = alignment,
            contentScale = contentScale
        ) {
            onSurfaceSession {
                // If we're providing a surface, we must wait for the source to be
                // finished with the surface before we allow the surface session to
                // complete, so always run inside a non-cancellable context
                withContext(NonCancellable) {
                    with(surfaceRequestScope) { provideSurfaceAndWaitForCompletion(surface) }
                }
            }
        }
    }
}

private class SurfaceRequestScope(val surfaceRequest: SurfaceRequest) : CoroutineScope {
    val surfaceRequestJob = Job()
    override val coroutineContext: CoroutineContext = surfaceRequestJob + Dispatchers.Unconfined

    init {
        surfaceRequest.addRequestCancellationListener(Runnable::run) {
            this.cancel("SurfaceRequest has been cancelled.")
        }
    }

    suspend fun provideSurfaceAndWaitForCompletion(surface: Surface) =
        suspendCancellableCoroutine<Unit> { continuation ->
            surfaceRequest.provideSurface(surface, Runnable::run) { continuation.resume(Unit) }

            continuation.invokeOnCancellation {
                assert(false) {
                    "provideSurfaceAndWaitForCompletion should always be called in a " +
                        "NonCancellable context to ensure the Surface is not closed before the " +
                        "frame source has finished using it."
                }
            }
        }

    fun complete() {
        // If a surface hasn't yet been provided the surface, this call will succeed. Otherwise
        // it will be a no-op.
        surfaceRequest.willNotProvideSurface()
        // Ensure the job of this coroutine completes.
        surfaceRequestJob.complete()
    }
}

@Immutable
private data class ViewfinderArgs(
    val surfaceRequest: SurfaceRequest,
    val implementationMode: ImplementationMode,
    val transformationInfo: TransformationInfo
)
