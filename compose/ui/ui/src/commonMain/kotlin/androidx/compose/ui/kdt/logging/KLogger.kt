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
package androidx.compose.ui.kdt.logging

class KLogger(baseLogger: BaseLogger) : BaseLogger by baseLogger {
  inline fun trace(msg: () -> Any?) {
    if (isTraceEnabled) trace(msg())
  }

  inline fun debug(msg: () -> Any?) {
    if (isDebugEnabled) debug(msg())
  }

  inline fun info(msg: () -> Any?) {
    if (isInfoEnabled) info(msg())
  }

  inline fun warn(msg: () -> Any?) {
    if (isWarnEnabled) warn(msg())
  }

  inline fun error(msg: () -> Any?) {
    if (isErrorEnabled) error(msg())
  }

  inline fun trace(t: Throwable?, msg: () -> Any?) {
    if (isTraceEnabled) trace(t, msg())
  }

  inline fun debug(t: Throwable?, msg: () -> Any?) {
    if (isDebugEnabled) debug(t, msg())
  }

  inline fun info(t: Throwable?, msg: () -> Any?) {
    if (isInfoEnabled) info(t, msg())
  }

  inline fun warn(t: Throwable?, msg: () -> Any?) {
    if (isWarnEnabled) warn(t, msg())
  }

  inline fun error(t: Throwable?, msg: () -> Any?) {
    if (isErrorEnabled) error(t, msg())
  }
}