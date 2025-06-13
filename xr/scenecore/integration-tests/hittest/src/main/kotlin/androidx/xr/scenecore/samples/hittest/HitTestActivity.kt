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

package androidx.xr.scenecore.samples.hittest

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.xr.runtime.Config
import androidx.xr.runtime.Config.HeadTrackingMode
import androidx.xr.runtime.Session
import androidx.xr.runtime.SessionCreateSuccess
import androidx.xr.runtime.math.IntSize2d
import androidx.xr.runtime.math.Pose
import androidx.xr.runtime.math.Quaternion
import androidx.xr.runtime.math.Vector3
import androidx.xr.scenecore.GltfModel
import androidx.xr.scenecore.GltfModelEntity
import androidx.xr.scenecore.InteractableComponent
import androidx.xr.scenecore.MovableComponent
import androidx.xr.scenecore.PanelEntity
import androidx.xr.scenecore.scene
import java.nio.file.Paths

class HitTestActivity : AppCompatActivity() {

    private val session by lazy { (Session.create(this) as SessionCreateSuccess).session }
    private var transformWidgetModel: GltfModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.hittest_activity)
        session.resume()
        session.configure(Config(headTracking = HeadTrackingMode.LAST_KNOWN))

        // Create a single panel with text
        @SuppressLint("InflateParams")
        val panelContentView = layoutInflater.inflate(R.layout.panel, null)
        val panelEntity =
            PanelEntity.create(
                session,
                panelContentView,
                IntSize2d(640, 480),
                "panel",
                Pose(Vector3(0f, -0.5f, 0.5f)),
            )
        panelEntity.parent = session.scene.activitySpace
        val movableComponent = MovableComponent.create(session)
        if (!panelEntity.addComponent(movableComponent)) {
            Log.e("HitTestActivity", "Error adding MovableComponent to panelEntity")
        }

        val transformWidgetModelFuture =
            GltfModel.createAsync(session, Paths.get("models", "xyzArrows.glb"))
        transformWidgetModelFuture.addListener(
            { transformWidgetModel = transformWidgetModelFuture.get() },
            Runnable::run,
        )

        val buttonHitTest: Button = panelContentView.findViewById(R.id.buttonHitTest)
        buttonHitTest.setOnClickListener {
            if (session.scene.spatialUser.head != null) {

                val hitTestFuture =
                    session.scene.spatialUser.head!!
                        .hitTestAsync(Vector3(), Vector3(0f, 0f, -1f))
                        .get()
                if (hitTestFuture.hitPosition != null && hitTestFuture.surfaceNormal != null) {
                    val updatedRotation =
                        Quaternion.fromLookTowards(
                            hitTestFuture.surfaceNormal!!,
                            session.scene.spatialUser.head!!
                                .transformPoseTo(
                                    Pose(Vector3(0f, 1f, 0f)),
                                    session.scene.activitySpace,
                                )
                                .translation,
                        )
                    val hitTestPose =
                        session.scene.spatialUser.head!!.transformPoseTo(
                            Pose(hitTestFuture.hitPosition!!, updatedRotation),
                            session.scene.activitySpace,
                        )
                    transformWidgetModel?.let {
                        val gltfEntity = GltfModelEntity.create(session, it, hitTestPose)
                        gltfEntity.parent = session.scene.activitySpace
                    }
                }
            }
        }

        val dragonModelFuture =
            GltfModel.createAsync(session, Paths.get("models", "Dragon_Evolved.gltf"))
        dragonModelFuture.addListener(
            {
                val dragonModel = dragonModelFuture.get()
                val gltfEntity =
                    GltfModelEntity.create(session, dragonModel, Pose(Vector3(1f, 0f, 0f)))
                gltfEntity.parent = session.scene.activitySpace
                val interactableComponent = InteractableComponent.create(session, mainExecutor) {}
                if (!gltfEntity.addComponent(interactableComponent)) {
                    Log.e("HitTestActivity", "Error adding InteractableComponent to gltfEntity")
                }
            },
            Runnable::run,
        )
    }
}
