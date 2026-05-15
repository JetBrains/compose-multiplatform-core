package androidx.compose.ui.desktop.macos

import androidx.compose.ui.ComposeUIDispatcher
import androidx.compose.ui.desktop.LightweightWindowId
import androidx.compose.ui.desktop.logging.logger
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.jetbrains.desktop.macos.AuthorizationStatus
import org.jetbrains.desktop.macos.NotificationAction
import org.jetbrains.desktop.macos.NotificationCategory
import org.jetbrains.desktop.macos.NotificationCenter
import org.jetbrains.desktop.macos.NotificationSound
import kotlin.collections.set
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class MacOsNotificationCenter private constructor (private val application: MacOsApplication) {
    private suspend fun awaitWhenReady() {
        application.awaitWhenReady()
    }

    init {
        NotificationCenter.registerNotificationCategories(emptyList())
        NotificationCenter.setActionResponseCallback(::onNotificationAction)
    }

    companion object {
        internal fun init(application: MacOsApplication): MacOsNotificationCenter? {
            return if (NotificationCenter.isSupportedByApplication) {
                MacOsNotificationCenter(application)
            } else {
                null
            }
        }
    }

    internal suspend fun getAuthorizationStatus(): AuthorizationStatus =
        suspendCancellableCoroutine { cont ->
            NotificationCenter.getAuthorizationStatus { status ->
                cont.resume(status)
            }
        }

    internal suspend fun requestAuthorization(): Boolean = suspendCancellableCoroutine { cont ->
        NotificationCenter.requestAuthorization { status ->
            cont.resume(status)
        }
    }

    internal fun onNotificationAction(
        notificationId: NotificationCenter.NotificationId,
        actionId: NotificationCenter.ActionId,
    ) {
        val notificationId = NotificationId(notificationId.value)
        // after action invocation we remove the callback.
        // according to my observations on macOS 26.1, it's not possible to trigger two actions in one notification
        actionCallbacks.remove(notificationId)?.firstOrNull { it.id.toActionId() == actionId }?.let { action ->
            action.bringToForeground?.let { windowId ->
                application.windows[windowId]?.requestFocusAndBringToFront()
            }
            action.block.invoke()
        }
    }

    suspend fun isNotificationsAllowed(): Boolean {
        awaitWhenReady()
        return withContext(ComposeUIDispatcher) {
            when (getAuthorizationStatus()) {
                AuthorizationStatus.NotDetermined -> {
                    requestAuthorization()
                }
                AuthorizationStatus.Denied -> {
                    false
                }
                AuthorizationStatus.Authorized -> {
                    true
                }
                AuthorizationStatus.Provisional -> {
                    true
                }
                AuthorizationStatus.Ephemeral -> {
                    true
                }
            }
        }
    }

    internal suspend fun showNotificationImpl(
        title: String,
        description: String,
        sound: Sound,
        notificationId: String,
        categoryId: NotificationCenter.CategoryId,
    ) {
        suspendCancellableCoroutine { cont ->
            NotificationCenter.showNotification(
                title = title,
                body = description,
                sound = sound.toKDTSound(),
                notificationId = NotificationCenter.NotificationId(notificationId),
                categoryId = categoryId,
            ) { error ->
                if (error != null) {
                    cont.resumeWithException(Error(error))
                } else {
                    cont.resume(Unit)
                }
            }
        }
    }

    private val registeredCategories = hashMapOf<ActionsCacheKey, NotificationCategory>()
    private var idCounter = 0L
    private fun makeStringUnique(name: String) = "$name#${idCounter++}"

    data class ActionCacheKeyItem(
        val id: Action.Id,
        val bringToForeground: LightweightWindowId?,
    )

    @JvmInline
    value class ActionsCacheKey(val items: List<ActionCacheKeyItem>)

    private fun actionsToCacheKey(actions: Array<out Action>): ActionsCacheKey {
        return ActionsCacheKey(actions.map { ActionCacheKeyItem(it.id, it.bringToForeground) })
    }

    private fun cachedCategoryId(actions: Array<out Action>): NotificationCenter.CategoryId {
        if (actions.isEmpty()) {
            return NotificationCenter.DefaultCategory
        }
        val cacheKey = actionsToCacheKey(actions)

        val categoryId = registeredCategories[cacheKey]
        if (categoryId != null) {
            return categoryId.categoryId
        }

        val newCategoryId = NotificationCenter.CategoryId(makeStringUnique("Category"))
        val categoryActions =
            actions.filter { it.id is Action.Id.Custom }.map {
                val actionId = it.id.toActionId()
                NotificationAction(
                    actionId = actionId,
                    isForeground = it.bringToForeground != null,
                    title = actionId.value,
                )
            }
        registeredCategories[cacheKey] =
            NotificationCategory(categoryId = newCategoryId, categoryActions)
        NotificationCenter.registerNotificationCategories(registeredCategories.values.toList())
        return newCategoryId
    }

    private var actionCallbacks =
        mutableMapOf<NotificationId, List<Action>>()

    suspend fun showNotification(
        title: String,
        description: String,
        sound: Sound,
        vararg actions: Action,
    ): NotificationId? {
        awaitWhenReady()
        return withContext(ComposeUIDispatcher) {
            if (isNotificationsAllowed().not()) return@withContext null

            val categoryId = cachedCategoryId(actions)
            val notificationId = NotificationId(makeStringUnique(title))
            actionCallbacks[notificationId] = actions.toList()

            try {
                showNotificationImpl(
                    title,
                    description,
                    sound,
                    notificationId.value,
                    categoryId,
                )

            } catch (e: Throwable) {
                actionCallbacks.remove(notificationId)
                logger.error(e) {
                    "Failed to show notification: $title, $description"
                }
                return@withContext null
            }
            return@withContext notificationId
        }
    }

    suspend fun removeNotification(notificationId: NotificationId) {
        awaitWhenReady()
        withContext(ComposeUIDispatcher) {
            actionCallbacks.remove(notificationId)
            NotificationCenter.removeNotification(NotificationCenter.NotificationId(notificationId.value))
        }
    }

    @JvmInline
    value class NotificationId(val value: String)

    /**
     * See the doc: https://developer.apple.com/documentation/usernotifications/unnotificationsound?language=objc
     */
    sealed class Sound {
        object Default : Sound()
        object None : Sound()
        object Critical : Sound()
        object Ringtone : Sound()
        data class Named(val name: String) : Sound()
        data class CriticalNamed(val name: String) : Sound()
    }

    class Action(val id: Id, val bringToForeground: LightweightWindowId?, val block: () -> Unit) {
        sealed interface Id {
            data class Custom(val title: String) : Id
            data object Default : Id
            data object Dismiss : Id
        }
    }
}

private fun MacOsNotificationCenter.Action.Id.toActionId(): NotificationCenter.ActionId {
    return when (this) {
        is MacOsNotificationCenter.Action.Id.Custom -> NotificationCenter.ActionId(title)
        MacOsNotificationCenter.Action.Id.Default -> NotificationCenter.DefaultAction
        MacOsNotificationCenter.Action.Id.Dismiss -> NotificationCenter.DismissAction
    }
}

fun MacOsNotificationCenter.Sound.toKDTSound(): NotificationSound {
    return when (this) {
        MacOsNotificationCenter.Sound.Critical -> NotificationSound.Critical
        is MacOsNotificationCenter.Sound.CriticalNamed -> NotificationSound.CriticalNamed(name)
        MacOsNotificationCenter.Sound.Default -> NotificationSound.Default
        is MacOsNotificationCenter.Sound.Named -> NotificationSound.Named(name)
        MacOsNotificationCenter.Sound.None -> NotificationSound.None
        MacOsNotificationCenter.Sound.Ringtone -> NotificationSound.Ringtone
    }
}

private val logger = logger<MacOsNotificationCenter>()
