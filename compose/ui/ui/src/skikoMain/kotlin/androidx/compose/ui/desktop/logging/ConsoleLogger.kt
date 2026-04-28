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
package androidx.compose.ui.desktop.logging

import kotlin.reflect.KClass

class ConsoleLogger(private val name: String) : BaseLogger {
  override val isTraceEnabled: Boolean get() = false
  override val isDebugEnabled: Boolean get() = false
  override val isInfoEnabled: Boolean get() = true
  override val isWarnEnabled: Boolean get() = true
  override val isErrorEnabled: Boolean get() = true

  override fun trace(message: Any?) = log("TRACE", message, null)
  override fun debug(message: Any?) = log("DEBUG", message, null)
  override fun info(message: Any?) = log("INFO", message, null)
  override fun warn(message: Any?) = log("WARN", message, null)
  override fun error(message: Any?) = log("ERROR", message, null)

  override fun trace(t: Throwable?, message: Any?) = log("TRACE", message, t)
  override fun debug(t: Throwable?, message: Any?) = log("DEBUG", message, t)
  override fun info(t: Throwable?, message: Any?) = log("INFO", message, t)
  override fun warn(t: Throwable?, message: Any?) = log("WARN", message, t)
  override fun error(t: Throwable?, message: Any?) = log("ERROR", message, t)

  private fun log(level: String, message: Any?, t: Throwable?) {
    println("[$level] $name: $message")
    t?.printStackTrace()
  }
}

object ConsoleKLoggerFactory : KLoggerFactory {
  override fun logger(owner: KClass<*>): KLogger =
      KLogger(ConsoleLogger(owner.simpleName ?: "anonymous"))
  override fun logger(owner: Any): KLogger =
      KLogger(
          ConsoleLogger(
              owner::class.simpleName ?: "anonymous"
          )
      )
  override fun logger(name: String): KLogger =
      KLogger(ConsoleLogger(name))
}
