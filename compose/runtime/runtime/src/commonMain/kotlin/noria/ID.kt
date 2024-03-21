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
import androidx.compose.runtime.remember

@kotlin.jvm.JvmInline
value class ID(val id: Int) {
  companion object {
    val NULL = ID(-1)
    private var nextId: Int = 0
    fun nextIdDoNotUse(): ID {
      return ID(nextId++)
    }
  }
}

/*
* Allocates a stable positional ID
* */
@Deprecated("Use Compose API")
@Composable
fun NoriaContext.ID(): ID {
  return remember { ID.nextIdDoNotUse() }
}

/*
* Allocates a stable positional integer
* */
@Deprecated("Use Compose API")
@Composable
fun NoriaContext.positionalId(): Int {
  return ID().id
}
