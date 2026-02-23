/*
 * Copyright 2023 The Android Open Source Project
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

// copied from kotlin js, they are not available for Float for some reason.
// https://github.com/JetBrains/kotlin/blob/284e9b4041bd815b5b1b489070bbb1f1db6de35c/libraries/stdlib/js/src/kotlin/math.kt#L492
internal actual fun Float.nextUp(): Float =
    when {
        this.isNaN() || this == Float.POSITIVE_INFINITY -> this
        this == 0f -> Float.MIN_VALUE
        else -> Float.fromBits(this.toRawBits() + if (this > 0) 1 else -1)
    }

internal actual fun Float.nextDown(): Float =
    when {
        this.isNaN() || this == Float.NEGATIVE_INFINITY -> this
        this == 0f -> -Float.MIN_VALUE
        else -> Float.fromBits(this.toRawBits() + if (this > 0) -1 else 1)
    }
