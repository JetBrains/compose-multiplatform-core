package androidx.compose.ui.kdt

import androidx.annotation.MainThread
import androidx.compose.runtime.CompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlinx.coroutines.CoroutineScope


// TODO[wojciech.krystyniak] This should be internal, but we need it for TestWindow
class Scene<T> /* internal */ constructor(
    internal val coroutineScope: CoroutineScope,
    private val prepareMainThread: () -> T,
    private val restoreMainThread: (T) -> Unit,
    internal val reconcile: () -> Unit,
) {
    @OptIn(ExperimentalContracts::class)
    @MainThread
    internal inline fun <T> withPreparedMainThread(block: () -> T): T {
        contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
        val previousMainThreadState = prepareMainThread()
        try {
            return block()
        } finally {
            restoreMainThread(previousMainThreadState)
        }
    }
}

/* internal */ val ProvidableLocalScene = staticCompositionLocalOf<Scene<*>> {
    error("No Scene provided")
}

val LocalScene: CompositionLocal<Scene<*>> = ProvidableLocalScene

//internal data class SceneContextElement(val scene: Scene<*>) : CoroutineContext.Element {
//    override val key: CoroutineContext.Key<*> get() = SceneContextElement
//
//    companion object : CoroutineContext.Key<SceneContextElement>
//}
//
//internal suspend fun currentScene(): Scene<*>? = currentCoroutineContext()[SceneContextElement]?.scene
