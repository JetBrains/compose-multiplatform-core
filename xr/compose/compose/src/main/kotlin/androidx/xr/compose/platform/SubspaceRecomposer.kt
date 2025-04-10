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

package androidx.xr.compose.platform

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.runtime.PausableMonotonicFrameClock
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.platform.AndroidUiDispatcher
import androidx.core.os.HandlerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.CONFLATED
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** See [androidx.compose.ui.platform.WindowRecomposerPolicy] */
@InternalComposeUiApi
internal object SubspaceRecomposerPolicy {
    private val factory = AtomicReference(SubspaceRecomposerFactory.LifecycleAware)

    internal fun createAndInstallSubspaceRecomposer(
        rootElement: AbstractComposeElement
    ): Recomposer {
        val newRecomposer = factory.get().createRecomposer(rootElement)
        rootElement.compositionContext = newRecomposer

        // If the Recomposer shuts down, unregister it so that a future request for a subspace
        // recomposer will consult the factory for a new one.
        // TODO: update to GlobalScope.launch(AndroidUiDispatcher.CurrentThread) when migrating
        //       to androidx-main
        val scope = MainScope()
        val unsetJob =
            scope.launch {
                try {
                    newRecomposer.join()
                } finally {
                    // Unset if the element is detached. (See below for the attach state change
                    // listener). Since this is in a finally in this coroutine, even if this job is
                    // cancelled we will resume on the window's UI thread and perform this
                    // manipulation
                    // there.
                    if (rootElement.compositionContext === newRecomposer) {
                        rootElement.compositionContext = null
                    }
                }
            }

        // If the root element is detached, cancel the await for recomposer shutdown above.
        // This will also unset the tag reference to this recomposer during its cleanup.
        rootElement.addOnAttachStateChangeListener(
            object : SpatialElement.OnAttachStateChangeListener {
                override fun onElementAttachedToSubspace(
                    spatialComposeScene: SpatialComposeScene
                ) {}

                override fun onElementDetachedFromSubspace(
                    spatialComposeScene: SpatialComposeScene
                ) {
                    rootElement.removeOnAttachStateChangeListener(this)
                    // Cancel the job to clean up the composition context reference in the element.
                    unsetJob.cancel()

                    // Also cancel the recomposer as it is not shared with another tree.
                    newRecomposer.cancel()
                }
            }
        )

        return newRecomposer
    }
}

/**
 * A factory for creating an subspace-scoped [Recomposer].
 *
 * See [createRecomposer] for more info.
 */
@InternalComposeUiApi
private fun interface SubspaceRecomposerFactory {
    /**
     * Creates a [Recomposer] for a subspace with [rootElement] being the root of the Compose
     * hierarchy.
     *
     * The factory is responsible for establishing a policy for [shutting down][Recomposer.cancel]
     * the returned [Recomposer]. [rootElement] will hold a hard reference to the returned
     * [Recomposer] until it [joins][Recomposer.join] after shutting down.
     */
    fun createRecomposer(rootElement: AbstractComposeElement): Recomposer

    companion object {
        /**
         * A [SubspaceRecomposerFactory] that creates **lifecycle-aware** [Recomposer]s.
         *
         * Returned [Recomposer]s will be bound to the [SpatialComposeScene] of the 'rootElement'
         * argument of [createRecomposer] and will be destroyed once the [SpatialComposeScene]
         * lifecycle ends.
         *
         * The recomposer will run [recomposition][Recomposer.runRecomposeAndApplyChanges] and
         * composition effects on the [AndroidUiDispatcher.CurrentThread]. The associated
         * [MonotonicFrameClock] will only produce frames when the [Lifecycle] is at least
         * [Lifecycle.State.STARTED], causing animations and other uses of [MonotonicFrameClock]
         * APIs to suspend until a **visible** frame will be produced.
         */
        @OptIn(ExperimentalComposeUiApi::class)
        val LifecycleAware: SubspaceRecomposerFactory = SubspaceRecomposerFactory { rootElement ->
            createLifecycleAwareSubspaceRecomposer(rootElement)
        }
    }
}

/**
 * Create a [Lifecycle] and
 * [subspace attachment][SpatialElement.isAttachedToSpatialComposeScene]-aware [Recomposer] for the
 * [rootElement] with the same behavior as [SubspaceRecomposerFactory.LifecycleAware].
 *
 * [coroutineContext] will override any [CoroutineContext] elements from the default configuration
 * normally used for this content element. The default [CoroutineContext] contains
 * [AndroidUiDispatcher.CurrentThread];
 *
 * This function should only be called from the UI thread of this [SpatialElement] or its intended
 * UI thread if it is currently detached. It must also only be called when the element is attached
 * to a subspace, i.e., [SpatialElement.spatialComposeScene] must not be `null`. If the
 * [SpatialElement.spatialComposeScene] is `null`, an [IllegalStateException] will be thrown.
 *
 * The returned [Recomposer] will be [cancelled][Recomposer.cancel] when the [rootElement] is
 * detached from its subspace or if its determined that the subspace is destroyed and its
 * [Lifecycle] has [ended][Lifecycle.Event.ON_DESTROY].
 *
 * Recomposition and associated [frame-based][MonotonicFrameClock] effects may be throttled or
 * paused while the [Lifecycle] is not at least [Lifecycle.State.STARTED].
 */
@ExperimentalComposeUiApi
private fun createLifecycleAwareSubspaceRecomposer(
    rootElement: AbstractComposeElement,
    coroutineContext: CoroutineContext = EmptyCoroutineContext,
): Recomposer {
    val subspace =
        checkNotNull(rootElement.spatialComposeScene) {
            "Element $rootElement is not attached to a subspace."
        }

    // Only access AndroidUiDispatcher.CurrentThread if we would use an element from it,
    // otherwise prevent lazy initialization.
    val baseContext =
        if (
            coroutineContext[ContinuationInterceptor] == null ||
                coroutineContext[MonotonicFrameClock] == null
        ) {
            AndroidUiDispatcher.CurrentThread + coroutineContext
        } else {
            coroutineContext
        }

    val pausableClock =
        baseContext[MonotonicFrameClock]?.let { PausableMonotonicFrameClock(it).apply { pause() } }

    var systemDurationScaleSettingConsumer: MotionDurationScaleImpl? = null
    val motionDurationScale =
        baseContext[MotionDurationScale]
            ?: MotionDurationScaleImpl().also { systemDurationScaleSettingConsumer = it }

    val contextWithClockAndMotionScale =
        baseContext + (pausableClock ?: EmptyCoroutineContext) + motionDurationScale
    val recomposer =
        Recomposer(contextWithClockAndMotionScale).also { it.pauseCompositionFrameClock() }
    val runRecomposeScope = CoroutineScope(contextWithClockAndMotionScale)

    // Removing the element that holds the spatial scene graph means it may never be reattached
    // again.
    // Since this factory function is used to create a new recomposer for each invocation and
    // does not reuse a single instance like other factories might, shut it down whenever it
    // becomes detached. This can easily happen as part of subspace content.
    rootElement.onDetachedFromSubspaceOnce { recomposer.cancel() }

    subspace.lifecycle.addObserver(
        object : LifecycleEventObserver {
            override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
                val self = this
                when (event) {
                    Lifecycle.Event.ON_CREATE -> {
                        // UNDISPATCHED launch since we've configured this scope to be on the UI
                        // thread.
                        runRecomposeScope.launch(start = CoroutineStart.UNDISPATCHED) {
                            var durationScaleJob: Job? = null
                            try {
                                durationScaleJob =
                                    systemDurationScaleSettingConsumer?.let {
                                        val durationScaleStateFlow =
                                            getAnimationScaleFlowFor(
                                                subspace.ownerActivity.applicationContext
                                            )
                                        it.scaleFactor = durationScaleStateFlow.value
                                        launch {
                                            durationScaleStateFlow.collect { scaleFactor ->
                                                it.scaleFactor = scaleFactor
                                            }
                                        }
                                    }
                                recomposer.runRecomposeAndApplyChanges()
                            } finally {
                                durationScaleJob?.cancel()
                                // If runRecomposeAndApplyChanges returns or this coroutine is
                                // cancelled
                                // it means we no longer care about this lifecycle. Clean up the
                                // dangling references tied to this observer.
                                source.lifecycle.removeObserver(self)
                            }
                        }
                    }
                    Lifecycle.Event.ON_START -> {
                        // The clock starts life as paused so resume it when starting. If it is
                        // already
                        // running (this ON_START is after an ON_STOP) then the resume is ignored.
                        pausableClock?.resume()

                        // Resumes the frame clock dispatching if this is an ON_START after an
                        // ON_STOP
                        // that paused it. If the recomposer is not paused  calling
                        // `resumeFrameClock()`
                        // is ignored.
                        recomposer.resumeCompositionFrameClock()
                    }
                    Lifecycle.Event.ON_STOP -> {
                        // Pause the recomposer's frame clock which will pause all calls to
                        // `withFrameNanos` (e.g. animations) while the window is stopped.
                        recomposer.pauseCompositionFrameClock()
                    }
                    Lifecycle.Event.ON_DESTROY -> {
                        recomposer.cancel()
                    }
                    Lifecycle.Event.ON_PAUSE -> {
                        // Nothing
                    }
                    Lifecycle.Event.ON_RESUME -> {
                        // Nothing
                    }
                    Lifecycle.Event.ON_ANY -> {
                        // Nothing
                    }
                }
            }
        }
    )

    return recomposer
}

private class MotionDurationScaleImpl : MotionDurationScale {
    override var scaleFactor by mutableFloatStateOf(1f)
}

private val animationScale = mutableMapOf<Context, StateFlow<Float>>()

// Callers of this function should pass an application context. Passing an activity context might
// result in activity leaks.
private fun getAnimationScaleFlowFor(applicationContext: Context): StateFlow<Float> {
    return synchronized(animationScale) {
        animationScale.getOrPut(applicationContext) {
            val resolver = applicationContext.contentResolver
            val animationScaleUri =
                Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE)
            val channel = Channel<Unit>(CONFLATED)
            val contentObserver =
                object : ContentObserver(HandlerCompat.createAsync(Looper.getMainLooper())) {
                    override fun onChange(selfChange: Boolean, uri: Uri?) {
                        @Suppress("UNUSED_VARIABLE") val unused = channel.trySend(Unit)
                    }
                }

            callbackFlow {
                    resolver.registerContentObserver(animationScaleUri, false, contentObserver)
                    try {
                        for (value in channel) {
                            val newValue =
                                Settings.Global.getFloat(
                                    applicationContext.contentResolver,
                                    Settings.Global.ANIMATOR_DURATION_SCALE,
                                    1f,
                                )
                            send(newValue)
                        }
                    } finally {
                        resolver.unregisterContentObserver(contentObserver)
                    }
                }
                .stateIn(
                    MainScope(),
                    SharingStarted.WhileSubscribed(),
                    Settings.Global.getFloat(
                        applicationContext.contentResolver,
                        Settings.Global.ANIMATOR_DURATION_SCALE,
                        1f,
                    ),
                )
        }
    }
}
