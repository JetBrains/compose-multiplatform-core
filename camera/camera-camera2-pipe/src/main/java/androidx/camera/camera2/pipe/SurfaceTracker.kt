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

package androidx.camera.camera2.pipe

import android.view.Surface
import androidx.annotation.RestrictTo

/**
 * A SurfaceTracker tracks the current usage of [Surface]s at the CameraGraph level. It keeps track
 * of the surface usages through the tokens acquired from [CameraSurfaceManager].
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public interface SurfaceTracker {
    /**
     * Disconnect and releases the current SurfaceTokens. Intended to be used when a camera is
     * closed due to disconnect, error or just simple camera shutdown. Note that after this call, no
     * tokens are going to be acquired for new Surfaces until [registerAllSurfaces] is called.
     */
    public fun unregisterAllSurfaces()

    /**
     * Reacquires the SurfaceTokens for the currently configured Surfaces. Intended to be used when
     * the camera is restarted and the Surfaces are used again.
     */
    public fun registerAllSurfaces()
}
