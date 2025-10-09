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

package androidx.xr.arcore.testapp.helloar.rendering

import android.annotation.SuppressLint
import android.content.res.Resources
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.xr.arcore.AugmentedObject
import androidx.xr.runtime.AugmentedObjectCategory
import androidx.xr.runtime.Session
import androidx.xr.runtime.TrackingState
import androidx.xr.runtime.math.FloatSize3d
import androidx.xr.runtime.math.Vector3
import androidx.xr.scenecore.GltfModel
import androidx.xr.scenecore.GltfModelEntity
import androidx.xr.scenecore.scene
import java.nio.file.Paths
import java.util.Collections
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal class AugmentedObjectRenderer(val session: Session, val coroutineScope: CoroutineScope) :
    DefaultLifecycleObserver {
    private val _cubeModelMap = Collections.synchronizedMap(HashMap<String, GltfModel?>())
    private val _renderedObjects: MutableStateFlow<List<AugmentedObjectModel>> =
        MutableStateFlow(mutableListOf<AugmentedObjectModel>())

    internal val renderedObjects: StateFlow<Collection<AugmentedObjectModel>> =
        _renderedObjects.asStateFlow()

    private lateinit var updateJob: CompletableJob

    override fun onResume(owner: LifecycleOwner) {
        updateJob =
            SupervisorJob(
                coroutineScope.launch {
                    AugmentedObject.subscribe(session).collect { updateObjectModels(it) }
                }
            )
    }

    override fun onPause(owner: LifecycleOwner) {
        updateJob.complete()
        _renderedObjects.value = emptyList<AugmentedObjectModel>()
    }

    private suspend fun updateObjectModels(objects: Collection<AugmentedObject>) {
        val objectsToRender = _renderedObjects.value.toMutableList()
        // create renderers for new objects
        for (obj in objects) {
            if (_renderedObjects.value.none { it.id == obj.hashCode() }) {
                addObjectModel(obj, objectsToRender)
            }
        }

        // stop rendering dropped objects
        for (renderedObject in objectsToRender) {
            if (objects.none { it.hashCode() == renderedObject.id }) {
                removeObjectModel(renderedObject, objectsToRender)
            }
        }

        // emit to notify collectors that the collection has been updated.
        _renderedObjects.value = objectsToRender
    }

    private suspend fun addObjectModel(
        obj: AugmentedObject,
        objectsToRender: MutableList<AugmentedObjectModel>,
    ) {
        val category = obj.state.value.category.toString()
        val asset =
            if (SUPPORTED_OBJECT_MODELS.containsKey(category)) {
                SUPPORTED_OBJECT_MODELS[category]
            } else {
                DEFAULT_OBJECT_MODEL
            }
        _cubeModelMap.putIfAbsent(category, GltfModel.create(session, Paths.get("models", asset)))
        val gltfCubeModelEntity: GltfModelEntity? =
            GltfModelEntity.create(session, _cubeModelMap[category]!!)
        // The counter starts at max to trigger the resize on the first update loop since emulators
        // only
        // update their static planes once.
        var counter = PANEL_RESIZE_UPDATE_COUNT
        // Make the render job a child of the update job so it completes when the parent completes.
        val renderJob =
            coroutineScope.launch(updateJob) {
                obj.state.collect { state ->
                    when (state.trackingState) {
                        TrackingState.TRACKING -> {
                            if (state.category == AugmentedObjectCategory.UNKNOWN) {
                                gltfCubeModelEntity!!.setEnabled(false)
                            } else {
                                gltfCubeModelEntity!!.setEnabled(true)
                                gltfCubeModelEntity!!.setAlpha(
                                    TRACKED_ALPHA_VALUES[state.category.toString()]
                                        ?: TRACKED_ALPHA_DEFAULT
                                )
                                counter++

                                val newPose =
                                    session.scene.perceptionSpace.transformPoseTo(
                                        state.centerPose,
                                        session.scene.activitySpace,
                                    )
                                gltfCubeModelEntity!!.setPose(newPose)

                                if (counter > PANEL_RESIZE_UPDATE_COUNT) {
                                    gltfCubeModelEntity!!.setScale(scaledExtents(state.extents))
                                    counter = 0
                                }
                            }
                        }
                        TrackingState.PAUSED -> gltfCubeModelEntity!!.setAlpha(PAUSED_ALPHA)
                        TrackingState.STOPPED -> gltfCubeModelEntity!!.setEnabled(false)
                    }
                }
            }

        objectsToRender.add(
            AugmentedObjectModel(
                obj.hashCode(),
                obj.state.value.category,
                obj.state,
                gltfCubeModelEntity!!,
                renderJob,
            )
        )
    }

    private fun removeObjectModel(
        objectModel: AugmentedObjectModel,
        objsToRender: MutableList<AugmentedObjectModel>,
    ) {
        objectModel.renderJob?.cancel()
        objectModel.modelEntity.dispose()
        objsToRender.remove(objectModel)
    }

    private fun scaledExtents(extents: FloatSize3d): Vector3 {
        return Vector3(
            extents.width * MODEL_SCALING_FACTOR,
            extents.height * MODEL_SCALING_FACTOR,
            extents.depth * MODEL_SCALING_FACTOR,
        )
    }

    private companion object {
        private val PX_PER_METER = Resources.getSystem().displayMetrics.density * 1111.11f
        private const val PANEL_RESIZE_UPDATE_COUNT = 50

        @SuppressLint("PrimitiveInCollection")
        private val TRACKED_ALPHA_VALUES =
            mapOf("Keyboard" to .05f, "Mouse" to .25f, "Laptop" to .1f)
        private val SUPPORTED_OBJECT_MODELS =
            mapOf(
                "Keyboard" to "BoundingBoxKeyboard.glb",
                "Mouse" to "BoundingBoxMouse.glb",
                "Laptop" to "BoundingBoxLaptop.glb",
            )
        private val DEFAULT_OBJECT_MODEL = "BoundingBoxKeyboard.glb"
        private const val TRACKED_ALPHA_DEFAULT = .5f
        private const val PAUSED_ALPHA = 0.25f

        private const val MODEL_SCALING_FACTOR = 1f / 1.7f / 2f
    }
}
