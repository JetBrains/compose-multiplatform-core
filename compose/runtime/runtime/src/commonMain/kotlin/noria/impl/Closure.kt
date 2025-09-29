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

package noria.impl


/*
* DO NOT INLINE THIS FUNCTION
* this function allows us to bypass arity check generated for each cast to lambda type
*/
@Suppress("UNCHECKED_CAST")
internal fun <T> uncheckedCast(any: Any?): T = any as T

class Closure(val closure: Array<out Any?>, val f: Function<*>) : Function0<Any?>,
  Function1<Any?, Any?>,
  Function2<Any?, Any?, Any?>,
  Function3<Any?, Any?, Any?, Any?>,
  Function4<Any?, Any?, Any?, Any?, Any?>,
  Function5<Any?, Any?, Any?, Any?, Any?, Any?>,
  Function6<Any?, Any?, Any?, Any?, Any?, Any?, Any?>,
  Function7<Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?> {
  override fun equals(other: Any?): Boolean {
    return other is Closure && f::class == other.f::class && closure contentEquals other.closure
  }

  override fun hashCode(): Int {
    return closure.contentHashCode()
  }

  override fun invoke(): Any? {
    return uncheckedCast<Function0<Any?>>(f).invoke()
  }

  override fun invoke(p1: Any?): Any? {
    return uncheckedCast<Function1<Any?, Any?>>(f).invoke(p1)
  }

  override fun invoke(p1: Any?, p2: Any?): Any? {
    return uncheckedCast<Function2<Any?, Any?, Any?>>(f).invoke(p1, p2)
  }

  override fun invoke(p1: Any?, p2: Any?, p3: Any?): Any? {
    return uncheckedCast<Function3<Any?, Any?, Any?, Any?>>(f).invoke(p1, p2, p3)
  }

  override fun invoke(p1: Any?, p2: Any?, p3: Any?, p4: Any?): Any? {
    return uncheckedCast<Function4<Any?, Any?, Any?, Any?, Any?>>(f).invoke(p1, p2, p3, p4)
  }

  override fun invoke(p1: Any?, p2: Any?, p3: Any?, p4: Any?, p5: Any?): Any? {
    return uncheckedCast<Function5<Any?, Any?, Any?, Any?, Any?, Any?>>(f).invoke(p1, p2, p3, p4, p5)
  }

  override fun invoke(p1: Any?, p2: Any?, p3: Any?, p4: Any?, p5: Any?, p6: Any?): Any? {
    return uncheckedCast<Function6<Any?, Any?, Any?, Any?, Any?, Any?, Any?>>(f).invoke(p1, p2, p3, p4, p5, p6)
  }

  override fun invoke(p1: Any?, p2: Any?, p3: Any?, p4: Any?, p5: Any?, p6: Any?, p7: Any?): Any? {
    return uncheckedCast<Function7<Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?>>(f).invoke(p1, p2, p3, p4, p5, p6, p7)
  }

  override fun toString(): String {
    return "Closure(${f::class}, ${closure.joinToString()})"
  }
}
