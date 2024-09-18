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

package androidx.compose.ui.uikit

import kotlinx.cinterop.CValue
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGSize
import platform.UIKit.NSLayoutConstraint
import platform.UIKit.UIView

internal fun UIView.embedSubview(subview: UIView): List<NSLayoutConstraint> {
    addSubview(subview)
    subview.translatesAutoresizingMaskIntoConstraints = false
    return subview.layoutConstraintsToMatch(this).also {
        NSLayoutConstraint.activateConstraints(it)
    }
}

internal fun UIView.layoutConstraintsToMatch(other: UIView) =
    listOf(
        leftAnchor.constraintEqualToAnchor(other.leftAnchor),
        rightAnchor.constraintEqualToAnchor(other.rightAnchor),
        topAnchor.constraintEqualToAnchor(other.topAnchor),
        bottomAnchor.constraintEqualToAnchor(other.bottomAnchor)
    )

internal fun UIView.layoutConstraintsToCenterInParent(parent: UIView, size: CValue<CGSize>) =
    size.useContents {
        listOf(
            centerXAnchor.constraintEqualToAnchor(parent.centerXAnchor),
            centerYAnchor.constraintEqualToAnchor(parent.centerYAnchor),
            widthAnchor.constraintEqualToConstant(width),
            heightAnchor.constraintEqualToConstant(height)
        )
    }

/**
 * A box for a list of constraints which deactivates the old constraints and activates the new ones
 * when new value is set.
 */
internal class ExclusiveLayoutConstraints {
    private var constraints: List<NSLayoutConstraint> = emptyList()

    fun set(value: List<NSLayoutConstraint>) {
        if (constraints.isNotEmpty()) {
            NSLayoutConstraint.deactivateConstraints(constraints)
        }
        constraints = value
        NSLayoutConstraint.activateConstraints(constraints)
    }
}