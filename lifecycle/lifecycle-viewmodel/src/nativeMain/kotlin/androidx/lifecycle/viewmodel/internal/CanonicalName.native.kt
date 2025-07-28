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

package androidx.kruth

import kotlin.reflect.KClass

<<<<<<<< HEAD:kruth/kruth/src/nativeMain/kotlin/androidx/kruth/KClassExt.native.kt
internal actual val KClass<*>.qName: String?
    get() = qualifiedName
========
internal actual val KClass<*>?.canonicalName: String?
    get() = this?.qualifiedName
>>>>>>>> v1.9.0+dev2718:lifecycle/lifecycle-viewmodel/src/nativeMain/kotlin/androidx/lifecycle/viewmodel/internal/CanonicalName.native.kt
