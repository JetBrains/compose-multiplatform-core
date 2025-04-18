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

package androidx.xr.scenecore.samples.movable

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.CheckBox
import android.widget.Switch
import androidx.appcompat.app.AppCompatActivity
import androidx.xr.runtime.math.Pose
import androidx.xr.runtime.math.Ray
import androidx.xr.runtime.math.Vector3
import androidx.xr.scenecore.AnchorPlacement
import androidx.xr.scenecore.Entity
import androidx.xr.scenecore.MovableComponent
import androidx.xr.scenecore.MoveListener
import androidx.xr.scenecore.PanelEntity
import androidx.xr.scenecore.PermissionHelper
import androidx.xr.scenecore.PixelDimensions
import androidx.xr.scenecore.PlaneSemantic
import androidx.xr.scenecore.PlaneType
import androidx.xr.scenecore.Session
import java.util.concurrent.Executors

/**
 * A simple activity that creates a panel and attaches a movable component to it.
 *
 * <p>The movable panel has switched that can be toggled to enable and disable all the options of
 * the movable component. The panel can also be parented to a stationary panel that is offset from
 * the activity space. When system movable is disabled, the panel will still be moved but the
 * position will be set manually based on the results of the move event.
 *
 * <p>The purpose of this app is to enable easy internal testing of the movable component. Unlike
 * the InputMoveResize sample app this is not intended to be tested by QA at this time.
 */
class MovableActivity : AppCompatActivity() {

    private val session by lazy { Session.create(this) }
    private var systemMovable = false
    private var scaleInZ = false
    private var anchorable = false
    private var parentedToPanel = false
    private var movableComponent: MovableComponent? = null
    private val executor = Executors.newSingleThreadExecutor()
    private var planeTypeFilter: MutableSet<Int> = mutableSetOf()
    private var planeSemanticFilter: MutableSet<Int> = mutableSetOf()

    companion object {
        private const val TAG = "MovableActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.movable_activity)
        if (
            !PermissionHelper.hasPermission(this, PermissionHelper.SCENE_UNDERSTANDING_PERMISSION)
        ) {
            PermissionHelper.requestPermission(
                this,
                PermissionHelper.SCENE_UNDERSTANDING_PERMISSION,
                PermissionHelper.SCENE_UNDERSTANDING_PERMISSION_CODE,
            )
        }
        @SuppressLint("InflateParams")
        val stationaryPanelContentView = layoutInflater.inflate(R.layout.stationary_panel, null)
        val stationaryPanelEntity =
            PanelEntity.create(
                session,
                stationaryPanelContentView,
                PixelDimensions(640, 480),
                "stationaryPanel",
                Pose(Vector3(1f, 0f, 0f)),
            )

        // Create a single panel with text
        @SuppressLint("InflateParams")
        val movablePanelContentView = layoutInflater.inflate(R.layout.movable_panel, null)
        val movablePanelEntity =
            PanelEntity.create(
                session,
                movablePanelContentView,
                PixelDimensions(640, 880),
                "panel",
                Pose(Vector3(0f, 0f, 0.1f)),
            )
        val sysMovSwitch = movablePanelContentView.findViewById<Switch>(R.id.sys_mov_switch)
        sysMovSwitch.setOnCheckedChangeListener { _, isChecked: Boolean ->
            systemMovable = isChecked
            replaceMovableComponent(movablePanelEntity)
        }
        val scaleInZSwitch = movablePanelContentView.findViewById<Switch>(R.id.scale_in_z_switch)
        scaleInZSwitch.setOnCheckedChangeListener { _, isChecked: Boolean ->
            scaleInZ = isChecked
            replaceMovableComponent(movablePanelEntity)
        }
        val anchorableSwitch = movablePanelContentView.findViewById<Switch>(R.id.anchorable_switch)
        anchorableSwitch.setOnCheckedChangeListener { _, isChecked: Boolean ->
            anchorable = isChecked
            replaceMovableComponent(movablePanelEntity)
        }
        val parentSwitch = movablePanelContentView.findViewById<Switch>(R.id.parent_switch)
        parentSwitch.setOnCheckedChangeListener { _, isChecked: Boolean ->
            when (isChecked) {
                true -> movablePanelEntity.setParent(stationaryPanelEntity)
                false -> movablePanelEntity.setParent(session.activitySpace)
            }
            movablePanelEntity.setPose(Pose(Vector3(0f, 0f, 0.1f)))
        }

        setupAnchorPlacementCheckboxes(movablePanelContentView, movablePanelEntity)

        replaceMovableComponent(movablePanelEntity)
    }

    private fun setupAnchorPlacementCheckboxes(view: View, movablePanelEntity: Entity) {
        val planeTypeCheckboxMap =
            mapOf(
                view.findViewById<CheckBox>(R.id.planetype_any_checkbox) to PlaneType.ANY,
                view.findViewById<CheckBox>(R.id.planetype_horizontal_checkbox) to
                    PlaneType.HORIZONTAL,
                view.findViewById<CheckBox>(R.id.planetype_vertical_checkbox) to PlaneType.VERTICAL,
            )
        val planeSemanticCheckboxMap =
            mapOf(
                view.findViewById<CheckBox>(R.id.planesemantic_any_checkbox) to PlaneSemantic.ANY,
                view.findViewById<CheckBox>(R.id.planesemantic_wall_checkbox) to PlaneSemantic.WALL,
                view.findViewById<CheckBox>(R.id.planesemantic_ceiling_checkbox) to
                    PlaneSemantic.CEILING,
                view.findViewById<CheckBox>(R.id.planesemantic_table_checkbox) to
                    PlaneSemantic.TABLE,
                view.findViewById<CheckBox>(R.id.planesemantic_floor_checkbox) to
                    PlaneSemantic.FLOOR,
            )

        for ((planeView, planeType) in planeTypeCheckboxMap) {
            if (planeView.isChecked) {
                planeTypeFilter.add(planeType)
            }
            planeView.setOnCheckedChangeListener { _, isChecked: Boolean ->
                when (isChecked) {
                    true -> planeTypeFilter.add(planeType)
                    false -> planeTypeFilter.remove(planeType)
                }
                replaceMovableComponent(movablePanelEntity)
            }
        }

        for ((planeView, planeSemantic) in planeSemanticCheckboxMap) {
            if (planeView.isChecked) {
                planeSemanticFilter.add(planeSemantic)
            }
            planeView.setOnCheckedChangeListener { _, isChecked: Boolean ->
                when (isChecked) {
                    true -> planeSemanticFilter.add(planeSemantic)
                    false -> planeSemanticFilter.remove(planeSemantic)
                }
                replaceMovableComponent(movablePanelEntity)
            }
        }
    }

    private fun replaceMovableComponent(movablePanelEntity: Entity) {
        movableComponent?.let { movablePanelEntity.removeComponent(it) }
        val anchorPlacementSet: MutableSet<AnchorPlacement> = mutableSetOf()
        if (anchorable) {
            anchorPlacementSet.add(
                AnchorPlacement.createForPlanes(planeTypeFilter, planeSemanticFilter)
            )
        }

        movableComponent =
            MovableComponent.create(session, systemMovable, scaleInZ, anchorPlacementSet)
        movableComponent?.let {
            if (!movablePanelEntity.addComponent(it)) {
                Log.e(TAG, "Error adding Movable component to parentedMovableEntity")
            }
            it.addMoveListener(
                executor,
                object : MoveListener {
                    override fun onMoveUpdate(
                        entity: Entity,
                        currentInputRay: Ray,
                        currentPose: Pose,
                        currentScale: Float,
                    ) {
                        if (!systemMovable) {
                            entity.setPose(currentPose)
                            entity.setScale(currentScale)
                        }
                    }

                    override fun onMoveEnd(
                        entity: Entity,
                        finalInputRay: Ray,
                        finalPose: Pose,
                        finalScale: Float,
                        updatedParent: Entity?,
                    ) {
                        if (!systemMovable) {
                            entity.setPose(finalPose)
                            entity.setScale(finalScale)
                        }
                        if (updatedParent != null) {
                            Log.i(TAG, "Panel parent is updated to: $updatedParent")
                        }
                    }
                },
            )
        }
    }
}
