package androidx.compose.ui.kdt

import fleet.util.logging.logger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.channels.Channel
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * Dispatcher with the ability to synchronously drain all pending tasks, including ones that
 * are scheduled in the process of draining.
 * If not drained manually, all tasks will eventually be drained by a job launched into [dispatcher].
 */
internal class DrainableCoroutineDispatcher(private val dispatcher: CoroutineDispatcher) : CoroutineDispatcher() {
  private val tasks = Channel<Runnable>(capacity = Channel.Factory.UNLIMITED)
  private val drainingScheduled = AtomicBoolean(false)
  private val disposed = AtomicBoolean(false)

  @OptIn(ExperimentalStdlibApi::class)
  override fun dispatch(context: CoroutineContext, block: Runnable) {
    check(!disposed.load())
    tasks.trySend(block)
    if (drainingScheduled.compareAndSet(false, true)) {
      dispatcher.dispatch(EmptyCoroutineContext, ::drain)
      check(!disposed.load())
    }
  }

  private fun doDrain() {
    do {
      val task = tasks.tryReceive().getOrNull()
      try {
        task?.run()
      }
      catch (throwable: Throwable) {
        logger.error(throwable, "Failed to run dispatched task")
      }
    } while (task != null)
  }

  /**
   * Synchronously executes all scheduled tasks, including ones that are newly dispatched
   * while draining, until there are none remaining.
   * **This method must be called on the same thread that [dispatcher] would dispatch into!**
   */
  fun drain() {
    doDrain()
    drainingScheduled.store(false)
    // we need to double doDrain here
    // because we might skip scheduling exactly
    // between first doDrain and reseting the atomic
    doDrain()
  }

  fun completeAndJoin() {
    doDrain()
    disposed.store(true)
  }

  companion object {
    val logger = logger<DrainableCoroutineDispatcher>()
  }
}
