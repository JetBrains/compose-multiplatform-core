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

package androidx.xr.runtime.testing

import android.app.Activity
import androidx.kruth.assertThat
import androidx.xr.runtime.internal.RenderingEntityFactory
import androidx.xr.runtime.internal.RenderingRuntime
import androidx.xr.runtime.internal.SceneRuntime
import com.google.common.truth.Truth
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FakeRenderingRuntimeTest {
    private lateinit var sceneRuntime: SceneRuntime
    private lateinit var renderingRuntime: RenderingRuntime
    private lateinit var fakeRenderingRuntime: FakeRenderingRuntime

    @Before
    fun setUp() {
        val activityController = Robolectric.buildActivity<Activity?>(Activity::class.java)
        val activity: Activity? = activityController.create().start().get()

        assertThat(activity).isNotNull()

        sceneRuntime = FakeSceneRuntime()
        fakeRenderingRuntime = FakeRenderingRuntime(sceneRuntime as RenderingEntityFactory)
        renderingRuntime = fakeRenderingRuntime
    }

    @After
    fun tearDown() {
        renderingRuntime.dispose()
        sceneRuntime.dispose()
    }

    @Test
    fun setReflectionTexture_checkReturnedValue() {
        check(fakeRenderingRuntime.reflectionTexture == null)

        val resource = FakeResource(0)
        fakeRenderingRuntime.reflectionTexture = resource

        Truth.assertThat(renderingRuntime.borrowReflectionTexture()).isEqualTo(resource)
    }

    @Test
    fun destroyTexture_checkReflectionTexture() {
        check(fakeRenderingRuntime.reflectionTexture == null)

        val resource = FakeResource(0)
        fakeRenderingRuntime.reflectionTexture = resource
        renderingRuntime.destroyTexture(resource)

        Truth.assertThat(fakeRenderingRuntime.reflectionTexture).isNull()
    }
}
