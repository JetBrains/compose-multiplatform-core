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

package androidx.wear.compose.remote.integration.demos.components

import androidx.compose.foundation.layout.Column
import androidx.compose.remote.creation.compose.capture.RememberRemoteDocumentInline
import androidx.compose.remote.creation.compose.layout.RemoteAlignment
import androidx.compose.remote.creation.compose.layout.RemoteBox
import androidx.compose.remote.creation.compose.layout.RemoteComposable
import androidx.compose.remote.creation.compose.modifier.RemoteModifier
import androidx.compose.remote.creation.compose.modifier.fillMaxWidth
import androidx.compose.remote.creation.compose.modifier.padding
import androidx.compose.remote.creation.compose.state.rdp
import androidx.compose.remote.player.compose.RemoteDocumentPlayer
import androidx.compose.remote.player.core.RemoteDocument
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.ListSubHeader
import androidx.wear.compose.material3.Text

@Composable
@Suppress("RestrictedApiAndroidX")
fun RemoteDemoItem(
    label: String,
    modifier: Modifier = Modifier,
    playerModifier: Modifier = Modifier,
    documentWidth: Int? = null,
    documentHeight: Int? = null,
    content: @Composable @RemoteComposable () -> Unit,
) {
    var documentState by remember { mutableStateOf<RemoteDocument?>(null) }

    Column(modifier = modifier) {
        ListSubHeader { Text(label) }

        @Suppress("COMPOSE_APPLIER_CALL_MISMATCH") // b/481422057
        RememberRemoteDocumentInline(
            onDocument = { doc ->
                println("Document generated: $doc")
                if (documentState == null) {
                    // Generate seems to get called again with a partial document
                    // Essentially re-recording but with existing state, so document is incomplete
                    documentState = RemoteDocument(doc)
                }
            },
            content = {
                RemoteBox(
                    modifier = RemoteModifier.fillMaxWidth().padding(horizontal = 8.rdp),
                    contentAlignment = RemoteAlignment.Center,
                    content = content,
                )
            },
        )

        if (documentState != null) {
            val windowInfo = LocalWindowInfo.current

            @Composable fun getDefaultHeight() = with(LocalDensity.current) { 50.dp.toPx() }.toInt()

            RemoteDocumentPlayer(
                document = documentState!!.document,
                documentWidth = documentWidth ?: windowInfo.containerSize.width,
                documentHeight = documentHeight ?: getDefaultHeight(),
                modifier = playerModifier,
                debugMode = 0,
                onNamedAction = { _, _, _ -> },
            )
        }
    }
}
