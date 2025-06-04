/*
 * Copyright 2024 The Android Open Source Project
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

package androidx.xr.scenecore.samples.splitenginetest

import android.app.Activity
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import androidx.xr.runtime.Session
import androidx.xr.runtime.SessionCreateSuccess
import androidx.xr.runtime.math.Pose
import androidx.xr.runtime.math.Quaternion
import androidx.xr.runtime.math.Vector3
import androidx.xr.scenecore.ExrImage
import androidx.xr.scenecore.GltfModel
import androidx.xr.scenecore.GltfModelEntity
import androidx.xr.scenecore.InputEvent
import androidx.xr.scenecore.InteractableComponent
import androidx.xr.scenecore.MovableComponent
import androidx.xr.scenecore.SpatialEnvironment.SpatialEnvironmentPreference
import androidx.xr.scenecore.scene
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.delay

class SplitEngineTestActivity : ComponentActivity() {

    private val activity = this

    private val session by lazy {
        // SplitEngine is enabled by default.
        (Session.create(this) as SessionCreateSuccess).session
    }

    private var spatialEnvironmentPreference: SpatialEnvironmentPreference? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        session.scene.spatialEnvironment.setPassthroughOpacityPreference(0.0f)

        setContent { ComposeEntry(session, activity) }
    }

    private fun togglePassthrough(session: Session) {
        val passthroughOpacity: Float =
            session.scene.spatialEnvironment.getCurrentPassthroughOpacity()
        Log.i("TogglePassthrough", "TogglePassthrough!")
        when (passthroughOpacity) {
            0.0f -> session.scene.spatialEnvironment.setPassthroughOpacityPreference(1.0f)
            1.0f -> session.scene.spatialEnvironment.setPassthroughOpacityPreference(0.0f)
        }
    }

    private fun setSkyboxAndGeometry(skybox: ExrImage?, geometry: GltfModel?) {
        spatialEnvironmentPreference = SpatialEnvironmentPreference(skybox, geometry)
        session.scene.spatialEnvironment.setSpatialEnvironmentPreference(
            spatialEnvironmentPreference
        )
    }

    // TODO: b/324947709 - Refactor common @Composable code into a utility library for common usage
    // across sample apps.
    /** A boilerplate entrypoint to wire up ComposeUI into the main Panel. */
    @Composable
    fun ComposeEntry(session: Session, activity: Activity) {
        val composeView =
            ComposeView(activity.applicationContext).apply {
                setViewCompositionStrategy(
                    ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
                )
                setContent { SplitEngineTestActivityUI(session) }
            }
        composeView.setViewTreeLifecycleOwner(activity as LifecycleOwner)
        composeView.setViewTreeViewModelStoreOwner(activity as ViewModelStoreOwner)
        composeView.setViewTreeSavedStateRegistryOwner(activity as SavedStateRegistryOwner)

        // Add a panel to the main activity with a button to toggle passthrough
        LaunchedEffect(Unit) { activity.setContentView(composeView) }
    }

    /*
     * Panel UI which exercises split engine pathways integrated with .
     */
    @Composable
    fun SplitEngineTestActivityUI(session: Session) {
        val movableComponentMP = remember { mutableStateOf<MovableComponent?>(null) }

        val blueSkybox = remember { mutableStateOf<ExrImage?>(null) }
        val groundGeometry = remember { mutableStateOf<GltfModel?>(null) }
        val dragonModel = remember { mutableStateOf<GltfModel?>(null) }
        val dragonEntity = remember { mutableStateOf<GltfModelEntity?>(null) }
        val dragonAnimationState = remember {
            androidx.compose.runtime.mutableIntStateOf(GltfModelEntity.AnimationState.STOPPED)
        }
        val glimmerModel = remember { mutableStateOf<GltfModel?>(null) }
        val glimmerEntity = remember { mutableStateOf<GltfModelEntity?>(null) }
        var interactableAttached by remember { mutableStateOf(false) }

        // This will continuously poll the animation state to verify that the state is updated to
        // "STOPPED" after a single run is done. Ideally we would register a listener for this, but
        // the
        // current API doesn't support it.
        LaunchedEffect(Unit) {
            while (true) {
                if (dragonEntity.value != null) {
                    dragonAnimationState.intValue = dragonEntity.value!!.getAnimationState()
                }
                delay(16)
            }
        }

        Row(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Column(
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.weight(1f).padding(8.dp),
            ) {
                Text(text = "System APIs", fontSize = 50.sp)
                Button(onClick = { togglePassthrough(session) }) {
                    Text(text = "Toggle Passthrough", fontSize = 30.sp)
                }
                Button(onClick = { session.scene.spatialEnvironment.requestFullSpaceMode() }) {
                    // Set up the MoveableComponent on the first jump into FSM to allow the user to
                    // move the
                    // main panel around.
                    if (movableComponentMP.value == null) {
                        movableComponentMP.value = MovableComponent.create(session)
                        val unused =
                            session.scene.mainPanelEntity.addComponent(movableComponentMP.value!!)
                    }
                    Text(text = "Request FSM", fontSize = 30.sp)
                }
                Button(onClick = { session.scene.spatialEnvironment.requestHomeSpaceMode() }) {
                    Text(text = "Request HSM", fontSize = 30.sp)
                }
                Button(onClick = { ActivityCompat.recreate(activity) }) {
                    Text(text = "Recreate Activity", fontSize = 30.sp)
                }
            }
            Column(
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.weight(1f).padding(8.dp).verticalScroll(rememberScrollState()),
            ) {
                Text(text = "Split Engine APIs", fontSize = 50.sp)
                Button(
                    onClick = {
                        val skyboxTokenFuture: ListenableFuture<ExrImage> =
                            ExrImage.create(session, "skyboxes/BlueSkybox.zip")
                        skyboxTokenFuture.addListener(
                            {
                                try {
                                    blueSkybox.value = skyboxTokenFuture.get()
                                } catch (e: Exception) {
                                    Log.e(
                                        "SplitEngineTestActivity",
                                        "Failed to load BlueSkybox: " + e.message,
                                    )
                                }
                            },
                            Runnable::run,
                        )
                    }
                ) {
                    Text(text = "Load Skybox Blue", fontSize = 30.sp)
                }
                Button(
                    onClick = {
                        setSkyboxAndGeometry(
                            blueSkybox.value,
                            spatialEnvironmentPreference?.geometry,
                        )
                    }
                ) {
                    Text(text = "Set Skybox Blue", fontSize = 30.sp)
                }
                Button(
                    onClick = { setSkyboxAndGeometry(null, spatialEnvironmentPreference?.geometry) }
                ) {
                    Text(text = "Remove Skybox Blue", fontSize = 30.sp)
                }
                Button(
                    onClick = {
                        val gltfTokenFuture: ListenableFuture<GltfModel> =
                            GltfModel.create(session, "models/GroundGeometry.glb")
                        gltfTokenFuture.addListener(
                            {
                                try {
                                    groundGeometry.value = gltfTokenFuture.get()
                                } catch (e: Exception) {
                                    Log.e(
                                        "SplitEngineTestActivity",
                                        "Failed to load GroundGeometry: " + e.message,
                                    )
                                }
                            },
                            Runnable::run,
                        )
                    }
                ) {
                    Text(text = "Load Ground Geometry", fontSize = 30.sp)
                }
                Button(
                    onClick = {
                        setSkyboxAndGeometry(
                            spatialEnvironmentPreference?.skybox,
                            groundGeometry.value,
                        )
                    }
                ) {
                    Text(text = "Set Ground Geometry", fontSize = 30.sp)
                }
                Button(
                    onClick = { setSkyboxAndGeometry(spatialEnvironmentPreference?.skybox, null) }
                ) {
                    Text(text = "Remove Ground Geometry", fontSize = 30.sp)
                }
                Button(
                    onClick = {
                        val gltfToken: ListenableFuture<GltfModel> =
                            GltfModel.create(session, "models/l2a_pulse.glb")
                        gltfToken.addListener(
                            {
                                try {
                                    glimmerModel.value = gltfToken.get()
                                } catch (e: Exception) {
                                    Log.e(
                                        "SplitEngineTestActivity",
                                        "Failed to load Glimmer Model: " + e.message,
                                    )
                                }
                            },
                            Runnable::run,
                        )
                    }
                ) {
                    Text(text = "Load Glimmer Model", fontSize = 30.sp)
                }
                Button(
                    onClick = {
                        val gltfTokenFuture: ListenableFuture<GltfModel> =
                            GltfModel.create(session, "models/Dragon_Evolved.gltf")
                        gltfTokenFuture.addListener(
                            {
                                try {
                                    dragonModel.value = gltfTokenFuture.get()
                                } catch (e: Exception) {
                                    Log.e(
                                        "SplitEngineTestActivity",
                                        "Failed to load Dragon Model: " + e.message,
                                    )
                                }
                            },
                            Runnable::run,
                        )
                    }
                ) {
                    Text(text = "Load Dragon Model Split Engine", fontSize = 30.sp)
                }
                if (dragonModel.value != null) {
                    Button(
                        onClick = {
                            dragonEntity.value =
                                GltfModelEntity.create(
                                    session,
                                    dragonModel.value!!,
                                    Pose(
                                        Vector3(2.0f, 0.0f, -1.0f),
                                        Quaternion(0.0f, 0.0f, 0.0f, 1.0f),
                                    ),
                                )
                        }
                    ) {
                        Text(text = "Create Dragon Entity Split Engine", fontSize = 30.sp)
                    }
                }
                if (dragonEntity.value != null) {
                    val interactableComponent =
                        InteractableComponent.create(session, mainExecutor) {
                            if (it.action == InputEvent.ACTION_DOWN) {
                                dragonEntity.value!!.setScale(
                                    dragonEntity.value!!.getScale() * 1.1f
                                )
                            }
                        }
                    Text(text = "Attach Interactable Component", fontSize = 16.sp)
                    val dragonScale = dragonEntity.value!!.getScale()
                    Switch(
                        checked = interactableAttached,
                        onCheckedChange = {
                            interactableAttached = it
                            if (interactableAttached) {
                                val unused =
                                    dragonEntity.value!!.addComponent(interactableComponent)
                            } else {
                                dragonEntity.value!!
                                    .getComponentsOfType(InteractableComponent::class.java)
                                    .forEach { component ->
                                        dragonEntity.value!!.removeComponent(component)
                                    }
                                dragonEntity.value!!.setScale(dragonScale)
                            }
                        },
                    )
                    Button(
                        onClick = {
                            dragonEntity.value!!.dispose()
                            dragonEntity.value = null
                        }
                    ) {
                        Text(text = "Destroy Dragon Entity Split Engine", fontSize = 30.sp)
                    }
                }
            }
            if (dragonEntity.value != null) {
                Column(
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.weight(1f).padding(8.dp),
                ) {
                    Text(text = "Animation APIs", fontSize = 50.sp)
                    Button(
                        onClick = {
                            // Independently tested this with animation name string set to Null
                            dragonEntity.value!!.startAnimation(false, "Fast_Flying")
                            dragonAnimationState.intValue = dragonEntity.value!!.getAnimationState()
                        }
                    ) {
                        Text(text = "Animate Dragon Entity", fontSize = 30.sp)
                    }
                    Button(
                        onClick = {
                            dragonEntity.value!!.startAnimation(true, "Fast_Flying")
                            dragonAnimationState.intValue = dragonEntity.value!!.getAnimationState()
                        }
                    ) {
                        Text(text = "Loop Animate Dragon Entity", fontSize = 30.sp)
                    }
                    Button(
                        onClick = {
                            dragonEntity.value!!.stopAnimation()
                            dragonAnimationState.intValue = dragonEntity.value!!.getAnimationState()
                        }
                    ) {
                        Text(text = "Stop Animate Dragon Entity", fontSize = 30.sp)
                    }
                    Text(
                        text =
                            "Dragon Entity Animation State: ${dragonAnimationState.intValue!!}", // +
                        // dragonAnimationState.intValue!!.name,
                        fontSize = 30.sp,
                    )
                }
            }
            if (glimmerModel.value != null) {
                Column(
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.weight(1f).padding(8.dp),
                ) {
                    Text(text = "Glimmer", fontSize = 50.sp)
                    Button(
                        onClick = {
                            if (glimmerEntity.value == null) {
                                glimmerEntity.value =
                                    GltfModelEntity.create(
                                        session,
                                        glimmerModel.value!!,
                                        Pose(
                                            Vector3(0.0f, 0.0f, 0.0f),
                                            Quaternion(0.0f, 0.0f, 0.0f, 1.0f),
                                        ),
                                    )
                            }
                            glimmerEntity.value!!.startAnimation(false)
                        }
                    ) {
                        Text(text = "Play Glimmer", fontSize = 30.sp)
                    }
                }
            }
        }
    }
}
