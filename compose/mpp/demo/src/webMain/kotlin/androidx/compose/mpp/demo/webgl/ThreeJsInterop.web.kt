/*
 * Copyright 2026 The Android Open Source Project
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

@file:OptIn(ExperimentalWasmJsInterop::class)

package androidx.compose.mpp.demo.webgl

import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsAny
import kotlin.js.Promise
import kotlin.js.unsafeCast
import kotlinx.coroutines.suspendCancellableCoroutine
import org.khronos.webgl.WebGLFramebuffer
import org.khronos.webgl.WebGLRenderingContext
import org.w3c.dom.HTMLCanvasElement

/*
 * Typed bindings for the slice of three.js this demo uses.
 *
 * The three module is reached with a dynamic `import('three')` instead of `@JsModule` externals on
 * purpose: `@JsModule` requires `@JsNonModule` alongside it when the Kotlin/JS target compiles to
 * UMD (which this demo does), and Kotlin/Wasm has no `@JsNonModule` at all. A statically imported,
 * typed three.js binding therefore cannot live in a source set shared by js and wasmJs. Loading the
 * module dynamically and typing the objects it hands back keeps every line below in `webMain`, and
 * webpack still resolves the literal `import('three')` against the npm dependency at bundle time.
 */

/** The `three` module namespace object, as returned by `import('three')`. */
internal external interface ThreeModule : JsAny

internal external interface ThreeRenderer : JsAny {
    var autoClear: Boolean

    /**
     * Points [renderTarget] at a framebuffer three.js did not create — the hook WebXR uses, and the
     * reason this demo can keep owning the texture Skia adopted. Once set, `setRenderTarget` skips
     * three's own render target setup entirely, so it never allocates a texture, a framebuffer or a
     * depth buffer for that target.
     */
    fun setRenderTargetFramebuffer(renderTarget: ThreeRenderTarget, framebuffer: WebGLFramebuffer?)

    fun setRenderTarget(renderTarget: ThreeRenderTarget?)

    fun setClearColor(color: Int, alpha: Double)

    fun render(scene: ThreeScene, camera: ThreeCamera)

    /**
     * Makes three.js forget the GL state it cached. Mandatory here: Skia rendered the previous frame
     * through the very same context and left its own state behind. three.js documents this method as
     * being "mostly relevant for applications which share a single WebGL context across multiple
     * WebGL libraries", which is exactly this situation.
     */
    fun resetState()

    /** Releases three's own GL objects (programs, buffers, VAOs). Does not touch the context. */
    fun dispose()
}

/**
 * A three.js render target that is only a descriptor: its framebuffer is ours, set through
 * [ThreeRenderer.setRenderTargetFramebuffer]. Deliberately opaque, because neither `setSize()` nor
 * `dispose()` may ever be called on it — three's disposal path would delete a framebuffer it never
 * created, i.e. ours. Size changes create a new instance instead.
 */
internal external interface ThreeRenderTarget : JsAny

internal external interface ThreeObject3D : JsAny {
    val position: ThreeVector3
    val rotation: ThreeEuler
}

internal external interface ThreeScene : ThreeObject3D

internal external interface ThreeCamera : ThreeObject3D

internal external interface ThreePerspectiveCamera : ThreeCamera {
    var aspect: Double

    fun updateProjectionMatrix()
}

internal external interface ThreeMesh : ThreeObject3D

internal external interface ThreeStandardMaterial : JsAny {
    val color: ThreeColor
    var roughness: Double
    var metalness: Double
    var opacity: Double
}

internal external interface ThreeLight : ThreeObject3D {
    var intensity: Double
}

internal external interface ThreeColor : JsAny {
    fun setHSL(h: Double, s: Double, l: Double)
}

internal external interface ThreeVector3 : JsAny {
    var x: Double
    var y: Double
    var z: Double

    fun set(x: Double, y: Double, z: Double)
}

internal external interface ThreeEuler : JsAny {
    var x: Double
    var y: Double
    var z: Double
}

/** The handles the demo mutates every frame, bundled by [createKnotScene]. */
internal external interface ThreeKnotScene : JsAny {
    val scene: ThreeScene
    val camera: ThreePerspectiveCamera
    val knot: ThreeMesh
    val material: ThreeStandardMaterial
    val keyLight: ThreeLight
}

/** Loads the `three` npm package. */
internal suspend fun loadThreeModule(): ThreeModule? =
    importThree().await()?.unsafeCast<ThreeModule>()

/**
 * Creates the renderer on top of the canvas *and* the context Skiko already owns.
 *
 * Passing `context` is what keeps everything in one WebGL context — WebGL has no share groups, so a
 * renderer with its own context could never produce a texture Skia is allowed to read. The renderer
 * must therefore never be asked to resize anything (`setSize`, `setPixelRatio`) or to drop the
 * context (`forceContextLoss`): the canvas and the context belong to Compose.
 */
// language=js
internal fun createThreeRenderer(
    three: ThreeModule,
    canvas: HTMLCanvasElement,
    gl: WebGLRenderingContext,
): ThreeRenderer = js(
    """(function() {
        const renderer = new three.WebGLRenderer({
            canvas: canvas,
            context: gl,
            alpha: true,
            premultipliedAlpha: true,
        });
        renderer.autoClear = true;
        // Transparent, premultiplied clear so that Compose content shows through the texture, and so
        // that the adopted image blends the way ColorAlphaType.PREMUL promises.
        renderer.setClearColor(0x000000, 0);
        return renderer;
    })()"""
)

/**
 * Creates the render target descriptor for a texture of [width] x [height]. Its `viewport` is derived
 * from that size, which is what three.js renders with once the target is active.
 */
// language=js
internal fun createRenderTarget(three: ThreeModule, width: Int, height: Int): ThreeRenderTarget = js(
    """(new three.WebGLRenderTarget(width, height))"""
)

/** Builds the scene graph: a lit torus knot, entirely procedural, no external assets. */
// language=js
internal fun createKnotScene(three: ThreeModule): ThreeKnotScene = js(
    """(function() {
        const scene = new three.Scene();

        const camera = new three.PerspectiveCamera(42, 1.6, 0.1, 100);
        camera.position.set(0, 0, 4.2);

        const material = new three.MeshStandardMaterial({
            color: 0x66d9ff,
            roughness: 0.3,
            metalness: 0.6,
            transparent: true,
            opacity: 0.8,
            side: three.DoubleSide,
        });
        const geometry = new three.TorusKnotGeometry(0.85, 0.3, 220, 32, 2, 3);
        const knot = new three.Mesh(geometry, material);
        scene.add(knot);

        const keyLight = new three.DirectionalLight(0xffffff, 3);
        keyLight.position.set(2.5, 3.0, 4.0);
        scene.add(keyLight);

        const rimLight = new three.DirectionalLight(0xff5fa2, 2);
        rimLight.position.set(-3.0, -1.5, -2.0);
        scene.add(rimLight);

        scene.add(new three.AmbientLight(0x223355, 1));

        return {
            scene: scene,
            camera: camera,
            knot: knot,
            material: material,
            keyLight: keyLight,
        };
    })()"""
)

/** Disposes the geometries and materials [createKnotScene] allocated. */
// language=js
internal fun disposeKnotScene(knotScene: ThreeKnotScene): Unit = js(
    """(function() {
        knotScene.scene.traverse(function(object) {
            if (object.geometry) object.geometry.dispose();
            if (object.material) object.material.dispose();
        });
    })()"""
)

// A bundler may hand back either the ES module namespace or a CommonJS interop wrapper, so the
// namespace is normalized here rather than at every call site.
// language=js
private fun importThree(): Promise<JsAny?> = js("import('three').then(function(m) { return m.default || m; })")

private suspend fun Promise<JsAny?>.await(): JsAny? = suspendCancellableCoroutine { continuation ->
    then(
        onFulfilled = { value -> continuation.resume(value); null },
        onRejected = { error ->
            continuation.resumeWithException(
                IllegalStateException("import('three') failed: $error}")
            )
            null
        },
    )
}
