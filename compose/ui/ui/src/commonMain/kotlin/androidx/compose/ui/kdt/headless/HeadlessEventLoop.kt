package androidx.compose.ui.kdt.headless


import fleet.util.multiplatform.linkToActual

fun createHeadlessEventLoop(): HeadlessEventLoop = linkToActual()

interface HeadlessEventLoop : AutoCloseable {
    fun dispatch(block: () -> Unit)
    fun isCurrentThread(): Boolean
    val pendingTasksCount: Int

    fun close(dropPendingTasks: Boolean)

    override fun close() {
        close(dropPendingTasks = false)
    }
}
