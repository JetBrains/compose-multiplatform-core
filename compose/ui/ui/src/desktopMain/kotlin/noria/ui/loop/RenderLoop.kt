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

package noria.ui.loop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composer
import androidx.compose.runtime.InternalComposeApi
import androidx.compose.runtime.currentComposer
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.flow.Flow
import noria.CallbackInterceptorCompositionLocal
import noria.ClosureContext
import noria.NoriaContext

interface RenderLoop : CoroutineContext.Element {
    companion object : CoroutineContext.Key<RenderLoop> {
//    val logger = logger<RenderLoop>()
    }

    @JvmInline
    value class FrameInfo(val elapsedTimeNanos: Long)

    override val key: CoroutineContext.Key<*> get() = RenderLoop

    suspend fun stopAndJoin()

    val framesFlow: Flow<FrameInfo>
}
@Composable
fun NoriaContext.tryToInvalidateCurrentFrame(f: () -> Boolean = { true }) {
    tryToInvalidateCurrentFrame(currentComposer, f)
}

@OptIn(InternalComposeApi::class)
fun tryToInvalidateCurrentFrame(composer: Composer, f: () -> Boolean) {
    val interceptor = composer.currentCompositionLocalMap[CallbackInterceptorCompositionLocal]
    composer.recordSideEffect {
        interceptor.execute(f)
    }
}

@OptIn(InternalComposeApi::class)
@Composable
fun NoriaContext.onFrameCompletion(block: ClosureContext.(RenderLoop.FrameInfo) -> Unit) {
    val interceptor = CallbackInterceptorCompositionLocal.current
    currentComposer.recordSideEffect { interceptor.execute { block(RenderLoop.FrameInfo(0L)) } }
}
