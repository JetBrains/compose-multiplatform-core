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

package androidx.xr.scenecore.samples.anchortest

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.xr.runtime.Config
import androidx.xr.runtime.PlaneTrackingMode
import androidx.xr.runtime.Session
import androidx.xr.runtime.SessionCreateSuccess
import androidx.xr.runtime.math.Pose
import androidx.xr.runtime.math.Vector3
import androidx.xr.scenecore.AnchorEntity
import androidx.xr.scenecore.Dimensions
import androidx.xr.scenecore.GltfModel
import androidx.xr.scenecore.GltfModelEntity
import androidx.xr.scenecore.PlaneSemantic
import androidx.xr.scenecore.PlaneType
import androidx.xr.scenecore.scene
import kotlin.math.cos
import kotlin.math.sin
import kotlin.time.TimeSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AnchorTestActivity : AppCompatActivity() {
    private val session by lazy { (Session.create(this) as SessionCreateSuccess).session }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.anchortest_activity)
        session.resume()
        session.configure(Config(planeTracking = PlaneTrackingMode.HorizontalAndVertical))
        // Create a transform widget model and assign it to an Anchor
        val transformWidgetModelFuture = GltfModel.create(session, "models/xyzArrows.glb")
        transformWidgetModelFuture.addListener(
            {
                val transformWidgetModel = transformWidgetModelFuture.get()
                setupAnchorUi(transformWidgetModel)
            },
            // This will cause the listener to be run on the UI thread
            Runnable::run,
        )
    }

    @Suppress("UNUSED_VARIABLE")
    fun setupAnchorUi(transformWidgetModel: GltfModel) {
        val anchoredTransformWidgetEntity =
            GltfModelEntity.create(session, transformWidgetModel, Pose.Identity)
        val anchor =
            AnchorEntity.create(session, Dimensions(0.1f, 0.1f), PlaneType.ANY, PlaneSemantic.ANY)
        anchor.addChild(anchoredTransformWidgetEntity)
        anchoredTransformWidgetEntity.setPose(Pose.Identity)

        // Create another that is not anchored to see it move with the scene
        val unused = GltfModelEntity.create(session, transformWidgetModel, Pose.Identity)

        lifecycleScope.launch {
            val pi = 3.14159F
            val timeSource = TimeSource.Monotonic
            val startTime = timeSource.markNow()
            val rotateTimeMs = 10000F

            while (true) {
                delay(16L)
                val angle =
                    (2 * pi) * ((timeSource.markNow() - startTime).inWholeMilliseconds) /
                        rotateTimeMs

                val pos = Vector3(sin(angle), cos(angle), 0F)
                // Moving the activity space should not move the anchor.
                session.scene.activitySpace.setPose(Pose(pos))
            }
        }
    }
}
