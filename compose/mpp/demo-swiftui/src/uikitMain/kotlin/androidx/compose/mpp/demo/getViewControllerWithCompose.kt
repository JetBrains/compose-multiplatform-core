/*
 * Copyright 2022 The Android Open Source Project
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

package androidx.compose.mpp.demo

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.UIKitView
import androidx.compose.ui.viewinterop.UIKitViewController
import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UILabel
import platform.UIKit.UIView
import platform.UIKit.UIViewController

// TODO This module is just a proxy to run the demo from mpp:demo. Figure out how to get rid of it.
//  If it is removed, there is no available configuration in IDE
fun getViewControllerWithCompose(
    testSimpleConstrains: () -> UIViewController,
    testComplexConstrains: () -> UIViewController,
    testSimpleSwiftUI: () -> UIViewController,
    testComplexSwiftUI: () -> UIViewController
) = ComposeUIViewController {
    var widthProgress by remember { mutableStateOf(0.5f) }
    Column(modifier = Modifier.safeDrawingPadding()) {
        Slider(widthProgress, onValueChange = {
            widthProgress = it
            println("widthProgress: $widthProgress")
        }, modifier = Modifier.fillMaxWidth())

        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            // IosDemo(arg)
            Column {
                Text("Simple constraints")
                Row {
                    Box(
                        modifier = Modifier.width((400 * widthProgress).dp).height(30.dp)
                            .border(2.dp, Color.Blue)
                    )
                    UIKitViewController({
                        testSimpleConstrains()
                    }, modifier = Modifier.border(2.dp, Color.Red))
                }
                Text("Complex constraints")
                Row {
                    Box(
                        modifier = Modifier.width((400 * widthProgress).dp).height(30.dp)
                            .border(2.dp, Color.Blue)
                    )
                    UIKitViewController({
                        testComplexConstrains()
                    }, modifier = Modifier.border(2.dp, Color.Red))
                }
                Text("Complex simple SwiftUI")
                Row {
                    Box(
                        modifier = Modifier.width((400 * widthProgress).dp).height(30.dp)
                            .border(2.dp, Color.Blue)
                    )
                    UIKitViewController({
                        testSimpleSwiftUI()
                    }, modifier = Modifier.border(2.dp, Color.Red))
                }
                Text("Complex complex SwiftUI")
                Row {
                    Box(
                        modifier = Modifier.width((400 * widthProgress).dp).height(30.dp)
                            .border(2.dp, Color.Blue)
                    )
                    UIKitViewController({
                        testComplexSwiftUI()
                    }, modifier = Modifier.border(2.dp, Color.Red))
                }
            }
        }
    }
}

