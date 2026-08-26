/*
 * Copyright 2026 The Android Open Source Project
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

@file:OptIn(
    androidx.compose.ui.ExperimentalComposeUiApi::class,
    kotlinx.cinterop.BetaInteropApi::class,
    kotlinx.cinterop.ExperimentalForeignApi::class,
)

package androidx.compose.mpp.demo

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Slider
import androidx.compose.material.Switch
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.UIKitView
import androidx.compose.ui.viewinterop.remeasureRequester
import androidx.compose.ui.viewinterop.rememberUIKitInteropRemeasureRequester
import kotlinx.cinterop.ObjCAction
import kotlinx.cinterop.readValue
import platform.CoreGraphics.CGRectZero
import platform.Foundation.NSSelectorFromString
import platform.UIKit.NSLayoutConstraint
import platform.UIKit.NSTextAlignmentCenter
import platform.UIKit.UIButton
import platform.UIKit.UIButtonTypeSystem
import platform.UIKit.UIColor
import platform.UIKit.UIControlEventTouchUpInside
import platform.UIKit.UIControlStateNormal
import platform.UIKit.UIFont
import platform.UIKit.UILabel
import platform.UIKit.UILayoutConstraintAxisVertical
import platform.UIKit.UIStackView
import platform.UIKit.UIView

internal fun uiKitInComposeSizingExamples() = Screen.Selection(
    "UIKit in Compose",
    Screen.Example("Multiline UILabel follows Compose width") {
        MultilineLabelFollowsComposeWidthDemo()
    },
    Screen.Example("Auto Layout stack fits Compose width") {
        AutoLayoutStackFitsComposeWidthDemo()
    },
    Screen.Example("Aspect-ratio UIKit view follows Compose height") {
        AspectRatioUIKitViewFollowsComposeHeightDemo()
    },
    Screen.Example("UIKit content change remeasures Compose") {
        UIKitContentChangeRemeasuresComposeDemo()
    },
    Screen.Example("Compose constraints override UIKit preferred size") {
        ComposeConstraintsOverrideUIKitPreferredSizeDemo()
    },
)

@Composable
private fun MultilineLabelFollowsComposeWidthDemo() {
    var width by remember { mutableFloatStateOf(240f) }

    UIKitInComposeSizingDemo(
        title = "Multiline UILabel follows a Compose width",
        explanation = "Compose fixes the width. UIKit measures the multiline label for that width and supplies the fitted height.",
        controls = {
            SizingSlider(
                label = "Compose width: ${width.toInt()} dp",
                value = width,
                onValueChange = { width = it },
            )
        },
    ) {
        UIKitView(
            factory = {
                UILabel().apply {
                    text = longLabelText
                    numberOfLines = 0
                    backgroundColor = nativeContentColor
                    textColor = UIColor.blackColor
                }
            },
            modifier = Modifier
                .width(width.dp)
                .interopNodeBorder(),
        )
    }
}

@Composable
private fun AutoLayoutStackFitsComposeWidthDemo() {
    var width by remember { mutableFloatStateOf(240f) }

    UIKitInComposeSizingDemo(
        title = "An Auto Layout stack fits a Compose width",
        explanation = "The width is still a Compose constraint, but UIKit derives the height from a UIStackView and its arranged labels.",
        controls = {
            SizingSlider(
                label = "Compose width: ${width.toInt()} dp",
                value = width,
                onValueChange = { width = it },
            )
        },
    ) {
        UIKitView(
            factory = {
                val title = UILabel().apply {
                    text = "UIKit UIStackView"
                    font = UIFont.boldSystemFontOfSize(17.0)
                    textColor = UIColor.whiteColor
                }
                val body = UILabel().apply {
                    text = longLabelText
                    numberOfLines = 0
                    textColor = UIColor.whiteColor
                }
                UIStackView().apply {
                    axis = UILayoutConstraintAxisVertical
                    spacing = 8.0
                    backgroundColor = stackContentColor
                    addArrangedSubview(title)
                    addArrangedSubview(body)
                }
            },
            modifier = Modifier
                .width(width.dp)
                .interopNodeBorder(),
        )
    }
}

@Composable
private fun AspectRatioUIKitViewFollowsComposeHeightDemo() {
    var height by remember { mutableFloatStateOf(120f) }

    UIKitInComposeSizingDemo(
        title = "An aspect-ratio UIKit view follows a Compose height",
        explanation = "Compose fixes only the height. UIKit's width = 2 × height constraint supplies the unbound width.",
        controls = {
            SizingSlider(
                label = "Compose height: ${height.toInt()} dp",
                value = height,
                onValueChange = { height = it },
                valueRange = 60f..180f,
            )
        },
    ) {
        UIKitView(
            factory = { AspectRatioUIKitView() },
            modifier = Modifier
                .height(height.dp)
                .interopNodeBorder(),
        )
    }
}

@Composable
private fun UIKitContentChangeRemeasuresComposeDemo() {
    val remeasureRequester = rememberUIKitInteropRemeasureRequester()

    UIKitInComposeSizingDemo(
        title = "A UIKit content change remeasures Compose",
        explanation = "Tap the native button. After changing its label, UIKit explicitly requests Compose remeasurement so the node receives the new fitted height.",
    ) {
        UIKitView(
            factory = {
                ExpandingUIKitContentView(
                    requestRemeasure = remeasureRequester::requestRemeasure,
                )
            },
            modifier = Modifier
                .width(260.dp)
                .interopNodeBorder()
                .remeasureRequester(remeasureRequester),
        )
    }
}

@Composable
private fun ComposeConstraintsOverrideUIKitPreferredSizeDemo() {
    var appliesComposeSize by remember { mutableStateOf(false) }

    UIKitInComposeSizingDemo(
        title = "Compose constraints override UIKit's preferred size",
        explanation = "The native view prefers 80 × 100 pt. Enable the Compose constraint to make Compose supply the final 240 × 160 dp frame instead.",
        controls = {
            Switch(
                checked = appliesComposeSize,
                onCheckedChange = { appliesComposeSize = it },
            )
            Text(
                text = if (appliesComposeSize) {
                    "Compose provides 240 × 160 dp"
                } else {
                    "UIKit provides its preferred 80 × 100 pt"
                },
                style = MaterialTheme.typography.caption,
                color = Color.Gray,
            )
        },
    ) {
        UIKitView(
            factory = { FixedPreferredSizeUIKitView() },
            modifier = Modifier
                .then(if (appliesComposeSize) Modifier.size(240.dp, 160.dp) else Modifier)
                .interopNodeBorder(),
        )
    }
}

@Composable
private fun UIKitInComposeSizingDemo(
    title: String,
    explanation: String,
    controls: @Composable () -> Unit = {},
    content: @Composable BoxScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(title, style = MaterialTheme.typography.h6)
        Text(
            text = explanation,
            style = MaterialTheme.typography.body2,
            color = Color.Gray,
        )
        controls()
        Text(
            text = "The blue outline is the Compose UIKitView node.",
            style = MaterialTheme.typography.caption,
            color = Color.Gray,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color.White),
            contentAlignment = Alignment.Center,
            content = content,
        )
    }
}

@Composable
private fun SizingSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float> = 120f..360f,
) {
    Text(label, style = MaterialTheme.typography.body2)
    Slider(
        value = value,
        onValueChange = onValueChange,
        valueRange = valueRange,
    )
}

private fun Modifier.interopNodeBorder(): Modifier = border(2.dp, Color(0xFF2563EB))

private class AspectRatioUIKitView : UIView(frame = CGRectZero.readValue()) {
    init {
        translatesAutoresizingMaskIntoConstraints = false
        backgroundColor = UIColor(red = 0.98, green = 0.55, blue = 0.18, alpha = 1.0)

        val label = UILabel().apply {
            text = "UIKit\n2 × height"
            textAlignment = NSTextAlignmentCenter
            numberOfLines = 0
            textColor = UIColor.whiteColor
            translatesAutoresizingMaskIntoConstraints = false
        }
        addSubview(label)

        NSLayoutConstraint.activateConstraints(
            listOf(
                widthAnchor.constraintEqualToAnchor(heightAnchor, multiplier = 2.0),
                label.centerXAnchor.constraintEqualToAnchor(centerXAnchor),
                label.centerYAnchor.constraintEqualToAnchor(centerYAnchor),
            ),
        )
    }
}

private class ExpandingUIKitContentView(
    private val requestRemeasure: () -> Boolean,
) : UIView(frame = CGRectZero.readValue()) {
    private val messageLabel = UILabel()
    private val actionButton = UIButton.buttonWithType(UIButtonTypeSystem)
    private var expanded = false

    init {
        backgroundColor = dynamicContentColor

        messageLabel.numberOfLines = 0
        messageLabel.textColor = UIColor.blackColor

        actionButton.setTitleColor(UIColor.whiteColor, forState = UIControlStateNormal)
        actionButton.backgroundColor = UIColor(red = 0.49, green = 0.18, blue = 0.72, alpha = 1.0)
        actionButton.addTarget(
            target = this,
            action = NSSelectorFromString(::toggleContent.name),
            forControlEvents = UIControlEventTouchUpInside,
        )

        val stack = UIStackView().apply {
            axis = UILayoutConstraintAxisVertical
            spacing = 10.0
            translatesAutoresizingMaskIntoConstraints = false
            addArrangedSubview(messageLabel)
            addArrangedSubview(actionButton)
        }
        addSubview(stack)
        NSLayoutConstraint.activateConstraints(
            listOf(
                stack.topAnchor.constraintEqualToAnchor(topAnchor, constant = 12.0),
                stack.leadingAnchor.constraintEqualToAnchor(leadingAnchor, constant = 12.0),
                stack.trailingAnchor.constraintEqualToAnchor(trailingAnchor, constant = -12.0),
                stack.bottomAnchor.constraintEqualToAnchor(bottomAnchor, constant = -12.0),
            ),
        )

        updateContent()
    }

    @ObjCAction
    fun toggleContent() {
        expanded = !expanded
        updateContent()
        requestRemeasure()
    }

    private fun updateContent() {
        messageLabel.text = if (expanded) {
            "UIKit changed this label to several lines of text. The native stack is now taller, and the explicit remeasure request lets Compose use that new fitting height."
        } else {
            "UIKit content is collapsed."
        }
        actionButton.setTitle(
            if (expanded) "Collapse native content" else "Expand native content",
            forState = UIControlStateNormal,
        )
    }
}

private class FixedPreferredSizeUIKitView : UIView(frame = CGRectZero.readValue()) {
    init {
        translatesAutoresizingMaskIntoConstraints = false
        backgroundColor = UIColor(red = 0.12, green = 0.52, blue = 0.35, alpha = 1.0)

        val label = UILabel().apply {
            text = "UIKit\nprefers\n80 × 100"
            textAlignment = NSTextAlignmentCenter
            numberOfLines = 0
            textColor = UIColor.whiteColor
            translatesAutoresizingMaskIntoConstraints = false
        }
        addSubview(label)

        NSLayoutConstraint.activateConstraints(
            listOf(
                widthAnchor.constraintEqualToConstant(80.0),
                heightAnchor.constraintEqualToConstant(100.0),
                label.centerXAnchor.constraintEqualToAnchor(centerXAnchor),
                label.centerYAnchor.constraintEqualToAnchor(centerYAnchor),
            ),
        )
    }
}

private val longLabelText =
    "UIKit measures this multiline label using the width proposed by the surrounding Compose layout. Move the slider to see the text wrap and the fitted height change."

private val nativeContentColor = UIColor(red = 0.99, green = 0.88, blue = 0.56, alpha = 1.0)
private val stackContentColor = UIColor(red = 0.12, green = 0.34, blue = 0.65, alpha = 1.0)
private val dynamicContentColor = UIColor(red = 0.91, green = 0.83, blue = 0.98, alpha = 1.0)
