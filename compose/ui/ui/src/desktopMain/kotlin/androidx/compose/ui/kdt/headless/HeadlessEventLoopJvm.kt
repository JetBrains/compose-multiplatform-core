package androidx.compose.ui.kdt.headless

import androidx.compose.ui.kdt.logging.logger
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.jvm.Volatile

internal class HeadlessEventLoopJvm : HeadlessEventLoop {
    private val queue = LinkedBlockingQueue<Any>()
    private val pendingTasks = AtomicInteger(0)
    private val stateLock = Any()

    @Volatile
    private var loopThreadId: Long = 0

    @Volatile
    private var closed = false

    private val loopThread = thread(start = true, name = "HeadlessEventLoop") {
        loopThreadId = Thread.currentThread().id
        while (true) {
            when (val item = queue.take()) {
                CloseToken -> return@thread
                else -> {
                    pendingTasks.decrementAndGet()
                    try {
                        @Suppress("UNCHECKED_CAST")
                        (item as () -> Unit).invoke()
                    } catch (e: Throwable) {
                        logger.error(e, "Failed to run headless event loop task")
                    }
                }
            }
        }
    }

    override val pendingTasksCount: Int
        get() = pendingTasks.get()

    override fun isCurrentThread(): Boolean = Thread.currentThread().id == loopThreadId

    override fun dispatch(block: () -> Unit) {
        synchronized(stateLock) {
            if (closed) {
                return
            }
            pendingTasks.incrementAndGet()
            queue.offer(block)
        }
    }

    override fun close(dropPendingTasks: Boolean) {
        var droppedTasks = 0
        synchronized(stateLock) {
            if (closed) {
                return
            }
            closed = true
            if (dropPendingTasks) {
                while (true) {
                    val item = queue.poll() ?: break
                    if (item !== CloseToken) {
                        pendingTasks.decrementAndGet()
                        droppedTasks += 1
                    }
                }
            }
            queue.offer(CloseToken)
        }
        if (droppedTasks > 0) {
            logger.warn("Dropped $droppedTasks queued headless event loop tasks during shutdown")
        }
        if (Thread.currentThread() !== loopThread) {
            loopThread.join()
        }
    }

    private companion object {
        private val logger = logger<HeadlessEventLoopJvm>()
        private object CloseToken
    }
}

actual fun createHeadlessEventLoop(): HeadlessEventLoop = HeadlessEventLoopJvm()
