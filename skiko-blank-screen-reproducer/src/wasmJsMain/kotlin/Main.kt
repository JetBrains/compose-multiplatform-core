import kotlinx.browser.document
import kotlinx.browser.window
import org.jetbrains.skia.BackendRenderTarget
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.Color
import org.jetbrains.skia.ColorSpace
import org.jetbrains.skia.DirectContext
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Surface
import org.jetbrains.skia.SurfaceColorFormat
import org.jetbrains.skia.SurfaceOrigin
import org.jetbrains.skia.SurfaceProps
import org.jetbrains.skiko.SkiaLayer
import org.jetbrains.skiko.SkikoRenderDelegate
import org.jetbrains.skiko.wasm.onWasmReady
import org.w3c.dom.HTMLCanvasElement

/**
 * Pure-skiko reproducer for CMP-8615
 * "[Web]. Sometimes page stops rendering due to skiko error"
 * (WebGL: INVALID_OPERATION: drawElements: no valid shader program in use)
 *
 * Root cause hypothesis:
 * Two Skia DirectContexts end up talking to the SAME underlying WebGLRenderingContext.
 * Compose creates the second one on every resize: ComposeWindow.resize() calls
 * skiaLayer.attachTo(canvas) again, and SkiaLayer.attachTo creates a fresh
 * CanvasRenderer -> GL.createContext(canvas) (emscripten returns a new handle wrapping
 * the same WebGL context, since canvas.getContext returns the existing context) ->
 * new DirectContext.makeGL(). The previous DirectContext is orphaned and never closed.
 *
 * When the orphaned DirectContext is later destroyed (via skiko's FinalizationRegistry
 * at GC time), Skia's GrGLGpu destructor cleans up GL state on the shared context,
 * including glUseProgram(0). The live DirectContext's state cache still believes its
 * program is bound, so on the next frame with unchanged content it SKIPS glUseProgram
 * and calls drawElements with no program in use -> INVALID_OPERATION, and nothing is
 * drawn until some content change forces a program rebind. GC timing makes the bug
 * appear "random" in real apps.
 *
 * Two variants (pick via URL hash):
 *  - #close    (default) deterministic: create an "intruder" DirectContext on the same
 *               WebGL context, draw with it once, close() it. The destructor's
 *               glUseProgram(0) breaks the main renderer on the very same frame.
 *  - #reattach faithful to Compose: periodically re-attach the SkiaLayer to the canvas
 *               (exactly what ComposeWindow.resize() does) and provoke GC so the
 *               orphaned DirectContext gets finalized (run Chrome with
 *               --js-flags=--expose-gc to make it deterministic).
 */

const val GL_RGBA8 = 0x8058

fun main() {
    onWasmReady {
        val variant = window.location.hash.removePrefix("#").ifEmpty { "close" }
        println("Reproducer variant: $variant")
        document.getElementById("desc")?.textContent = "CMP-8615 pure-skiko reproducer, variant: $variant"

        val canvas = document.getElementById("c") as HTMLCanvasElement
        val layer = SkiaLayer()
        var frame = 0
        val paint = Paint().apply { color = Color.makeRGB(30, 100, 200) }

        layer.renderDelegate = object : SkikoRenderDelegate {
            override fun onRender(skCanvas: Canvas, width: Int, height: Int, nanoTime: Long) {
                frame++
                // Exactly ONE draw op with unchanged state per frame, so Skia reuses the
                // same cached GrGLProgram and skips glUseProgram on subsequent frames.
                // (A circle, not a rect: an axis-aligned rect fill can be optimized into
                // scissored clear, which doesn't need a shader program at all.)
                skCanvas.drawCircle(width / 2f, height / 2f, 100f, paint)

                if (variant == "close" && frame == 60) {
                    println("frame $frame: creating and closing an intruder DirectContext on the same WebGL context")
                    intruderDrawAndClose(width, height)
                    println("intruder closed; expect 'no valid shader program in use' from now on")
                }
                layer.needRender() // keep the render loop going
            }
        }
        layer.attachTo(canvas)
        layer.needRender()

        if (variant == "reattach") {
            var flip = 0
            setInterval({
                // This block mirrors ComposeWindow.resize() (ComposeWindowInternal.web.kt):
                layer.needRender()              // a rAF may already be pending on the OLD renderer
                flip++
                canvas.width = 800 + (flip % 2) * 16
                canvas.height = 600
                layer.attachTo(canvas)          // new emscripten GL handle + new DirectContext,
                                                // old DirectContext is orphaned, never closed
                layer.needRender()
                println("re-attach #$flip done, forcing GC")
                forceGc()                       // FinalizationRegistry -> ~GrGLGpu -> glUseProgram(0)
            }, 500)
        }
    }
}

/**
 * Simulates the effect of the orphaned DirectContext being finalized, but deterministically:
 * a second DirectContext on the same (current) WebGL context draws once - so its GrGLGpu has
 * a non-zero bound-program cache - and is then closed. GrGLGpu's destructor unbinds the
 * program (glUseProgram(0)) on the shared WebGL context, behind the back of the SkiaLayer's
 * own DirectContext, whose state cache still says its program is bound.
 */
private fun intruderDrawAndClose(width: Int, height: Int) {
    val ctx = DirectContext.makeGL()
    val rt = BackendRenderTarget.makeGL(width, height, 1, 8, 0, GL_RGBA8)
    val surface = Surface.makeFromBackendRenderTarget(
        ctx, rt, SurfaceOrigin.BOTTOM_LEFT, SurfaceColorFormat.RGBA_8888, ColorSpace.sRGB, SurfaceProps()
    ) ?: error("Cannot create intruder surface")
    surface.canvas.drawCircle(60f, 60f, 40f, Paint().apply { color = Color.makeRGB(200, 30, 30) })
    surface.flushAndSubmit()
    surface.close()
    rt.close()
    ctx.close()
}

private fun setInterval(callback: () -> Unit, ms: Int): Unit =
    js("{ setInterval(callback, ms); }")

private fun forceGc(): Unit =
    js("{ if (globalThis.gc) { globalThis.gc(); } else { console.log('globalThis.gc not exposed; run Chrome with --js-flags=--expose-gc, or wait for natural GC'); } }")
