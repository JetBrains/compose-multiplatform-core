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
package androidx.navigation3

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.SeekableTransitionState
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation3.SinglePaneNavDisplay.DEFAULT_TRANSITION_DURATION_MILLISECOND
import androidx.navigation3.SinglePaneNavDisplay.ENTER_TRANSITION_KEY
import androidx.navigation3.SinglePaneNavDisplay.EXIT_TRANSITION_KEY
import androidx.navigation3.SinglePaneNavDisplay.POP_ENTER_TRANSITION_KEY
import androidx.navigation3.SinglePaneNavDisplay.POP_EXIT_TRANSITION_KEY
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Object that indicates the features that can be handled by the [SinglePaneNavDisplay] */
public object SinglePaneNavDisplay {
    /**
     * Function to be called on the [NavEntry.metadata] to notify the [SinglePaneNavDisplay] that
     * the content should be animated using the provided transitions.
     */
    public fun transition(enter: EnterTransition?, exit: ExitTransition?): Map<String, Any> =
        if (enter == null || exit == null) emptyMap()
        else mapOf(ENTER_TRANSITION_KEY to enter, EXIT_TRANSITION_KEY to exit)

    /**
     * Function to be called on the [NavEntry.metadata] to notify the [SinglePaneNavDisplay] that,
     * when popping from backstack, the content should be animated using the provided transitions.
     */
    public fun popTransition(enter: EnterTransition?, exit: ExitTransition?): Map<String, Any> =
        if (enter == null || exit == null) emptyMap()
        else mapOf(POP_ENTER_TRANSITION_KEY to enter, POP_EXIT_TRANSITION_KEY to exit)

    internal const val ENTER_TRANSITION_KEY = "enterTransition"
    internal const val EXIT_TRANSITION_KEY = "exitTransition"
    internal const val POP_ENTER_TRANSITION_KEY = "popEnterTransition"
    internal const val POP_EXIT_TRANSITION_KEY = "popExitTransition"
    internal const val DEFAULT_TRANSITION_DURATION_MILLISECOND = 700
}

/**
 * Display for Composable content that displays a single pane of content at a time, but can move
 * that content in and out with customized transitions.
 *
 * The NavDisplay displays the content associated with the last key on the back stack.
 *
 * @param backStack the collection of keys that represents the state that needs to be handled
 * @param localProviders list of [NavLocalProvider] to add information to the provided entriess
 * @param modifier the modifier to be applied to the layout.
 * @param contentAlignment The [Alignment] of the [AnimatedContent]
 * @param enterTransition Default [EnterTransition] when navigating to [NavEntry]s. Can be
 *   overridden individually for each [NavEntry] by passing in the entry's transitions through
 *   [NavEntry.metadata].
 * @param exitTransition Default [ExitTransition] when navigating to [NavEntry]s. Can be overridden
 *   individually for each [NavEntry] by passing in the entry's transitions through
 *   [NavEntry.metadata].
 * @param popEnterTransition Default [EnterTransition] when popping [NavEntry]s. Can be overridden
 *   individually for each [NavEntry] by passing in the entry's transitions through
 *   [NavEntry.metadata].
 * @param popExitTransition Default [ExitTransition] when popping [NavEntry]s. Can be overridden
 *   individually for each [NavEntry] by passing in the entry's transitions through
 *   [NavEntry.metadata].
 * @param onBack a callback for handling system back presses
 * @param entryProvider lambda used to construct each possible [NavEntry]
 * @sample androidx.navigation3.samples.BaseNav
 * @sample androidx.navigation3.samples.CustomBasicDisplay
 */
@Composable
public fun <T : Any> SinglePaneNavDisplay(
    backStack: List<T>,
    modifier: Modifier = Modifier,
    localProviders: List<NavLocalProvider> = listOf(SaveableStateNavLocalProvider),
    contentAlignment: Alignment = Alignment.TopStart,
    sizeTransform: SizeTransform? = null,
    enterTransition: EnterTransition =
        fadeIn(
            animationSpec =
                tween(
                    DEFAULT_TRANSITION_DURATION_MILLISECOND,
                )
        ),
    exitTransition: ExitTransition =
        fadeOut(
            animationSpec =
                tween(
                    DEFAULT_TRANSITION_DURATION_MILLISECOND,
                )
        ),
    popEnterTransition: EnterTransition =
        fadeIn(
            animationSpec =
                tween(
                    DEFAULT_TRANSITION_DURATION_MILLISECOND,
                )
        ),
    popExitTransition: ExitTransition =
        fadeOut(
            animationSpec =
                tween(
                    DEFAULT_TRANSITION_DURATION_MILLISECOND,
                )
        ),
    onBack: () -> Unit = { if (backStack is MutableList) backStack.removeAt(backStack.size - 1) },
    entryProvider: (key: T) -> NavEntry<T>
) {
    require(backStack.isNotEmpty()) { "NavDisplay backstack cannot be empty" }

    val transitionAwareLifecycleNavLocalProvider = remember {
        TransitionAwareLifecycleNavLocalProvider()
    }
    NavBackStackProvider(
        backStack,
        entryProvider,
        localProviders + transitionAwareLifecycleNavLocalProvider
    ) { entries ->
        // Make a copy shallow copy so that transition.currentState and transition.targetState are
        // different backstack instances. This ensures currentState reflects the old backstack when
        // the backstack (targetState) is updated.
        val newStack = backStack.toList()
        val entry = entries.last()

        var progress by remember { mutableFloatStateOf(0f) }
        var inPredictiveBack by remember { mutableStateOf(false) }
        PredictiveBackHandler(backStack.size > 1) { backEvent ->
            progress = 0f
            try {
                backEvent.collect { value ->
                    inPredictiveBack = true
                    progress = value.progress
                }
                inPredictiveBack = false
                onBack()
            } catch (e: CancellationException) {
                inPredictiveBack = false
            }
        }

        val transitionState = remember {
            // The state returned here cannot be nullable cause it produces the input of the
            // transitionSpec passed into the AnimatedContent and that must match the non-nullable
            // scope exposed by the transitions on the NavHost and composable APIs.
            SeekableTransitionState(newStack)
        }

        val transition = rememberTransition(transitionState, label = entry.key.toString())
        val isPop = isPop(transition.currentState, newStack)
        // Incoming entry defines transitions, otherwise it uses default transitions from
        // NavDisplay
        val finalEnterTransition =
            if (isPop || inPredictiveBack) {
                entry.metadata[POP_ENTER_TRANSITION_KEY] as? EnterTransition ?: popEnterTransition
            } else {
                entry.metadata[ENTER_TRANSITION_KEY] as? EnterTransition ?: enterTransition
            }
        val finalExitTransition =
            if (isPop || inPredictiveBack) {
                entry.metadata[POP_EXIT_TRANSITION_KEY] as? ExitTransition ?: popExitTransition
            } else {
                entry.metadata[EXIT_TRANSITION_KEY] as? ExitTransition ?: exitTransition
            }

        if (inPredictiveBack) {
            LaunchedEffect(progress) {
                transitionState.seekTo(progress, newStack.subList(0, newStack.size - 1))
            }
        } else {
            LaunchedEffect(newStack) {
                // This ensures we don't animate after the back gesture is cancelled and we
                // are already on the current state
                if (transitionState.currentState != newStack) {
                    transitionState.animateTo(newStack)
                } else {
                    // convert from nanoseconds to milliseconds
                    val totalDuration = transition.totalDurationNanos / 1000000
                    // When the predictive back gesture is cancelled, we need to manually animate
                    // the SeekableTransitionState from where it left off, to zero and then
                    // snapTo the final position.
                    animate(
                        transitionState.fraction,
                        0f,
                        animationSpec = tween((transitionState.fraction * totalDuration).toInt())
                    ) { value, _ ->
                        this@LaunchedEffect.launch {
                            if (value > 0) {
                                // Seek the original transition back to the currentState
                                transitionState.seekTo(value)
                            }
                            if (value == 0f) {
                                // Once we animate to the start, we need to snap to the right state.
                                transitionState.snapTo(newStack)
                            }
                        }
                    }
                }
            }
        }

        transition.AnimatedContent(
            modifier = modifier,
            transitionSpec = {
                ContentTransform(
                    targetContentEnter = finalEnterTransition,
                    initialContentExit = finalExitTransition,
                    sizeTransform = sizeTransform
                )
            },
            contentAlignment = contentAlignment,
            contentKey = { it.last() }
        ) { innerStack ->
            // popped entries will be remembered and retrieved so it can animate out properly
            val currEntry =
                remember(innerStack) {
                    entries.findLast { entry -> entry.key == innerStack.last() }
                }
            CompositionLocalProvider(LocalNavAnimatedContentScope provides this) {
                currEntry!!.content.invoke(currEntry.key)
            }
        }
        LaunchedEffect(transition.currentState, transition.targetState) {
            // If we've reached the targetState, our animation has settled
            val settled = transition.currentState == transition.targetState
            transitionAwareLifecycleNavLocalProvider.isSettled = settled
        }
    }
}

private fun <T : Any> isPop(oldBackStack: List<T>, newBackStack: List<T>): Boolean {
    // entire stack replaced
    if (oldBackStack.first() != newBackStack.first()) return false
    // navigated
    if (newBackStack.size > oldBackStack.size) return false

    val divergingIndex =
        newBackStack.indices.firstOrNull { index -> newBackStack[index] != oldBackStack[index] }
    // if newBackStack never diverged from oldBackStack, then it is a clean subset of the oldStack
    // and is a pop
    return divergingIndex == null && newBackStack.size != oldBackStack.size
}

private class TransitionAwareLifecycleNavLocalProvider : NavLocalProvider {

    var isSettled by mutableStateOf(false)

    @Composable
    override fun ProvideToBackStack(backStack: List<Any>, content: @Composable (() -> Unit)) {
        val localInfo = remember(backStack) { TransitionAwareLifecycleNavLocalInfo(backStack) }
        CompositionLocalProvider(LocalTransitionAwareLifecycleNavLocalInfo provides localInfo) {
            content.invoke()
        }
    }

    @Composable
    override fun <T : Any> ProvideToEntry(entry: NavEntry<T>) {
        val backStack = LocalTransitionAwareLifecycleNavLocalInfo.current.backStack
        // TODO: Handle duplicate keys
        val isInBackStack = entry.key in backStack
        val maxLifecycle =
            when {
                isInBackStack && isSettled -> Lifecycle.State.RESUMED
                isInBackStack && !isSettled -> Lifecycle.State.STARTED
                else /* !isInBackStack */ -> Lifecycle.State.CREATED
            }
        LifecycleOwner(maxLifecycle = maxLifecycle) { entry.content.invoke(entry.key) }
    }
}

private val LocalTransitionAwareLifecycleNavLocalInfo =
    compositionLocalOf<TransitionAwareLifecycleNavLocalInfo> {
        error(
            "CompositionLocal LocalTransitionAwareLifecycleNavLocalInfo not present. You must " +
                "call ProvideToBackStack before calling ProvideToEntry."
        )
    }

private class TransitionAwareLifecycleNavLocalInfo(val backStack: List<Any>)

@Composable
private fun LifecycleOwner(
    maxLifecycle: Lifecycle.State = Lifecycle.State.RESUMED,
    parentLifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current,
    content: @Composable () -> Unit
) {
    val childLifecycleOwner = remember(parentLifecycleOwner) { ChildLifecycleOwner() }
    // Pass LifecycleEvents from the parent down to the child
    DisposableEffect(childLifecycleOwner, parentLifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            childLifecycleOwner.handleLifecycleEvent(event)
        }

        parentLifecycleOwner.lifecycle.addObserver(observer)

        onDispose { parentLifecycleOwner.lifecycle.removeObserver(observer) }
    }
    // Ensure that the child lifecycle is capped at the maxLifecycle
    LaunchedEffect(childLifecycleOwner, maxLifecycle) {
        childLifecycleOwner.maxLifecycle = maxLifecycle
    }
    // Now install the LifecycleOwner as a composition local
    CompositionLocalProvider(LocalLifecycleOwner provides childLifecycleOwner) { content.invoke() }
}

private class ChildLifecycleOwner : LifecycleOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    var maxLifecycle: Lifecycle.State = Lifecycle.State.INITIALIZED
        set(maxState) {
            field = maxState
            updateState()
        }

    private var parentLifecycleState: Lifecycle.State = Lifecycle.State.CREATED

    fun handleLifecycleEvent(event: Lifecycle.Event) {
        parentLifecycleState = event.targetState
        updateState()
    }

    fun updateState() {
        if (parentLifecycleState.ordinal < maxLifecycle.ordinal) {
            lifecycleRegistry.currentState = parentLifecycleState
        } else {
            lifecycleRegistry.currentState = maxLifecycle
        }
    }
}
