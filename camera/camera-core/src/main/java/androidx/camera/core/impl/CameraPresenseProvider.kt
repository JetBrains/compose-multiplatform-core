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

import androidx.annotation.GuardedBy
import androidx.camera.core.CameraIdentifier
import androidx.camera.core.CameraPresenceListener
import androidx.camera.core.CameraState
import androidx.camera.core.Logger
import androidx.camera.core.impl.annotation.ExecutedBy
import androidx.camera.core.impl.utils.executor.CameraXExecutors
import androidx.lifecycle.Observer
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Central provider that orchestrates camera presence updates across core components.
 *
 * This class ensures a strict, transactional update order:
 * 1. CameraFactory (receives raw IDs)
 * 2. CameraRepository (populates CameraInternal objects from filtered IDs)
 * 3. Other dependent listeners like CameraDeviceSurfaceManager and CameraCoordinator
 *
 * If any step fails, it orchestrates a rollback of all previously successful steps.
 */
public class CameraPresenceProvider(private val backgroundExecutor: Executor) {

    private val observerLock = Any()

    private var cameraFactory: CameraFactory? = null
    private var cameraRepository: CameraRepository? = null
    private var sourcePresenceObservable: Observable<List<CameraIdentifier>>? = null
    private val sourceObserver: SourceObservableObserver = SourceObservableObserver()

    @Volatile private var currentFilteredIds: List<CameraIdentifier> = emptyList()

    private val isMonitoring = AtomicBoolean(false)

    private val dependentInternalListeners = CopyOnWriteArrayList<InternalCameraPresenceListener>()
    private val publicApiListeners = CopyOnWriteArrayList<ListenerWrapper>()
    @GuardedBy("observerLock")
    private val cameraStateObservers = mutableMapOf<String, Observer<CameraState>>()

    private data class ListenerWrapper(
        val listener: CameraPresenceListener,
        val executor: Executor,
    )

    /**
     * Starts monitoring camera presence.
     *
     * @param cameraFactory The factory that provides the presence observable.
     * @param cameraRepository The repository to get camera instances from.
     */
    public fun startup(cameraFactory: CameraFactory, cameraRepository: CameraRepository) {
        if (!isMonitoring.compareAndSet(false, true)) {
            return
        }
        Logger.i(TAG, "Starting CameraPresenceProvider monitoring.")

        this.currentFilteredIds =
            cameraFactory.availableCameraIds.map { CameraIdentifier.create(it) }
        this.cameraFactory = cameraFactory
        this.cameraRepository = cameraRepository
        this.sourcePresenceObservable = cameraFactory.cameraPresenceSource

        sourcePresenceObservable?.addObserver(
            CameraXExecutors.newSequentialExecutor(backgroundExecutor),
            sourceObserver,
        )
    }

    /** Shuts down the provider and cleans up all resources. */
    public fun shutdown() {
        if (!isMonitoring.getAndSet(false)) {
            Logger.d(TAG, "Shutdown called when not monitoring. Ignoring.")
            return
        }
        Logger.i(TAG, "Shutting down CameraPresenceProvider monitoring.")

        sourcePresenceObservable?.removeObserver(sourceObserver)
        clearAllCameraStateObservers()

        dependentInternalListeners.clear()
        publicApiListeners.clear()
        currentFilteredIds = emptyList()
        cameraFactory = null
        cameraRepository = null
    }

    private inner class SourceObservableObserver : Observable.Observer<List<CameraIdentifier>> {
        override fun onNewData(rawCameraIdentifiers: List<CameraIdentifier>?) {
            if (!isMonitoring.get()) return
            val factory = cameraFactory ?: return

            // Phase 1: Update CameraFactory
            val rawIdStrings = rawCameraIdentifiers?.map { it.internalId } ?: emptyList()
            try {
                factory.onCameraIdsUpdated(rawIdStrings)
            } catch (e: Exception) {
                Logger.e(TAG, "CameraFactory failed to update. Triggering refresh.", e)
                sourcePresenceObservable?.fetchData()
                return
            }

            // Phase 2: Get filtered list and start transaction
            val newFilteredIds = factory.availableCameraIds.map { CameraIdentifier.create(it) }
            processFilteredCameraIdUpdate(newFilteredIds)
        }

        override fun onError(t: Throwable) {
            if (!isMonitoring.get()) return
            Logger.e(TAG, "Error from source camera presence observable. Triggering refresh.", t)
            sourcePresenceObservable?.fetchData()
        }
    }

    @ExecutedBy("backgroundExecutor")
    private fun processFilteredCameraIdUpdate(newFilteredIdentifiers: List<CameraIdentifier>) {
        val oldFilteredIdsSnapshot = currentFilteredIds.toList()
        if (newFilteredIdentifiers == oldFilteredIdsSnapshot) {
            return
        }

        // Calculate the diff once at the beginning.
        val oldIdSet = oldFilteredIdsSnapshot.toSet()
        val newIdSet = newFilteredIdentifiers.toSet()
        val addedCameras = newIdSet - oldIdSet
        val removedCameras = oldIdSet - newIdSet

        // Transaction Start
        val successfullyUpdatedListeners = mutableListOf<InternalCameraPresenceListener>()
        val newFilteredIdStrings = newFilteredIdentifiers.map { it.internalId }

        try {
            // 1. Unregister observers from cameras that are being removed.
            removedCameras.forEach { removeCameraStateObserver(it.internalId) }

            // 2. Update CameraRepository and other internal listeners.
            cameraRepository?.let {
                Logger.d(TAG, "Updating CameraRepository...")
                it.onCamerasUpdated(newFilteredIdStrings)
                successfullyUpdatedListeners.add(it)
                Logger.d(TAG, "CameraRepository updated successfully.")
            }

            // Phase 3: Update all other dependent listeners
            if (dependentInternalListeners.isNotEmpty()) {
                Logger.d(TAG, "Updating ${dependentInternalListeners.size} dependent listeners...")
                dependentInternalListeners.forEach { listener ->
                    listener.onCamerasUpdated(newFilteredIdStrings)
                    successfullyUpdatedListeners.add(listener)
                }
            }

            // 4. Transaction Success: Commit state, add new observers, and notify public listeners.
            currentFilteredIds = newFilteredIdentifiers
            addedCameras.forEach { conditionallySetupCameraStateObserver(it.internalId) }
            notifyPublicListeners(addedCameras, removedCameras)
        } catch (e: Exception) { // Catch any runtime exception to ensure rollback
            // Transaction Failure: Rollback
            Logger.e(TAG, "A core module failed to update. Rolling back changes.", e)
            val oldFilteredIdStrings = oldFilteredIdsSnapshot.map { it.internalId }

            // 1. Rollback internal listeners.
            successfullyUpdatedListeners.asReversed().forEach { listener ->
                try {
                    listener.onCamerasUpdated(oldFilteredIdStrings)
                } catch (rollbackException: Exception) {
                    Logger.e(TAG, "Failed to rollback listener: $listener", rollbackException)
                }
            }

            // 2. Rollback the observer state to match the old state.
            //    - Re-add observers for cameras we tried to remove.
            //    - Remove observers for cameras we failed to add.
            removedCameras.forEach { conditionallySetupCameraStateObserver(it.internalId) }
            addedCameras.forEach { removeCameraStateObserver(it.internalId) }
        }
    }

    /** Helper to notify all public listeners of the final diff. */
    @ExecutedBy("backgroundExecutor")
    private fun notifyPublicListeners(
        addedCameras: Set<CameraIdentifier>,
        removedCameras: Set<CameraIdentifier>,
    ) {
        if (addedCameras.isNotEmpty()) {
            Logger.i(TAG, "Notifying ${addedCameras.size} cameras added.")
            notifyPublicCamerasAdded(addedCameras)
        }
        if (removedCameras.isNotEmpty()) {
            Logger.i(TAG, "Notifying ${removedCameras.size} cameras removed.")
            notifyPublicCamerasRemoved(removedCameras)
        }
    }

    public fun addDependentInternalListener(listener: InternalCameraPresenceListener) {
        dependentInternalListeners.add(listener)
    }

    public fun removeDependentInternalListener(listener: InternalCameraPresenceListener) {
        dependentInternalListeners.remove(listener)
    }

    @ExecutedBy("backgroundExecutor")
    private fun conditionallySetupCameraStateObserver(systemCameraId: String) {
        val repo = cameraRepository ?: return
        try {
            val cameraInternal = repo.getCamera(systemCameraId)
            setupCameraStateObserver(cameraInternal.cameraInfoInternal)
        } catch (_: IllegalArgumentException) {
            Logger.w(
                TAG,
                "CameraInternal not found for $systemCameraId. Cannot setup state observer.",
            )
        }
    }

    @ExecutedBy("backgroundExecutor")
    private fun setupCameraStateObserver(cameraInfoInternal: CameraInfoInternal) {
        val cameraIdStr = cameraInfoInternal.cameraId
        if (!isMonitoring.get()) {
            return
        }

        synchronized(observerLock) {
            if (cameraStateObservers.containsKey(cameraIdStr)) {
                return
            }
            val stateObserver =
                Observer<CameraState> { cameraState ->
                    if (!isMonitoring.get()) {
                        Logger.d(
                            TAG,
                            "Ignore camera state change handling since already stop monitoring",
                        )
                    } else if (
                        cameraState?.error?.type == CameraState.ErrorType.CRITICAL ||
                            cameraState?.type == CameraState.Type.CLOSED
                    ) {
                        Logger.w(
                            TAG,
                            "Camera $cameraIdStr state changed to ${cameraState.type} with " +
                                "error: ${cameraState.error?.code}. Triggering refresh.",
                        )
                        sourcePresenceObservable?.fetchData()
                    }
                }
            CameraXExecutors.mainThreadExecutor().execute {
                cameraInfoInternal.cameraState.observeForever(stateObserver)
            }
            cameraStateObservers[cameraIdStr] = stateObserver
            Logger.d(TAG, "Registered state observer for camera: $cameraIdStr")
        }
    }

    @ExecutedBy("backgroundExecutor")
    private fun removeCameraStateObserver(systemCameraId: String) {
        synchronized(observerLock) {
            val observer = cameraStateObservers.remove(systemCameraId)
            val repo = cameraRepository
            if (observer != null && repo != null) {
                try {
                    val cameraInternal = repo.getCamera(systemCameraId)
                    CameraXExecutors.mainThreadExecutor().execute {
                        cameraInternal.cameraInfoInternal.cameraState.removeObserver(observer)
                    }
                    Logger.d(TAG, "Removed state observer for: $systemCameraId")
                } catch (_: IllegalArgumentException) {
                    // Safe to ignore. Camera was already removed from repo.
                }
            }
        }
    }

    /** Clears all state observers, ensuring LiveData observers are removed on the main thread. */
    private fun clearAllCameraStateObservers() {
        val observersToClear: Map<String, Observer<CameraState>>
        synchronized(observerLock) {
            if (cameraStateObservers.isEmpty()) {
                return
            }
            observersToClear = cameraStateObservers.toMap()
            cameraStateObservers.clear()
        }

        val repo = cameraRepository
        if (repo != null) {
            val cameraInfosToRemoveObserver =
                repo.cameras.mapNotNull { cameraInternal -> cameraInternal?.cameraInfoInternal }
            Logger.d(TAG, "Clearing all ${observersToClear.size} state observers.")
            observersToClear.forEach { (cameraId, observer) ->
                CameraXExecutors.mainThreadExecutor().execute {
                    try {
                        cameraInfosToRemoveObserver
                            .firstOrNull { it.cameraId == cameraId }
                            ?.cameraState
                            ?.removeObserver(observer)
                    } catch (_: IllegalArgumentException) {
                        // Safe to ignore, the camera might have already been removed.
                    }
                }
            }
        }
    }

    public fun addCameraPresenceListener(listener: CameraPresenceListener, executor: Executor) {
        val wrapper = ListenerWrapper(listener, executor)
        publicApiListeners.add(wrapper)

        executor.execute {
            val currentIds = currentFilteredIds.toSet()
            if (currentIds.isNotEmpty()) {
                listener.onCamerasAdded(currentIds)
            }
        }
    }

    public fun removeCameraPresenceListener(listener: CameraPresenceListener) {
        publicApiListeners.removeAll { it.listener == listener }
    }

    private fun notifyPublicCamerasAdded(addedIds: Set<CameraIdentifier>) {
        publicApiListeners.forEach { wrapper ->
            wrapper.executor.execute { wrapper.listener.onCamerasAdded(addedIds) }
        }
    }

    private fun notifyPublicCamerasRemoved(removedIds: Set<CameraIdentifier>) {
        publicApiListeners.forEach { wrapper ->
            wrapper.executor.execute { wrapper.listener.onCamerasRemoved(removedIds) }
        }
    }

    public companion object {
        private const val TAG = "CameraPresencePrvdr"
    }
}
