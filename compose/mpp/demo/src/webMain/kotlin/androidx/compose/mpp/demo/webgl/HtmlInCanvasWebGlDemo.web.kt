/*
 * Copyright 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

@file:OptIn(ExperimentalComposeUiApi::class)

package androidx.compose.mpp.demo.webgl

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Text
import androidx.compose.mpp.demo.Screen
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.webgl.WebGLRenderTarget
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.webgl.rememberWebGLRenderTarget
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import kotlin.math.roundToInt
import org.khronos.webgl.WebGLProgram
import org.khronos.webgl.WebGLRenderingContext
import org.khronos.webgl.WebGLRenderingContext.Companion.CLAMP_TO_EDGE
import org.khronos.webgl.WebGLRenderingContext.Companion.FRAGMENT_SHADER
import org.khronos.webgl.WebGLRenderingContext.Companion.LINEAR
import org.khronos.webgl.WebGLRenderingContext.Companion.TEXTURE_2D
import org.khronos.webgl.WebGLRenderingContext.Companion.TEXTURE_MAG_FILTER
import org.khronos.webgl.WebGLRenderingContext.Companion.TEXTURE_MIN_FILTER
import org.khronos.webgl.WebGLRenderingContext.Companion.TEXTURE_WRAP_S
import org.khronos.webgl.WebGLRenderingContext.Companion.TEXTURE_WRAP_T
import org.khronos.webgl.WebGLRenderingContext.Companion.TRIANGLE_STRIP
import org.khronos.webgl.WebGLRenderingContext.Companion.VERTEX_SHADER
import org.khronos.webgl.WebGLTexture
import kotlin.js.JsAny
import org.w3c.dom.HTMLDivElement
import org.w3c.dom.HTMLElement

val HtmlInCanvasWebGlScreen = Screen.Example("HTML in WebGL") { HtmlInCanvasWebGlDemo() }

@Composable
private fun HtmlInCanvasWebGlDemo() {
    var boundsPx by remember { mutableStateOf(IntSize(640, 360)) }
    var originPx by remember { mutableStateOf(Offset.Zero) }

    val target = rememberWebGLRenderTarget(boundsPx) ?: return
    val htmlRenderer = remember(target) { HtmlTextureRenderer(target) }
    val supported = remember(htmlRenderer) { htmlRenderer.initialize() }

    DisposableEffect(htmlRenderer, target) {
        onDispose {
            htmlRenderer.dispose()
            target.markGLStateStale()
        }
    }

    if (!supported) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("HTML-in-Canvas is not available in this browser.")
            Text("Try Chrome Canary with the canvas-draw-element flag enabled and the HTML-in-Canvas origin trial.")
        }
        return
    }

    val density = LocalDensity.current.density

    SideEffect {
        htmlRenderer.syncElementBox(
            widthCss = boundsPx.width / density,
            heightCss = boundsPx.height / density,
            leftCss = originPx.x / density,
            topCss = originPx.y / density,
        )
    }

    LaunchedEffect(htmlRenderer, target) {
        while (true) {
            withFrameNanos { frameTimeNanos ->
                htmlRenderer.renderFrame(frameTimeNanos)
            }
        }
    }

    var circleOffset by remember { mutableStateOf(Offset.Zero) }

    Box(modifier = Modifier
        .fillMaxSize()
        .pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                event.changes.fastForEach {
                    circleOffset = it.position
                }
            }
        }
    }) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Interactive HTML rendered into a WebGL texture")
            Box(Modifier.fillMaxWidth(0.8f).aspectRatio(640f / 360f)) {
                Image(
                    painter = target.painter,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier =
                        Modifier.fillMaxSize()
                            .onGloballyPositioned { coordinates ->
                                val bounds = coordinates.boundsInWindow()
                                boundsPx =
                                    IntSize(bounds.width.roundToInt(), bounds.height.roundToInt())
                                originPx = Offset(bounds.left, bounds.top)
                            },
                )
            }
        }

        Box(modifier = Modifier
            .graphicsLayer {
                translationX = circleOffset.x
                translationY = circleOffset.y
            }
            .clip(CircleShape)
            .size(100.dp)
            .background(Color.Gray.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center
        ) {
            Text("Compose")
        }
    }
}

private class HtmlTextureRenderer(private val target: WebGLRenderTarget) {
    private val gl = target.webGLContext
    private val canvas = target.htmlCanvas
    private val program = gl.createProgram(VERTEX_SHADER_SOURCE, FRAGMENT_SHADER_SOURCE)
    private val texture = gl.createTexture() ?: error("gl.createTexture() returned null")
    private var element: HTMLElement? = null

    fun initialize(): Boolean {
        val element = createInteractiveElement(canvas) ?: return false
        this.element = element
        gl.bindTexture(TEXTURE_2D, texture)
        gl.texParameteri(TEXTURE_2D, TEXTURE_MIN_FILTER, LINEAR)
        gl.texParameteri(TEXTURE_2D, TEXTURE_MAG_FILTER, LINEAR)
        gl.texParameteri(TEXTURE_2D, TEXTURE_WRAP_S, CLAMP_TO_EDGE)
        gl.texParameteri(TEXTURE_2D, TEXTURE_WRAP_T, CLAMP_TO_EDGE)
        gl.bindTexture(TEXTURE_2D, null)
        return true
    }

    fun syncElementBox(widthCss: Float, heightCss: Float, leftCss: Float, topCss: Float) {
        val element = element ?: return
        if (widthCss <= 0f || heightCss <= 0f) return
        syncElementBox(element, widthCss.toDouble(), heightCss.toDouble(), leftCss.toDouble(), topCss.toDouble())
    }

    fun renderFrame(frameTimeNanos: Long) {
        target.render { renderFrameInTarget(frameTimeNanos) }
    }

    private fun renderFrameInTarget(frameTimeNanos: Long) {
        val element = element ?: return
        if (!uploadElement(gl, texture, element, target.size.width, target.size.height)) return
        with(target) {
            webGLContext.viewport(0, 0, size.width, size.height)
            webGLContext.disable(WebGLRenderingContext.SCISSOR_TEST)
            webGLContext.clearColor(0f, 0f, 0f, 0f)
            webGLContext.clear(WebGLRenderingContext.COLOR_BUFFER_BIT)
            webGLContext.useProgram(program)
            webGLContext.activeTexture(WebGLRenderingContext.TEXTURE0)
            webGLContext.bindTexture(TEXTURE_2D, texture)
            webGLContext.uniform1i(gl.getUniformLocation(program, "source"), 0)
            webGLContext.drawArrays(TRIANGLE_STRIP, 0, 4)
            webGLContext.bindTexture(TEXTURE_2D, null)
        }
    }

    fun dispose() {
        element?.let(::removeElement)
        gl.deleteTexture(texture)
        gl.deleteProgram(program)
    }

    companion object {
        private val VERTEX_SHADER_SOURCE = """
            #version 300 es
            out vec2 uv;
            const vec2 p[4] = vec2[4](vec2(-1,-1), vec2(1,-1), vec2(-1,1), vec2(1,1));
            void main() { gl_Position = vec4(p[gl_VertexID], 0, 1); uv = vec2((p[gl_VertexID].x+1.)*.5, 1.-(p[gl_VertexID].y+1.)*.5); }
        """.trimIndent()
        private val FRAGMENT_SHADER_SOURCE = """
            #version 300 es
            precision mediump float;
            uniform sampler2D source;
            in vec2 uv;
            out vec4 color;
            void main() { color = texture(source, uv); }
        """.trimIndent()
    }
}

/** Returns `null` when the HTML-in-Canvas API is unavailable. */
private fun createInteractiveElement(canvas: org.w3c.dom.HTMLCanvasElement): HTMLDivElement? = js(
    """(function() {
        if (!('texElementImage2D' in WebGL2RenderingContext.prototype)) return null;
        // Opt canvas children into layout/hit-testing. Must be set before the child is appended.
        if ('layoutSubtree' in canvas) canvas.layoutSubtree = true;
        else canvas.setAttribute('layoutsubtree', '');
        const card = document.createElement('div');
        card.style.cssText = 'user-select:text;box-sizing:border-box;transform-origin:0 0;pointer-events:auto;padding:48px;background:linear-gradient(135deg,#182848,#4b6cb7);color:white;font:28px sans-serif;text-align:center;';
        card.innerHTML = '<strong>HTML in Canvas</strong><br><small>Real DOM text and controls</small><br><button>Click me</button><p></p>';
        const button = card.querySelector('button');
        const output = card.querySelector('p');
        let clicks = 0;
        button.addEventListener('click', function(event) {
            clicks++;
            output.textContent = '✅ The DOM button works: ' + clicks +
                (clicks === 1 ? ' click' : ' clicks');
        });
        canvas.appendChild(card);
        if (canvas.requestPaint) canvas.requestPaint();
        return card;
    })(canvas)"""
)

/**
 * Aligns the element's DOM box with the box it is drawn into, so that hit testing, focus and
 * accessibility (which all use the DOM location) match what the user sees. The element is a child
 * of the canvas, so it is laid out at the canvas' origin; a plain translation is enough as long as
 * the CSS size equals the destination size. For non-trivial draw transforms use
 * `canvas.getElementTransform(element, drawTransform)` instead.
 */
private fun syncElementBox(
    element: HTMLElement,
    widthCss: Double,
    heightCss: Double,
    leftCss: Double,
    topCss: Double,
): Unit = js(
    """(function() {
        element.style.width = widthCss + 'px';
        element.style.height = heightCss + 'px';
        element.style.transformOrigin = '0 0';
        element.style.transform = 'translate(' + leftCss + 'px, ' + topCss + 'px)';
    })()"""
)

private fun uploadElement(
    gl: WebGLRenderingContext,
    texture: WebGLTexture,
    element: HTMLElement,
    widthPx: Int,
    heightPx: Int,
): Boolean = js(
    """(function() {
        try {
            if (!gl.texElementImage2D) return false;
            gl.bindTexture(gl.TEXTURE_2D, texture);
            try {
                // Current API
                gl.texElementImage2D(gl.TEXTURE_2D, gl.RGBA8, element,
                                     { width: widthPx, height: heightPx });
            } catch (signatureError) {
                // Legacy Chrome builds
                gl.texElementImage2D(gl.TEXTURE_2D, 0, gl.RGBA, gl.RGBA, gl.UNSIGNED_BYTE, element);
            }
            return true;
        } catch (error) {
            return false;
        }
    })()"""
)

private fun removeElement(element: JsAny): Unit = js("element.remove()")

private fun WebGLRenderingContext.createProgram(vertex: String, fragment: String): WebGLProgram {
    val program = createProgram() ?: error("gl.createProgram() returned null")
    for ((type, source) in listOf(VERTEX_SHADER to vertex, FRAGMENT_SHADER to fragment)) {
        val shader = createShader(type) ?: error("gl.createShader() returned null")
        shaderSource(shader, source)
        compileShader(shader)
        check(getShaderInfoLog(shader).isNullOrBlank())
        attachShader(program, shader)
        deleteShader(shader)
    }
    linkProgram(program)
    check(getProgramInfoLog(program).isNullOrBlank())
    return program
}
