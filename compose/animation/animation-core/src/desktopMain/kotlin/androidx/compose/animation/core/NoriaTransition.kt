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

@file:OptIn(InternalAnimationApi::class)

package androidx.compose.animation.core

import noria.NoriaContext
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.runtime.*

/**
 * This creates a [DeferredAnimation], which will not animate until it is set up using
 * [DeferredAnimation.animate]. Once the animation is set up, it will animate from the
 * [currentState][Transition.currentState] to [targetState][Transition.targetState]. If the
 * [Transition] has already arrived at its target state at the time when the animation added, there
 * will be no animation.
 *
 * @param typeConverter A converter to convert any value of type [T] from/to an [AnimationVector]
 * @param label A label for differentiating this animation from others in android studio.
 *
 * @suppress
 */
@InternalAnimationApi
@Composable
fun <S, T, V : AnimationVector> Transition<S>.noriaCreateDeferredAnimation(
    noriaContext: NoriaContext,
    typeConverter: TwoWayConverter<T, V>,
    label: String = "DeferredAnimation"
): Transition<S>.DeferredAnimation<T, V> {
  with(noriaContext) {
    val lazyAnim = remember(this@noriaCreateDeferredAnimation) { DeferredAnimation(typeConverter, label) }
    DisposableEffect(lazyAnim) {
      onDispose {
        removeAnimation(lazyAnim)
      }
    }
    if (isSeeking) {
      lazyAnim.setupSeeking()
    }
    return lazyAnim
  }
}

/**
 * [noriaCreateChildTransition] creates a child Transition based on the mapping between parent state to
 * child state provided in [transformToChildState]. This serves the following purposes:
 * 1) Hoist the child transition state into parent transition. Therefore the parent Transition
 * will be aware of whether there's any on-going animation due to the same target state change.
 * This will further allow sequential animation to be set up when all animations have finished.
 * 2) Separation of concerns. The child transition can respresent a much more simplified state
 * transition when, for example, mapping from an enum parent state to a Boolean visible state for
 * passing further down the compose tree. The child composables hence can be designed around
 * handling a more simple and a more relevant state change.
 *
 * [label] is used to differentiate from other animations in the same transition in Android Studio.
 *
 * @sample androidx.compose.animation.core.samples.CreateChildTransitionSample
 */
@ExperimentalTransitionApi
@Composable
inline fun <S : @NoriaOnly Any, T> Transition<S>.noriaCreateChildTransition(
    noriaContext: NoriaContext,
    label: String = "ChildTransition",
    transformToChildState: @Composable (parentState: S) -> T,
): Transition<T> {
  with(noriaContext) {
    val initialParentState = remember(this@noriaCreateChildTransition) { this@noriaCreateChildTransition.currentState }
    val initialState = transformToChildState(if (isSeeking) currentState else initialParentState)
    val targetState = transformToChildState(this@noriaCreateChildTransition.targetState)
    return createChildTransitionInternal(noriaContext, initialState, targetState, label)
  }
}

@PublishedApi
@Composable
internal fun <S, T> Transition<S>.createChildTransitionInternal(
    noriaContext: NoriaContext,
    initialState: T,
    targetState: T,
    childLabel: String,
): Transition<T> {
  with(noriaContext) {
    val transition = remember(this@createChildTransitionInternal) {
      Transition(MutableTransitionState(initialState), "${this@createChildTransitionInternal.label} > $childLabel")
    }

    DisposableEffect(transition) {
      addTransition(transition)
      onDispose {
        removeTransition(transition)
      }
    }

    if (isSeeking) {
      transition.setPlaytimeAfterInitialAndTargetStateEstablished(
        initialState,
        targetState,
        this@createChildTransitionInternal.lastSeekedTimeNanos
      )
    }
    else {
      transition.updateTarget(targetState)
      transition.isSeeking = false
    }
    return transition
  }
}

/**
 * Creates an animation of type [T] as a part of the given [Transition]. This means the states
 * of this animation will be managed by the [Transition]. [typeConverter] will be used to convert
 * between type [T] and [AnimationVector] so that the animation system knows how to animate it.
 *
 * [targetValueByState] is used as a mapping from a target state to the target value of this
 * animation. [Transition] will be using this mapping to determine what value to target this
 * animation towards. __Note__ that [targetValueByState] is a composable function. This means the
 * mapping function could access states, CompositionLocals, themes, etc. If the targetValue changes
 * outside of a [Transition] run (i.e. when the [Transition] already reached its targetState), the
 * [Transition] will start running again to ensure this animation reaches its new target smoothly.
 *
 * An optional [transitionSpec] can be provided to specify (potentially different) animation for
 * each pair of initialState and targetState. [FiniteAnimationSpec] includes any non-infinite
 * animation, such as [tween], [spring], [keyframes] and even [repeatable], but not
 * [infiniteRepeatable]. By default, [transitionSpec] uses a [spring] animation for all transition
 * destinations.
 *
 * [label] is used to differentiate from other animations in the same transition in Android Studio.
 *
 * @return A [State] object, the value of which is updated by animation
 * @see updateTransition
 * @see Transition.noriaAnimateFloat
 * @see androidx.compose.animation.animateColor
 */
@Composable
inline fun <S, T, V : AnimationVector> Transition<S>.noriaAnimateValue(
    noriaContext: NoriaContext,
    typeConverter: TwoWayConverter<T, V>,
    noinline transitionSpec: @Composable Transition.Segment<S>.() -> FiniteAnimationSpec<T> =
        { spring() },
    label: String = "ValueAnimation",
    targetValueByState: @Composable NoriaContext.(state: S) -> T
): State<T> {

    val initialValue = noriaContext.targetValueByState(currentState)
    val targetValue = noriaContext.targetValueByState(targetState)
    val animationSpec = transitionSpec(segment)

    return createTransitionAnimation(noriaContext, initialValue, targetValue, animationSpec, typeConverter, label)
}

@PublishedApi
@Composable
internal fun <S, T, V : AnimationVector> Transition<S>.createTransitionAnimation(
    noriaContext: NoriaContext,
    initialValue: T,
    targetValue: T,
    animationSpec: FiniteAnimationSpec<T>,
    typeConverter: TwoWayConverter<T, V>,
    label: String
): State<T> {
  with(noriaContext) {
    val transitionAnimation = remember(this@createTransitionAnimation) {
      // Initialize the animation state to initialState value, so if it's added during a
      // transition run, it'll participate in the animation.
      // This is preferred because it's easy to opt out - Simply adding new animation once
      // currentState == targetState would opt out.
      TransitionAnimationState(
        initialValue,
        typeConverter.createZeroVectorFrom(targetValue),
        typeConverter,
        label
      )
    }
    if (isSeeking) {
      // In the case of seeking, we also need to update initial value as needed
      transitionAnimation.updateInitialAndTargetValue(
        initialValue,
        targetValue,
        animationSpec
      )
    }
    else {
      transitionAnimation.updateTargetValue(targetValue, animationSpec)
    }

    DisposableEffect(transitionAnimation) {
      addAnimation(transitionAnimation)
      onDispose {
        removeAnimation(transitionAnimation)
      }
    }
    return transitionAnimation
  }
}

// TODO: Remove noinline when b/174814083 is fixed.
/**
 * Creates a Float animation as a part of the given [Transition]. This means the states
 * of this animation will be managed by the [Transition].
 *
 * [targetValueByState] is used as a mapping from a target state to the target value of this
 * animation. [Transition] will be using this mapping to determine what value to target this
 * animation towards. __Note__ that [targetValueByState] is a composable function. This means the
 * mapping function could access states, CompositionLocals, themes, etc. If the targetValue changes
 * outside of a [Transition] run (i.e. when the [Transition] already reached its targetState), the
 * [Transition] will start running again to ensure this animation reaches its new target smoothly.
 *
 * An optional [transitionSpec] can be provided to specify (potentially different) animation for
 * each pair of initialState and targetState. [FiniteAnimationSpec] includes any non-infinite
 * animation, such as [tween], [spring], [keyframes] and even [repeatable], but not
 * [infiniteRepeatable]. By default, [transitionSpec] uses a [spring] animation for all transition
 * destinations.
 *
 * @sample androidx.compose.animation.core.samples.AnimateFloatSample
 *
 * [label] is used to differentiate from other animations in the same transition in Android Studio.
 *
 * @return A [State] object, the value of which is updated by animation
 * @see updateTransition
 * @see Transition.noriaAnimateValue
 * @see androidx.compose.animation.animateColor
 */
@Composable
inline fun <S> Transition<S>.noriaAnimateFloat(
    noriaContext: NoriaContext,
    noinline transitionSpec:
    @Composable Transition.Segment<S>.() -> FiniteAnimationSpec<Float> = { spring() },
    label: String = "FloatAnimation",
    targetValueByState: @Composable NoriaContext.(state: S) -> Float
): State<Float> =
    noriaAnimateValue(noriaContext, Float.VectorConverter, transitionSpec, label, targetValueByState)

/**
 * Creates a [Dp] animation as a part of the given [Transition]. This means the states
 * of this animation will be managed by the [Transition].
 *
 * [targetValueByState] is used as a mapping from a target state to the target value of this
 * animation. [Transition] will be using this mapping to determine what value to target this
 * animation towards. __Note__ that [targetValueByState] is a composable function. This means the
 * mapping function could access states, CompositionLocals, themes, etc. If the targetValue changes
 * outside of a [Transition] run (i.e. when the [Transition] already reached its targetState), the
 * [Transition] will start running again to ensure this animation reaches its new target smoothly.
 *
 * An optional [transitionSpec] can be provided to specify (potentially different) animation for
 * each pair of initialState and targetState. [FiniteAnimationSpec] includes any non-infinite
 * animation, such as [tween], [spring], [keyframes] and even [repeatable], but not
 * [infiniteRepeatable]. By default, [transitionSpec] uses a [spring] animation for all transition
 * destinations.
 *
 * [label] is used to differentiate from other animations in the same transition in Android Studio.
 *
 * @return A [State] object, the value of which is updated by animation
 */
@Composable
inline fun <S> Transition<S>.animateDp(
    noriaContext: NoriaContext,
    noinline transitionSpec: @Composable Transition.Segment<S>.() -> FiniteAnimationSpec<Dp> = {
        spring(visibilityThreshold = Dp.VisibilityThreshold)
    },
    label: String = "DpAnimation",
    targetValueByState: @Composable NoriaContext.(state: S) -> Dp
): State<Dp> =
    noriaAnimateValue(noriaContext, Dp.VectorConverter, transitionSpec, label, targetValueByState)

/**
 * Creates an [Offset] animation as a part of the given [Transition]. This means the states
 * of this animation will be managed by the [Transition].
 *
 * [targetValueByState] is used as a mapping from a target state to the target value of this
 * animation. [Transition] will be using this mapping to determine what value to target this
 * animation towards. __Note__ that [targetValueByState] is a composable function. This means the
 * mapping function could access states, CompositionLocals, themes, etc. If the targetValue changes
 * outside of a [Transition] run (i.e. when the [Transition] already reached its targetState), the
 * [Transition] will start running again to ensure this animation reaches its new target smoothly.
 *
 * An optional [transitionSpec] can be provided to specify (potentially different) animation for
 * each pair of initialState and targetState. [FiniteAnimationSpec] includes any non-infinite
 * animation, such as [tween], [spring], [keyframes] and even [repeatable], but not
 * [infiniteRepeatable]. By default, [transitionSpec] uses a [spring] animation for all transition
 * destinations.
 *
 * [label] is used to differentiate from other animations in the same transition in Android Studio.
 *
 * @return A [State] object, the value of which is updated by animation
 */
@Composable
inline fun <S> Transition<S>.noriaAnimateOffset(
    noriaContext: NoriaContext,
    noinline transitionSpec: @Composable Transition.Segment<S>.() -> FiniteAnimationSpec<Offset> = {
        spring(visibilityThreshold = Offset.VisibilityThreshold)
    },
    label: String = "OffsetAnimation",
    targetValueByState: @Composable NoriaContext.(state: S) -> Offset
): State<Offset> =
    noriaAnimateValue(noriaContext, Offset.VectorConverter, transitionSpec, label, targetValueByState)

/**
 * Creates a [Size] animation as a part of the given [Transition]. This means the states
 * of this animation will be managed by the [Transition].
 *
 * [targetValueByState] is used as a mapping from a target state to the target value of this
 * animation. [Transition] will be using this mapping to determine what value to target this
 * animation towards. __Note__ that [targetValueByState] is a composable function. This means the
 * mapping function could access states, CompositionLocals, themes, etc. If the targetValue changes
 * outside of a [Transition] run (i.e. when the [Transition] already reached its targetState), the
 * [Transition] will start running again to ensure this animation reaches its new target smoothly.
 *
 * An optional [transitionSpec] can be provided to specify (potentially different) animation for
 * each pair of initialState and targetState. [FiniteAnimationSpec] includes any non-infinite
 * animation, such as [tween], [spring], [keyframes] and even [repeatable], but not
 * [infiniteRepeatable]. By default, [transitionSpec] uses a [spring] animation for all transition
 * destinations.
 *
 * [label] is used to differentiate from other animations in the same transition in Android Studio.
 *
 * @return A [State] object, the value of which is updated by animation
 */
@Composable
inline fun <S> Transition<S>.noriaAnimateSize(
    noriaContext: NoriaContext,
    noinline transitionSpec: @Composable Transition.Segment<S>.() -> FiniteAnimationSpec<Size> = {
        spring(visibilityThreshold = Size.VisibilityThreshold)
    },
    label: String = "SizeAnimation",
    targetValueByState: @Composable NoriaContext.(state: S) -> Size
): State<Size> =
    noriaAnimateValue(noriaContext, Size.VectorConverter, transitionSpec, label, targetValueByState)

/**
 * Creates a [IntOffset] animation as a part of the given [Transition]. This means the states
 * of this animation will be managed by the [Transition].
 *
 * [targetValueByState] is used as a mapping from a target state to the target value of this
 * animation. [Transition] will be using this mapping to determine what value to target this
 * animation towards. __Note__ that [targetValueByState] is a composable function. This means the
 * mapping function could access states, CompositionLocals, themes, etc. If the targetValue changes
 * outside of a [Transition] run (i.e. when the [Transition] already reached its targetState), the
 * [Transition] will start running again to ensure this animation reaches its new target smoothly.
 *
 * An optional [transitionSpec] can be provided to specify (potentially different) animation for
 * each pair of initialState and targetState. [FiniteAnimationSpec] includes any non-infinite
 * animation, such as [tween], [spring], [keyframes] and even [repeatable], but not
 * [infiniteRepeatable]. By default, [transitionSpec] uses a [spring] animation for all transition
 * destinations.
 *
 * [label] is used to differentiate from other animations in the same transition in Android Studio.
 *
 * @return A [State] object, the value of which is updated by animation
 */
@Composable
inline fun <S> Transition<S>.noriaAnimateIntOffset(
    noriaContext: NoriaContext,
    noinline transitionSpec:
    @Composable Transition.Segment<S>.() -> FiniteAnimationSpec<IntOffset> =
        { spring(visibilityThreshold = IntOffset(1, 1)) },
    label: String = "IntOffsetAnimation",
    targetValueByState: @Composable NoriaContext.(state: S) -> IntOffset
): State<IntOffset> =
    noriaAnimateValue(noriaContext, IntOffset.VectorConverter, transitionSpec, label, targetValueByState)

/**
 * Creates a [Int] animation as a part of the given [Transition]. This means the states
 * of this animation will be managed by the [Transition].
 *
 * [targetValueByState] is used as a mapping from a target state to the target value of this
 * animation. [Transition] will be using this mapping to determine what value to target this
 * animation towards. __Note__ that [targetValueByState] is a composable function. This means the
 * mapping function could access states, CompositionLocals, themes, etc. If the targetValue changes
 * outside of a [Transition] run (i.e. when the [Transition] already reached its targetState), the
 * [Transition] will start running again to ensure this animation reaches its new target smoothly.
 *
 * An optional [transitionSpec] can be provided to specify (potentially different) animation for
 * each pair of initialState and targetState. [FiniteAnimationSpec] includes any non-infinite
 * animation, such as [tween], [spring], [keyframes] and even [repeatable], but not
 * [infiniteRepeatable]. By default, [transitionSpec] uses a [spring] animation for all transition
 * destinations.
 *
 * [label] is used to differentiate from other animations in the same transition in Android Studio.
 *
 * @return A [State] object, the value of which is updated by animation
 */
@Composable
inline fun <S> Transition<S>.noriaAnimateInt(
    noriaContext: NoriaContext,
    noinline transitionSpec: @Composable Transition.Segment<S>.() -> FiniteAnimationSpec<Int> = {
        spring(visibilityThreshold = 1)
    },
    label: String = "IntAnimation",
    targetValueByState: @Composable NoriaContext.(state: S) -> Int
): State<Int> =
    noriaAnimateValue(noriaContext, Int.VectorConverter, transitionSpec, label, targetValueByState)

/**
 * Creates a [IntSize] animation as a part of the given [Transition]. This means the states
 * of this animation will be managed by the [Transition].
 *
 * [targetValueByState] is used as a mapping from a target state to the target value of this
 * animation. [Transition] will be using this mapping to determine what value to target this
 * animation towards. __Note__ that [targetValueByState] is a composable function. This means the
 * mapping function could access states, CompositionLocals, themes, etc. If the targetValue changes
 * outside of a [Transition] run (i.e. when the [Transition] already reached its targetState), the
 * [Transition] will start running again to ensure this animation reaches its new target smoothly.
 *
 * An optional [transitionSpec] can be provided to specify (potentially different) animation for
 * each pair of initialState and targetState. [FiniteAnimationSpec] includes any non-infinite
 * animation, such as [tween], [spring], [keyframes] and even [repeatable], but not
 * [infiniteRepeatable]. By default, [transitionSpec] uses a [spring] animation for all transition
 * destinations.
 *
 * [label] is used to differentiate from other animations in the same transition in Android Studio.
 *
 * @return A [State] object, the value of which is updated by animation
 */
@Composable
inline fun <S> Transition<S>.noriaAnimateIntSize(
    noriaContext: NoriaContext,
    noinline transitionSpec: @Composable Transition.Segment<S>.() -> FiniteAnimationSpec<IntSize> =
        { spring(visibilityThreshold = IntSize(1, 1)) },
    label: String = "IntSizeAnimation",
    targetValueByState: @Composable NoriaContext.(state: S) -> IntSize
): State<IntSize> =
    noriaAnimateValue(noriaContext, IntSize.VectorConverter, transitionSpec, label, targetValueByState)

/**
 * Creates a [Rect] animation as a part of the given [Transition]. This means the states
 * of this animation will be managed by the [Transition].
 *
 * [targetValueByState] is used as a mapping from a target state to the target value of this
 * animation. [Transition] will be using this mapping to determine what value to target this
 * animation towards. __Note__ that [targetValueByState] is a composable function. This means the
 * mapping function could access states, CompositionLocals, themes, etc. If the targetValue changes
 * outside of a [Transition] run (i.e. when the [Transition] already reached its targetState), the
 * [Transition] will start running again to ensure this animation reaches its new target smoothly.
 *
 * An optional [transitionSpec] can be provided to specify (potentially different) animation for
 * each pair of initialState and targetState. [FiniteAnimationSpec] includes any non-infinite
 * animation, such as [tween], [spring], [keyframes] and even [repeatable], but not
 * [infiniteRepeatable]. By default, [transitionSpec] uses a [spring] animation for all transition
 * destinations.
 *
 * [label] is used to differentiate from other animations in the same transition in Android Studio.
 *
 * @return A [State] object, the value of which is updated by animation
 */
@Composable
inline fun <S> Transition<S>.noriaAnimateRect(
    noriaContext: NoriaContext,
    noinline transitionSpec: @Composable Transition.Segment<S>.() -> FiniteAnimationSpec<Rect> =
        { spring(visibilityThreshold = Rect.VisibilityThreshold) },
    label: String = "RectAnimation",
    targetValueByState: @Composable NoriaContext.(state: S) -> Rect
): State<Rect> =
    noriaAnimateValue(noriaContext, Rect.VectorConverter, transitionSpec, label, targetValueByState)
