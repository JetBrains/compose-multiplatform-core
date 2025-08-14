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

package androidx.camera.integration.core

import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.HandlerThread
import androidx.camera.camera2.Camera2Config
import androidx.camera.camera2.pipe.integration.CameraPipeConfig
import androidx.camera.core.CameraIdentifier
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraXConfig
import androidx.camera.core.Preview
import androidx.camera.core.RetryPolicy
import androidx.camera.core.impl.CameraDeviceSurfaceManager
import androidx.camera.core.impl.CameraFactory
import androidx.camera.core.impl.CameraInternal
import androidx.camera.core.impl.CameraThreadConfig
import androidx.camera.core.impl.Observable
import androidx.camera.core.impl.UseCaseConfigFactory
import androidx.camera.core.internal.StreamSpecsCalculator
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.testing.impl.CameraPipeConfigTestRule
import androidx.camera.testing.impl.CameraUtil
import androidx.camera.testing.impl.SurfaceTextureProvider
import androidx.camera.testing.impl.WakelockEmptyActivityRule
import androidx.camera.testing.impl.fakes.FakeLifecycleOwner
import androidx.test.core.app.ApplicationProvider
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Device tests for verifying the options set in [CameraXConfig].
 *
 * This test uses a custom [CameraFactory.Provider] wrapper to intercept and verify the behavior of
 * different configurations.
 */
@LargeTest
@RunWith(Parameterized::class)
class CameraXConfigDeviceTest(private val implName: String, private val baseConfig: CameraXConfig) {
    @get:Rule
    val cameraPipeConfigTestRule =
        CameraPipeConfigTestRule(active = implName.contains(CameraPipeConfig::class.simpleName!!))

    @get:Rule
    val cameraRule =
        CameraUtil.grantCameraPermissionAndPreTestAndPostTest(
            CameraUtil.PreTestCameraIdList(baseConfig)
        )

    @get:Rule val wakelockEmptyActivityRule = WakelockEmptyActivityRule()

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "implName={0}")
        fun data() =
            mutableListOf<Array<Any?>>().apply {
                add(arrayOf(Camera2Config::class.simpleName, Camera2Config.defaultConfig()))
                add(arrayOf(CameraPipeConfig::class.simpleName, CameraPipeConfig.defaultConfig()))
            }
    }

    private val context: Context = ApplicationProvider.getApplicationContext()
    private var cameraProvider: ProcessCameraProvider? = null
    private lateinit var fakeLifecycleOwner: FakeLifecycleOwner

    @Before
    fun setUp() {
        fakeLifecycleOwner = FakeLifecycleOwner()
        fakeLifecycleOwner.startAndResume()
    }

    @After
    fun tearDown() {
        ProcessCameraProvider.shutdown().get(10, TimeUnit.SECONDS)
    }

    @Test
    fun availableCamerasLimiter_limitsAvailableCameras() {
        // Arrange
        assumeTrue(deviceHasFrontCamera())
        val limiter = CameraSelector.DEFAULT_FRONT_CAMERA
        val originalFactoryProvider = baseConfig.getCameraFactoryProvider(null)!!
        val factoryWrapper = FakeCameraFactoryWrapper(originalFactoryProvider)

        val customConfig =
            CameraXConfig.Builder.fromConfig(baseConfig)
                .setAvailableCamerasLimiter(limiter)
                .setCameraFactoryProvider(factoryWrapper)
                .build()

        // Act
        initializeProviderWithConfig(customConfig)

        // Assert
        val availableCameras = cameraProvider!!.availableCameraInfos
        assertThat(availableCameras.isNotEmpty()).isTrue()
        availableCameras.forEach {
            assertThat(it.lensFacing).isEqualTo(CameraSelector.LENS_FACING_FRONT)
        }
    }

    @Test
    fun customExecutorAndScheduler_canBindUseCase() {
        // Arrange
        val handlerThread = HandlerThread("CustomScheduler").apply { start() }
        val customSchedulerHandler = Handler(handlerThread.looper)
        val customCameraExecutor = Executors.newSingleThreadExecutor()
        val customConfig =
            CameraXConfig.Builder.fromConfig(baseConfig)
                .setCameraExecutor(customCameraExecutor)
                .setSchedulerHandler(customSchedulerHandler)
                .build()

        // Act
        initializeProviderWithConfig(customConfig)

        // Assert
        bindPreviewAndVerify()

        // Cleanup
        cameraProvider?.shutdownAsync()?.get(10, TimeUnit.SECONDS)
        handlerThread.quitSafely()
    }

    @Test
    fun retryPolicy_succeedsWhenCamerasBecomeAvailable() {
        // Arrange: Check if device is expected to have front/back cameras.
        assumeTrue(
            "Device must report FEATURE_CAMERA and FEATURE_CAMERA_FRONT",
            deviceHasFrontAndBackCameras(),
        )

        val allCameraIds = CameraUtil.getBackwardCompatibleCameraIdListOrThrow().toSet()
        val retryAttemptToSucceed = 2
        val factoryWrapper =
            FakeCameraFactoryWrapper(
                baseConfig.getCameraFactoryProvider(null)!!,
                // Start with an initial state of NO cameras available.
                initialVisibleIds = emptySet(),
            )

        // Arrange: A policy that "fixes" the camera availability on the 2nd retry attempt.
        val customPolicy = RetryPolicy { executionState ->
            if (executionState.numOfAttempts >= retryAttemptToSucceed) {
                // On the 2nd attempt, restore all cameras to the controllable factory.
                factoryWrapper.controllableFactory?.setVisibleCameraIds(allCameraIds)
            }
            // Always ask to retry until it succeeds.
            RetryPolicy.RetryConfig.DEFAULT_DELAY_RETRY
        }

        val customConfig =
            CameraXConfig.Builder.fromConfig(baseConfig)
                .setCameraProviderInitRetryPolicy(customPolicy)
                .setCameraFactoryProvider(factoryWrapper)
                .build()

        // Act: Initialize. This will hang and retry until our policy "fixes" the camera list.
        initializeProviderWithConfig(customConfig)

        // Assert: Initialization succeeded and all cameras are now available.
        assertThat(cameraProvider).isNotNull()
        assertThat(cameraProvider!!.availableCameraInfos.size).isEqualTo(allCameraIds.size)
        assertThat(cameraProvider!!.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)).isTrue()
        assertThat(cameraProvider!!.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)).isTrue()
    }

    @Test
    fun customCameraFactoryProvider_isUsedByCameraX() {
        // Arrange: Create a verifiable provider that delegates to the real one.
        val originalFactoryProvider = baseConfig.getCameraFactoryProvider(null)!!
        val verifiableProvider = VerifiableCameraFactoryProvider(originalFactoryProvider)

        val customConfig =
            CameraXConfig.Builder.fromConfig(baseConfig)
                .setCameraFactoryProvider(verifiableProvider)
                .build()

        // Act: Initialize CameraX with the custom provider.
        initializeProviderWithConfig(customConfig)

        // Assert: The custom provider's newInstance method was called.
        assertThat(verifiableProvider.isNewInstanceCalled).isTrue()

        // Assert: CameraX still initialized correctly.
        assumeTrue(deviceHasBackCamera())
        assertThat(cameraProvider!!.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)).isTrue()
    }

    @Test
    fun customSurfaceManagerProvider_isUsedByCameraX() {
        // Arrange: Create a verifiable provider that delegates to the real one.
        val originalProvider = baseConfig.getDeviceSurfaceManagerProvider(null)!!
        val verifiableProvider = VerifiableSurfaceManagerProvider(originalProvider)

        val customConfig =
            CameraXConfig.Builder.fromConfig(baseConfig)
                .setDeviceSurfaceManagerProvider(verifiableProvider)
                .build()

        // Act: Initialize CameraX with the custom provider.
        initializeProviderWithConfig(customConfig)

        // Assert: The custom provider's newInstance method was called.
        assertThat(verifiableProvider.isNewInstanceCalled).isTrue()

        // Assert: CameraX still initialized correctly.
        assumeTrue(deviceHasBackCamera())
        assertThat(cameraProvider!!.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)).isTrue()
    }

    @Test
    fun customUseCaseConfigFactoryProvider_isUsedByCameraX() {
        // Arrange: Create a verifiable provider that delegates to the real one.
        val originalProvider = baseConfig.getUseCaseConfigFactoryProvider(null)!!
        val verifiableProvider = VerifiableUseCaseConfigFactoryProvider(originalProvider)

        val customConfig =
            CameraXConfig.Builder.fromConfig(baseConfig)
                .setUseCaseConfigFactoryProvider(verifiableProvider)
                .build()

        // Act: Initialize CameraX with the custom provider.
        initializeProviderWithConfig(customConfig)

        // Assert: The custom provider's newInstance method was called.
        assertThat(verifiableProvider.isNewInstanceCalled).isTrue()

        // Assert: CameraX still initialized correctly.
        assumeTrue(deviceHasBackCamera())
        assertThat(cameraProvider!!.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)).isTrue()
    }

    private fun initializeProviderWithConfig(config: CameraXConfig) {
        ProcessCameraProvider.configureInstance(config)
        cameraProvider = ProcessCameraProvider.getInstance(context)[10, TimeUnit.SECONDS]
    }

    private fun bindPreviewAndVerify() {
        assumeTrue(deviceHasBackCamera())
        val preview = Preview.Builder().build()
        val previewMonitor = PreviewMonitor()

        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            preview.surfaceProvider = previewMonitor.getSurfaceProvider()
            cameraProvider!!.bindToLifecycle(
                fakeLifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
            )
        }
        previewMonitor.waitForStream()
    }

    private fun deviceHasFrontCamera(): Boolean {
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT)
    }

    private fun deviceHasBackCamera(): Boolean {
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA)
    }

    private fun deviceHasFrontAndBackCameras(): Boolean {
        return deviceHasFrontCamera() && deviceHasBackCamera()
    }

    /**
     * A [CameraFactory.Provider] that creates a [ControllableCameraFactory].
     *
     * @param delegate The actual [CameraFactory.Provider] to be wrapped.
     * @param initialVisibleIds The set of camera IDs that the created factory should expose
     *   initially. If null, all cameras from the delegate are visible.
     */
    class FakeCameraFactoryWrapper(
        private val delegate: CameraFactory.Provider,
        private val initialVisibleIds: Set<String>? = null,
    ) : CameraFactory.Provider {

        private var cachedControllableFactory: ControllableCameraFactory? = null
        private val lock = Any()

        val controllableFactory: ControllableCameraFactory?
            get() = cachedControllableFactory

        override fun newInstance(
            context: Context,
            threadConfig: CameraThreadConfig,
            availableCamerasLimiter: CameraSelector?,
            cameraOpenRetryMaxTimeoutInMs: Long,
            cameraXConfig: CameraXConfig?,
            streamSpecsCalculator: StreamSpecsCalculator,
        ): CameraFactory {
            synchronized(lock) {
                // If we have already created and cached an instance, return it immediately.
                cachedControllableFactory?.let {
                    return it
                }

                // Otherwise, create it for the first time.
                val realFactory =
                    delegate.newInstance(
                        context,
                        threadConfig,
                        availableCamerasLimiter,
                        cameraOpenRetryMaxTimeoutInMs,
                        cameraXConfig,
                        streamSpecsCalculator,
                    )
                val initialIds = initialVisibleIds ?: realFactory.availableCameraIds
                val newFactory = ControllableCameraFactory(realFactory, initialIds)

                // Cache it before returning.
                cachedControllableFactory = newFactory
                return newFactory
            }
        }
    }

    /**
     * A CameraFactory wrapper that allows dynamically controlling which cameras are visible.
     *
     * @param delegate The real CameraFactory instance.
     * @param initialVisibleIds The initial set of camera IDs this factory will report.
     */
    class ControllableCameraFactory(
        private val delegate: CameraFactory,
        initialVisibleIds: Set<String>,
    ) : CameraFactory by delegate {
        private val cameraPresenceSource =
            FakeObservable(initialVisibleIds.map { CameraIdentifier.create(it) })

        @Volatile private var visibleCameraIds: Set<String> = initialVisibleIds

        fun setVisibleCameraIds(ids: Set<String>) {
            visibleCameraIds = ids
            // When the visible cameras change, push an update through our fake observable.
            cameraPresenceSource.setValue(ids.map { CameraIdentifier.create(it) })
        }

        override fun getAvailableCameraIds(): Set<String> = visibleCameraIds

        override fun getCamera(cameraId: String): CameraInternal {
            if (!visibleCameraIds.contains(cameraId)) {
                throw IllegalArgumentException("Camera $cameraId is not in the visible set.")
            }
            return delegate.getCamera(cameraId)
        }

        override fun getCameraPresenceSource(): Observable<List<CameraIdentifier>> {
            return cameraPresenceSource
        }

        override fun shutdown() {
            delegate.shutdown()
        }
    }

    /**
     * A wrapper for a CameraFactory.Provider that records whether its newInstance method has been
     * called.
     *
     * @param delegate The actual [CameraFactory.Provider] to delegate to after recording the call.
     */
    private class VerifiableCameraFactoryProvider(private val delegate: CameraFactory.Provider) :
        CameraFactory.Provider {

        @Volatile
        var isNewInstanceCalled = false
            private set

        override fun newInstance(
            context: Context,
            threadConfig: CameraThreadConfig,
            availableCamerasLimiter: CameraSelector?,
            cameraOpenRetryMaxTimeoutInMs: Long,
            cameraXConfig: CameraXConfig?,
            streamSpecsCalculator: StreamSpecsCalculator,
        ): CameraFactory {
            // Record that this custom provider was used.
            isNewInstanceCalled = true
            // Delegate to the original provider to ensure CameraX can initialize correctly.
            return delegate.newInstance(
                context,
                threadConfig,
                availableCamerasLimiter,
                cameraOpenRetryMaxTimeoutInMs,
                cameraXConfig,
                streamSpecsCalculator,
            )
        }
    }

    /**
     * A wrapper for a CameraDeviceSurfaceManager.Provider that records whether its newInstance
     * method has been called.
     */
    private class VerifiableSurfaceManagerProvider(
        private val delegate: CameraDeviceSurfaceManager.Provider
    ) : CameraDeviceSurfaceManager.Provider {

        @Volatile
        var isNewInstanceCalled = false
            private set

        override fun newInstance(
            context: Context,
            cameraManager: Any?,
            availableCameraIds: Set<String>,
        ): CameraDeviceSurfaceManager {
            isNewInstanceCalled = true
            return delegate.newInstance(context, cameraManager, availableCameraIds)
        }
    }

    /**
     * A wrapper for a UseCaseConfigFactory.Provider that records whether its newInstance method has
     * been called.
     */
    private class VerifiableUseCaseConfigFactoryProvider(
        private val delegate: UseCaseConfigFactory.Provider
    ) : UseCaseConfigFactory.Provider {

        @Volatile
        var isNewInstanceCalled = false
            private set

        override fun newInstance(context: Context): UseCaseConfigFactory {
            isNewInstanceCalled = true
            return delegate.newInstance(context)
        }
    }

    private class FakeObservable<T>(initialValue: T) : Observable<T> {
        private val observers = CopyOnWriteArrayList<Observable.Observer<T>>()
        private var value: T = initialValue

        fun setValue(newValue: T) {
            value = newValue
            for (observer in observers) {
                observer.onNewData(value)
            }
        }

        override fun fetchData(): ListenableFuture<T> {
            for (observer in observers) {
                observer.onNewData(value)
            }
            return Futures.immediateFuture<T>(value)
        }

        override fun addObserver(executor: Executor, observer: Observable.Observer<in T>) {
            observers.add(observer as Observable.Observer<T>)
            executor.execute { observer.onNewData(value) }
        }

        override fun removeObserver(observer: Observable.Observer<in T>) {
            observers.remove(observer as Observable.Observer<T>)
        }
    }

    /** A simple monitor to verify that preview frames are being produced. */
    class PreviewMonitor {
        private val frameReceivedSemaphore = Semaphore(0)
        private val surfaceProvider =
            SurfaceTextureProvider.createAutoDrainingSurfaceTextureProvider {
                frameReceivedSemaphore.release()
            }

        fun getSurfaceProvider(): Preview.SurfaceProvider = surfaceProvider

        fun waitForStream(timeoutSeconds: Long = 5) {
            assertThat(frameReceivedSemaphore.tryAcquire(5, timeoutSeconds, TimeUnit.SECONDS))
                .isTrue()
        }
    }
}
