@file:Suppress("DuplicatedCode")

package androidx.compose.ui.desktop.gtk

import androidx.compose.ui.desktop.LightweightWindowId
import androidx.compose.ui.desktop.logging.logger
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.jetbrains.desktop.gtk.Application
import org.jetbrains.desktop.gtk.Event
import org.jetbrains.desktop.gtk.RequestId
import org.jetbrains.desktop.gtk.ShowNotificationParams

class GtkNotificationCenter(
    private val application: Application,
) {
    private data class PendingNotification(
        val action: Action?,
        val continuation: kotlinx.coroutines.CancellableContinuation<NotificationId?>,
    )

    private val pendingNotifications = mutableMapOf<RequestId, PendingNotification>()
    private val actionCallbacks = mutableMapOf<NotificationId, Action>()

    fun isNotificationsAllowed(): Boolean = true

    suspend fun showNotification(
        title: String,
        description: String,
        sound: Sound,
        vararg actions: Action,
    ): NotificationId? {
        return withContext(Dispatchers.Main.immediate) {
            // Wait for the launch job before any native notification call, matching macOS's
            // ordering gate (requests issued before the launch completes may be dropped or
            // misordered). Called on the object directly rather than current() so a
            // notification racing shutdown resolves the already-completed job instead of throwing.
            GtkApplication.awaitWhenReady()
            if (isNotificationsAllowed().not()) {
                return@withContext null
            }

            val requestId = runCatching {
                application.requestShowNotification(
                    ShowNotificationParams(
                        title = title,
                        body = description,
                        soundFilePath = null,
                    ),
                )
            }.onFailure { throwable ->
                logger.error(throwable) {
                    "Failed to show notification: $title"
                }
            }.getOrNull() ?: return@withContext null

            suspendCancellableCoroutine { continuation ->
                pendingNotifications[requestId] = PendingNotification(actions.firstOrNull(), continuation)
                continuation.invokeOnCancellation {
                    pendingNotifications.remove(requestId)
                }
            }
        }
    }

    suspend fun removeNotification(notificationId: NotificationId) {
        withContext(Dispatchers.Main.immediate) {
            GtkApplication.awaitWhenReady()
            actionCallbacks.remove(notificationId)
            application.closeNotification(notificationId.value)
        }
    }

    internal fun onNotificationShown(event: Event.NotificationShown) {
        pendingNotifications.remove(event.requestId)?.let { pending ->
            val notificationId = event.notificationId
            if (notificationId == null) {
                pending.continuation.resume(null)
            } else {
                val id = NotificationId(notificationId)
                pending.action?.let { actionCallbacks[id] = it }
                pending.continuation.resume(id)
            }
        }
    }

    internal fun onNotificationClosed(
        event: Event.NotificationClosed,
        activateWindow: (LightweightWindowId, String) -> Unit,
    ) {
        val notificationId = NotificationId(event.notificationId)
        actionCallbacks.remove(notificationId)?.let { action ->
            event.activationToken?.let { activationToken ->
                action.bringToForeground?.let { windowId ->
                    activateWindow(windowId, activationToken)
                }
            }
            action.block()
        }
    }

    @JvmInline
    value class NotificationId(val value: UInt)

    sealed class Sound {
        data object Default : Sound()
        data object None : Sound()
        data class Named(val name: String) : Sound()
    }

    class Action(
        val id: Id,
        val bringToForeground: LightweightWindowId?,
        val block: () -> Unit,
    ) {
        sealed interface Id {
            data class Custom(val title: String) : Id
            data object Default : Id
            data object Dismiss : Id
        }
    }
}

private val logger = logger<GtkNotificationCenter>()
