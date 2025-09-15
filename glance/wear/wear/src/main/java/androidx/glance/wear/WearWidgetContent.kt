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

package androidx.glance.wear

import androidx.glance.wear.data.WearWidgetContentData

/** Describes the contents of a Widget. */
// TODO: change content to be of type `@RemoteComposable @Composable () -> Unit` once the dependency
//  is available.
public class WearWidgetContent(private val content: ByteArray) {

    internal fun toData(): WearWidgetContentData {
        return WearWidgetContentData().apply { payload = content }
    }
}
