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

package androidx.compose.animation.core

import androidx.compose.animation.core.VectorConverter
import androidx.compose.runtime.*
import androidx.compose.runtime.remember
import noria.NoriaContext

/**
 * Creates an animation of type [T] that runs infinitely as a part of the given
 * [InfiniteTransition]. Any data type can be animated so long as it can be converted from and to
 * an [AnimationVector]. This conversion needs to be provided as a [typeConverter]. Some examples
 * of such [TwoWayConverter] are: [Int.VectorConverter][Int.Companion.VectorConverter],
 * [Dp.VectorConverter][Dp.Companion.VectorConverter],
 * [Size.VectorConverter][Size.Companion.VectorConverter], etc
 *
 * Once the animation is created, it will run from [initialValue] to [targetValue] and repeat.
 * Depending on the [RepeatMode] of the provided [animationSpec], the animation could either
 * restart after each iteration (i.e. [RepeatMode.Restart]), or reverse after each iteration (i.e
 * . [RepeatMode.Reverse]).
 *
 * If [initialValue] or [targetValue] is changed at any point during the animation, the animation
 * will be restarted with the new [initialValue] and [targetValue]. __Note__: this means
 * continuity will *not* be preserved.
 *
 * A [label] for differentiating this animation from others in android studio.
 *
 * @sample androidx.compose.animation.core.samples.InfiniteTransitionAnimateValueSample
 *
 * @see [InfiniteTransition.noriaAnimateFloat]
 * @see [androidx.compose.animation.animateColor]
 */
@Composable
fun <T, V : AnimationVector> InfiniteTransition.noriaAnimateValue(
  context: @NoriaOnly NoriaContext,
  initialValue: T,
  targetValue: T,
  typeConverter: TwoWayConverter<T, V>,
  animationSpec: InfiniteRepeatableSpec<T>,
  label: String = "ValueAnimation"
): State<T> {
  with(context) {
    val transitionAnimation =
      remember {
        TransitionAnimationState(
          initialValue, targetValue, typeConverter, animationSpec, label
        )
      }

    SideEffect {
      if (initialValue != transitionAnimation.initialValue ||
          targetValue != transitionAnimation.targetValue
      ) {
        transitionAnimation.updateValues(
          initialValue = initialValue,
          targetValue = targetValue,
          animationSpec = animationSpec
        )
      }
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

/**
 * Creates an animation of Float type that runs infinitely as a part of the given
 * [InfiniteTransition].
 *
 * Once the animation is created, it will run from [initialValue] to [targetValue] and repeat.
 * Depending on the [RepeatMode] of the provided [animationSpec], the animation could either
 * restart after each iteration (i.e. [RepeatMode.Restart]), or reverse after each iteration (i.e
 * . [RepeatMode.Reverse]).
 *
 * If [initialValue] or [targetValue] is changed at any point during the animation, the animation
 * will be restarted with the new [initialValue] and [targetValue]. __Note__: this means
 * continuity will *not* be preserved.
 *
 * A [label] for differentiating this animation from others in android studio.
 *
 * @sample androidx.compose.animation.core.samples.InfiniteTransitionSample
 *
 * @see [InfiniteTransition.noriaAnimateValue]
 * @see [androidx.compose.animation.animateColor]
 */
@Composable
fun InfiniteTransition.noriaAnimateFloat(
  context: @NoriaOnly NoriaContext,
  initialValue: Float,
  targetValue: Float,
  animationSpec: InfiniteRepeatableSpec<Float>,
  label: String = "FloatAnimation"
): State<Float> =
  noriaAnimateValue(context, initialValue, targetValue, Float.VectorConverter, animationSpec, label)

@Deprecated(
  "animateValue APIs now have a new label parameter added.",
  level = DeprecationLevel.HIDDEN
)
@Composable
fun <T, V : AnimationVector> InfiniteTransition.noriaAnimateValue(
  context: @NoriaOnly NoriaContext,
  initialValue: T,
  targetValue: T,
  typeConverter: TwoWayConverter<T, V>,
  animationSpec: InfiniteRepeatableSpec<T>,
): State<T> {
  return noriaAnimateValue(
    context,
    initialValue = initialValue,
    targetValue = targetValue,
    typeConverter = typeConverter,
    animationSpec = animationSpec,
    label = "ValueAnimation"
  )
}

@Deprecated(
  "animateFloat APIs now have a new label parameter added.",
  level = DeprecationLevel.HIDDEN
)
@Composable
fun InfiniteTransition.noriaAnimateFloat(
  context: @NoriaOnly NoriaContext,
  initialValue: Float,
  targetValue: Float,
  animationSpec: InfiniteRepeatableSpec<Float>
): State<Float> {
  return noriaAnimateFloat(
    context,
    initialValue = initialValue,
    targetValue = targetValue,
    animationSpec = animationSpec,
    label = "FloatAnimation"
  )
}
