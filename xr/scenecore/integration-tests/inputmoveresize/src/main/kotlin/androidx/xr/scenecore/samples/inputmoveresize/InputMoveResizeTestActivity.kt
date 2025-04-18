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

package androidx.xr.scenecore.samples.inputmoveresize

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.CheckBox
import android.widget.CompoundButton
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.xr.runtime.math.Pose
import androidx.xr.runtime.math.Ray
import androidx.xr.runtime.math.Vector3
import androidx.xr.scenecore.BasePanelEntity
import androidx.xr.scenecore.Dimensions
import androidx.xr.scenecore.Entity
import androidx.xr.scenecore.InputEvent
import androidx.xr.scenecore.InteractableComponent
import androidx.xr.scenecore.MovableComponent
import androidx.xr.scenecore.MoveListener
import androidx.xr.scenecore.PanelEntity
import androidx.xr.scenecore.PixelDimensions
import androidx.xr.scenecore.ResizableComponent
import androidx.xr.scenecore.ResizeListener
import androidx.xr.scenecore.Session
import java.util.concurrent.Executors

class InputMoveResizeTestActivity : AppCompatActivity() {

    private val session by lazy { Session.create(this) }
    private val executor by lazy { Executors.newSingleThreadExecutor() }
    private var interactablePanelActive = false
    private var movablePanelActive = false
    private var resizablePanelActive = false
    private var mainPanelMovableActive = false
    private var mainPanelResizableActive = false

    private val moveListener =
        object : MoveListener {
            override fun onMoveStart(
                entity: Entity,
                initialInputRay: Ray,
                initialPose: Pose,
                initialScale: Float,
                initialParent: Entity,
            ) {
                Log.i(TAG, "$entity $initialInputRay $initialPose $initialScale $initialParent")
            }

            override fun onMoveUpdate(
                entity: Entity,
                currentInputRay: Ray,
                currentPose: Pose,
                currentScale: Float,
            ) {
                Log.i(TAG, "$entity $currentInputRay$currentPose $currentScale")
                updatePoseAndScale(entity, currentPose, currentScale)
            }

            override fun onMoveEnd(
                entity: Entity,
                finalInputRay: Ray,
                finalPose: Pose,
                finalScale: Float,
                updatedParent: Entity?,
            ) {
                Log.i(TAG, "$entity $finalInputRay $finalPose $finalScale $updatedParent")
                updatePoseAndScale(entity, finalPose, finalScale)
            }
        }

    private val resizeListener =
        object : ResizeListener {
            override fun onResizeStart(entity: Entity, originalSize: Dimensions) {
                Log.i(TAG, "$entity $originalSize")
            }

            override fun onResizeUpdate(entity: Entity, newSize: Dimensions) {
                Log.i(TAG, "$entity $newSize")
            }

            override fun onResizeEnd(entity: Entity, finalSize: Dimensions) {
                Log.i(TAG, "$entity $finalSize")
                (entity as BasePanelEntity<*>).setSize(finalSize)
            }
        }

    companion object {
        private const val TAG = "InputMoveResizeTest"
    }

    private fun updatePoseAndScale(entity: Entity, pose: Pose, scale: Float) {
        Log.i(TAG, "$entity $pose $scale")
        entity.setPose(pose)
        entity.setScale(scale)
    }

    private fun updateTextInPanel(text: String, panel: View) {
        val textView = panel.findViewById<TextView>(R.id.textView)
        textView.text = text
    }

    private fun createPanelEntityWithText(text: String, panel: View): PanelEntity {
        updateTextInPanel(text, panel)
        val switch = panel.findViewById<Switch>(R.id.switch1)
        val switchText = "$text Switch"
        switch.text = switchText

        val panelEntity =
            PanelEntity.create(
                session,
                panel,
                PixelDimensions(640, 480),
                "panel",
                Pose(Vector3(0f, -0.5f, 0.5f)),
            )
        panelEntity.setParent(session.activitySpace)
        return panelEntity
    }

    private fun changeTextAndBGColor(textView: TextView) {
        val backgroundColor = (Math.random() * 0xffffff).toInt()
        textView.setBackgroundColor((backgroundColor + 0xff000000).toInt())
        textView.setTextColor((0xffffffff - backgroundColor).toInt())
    }

    /** Setup the main panel to be movable and resizable. */
    private fun setupMainPanel() {
        val mainPanelSystemMovable = findViewById<CheckBox>(R.id.systemMovable)
        mainPanelSystemMovable.isChecked = true
        val mainPanelScaleInZ = findViewById<CheckBox>(R.id.scaleInZ)
        mainPanelScaleInZ.isChecked = true
        var mainPanelMovableComponent = MovableComponent.create(session)
        mainPanelMovableComponent.size = session.mainPanelEntity.getSize()

        fun updateMainPanelMovableComponent() {
            if (mainPanelMovableActive) {
                session.mainPanelEntity.removeComponent(mainPanelMovableComponent)
            }
            mainPanelMovableComponent =
                MovableComponent.create(
                    session,
                    mainPanelSystemMovable.isChecked,
                    mainPanelScaleInZ.isChecked,
                    emptySet(),
                )

            when (mainPanelSystemMovable.isChecked) {
                true -> mainPanelMovableComponent.removeMoveListener(moveListener)
                false -> mainPanelMovableComponent.addMoveListener(executor, moveListener)
            }
        }

        val mainPanelCheckBoxListener =
            CompoundButton.OnCheckedChangeListener { _, _ ->
                updateMainPanelMovableComponent()
                mainPanelMovableActive =
                    session.mainPanelEntity.addComponent(mainPanelMovableComponent)
            }

        mainPanelSystemMovable.setOnCheckedChangeListener(mainPanelCheckBoxListener)
        mainPanelScaleInZ.setOnCheckedChangeListener(mainPanelCheckBoxListener)

        val mainPanelMovableSwitch = findViewById<Switch>(R.id.movableSwitch)
        mainPanelMovableSwitch.setOnCheckedChangeListener { _, isChecked ->
            mainPanelMovableComponent.size = session.mainPanelEntity.getSize()
            when (isChecked) {
                true -> {
                    updateMainPanelMovableComponent()
                    mainPanelMovableActive =
                        session.mainPanelEntity.addComponent(mainPanelMovableComponent)
                    mainPanelSystemMovable.visibility = View.VISIBLE
                    mainPanelScaleInZ.visibility = View.VISIBLE
                }
                false -> {
                    if (mainPanelMovableActive) {
                        session.mainPanelEntity.removeComponent(mainPanelMovableComponent)
                    }
                    mainPanelSystemMovable.visibility = View.GONE
                    mainPanelScaleInZ.visibility = View.GONE
                }
            }
        }

        val mainPanelResizableComponent = ResizableComponent.create(session)
        mainPanelResizableComponent.size = session.mainPanelEntity.getSize()
        mainPanelResizableComponent.addResizeListener(mainExecutor, resizeListener)

        val mainPanelAnyAspectRatioButton = findViewById<RadioButton>(R.id.radioButton1)
        mainPanelAnyAspectRatioButton.text = getString(R.string.any_aspect_ratio_label)
        val mainPanelPortraitAspectRadioButton = findViewById<RadioButton>(R.id.radioButton2)
        mainPanelPortraitAspectRadioButton.text = getString(R.string.portrait_label)
        val mainPanelLandscapeAspectRadioButton = findViewById<RadioButton>(R.id.radioButton3)
        mainPanelLandscapeAspectRadioButton.text = getString(R.string.landscape_label)
        val mainPanelAspectRatioRadioGroup = findViewById<RadioGroup>(R.id.radioGroup1)
        mainPanelAspectRatioRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            mainPanelResizableComponent.fixedAspectRatio =
                when (checkedId) {
                    R.id.radioButton2 -> 0.7f
                    R.id.radioButton3 -> 1.4f
                    // A negative ratio means "no preferences."
                    else -> -12.345f
                }
        }
        mainPanelResizableComponent.fixedAspectRatio = 0.0f // no preferences initially

        val mainPanelResizableSwitch = findViewById<Switch>(R.id.resizableSwitch)
        mainPanelResizableSwitch.setOnCheckedChangeListener { _, isChecked ->
            mainPanelResizableComponent.size = session.mainPanelEntity.getSize()
            when (isChecked) {
                true ->
                    mainPanelResizableActive =
                        session.mainPanelEntity.addComponent(mainPanelResizableComponent)
                false ->
                    if (mainPanelResizableActive) {
                        session.mainPanelEntity.removeComponent(mainPanelResizableComponent)
                    }
            }
            mainPanelAspectRatioRadioGroup.visibility = if (isChecked) View.VISIBLE else View.GONE
        }
    }

    @Suppress("UNUSED_VARIABLE")
    @SuppressLint("InflateParams")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.inputmoveresizetestactivity)
        setupMainPanel()

        // Create the movable spatial panel.
        val movablePanelView = layoutInflater.inflate(R.layout.panel, null)
        val movablePanelEntity = createPanelEntityWithText("Movable", movablePanelView)
        movablePanelEntity.setPose(Pose(Vector3(-0.8f, 0.2f, 0.1f)))
        movablePanelEntity.setParent(session.mainPanelEntity)
        // Set the movable panel corner radius to 0.
        movablePanelEntity.setCornerRadius(0.0f)

        val systemMovableCheckbox = movablePanelView.findViewById<CheckBox>(R.id.systemMovable)
        val scaleInZCheckBox = movablePanelView.findViewById<CheckBox>(R.id.scaleInZ)

        systemMovableCheckbox.isChecked = true
        scaleInZCheckBox.isChecked = true

        var movablePanelComponent = MovableComponent.create(session)
        movablePanelComponent.size = movablePanelEntity.getSize()
        fun updateMovablePanelComponent() {
            if (movablePanelActive) {
                movablePanelEntity.removeComponent(movablePanelComponent)
            }
            movablePanelComponent =
                MovableComponent.create(
                    session,
                    systemMovableCheckbox.isChecked,
                    scaleInZCheckBox.isChecked,
                )
            when (systemMovableCheckbox.isChecked) {
                true -> movablePanelComponent.removeMoveListener(moveListener)
                else -> movablePanelComponent.addMoveListener(executor, moveListener)
            }
        }
        val checkBoxListener =
            CompoundButton.OnCheckedChangeListener { _, _ ->
                updateMovablePanelComponent()
                movablePanelActive = movablePanelEntity.addComponent(movablePanelComponent)
            }

        systemMovableCheckbox.setOnCheckedChangeListener(checkBoxListener)
        scaleInZCheckBox.setOnCheckedChangeListener(checkBoxListener)

        val movablePanelSwitch = movablePanelView.findViewById<Switch>(R.id.switch1)
        movablePanelSwitch.text = getString(R.string.movable_label)
        movablePanelSwitch.setOnCheckedChangeListener { _, isChecked ->
            movablePanelComponent.size = movablePanelEntity.getSize()
            when (isChecked) {
                true -> {
                    updateMovablePanelComponent()
                    movablePanelActive = movablePanelEntity.addComponent(movablePanelComponent)
                    systemMovableCheckbox.visibility = View.VISIBLE
                    scaleInZCheckBox.visibility = View.VISIBLE
                }
                false -> {
                    if (movablePanelActive) {
                        movablePanelEntity.removeComponent(movablePanelComponent)
                    }
                    systemMovableCheckbox.visibility = View.GONE
                    scaleInZCheckBox.visibility = View.GONE
                }
            }
        }

        // Create a spatial panel with all components.
        val everythingPanelView = layoutInflater.inflate(R.layout.panel, null)
        val everythingPanelEntity = createPanelEntityWithText("Everything", everythingPanelView)
        everythingPanelEntity.setParent(movablePanelEntity)
        everythingPanelEntity.setPose(Pose(Vector3(0.0f, -0.5f, 0.0f)))
        // Set the everything panel corner radius to 0.
        everythingPanelEntity.setCornerRadius(0.0f)

        val everythingPanelSwitch = everythingPanelView.findViewById<Switch>(R.id.switch1)
        val everythingPanelInteractableComponent =
            InteractableComponent.create(session, executor) {
                Log.i(TAG, "input event $it")
                if (it.action == InputEvent.ACTION_DOWN) {
                    changeTextAndBGColor(everythingPanelView.findViewById(R.id.textView))
                }
            }
        val everythingPanelMovableComponent = MovableComponent.create(session)
        everythingPanelMovableComponent.size = everythingPanelEntity.getSize()
        val everythingPanelResizeComponent = ResizableComponent.create(session)
        everythingPanelResizeComponent.size = everythingPanelEntity.getSize()
        everythingPanelResizeComponent.addResizeListener(mainExecutor, resizeListener)
        everythingPanelSwitch.setOnCheckedChangeListener { _, isChecked ->
            everythingPanelMovableComponent.size = everythingPanelEntity.getSize()
            everythingPanelResizeComponent.size = everythingPanelEntity.getSize()
            when (isChecked) {
                true -> {
                    checkNotNull(
                        everythingPanelEntity.addComponent(everythingPanelInteractableComponent)
                    ) {
                        "Component is null"
                    }
                    checkNotNull(
                        everythingPanelEntity.addComponent(everythingPanelMovableComponent)
                    ) {
                        "Component is null"
                    }
                    checkNotNull(
                        everythingPanelEntity.addComponent(everythingPanelResizeComponent)
                    ) {
                        "Component is null"
                    }
                }
                false -> {
                    everythingPanelEntity.removeAllComponents()
                }
            }
        }

        // Create a resizable spatial panel.
        val resizablePanelView = layoutInflater.inflate(R.layout.panel, null)
        val resizablePanelEntity = createPanelEntityWithText("Resizable", resizablePanelView)
        resizablePanelEntity.setPose(Pose(Vector3(0.9f, 0.2f, -0.1f)))
        resizablePanelEntity.setParent(session.mainPanelEntity)
        // Set the resizable panel corner radius to 0.
        resizablePanelEntity.setCornerRadius(0.0f)

        val resizablePanelComponent = ResizableComponent.create(session)
        resizablePanelComponent.size = resizablePanelEntity.getSize()
        resizablePanelComponent.addResizeListener(mainExecutor, resizeListener)

        val anyAspectRatioButton = resizablePanelView.findViewById<RadioButton>(R.id.radioButton1)
        anyAspectRatioButton.text = getString(R.string.any_aspect_ratio_label)
        val portraitAspectRadioButton =
            resizablePanelView.findViewById<RadioButton>(R.id.radioButton2)
        portraitAspectRadioButton.text = getString(R.string.portrait_label)
        val landscapeAspectRadioButton =
            resizablePanelView.findViewById<RadioButton>(R.id.radioButton3)
        landscapeAspectRadioButton.text = getString(R.string.landscape_label)
        val aspectRatioRadioGroup = resizablePanelView.findViewById<RadioGroup>(R.id.radioGroup1)
        aspectRatioRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            resizablePanelComponent.fixedAspectRatio =
                when (checkedId) {
                    R.id.radioButton2 -> 0.7f
                    R.id.radioButton3 -> 1.4f
                    // A negative ratio means "no preferences."
                    else -> -12.345f
                }
        }
        resizablePanelComponent.fixedAspectRatio = 0.0f // no preferences initially

        val resizablePanelSwitch = resizablePanelView.findViewById<Switch>(R.id.switch1)
        resizablePanelSwitch.setOnCheckedChangeListener { _, isChecked ->
            resizablePanelComponent.size = resizablePanelEntity.getSize()
            when (isChecked) {
                true ->
                    resizablePanelActive =
                        resizablePanelEntity.addComponent(resizablePanelComponent)
                false ->
                    if (resizablePanelActive) {
                        resizablePanelEntity.removeComponent(resizablePanelComponent)
                    }
            }
            aspectRatioRadioGroup.visibility = if (isChecked) View.VISIBLE else View.GONE
        }

        // Create a interactable spatial panel.
        val interactablePanelView = layoutInflater.inflate(R.layout.panel, null)
        val interactablePanelEntity =
            createPanelEntityWithText("Interactable", interactablePanelView)
        interactablePanelEntity.setParent(resizablePanelEntity)
        interactablePanelEntity.setPose(Pose(Vector3(0f, -0.5f, 0.0f)))
        // Set the interactable panel corner radius to 0.
        interactablePanelEntity.setCornerRadius(0.0f)

        val interactablePanelTextView = interactablePanelView.findViewById<TextView>(R.id.textView)
        val interactableComponent =
            InteractableComponent.create(session, mainExecutor) {
                Log.i(TAG, "input event $it")
                if (it.action == InputEvent.ACTION_DOWN) {
                    changeTextAndBGColor(interactablePanelTextView)
                }
            }
        val interactablePanelSwitch = interactablePanelView.findViewById<Switch>(R.id.switch1)
        interactablePanelSwitch.setOnCheckedChangeListener { _, isChecked ->
            when (isChecked) {
                true ->
                    interactablePanelActive =
                        interactablePanelEntity.addComponent(interactableComponent)
                false ->
                    if (interactablePanelActive) {
                        interactablePanelEntity.removeComponent(interactableComponent)
                    }
            }
        }
    }
}
