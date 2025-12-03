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

import android.content.Context
import androidx.annotation.RestrictTo
import androidx.compose.remote.creation.CreationDisplayInfo
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.wear.composable.WearWidgetContainer
import androidx.glance.wear.parcel.WearWidgetCapture

/**
 * Describes the contents of a Widget with Remote Compose.
 *
 * The content provided will be captured into a Remote Compose document.
 *
 * @property content The RemoteComposable corresponding to contents of the widget.
 */
// TODO: Add @RemoteComposable annotation to content parameter once it's public.
public class WearWidgetDocument(public val content: @Composable () -> Unit) : WearWidgetData {

    @Suppress("RestrictedApiAndroidX")
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    override suspend fun captureRawContent(
        context: Context,
        params: WearWidgetParams,
    ): WearWidgetRawContent {
        return WearWidgetCapture.capture(
            context,
            CreationDisplayInfo(
                params.widthDp.dpToPx(context),
                params.heightDp.dpToPx(context),
                context.resources.displayMetrics.density,
            ),
        ) {
            WearWidgetContainer(
                horizontalPadding = params.horizontalPaddingDp.dp,
                verticalPadding = params.verticalPaddingDp.dp,
                cornerRadius = params.cornerRadiusDp.dp,
                content = content,
            )
        }
    }
}

private fun Float.dpToPx(context: Context): Int =
    (this * context.resources.displayMetrics.density).toInt()
