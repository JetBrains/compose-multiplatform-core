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

package noria

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import kotlinx.coroutines.CoroutineDispatcher
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.Runnable
import noria.impl.EffectCoroutineContextCompositionLocal
import kotlin.coroutines.CoroutineContext

val CallbackInterceptorCompositionLocal = staticCompositionLocalOf<CallbackInterceptor> {
  CallbackInterceptor
}

interface CallbackInterceptor {
  companion object : CallbackInterceptor {
    override fun <T> execute(f: () -> T): T = f()
  }

  fun <T> execute(f: () -> T): T

  operator fun plus(rhs: CallbackInterceptor): CallbackInterceptor = let { lhs ->
    object : CallbackInterceptor {
      override fun <T> execute(f: () -> T): T =
        rhs.execute {
          lhs.execute {
            f()
          }
        }
    }
  }
}

@Composable
@OptIn(ExperimentalStdlibApi::class)
fun NoriaContext.withCallbackInterceptor(interceptor: CallbackInterceptor, body: @Composable NoriaContext.() -> Unit) {
  val currentInterceptor = CallbackInterceptorCompositionLocal.current
  val combinedCallbackInterceptor = remember { currentInterceptor + interceptor }
  val effectsCoroutineContext = EffectCoroutineContextCompositionLocal.current
  val combinedEffectCoroutineContext = remember {
    val outerDispatcher = requireNotNull(effectsCoroutineContext[CoroutineDispatcher]) {
      "A CoroutineDispatcher must be present on the CoroutineContext given via EffectCoroutineContextNoriaKey"
    }

    effectsCoroutineContext + object : CoroutineDispatcher() {
      override fun dispatch(context: CoroutineContext, block: Runnable) {
        outerDispatcher.dispatch(context, Runnable {
          interceptor.execute {
            block.run()
          }
        })
      }
    }
  }
  return CompositionLocalProvider(
    CallbackInterceptorCompositionLocal provides combinedCallbackInterceptor,
    EffectCoroutineContextCompositionLocal provides combinedEffectCoroutineContext
  ) {
    body()
  }
}