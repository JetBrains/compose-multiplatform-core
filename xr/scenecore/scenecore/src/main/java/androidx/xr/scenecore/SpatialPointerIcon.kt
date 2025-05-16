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

package androidx.xr.scenecore

import androidx.annotation.RestrictTo

/** Specifies the pointer icon that is rendered in the spatialized scene. */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
public sealed class SpatialPointerIcon protected constructor(private val name: String)

/**
 * Do not render an icon for the pointer; this option can be used to hide the pointer icon, either
 * because the client wants it to be invisible or to implement custom icon rendering.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
public object SpatialPointerIconNone : SpatialPointerIcon("none")

/** Renders the icon for the pointer as a circle. */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
public object SpatialPointerIconCircle : SpatialPointerIcon("circle")
