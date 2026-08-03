package androidx.compose.ui.desktop.headless



expect fun createHeadlessEventLoop(libraryFolderPath: String): HeadlessEventLoop

interface HeadlessEventLoop : AutoCloseable {
    fun dispatch(block: () -> Unit)
    fun isCurrentThread(): Boolean
    val pendingTasksCount: Int

    fun close(dropPendingTasks: Boolean)

    override fun close() {
        close(dropPendingTasks = false)
    }
}
