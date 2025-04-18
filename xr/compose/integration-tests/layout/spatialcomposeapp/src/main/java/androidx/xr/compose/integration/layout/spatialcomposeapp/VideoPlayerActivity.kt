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

package androidx.xr.compose.integration.layout.spatialcomposeapp

import android.media.MediaPlayer
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.xr.compose.spatial.Subspace
import androidx.xr.compose.subspace.SpatialPanel
import androidx.xr.compose.subspace.layout.SubspaceModifier
import androidx.xr.compose.subspace.layout.height
import androidx.xr.compose.subspace.layout.movable
import androidx.xr.compose.subspace.layout.width
import androidx.xr.runtime.math.Pose
import androidx.xr.runtime.math.Quaternion
import androidx.xr.runtime.math.Vector3
import androidx.xr.scenecore.Dimensions
import androidx.xr.scenecore.MovableComponent
import androidx.xr.scenecore.PanelEntity
import androidx.xr.scenecore.PixelDimensions
import androidx.xr.scenecore.Session
import androidx.xr.scenecore.SurfaceEntity

/**
 * This is a sample activity that shows how to use Compose XR to create a SurfaceEntity and use it
 * to display a video.
 */
class VideoPlayerActivity : ComponentActivity() {
    private lateinit var mediaPlayer: MediaPlayer

    private val session by lazy { Session.create(this) }

    private var surfaceEntity: SurfaceEntity? = null
    private var movableComponent: MovableComponent? = null
    private var videoPlaying by mutableStateOf<Boolean>(false)
    private var controlPanelEntity: PanelEntity? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        session.spatialEnvironment.setPassthroughOpacityPreference(0.0f)
        setContent { Subspace { SurfaceEntityContent(session) } }
    }

    /** Sets up the surface entity */
    @Composable
    private fun SurfaceEntityContent(session: Session) {

        SpatialPanel(SubspaceModifier.height(600.dp).width(600.dp).movable()) {
            Box(
                modifier = Modifier.background(Color.LightGray).fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                BackHandler {
                    Log.i(
                        "BackHandler",
                        "Gnav BACK is being handled by Surface Entity back handler"
                    )
                    destroySurfaceEntity()
                    finish()
                }
                VideoPlayerTestActivityUI(session)
            }
        }
    }

    @Composable
    fun BigBuckBunnyButton() {
        Button(
            onClick = {
                surfaceEntity =
                    SurfaceEntity.create(
                        session,
                        SurfaceEntity.StereoMode.TOP_BOTTOM,
                        Pose(Vector3(0f, 0.0f, 0.1f), Quaternion(0.0f, 0.0f, 0.0f, 1.0f)),
                    )
                // Make the video player movable (to make it easier to look at it from different
                // angles and distances)
                movableComponent = MovableComponent.create(session)

                // The quad has a radius of 1.0 meters
                movableComponent!!.size = Dimensions(1.0f, 1.0f, 1.0f)
                @Suppress("UNUSED_VARIABLE")
                val unused = surfaceEntity!!.addComponent(movableComponent!!)

                // Set up MediaPlayer
                mediaPlayer = MediaPlayer()
                mediaPlayer.setSurface(surfaceEntity!!.getSurface())

                // For Testers: This file should be packaged with the APK.
                mediaPlayer.setDataSource(assets.openFd("vid_bigbuckbunny.mp4"))

                mediaPlayer.setOnCompletionListener { mediaPlayer.release() }
                mediaPlayer.setOnVideoSizeChangedListener { _, width, height ->
                    check(width >= 0 && height >= 0) { "Video dimensions must be positive" }
                    // Resize the canvas to match the video aspect ratio - accounting for the stereo
                    // mode.
                    var dimensions =
                        getCanvasAspectRatio(SurfaceEntity.StereoMode.TOP_BOTTOM, width, height)
                    surfaceEntity!!.canvasShape =
                        SurfaceEntity.CanvasShape.Quad(dimensions.width, dimensions.height)

                    // Resize the MovableComponent to match the canvas dimensions.
                    movableComponent!!.size = surfaceEntity!!.dimensions
                }
                mediaPlayer.setOnPreparedListener { mediaPlayer.start() }
                mediaPlayer.prepareAsync()
                videoPlaying = true
            }
        ) {
            Text(text = "Play Big Buck Bunny", fontSize = 20.sp)
        }
    }

    @Composable
    fun VideoPlayerControls() {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { destroySurfaceEntity() }) {
                    Text(text = "End Video", fontSize = 10.sp)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = {
                        surfaceEntity!!.canvasShape = SurfaceEntity.CanvasShape.Quad(1.0f, 1.0f)
                        // Move the Quad-shaped canvas to a spot in front of the User.
                        surfaceEntity!!.setPose(
                            session.spatialUser.head?.transformPoseTo(
                                Pose(
                                    Vector3(0.0f, 0.0f, -1.5f),
                                    Quaternion(0.0f, 0.0f, 0.0f, 1.0f)
                                ),
                                session.activitySpace,
                            )!!
                        )
                    }
                ) {
                    Text(text = "Set Quad", fontSize = 10.sp)
                }
                Button(
                    onClick = {
                        surfaceEntity!!.canvasShape = SurfaceEntity.CanvasShape.Vr360Sphere(1.0f)
                    }
                ) {
                    Text(text = "Set Vr360", fontSize = 10.sp)
                }
                Button(
                    onClick = {
                        surfaceEntity!!.canvasShape =
                            SurfaceEntity.CanvasShape.Vr180Hemisphere(1.0f)
                    }
                ) {
                    Text(text = "Set Vr180", fontSize = 10.sp)
                }
            } // end row
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { surfaceEntity!!.stereoMode = SurfaceEntity.StereoMode.MONO }) {
                    Text(text = "Mono", fontSize = 10.sp)
                }
                Button(
                    onClick = { surfaceEntity!!.stereoMode = SurfaceEntity.StereoMode.TOP_BOTTOM }
                ) {
                    Text(text = "Top-Bottom", fontSize = 10.sp)
                }
                Button(
                    onClick = { surfaceEntity!!.stereoMode = SurfaceEntity.StereoMode.SIDE_BY_SIDE }
                ) {
                    Text(text = "Side-by-Side", fontSize = 10.sp)
                }
            } // end row
        } // end column
    }

    private fun setupControlPanel() {
        // Technically this leaks, but it's a sample / test app.
        val panelContentView =
            ComposeView(this).apply {
                setViewCompositionStrategy(
                    ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
                )
                setContent { VideoPlayerControls() }
            }

        controlPanelEntity =
            PanelEntity.create(
                session,
                panelContentView,
                pixelDimensions = PixelDimensions(640, 480),
                "playerControls",
                Pose(Vector3(0.0f, -0.4f, -0.85f)), // kind of low, but within a 1m radius
            )
        controlPanelEntity!!.setParent(surfaceEntity!!)
    }

    fun destroySurfaceEntity() {
        if (surfaceEntity == null) {
            return
        }
        if (::mediaPlayer.isInitialized) {
            mediaPlayer.release()
        }
        videoPlaying = false
        surfaceEntity!!.dispose()
        surfaceEntity = null
    }

    fun getCanvasAspectRatio(stereoMode: Int, videoWidth: Int, videoHeight: Int): Dimensions {
        when (stereoMode) {
            SurfaceEntity.StereoMode.MONO ->
                return Dimensions(1.0f, videoHeight.toFloat() / videoWidth, 0.0f)
            SurfaceEntity.StereoMode.TOP_BOTTOM ->
                return Dimensions(1.0f, 0.5f * videoHeight.toFloat() / videoWidth, 0.0f)
            SurfaceEntity.StereoMode.SIDE_BY_SIDE ->
                return Dimensions(1.0f, 2.0f * videoHeight.toFloat() / videoWidth, 0.0f)
            else -> throw IllegalArgumentException("Unsupported stereo mode: $stereoMode")
        }
    }

    @Composable
    fun VideoPlayerTestActivityUI(session: Session) {
        val movableComponentMP = remember { mutableStateOf<MovableComponent?>(null) }
        val videoPaused = remember { mutableStateOf(false) }
        movableComponentMP.value = MovableComponent.create(session)
        @Suppress("UNUSED_VARIABLE")
        val unused = session.mainPanelEntity.addComponent(movableComponentMP.value!!)
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Column(
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.weight(1f).padding(8.dp),
            ) {
                Text(text = "(Stereo) SurfaceEntity", fontSize = 50.sp)
                if (videoPlaying == false) {
                    // High level testcases
                    BigBuckBunnyButton()
                } else {
                    Column(
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.weight(1f).padding(8.dp),
                    ) {
                        VideoPlayerControls()
                        Button(
                            onClick = {
                                videoPaused.value = !videoPaused.value
                                if (videoPaused.value) {
                                    mediaPlayer.pause()
                                } else {
                                    mediaPlayer.start()
                                }
                            }
                        ) {
                            Text(text = "Toggle Pause Stereo video", fontSize = 30.sp)
                        }
                    }
                }
            }
        }
    }
}
