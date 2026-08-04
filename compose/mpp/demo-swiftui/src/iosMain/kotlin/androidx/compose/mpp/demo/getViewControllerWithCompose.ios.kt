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

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.Button
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
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.UIKitView
import androidx.compose.ui.viewinterop.rememberUIKitInteropRemeasureRequester
import androidx.compose.ui.viewinterop.remeasureRequester
import androidx.compose.ui.window.ComposeUIView
import androidx.compose.ui.window.ComposeUIViewController
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCAction
import kotlinx.cinterop.readValue
import platform.CoreGraphics.CGRectZero
import platform.Foundation.NSSelectorFromString
import platform.UIKit.NSLayoutConstraint
import platform.UIKit.UIColor
import platform.UIKit.UIControlEventValueChanged
import platform.UIKit.UILabel
import platform.UIKit.UILayoutConstraintAxisHorizontal
import platform.UIKit.UILayoutConstraintAxisVertical
import platform.UIKit.UILayoutPriorityDefaultHigh
import platform.UIKit.UISlider
import platform.UIKit.UIStackView
import platform.UIKit.UIStackViewAlignmentCenter
import platform.UIKit.UISwitch
import platform.UIKit.UIView
import platform.UIKit.UIViewController

// TODO This module is just a proxy to run the demo from mpp:demo. Figure out how to get rid of it.
//  If it is removed, there is no available configuration in IDE
@OptIn(ExperimentalComposeUiApi::class)
fun getViewControllerWithCompose(
    makeHostingViewController: (Int) -> UIViewController,
    makeSizingDemoViewController: (UIView, Int) -> UIViewController,
): UIViewController = ComposeUIViewController {
    IosDemo(
        arg = "",
        makeHostingController = makeHostingViewController,
        makeSizingDemoController = makeSizingDemoViewController,
    )
}

@OptIn(ExperimentalComposeUiApi::class)
fun getComposeSizingDemoView(): UIView =
    makeComposeSizingDemoView(
        embedsUIKitView = false,
    )

@OptIn(ExperimentalComposeUiApi::class)
fun getComposeHostedSizingDemoView(): UIView =
    makeComposeSizingDemoView(
        embedsUIKitView = false,
    )

@OptIn(ExperimentalComposeUiApi::class)
fun getUIKitInteropSizingDemoView(): UIView =
    makeComposeSizingDemoView(
        embedsUIKitView = true,
    )

@OptIn(ExperimentalComposeUiApi::class)
private fun makeComposeSizingDemoView(
    embedsUIKitView: Boolean,
): UIView = ComposeUIView {
    ComposeSizingDemoContent(
        embedsUIKitView = embedsUIKitView,
    )
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun ComposeSizingDemoContent(
    embedsUIKitView: Boolean = false,
) {
    var applyWidth by remember { mutableStateOf(true) }
    var applyHeight by remember { mutableStateOf(false) }
    var width by remember { mutableFloatStateOf(220f) }
    var height by remember { mutableFloatStateOf(220f) }
    var composeOnlyContentExpanded by remember { mutableStateOf(false) }
    val embeddedContentName = if (embedsUIKitView) "UIKit" else "Compose"

    val previewModifier = Modifier
        .then(if (applyWidth) Modifier.width(width.dp) else Modifier)
        .then(if (applyHeight) Modifier.height(height.dp) else Modifier)
        .border(2.dp, if (embedsUIKitView) Color(0xFF16A34A) else Color(0xFF3B82F6))
        .background(if (embedsUIKitView) Color(0xFFEFFAF2) else Color(0xFFEAF2FF))
        .padding(16.dp)

    MaterialTheme {
        Column(
            modifier = Modifier
                .onSizeChanged {
                    println("[ComposeSizingDemo] content layout size=$it, embedsUIKitView=$embedsUIKitView")
                }
                .background(Color.White)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "$embeddedContentName content controls",
                style = MaterialTheme.typography.body1
            )

            SizeControlRow(
                title = "Width",
                isEnabled = applyWidth,
                value = width,
                onEnabledChange = { applyWidth = it },
                onValueChange = { width = it },
                range = 120f..420f
            )

            SizeControlRow(
                title = "Height",
                isEnabled = applyHeight,
                value = height,
                onEnabledChange = { applyHeight = it },
                onValueChange = { height = it },
                range = 120f..520f
            )

            if (embedsUIKitView) {
                UIKitPreviewArea(previewModifier)
            } else {
                Box(previewModifier) {
                    ComposePreviewArea(
                        applyWidth = applyWidth,
                        width = width,
                        applyHeight = applyHeight,
                        height = height
                    )
                }

                ComposeOnlySizingProbe(
                    expanded = composeOnlyContentExpanded,
                    onExpandedChange = {
                        composeOnlyContentExpanded = it
                        println("[ComposeSizingDemo] compose-only sizing probe expanded=$it")
                    }
                )
            }
        }
    }
}

/**
 * Changes only Compose state. In the SwiftUI-hosted demo this must make SwiftUI query the
 * unchanged width / unbounded-height proposal again, then resize the red host border.
 */
@Composable
private fun ComposeOnlySizingProbe(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Compose-only intrinsic sizing probe",
            style = MaterialTheme.typography.body1
        )
        Text(
            text = "Does not change any SwiftUI state or proposal.",
            style = MaterialTheme.typography.body2,
            color = Color.Gray
        )
        Button(onClick = { onExpandedChange(!expanded) }) {
            Text(if (expanded) "Collapse Compose-only content" else "Expand Compose-only content")
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(if (expanded) 220.dp else 60.dp)
                .background(if (expanded) Color(0xFF7C3AED) else Color(0xFFB9A5E9))
                .padding(12.dp)
        ) {
            Text(
                text = if (expanded) "Compose added 160 dp of height" else "Compose-only content: 60 dp",
                color = Color.White
            )
        }
    }
}

@Composable
private fun ComposePreviewArea(
    applyWidth: Boolean,
    width: Float,
    applyHeight: Boolean,
    height: Float
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Compose preview area", style = MaterialTheme.typography.body1)
        Text(
            text = "Width: ${if (applyWidth) width.toInt().toString() else "auto"} dp",
            style = MaterialTheme.typography.body2
        )
        Text(
            text = "Height: ${if (applyHeight) height.toInt().toString() else "auto"} dp",
            style = MaterialTheme.typography.body2
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Resize me from the host above or from Compose here.",
            style = MaterialTheme.typography.body2
        )
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun UIKitPreviewArea(previewModifier: Modifier) {
    val remeasureRequester = rememberUIKitInteropRemeasureRequester()

    UIKitView(
        factory = {
            UIKitSizingDemoView(requestRemeasure = remeasureRequester::requestRemeasure)
        },
        modifier = previewModifier
            .then(Modifier.remeasureRequester(remeasureRequester))
    )
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private class UIKitSizingDemoView(
    val requestRemeasure: () -> Unit = {}
) : UIView(frame = CGRectZero.readValue()) {

    private val controlsStack = UIStackView(frame = CGRectZero.readValue())
    private val widthSwitch = UISwitch(frame = CGRectZero.readValue())
    private val heightSwitch = UISwitch(frame = CGRectZero.readValue())
    private val widthSlider = UISlider(frame = CGRectZero.readValue())
    private val heightSlider = UISlider(frame = CGRectZero.readValue())
    private val widthValueLabel = UILabel(frame = CGRectZero.readValue())
    private val heightValueLabel = UILabel(frame = CGRectZero.readValue())
    private val previewLabel = UILabel(frame = CGRectZero.readValue())
    private val previewWidthConstraint: NSLayoutConstraint
    private val previewHeightConstraint: NSLayoutConstraint
    private val previewTrailingConstraint: NSLayoutConstraint
    private val previewContainedTrailingConstraint: NSLayoutConstraint

    init {
        backgroundColor = UIColor(red = 1.0, green = 0.98, blue = 0.92, alpha = 1.0)
        clipsToBounds = true

        widthSwitch.setOn(true, animated = false)
        heightSwitch.setOn(false, animated = false)

        configureSlider(widthSlider, value = 220f, range = 120f..420f)
        configureSlider(heightSlider, value = 220f, range = 80f..320f)
        configureValueLabel(widthValueLabel)
        configureValueLabel(heightValueLabel)

        controlsStack.axis = UILayoutConstraintAxisVertical
        controlsStack.spacing = 10.0
        controlsStack.translatesAutoresizingMaskIntoConstraints = false
        controlsStack.addArrangedSubview(makeControlBlock("Width", widthSwitch, widthValueLabel, widthSlider))
        controlsStack.addArrangedSubview(makeControlBlock("Height", heightSwitch, heightValueLabel, heightSlider))

        previewLabel.numberOfLines = 0
        previewLabel.backgroundColor = UIColor(red = 1.0, green = 0.5, blue = 0.92, alpha = 1.0)
        previewLabel.translatesAutoresizingMaskIntoConstraints = false

        addSubview(controlsStack)
        addSubview(previewLabel)

        previewWidthConstraint = previewLabel.widthAnchor.constraintEqualToConstant(widthSlider.value.toDouble())
        previewHeightConstraint = previewLabel.heightAnchor.constraintEqualToConstant(heightSlider.value.toDouble())
        previewTrailingConstraint = previewLabel.trailingAnchor.constraintEqualToAnchor(trailingAnchor)
        previewContainedTrailingConstraint = previewLabel.trailingAnchor.constraintLessThanOrEqualToAnchor(trailingAnchor)
        previewContainedTrailingConstraint.priority = UILayoutPriorityDefaultHigh

        NSLayoutConstraint.activateConstraints(
            listOf(
                controlsStack.topAnchor.constraintEqualToAnchor(topAnchor),
                controlsStack.leadingAnchor.constraintEqualToAnchor(leadingAnchor),
                controlsStack.trailingAnchor.constraintEqualToAnchor(trailingAnchor),
                previewLabel.topAnchor.constraintEqualToAnchor(controlsStack.bottomAnchor, constant = 12.0),
                previewLabel.leadingAnchor.constraintEqualToAnchor(leadingAnchor),
                previewLabel.bottomAnchor.constraintLessThanOrEqualToAnchor(bottomAnchor),
            )
        )

        listOf(widthSwitch, heightSwitch, widthSlider, heightSlider).forEach { control ->
            control.addTarget(
                target = this,
                action = NSSelectorFromString(::valueChanged.name),
                forControlEvents = UIControlEventValueChanged
            )
        }

        updatePreview()
    }

    @ObjCAction
    fun valueChanged() {
        updatePreview()
        requestRemeasure()
    }

    private fun makeControlBlock(
        title: String,
        toggle: UISwitch,
        valueLabel: UILabel,
        slider: UISlider
    ): UIStackView {
        val titleLabel = UILabel(frame = CGRectZero.readValue()).apply {
            text = title
            textColor = UIColor.grayColor
        }
        val spacer = UIView(frame = CGRectZero.readValue())

        val row = UIStackView(frame = CGRectZero.readValue()).apply {
            axis = UILayoutConstraintAxisHorizontal
            alignment = UIStackViewAlignmentCenter
            spacing = 8.0
            addArrangedSubview(titleLabel)
            addArrangedSubview(toggle)
            addArrangedSubview(spacer)
            addArrangedSubview(valueLabel)
        }

        return UIStackView(frame = CGRectZero.readValue()).apply {
            axis = UILayoutConstraintAxisVertical
            spacing = 4.0
            addArrangedSubview(row)
            addArrangedSubview(slider)
        }
    }

    private fun configureSlider(
        slider: UISlider,
        value: Float,
        range: ClosedFloatingPointRange<Float>
    ) {
        slider.minimumValue = range.start
        slider.maximumValue = range.endInclusive
        slider.value = value
    }

    private fun configureValueLabel(label: UILabel) {
        label.textColor = UIColor.grayColor
    }

    private fun updatePreview() {
        previewWidthConstraint.constant = widthSlider.value.toDouble()
        previewHeightConstraint.constant = heightSlider.value.toDouble()
        previewWidthConstraint.active = widthSwitch.on
        previewHeightConstraint.active = heightSwitch.on
        previewTrailingConstraint.active = !widthSwitch.on
        previewContainedTrailingConstraint.active = widthSwitch.on
//        previewLabel.preferredMaxLayoutWidth = if (widthSwitch.on) {
//            widthSlider.value.toDouble()
//        } else {
//            0.0
//        }

        widthValueLabel.text = widthSlider.value.toInt().toString()
        heightValueLabel.text = heightSlider.value.toInt().toString()
        previewLabel.text = buildString {
            appendLine("UILabel")
            appendLine("Width: ${if (widthSwitch.on) widthSlider.value.toInt().toString() else "auto"} pt")
            appendLine("Height: ${if (heightSwitch.on) heightSlider.value.toInt().toString() else "auto"} pt")
            append("These UIKit controls update UIKit constraints. These UIKit controls update UIKit constraints. These UIKit controls update UIKit constraints. These UIKit controls update UIKit constraints.")
        }
//        previewLabel.invalidateIntrinsicContentSize()
//        invalidateIntrinsicContentSize()
//        setNeedsLayout()
    }
}

@Composable
private fun SizeControlRow(
    title: String,
    isEnabled: Boolean,
    value: Float,
    onEnabledChange: (Boolean) -> Unit,
    onValueChange: (Float) -> Unit,
    range: ClosedFloatingPointRange<Float>
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.caption,
                color = Color.Gray
            )
            Switch(
                checked = isEnabled,
                onCheckedChange = onEnabledChange,
                modifier = Modifier.height(24.dp)
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = value.toInt().toString(),
                style = MaterialTheme.typography.caption,
                fontFamily = FontFamily.Monospace,
                color = Color.Gray
            )
        }

        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
        )
    }
}
