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

package androidx.xr.runtime.testing

import androidx.annotation.RestrictTo
import androidx.xr.runtime.internal.Component
import androidx.xr.runtime.internal.Entity
import androidx.xr.runtime.internal.InputEventListener
import androidx.xr.runtime.internal.SpaceValue
import androidx.xr.runtime.math.Pose
import androidx.xr.runtime.math.Vector3
import java.util.concurrent.Executor

// TODO: b/405218432 - Implement this correctly instead of stubbing it out.
/** Test-only implementation of [Entity] */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
public open class FakeEntity : Entity, FakeActivityPose() {
    override val children: List<Entity> = emptyList()

    override var parent: Entity? = null

    override var contentDescription: String = ""

    override fun setHidden(hidden: Boolean): Unit {}

    override fun addChild(child: Entity): Unit {}

    override fun getPose(@SpaceValue relativeTo: Int): Pose = Pose()

    override fun setPose(pose: Pose, @SpaceValue relativeTo: Int) {}

    override fun getScale(@SpaceValue relativeTo: Int): Vector3 = Vector3()

    override fun setScale(scale: Vector3, @SpaceValue relativeTo: Int) {}

    override fun getAlpha(@SpaceValue relativeTo: Int): Float = 1.0f

    override fun setAlpha(alpha: Float, @SpaceValue relativeTo: Int) {}

    override fun addChildren(children: List<Entity>): Unit {}

    override fun isHidden(includeParents: Boolean): Boolean = false

    @Suppress("ExecutorRegistration")
    override fun addInputEventListener(executor: Executor, listener: InputEventListener) {}

    override fun removeInputEventListener(listener: InputEventListener) {}

    override fun dispose() {}

    override fun addComponent(component: Component): Boolean = true

    override fun getComponents(): List<Component> = emptyList()

    override fun <T : Component> getComponentsOfType(type: Class<out T>): List<T> = emptyList()

    override fun removeComponent(component: Component) {}

    override fun removeAllComponents() {}
}
