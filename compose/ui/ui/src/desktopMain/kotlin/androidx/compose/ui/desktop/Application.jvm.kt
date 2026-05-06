/*
 * Copyright 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.compose.ui.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composition
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.desktop.gtk.GtkApplication
import androidx.compose.ui.desktop.gtk.GtkUriHandler
import androidx.compose.ui.desktop.linux.LinuxApplication
import androidx.compose.ui.desktop.linux.LinuxUriHandler
import androidx.compose.ui.desktop.macos.MacOsApplication
import androidx.compose.ui.desktop.macos.MacOsUriHandler
//import androidx.compose.ui.kdt.windows.WindowsApplication
//import androidx.compose.ui.kdt.windows.WindowsUriHandler
import androidx.compose.ui.platform.DesktopPlatform
import androidx.compose.ui.platform.GlobalSnapshotManager
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.window.ApplicationScope
import java.nio.file.Path
import kotlin.concurrent.thread
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.jetbrains.skiko.MainUIDispatcher

fun runApplicationBlocking(
    identifier: String,
    openUrls: (List<String>) -> Unit = {},
    libraryFolder: kotlinx.io.files.Path = defaultLibraryFolder(),
    logFolder: kotlinx.io.files.Path = defaultLogFolder(),
    content: @Composable () -> Unit,
) {
    runBlocking {
        awaitApplication(
            identifier,
            openUrls,
            libraryFolder,
            logFolder,
            content
        )
    }
}

actual fun initializeApplication(
    identifier: String,
    openUrls: (List<String>) -> Unit,
    libraryFolder: kotlinx.io.files.Path,
    logFolder: kotlinx.io.files.Path,
    uriHandler: UriHandler,
    customQuit: (() -> Boolean)?,
) {
    val libraryFolderPath = Path.of(libraryFolder.toString())
    val logFolderPath = Path.of(logFolder.toString())
    val application = initializeJvmApplication(
        identifier = identifier,
        openUrls = openUrls,
        libraryFolderPath = libraryFolderPath,
        logFolderPath = logFolderPath,
        uriHandler = uriHandler,
        customQuit = customQuit,
    )
    activateApplication(application)
}

suspend fun awaitApplication(
    content: @Composable ApplicationScope.() -> Unit
) {
    withContext(MainUIDispatcher) {
        withContext(YieldFrameClock) {
            GlobalSnapshotManager.ensureStarted()

            val recomposer = Recomposer(coroutineContext)
            var isOpen by mutableStateOf(true)

            val applicationScope = object : ApplicationScope {
                override fun exitApplication() {
                    isOpen = false
                }
            }

            coroutineScope {
                val scene = Scene<Unit>(coroutineScope = this, {}, {})

                launch {
                    recomposer.runRecomposeAndApplyChanges()
                }

                launch {
                    val applier = ApplicationApplier()
                    val composition = Composition(applier, recomposer)
                    try {
                        composition.setContent {
                            CompositionLocalProvider(ProvidableLocalScene provides scene) {
                                if (isOpen) {
                                    applicationScope.content()
                                }
                            }
                        }
                        recomposer.close()
                        recomposer.join()
                    } finally {
                        composition.dispose()
                    }
                }
            }
        }
    }
}

internal actual fun currentApplication(): Application = currentJvmApplication()

internal actual fun defaultUriHandler(): UriHandler =
    when (DesktopPlatform.Current) {
        DesktopPlatform.MacOS -> MacOsUriHandler()
        DesktopPlatform.Linux -> when (currentLinuxWindowSystem()) {
            LinuxWindowSystem.Wayland -> LinuxUriHandler()
            LinuxWindowSystem.Gtk -> GtkUriHandler()
        }
//        DesktopPlatform.Windows -> WindowsUriHandler()
        DesktopPlatform.Windows -> TODO()
        DesktopPlatform.Unknown -> error("Unsupported desktop platform: ${DesktopPlatform.Current}")
    }

internal fun defaultLibraryFolder(): kotlinx.io.files.Path = kotlinx.io.files.Path(
    checkNotNull(System.getProperty("kdt.library.folder.path")) {
        "Please specify a path that points to the KDT library binaries as the kdt.library.folder.path property"
    },
)

internal fun defaultLogFolder(): kotlinx.io.files.Path = kotlinx.io.files.Path(
    checkNotNull(System.getProperty("kdt.native.log.path")) {
        "Please specify a path for KDT log files as the kdt.native.log.path property"
    },
)

internal actual fun activateApplication(application: Application) {
    JvmApplicationRegistry.activate(application)
}

internal actual fun deactivateApplication(application: Application) {
    JvmApplicationRegistry.deactivate(application)
}

internal actual fun removeApplication(application: Application) {
    JvmApplicationRegistry.remove(application)
}

private fun initializeJvmApplication(
    identifier: String,
    openUrls: (List<String>) -> Unit,
    libraryFolderPath: Path,
    logFolderPath: Path,
    uriHandler: UriHandler,
    customQuit: (() -> Boolean)?,
) : Application {
    return when (DesktopPlatform.Current) {
        DesktopPlatform.MacOS -> MacOsApplication.initialize(identifier, openUrls, libraryFolderPath, logFolderPath, uriHandler, customQuit).let { MacOsApplication.current() }
        DesktopPlatform.Linux -> when (currentLinuxWindowSystem()) {
            LinuxWindowSystem.Wayland -> {
                LinuxApplication.initialize(identifier, openUrls, libraryFolderPath, logFolderPath, uriHandler, customQuit)
                LinuxApplication.current()
            }
            LinuxWindowSystem.Gtk -> {
                GtkApplication.initialize(identifier, openUrls, libraryFolderPath, logFolderPath, uriHandler, customQuit)
                GtkApplication.current()
            }
        }
//        DesktopPlatform.Windows -> WindowsApplication.initialize(identifier, openUrls, libraryFolderPath, logFolderPath, uriHandler, customQuit).let { WindowsApplication.current() }
        DesktopPlatform.Windows -> TODO()
        DesktopPlatform.Unknown -> error("Unsupported desktop platform: ${DesktopPlatform.Current}")
    }
}

private fun currentJvmApplication(): Application = JvmApplicationRegistry.current()

private object JvmApplicationRegistry {
    private val lock = Any()
    private val retainedApplications = LinkedHashSet<Application>()
    private var activeApplication: Application? = null
    private var hookInstalled = false
    private var shutdownInProgress = false

    fun activate(application: Application) {
        synchronized(lock) {
            if (!hookInstalled) {
                installHook()
                hookInstalled = true
            }
            val currentActiveApplication = activeApplication
            check(currentActiveApplication == null || currentActiveApplication === application) {
                "Another Application is already active in this JVM process: ${currentActiveApplication?.javaClass?.name}"
            }
            retainedApplications.add(application)
            activeApplication = application
        }
    }

    fun deactivate(application: Application) {
        synchronized(lock) {
            if (activeApplication === application) {
                activeApplication = null
            }
        }
    }

    fun remove(application: Application) {
        synchronized(lock) {
            if (activeApplication === application) {
                activeApplication = null
            }
            retainedApplications.remove(application)
        }
    }

    fun current(): Application =
        synchronized(lock) {
            checkNotNull(activeApplication) { "No active Application has been initialized for this JVM process" }
        }

    private fun installHook() {
        Runtime.getRuntime().addShutdownHook(
            thread(start = false, name = "KDT application shutdown") {
                val applications =
                    synchronized(lock) {
                        if (shutdownInProgress) {
                            emptyList()
                        } else {
                            shutdownInProgress = true
                            retainedApplications.toList().asReversed()
                        }
                    }
                runBlocking {
                    applications.forEach { application ->
                        runCatching { application.stopAndJoin() }
                    }
                }
            },
        )
    }
}
