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
import android.view.Display
import androidx.activity.ComponentActivity
import androidx.annotation.RestrictTo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.xr.compose.platform.LocalSession
import androidx.xr.compose.platform.LocalSpatialCapabilities
import androidx.xr.compose.platform.LocalSpatialConfiguration
import androidx.xr.compose.platform.SceneManager
import androidx.xr.compose.subspace.SubspaceComposable
import androidx.xr.runtime.Session
import androidx.xr.runtime.internal.JxrPlatformAdapter
import androidx.xr.runtime.testing.FakeRuntimeFactory
import androidx.xr.scenecore.impl.JxrPlatformAdapterAxr
import androidx.xr.scenecore.impl.extensions.XrExtensionsProvider
import androidx.xr.scenecore.impl.impress.FakeImpressApiImpl
import androidx.xr.scenecore.impl.perception.PerceptionLibrary
import androidx.xr.scenecore.testing.FakeScheduledExecutorService
import com.android.extensions.xr.ShadowConfig
import com.android.extensions.xr.XrExtensions
import com.google.androidxr.splitengine.SplitEngineSubspaceManager
import com.google.ar.imp.view.splitengine.ImpSplitEngineRenderer
import org.mockito.Mockito.mock
import org.robolectric.shadows.ShadowDisplay

/**
 * Custom test class that should be used for testing [SubspaceComposable] content.
 *
 * TODO(b/436320533): Deprecate SubspaceTestingActivity in favor of using
 *   setContentWithCompatibilityForXr
 */
@Suppress("ForbiddenSuperClass")
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
public class SubspaceTestingActivity : ComponentActivity() {
    public val extensions: XrExtensions = XrExtensionsProvider.getXrExtensions()!!
    @Suppress("MutableBareField") public var session: Session? = null

    /** Throws an exception by default under test; return Robolectric Display impl instead. */
    override fun getDisplay(): Display = ShadowDisplay.getDefaultDisplay()

    override fun onStart() {
        SceneManager.start()
        super.onStart()

        ShadowConfig.extract(XrExtensionsProvider.getXrExtensions()!!.config!!)
            .setDefaultDpPerMeter(1151.856f)
    }

    override fun onDestroy() {
        SceneManager.stop()
        super.onDestroy()
    }
}

/**
 * Analog to [AndroidComposeTestRule.setContent] for testing content in XR. This creates the minimum
 * environment necessary for testing content in XR.
 *
 * If an XR [Session] is not already created and assigned using [AndroidComposeTestRule.session],
 * then a test XR [Session] will be created.
 *
 * @param content The content to render to the test [Activity].
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
public fun AndroidComposeTestRule<*, *>.setContentWithCompatibilityForXr(
    content: @Composable () -> Unit
) {
    SceneManager.start()
    ShadowConfig.extract(XrExtensionsProvider.getXrExtensions()!!.config!!)
        .setDefaultDpPerMeter(1151.856f)

    if (session == null) {
        session = createFakeSession(activity)
    }

    activity.lifecycle.addObserver(
        object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                SceneManager.stop()
                activity.session = null
                owner.lifecycle.removeObserver(this)
                super.onDestroy(owner)
            }
        }
    )

    setContent {
        CompositionLocalProvider(
            LocalSession provides session!!,
            LocalSpatialConfiguration provides TestSessionSpatialConfiguration(session!!),
            LocalSpatialCapabilities provides TestSessionSpatialCapabilities(session!!),
            content = content,
        )
    }
}

/**
 * The XR [Session] for the current [androidx.compose.ui.test.junit4.AndroidComposeTestRule].
 *
 * This will be null until the value is set or [setContentWithCompatibilityForXr] is called, after
 * which the value will be non-null and return the current [Session]. Setting this value after
 * calling [setContentWithCompatibilityForXr] will not change the Session that is used for that
 * content block. Setting the value to null will indicate that the default test Session should be
 * used.
 */
public var AndroidComposeTestRule<*, *>.session: Session?
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX) get() = activity.session
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
    set(value) {
        activity.session = value
    }

private var Activity.session: Session?
    get() = window.decorView.getTag(R.id.xr_session) as? Session
    set(value) {
        window.decorView.setTag(R.id.xr_session, value)
    }

/**
 * Finds a semantics node (in the Subspace hierarchy) that matches the given condition.
 *
 * This only locates nodes in the Subspace hierarchy and will not include nodes from 2D compose
 * contexts. For example, it will return SpatialPanel, SpatialRow, or SpatialColumn nodes, but it
 * will not return Row, Column, or Text nodes. For 2D nodes, use [AndroidComposeTestRule.onNode].
 *
 * Any subsequent operation on its result will expect exactly one element found (unless
 * [SubspaceSemanticsNodeInteraction.assertDoesNotExist] is used) and will throw an [AssertionError]
 * if none or more than one element is found.
 *
 * For usage patterns and semantics concepts see [SubspaceSemanticsNodeInteraction]
 *
 * @param matcher Matcher used for filtering
 * @see onAllSubspaceNodes to work with multiple elements
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
public fun AndroidComposeTestRule<*, *>.onSubspaceNode(
    matcher: SubspaceSemanticsMatcher
): SubspaceSemanticsNodeInteraction =
    SubspaceSemanticsNodeInteraction(SubspaceTestContext(this), matcher)

/**
 * Finds all semantics nodes (in the Subspace hierarchy) that match the given condition.
 *
 * This only locates nodes in the Subspace hierarchy and will not include nodes from 2D compose
 * contexts. For example, it will return SpatialPanel, SpatialRow, or SpatialColumn nodes, but it
 * will not return Row, Column, or Text nodes. For 2D nodes, use [AndroidComposeTestRule.onNode].
 *
 * If you are working with elements that are not supposed to occur multiple times use
 * [onSubspaceNode] instead.
 *
 * For usage patterns and semantics concepts see [SubspaceSemanticsNodeInteraction]
 *
 * @param matcher Matcher used for filtering.
 * @see onSubspaceNode
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
public fun AndroidComposeTestRule<*, *>.onAllSubspaceNodes(
    matcher: SubspaceSemanticsMatcher
): SubspaceSemanticsNodeInteractionCollection =
    SubspaceSemanticsNodeInteractionCollection(SubspaceTestContext(this), matcher)

/**
 * Finds a semantics node (in the Subspace hierarchy) identified by the given tag.
 *
 * This only locates nodes in the Subspace hierarchy and will not include nodes from 2D compose
 * contexts. For example, it will return SpatialPanel, SpatialRow, or SpatialColumn nodes, but it
 * will not return Row, Column, or Text nodes. For 2D nodes, use [AndroidComposeTestRule.onNode].
 *
 * For usage patterns and semantics concepts see [SubspaceSemanticsNodeInteraction]
 *
 * @param testTag The tag to search for. Looks for an exact match only.
 * @see onSubspaceNode for more information.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
public fun AndroidComposeTestRule<*, *>.onSubspaceNodeWithTag(
    testTag: String
): SubspaceSemanticsNodeInteraction = onSubspaceNode(hasTestTag(testTag))

/**
 * Finds all semantics nodes (in the Subspace hierarchy) identified by the given tag.
 *
 * This only locates nodes in the Subspace hierarchy and will not include nodes from 2D compose
 * contexts. For example, it will return SpatialPanel, SpatialRow, or SpatialColumn nodes, but it
 * will not return Row, Column, or Text nodes. For 2D nodes, use [AndroidComposeTestRule.onNode].
 *
 * For usage patterns and semantics concepts see [SubspaceSemanticsNodeInteraction]
 *
 * @param testTag The tag to search for. Looks for an exact matches only.
 * @see onSubspaceNode for more information.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
public fun AndroidComposeTestRule<*, *>.onAllSubspaceNodesWithTag(
    testTag: String
): SubspaceSemanticsNodeInteractionCollection = onAllSubspaceNodes(hasTestTag(testTag))

/**
 * Create a fake [Session] for testing.
 *
 * A convenience method that creates a fake [Session] for testing. If runtime is not provided, a
 * fake [JxrPlatformAdapter] will be created by default.
 *
 * @param activity The [SubspaceTestingActivity] to use for the [Session].
 * @param runtime The [JxrPlatformAdapter] to use for the [Session].
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
public fun createFakeSession(
    activity: Activity,
    runtime: JxrPlatformAdapter = createFakeRuntime(activity),
): Session =
    Session(
        activity,
        FakeRuntimeFactory().createRuntime(activity).apply { lifecycleManager.create() },
        runtime,
    )

/**
 * Create a fake [JxrPlatformAdapter] for testing.
 *
 * A convenience method that creates a fake [JxrPlatformAdapter] for testing.
 *
 * @param activity The [Activity] to use for the [JxrPlatformAdapter].
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
public fun createFakeRuntime(activity: Activity): JxrPlatformAdapter =
    // FakeJxrPlatformAdapterFactory().createPlatformAdapter(activity)
    JxrPlatformAdapterAxr.create(
        /* activity = */ activity,
        /* executor = */ FakeScheduledExecutorService(),
        /* extensions = */ XrExtensionsProvider.getXrExtensions()!!,
        /* impressApi = */ FakeImpressApiImpl(),
        /* perceptionLibrary = */ PerceptionLibrary(),
        /* splitEngineSubspaceManager = */ mock(SplitEngineSubspaceManager::class.java),
        /* splitEngineRenderer = */ mock(ImpSplitEngineRenderer::class.java),
    )
