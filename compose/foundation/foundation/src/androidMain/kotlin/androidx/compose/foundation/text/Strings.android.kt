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

package androidx.compose.foundation.text

import android.content.res.Resources
import android.os.Build
import androidx.compose.foundation.R
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext

@Immutable
@JvmInline
internal actual value class Strings actual constructor(actual val value: Int) {
    actual companion object {
        actual val Cut: Strings get() = Strings(android.R.string.cut)
        actual val Copy: Strings get() = Strings(android.R.string.copy)
        actual val Paste: Strings get() = Strings(android.R.string.paste)
        actual val SelectAll: Strings get() = Strings(android.R.string.selectAll)
        actual val Autofill: Strings get() = Strings(
            if (Build.VERSION.SDK_INT <= 26) {
                R.string.autofill
            } else {
                android.R.string.autofill
            }
        )
    }
}

@Composable
@ReadOnlyComposable
internal actual fun getString(string: Strings): String {
    LocalConfiguration.current
    val resources = LocalContext.current.resources
    return resources.getString(string.value)
}

@Immutable
@JvmInline
internal actual value class Icons actual constructor(actual val value: Int) {
    actual companion object {
        actual val ActionModeCutDrawable: Icons
            get() = Icons(android.R.attr.actionModeCutDrawable)
        actual val ActionModeCopyDrawable: Icons
            get() = Icons(android.R.attr.actionModeCopyDrawable)
        actual val ActionModePasteDrawable: Icons
            get() = Icons(android.R.attr.actionModePasteDrawable)
        actual val ActionModeSelectAllDrawable: Icons
            get() = Icons(android.R.attr.actionModeSelectAllDrawable)
        actual val ID_NULL: Icons
            get() = Icons(Resources.ID_NULL)
    }
}