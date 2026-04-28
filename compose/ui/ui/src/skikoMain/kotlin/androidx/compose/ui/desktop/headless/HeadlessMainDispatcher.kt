package androidx.compose.ui.desktop.headless

import kotlinx.coroutines.MainCoroutineDispatcher
import kotlinx.coroutines.Runnable
import kotlin.coroutines.CoroutineContext

internal sealed class HeadlessMainDispatcherBase(protected val eventLoop: HeadlessEventLoop) : MainCoroutineDispatcher() {
    override val immediate: HeadlessImmediateMainDispatcher by lazy { HeadlessImmediateMainDispatcher(eventLoop) }

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        eventLoop.dispatch { block.run() }
    }
}

internal class HeadlessMainDispatcher(eventLoop: HeadlessEventLoop) : HeadlessMainDispatcherBase(eventLoop) {
    override fun toString(): String = "Dispatchers.HeadlessMain"
}

internal class HeadlessImmediateMainDispatcher(eventLoop: HeadlessEventLoop) : HeadlessMainDispatcherBase(eventLoop) {
    override fun isDispatchNeeded(context: CoroutineContext): Boolean {
        return !eventLoop.isCurrentThread()
    }

    override fun toString(): String = "Dispatchers.HeadlessMain.immediate"
}
