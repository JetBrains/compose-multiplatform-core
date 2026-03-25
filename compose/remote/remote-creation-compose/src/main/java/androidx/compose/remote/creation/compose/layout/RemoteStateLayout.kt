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
@file:RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)

package androidx.compose.remote.creation.compose.layout

import androidx.annotation.RestrictTo
import androidx.compose.foundation.layout.Box
import androidx.compose.remote.creation.compose.modifier.RemoteModifier
import androidx.compose.remote.creation.compose.modifier.toComposeUiLayout
import androidx.compose.remote.creation.compose.modifier.toRecordingModifier
import androidx.compose.remote.creation.compose.state.RemoteEnum
import androidx.compose.remote.creation.compose.state.RemoteInt
import androidx.compose.remote.creation.compose.state.rememberMutableRemoteEnum
import androidx.compose.remote.creation.compose.state.rememberMutableRemoteInt
import androidx.compose.remote.creation.compose.v2.RemoteComposeApplierV2
import androidx.compose.remote.creation.compose.v2.StateLayoutV2
import androidx.compose.runtime.Composable
import androidx.compose.runtime.currentComposer
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.DrawModifier
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.util.fastForEach
import kotlin.enums.EnumEntries
import kotlin.enums.enumEntries

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class RemoteStateMachine<T>
internal constructor(public val currentState: RemoteInt, public val states: List<T>) {

    public fun size(): Int {
        return states.size
    }
}

@RemoteComposable
@Composable
public fun rememberStateMachine(
    currentState: RemoteInt = rememberMutableRemoteInt(0),
    vararg states: Int,
): RemoteStateMachine<Int> {
    val currentState = rememberMutableRemoteInt(0)
    return remember { RemoteStateMachine(currentState, states.sorted()) }
}

@RemoteComposable
@Composable
public inline fun <reified T : Enum<T>> rememberStateMachine(
    currentState: RemoteEnum<T> = rememberMutableRemoteEnum(enumEntries<T>().first())
): RemoteStateMachine<T> {
    return rememberStateMachine(currentState, enumEntries())
}

@RemoteComposable
@Composable
public fun <T : Enum<T>> rememberStateMachine(
    currentState: RemoteEnum<T>,
    enumEntries: EnumEntries<T>,
): RemoteStateMachine<T> {
    return remember<RemoteStateMachine<T>> {
        RemoteStateMachine(currentState.intValue, enumEntries)
    }
}

/** Utility modifier to record the layout information */
internal class RemoteComposeStateLayoutModifier(
    public var modifier: RemoteModifier,
    public var currentState: RemoteInt,
) : DrawModifier {
    override fun ContentDrawScope.draw() {
        drawIntoRemoteCanvas { canvas ->
            canvas.document.startStateLayout(
                canvas.toRecordingModifier(modifier),
                with(canvas) { currentState.id },
            )
            this@draw.drawContent()
            canvas.document.endStateLayout()
        }
    }
}

@RemoteComposable
@Composable
public inline fun <reified T : Enum<T>> RemoteStateLayout(
    state: RemoteEnum<T>,
    modifier: RemoteModifier = RemoteModifier,
    noinline content: @Composable (T) -> Unit,
) {
    RemoteStateLayout(
        stateMachine = rememberStateMachine(state),
        modifier = modifier,
        content = content,
    )
}

@RemoteComposable
@Composable
public fun <T> RemoteStateLayout(
    stateMachine: RemoteStateMachine<T>,
    modifier: RemoteModifier = RemoteModifier,
    content: @Composable (T) -> Unit,
) {
    if (currentComposer.applier is RemoteComposeApplierV2) {
        StateLayoutV2(stateMachine, modifier, content)
        return
    }
    @Suppress("COMPOSE_APPLIER_CALL_MISMATCH") // b/481422057
    Box(
        RemoteComposeStateLayoutModifier(modifier, stateMachine.currentState)
            .then(modifier.toComposeUiLayout())
    ) {
        stateMachine.states.fastForEach { state -> content(state) }
    }
}
