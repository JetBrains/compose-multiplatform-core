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

package androidx.compose.remote.player.core.platform

import androidx.annotation.RestrictTo
import java.io.InputStream
import java.net.URL

/** Implementation of BitmapLoader for Android, only supporting URL fetching. */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class AndroidBitmapLoader : BitmapLoader {
    override fun loadBitmap(url: String): InputStream {
        return URL(url).openStream()
    }
}
