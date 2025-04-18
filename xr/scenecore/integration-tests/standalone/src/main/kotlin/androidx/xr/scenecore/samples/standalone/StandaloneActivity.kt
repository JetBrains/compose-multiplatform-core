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

package androidx.xr.scenecore.samples.standalone

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.xr.runtime.math.Pose
import androidx.xr.runtime.math.Quaternion
import androidx.xr.runtime.math.Vector3
import androidx.xr.scenecore.GltfModel
import androidx.xr.scenecore.GltfModelEntity
import androidx.xr.scenecore.PanelEntity
import androidx.xr.scenecore.PixelDimensions
import androidx.xr.scenecore.Session
import kotlin.math.cos
import kotlin.math.sin
import kotlin.time.TimeSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class StandaloneActivity : AppCompatActivity() {

    private val session by lazy { Session.create(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.standalone_activity)

        // Create a single panel with text
        @SuppressLint("InflateParams")
        val panelContentView = layoutInflater.inflate(R.layout.panel, null)
        val panelEntity =
            PanelEntity.create(
                session,
                panelContentView,
                PixelDimensions(640, 480),
                "panel",
                Pose(Vector3(0f, -0.5f, 0.5f)),
            )
        panelEntity.setParent(session.activitySpace)

        // Create multiple orbiting shark models
        val sharkModelFuture = GltfModel.create(session, "models/GreatWhiteShark.glb")
        sharkModelFuture.addListener(
            {
                val sharkModel = sharkModelFuture.get()
                createModelSolarSystem(session, sharkModel)
            },
            Runnable::run,
        )
    }

    private fun createModelSolarSystem(session: Session, model: GltfModel) {
        val sunShark = GltfModelEntity.create(session, model, Pose(Vector3(-0.5f, -3f, -9f)))
        sunShark.setParent(session.activitySpace)
        val planetShark = GltfModelEntity.create(session, model, Pose(Vector3(-1f, -3f, -9f)))
        planetShark.setParent(sunShark)
        val moonShark = GltfModelEntity.create(session, model, Pose(Vector3(-1.5f, -3f, -9f)))
        moonShark.setParent(planetShark)

        orbitModelAroundParent(planetShark, 4f, 0f, 20000f)
        orbitModelAroundParent(moonShark, 2f, 1.67f, 5000f)
    }

    // TODO: b/339450306 - Simply update parent's rotation once math library is added to SceneCore
    private fun orbitModelAroundParent(
        modelEntity: GltfModelEntity,
        radius: Float,
        startAngle: Float,
        rotateTimeMs: Float,
    ) {
        lifecycleScope.launch {
            val pi = 3.14159F
            val timeSource = TimeSource.Monotonic
            val startTime = timeSource.markNow()

            while (true) {
                delay(16L)
                val deltaAngle =
                    (2 * pi) * ((timeSource.markNow() - startTime).inWholeMilliseconds) /
                        rotateTimeMs

                val angle = startAngle + deltaAngle
                val pos = Vector3(radius * cos(angle), 0F, radius * sin(angle))
                modelEntity.setPose(Pose(pos, Quaternion.Identity))
            }
        }
    }
}
