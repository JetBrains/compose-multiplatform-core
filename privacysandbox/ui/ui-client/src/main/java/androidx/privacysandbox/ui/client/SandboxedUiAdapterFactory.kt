/*
 * Copyright 2022 The Android Open Source Project
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

package androidx.privacysandbox.ui.client

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Bundle
import android.view.Display
import android.view.SurfaceControlViewHost
import android.view.View
import android.window.SurfaceSyncGroup
import androidx.annotation.RequiresApi
import androidx.privacysandbox.ui.client.SandboxedUiAdapterFactory.LocalAdapter
import androidx.privacysandbox.ui.client.SandboxedUiAdapterFactory.RemoteAdapter
import androidx.privacysandbox.ui.core.ClientAdapterWrapper
import androidx.privacysandbox.ui.core.IDelegatingSandboxedUiAdapter
import androidx.privacysandbox.ui.core.IRemoteSessionClient
import androidx.privacysandbox.ui.core.IRemoteSessionController
import androidx.privacysandbox.ui.core.ISandboxedUiAdapter
import androidx.privacysandbox.ui.core.ProtocolConstants
import androidx.privacysandbox.ui.core.RemoteCallManager.addBinderDeathListener
import androidx.privacysandbox.ui.core.RemoteCallManager.closeRemoteSession
import androidx.privacysandbox.ui.core.RemoteCallManager.tryToCallRemoteObject
import androidx.privacysandbox.ui.core.SandboxedUiAdapter
import androidx.privacysandbox.ui.core.SessionData
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.concurrent.Executor

/**
 * Provides an adapter created from a supplied Bundle which acts as a proxy between the host app and
 * the Binder provided by the provider of content.
 */
object SandboxedUiAdapterFactory {

    // Bundle key is a binary compatibility requirement
    private const val UI_ADAPTER_BINDER = "uiAdapterBinder"

    private val uiAdapterFactoryDelegate =
        object : UiAdapterFactoryDelegate() {
            override val uiAdapterBinderKey: String = UI_ADAPTER_BINDER
            override val adapterDescriptor: String = ISandboxedUiAdapter.DESCRIPTOR
        }

    /**
     * @throws IllegalArgumentException if {@code coreLibInfo} does not contain a Binder with the
     *   key UI_ADAPTER_BINDER
     */
    fun createFromCoreLibInfo(coreLibInfo: Bundle): SandboxedUiAdapter {
        val uiAdapterBinder = uiAdapterFactoryDelegate.requireNotNullAdapterBinder(coreLibInfo)
        // the following check for DelegatingAdapter check must happen before the checks for
        // remote/local binder as the checks below have fallback to a RemoteAdapter if it's not
        // local.
        if (
            uiAdapterBinder.interfaceDescriptor?.equals(
                "androidx.privacysandbox.ui.core.IDelegatingSandboxedUiAdapter"
            ) == true
        ) {
            val delegate =
                coreLibInfo.getBundle(ProtocolConstants.delegateKey)
                    ?: throw UnsupportedOperationException(
                        "DelegatingAdapter must have a non null delegate"
                    )
            return ClientDelegatingAdapter(
                IDelegatingSandboxedUiAdapter.Stub.asInterface(uiAdapterBinder),
                createFromCoreLibInfo(delegate)
            )
        }

        return if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
                !uiAdapterFactoryDelegate.shouldUseLocalAdapter(coreLibInfo)
        ) {
            RemoteAdapter(coreLibInfo)
        } else {
            LocalAdapter(coreLibInfo)
        }
    }

    /**
     * [LocalAdapter] fetches UI from a provider living on same process as the client but on a
     * different class loader.
     */
    @SuppressLint("BanUncheckedReflection") // using reflection on library classes
    private class LocalAdapter(private val adapterBundle: Bundle) :
        SandboxedUiAdapter, ClientAdapterWrapper {

        val uiProviderBinder =
            requireNotNull(adapterBundle.getBinder(UI_ADAPTER_BINDER)) {
                "Invalid bundle, missing $UI_ADAPTER_BINDER."
            }

        private val targetSessionClientClass =
            Class.forName(
                "androidx.privacysandbox.ui.core.SandboxedUiAdapter\$SessionClient",
                /* initialize = */ false,
                uiProviderBinder.javaClass.classLoader
            )

        private val targetSessionDataClass =
            Class.forName(
                "androidx.privacysandbox.ui.core.SessionData",
                /* initialize = */ false,
                uiProviderBinder.javaClass.classLoader
            )

        private val targetSessionDataCompanionObject =
            targetSessionDataClass.getDeclaredField("Companion").get(null)

        // The adapterInterface provided must have a openSession method on its class.
        // Since the object itself has been instantiated on a different classloader, we
        // need reflection to get hold of it.
        private val openSessionMethod: Method =
            Class.forName(
                    "androidx.privacysandbox.ui.core.SandboxedUiAdapter",
                    /*initialize=*/ false,
                    uiProviderBinder.javaClass.classLoader
                )
                .getMethod(
                    "openSession",
                    Context::class.java,
                    targetSessionDataClass,
                    Int::class.java,
                    Int::class.java,
                    Boolean::class.java,
                    Executor::class.java,
                    targetSessionClientClass
                )

        private val fromBundleMethod: Method =
            targetSessionDataCompanionObject.javaClass.getMethod("fromBundle", Bundle::class.java)

        override fun openSession(
            context: Context,
            sessionData: SessionData,
            initialWidth: Int,
            initialHeight: Int,
            isZOrderOnTop: Boolean,
            clientExecutor: Executor,
            client: SandboxedUiAdapter.SessionClient
        ) {
            try {
                // We can't pass the client object as-is since it's been created on a different
                // classloader.
                val sessionClientProxy =
                    Proxy.newProxyInstance(
                        uiProviderBinder.javaClass.classLoader,
                        arrayOf(targetSessionClientClass),
                        SessionClientProxyHandler(client)
                    )
                openSessionMethod.invoke(
                    uiProviderBinder,
                    context,
                    fromBundleMethod.invoke(
                        targetSessionDataCompanionObject,
                        SessionData.toBundle(sessionData)
                    ),
                    initialWidth,
                    initialHeight,
                    isZOrderOnTop,
                    clientExecutor,
                    sessionClientProxy
                )
            } catch (exception: Throwable) {
                client.onSessionError(exception)
            }
        }

        override fun getSourceBundle(): Bundle {
            return adapterBundle
        }

        private class SessionClientProxyHandler(
            private val origClient: SandboxedUiAdapter.SessionClient,
        ) : InvocationHandler {

            override fun invoke(proxy: Any, method: Method, args: Array<Any>?): Any {
                return when (method.name) {
                    "onSessionOpened" -> {
                        // We have to forward the call to original client, but it won't
                        // recognize Session class on targetClassLoader. We need proxy for it
                        // on local ClassLoader.
                        args!! // This method will always have an argument, so safe to !!
                        origClient.onSessionOpened(SessionProxy(args[0]))
                    }
                    "onSessionError" -> {
                        args!! // This method will always have an argument, so safe to !!
                        val throwable = args[0] as Throwable
                        origClient.onSessionError(throwable)
                    }
                    "onResizeRequested" -> {
                        args!! // This method will always have an argument, so safe to !!
                        val width = args[0] as Int
                        val height = args[1] as Int
                        origClient.onResizeRequested(width, height)
                    }
                    "toString" -> origClient.toString()
                    "equals" -> proxy === args?.get(0)
                    "hashCode" -> hashCode()
                    else -> {
                        throw UnsupportedOperationException(
                            "Unexpected method call object:$proxy, method: $method, args: $args"
                        )
                    }
                }
            }
        }

        /** Create [SandboxedUiAdapter.Session] that proxies to [origSession] */
        private class SessionProxy(
            private val origSession: Any,
        ) : SandboxedUiAdapter.Session {

            private val targetClass =
                Class.forName(
                        "androidx.privacysandbox.ui.core.SandboxedUiAdapter\$Session",
                        /* initialize = */ false,
                        origSession.javaClass.classLoader
                    )
                    .also { it.cast(origSession) }

            private val getViewMethod = targetClass.getMethod("getView")
            private val notifyResizedMethod =
                targetClass.getMethod("notifyResized", Int::class.java, Int::class.java)
            private val getSignalOptionsMethod = targetClass.getMethod("getSignalOptions")
            private val notifyZOrderChangedMethod =
                targetClass.getMethod("notifyZOrderChanged", Boolean::class.java)
            private val notifyConfigurationChangedMethod =
                targetClass.getMethod("notifyConfigurationChanged", Configuration::class.java)
            private val notifyUiChangedMethod =
                targetClass.getMethod("notifyUiChanged", Bundle::class.java)
            private val notifySessionRenderedMethod =
                targetClass.getMethod("notifySessionRendered", Set::class.java)
            private val closeMethod = targetClass.getMethod("close")

            override val view: View
                get() = getViewMethod.invoke(origSession) as View

            override val signalOptions: Set<String>
                @Suppress("UNCHECKED_CAST") // using reflection on library classes
                get() = getSignalOptionsMethod.invoke(origSession) as Set<String>

            override fun notifyResized(width: Int, height: Int) {
                val parentView = view.parent as View
                view.layout(
                    parentView.paddingLeft,
                    parentView.paddingTop,
                    parentView.paddingLeft + width,
                    parentView.paddingTop + height
                )
                notifyResizedMethod.invoke(origSession, width, height)
            }

            override fun notifyZOrderChanged(isZOrderOnTop: Boolean) {
                notifyZOrderChangedMethod.invoke(origSession, isZOrderOnTop)
            }

            override fun notifyConfigurationChanged(configuration: Configuration) {
                notifyConfigurationChangedMethod.invoke(origSession, configuration)
            }

            override fun notifyUiChanged(uiContainerInfo: Bundle) {
                notifyUiChangedMethod.invoke(origSession, uiContainerInfo)
            }

            override fun notifySessionRendered(supportedSignalOptions: Set<String>) {
                notifySessionRenderedMethod.invoke(origSession, supportedSignalOptions)
            }

            override fun close() {
                closeMethod.invoke(origSession)
            }
        }
    }

    /** [RemoteAdapter] fetches content from a provider living on a different process. */
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private class RemoteAdapter(private val adapterBundle: Bundle) :
        SandboxedUiAdapter, ClientAdapterWrapper {

        val uiAdapterBinder =
            requireNotNull(adapterBundle.getBinder(UI_ADAPTER_BINDER)) {
                "Invalid bundle, missing $UI_ADAPTER_BINDER."
            }
        val adapterInterface: ISandboxedUiAdapter =
            ISandboxedUiAdapter.Stub.asInterface(uiAdapterBinder)

        override fun openSession(
            context: Context,
            sessionData: SessionData,
            initialWidth: Int,
            initialHeight: Int,
            isZOrderOnTop: Boolean,
            clientExecutor: Executor,
            client: SandboxedUiAdapter.SessionClient
        ) {
            val mDisplayManager =
                context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
            val displayId = mDisplayManager.getDisplay(Display.DEFAULT_DISPLAY).displayId

            tryToCallRemoteObject(adapterInterface) {
                this.openRemoteSession(
                    SessionData.toBundle(sessionData),
                    displayId,
                    initialWidth,
                    initialHeight,
                    isZOrderOnTop,
                    RemoteSessionClient(context, client, clientExecutor)
                )
            }
        }

        override fun getSourceBundle(): Bundle {
            return adapterBundle
        }

        class RemoteSessionClient(
            val context: Context,
            val client: SandboxedUiAdapter.SessionClient,
            val clientExecutor: Executor
        ) : IRemoteSessionClient.Stub() {

            lateinit var contentView: ContentView

            override fun onRemoteSessionOpened(
                surfacePackage: SurfaceControlViewHost.SurfacePackage,
                remoteSessionController: IRemoteSessionController,
                isZOrderOnTop: Boolean,
                signalOptions: List<String>
            ) {
                contentView = ContentView(context, remoteSessionController)
                contentView.setChildSurfacePackage(surfacePackage)
                contentView.setZOrderOnTop(isZOrderOnTop)
                contentView.addOnAttachStateChangeListener(
                    object : View.OnAttachStateChangeListener {

                        private var hasViewBeenPreviouslyAttached = false

                        override fun onViewAttachedToWindow(v: View) {
                            if (hasViewBeenPreviouslyAttached) {
                                tryToCallRemoteObject(remoteSessionController) {
                                    this.notifyFetchUiForSession()
                                }
                            } else {
                                hasViewBeenPreviouslyAttached = true
                            }
                        }

                        override fun onViewDetachedFromWindow(v: View) {}
                    }
                )

                clientExecutor.execute {
                    client.onSessionOpened(
                        SessionImpl(
                            contentView,
                            remoteSessionController,
                            surfacePackage,
                            signalOptions.toSet()
                        )
                    )
                }
                addBinderDeathListener(remoteSessionController) {
                    onRemoteSessionError("Remote process died")
                }
            }

            override fun onRemoteSessionError(errorString: String) {
                clientExecutor.execute { client.onSessionError(Throwable(errorString)) }
            }

            override fun onResizeRequested(width: Int, height: Int) {
                clientExecutor.execute { client.onResizeRequested(width, height) }
            }

            override fun onSessionUiFetched(surfacePackage: SurfaceControlViewHost.SurfacePackage) {
                contentView.setChildSurfacePackage(surfacePackage)
            }
        }

        private class SessionImpl(
            val contentView: ContentView,
            val remoteSessionController: IRemoteSessionController,
            val surfacePackage: SurfaceControlViewHost.SurfacePackage,
            override val signalOptions: Set<String>
        ) : SandboxedUiAdapter.Session {

            override val view: View = contentView

            override fun notifyConfigurationChanged(configuration: Configuration) {
                tryToCallRemoteObject(remoteSessionController) {
                    this.notifyConfigurationChanged(configuration)
                }
            }

            override fun notifyResized(width: Int, height: Int) {

                val parentView = contentView.parent as View

                val clientResizeRunnable = Runnable {
                    contentView.layout(
                        /* left = */ parentView.paddingLeft,
                        /* top = */ parentView.paddingTop,
                        /* right = */ parentView.paddingLeft + width,
                        /* bottom = */ parentView.paddingTop + height
                    )
                }

                val providerResizeRunnable = Runnable {
                    tryToCallRemoteObject(remoteSessionController) {
                        this.notifyResized(width, height)
                    }
                }

                val syncGroup = SurfaceSyncGroup("AppAndSdkViewsSurfaceSync")

                syncGroup.add(contentView.rootSurfaceControl, clientResizeRunnable)
                syncGroup.add(surfacePackage, providerResizeRunnable)
                syncGroup.markSyncReady()
            }

            override fun notifyZOrderChanged(isZOrderOnTop: Boolean) {
                contentView.setZOrderOnTop(isZOrderOnTop)
                tryToCallRemoteObject(remoteSessionController) {
                    this.notifyZOrderChanged(isZOrderOnTop)
                }
            }

            override fun notifyUiChanged(uiContainerInfo: Bundle) {
                tryToCallRemoteObject(remoteSessionController) {
                    this.notifyUiChanged(uiContainerInfo)
                }
            }

            override fun notifySessionRendered(supportedSignalOptions: Set<String>) {
                tryToCallRemoteObject(remoteSessionController) {
                    this.notifySessionRendered(supportedSignalOptions.toList())
                }
            }

            override fun close() {
                closeRemoteSession(remoteSessionController)
            }
        }
    }
}
