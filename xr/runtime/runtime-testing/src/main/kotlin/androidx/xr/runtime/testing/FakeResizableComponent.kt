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
import androidx.xr.runtime.internal.Dimensions
import androidx.xr.runtime.internal.ResizableComponent
import androidx.xr.runtime.internal.ResizeEventListener
import java.util.concurrent.Executor

// TODO: b/405218432 - Implement this correctly instead of stubbing it out.
/** Fake implementation of [ResizableComponent] for testing. */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
public class FakeResizableComponent : ResizableComponent, FakeComponent() {

    override var size: Dimensions = Dimensions(0.0f, 0.0f, 0.0f)

    override var minimumSize: Dimensions = Dimensions(0.0f, 0.0f, 0.0f)

    override var maximumSize: Dimensions = Dimensions(0.0f, 0.0f, 0.0f)

    override var fixedAspectRatio: Float = 0.0f

    @get:Suppress("GetterSetterNames") override var autoHideContent: Boolean = false

    @get:Suppress("GetterSetterNames") override var autoUpdateSize: Boolean = false

    @get:Suppress("GetterSetterNames") override var forceShowResizeOverlay: Boolean = false

    @Suppress("ExecutorRegistration")
    override fun addResizeEventListener(
        executor: Executor,
        resizeEventListener: ResizeEventListener,
    ) {}

    override fun removeResizeEventListener(resizeEventListener: ResizeEventListener) {}
}
