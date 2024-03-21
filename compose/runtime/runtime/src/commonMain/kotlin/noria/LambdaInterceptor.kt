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
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf

interface LambdaInterceptor {

  companion object : LambdaInterceptor {
    val compositionLocal = staticCompositionLocalOf<LambdaInterceptor> { LambdaInterceptor }

    override fun <T> execute(id: ID, invalidate: (reason: Any?) -> Unit, block: () -> T): T =
      block()

    override fun destroy(id: ID, f: () -> Unit) =
      f()
  }

  fun <T> execute(id: ID, invalidate: (reason: Any?) -> Unit, block: () -> T): T

  fun destroy(id: ID, f: () -> Unit)

  operator fun plus(rhs: LambdaInterceptor): LambdaInterceptor = let { lhs ->
    object : LambdaInterceptor {
      override fun <T> execute(id: ID, invalidate: (reason: Any?) -> Unit, block: () -> T): T =
        rhs.run {
          execute(id, invalidate, block = {
            lhs.run {
              execute(id, invalidate, block)
            }
          })
        }

      override fun destroy(id: ID, f: () -> Unit) {
        rhs.run {
          destroy(id) {
            lhs.run {
              destroy(id, f)
            }
          }
        }
      }
    }
  }
}

@Composable
fun NoriaContext.withLambdaInterceptor(lambdaInterceptor: LambdaInterceptor, body: @Composable NoriaContext.() -> Unit) {
  val currentInterceptor = LambdaInterceptor.compositionLocal.current
  val composition = remember { currentInterceptor + lambdaInterceptor }
  return CompositionLocalProvider(LambdaInterceptor.compositionLocal provides composition) {
    body()
  }
}