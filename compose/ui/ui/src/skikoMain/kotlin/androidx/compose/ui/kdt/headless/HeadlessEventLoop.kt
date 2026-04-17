package androidx.compose.ui.kdt.headless



expect fun createHeadlessEventLoop(): HeadlessEventLoop

interface HeadlessEventLoop : AutoCloseable {
    fun dispatch(block: () -> Unit)
    fun isCurrentThread(): Boolean
    val pendingTasksCount: Int

    fun close(dropPendingTasks: Boolean)

    override fun close() {
        close(dropPendingTasks = false)
    }
}
