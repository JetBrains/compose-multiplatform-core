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

package androidx.xr.compose.subspace.layout

import androidx.xr.compose.subspace.node.SubspaceModifierNodeElement
import androidx.xr.scenecore.PointSourceParams

/**
 * Used to provide a [PointSourceParams] to allow specifying the modified Composable as an audio
 * source. See [PointSourceParams] for more info on how to attach this object to a media source.
 *
 * PointSourceParams are used to configure a sound to be spatialized as a point in 3D space. This is
 * to override the default behavior where sound is played from the SpatialMainPanel.
 *
 * Example usage:
 * ```kotlin
 * @Composable
 * @SubspaceComposable
 * public fun SampleLayoutWithMedia(mediaUri: Uri) {
 *     val session = LocalSession.current
 *     val context = LocalContext.current
 *
 *     SpatialColumn {
 *         SpatialMainPanel()
 *
 *         // Audio would play from the SpatialMainPanel above unless we utilize
 *         // onPointSourceParamsAvailable with this Composable.
 *         val mediaPlayer = remember { MediaPlayer() }
 *         val paramsSet = remember { mutableStateOf(false) }
 *         SpatialPanel(
 *             SubspaceModifier.size(400.dp)
 *                 .onPointSourceParamsAvailable {
 *                     if (!paramsSet.value) {
 *                          paramsSet.value = true
 *                          mediaPlayer.setDataSource(context, mediaUri)
 *                          SpatialMediaPlayer.setPointSourceParams(session!!, mediaPlayer, it)
 *                          mediaPlayer.prepare()
 *                          mediaPlayer.start()
 *                     }
 *                 }
 *                 .movable()
 *         ) {
 *             DisposableEffect(Unit) { onDispose { mediaPlayer.release() } }
 *
 *             // Use this for playing video, or omit it for audio only use cases.
 *             AndroidExternalSurface {
 *                 onSurface { surface, _, _ -> mediaPlayer.setSurface(surface) }
 *             }
 *         }
 *     }
 * }
 * ```
 *
 * @param onPointSourceParamsAvailable Will be called with a [PointSourceParams] once it is
 *   generated.
 */
public fun SubspaceModifier.onPointSourceParamsAvailable(
    onPointSourceParamsAvailable: (PointSourceParams) -> Unit
): SubspaceModifier = this.then(PointSourceElement(onPointSourceParamsAvailable))

private class PointSourceElement(private val onPointSourceParams: (PointSourceParams) -> Unit) :
    SubspaceModifierNodeElement<PointSourceNode>() {

    override fun create(): PointSourceNode = PointSourceNode(onPointSourceParams)

    override fun update(node: PointSourceNode) {
        node.onPointSourceParams = onPointSourceParams
    }

    override fun hashCode(): Int {
        return onPointSourceParams.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PointSourceElement) return false

        return onPointSourceParams === other.onPointSourceParams
    }
}

private class PointSourceNode(internal var onPointSourceParams: (PointSourceParams) -> Unit) :
    SubspaceModifier.Node(), CoreEntityNode {
    private var hasInvoked = false

    override fun CoreEntityScope.modifyCoreEntity() {
        if (!hasInvoked) {
            onPointSourceParams(PointSourceParams(coreEntity.entity))
            hasInvoked = true
        }
    }
}
