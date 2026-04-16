package androidx.compose.ui.kdt

import androidx.compose.ui.ComposeDispatcher
import kotlin.concurrent.Volatile
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.MainCoroutineDispatcher
import kotlinx.coroutines.internal.MainDispatcherFactory

@OptIn(InternalCoroutinesApi::class)
class KdtMainDispatcherFactory : MainDispatcherFactory {
    companion object {
        /**
         * Optional dispatcher override. Set this before any use of Dispatchers.Main.
         */
        @Volatile
        internal var overridingMainDispatcher: MainCoroutineDispatcher? = null
    }

    override val loadPriority: Int
        get() = 0

    override fun createDispatcher(allFactories: List<MainDispatcherFactory>): MainCoroutineDispatcher {
        return overridingMainDispatcher ?: ComposeDispatcher
    }
}
