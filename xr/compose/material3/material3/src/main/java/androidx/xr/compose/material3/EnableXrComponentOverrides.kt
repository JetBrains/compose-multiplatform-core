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

package androidx.xr.compose.material3

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ComponentOverrideApi
import androidx.compose.material3.LocalBasicAlertDialogOverride
import androidx.compose.material3.LocalNavigationBarOverride
import androidx.compose.material3.LocalNavigationRailOverride
import androidx.compose.material3.LocalSingleRowTopAppBarOverride
import androidx.compose.material3.LocalTwoRowsTopAppBarOverride
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveComponentOverrideApi
import androidx.compose.material3.adaptive.layout.LocalAnimatedPaneOverride
import androidx.compose.material3.adaptive.layout.LocalThreePaneScaffoldOverride
import androidx.compose.material3.adaptive.navigationsuite.LocalNavigationSuiteScaffoldOverride
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidedValue
import androidx.xr.compose.platform.LocalSpatialCapabilities

/**
 * Clients can wrap their Compose hierarchy in this function to dynamically enable XR components
 * when in the proper environment.
 *
 * The [overrideEnabler] param determines whether each component will use an XR version.
 */
@ExperimentalMaterial3XrApi
@OptIn(
    ExperimentalMaterial3ComponentOverrideApi::class,
    ExperimentalMaterial3AdaptiveComponentOverrideApi::class,
    ExperimentalMaterial3Api::class
)
@Composable
public fun EnableXrComponentOverrides(
    overrideEnabler: XrComponentOverrideEnabler = DefaultXrComponentOverrideEnabler,
    content: @Composable () -> Unit,
) {
    val context = XrComponentOverrideEnablerContextImpl

    // Override CompositionLocals for all ComponentOverrides, as specified by the provided enabler.
    val componentOverrides =
        buildList<ProvidedValue<*>> {
            with(overrideEnabler) {
                val shouldOverrideNavigationSuiteScaffold =
                    context.shouldOverrideComponent(XrComponentOverride.NavigationSuiteScaffold)
                if (shouldOverrideNavigationSuiteScaffold) {
                    add(
                        LocalNavigationSuiteScaffoldOverride provides
                            XrNavigationSuiteScaffoldOverride
                    )
                }
                // Automatically enable NavBar and NavRail when NavSuiteScaffold is enabled
                if (
                    shouldOverrideNavigationSuiteScaffold ||
                        context.shouldOverrideComponent(XrComponentOverride.NavigationRail)
                ) {
                    add(LocalNavigationRailOverride provides XrNavigationRailOverride)
                }
                if (
                    shouldOverrideNavigationSuiteScaffold ||
                        context.shouldOverrideComponent(XrComponentOverride.NavigationBar)
                ) {
                    add(LocalNavigationBarOverride provides XrNavigationBarOverride)
                }
                if (context.shouldOverrideComponent(XrComponentOverride.ThreePaneScaffold)) {
                    add(LocalThreePaneScaffoldOverride provides XrThreePaneScaffoldOverride)
                    add(LocalAnimatedPaneOverride provides XrAnimatedPaneOverride)
                }

                if (context.shouldOverrideComponent(XrComponentOverride.SingleRowTopAppBar)) {
                    add(LocalSingleRowTopAppBarOverride provides XrSingleRowTopAppBarOverride)
                }
                if (context.shouldOverrideComponent(XrComponentOverride.TwoRowsTopAppBar)) {
                    add(LocalTwoRowsTopAppBarOverride provides XrTwoRowsTopAppBarOverride)
                }

                if (context.shouldOverrideComponent(XrComponentOverride.BasicAlertDialog)) {
                    add(LocalBasicAlertDialogOverride provides XrBasicAlertDialogOverride)
                }
            }
        }
    CompositionLocalProvider(values = componentOverrides.toTypedArray(), content = content)
}

/** Interface that a client can provide to enable/disable XR overrides on a per-component basis. */
@ExperimentalMaterial3XrApi
public interface XrComponentOverrideEnabler {
    /**
     * Used to determine whether the XR version of a given component should be used.
     *
     * @param component the component that may or may not use the XR version
     * @return whether the XR version of this component should be used
     */
    @Composable
    @ExperimentalMaterial3XrApi
    public fun XrComponentOverrideEnablerContext.shouldOverrideComponent(
        component: XrComponentOverride
    ): Boolean
}

/** Information about the current XR environment. */
@ExperimentalMaterial3XrApi
public sealed interface XrComponentOverrideEnablerContext {
    /** Whether the user is in an environment that supports XR spatialization. */
    @ExperimentalMaterial3XrApi @get:Composable public val isSpatializationEnabled: Boolean
}

/** The set of Material Components that can be overridden on XR. */
@ExperimentalMaterial3XrApi
@JvmInline
public value class XrComponentOverride private constructor(private val name: String) {
    public companion object {
        /** Material3 NavigationRail. */
        @Suppress("OPT_IN_MARKER_ON_WRONG_TARGET")
        @get:ExperimentalMaterial3XrApi
        @ExperimentalMaterial3XrApi
        public val NavigationRail: XrComponentOverride = XrComponentOverride("NavigationRail")

        /** Material3 NavigationBar. */
        @Suppress("OPT_IN_MARKER_ON_WRONG_TARGET")
        @get:ExperimentalMaterial3XrApi
        @ExperimentalMaterial3XrApi
        public val NavigationBar: XrComponentOverride = XrComponentOverride("NavigationBar")

        /** Material3 Adaptive NavigationSuiteScaffold. */
        @Suppress("OPT_IN_MARKER_ON_WRONG_TARGET")
        @get:ExperimentalMaterial3XrApi
        @ExperimentalMaterial3XrApi
        public val NavigationSuiteScaffold: XrComponentOverride =
            XrComponentOverride("NavigationSuiteScaffold")

        /** Material3 Adaptive ThreePaneScaffold. */
        @Suppress("OPT_IN_MARKER_ON_WRONG_TARGET")
        @get:ExperimentalMaterial3XrApi
        @ExperimentalMaterial3XrApi
        public val ThreePaneScaffold: XrComponentOverride = XrComponentOverride("ThreePaneScaffold")

        /** Material3 single-row TopAppBar. */
        @Suppress("OPT_IN_MARKER_ON_WRONG_TARGET")
        @get:ExperimentalMaterial3XrApi
        @ExperimentalMaterial3XrApi
        public val SingleRowTopAppBar: XrComponentOverride =
            XrComponentOverride("SingleRowTopAppBar")

        /** Material3 two-rows TopAppBar. */
        @Suppress("OPT_IN_MARKER_ON_WRONG_TARGET")
        @get:ExperimentalMaterial3XrApi
        @ExperimentalMaterial3XrApi
        public val TwoRowsTopAppBar: XrComponentOverride = XrComponentOverride("TwoRowsTopAppBar")

        /** Material3 BasicAlertDialog. */
        @Suppress("OPT_IN_MARKER_ON_WRONG_TARGET")
        @get:ExperimentalMaterial3XrApi
        @ExperimentalMaterial3XrApi
        public val BasicAlertDialog: XrComponentOverride = XrComponentOverride("BasicAlertDialog")
    }
}

@ExperimentalMaterial3XrApi
private object XrComponentOverrideEnablerContextImpl : XrComponentOverrideEnablerContext {
    override val isSpatializationEnabled: Boolean
        @Composable get() = LocalSpatialCapabilities.current.isSpatialUiEnabled
}

@OptIn(ExperimentalMaterial3XrApi::class)
private object DefaultXrComponentOverrideEnabler : XrComponentOverrideEnabler {
    @Composable
    override fun XrComponentOverrideEnablerContext.shouldOverrideComponent(
        component: XrComponentOverride
    ): Boolean = isSpatializationEnabled
}
