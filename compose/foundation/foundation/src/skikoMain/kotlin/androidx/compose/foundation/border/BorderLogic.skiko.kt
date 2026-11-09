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

<<<<<<<< HEAD:xr/compose/material3/material3/src/main/java/androidx/xr/compose/material3/ExperimentalMaterial3XrApi.kt
package androidx.xr.compose.material3

@RequiresOptIn(
    "This material XR API is experimental and is likely to change or to be removed in the future."
)
@Retention(AnnotationRetention.BINARY)
public annotation class ExperimentalMaterial3XrApi
========
package androidx.compose.foundation.border

internal actual fun shouldUseGraphicsLayerForGenericBorder(): Boolean = false
>>>>>>>> origin/release/1.13:compose/foundation/foundation/src/skikoMain/kotlin/androidx/compose/foundation/border/BorderLogic.skiko.kt
