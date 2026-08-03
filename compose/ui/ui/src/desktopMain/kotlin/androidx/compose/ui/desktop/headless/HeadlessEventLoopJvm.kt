package androidx.compose.ui.desktop.headless

import androidx.compose.ui.desktop.logging.logger
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
                    try {
                        @Suppress("UNCHECKED_CAST")
                        (item as () -> Unit).invoke()
                    } catch (e: Throwable) {
                        logger.error(e, "Failed to run headless event loop task")
                    } finally {
                        pendingTasks.decrementAndGet()
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

actual fun createHeadlessEventLoop(libraryFolderPath: String): HeadlessEventLoop {
    // Skia/skiko in a headless test JVM can load its natives from this path; FleetTest passes the
    // shared skiko folder here (same contract as Noria's HeadlessEventLoopJvm). Only point skiko at
    // the folder when it actually contains the native: once `skiko.library.path` is set, skiko loads
    // solely from there and will NOT fall back to the native bundled in its runtime jar. Callers that
    // pass a folder without skiko natives (e.g. java.io.tmpdir in-repo tests) then rely on that jar.
    if (folderContainsSkikoNative(libraryFolderPath)) {
        System.setProperty("skiko.library.path", libraryFolderPath)
    }
    return HeadlessEventLoopJvm()
}

private fun folderContainsSkikoNative(libraryFolderPath: String): Boolean =
    java.io.File(libraryFolderPath).listFiles()?.any { file ->
        val name = file.name
        name.contains("skiko", ignoreCase = true) &&
            (name.endsWith(".dylib") || name.endsWith(".so") || name.endsWith(".dll"))
    } == true
