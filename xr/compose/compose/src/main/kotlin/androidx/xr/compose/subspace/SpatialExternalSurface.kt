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

package androidx.xr.compose.subspace

import android.view.Surface
import androidx.annotation.RestrictTo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.ExperimentalComposeApi
import androidx.compose.runtime.remember
import androidx.xr.compose.platform.LocalSession
import androidx.xr.compose.subspace.layout.CoreSurfaceEntity
import androidx.xr.compose.subspace.layout.SpatialAlignment
import androidx.xr.compose.subspace.layout.SpatialFeatheringEffect
import androidx.xr.compose.subspace.layout.SpatialSmoothFeatheringEffect
import androidx.xr.compose.subspace.layout.SubspaceLayout
import androidx.xr.compose.subspace.layout.SubspaceModifier
import androidx.xr.compose.subspace.layout.ZeroFeatheringSize
import androidx.xr.scenecore.SurfaceEntity

private const val DEFAULT_SIZE_PX = 400

/**
 * [SpatialExternalSurfaceScope] is a scoped environment that provides the [Surface] associated with
 * a [SpatialExternalSurface]
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
public interface SpatialExternalSurfaceScope {
    /**
     * Invoked only one time when the Surface is created. This will execute before any layout or
     * modifiers are computed.
     */
    public fun onSurfaceCreated(onSurfaceCreated: (Surface) -> Unit)

    /** Invoked when the Composable and its associated [Surface] are destroyed. */
    public fun onSurfaceDestroyed(onSurfaceDestroyed: (Surface) -> Unit)
}

private class SpatialExternalSurfaceScopeInstance(private val entity: CoreSurfaceEntity) :
    SpatialExternalSurfaceScope {

    private var executedInit = false
    private var pendingOnDestroy: ((Surface) -> Unit)? = null

    override fun onSurfaceCreated(onSurfaceCreated: (Surface) -> Unit) {
        if (!executedInit) {
            executedInit = true
            onSurfaceCreated(entity.surfaceEntity.getSurface())
        }
    }

    override fun onSurfaceDestroyed(onSurfaceDestroyed: (Surface) -> Unit) {
        pendingOnDestroy = onSurfaceDestroyed
    }

    internal fun executeOnDestroy() {
        pendingOnDestroy?.let { it(entity.surfaceEntity.getSurface()) }
        entity.dispose()
    }
}

/** Mode for SpatialExternalSurface display. */
@JvmInline
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
public value class StereoMode private constructor(public val value: Int) {
    public companion object {
        /** Each eye will see the entire surface (no separation). */
        public val Mono: StereoMode = StereoMode(SurfaceEntity.StereoMode.MONO)
        /** The [top, bottom] halves of the surface will map to [left, right] eyes. */
        public val TopBottom: StereoMode = StereoMode(SurfaceEntity.StereoMode.TOP_BOTTOM)
        /** The [left, right] halves of the surface will map to [left, right] eyes. */
        public val SideBySide: StereoMode = StereoMode(SurfaceEntity.StereoMode.SIDE_BY_SIDE)
    }
}

/** Protection levels for the Surface content. */
@JvmInline
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
public value class ContentSecurityLevel private constructor(public val value: Int) {
    public companion object {
        /** No security is applied. */
        public val None: ContentSecurityLevel =
            ContentSecurityLevel(SurfaceEntity.ContentSecurityLevel.NONE)
        /**
         * Protects digital rights content that is encoded in a scheme supported by the device. This
         * will prevent recordings of the Surface content. This protection level is only usable with
         * secure media content. A protected Surface can't play non-drm digital content.
         */
        public val DrmProtected: ContentSecurityLevel =
            ContentSecurityLevel(SurfaceEntity.ContentSecurityLevel.PROTECTED)
    }
}

/**
 * A Composable that creates and owns an Android Surface into which the application can render
 * stereo image content. This Surface is then texture mapped to the canvas, and if a stereoscopic
 * StereoMode is specified, then the User will see left and right eye content mapped to the
 * appropriate display. Width and height will default to 400 pixels if it is not specified using
 * size modifiers.
 *
 * Note that this Surface does not capture input events. It is also not currently possible to
 * synchronize StereoMode changes with application rendering or video decoding. This composable
 * currently cannot render in front of other panels, so movable modifier usage is not recommended if
 * there are other panels in the layout, aside from the content block of this Composable. Digital
 * rights management provided by different media players will require the proper
 * [contentSecurityLevel] to be set.
 *
 * @param modifier SubspaceModifiers to apply to the SpatialSurfacePanel.
 * @param stereoMode The [StereoMode] which describes how parts of the surface are displayed to the
 *   user's eyes. This will affect how the content is interpreted and displayed on the surface.
 * @param featheringEffect A [SpatialFeatheringEffect] to apply to to canvas of
 *   [SpatialExternalSurfaceScope.surface].
 * @param contentSecurityLevel Sets a security level of the Surface to secure digital content. Using
 *   DrmProtected is currently in an Experimental state. Currently this field does not support
 *   recomposition, and there might be rendering issues if creating a new SpatialExternalSurface
 *   immediately after removing a DrmProtected one from composition. Playing a list of mixed drm and
 *   non drm content with one Surface is not supported. There may also be rendering issues if the
 *   AndroidManifest includes a full space start mode for the Activity using this Composable.
 * @param content Content block where the surface can be accessed using
 *   [SpatialExternalSurfaceScope.surface]. Composable content will be rendered over the Surface
 *   canvas. If using [StereoMode.SideBySide] or [StereoMode.TopBottom], it is recommended to offset
 *   Composable content far enough to avoid depth perception issues.
 */
@Composable
@SubspaceComposable
@ExperimentalComposeApi
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
public fun SpatialExternalSurface(
    stereoMode: StereoMode,
    modifier: SubspaceModifier = SubspaceModifier,
    featheringEffect: SpatialFeatheringEffect = SpatialSmoothFeatheringEffect(ZeroFeatheringSize),
    contentSecurityLevel: ContentSecurityLevel = ContentSecurityLevel.None,
    content: @Composable @SubspaceComposable SpatialExternalSurfaceScope.() -> Unit,
) {
    val session = LocalSession.current

    val coreSurfaceEntity = rememberCoreSurfaceEntity {
        SurfaceEntity.create(
            session = checkNotNull(session) { "Session is required" },
            stereoMode = stereoMode.value,
            contentSecurityLevel = contentSecurityLevel.value,
        )
    }
    val instance = remember { SpatialExternalSurfaceScopeInstance(coreSurfaceEntity) }

    // Stereo mode can update during a recomposition.
    coreSurfaceEntity.stereoMode = stereoMode.value

    coreSurfaceEntity.setFeatheringEffect(featheringEffect)

    DisposableEffect(true) { onDispose { instance.executeOnDestroy() } }

    SubspaceLayout(
        modifier = modifier,
        coreEntity = coreSurfaceEntity,
        content = { instance.content() },
        measurePolicy = SpatialBoxMeasurePolicy(SpatialAlignment.Center, false),
    )
}
