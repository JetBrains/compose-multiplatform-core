/*
 * Copyright (C) 2025 The Android Open Source Project
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

package androidx.ink.authoring

import androidx.annotation.RestrictTo
import androidx.ink.geometry.Box
import androidx.ink.strokes.StrokeInputBatch

/**
 * Implement this interface to efficiently build a custom shape over the course of multiple
 * rendering frames with incremental freehand inputs.
 *
 * Resembles the interface of [androidx.ink.strokes.InProgressStroke], but can be implemented with
 * custom functionality to achieve low latency authoring with custom geometry and rendering.
 *
 * The internal state of this object should only be modified by the functions of this interface so
 * that its rendered state stays in sync properly with its logical state. Low latency rendering
 * avoids re-rendering the object each time, so managing this improperly could result in graphics
 * artifacts or poor performance.
 *
 * This instance will be reused for performance purposes according to the following lifecycle:
 * 1. A shape is begun by calling [start] with a chosen [ShapeSpec].
 * 2. The shape is repeatedly updated by:
 *     1. Calling [enqueueInputs] with any new real and predicted freehand inputs. This may happen
 *        multiple times in a row before the next call to [update].
 *     2. Calling [update] to process the enqueued inputs into a calculated shape for rendering. If
 *        [changesWithTime] returns `true`, then this may be called multiple times in a row before
 *        the next call to [enqueueInputs].
 *     3. Rendering the calculated shape using an [InProgressShapeRenderer] compatible with this
 *        shape type.
 * 3. Call [finishInput] once there are no more inputs for this stroke (e.g. the user lifts the
 *    stylus from the screen).
 * 4. Continue to call [update] and render until [getCompletedShape] returns a non-null value, and
 *    even afterwards if [changesWithTime] returns `true`.
 *
 * @param ShapeSpec A type that defines how inputs will be interpreted. This is typically pure,
 *   immutable data.
 * @param CompletedShape A type that represents the completed form of this [InProgressShape].
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP) // PublicApiNotReadyForJetpackReview
public interface InProgressShape<ShapeSpec : Any, CompletedShape : Any> {

    /**
     * Clear and start a new shape with the given [ShapeSpec].
     *
     * This includes clearing or resetting any existing inputs, calculated shape data, and updated
     * region. This method will be called at least once after construction before making any calls
     * to [enqueueInputs] or [update].
     */
    public fun start(shapeSpec: ShapeSpec)

    /**
     * Append the incremental [realInputs] and set the prediction to [predictedInputs], replacing
     * any previous prediction. Queued inputs must not be processed right away - they can only be
     * processed on the next call to [update].
     *
     * Implementations of this function can assume that:
     * * [start] has been previously called to set the current [ShapeSpec].
     * * [finishInput] has not been called since the last call to [start].
     * * [realInputs] and [predictedInputs] form a valid stroke input sequence together with
     *   previously added real input. In particular, this means that the first input in [realInputs]
     *   must be valid following the last input in previously added real inputs, and the first input
     *   in [predictedInputs] must be valid following the last input in [realInputs]: They have the
     *   same [androidx.ink.brush.InputToolType], their
     *   [androidx.ink.strokes.StrokeInput.elapsedTimeMillis] values are monotonically
     *   non-decreasing, and they do not duplicate the previous input.
     *
     * Either one or both of [realInputs] and [predictedInputs] may be empty.
     *
     * The [realInputs] and [predictedInputs] instances will be recycled and cannot be retained -
     * implementations must copy the necessary data from them before this function returns.
     */
    public fun enqueueInputs(realInputs: StrokeInputBatch, predictedInputs: StrokeInputBatch)

    /**
     * Whether calls to [update] with new timestamp values may cause changes to [getUpdatedRegion],
     * even when there haven't been calls to [enqueueInputs].
     */
    public fun changesWithTime(): Boolean

    /**
     * Update internal state of this shape based on the provided timestamps as well as any inputs
     * passed to [enqueueInputs] since the last call to [update].
     *
     * @param inputElapsedTimeMillis The current timestamp that is directly comparable to those in
     *   the [StrokeInputBatch] objects provided to [enqueueInputs], which is the time since the
     *   first input event for this stroke. This timestamp is monotonically non-decreasing. Prefer
     *   to use this value for most circumstances instead of [systemElapsedTimeMillis].
     * @param systemElapsedTimeMillis The current timestamp in the
     *   [android.os.SystemClock.uptimeMillis] time base. Prefer to use [inputElapsedTimeMillis] for
     *   most cases, but this value may be useful when aligning with animations in the rest of an
     *   application as it's comparable with [android.view.Choreographer.FrameCallback] timestamps
     *   used for animation calculations.
     * @param forceCompletion When this is `true`, then a subsequent call to [getCompletedShape]
     *   must should return a non-null [CompletedShape].
     */
    public fun update(
        inputElapsedTimeMillis: Long,
        systemElapsedTimeMillis: Long,
        forceCompletion: Boolean,
    )

    /**
     * Indicates that this shape should will longer be drawn. It will not receive any further calls
     * to [enqueueInputs] or [update], and it will never receive a call to [finishInput]. A call to
     * this function should be reflected in the next call to [getUpdatedRegion] - the updated region
     * of a canceled shape must include the entire shape's bounding box, along with the locations of
     * any other content that had been removed before cancellation but after the last call to
     * [resetUpdatedRegion]. After [resetUpdatedRegion] has been called following [cancel], then
     * further calls to [getUpdatedRegion] must return `null`.
     */
    public fun cancel()

    /**
     * Returns an axis-aligned bounding rectangle of parts of the shape added, removed, or changed
     * since the last call to [resetUpdatedRegion]. The primary way that these changes would occur
     * is through calls to [update], but calls to [cancel] should also be reflected here by
     * including the most recent bounding box of the entire shape. The resulting [Box] should be in
     * the same coordinate system as the inputs supplied to [enqueueInputs]. If no changes have
     * occurred since the last call to [resetUpdatedRegion], then this must return `null`.
     */
    public fun getUpdatedRegion(): Box?

    /**
     * Reset the calculation of [getUpdatedRegion] so that if it is called again after this without
     * calling [update] or [cancel] first, then the result will be `null` to represent no changes.
     */
    public fun resetUpdatedRegion()

    /** Indicate that no new calls to [enqueueInputs] will be made. */
    public fun finishInput()

    /**
     * Get the [CompletedShape] if this shape has been completed, or `null` otherwise. This can only
     * return a non-null value sometime after [finishInput] is called.
     *
     * In most circumstances, this will return a value as soon as [finishInput] is called. But some
     * shapes involve time-based effects after all inputs have been received, which may cause this
     * to return `null` for a little while longer.
     *
     * If those effects are finite and prevent the shape from being considered completed, then this
     * function may return `null` until the time passed to [update] progresses enough. Some examples
     * of this include a shape "settling", e.g. to simulate motion coming to rest or the physical
     * process of ink absorbing or "bleeding" into paper.
     *
     * Once this returns a non-null value, it will not be called again until this object is reused
     * for another shape by [start] being called.
     *
     * But even after this function returns a non-null value, [changesWithTime] may still return
     * `true`, leading to more calls to [update] that can power an infinite time-based effect such
     * as a looping animation, but which doesn't affect the [CompletedShape] returned by this
     * function. An example of this may be to calculate an animation progress value which is
     * expected to be continued once the shape has been handed off via
     * [InProgressStrokesFinishedListener].
     */
    public fun getCompletedShape(): CompletedShape?

    /**
     * Clears the shape without starting a new one. It may be recycled, so for best performance,
     * consider clearing data without freeing underlying resources that can be reused.
     */
    public fun clear()
}
