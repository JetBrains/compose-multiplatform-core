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

package androidx.xr.compose.integration.layout.animationexplorationapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.xr.compose.spatial.Subspace
import androidx.xr.compose.subspace.MovePolicy
import androidx.xr.compose.subspace.ResizePolicy
import androidx.xr.compose.subspace.SpatialColumn
import androidx.xr.compose.subspace.SpatialCurvedRow
import androidx.xr.compose.subspace.SpatialPanel
import androidx.xr.compose.subspace.layout.SubspaceModifier
import androidx.xr.compose.subspace.layout.fillMaxSize
import androidx.xr.compose.subspace.layout.padding

class SampleAnimationsActivity : ComponentActivity() {

    enum class AnimationStyle {
        SequentialExample,
        ConcurrentExample1,
        ConcurrentExample2,
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val (animationStyle, setAnimationStyle) =
                remember { mutableStateOf(AnimationStyle.SequentialExample) }

            Subspace {
                SpatialCurvedRow(modifier = SubspaceModifier.fillMaxSize(), curveRadius = 1025.dp) {
                    SpatialPanel(
                        modifier = SubspaceModifier.padding(50.dp),
                        resizePolicy = ResizePolicy(true),
                        dragPolicy = MovePolicy(true),
                    ) {
                        Column(
                            modifier = Modifier.background(Color.LightGray).padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Button(
                                onClick = { setAnimationStyle(AnimationStyle.SequentialExample) }
                            ) {
                                Text(text = "Sequential animation")
                            }
                            Button(
                                onClick = { setAnimationStyle(AnimationStyle.ConcurrentExample1) }
                            ) {
                                Text(text = "Concurrent animation")
                            }
                            Button(
                                onClick = { setAnimationStyle(AnimationStyle.ConcurrentExample2) }
                            ) {
                                Text(text = "Concurrent animation 2")
                            }
                        }
                    }
                    SpatialColumn(modifier = SubspaceModifier.padding(50.dp)) {
                        when (animationStyle) {
                            AnimationStyle.SequentialExample -> {
                                SequentialAnimationExample()
                            }
                            AnimationStyle.ConcurrentExample1 -> {
                                ConcurrentAnimationExample1()
                            }
                            AnimationStyle.ConcurrentExample2 -> {
                                ConcurrentAnimationExample2()
                            }
                        }
                    }
                }
            }
        }
    }
}
