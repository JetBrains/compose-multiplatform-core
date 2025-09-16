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
package androidx.camera.core.impl

import android.os.Looper
import androidx.camera.core.CameraIdentifier
import androidx.camera.core.CameraPresenceListener
import androidx.camera.core.CameraSelector
import androidx.camera.testing.fakes.FakeCamera
import androidx.camera.testing.fakes.FakeCameraInfoInternal
import androidx.camera.testing.impl.fakes.FakeCameraCoordinator
import androidx.camera.testing.impl.fakes.FakeCameraDeviceSurfaceManager
import androidx.camera.testing.impl.fakes.FakeCameraFactory
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import java.util.concurrent.Executor
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.internal.DoNotInstrument

@RunWith(RobolectricTestRunner::class)
@DoNotInstrument
class CameraPresenceProviderTest {

    private val sourceObservable = MutableObservable<List<CameraIdentifier>>()
    private val fakeCameraFactory = FakeCameraFactory()
    private lateinit var testCameraRepository: TestCameraRepository
    private val fakeCoordinator = FakeCameraCoordinator()
    private val fakeSurfaceManager = FakeCameraDeviceSurfaceManager()
    private val fakeValidator = FakeCameraValidator()
    private val publicListener = TestCameraPresenceListener()

    private lateinit var provider: CameraPresenceProvider

    @Before
    fun setUp() {
        // Setup the factory to provide the test's controllable observable
        fakeCameraFactory.cameraPresenceSource = sourceObservable

        // Create the TestCameraRepository wrapper for testing
        testCameraRepository = TestCameraRepository(fakeCameraFactory)

        provider = CameraPresenceProvider(MoreExecutors.directExecutor())
        provider.startup(fakeValidator, fakeCameraFactory, testCameraRepository)
        provider.addDependentInternalListener(fakeSurfaceManager)
        provider.addDependentInternalListener(fakeCoordinator)
        provider.addCameraPresenceListener(publicListener, MoreExecutors.directExecutor())
    }

    @Test
    fun onNewData_abortsUpdate_whenValidatorReturnsInvalid() {
        // Arrange: Start with one camera.
        fakeCameraFactory.insertCamera(CameraSelector.LENS_FACING_BACK, CAMERA_ID_0) {
            FakeCamera(CAMERA_ID_0, null, FakeCameraInfoInternal())
        }
        sourceObservable.updateData(listOf(IDENTIFIER_0))

        // Sanity check initial state.
        assertThat(testCameraRepository.updateCount).isEqualTo(1)
        assertThat(testCameraRepository.lastReceivedIds).containsExactly(CAMERA_ID_0)

        // Act: Configure validator to fail the next update.
        fakeValidator.setNextIsChangeInvalid(true)
        // Attempt to remove the camera.
        sourceObservable.updateData(emptyList())

        // Assert: The update should have been aborted.
        // The repository update count remains 1 because the invalid update was blocked.
        assertThat(testCameraRepository.updateCount).isEqualTo(1)
        // The repository state should not have changed.
        assertThat(testCameraRepository.lastReceivedIds).containsExactly(CAMERA_ID_0)
        // The public listener should not be notified of any removal.
        assertThat(publicListener.removedCameras).isEmpty()
    }

    @Test
    fun onNewData_allowsNonImpactfulRemoval_fromDegradedState() {
        // Arrange: Start in a degraded state with a required BACK camera and an EXTERNAL camera,
        // but missing the required FRONT camera.
        fakeCameraFactory.insertCamera(CameraSelector.LENS_FACING_BACK, CAMERA_ID_0) {
            FakeCamera(
                CAMERA_ID_0,
                null,
                FakeCameraInfoInternal(CAMERA_ID_0, 0, CameraSelector.LENS_FACING_BACK),
            )
        }
        fakeCameraFactory.insertCamera(CameraSelector.LENS_FACING_EXTERNAL, CAMERA_ID_EXTERNAL) {
            FakeCamera(
                CAMERA_ID_EXTERNAL,
                null,
                FakeCameraInfoInternal(CAMERA_ID_EXTERNAL, 0, CameraSelector.LENS_FACING_EXTERNAL),
            )
        }
        sourceObservable.updateData(listOf(IDENTIFIER_0, IDENTIFIER_EXTERNAL))
        assertThat(testCameraRepository.updateCount).isEqualTo(1)

        // Act: Remove the non-essential EXTERNAL camera. The validator will allow this
        // by default (isChangeInvalid returns false).
        fakeCameraFactory.removeCamera(CAMERA_ID_EXTERNAL)
        sourceObservable.updateData(listOf(IDENTIFIER_0))

        // Assert: The update proceeds because it doesn't make the state worse.
        assertThat(testCameraRepository.updateCount).isEqualTo(2)
        assertThat(testCameraRepository.lastReceivedIds).containsExactly(CAMERA_ID_0)
        assertThat(publicListener.removedCameras).containsExactly(IDENTIFIER_EXTERNAL)
    }

    @Test
    fun onNewData_blocksImpactfulRemoval_fromDegradedState() {
        // Arrange: Start in a degraded state with a required BACK camera and an EXTERNAL camera.
        fakeCameraFactory.insertCamera(CameraSelector.LENS_FACING_BACK, CAMERA_ID_0) {
            FakeCamera(
                CAMERA_ID_0,
                null,
                FakeCameraInfoInternal(CAMERA_ID_0, 0, CameraSelector.LENS_FACING_BACK),
            )
        }
        fakeCameraFactory.insertCamera(CameraSelector.LENS_FACING_EXTERNAL, CAMERA_ID_EXTERNAL) {
            FakeCamera(
                CAMERA_ID_EXTERNAL,
                null,
                FakeCameraInfoInternal(CAMERA_ID_EXTERNAL, 0, CameraSelector.LENS_FACING_EXTERNAL),
            )
        }
        sourceObservable.updateData(listOf(IDENTIFIER_0, IDENTIFIER_EXTERNAL))
        assertThat(testCameraRepository.updateCount).isEqualTo(1)

        // Act: Configure the validator to block the next removal, then attempt to remove the
        // last required BACK camera.
        fakeValidator.setNextIsChangeInvalid(true)
        sourceObservable.updateData(listOf(IDENTIFIER_EXTERNAL))

        // Assert: The update is blocked to prevent losing the last required camera.
        assertThat(testCameraRepository.updateCount).isEqualTo(1)
        assertThat(testCameraRepository.lastReceivedIds)
            .containsExactly(CAMERA_ID_0, CAMERA_ID_EXTERNAL)
        assertThat(publicListener.removedCameras).isEmpty()
    }

    @Test
    fun startup_notifiesPublicListenerOfInitialCameras() {
        // Arrange: Initial state in factory
        fakeCameraFactory.insertCamera(CameraSelector.LENS_FACING_BACK, CAMERA_ID_0) {
            FakeCamera(CAMERA_ID_0, null, FakeCameraInfoInternal())
        }

        // Act: Push initial state from the source observable
        sourceObservable.updateData(listOf(IDENTIFIER_0))

        // Assert: The public listener gets the initial set
        assertThat(publicListener.addedCameras).containsExactly(IDENTIFIER_0)
    }

    @Test
    fun onNewData_updatesAllListeners() {
        // Arrange
        fakeCameraFactory.insertCamera(CameraSelector.LENS_FACING_BACK, CAMERA_ID_0) {
            FakeCamera(CAMERA_ID_0, null, FakeCameraInfoInternal())
        }
        // Act
        sourceObservable.updateData(listOf(IDENTIFIER_0))

        // Assert
        assertThat(testCameraRepository.updateCount).isEqualTo(1)
        assertThat(fakeSurfaceManager.cameraUpdateCount).isEqualTo(1)
        assertThat(fakeCoordinator.cameraUpdateCount).isEqualTo(1)
        assertThat(testCameraRepository.lastReceivedIds).containsExactly(CAMERA_ID_0)
        assertThat(publicListener.addedCameras).containsExactly(IDENTIFIER_0)
    }

    @Test
    fun onNewData_handlesRemoval() {
        // Arrange: Start with two cameras
        fakeCameraFactory.insertCamera(CameraSelector.LENS_FACING_BACK, CAMERA_ID_0) {
            FakeCamera(CAMERA_ID_0, null, FakeCameraInfoInternal())
        }
        fakeCameraFactory.insertCamera(CameraSelector.LENS_FACING_FRONT, CAMERA_ID_1) {
            FakeCamera(CAMERA_ID_1, null, FakeCameraInfoInternal())
        }
        sourceObservable.updateData(listOf(IDENTIFIER_0, IDENTIFIER_1))
        publicListener.addedCameras.clear() // Clear state for next assertion

        // Act: Push an update where one camera is removed
        fakeCameraFactory.removeCamera(CAMERA_ID_0)
        sourceObservable.updateData(listOf(IDENTIFIER_1))

        // Assert
        assertThat(testCameraRepository.lastReceivedIds).containsExactly(CAMERA_ID_1)
        assertThat(publicListener.removedCameras).containsExactly(IDENTIFIER_0)
        assertThat(publicListener.addedCameras).isEmpty()
    }

    @Test
    fun transactionFailsAndRollsBack_whenRepositoryFails() {
        // Arrange
        fakeCameraFactory.insertCamera(CameraSelector.LENS_FACING_BACK, CAMERA_ID_0) {
            FakeCamera(CAMERA_ID_0, null, FakeCameraInfoInternal())
        }
        testCameraRepository.setShouldThrow(true)

        // Act
        sourceObservable.updateData(listOf(IDENTIFIER_0))

        // Assert
        assertThat(testCameraRepository.updateCount).isEqualTo(1) // Attempted once
        assertThat(fakeSurfaceManager.cameraUpdateCount).isEqualTo(0) // Never called
        assertThat(fakeCoordinator.cameraUpdateCount).isEqualTo(0) // Never called
        assertThat(publicListener.addedCameras).isEmpty()
    }

    @Test
    fun transactionFailsAndRollsBack_whenDependentListenerFails() {
        // Arrange: Start with camera 0.
        fakeCameraFactory.insertCamera(CameraSelector.LENS_FACING_BACK, CAMERA_ID_0) {
            FakeCamera(CAMERA_ID_0, null, FakeCameraInfoInternal())
        }
        sourceObservable.updateData(listOf(IDENTIFIER_0))
        // Configure coordinator to fail on the next update.
        fakeCoordinator.setCamerasUpdateShouldThrow(true)

        // Act: Attempt to update to a new set of cameras
        fakeCameraFactory.insertCamera(CameraSelector.LENS_FACING_FRONT, CAMERA_ID_1) {
            FakeCamera(CAMERA_ID_1, null, FakeCameraInfoInternal())
        }
        sourceObservable.updateData(listOf(IDENTIFIER_0, IDENTIFIER_1))

        // Assert:
        // Repository was updated (init), updated again, then rolled back.
        assertThat(testCameraRepository.updateCount).isEqualTo(3)
        // SurfaceManager was updated (init), updated again, then rolled back.
        assertThat(fakeSurfaceManager.cameraUpdateCount).isEqualTo(3)
        // Coordinator was attempted for the first update, then the failing update.
        assertThat(fakeCoordinator.cameraUpdateCount).isEqualTo(2)

        // Assert: The repository's final state is the old state.
        assertThat(testCameraRepository.lastReceivedIds).containsExactly(CAMERA_ID_0)
    }

    @Test
    fun removeCameraPresenceListener_stopsReceivingUpdates() {
        // Arrange
        val listenerToRemove = TestCameraPresenceListener()
        provider.addCameraPresenceListener(listenerToRemove, MoreExecutors.directExecutor())

        // Act & Assert: First update is received.
        fakeCameraFactory.insertCamera(CameraSelector.LENS_FACING_BACK, CAMERA_ID_0) {
            FakeCamera(CAMERA_ID_0, null, FakeCameraInfoInternal())
        }
        sourceObservable.updateData(listOf(IDENTIFIER_0))
        assertThat(listenerToRemove.addedCameras).containsExactly(IDENTIFIER_0)

        // Act: Remove the listener.
        provider.removeCameraPresenceListener(listenerToRemove)
        listenerToRemove.addedCameras.clear() // Reset for assertion

        // Act: Push a second update.
        fakeCameraFactory.insertCamera(CameraSelector.LENS_FACING_FRONT, CAMERA_ID_1) {
            FakeCamera(CAMERA_ID_1, null, FakeCameraInfoInternal())
        }
        sourceObservable.updateData(listOf(IDENTIFIER_0, IDENTIFIER_1))

        // Assert: The removed listener should not have received the second update.
        assertThat(listenerToRemove.addedCameras).isEmpty()
        // A different, still-active listener should have received it.
        assertThat(publicListener.addedCameras).containsExactly(IDENTIFIER_1)
    }

    @Test
    fun addCameraPresenceListener_isNotNotified_whenNoInitialCameras() {
        // Arrange: Initial camera list is empty.
        sourceObservable.updateData(emptyList())

        // Act
        val newListener = TestCameraPresenceListener()
        provider.addCameraPresenceListener(newListener, MoreExecutors.directExecutor())

        // Assert: The new listener is not called back because the initial list is empty.
        assertThat(newListener.addedCameras).isEmpty()
    }

    @Test
    fun shutdown_clearsResourcesAndStopsMonitoring() {
        // Arrange: Process one update successfully.
        fakeCameraFactory.insertCamera(CameraSelector.LENS_FACING_BACK, CAMERA_ID_0) {
            FakeCamera(CAMERA_ID_0, null, FakeCameraInfoInternal())
        }
        sourceObservable.updateData(listOf(IDENTIFIER_0))
        assertThat(testCameraRepository.updateCount).isEqualTo(1)
        assertThat(publicListener.addedCameras).hasSize(1)

        // Act: Shutdown the provider.
        provider.shutdown()
        // Executes any pending tasks on the main looper, including LiveData observers.
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        // Act: Push another update after shutdown.
        sourceObservable.updateData(listOf(IDENTIFIER_0, IDENTIFIER_1))

        // Assert: No new updates should have been processed. The counters remain the same.
        assertThat(testCameraRepository.updateCount).isEqualTo(1)
        assertThat(publicListener.addedCameras).hasSize(1) // Still contains only IDENTIFIER_0
    }

    @Test
    fun noOpUpdate_doesNotNotifyListeners() {
        // Arrange: Process an initial update.
        fakeCameraFactory.insertCamera(CameraSelector.LENS_FACING_BACK, CAMERA_ID_0) {
            FakeCamera(CAMERA_ID_0, null, FakeCameraInfoInternal())
        }
        sourceObservable.updateData(listOf(IDENTIFIER_0))
        assertThat(testCameraRepository.updateCount).isEqualTo(1)
        assertThat(publicListener.addedCameras).hasSize(1)
        publicListener.addedCameras.clear() // Reset for assertion

        // Act: Push the exact same camera list again.
        sourceObservable.updateData(listOf(IDENTIFIER_0))

        // Assert: No listeners should have been called again for a no-op update.
        assertThat(testCameraRepository.updateCount).isEqualTo(1)
        assertThat(publicListener.addedCameras).isEmpty()
    }

    private companion object {
        private const val CAMERA_ID_0 = "0"
        private const val CAMERA_ID_1 = "1"
        private const val CAMERA_ID_EXTERNAL = "2"
        private val IDENTIFIER_0 = CameraIdentifier.create(CAMERA_ID_0)
        private val IDENTIFIER_1 = CameraIdentifier.create(CAMERA_ID_1)
        private val IDENTIFIER_EXTERNAL = CameraIdentifier.create(CAMERA_ID_EXTERNAL)
    }

    private class MutableObservable<T> : Observable<T> {
        private var observer: Observable.Observer<T>? = null
        private var executor: Executor? = null

        fun updateData(data: T) {
            executor?.execute { observer?.onNewData(data) }
        }

        override fun fetchData(): ListenableFuture<T> =
            Futures.immediateFailedFuture(RuntimeException("Not implemented"))

        override fun addObserver(executor: Executor, observer: Observable.Observer<in T>) {
            this.executor = executor
            @Suppress("UNCHECKED_CAST")
            this.observer = observer as Observable.Observer<T>
        }

        override fun removeObserver(observer: Observable.Observer<in T>) {
            this.observer = null
            this.executor = null
        }
    }

    private class TestCameraPresenceListener : CameraPresenceListener {
        val addedCameras = mutableSetOf<CameraIdentifier>()
        val removedCameras = mutableSetOf<CameraIdentifier>()

        override fun onCamerasAdded(cameraIdentifiers: Set<CameraIdentifier>) {
            addedCameras.clear()
            addedCameras.addAll(cameraIdentifiers)
        }

        override fun onCamerasRemoved(cameraIdentifiers: Set<CameraIdentifier>) {
            removedCameras.clear()
            removedCameras.addAll(cameraIdentifiers)
        }
    }

    /** A test wrapper around a real CameraRepository to track calls and inject faults. */
    private class TestCameraRepository(factory: CameraFactory) : CameraRepository() {
        var updateCount = 0
        var lastReceivedIds: List<String>? = null
        private var shouldThrow = false

        init {
            init(factory)
        }

        override fun onCamerasUpdated(cameraIds: List<String>) {
            updateCount++
            lastReceivedIds = cameraIds
            if (shouldThrow) {
                throw CameraUpdateException("Test failure")
            }
            super.onCamerasUpdated(cameraIds)
        }

        fun setShouldThrow(shouldThrow: Boolean) {
            this.shouldThrow = shouldThrow
        }
    }

    /** A fake implementation of CameraValidator for testing purposes. */
    private class FakeCameraValidator : CameraValidator {
        private var nextIsChangeInvalid = false

        fun setNextIsChangeInvalid(isInvalid: Boolean) {
            nextIsChangeInvalid = isInvalid
        }

        override fun validateOnFirstInit(cameraRepository: CameraRepository) {
            // No-op for these tests, as we are not testing initial validation here.
        }

        override fun isChangeInvalid(
            currentCameras: Set<CameraInternal>,
            removedCameras: Set<CameraIdentifier>,
        ): Boolean {
            val result = nextIsChangeInvalid
            // Reset after use so the next check defaults to valid.
            nextIsChangeInvalid = false
            return result
        }
    }
}
