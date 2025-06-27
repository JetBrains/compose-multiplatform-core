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

package androidx.navigation3

import androidx.activity.compose.PredictiveBackHandler
import androidx.collection.mutableObjectFloatMapOf
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
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.util.fastForEachReversed
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation3.SceneNavDisplay.DEFAULT_TRANSITION_DURATION_MILLISECOND
import androidx.navigation3.SceneNavDisplay.ENTER_TRANSITION_KEY
import androidx.navigation3.SceneNavDisplay.EXIT_TRANSITION_KEY
import androidx.navigation3.SceneNavDisplay.POP_ENTER_TRANSITION_KEY
import androidx.navigation3.SceneNavDisplay.POP_EXIT_TRANSITION_KEY
import kotlin.reflect.KClass
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

/** Object that indicates the features that can be handled by the [SceneNavDisplay] */
public object SceneNavDisplay {
    /**
     * Function to be called on the [NavEntry.metadata] to notify the [SceneNavDisplay] that the
     * content should be animated using the provided transitions.
     */
    public fun transition(enter: EnterTransition?, exit: ExitTransition?): Map<String, Any> =
        if (enter == null || exit == null) emptyMap()
        else mapOf(ENTER_TRANSITION_KEY to enter, EXIT_TRANSITION_KEY to exit)

    /**
     * Function to be called on the [NavEntry.metadata] to notify the [SceneNavDisplay] that, when
     * popping from backstack, the content should be animated using the provided transitions.
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
 * A nav display that renders and animates between different [Scene]s, each of which can render one
 * or more [NavEntry]s.
 *
 * The [Scene]s are calculated with the given [SceneStrategy], which may be an assembled delegated
 * chain of [SceneStrategy]s. If no [Scene] is calculated, the fallback will be to a
 * [SinglePaneSceneStrategy].
 *
 * It is allowable for different [Scene]s to render the same [NavEntry]s, perhaps on some conditions
 * as determined by the [sceneStrategy] based on window size, form factor, other arbitrary logic.
 *
 * If this happens, and these [Scene]s are rendered at the same time due to animation or predictive
 * back, then the content for the [NavEntry] will only be rendered in the most recent [Scene] that
 * is the target for being the current scene as determined by [sceneStrategy]. This enforces a
 * unique invocation of each [NavEntry], even if it is displayable by two different [Scene]s.
 *
 * @param backStack the collection of keys that represents the state that needs to be handled
 * @param modifier the modifier to be applied to the layout.
 * @param contentAlignment The [Alignment] of the [AnimatedContent]
 * @param onBack a callback for handling system back press. The passed [Int] refers to the number of
 *   entries to pop from the end of the backstack, as calculated by the [sceneStrategy].
 * @param preEntryDecorators a list of [NavEntryDecorator]s to include before the entry content
 *   invocation is made unique.
 * @param postEntryDecorators a list of [NavEntryDecorator]s to include after the entry content
 *   invocation is made unique.
 * @param sceneStrategy the [SceneStrategy] to determine which scene to render a list of entries.
 * @param sizeTransform the [SizeTransform] for the [AnimatedContent].
 * @param enterTransition Default [EnterTransition] when navigating to [NavEntry]s.
 * @param exitTransition Default [ExitTransition] when navigating to [NavEntry]s.
 * @param popEnterTransition Default [EnterTransition] when popping [NavEntry]s.
 * @param popExitTransition Default [ExitTransition] when popping [NavEntry]s.
 * @param entryProvider lambda used to construct each possible [NavEntry]
 * @sample androidx.navigation3.samples.SceneNav
 * @sample androidx.navigation3.samples.SceneNavSharedEntrySample
 * @sample androidx.navigation3.samples.SceneNavSharedElementSample
 */
@Composable
public fun <T : Any> SceneNavDisplay(
    backStack: List<T>,
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.TopStart,
    onBack: (Int) -> Unit = {
        if (backStack is MutableList<T>) {
            repeat(it) { backStack.removeAt(backStack.lastIndex) }
        }
    },
    preEntryDecorators: List<NavEntryDecorator> = emptyList(),
    postEntryDecorators: List<NavEntryDecorator> = emptyList(),
    sceneStrategy: SceneStrategy<T> = SinglePaneSceneStrategy(),
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
    entryProvider: (key: T) -> NavEntry<T>,
) {
    require(backStack.isNotEmpty()) { "NavDisplay backstack cannot be empty" }

    val transitionAwareLifecycleNavEntryDecorator = remember {
        TransitionAwareLifecycleNavEntryDecorator()
    }

    DecoratedNavEntryProvider(
        backStack = backStack,
        entryDecorators =
            // Order here is very important:
            // First we include any entry decorators that should apply to concurrent calls to
            // entries (like setting up shared elements for entire entries)
            preEntryDecorators +
                listOf(
                    // Next, we enforce that we only render 1 call for each entry, depending on the
                    // most recent scenes displayed
                    RenderCurrentEntriesOnlyDecorator,
                    // Afterwards, we wrap everything after in a movableContentOf call to ensure
                    // that
                    // the rest of the content entries can be moved around between different call
                    // sites
                    // while maintaining their instance
                    MovableContentNavEntryDecorator,
                    SaveableStateNavEntryDecorator,
                    transitionAwareLifecycleNavEntryDecorator,
                ) +
                postEntryDecorators,
        entryProvider = entryProvider
    ) { entries ->
        val sceneResult = sceneStrategy.calculateSceneWithSinglePaneFallback(entries = entries)

        var progress by remember { mutableFloatStateOf(0f) }
        var inPredictiveBack by remember { mutableStateOf(false) }

        PredictiveBackHandler(sceneResult.previousEntries.isNotEmpty()) { backEvent ->
            progress = 0f
            try {
                backEvent.collect { value ->
                    inPredictiveBack = true
                    progress = value.progress
                }
                inPredictiveBack = false
                onBack(entries.size - sceneResult.previousEntries.size)
            } finally {
                inPredictiveBack = false
            }
        }

        val sceneKey = sceneResult.scene::class to sceneResult.scene.key

        val scenes = remember { mutableStateMapOf<Pair<KClass<*>, Any>, Scene<T>>() }
        // TODO: This should really be a mutableOrderedStateSetOf
        val mostRecentSceneKeys = remember { mutableStateListOf<Pair<KClass<*>, Any>>() }
        scenes[sceneKey] = sceneResult.scene

        val transitionState = remember {
            // The state returned here cannot be nullable cause it produces the input of the
            // transitionSpec passed into the AnimatedContent and that must match the non-nullable
            // scope exposed by the transitions on the NavHost and composable APIs.
            SeekableTransitionState(sceneKey)
        }

        val transition = rememberTransition(transitionState, label = sceneKey.toString())

        /** Keep track of the previous entries for the transition's current scene. */
        val transitionCurrentStateEntries = remember(transition.currentState) { entries.toList() }

        // Consider this a pop if the current entries match the previous entries we have recorded
        // from the current state of the transition
        val isPop =
            isPop(
                transitionCurrentStateEntries.map { it.key },
                entries.map { it.key },
            )

        val zIndices = remember { mutableObjectFloatMapOf<Pair<KClass<*>, Any>>() }
        val initialKey = transition.currentState
        val targetKey = transition.targetState
        val initialZIndex = zIndices.getOrPut(initialKey) { 0f }
        val targetZIndex =
            when {
                initialKey == targetKey -> initialZIndex
                isPop || inPredictiveBack -> initialZIndex - 1f
                else -> initialZIndex + 1f
            }
        zIndices[targetKey] = targetZIndex
        val transitionEntry =
            if (initialZIndex >= targetZIndex) {
                scenes[initialKey]!!.entries.last()
            } else {
                scenes[targetKey]!!.entries.last()
            }
        val finalEnterTransition =
            if (isPop || inPredictiveBack) {
                transitionEntry.metadata[POP_ENTER_TRANSITION_KEY] as? EnterTransition
                    ?: popEnterTransition
            } else {
                transitionEntry.metadata[ENTER_TRANSITION_KEY] as? EnterTransition
                    ?: enterTransition
            }
        val finalExitTransition =
            if (isPop || inPredictiveBack) {
                transitionEntry.metadata[POP_EXIT_TRANSITION_KEY] as? ExitTransition
                    ?: popExitTransition
            } else {
                transitionEntry.metadata[EXIT_TRANSITION_KEY] as? ExitTransition ?: exitTransition
            }

        if (inPredictiveBack) {
            val peekSceneResult =
                sceneStrategy.calculateSceneWithSinglePaneFallback(sceneResult.previousEntries)
            val peekSceneKey = peekSceneResult.scene::class to peekSceneResult.scene.key
            scenes[peekSceneKey] = peekSceneResult.scene
            if (transitionState.currentState != peekSceneKey) {
                LaunchedEffect(progress) { transitionState.seekTo(progress, peekSceneKey) }
            }
        } else {
            LaunchedEffect(sceneKey) {
                if (transitionState.currentState != sceneKey) {
                    transitionState.animateTo(sceneKey)
                }
                // This ensures we don't animate after the back gesture is cancelled and we
                // are already on the current state
                if (transitionState.currentState != sceneKey) {
                    transitionState.animateTo(sceneKey)
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
                                transitionState.snapTo(sceneKey)
                            }
                        }
                    }
                }
            }
        }

        LaunchedEffect(transition.targetState) {
            if (mostRecentSceneKeys.lastOrNull() != transition.targetState) {
                mostRecentSceneKeys.remove(transition.targetState)
                mostRecentSceneKeys.add(transition.targetState)
            }
        }
        // Determine which NavEntrys should be rendered within each scene.
        // Each renderable Scene, in order from the scene that is most recently the target scene to
        // the scene that is least recently the target scene will be assigned each visible
        // entry that hasn't already been assigned to a Scene that is more recent.
        val sceneToRenderableEntryMap =
            remember(
                mostRecentSceneKeys.toList(),
                scenes.values.map { scene -> scene.entries.map(NavEntry<T>::key) },
                transition.targetState,
            ) {
                buildMap {
                    val coveredEntryKeys = mutableSetOf<T>()
                    (mostRecentSceneKeys.filter { it != transition.targetState } +
                            listOf(transition.targetState))
                        .fastForEachReversed { sceneKey ->
                            val scene = scenes.getValue(sceneKey)
                            put(
                                sceneKey,
                                scene.entries
                                    .map { it.key }
                                    .filterNot(coveredEntryKeys::contains)
                                    .toSet(),
                            )
                            scene.entries.forEach { coveredEntryKeys.add(it.key) }
                        }
                }
            }

        transition.AnimatedContent(
            contentAlignment = contentAlignment,
            modifier = modifier,
            transitionSpec = {
                ContentTransform(
                    targetContentEnter = finalEnterTransition,
                    initialContentExit = finalExitTransition,
                    // z-index increases during navigate and decreases during pop.
                    targetContentZIndex = targetZIndex,
                    sizeTransform = sizeTransform
                )
            },
        ) { targetSceneKey ->
            val targetScene = scenes.getValue(targetSceneKey)
            CompositionLocalProvider(
                LocalNavAnimatedContentScope provides this,
                LocalEntriesToRenderInCurrentScene provides
                    sceneToRenderableEntryMap.getValue(targetSceneKey),
            ) {
                targetScene.content()
            }
        }

        // Clean-up scene book-keeping once the transition is finished.
        LaunchedEffect(transition) {
            snapshotFlow { transition.isRunning }
                .filter { !it }
                .collect {
                    scenes.keys.toList().forEach { key ->
                        if (key != transition.targetState) {
                            scenes.remove(key)
                        }
                    }
                    mostRecentSceneKeys.toList().forEach { key ->
                        if (key != transition.targetState) {
                            mostRecentSceneKeys.remove(key)
                        }
                    }
                }
        }

        LaunchedEffect(transition.currentState, transition.targetState) {
            // If we've reached the targetState, our animation has settled
            val settled = transition.currentState == transition.targetState
            transitionAwareLifecycleNavEntryDecorator.isSettled = settled
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

/**
 * A [NavEntryDecorator] that wraps each entry in a [movableContentOf] to allow nav displays to
 * arbitrarily place entries in different places in the composable call hierarchy.
 *
 * This should likely be the first [NavEntryDecorator] to ensure that other
 * [NavEntryDecorator.DecorateEntry] calls that are stateful are moved properly inside the
 * [movableContentOf].
 */
internal object MovableContentNavEntryDecorator : NavEntryDecorator {

    @Composable
    override fun DecorateBackStack(backStack: List<Any>, content: @Composable (() -> Unit)) {
        val backStackKeys = backStack.toSet()

        // This is an intricate dance to create a movableContentOf for each entry that is scoped
        // to the backstack, that calls the correct updated content.
        // First we associate each key in the backstack with a MutableState that will contain
        // the actual content of the entry, as updated in DecorateEntry.
        // The MutableState's remembered lifecycle precisely matches when its key is in the
        // backstack.
        val movableContentContentHolderMap: Map<Any, MutableState<@Composable () -> Unit>> =
            backStackKeys.associateWith { key ->
                key(key) {
                    remember {
                        mutableStateOf(
                            @Composable {
                                error(
                                    "Should not be called, this should always be updated in" +
                                        "DecorateEntry with the real content"
                                )
                            }
                        )
                    }
                }
            }

        // Second we create another map containing the movable contents themselves, again
        // by associating the backstack key with a remembered movableContentOf
        // The critical thing here is that the movableContentOf's remembered lifecycle precisely
        // matches when its key is in the backstack.
        val movableContentHolderMap: Map<Any, @Composable () -> Unit> =
            backStackKeys.associateWith { key ->
                key(key) {
                    remember {
                        movableContentOf {
                            // In case the key is removed from the backstack while this is still
                            // being rendered, we remember the MutableState directly to allow
                            // rendering it while we are animating out.
                            remember { movableContentContentHolderMap.getValue(key) }.value()
                        }
                    }
                }
            }
        CompositionLocalProvider(
            LocalMovableContentNavLocalInfo provides
                MovableContentNavLocalInfo(movableContentHolderMap, movableContentContentHolderMap),
            content = content,
        )
    }

    @Composable
    override fun <T : Any> DecorateEntry(entry: NavEntry<T>) {
        val movableContentNavLocalInfo = LocalMovableContentNavLocalInfo.current
        key(entry.key) {
            // In case the key is removed from the backstack while this is still
            // being rendered, we remember the MutableState directly to allow
            // updating it while we are animating out.
            val movableContentContentHolder = remember {
                movableContentNavLocalInfo.movableContentContentHolderMap.getValue(entry.key)
            }
            // Update the state holder with the actual entry content
            movableContentContentHolder.value = { entry.content(entry.key) }
            // In case the key is removed from the backstack while this is still
            // being rendered, we remember the movableContent directly to allow
            // rendering it while we are animating out.
            val movableContentHolder = remember {
                movableContentNavLocalInfo.movableContentHolderMap.getValue(entry.key)
            }
            // Finally, render the entry content via the movableContentOf
            movableContentHolder()
        }
    }
}

internal val LocalMovableContentNavLocalInfo =
    staticCompositionLocalOf<MovableContentNavLocalInfo> {
        error(
            "CompositionLocal LocalMovableContentNavLocalInfo not present. You must call " +
                "DecorateBackStack before calling DecorateEntry."
        )
    }

@Immutable
internal class MovableContentNavLocalInfo(
    val movableContentHolderMap: Map<Any, @Composable () -> Unit>,
    val movableContentContentHolderMap: Map<Any, MutableState<@Composable () -> Unit>>
)

/**
 * A [NavEntryDecorator] that wraps each entry in a shared element that is controlled by the
 * [Scene].
 */
internal object RenderCurrentEntriesOnlyDecorator : NavEntryDecorator {
    @Composable
    override fun <T : Any> DecorateEntry(entry: NavEntry<T>) {
        if (LocalEntriesToRenderInCurrentScene.current.contains(entry.key)) {
            entry.content(entry.key)
        }
    }
}

/**
 * The entry keys to render in the current [Scene], in the sense of the target of the animation for
 * an [AnimatedContent] that is transitioning between different scenes.
 */
public val LocalEntriesToRenderInCurrentScene: ProvidableCompositionLocal<Set<Any>> =
    compositionLocalOf<Set<Any>> {
        throw IllegalStateException(
            "Unexpected access to LocalEntriesToRenderInCurrentScene. You should only " +
                "access LocalEntriesToRenderInCurrentScene inside a NavEntry passed " +
                "to SceneNavDisplay."
        )
    }

private val LocalTransitionAwareLifecycleNavLocalInfo =
    compositionLocalOf<TransitionAwareLifecycleNavLocalInfo> {
        error(
            "CompositionLocal LocalTransitionAwareLifecycleNavLocalInfo not present. You must " +
                "call DecorateBackStack before calling DecorateEntry."
        )
    }

private class TransitionAwareLifecycleNavEntryDecorator : NavEntryDecorator {

    var isSettled by mutableStateOf(true)

    @Composable
    override fun DecorateBackStack(backStack: List<Any>, content: @Composable (() -> Unit)) {
        val localInfo = remember(backStack) { TransitionAwareLifecycleNavLocalInfo(backStack) }
        CompositionLocalProvider(LocalTransitionAwareLifecycleNavLocalInfo provides localInfo) {
            content.invoke()
        }
    }

    @Composable
    override fun <T : Any> DecorateEntry(entry: NavEntry<T>) {
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
