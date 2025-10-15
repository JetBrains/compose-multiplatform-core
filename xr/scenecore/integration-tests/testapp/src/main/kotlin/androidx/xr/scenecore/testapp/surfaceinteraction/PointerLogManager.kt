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

package androidx.xr.scenecore.testapp.surfaceinteraction

import android.content.Context
import android.graphics.Color
import android.util.Log
import android.widget.LinearLayout
import androidx.xr.runtime.Session
import androidx.xr.runtime.math.IntSize2d
import androidx.xr.runtime.math.Vector3
import androidx.xr.scenecore.InputEvent
import androidx.xr.scenecore.PanelEntity
import androidx.xr.scenecore.testapp.R
import androidx.xr.scenecore.testapp.common.DebugTextLinearView

class PointerLogManager(context: Context, session: Session) {
    interface PointerState {
        val isValid: Boolean
        val validPanel: PanelEntity?
        val origin: Vector3
        val direction: Vector3
    }

    private val defaultImpl: PointerStateImpl
        get() = ptrStates[InputEvent.Pointer.POINTER_TYPE_DEFAULT]

    private val leftImpl: PointerStateImpl
        get() = ptrStates[InputEvent.Pointer.POINTER_TYPE_LEFT]

    private val rightImpl: PointerStateImpl
        get() = ptrStates[InputEvent.Pointer.POINTER_TYPE_RIGHT]

    val default: PointerState
        get() = defaultImpl

    val left: PointerState
        get() = leftImpl

    val right: PointerState
        get() = rightImpl

    private val ptrStates =
        mutableListOf<PointerStateImpl>(
            PointerStateImpl(context, session, InputEvent.Pointer.POINTER_TYPE_DEFAULT),
            PointerStateImpl(context, session, InputEvent.Pointer.POINTER_TYPE_LEFT),
            PointerStateImpl(context, session, InputEvent.Pointer.POINTER_TYPE_RIGHT),
        )

    fun update(inputEvent: InputEvent) {
        if (inputEvent.pointerType !in 0..2) {
            Log.e(TAG, "Invalid pointer type: ${inputEvent.pointerType}")
            return
        }
        ptrStates[inputEvent.pointerType].update(inputEvent)
    }

    fun reset() {
        defaultImpl.reset()
        leftImpl.reset()
        rightImpl.reset()
    }

    fun getLog(): String {
        var str = ""
        if (rightImpl.isValid) {
            str += rightImpl.toString() + "\n"
        }
        if (leftImpl.isValid) {
            str += leftImpl.toString() + "\n"
        }
        if (defaultImpl.isValid) {
            str += defaultImpl.toString() + "\n"
        }
        return str
    }

    private class PointerStateImpl(val context: Context, val session: Session, pointerType: Int) :
        PointerState {
        private val pointerTypeStr = pointerType.toPointerTypeString()
        private var source = -1
        private var action = -1
        private var actionCount = mutableMapOf<Int, Int>()
        private var hitInfos = mutableListOf<InputEvent.HitInfo>()
        private var hitInfoCount = 0
        private var panelView: DebugTextLinearView? = null
        private var panelBackground: LinearLayout? = null
        private var panelEntity: PanelEntity? = null

        override val isValid
            get() = source != -1

        override val validPanel
            get() = if (isValid) panelEntity else null

        override var origin = Vector3.Zero

        override var direction = Vector3.Zero

        fun update(inputEvent: InputEvent) {
            val validChanged = source == -1 && inputEvent.source != -1
            val sourceChanged = source != inputEvent.source
            val actionChanged = action != inputEvent.action

            source = inputEvent.source
            action = inputEvent.action
            origin = inputEvent.origin
            direction = inputEvent.direction
            actionCount[action] = actionCount.getOrDefault(action, 0) + 1

            this.hitInfos.clear()
            if (!inputEvent.hitInfoList.isEmpty()) {
                hitInfoCount++
                for (hitInfo in inputEvent.hitInfoList) {
                    this.hitInfos.add(hitInfo)
                }
            }

            if (validChanged) {
                panelView = DebugTextLinearView(context)
                panelBackground = panelView!!.findViewById(R.id.debugTextPanel)
                panelEntity =
                    PanelEntity.create(
                        session = session,
                        view = panelView!!,
                        pixelDimensions = IntSize2d(800, 360),
                        name = "DebugPanel$pointerTypeStr",
                    )
                panelEntity!!.setScale(0.05f)
            }
            if (sourceChanged) {
                panelView!!.setName("$pointerTypeStr ${source.toSourceString()}")
            }
            if (actionChanged) {
                when (action) {
                    InputEvent.Action.ACTION_HOVER_ENTER -> {
                        panelEntity!!.setEnabled(true)
                        panelBackground!!.setBackgroundColor(Color.YELLOW)
                        panelView!!.setLine("State", "HOVER")
                    }

                    InputEvent.Action.ACTION_DOWN -> {
                        panelEntity!!.setEnabled(true)
                        panelBackground!!.setBackgroundColor(Color.GREEN)
                        panelView!!.setLine("State", "PRESS")
                    }

                    InputEvent.Action.ACTION_UP,
                    InputEvent.Action.ACTION_CANCEL,
                    InputEvent.Action.ACTION_HOVER_EXIT -> panelEntity!!.setEnabled(false)
                }
            }
        }

        fun reset() {
            // if become invalid, disable panel
            if (source != -1) {
                panelEntity!!.setEnabled(false)
            }

            source = -1
            action = -1
            actionCount.clear()
            hitInfos.clear()
            hitInfoCount = 0
        }

        private fun getActionCountStr(action: Int): String {
            val count = actionCount.getOrDefault(action, 0)
            return String.format("%02d", count % 100)
        }

        private fun Vector3.toShortString(): String {
            return String.format("[%.2f, %.2f, %.2f]", x, y, z)
        }

        override fun toString(): String {
            var str =
                "$pointerTypeStr ${source.toSourceString()}: org${origin.toShortString()}" +
                    " dir${direction.toShortString()}"
            str +=
                "\n    [PRESS]" +
                    " DOWN: " +
                    getActionCountStr(InputEvent.Action.ACTION_DOWN) +
                    " MOVE: " +
                    getActionCountStr(InputEvent.Action.ACTION_MOVE) +
                    " UP: " +
                    getActionCountStr(InputEvent.Action.ACTION_UP) +
                    " CANCEL: " +
                    getActionCountStr(InputEvent.Action.ACTION_CANCEL)
            str +=
                "\n    [HOVER]" +
                    " ENTER: " +
                    getActionCountStr(InputEvent.Action.ACTION_HOVER_ENTER) +
                    " MOVE: " +
                    getActionCountStr(InputEvent.Action.ACTION_HOVER_MOVE) +
                    " EXIT: " +
                    getActionCountStr(InputEvent.Action.ACTION_HOVER_EXIT)

            str += "\n    [HIT_INFO] "
            if (hitInfos.isEmpty()) {
                str += "None"
            } else {
                str += String.format("%02d", hitInfoCount % 100)
                for (i in 0..hitInfos.size - 1) {
                    str += " [${i}]:"
                    str += (hitInfos[i].inputEntity.javaClass.simpleName ?: "null") + "@"
                    str +=
                        if (hitInfos[i].hitPosition == null) "[null]"
                        else hitInfos[i].hitPosition!!.toShortString()
                }
            }
            return str
        }
    }

    companion object {
        fun Int.toActionString(): String =
            when (this) {
                InputEvent.Action.ACTION_DOWN -> "DOWN"
                InputEvent.Action.ACTION_UP -> "UP"
                InputEvent.Action.ACTION_MOVE -> "MOVE"
                InputEvent.Action.ACTION_CANCEL -> "CANCEL"
                InputEvent.Action.ACTION_HOVER_ENTER -> "HOVER_ENTER"
                InputEvent.Action.ACTION_HOVER_MOVE -> "HOVER_MOVE"
                InputEvent.Action.ACTION_HOVER_EXIT -> "HOVER_EXIT"
                else -> "UNKNOWN_ACTION"
            }

        fun Int.toSourceString(): String =
            when (this) {
                InputEvent.Source.SOURCE_UNKNOWN -> "UNKNOWN"
                InputEvent.Source.SOURCE_HEAD -> "HEAD"
                InputEvent.Source.SOURCE_CONTROLLER -> "CONTROLLER"
                InputEvent.Source.SOURCE_HANDS -> "HANDS"
                InputEvent.Source.SOURCE_MOUSE -> "MOUSE"
                InputEvent.Source.SOURCE_GAZE_AND_GESTURE -> "GAZE_GESTURE"
                else -> "UNKNOWN_SOURCE"
            }

        fun Int.toPointerTypeString(): String =
            when (this) {
                InputEvent.Pointer.POINTER_TYPE_DEFAULT -> "DEFAULT"
                InputEvent.Pointer.POINTER_TYPE_LEFT -> "LEFT"
                InputEvent.Pointer.POINTER_TYPE_RIGHT -> "RIGHT"
                else -> "UNKNOWN_POINTER"
            }
    }
}
