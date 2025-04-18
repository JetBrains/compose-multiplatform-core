/*
 * Copyright 2024 The Android Open Source Project
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

package androidx.xr.compose.testing

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.View
import androidx.annotation.RestrictTo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.xr.compose.platform.LocalHasXrSpatialFeature
import androidx.xr.compose.platform.LocalSession
import androidx.xr.runtime.internal.ActivitySpace
import androidx.xr.runtime.internal.Entity
import androidx.xr.runtime.internal.HeadActivityPose
import androidx.xr.runtime.internal.JxrPlatformAdapter
import androidx.xr.runtime.internal.PanelEntity
import androidx.xr.runtime.internal.PerceptionSpaceActivityPose
import androidx.xr.runtime.internal.PixelDimensions
import androidx.xr.runtime.internal.SpatialEnvironment
import androidx.xr.runtime.math.Pose
import androidx.xr.scenecore.Session
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

/**
 * A Test environment composable wrapper to support testing elevated components locally.
 *
 * @param isXrEnabled Whether to enable XR.
 * @param isFullSpace Whether to enable full space mode.
 * @param runtime The [JxrPlatformAdapter] to use for the [Session].
 * @param content The content block containing the compose content to be tested.
 */
@Composable
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
public fun TestSetup(
    isXrEnabled: Boolean = true,
    isFullSpace: Boolean = true,
    runtime: JxrPlatformAdapter =
        createFakeRuntime(LocalContext.current.getActivity() as SubspaceTestingActivity),
    content: @Composable Session.() -> Unit,
) {
    val activity = LocalContext.current.getActivity() as SubspaceTestingActivity
    val session = remember {
        if (isXrEnabled) {
            createFakeSession(activity, runtime).apply {
                if (isFullSpace) {
                    spatialEnvironment.requestFullSpaceMode()
                } else {
                    spatialEnvironment.requestHomeSpaceMode()
                }
            }
        } else {
            createNonXrSession(activity)
        }
    }
    CompositionLocalProvider(
        LocalSession provides session,
        LocalHasXrSpatialFeature provides isXrEnabled,
    ) {
        session.content()
    }
}

private fun createNonXrSession(activity: Activity): Session {
    return Session.create(
        activity,
        mock<JxrPlatformAdapter> {
            on { spatialEnvironment } doReturn mock<SpatialEnvironment>()
            on { activitySpace } doReturn
                mock<ActivitySpace>(defaultAnswer = { throw UnsupportedOperationException() })
            on { headActivityPose } doReturn mock<HeadActivityPose>()
            on { perceptionSpaceActivityPose } doReturn
                mock<PerceptionSpaceActivityPose>(
                    defaultAnswer = { throw UnsupportedOperationException() }
                )
            on { mainPanelEntity } doReturn mock<PanelEntity>()
            on { requestHomeSpaceMode() } doAnswer { throw UnsupportedOperationException() }
            on { requestFullSpaceMode() } doAnswer { throw UnsupportedOperationException() }
            on { createActivityPanelEntity(any(), any(), any(), any(), any()) } doAnswer
                {
                    throw UnsupportedOperationException()
                }
            on { createAnchorEntity(any(), any(), any(), any()) } doAnswer
                {
                    throw UnsupportedOperationException()
                }
            on { createEntity(any(), any(), any()) } doAnswer
                {
                    throw UnsupportedOperationException()
                }
            on { createGltfEntity(any(), any(), any()) } doAnswer
                {
                    throw UnsupportedOperationException()
                }
            on {
                createPanelEntity(
                    any<Context>(),
                    any<Pose>(),
                    any<View>(),
                    any<PixelDimensions>(),
                    any<String>(),
                    any<Entity>(),
                )
            } doAnswer { throw UnsupportedOperationException() }
            on { createLoggingEntity(any()) } doAnswer { throw UnsupportedOperationException() }
        },
    )
}

private tailrec fun Context.getActivity(): Activity =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.getActivity()
        else -> error("Unexpected Context type when trying to resolve the context's Activity.")
    }
