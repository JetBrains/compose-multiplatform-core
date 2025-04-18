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

package androidx.wear.compose.material3

import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.GestureInclusion
import androidx.wear.compose.foundation.RevealActionType
import androidx.wear.compose.foundation.RevealDirection
import androidx.wear.compose.foundation.RevealDirection.Companion.Both
import androidx.wear.compose.foundation.RevealDirection.Companion.RightToLeft
import androidx.wear.compose.foundation.RevealState
import androidx.wear.compose.foundation.RevealValue
import androidx.wear.compose.foundation.SwipeToReveal
import androidx.wear.compose.foundation.SwipeToRevealDefaults.bidirectionalGestureInclusion
import androidx.wear.compose.foundation.SwipeToRevealDefaults.gestureInclusion
import androidx.wear.compose.foundation.createRevealAnchors
import androidx.wear.compose.foundation.rememberRevealState
import androidx.wear.compose.material3.ButtonDefaults.buttonColors
import androidx.wear.compose.material3.tokens.SwipeToRevealTokens
import androidx.wear.compose.materialcore.screenWidthDp
import kotlin.math.abs
import kotlinx.coroutines.launch

/**
 * [SwipeToReveal] Material composable. This adds the option to configure up to two additional
 * actions on a Composable: a mandatory [SwipeToRevealScope.primaryAction] and an optional
 * [SwipeToRevealScope.secondaryAction]. These actions are initially hidden and revealed only when
 * the [content] is swiped. These additional actions can be triggered by clicking on them after they
 * are revealed. [SwipeToRevealScope.primaryAction] will be triggered on full swipe of the
 * [content].
 *
 * For actions like "Delete", consider adding [SwipeToRevealScope.undoPrimaryAction] (displayed when
 * the [SwipeToRevealScope.primaryAction] is activated). Adding undo composables allow users to undo
 * the action that they just performed.
 *
 * [SwipeToReveal] composable adds the [CustomAccessibilityAction]s using the labels from primary
 * and secondary actions.
 *
 * Example of [SwipeToReveal] with primary and secondary actions
 *
 * @sample androidx.wear.compose.material3.samples.SwipeToRevealSample
 *
 * Example of [SwipeToReveal] with a Card composable, it reveals a taller button.
 *
 * @sample androidx.wear.compose.material3.samples.SwipeToRevealSingleActionCardSample
 *
 * Example of [SwipeToReveal] that doesn't reveal the actions, instead it only executes them when
 * fully swiped or bounces back to its initial state.
 *
 * @sample androidx.wear.compose.material3.samples.SwipeToRevealNonAnchoredSample
 *
 * Example of [SwipeToReveal] with a [TransformingLazyColumn]
 *
 * @sample androidx.wear.compose.material3.samples.SwipeToRevealWithTransformingLazyColumnSample
 * @param actions Actions of the [SwipeToReveal] composable, such as
 *   [SwipeToRevealScope.primaryAction]. [actions] should always include exactly one
 *   [SwipeToRevealScope.primaryAction]. [SwipeToRevealScope.secondaryAction],
 *   [SwipeToRevealScope.undoPrimaryAction] and [SwipeToRevealScope.undoSecondaryAction] are
 *   optional.
 * @param modifier [Modifier] to be applied on the composable
 * @param revealState [RevealState] of the [SwipeToReveal]
 * @param actionButtonHeight Desired height of the revealed action buttons. In case the content is a
 *   Button composable, it's suggested to use [SwipeToRevealDefaults.SmallActionButtonHeight], and
 *   for a Card composable, it's suggested to use [SwipeToRevealDefaults.LargeActionButtonHeight].
 * @param gestureInclusion Provides fine-grained control so that touch gestures can be excluded when
 *   they start in a certain region. An instance of [GestureInclusion] can be passed in here which
 *   will determine via [GestureInclusion.ignoreGestureStart] whether the gesture should proceed or
 *   not. By default, [gestureInclusion] allows gestures everywhere for when [revealState] contains
 *   anchors for both directions (see [bidirectionalGestureInclusion]). If it doesn't, then it
 *   allows gestures everywhere, except a zone on the left edge, which is used for swipe-to-dismiss
 *   (see [gestureInclusion]).
 * @param content The content that will be initially displayed over the other actions provided.
 * @see [androidx.wear.compose.foundation.SwipeToReveal]
 */
@Composable
public fun SwipeToReveal(
    actions: SwipeToRevealScope.() -> Unit,
    modifier: Modifier = Modifier,
    revealState: RevealState = rememberRevealState(anchors = SwipeToRevealDefaults.anchors()),
    actionButtonHeight: Dp = SwipeToRevealDefaults.SmallActionButtonHeight,
    gestureInclusion: GestureInclusion =
        if (revealState.hasBidirectionalAnchors()) {
            bidirectionalGestureInclusion
        } else {
            gestureInclusion(revealState)
        },
    content: @Composable () -> Unit,
) {
    val hapticFeedback = LocalHapticFeedback.current
    val children = SwipeToRevealScope()
    with(children, actions)
    val primaryAction = children.primaryAction
    require(primaryAction != null) {
        "PrimaryAction should be provided in actions by calling the PrimaryAction method"
    }

    val hasSecondaryAction = children.secondaryAction != null
    val iconStartFadeInFraction =
        if (hasSecondaryAction) {
            DOUBLE_ICON_VISIBLE_THRESHOLD_AS_SCREEN_WIDTH_PERCENTAGE
        } else {
            SINGLE_ICON_VISIBLE_THRESHOLD_AS_SCREEN_WIDTH_PERCENTAGE
        }
    val iconEndFadeInFraction =
        if (hasSecondaryAction) {
            DOUBLE_ICON_FADE_IN_END_THRESHOLD_AS_SCREEN_WIDTH_PERCENTAGE
        } else {
            SINGLE_ICON_FADE_IN_END_THRESHOLD_AS_SCREEN_WIDTH_PERCENTAGE
        }

    SwipeToReveal(
        modifier = modifier.fillMaxWidth(),
        primaryAction = {
            ActionButton(
                revealState,
                primaryAction,
                RevealActionType.PrimaryAction,
                actionButtonHeight,
                iconStartFadeInFraction,
                iconEndFadeInFraction,
                children.undoPrimaryAction != null,
            )
        },
        secondaryAction =
            children.secondaryAction?.let {
                {
                    ActionButton(
                        revealState,
                        it,
                        RevealActionType.SecondaryAction,
                        actionButtonHeight,
                        iconStartFadeInFraction,
                        iconEndFadeInFraction,
                        children.undoSecondaryAction != null,
                    )
                }
            },
        undoAction =
            when (revealState.lastActionType) {
                RevealActionType.SecondaryAction ->
                    children.undoSecondaryAction?.let {
                        {
                            ActionButton(
                                revealState,
                                it,
                                RevealActionType.UndoAction,
                                actionButtonHeight,
                                iconStartFadeInFraction,
                                iconEndFadeInFraction,
                            )
                        }
                    }
                else ->
                    children.undoPrimaryAction?.let {
                        {
                            ActionButton(
                                revealState,
                                it,
                                RevealActionType.UndoAction,
                                actionButtonHeight,
                                iconStartFadeInFraction,
                                iconEndFadeInFraction,
                            )
                        }
                    }
            },
        onFullSwipe = {
            // Full swipe triggers the main action, but does not set the click type.
            // Explicitly set the click type as main action when full swipe occurs.
            revealState.lastActionType = RevealActionType.PrimaryAction
            primaryAction.onClick()
        },
        state = revealState,
        gestureInclusion = gestureInclusion,
        content = content,
    )

    LaunchedEffect(revealState.targetValue) {
        if (
            (revealState.targetValue == RevealValue.LeftRevealed ||
                revealState.targetValue == RevealValue.RightRevealed)
        ) {
            hapticFeedback.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
        }
    }
}

internal fun RevealState.hasBidirectionalAnchors(): Boolean =
    this.swipeAnchors.keys.contains(RevealValue.LeftRevealed)

/**
 * Scope for the actions of a [SwipeToReveal] composable. Used to define the primary, secondary,
 * undo primary and undo secondary actions.
 */
public class SwipeToRevealScope {
    /**
     * Adds the primary action to a [SwipeToReveal]. This is required and exactly one primary action
     * should be specified. In case there are multiple, only the latest one will be displayed.
     *
     * When first revealed the primary action displays an icon and then, if fully swiped, it
     * additionally shows text.
     *
     * @param onClick Callback to be executed when the action is performed via a full swipe, or a
     *   button click.
     * @param icon Icon composable to be displayed for this action.
     * @param text Text composable to be displayed when the user fully swipes to execute the primary
     *   action.
     * @param containerColor Container color for this action.
     * @param contentColor Content color for this action.
     */
    public fun primaryAction(
        onClick: () -> Unit,
        icon: @Composable () -> Unit,
        text: @Composable () -> Unit,
        containerColor: Color = Color.Unspecified,
        contentColor: Color = Color.Unspecified
    ) {
        primaryAction = SwipeToRevealAction(onClick, icon, text, containerColor, contentColor)
    }

    /**
     * Adds the secondary action to a [SwipeToReveal]. This is optional and at most one secondary
     * action should be specified. In case there are multiple, only the latest one will be
     * displayed.
     *
     * Secondary action only displays an icon, because, unlike the primary action, it is never
     * extended to full width so does not have room to display text.
     *
     * @param onClick Callback to be executed when the action is performed via a button click.
     * @param icon Icon composable to be displayed for this action.
     * @param containerColor Container color for this action.
     * @param contentColor Content color for this action.
     */
    public fun secondaryAction(
        onClick: () -> Unit,
        icon: @Composable () -> Unit,
        containerColor: Color = Color.Unspecified,
        contentColor: Color = Color.Unspecified
    ) {
        secondaryAction = SwipeToRevealAction(onClick, icon, null, containerColor, contentColor)
    }

    /**
     * Adds the undo action for the primary action to a [SwipeToReveal]. Displayed after the user
     * performs the primary action. This is optional and at most one undo primary action should be
     * specified. In case there are multiple, only the latest one will be displayed.
     *
     * @param onClick Callback to be executed when the action is performed via a button click.
     * @param text Text composable to indicate what the undo action is, to be displayed when the
     *   user executes the primary action. This should include appropriated semantics for
     *   accessibility.
     * @param icon Optional Icon composable to be displayed for this action.
     * @param containerColor Container color for this action.
     * @param contentColor Content color for this action.
     */
    public fun undoPrimaryAction(
        onClick: () -> Unit,
        text: @Composable () -> Unit,
        icon: @Composable (() -> Unit)? = null,
        containerColor: Color = Color.Unspecified,
        contentColor: Color = Color.Unspecified
    ) {
        undoPrimaryAction = SwipeToRevealAction(onClick, icon, text, containerColor, contentColor)
    }

    /**
     * Adds the undo action for the secondary action to a [SwipeToReveal]. Displayed after the user
     * performs the secondary action.This is optional and at most one undo secondary action should
     * be specified. In case there are multiple, only the latest one will be displayed.
     *
     * @param onClick Callback to be executed when the action is performed via a button click.
     * @param text Text composable to indicate what the undo action is, to be displayed when the
     *   user executes the primary action. This should include appropriated semantics for
     *   accessibility.
     * @param icon Optional Icon composable to be displayed for this action.
     * @param containerColor Container color for this action.
     * @param contentColor Content color for this action.
     */
    public fun undoSecondaryAction(
        onClick: () -> Unit,
        text: @Composable () -> Unit,
        icon: @Composable (() -> Unit)? = null,
        containerColor: Color = Color.Unspecified,
        contentColor: Color = Color.Unspecified
    ) {
        undoSecondaryAction = SwipeToRevealAction(onClick, icon, text, containerColor, contentColor)
    }

    internal var primaryAction: SwipeToRevealAction? = null
    internal var undoPrimaryAction: SwipeToRevealAction? = null
    internal var secondaryAction: SwipeToRevealAction? = null
    internal var undoSecondaryAction: SwipeToRevealAction? = null
}

public object SwipeToRevealDefaults {

    /** Width that's required to display both actions in a [SwipeToReveal] composable. */
    public val DoubleActionAnchorWidth: Dp = 130.dp

    /** Width that's required to display a single action in a [SwipeToReveal] composable. */
    public val SingleActionAnchorWidth: Dp = 64.dp

    /** Standard height for a small revealed action, such as when the swiped item is a Button. */
    public val SmallActionButtonHeight: Dp = 52.dp

    /** Standard height for a large revealed action, such as when the swiped item is a Card. */
    public val LargeActionButtonHeight: Dp = 84.dp

    internal val IconSize = 26.dp

    /**
     * Creates the recommended anchors to support right-to-left swiping to reveal additional action
     * buttons.
     *
     * @param anchorWidth Absolute width, in dp, of the screen revealed items should be displayed
     *   in. Ignored if [useAnchoredActions] is set to false, as the items won't be anchored to the
     *   screen. For a single action SwipeToReveal component, this should be
     *   [SwipeToRevealDefaults.SingleActionAnchorWidth], and for a double action SwipeToReveal,
     *   [SwipeToRevealDefaults.DoubleActionAnchorWidth] to be able to display both action buttons.
     * @param useAnchoredActions Whether the actions should stay revealed, or bounce back to hidden
     *   when the user stops swiping. This is relevant for SwipeToReveal components with a single
     *   action. If the developer wants a swipe to clear behaviour, this should be set to false.
     */
    @SuppressLint("PrimitiveInCollection")
    @Composable
    public fun anchors(
        anchorWidth: Dp = SingleActionAnchorWidth,
        useAnchoredActions: Boolean = true,
    ): Map<RevealValue, Float> =
        createAnchors(anchorWidth = anchorWidth, useAnchoredActions = useAnchoredActions)

    /**
     * Creates anchors that allow the user to swipe either left-to-right or right-to-left to reveal
     * or execute the actions. This should not be used if the component is part of an activity, as
     * the gesture might conflict with the swipe-to-dismiss gesture. This is only supported for rare
     * cases where the current screen does not support swipe to dismiss.
     *
     * @param anchorWidth Absolute width, in dp, of the screen revealed items should be displayed
     *   in. Ignored if [useAnchoredActions] is set to false, as the items won't be anchored to the
     *   screen. For a single action SwipeToReveal component, this should be
     *   [SwipeToRevealDefaults.SingleActionAnchorWidth], and for a double action SwipeToReveal,
     *   [SwipeToRevealDefaults.DoubleActionAnchorWidth] to be able to display both action buttons.
     * @param useAnchoredActions Whether the actions should stay revealed, or bounce back to hidden
     *   when the user stops swiping. This is relevant for SwipeToReveal components with a single
     *   action. If the developer wants a swipe to clear behaviour, this should be set to false.
     */
    @SuppressLint("PrimitiveInCollection")
    @Composable
    public fun bidirectionalAnchors(
        anchorWidth: Dp = SingleActionAnchorWidth,
        useAnchoredActions: Boolean = true,
    ): Map<RevealValue, Float> =
        createAnchors(
            anchorWidth = anchorWidth,
            useAnchoredActions = useAnchoredActions,
            revealDirection = Both
        )

    @SuppressLint("PrimitiveInCollection")
    @Composable
    internal fun createAnchors(
        anchorWidth: Dp = SingleActionAnchorWidth,
        useAnchoredActions: Boolean = true,
        revealDirection: RevealDirection = RightToLeft,
    ): Map<RevealValue, Float> {
        val screenWidthDp = LocalConfiguration.current.screenWidthDp
        val anchorFraction = anchorWidth.value / screenWidthDp
        return createRevealAnchors(
            revealingAnchor = if (useAnchoredActions) anchorFraction else 0f,
            revealDirection = revealDirection,
        )
    }
}

@Composable
internal fun ActionButton(
    revealState: RevealState,
    action: SwipeToRevealAction,
    revealActionType: RevealActionType,
    buttonHeight: Dp,
    iconStartFadeInFraction: Float,
    iconEndFadeInFraction: Float,
    hasUndo: Boolean = false,
) {
    val containerColor =
        action.containerColor.takeOrElse {
            when (revealActionType) {
                RevealActionType.PrimaryAction ->
                    MaterialTheme.colorScheme.fromToken(
                        SwipeToRevealTokens.PrimaryActionContainerColor
                    )
                RevealActionType.SecondaryAction ->
                    MaterialTheme.colorScheme.fromToken(
                        SwipeToRevealTokens.SecondaryActionContainerColor
                    )
                RevealActionType.UndoAction ->
                    MaterialTheme.colorScheme.fromToken(
                        SwipeToRevealTokens.UndoActionContainerColor
                    )
                else -> Color.Unspecified
            }
        }
    val contentColor =
        action.contentColor.takeOrElse {
            when (revealActionType) {
                RevealActionType.PrimaryAction ->
                    MaterialTheme.colorScheme.fromToken(
                        SwipeToRevealTokens.PrimaryActionContentColor
                    )
                RevealActionType.SecondaryAction ->
                    MaterialTheme.colorScheme.fromToken(
                        SwipeToRevealTokens.SecondaryActionContentColor
                    )
                RevealActionType.UndoAction ->
                    MaterialTheme.colorScheme.fromToken(SwipeToRevealTokens.UndoActionContentColor)
                else -> Color.Unspecified
            }
        }
    val fullScreenPaddingDp = (screenWidthDp() * FULL_SCREEN_PADDING_FRACTION).dp
    val startPadding =
        when (revealActionType) {
            RevealActionType.UndoAction -> fullScreenPaddingDp
            else -> 0.dp
        }
    val endPadding =
        when (revealActionType) {
            RevealActionType.UndoAction -> fullScreenPaddingDp
            else -> 0.dp
        }
    val screenWidthPx = with(LocalDensity.current) { screenWidthDp().dp.toPx() }
    val fadeInStart = screenWidthPx * BUTTON_VISIBLE_THRESHOLD_AS_SCREEN_WIDTH_PERCENTAGE
    val fadeInEnd = screenWidthPx * BUTTON_FADE_IN_END_THRESHOLD_AS_SCREEN_WIDTH_PERCENTAGE
    val coroutineScope = rememberCoroutineScope()
    Button(
        modifier =
            Modifier.height(buttonHeight)
                .padding(startPadding, 0.dp, endPadding, 0.dp)
                .fillMaxWidth()
                .graphicsLayer {
                    val offset = abs(revealState.offset)
                    val shouldDisplayButton =
                        offset > screenWidthPx * BUTTON_VISIBLE_THRESHOLD_AS_SCREEN_WIDTH_PERCENTAGE
                    alpha =
                        if (shouldDisplayButton) {
                            val coercedOffset =
                                offset.coerceIn(
                                    minimumValue = fadeInStart,
                                    maximumValue = fadeInEnd
                                )
                            (coercedOffset - fadeInStart) / (fadeInEnd - fadeInStart)
                        } else {
                            0f
                        }
                },
        onClick = {
            coroutineScope.launch {
                try {
                    if (revealActionType == RevealActionType.UndoAction) {
                        revealState.animateTo(RevealValue.Covered)
                    } else {
                        if (hasUndo || revealActionType == RevealActionType.PrimaryAction) {
                            revealState.lastActionType = revealActionType
                            revealState.animateTo(
                                if (revealState.offset > 0) {
                                    RevealValue.LeftRevealed
                                } else {
                                    RevealValue.RightRevealed
                                }
                            )
                        }
                    }
                } finally {
                    // Execute onClick even if the animation gets interrupted
                    action.onClick()
                }
            }
        },
        colors = buttonColors(containerColor = containerColor, contentColor = contentColor),
        contentPadding = PaddingValues(ACTION_BUTTON_CONTENT_PADDING),
        shape = CircleShape
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val primaryActionTextRevealed = remember { mutableStateOf(false) }
            action.icon?.let {
                ActionIconWrapper(revealState, iconStartFadeInFraction, iconEndFadeInFraction, it)
            }
            when (revealActionType) {
                RevealActionType.PrimaryAction -> {
                    AnimatedVisibility(
                        visible = primaryActionTextRevealed.value,
                        enter =
                            fadeIn(spring(stiffness = Spring.StiffnessLow)) +
                                expandHorizontally(spring(stiffness = Spring.StiffnessMedium)),
                        exit =
                            fadeOut(spring(stiffness = Spring.StiffnessHigh)) +
                                shrinkHorizontally(spring(stiffness = Spring.StiffnessMedium)),
                    ) {
                        ActionText(action, contentColor)
                    }

                    LaunchedEffect(revealState.offset) {
                        primaryActionTextRevealed.value =
                            abs(revealState.offset) > revealState.revealThreshold &&
                                (revealState.targetValue == RevealValue.RightRevealed ||
                                    revealState.targetValue == RevealValue.LeftRevealed)
                    }
                }
                RevealActionType.UndoAction -> ActionText(action, contentColor)
            }
        }
    }
}

@Composable
private fun ActionText(action: SwipeToRevealAction, contentColor: Color) {
    require(action.text != null) { "A text composable should be provided to ActionText." }
    Row(modifier = Modifier.padding(start = action.icon?.let { ICON_AND_TEXT_PADDING } ?: 0.dp)) {
        CompositionLocalProvider(
            LocalContentColor provides contentColor,
            LocalTextStyle provides LocalTextStyle.current,
            LocalTextConfiguration provides
                TextConfiguration(
                    textAlign = LocalTextConfiguration.current.textAlign,
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1,
                )
        ) {
            action.text.invoke()
        }
    }
}

@Composable
private fun ActionIconWrapper(
    revealState: RevealState,
    iconStartFadeInFraction: Float,
    iconEndFadeInFraction: Float,
    content: @Composable () -> Unit
) {
    val screenWidthPx = with(LocalDensity.current) { screenWidthDp().dp.toPx() }
    val fadeInStart = screenWidthPx * iconStartFadeInFraction
    val fadeInEnd = screenWidthPx * iconEndFadeInFraction
    Box(
        modifier =
            Modifier.size(SwipeToRevealDefaults.IconSize, Dp.Unspecified).graphicsLayer {
                val offset = abs(revealState.offset)
                val shouldDisplayIcon = offset > fadeInStart
                alpha =
                    if (shouldDisplayIcon) {
                        val coercedOffset =
                            offset.coerceIn(minimumValue = fadeInStart, maximumValue = fadeInEnd)
                        (coercedOffset - fadeInStart) / (fadeInEnd - fadeInStart)
                    } else {
                        0f
                    }
            }
    ) {
        content()
    }
}

/** Data class to define an action to be displayed in a [SwipeToReveal] composable. */
internal data class SwipeToRevealAction(
    /** Callback to be executed when the action is performed via a full swipe, or a button click. */
    val onClick: () -> Unit,

    /**
     * Icon composable to be displayed for this action. This accepts a scale parameter that should
     * be used to increase icon icon when an action is fully revealed.
     */
    val icon: @Composable (() -> Unit)?,

    /**
     * Text composable to be displayed when the user fully swipes to execute the primary action, or
     * when the undo action is shown.
     */
    val text: @Composable (() -> Unit)?,

    /**
     * Color of the container, used for the background of the action button. This can be
     * [Color.Unspecified], and in case it is, needs to be replaced with a default.
     */
    val containerColor: Color,

    /**
     * Color of the content, used for the icon and text. This can be [Color.Unspecified], and in
     * case it is, needs to be replaced with a default.
     */
    val contentColor: Color,
)

/** Rapid animation length in milliseconds. */
internal const val RAPID_ANIMATION = 200

private val ICON_AND_TEXT_PADDING = 4.dp

private val ACTION_BUTTON_CONTENT_PADDING = 4.dp

// Swipe required to start displaying the action buttons.
private const val BUTTON_VISIBLE_THRESHOLD_AS_SCREEN_WIDTH_PERCENTAGE = 0.06f

// End threshold for the fade in progression of the action buttons.
private const val BUTTON_FADE_IN_END_THRESHOLD_AS_SCREEN_WIDTH_PERCENTAGE = 0.12f

// Swipe required to start displaying the icon for a single action button.
private const val SINGLE_ICON_VISIBLE_THRESHOLD_AS_SCREEN_WIDTH_PERCENTAGE = 0.15f

// Swipe required to start displaying the icon for two action buttons.
private const val DOUBLE_ICON_VISIBLE_THRESHOLD_AS_SCREEN_WIDTH_PERCENTAGE = 0.30f

// End threshold for the fade in progression of the icon for a single action button.
private const val SINGLE_ICON_FADE_IN_END_THRESHOLD_AS_SCREEN_WIDTH_PERCENTAGE = 0.21f

// End threshold for the fade in progression of the icon for two action buttons.
private const val DOUBLE_ICON_FADE_IN_END_THRESHOLD_AS_SCREEN_WIDTH_PERCENTAGE = 0.36f

private val FULL_SCREEN_PADDING_FRACTION = 0.0625f
