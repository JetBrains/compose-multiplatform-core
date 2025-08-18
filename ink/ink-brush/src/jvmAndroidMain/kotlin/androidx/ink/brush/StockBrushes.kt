/*
 * Copyright (C) 2024 The Android Open Source Project
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

package androidx.ink.brush

import androidx.annotation.RestrictTo
import androidx.ink.brush.BrushBehavior.BinaryOp
import androidx.ink.brush.BrushBehavior.BinaryOpNode
import androidx.ink.brush.BrushBehavior.OutOfRange
import androidx.ink.brush.BrushBehavior.ResponseNode
import androidx.ink.brush.BrushBehavior.Source
import androidx.ink.brush.BrushBehavior.SourceNode
import androidx.ink.brush.BrushBehavior.Target
import androidx.ink.brush.BrushBehavior.TargetNode
import androidx.ink.brush.BrushPaint.BlendMode
import androidx.ink.brush.BrushPaint.TextureLayer
import androidx.ink.brush.BrushPaint.TextureMapping
import androidx.ink.brush.BrushPaint.TextureOrigin
import androidx.ink.brush.BrushPaint.TextureSizeUnit
import androidx.ink.brush.BrushPaint.TextureWrap
import androidx.ink.geometry.Angle
import kotlin.jvm.JvmStatic

/**
 * Provides a fixed set of stock [BrushFamily] objects that any app can use.
 *
 * All brush designs are versioned, so apps can safely store input points and brush specs instead of
 * the pixel result, but be able to regenerate strokes from stored input points that look like the
 * strokes originally drawn by the user. Brush designs are intended to evolve over time, and are
 * released as update packs to the stock library.
 *
 * Each successive brush version will keep to the spirit of the brush, but the actual effect can
 * change between versions. For example, a new version of the highlighter may introduce a variation
 * on how round the tip is, or what sort of curve maps color to pressure.
 *
 * We generally recommend that applications use the latest brush version available; but some use
 * cases, such as art, should be careful to track which version of a brush was used if the document
 * is regenerated, so that the user gets the same visual result.
 */
@OptIn(ExperimentalInkCustomBrushApi::class)
public object StockBrushes {

    /**
     * The scale factor to apply to both X and Y dimensions of the mini emoji brush tip and texture
     * layer size.
     */
    private const val EMOJI_STAMP_SCALE = 1.5f

    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP) // NonPublicApi
    @get:RestrictTo(RestrictTo.Scope.LIBRARY_GROUP) // NonPublicApi
    @JvmStatic
    public val predictionFadeOutBehavior: BrushBehavior =
        BrushBehavior(
            terminalNodes =
                listOf(
                    TargetNode(
                        target = Target.OPACITY_MULTIPLIER,
                        targetModifierRangeStart = 1F,
                        targetModifierRangeEnd = 0.3F,
                        BinaryOpNode(
                            operation = BinaryOp.PRODUCT,
                            firstInput =
                                SourceNode(
                                    source = Source.PREDICTED_TIME_ELAPSED_IN_MILLIS,
                                    sourceValueRangeStart = 0F,
                                    sourceValueRangeEnd = 24F,
                                ),
                            // The second branch of the binary op node keeps the opacity fade-out
                            // from starting
                            // until the predicted inputs have traveled at least 1.5x brush-size.
                            secondInput =
                                ResponseNode(
                                    responseCurve = EasingFunction.Predefined.EASE_IN_OUT,
                                    input =
                                        SourceNode(
                                            source =
                                                Source
                                                    .PREDICTED_DISTANCE_TRAVELED_IN_MULTIPLES_OF_BRUSH_SIZE,
                                            sourceValueRangeStart = 1.5F,
                                            sourceValueRangeEnd = 2F,
                                        ),
                                ),
                        ),
                    )
                )
        )

    /**
     * Version 1 of a simple, circular fixed-width brush.
     *
     * The behavior of this [BrushFamily] will not meaningfully change in future releases. More
     * significant updates would be contained in a [BrushFamily] with a different name specifying a
     * later version number.
     */
    @JvmStatic
    public val markerV1: BrushFamily =
        BrushFamily(
            tip = BrushTip(behaviors = listOf(predictionFadeOutBehavior)),
            inputModel = BrushFamily.SPRING_MODEL,
        )

    /**
     * The latest version of a simple, circular fixed-width brush.
     *
     * The behavior of this [BrushFamily] may change in future releases, as it always points to the
     * latest version of the marker.
     */
    @JvmStatic public val markerLatest: BrushFamily = markerV1

    /**
     * Version 1 of a pressure- and speed-sensitive brush that is optimized for handwriting with a
     * stylus.
     *
     * The behavior of this [BrushFamily] will not meaningfully change in future releases. More
     * significant updates would be contained in a [BrushFamily] with a different name specifying a
     * later version number.
     */
    @JvmStatic
    public val pressurePenV1: BrushFamily =
        BrushFamily(
            tip =
                BrushTip(
                    behaviors =
                        listOf(
                            predictionFadeOutBehavior,
                            BrushBehavior(
                                Source.DISTANCE_REMAINING_IN_MULTIPLES_OF_BRUSH_SIZE,
                                Target.SIZE_MULTIPLIER,
                                sourceValueRangeStart = 3f,
                                sourceValueRangeEnd = 0f,
                                targetModifierRangeStart = 1f,
                                targetModifierRangeEnd = 0.75f,
                                OutOfRange.CLAMP,
                            ),
                            BrushBehavior(
                                Source.NORMALIZED_DIRECTION_Y,
                                Target.SIZE_MULTIPLIER,
                                sourceValueRangeStart = 0.45f,
                                sourceValueRangeEnd = 0.65f,
                                targetModifierRangeStart = 1.0f,
                                targetModifierRangeEnd = 1.17f,
                                OutOfRange.CLAMP,
                                responseTimeMillis = 25L,
                            ),
                            BrushBehavior(
                                Source.INPUT_ACCELERATION_LATERAL_IN_CENTIMETERS_PER_SECOND_SQUARED,
                                Target.SIZE_MULTIPLIER,
                                sourceValueRangeStart = -80f,
                                sourceValueRangeEnd = -230f,
                                targetModifierRangeStart = 1.0f,
                                targetModifierRangeEnd = 1.25f,
                                OutOfRange.CLAMP,
                                responseTimeMillis = 25L,
                            ),
                            BrushBehavior(
                                Source.NORMALIZED_PRESSURE,
                                Target.SIZE_MULTIPLIER,
                                sourceValueRangeStart = 0.8f,
                                sourceValueRangeEnd = 1f,
                                targetModifierRangeStart = 1.0f,
                                targetModifierRangeEnd = 1.5f,
                                OutOfRange.CLAMP,
                                responseTimeMillis = 30L,
                                enabledToolTypes = setOf(InputToolType.STYLUS),
                            ),
                        )
                ),
            inputModel = BrushFamily.SPRING_MODEL,
        )

    /**
     * The latest version of a pressure- and speed-sensitive brush that is optimized for handwriting
     * with a stylus.
     *
     * The behavior of this [BrushFamily] may change in future releases, as it always points to the
     * latest version of the pressure pen.
     */
    @JvmStatic public val pressurePenLatest: BrushFamily = pressurePenV1

    /**
     * Version 1 of a chisel-tip brush that is intended for highlighting text in a document (when
     * used with a translucent brush color).
     *
     * The behavior of this [BrushFamily] will not meaningfully change in future releases. More
     * significant updates would be contained in a [BrushFamily] with a different name specifying a
     * later version number.
     */
    @JvmStatic
    public val highlighterV1: BrushFamily =
        BrushFamily(
            tip =
                BrushTip(
                    scaleX = 0.25f,
                    scaleY = 1f,
                    cornerRounding = 0.3f,
                    rotation = Angle.degreesToRadians(150f),
                    behaviors =
                        listOf(
                            predictionFadeOutBehavior,
                            BrushBehavior(
                                Source.DISTANCE_REMAINING_IN_MULTIPLES_OF_BRUSH_SIZE,
                                Target.CORNER_ROUNDING_OFFSET,
                                sourceValueRangeStart = 0f,
                                sourceValueRangeEnd = 1f,
                                targetModifierRangeStart = 0.3f,
                                targetModifierRangeEnd = 1f,
                                OutOfRange.CLAMP,
                                responseTimeMillis = 15L,
                            ),
                            BrushBehavior(
                                Source.DISTANCE_TRAVELED_IN_MULTIPLES_OF_BRUSH_SIZE,
                                Target.CORNER_ROUNDING_OFFSET,
                                sourceValueRangeStart = 0f,
                                sourceValueRangeEnd = 1f,
                                targetModifierRangeStart = 0.3f,
                                targetModifierRangeEnd = 1f,
                                OutOfRange.CLAMP,
                                responseTimeMillis = 15L,
                            ),
                            BrushBehavior(
                                Source.DISTANCE_TRAVELED_IN_MULTIPLES_OF_BRUSH_SIZE,
                                Target.OPACITY_MULTIPLIER,
                                sourceValueRangeStart = 0f,
                                sourceValueRangeEnd = 3f,
                                targetModifierRangeStart = 1.1f,
                                targetModifierRangeEnd = 1f,
                                OutOfRange.CLAMP,
                                responseTimeMillis = 15L,
                            ),
                            BrushBehavior(
                                Source.DISTANCE_REMAINING_IN_MULTIPLES_OF_BRUSH_SIZE,
                                Target.OPACITY_MULTIPLIER,
                                sourceValueRangeStart = 0f,
                                sourceValueRangeEnd = 3f,
                                targetModifierRangeStart = 1.1f,
                                targetModifierRangeEnd = 1f,
                                OutOfRange.CLAMP,
                                responseTimeMillis = 15L,
                            ),
                        ),
                ),
            inputModel = BrushFamily.SPRING_MODEL,
        )

    /**
     * The latest version of a chisel-tip brush that is intended for highlighting text in a document
     * (when used with a translucent brush color).
     *
     * The behavior of this [BrushFamily] may change in future releases, as it always points to the
     * latest version of the pressure pen.
     */
    @JvmStatic public val highlighterLatest: BrushFamily = highlighterV1

    /**
     * Version 1 of a brush that appears as rounded rectangles with gaps in between them. This may
     * be decorative, or can be used to signify a user interaction like free-form (lasso) selection.
     *
     * The behavior of this [BrushFamily] will not meaningfully change in future releases. More
     * significant updates would be contained in a [BrushFamily] with a different name specifying a
     * later version number.
     */
    @JvmStatic
    public val dashedLineV1: BrushFamily =
        BrushFamily(
            tip =
                BrushTip(
                    scaleX = 2F,
                    scaleY = 1F,
                    cornerRounding = 0.45F,
                    particleGapDistanceScale = 3F,
                    behaviors =
                        listOf(
                            predictionFadeOutBehavior,
                            BrushBehavior(
                                listOf(
                                    TargetNode(
                                        Target.ROTATION_OFFSET_IN_RADIANS,
                                        -Angle.HALF_TURN_RADIANS,
                                        Angle.HALF_TURN_RADIANS,
                                        SourceNode(
                                            Source.DIRECTION_ABOUT_ZERO_IN_RADIANS,
                                            -Angle.HALF_TURN_RADIANS,
                                            Angle.HALF_TURN_RADIANS,
                                            OutOfRange.CLAMP,
                                        ),
                                    )
                                )
                            ),
                        ),
                ),
            inputModel = BrushFamily.SPRING_MODEL,
        )

    /**
     * The latest version of a brush that appears as rounded rectangles with gaps in between them.
     * This may be decorative, or can be used to signify a user interaction like free-form (lasso)
     * selection.
     *
     * The behavior of this [BrushFamily] may change in future releases, as it always points to the
     * latest version of the pressure pen.
     */
    @JvmStatic public val dashedLineLatest: BrushFamily = dashedLineV1

    /** The client texture ID for the background of the version-1 pencil brush. */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP) // NonPublicApi
    @get:RestrictTo(RestrictTo.Scope.LIBRARY_GROUP) // NonPublicApi
    @JvmStatic
    public val pencilUnstableBackgroundTextureId: String =
        "androidx.ink.brush.StockBrushes.pencil_background_unstable"

    /**
     * A development version of a brush that looks like pencil marks on subtly textured paper.
     *
     * In order to use this brush, the [TextureBitmapStore] provided to your renderer must map the
     * [pencilUnstableBackgroundTextureId] to a bitmap; otherwise, no texture will be visible.
     * Android callers may want to use [StockTextureBitmapStore] to provide this mapping.
     *
     * The behavior of this [BrushFamily] may change significantly in future releases. Once it has
     * stabilized, it will be renamed to `pencilV1`.
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP) // NonPublicApi
    @get:RestrictTo(RestrictTo.Scope.LIBRARY_GROUP) // NonPublicApi
    @JvmStatic
    public val pencilUnstable: BrushFamily =
        BrushFamily(
            tip = BrushTip(behaviors = listOf(predictionFadeOutBehavior)),
            paint =
                BrushPaint(
                    listOf(
                        TextureLayer(
                            clientTextureId = pencilUnstableBackgroundTextureId,
                            sizeX = 512F,
                            sizeY = 512F,
                            sizeUnit = TextureSizeUnit.STROKE_COORDINATES,
                            mapping = TextureMapping.TILING,
                        )
                    )
                ),
            inputModel = BrushFamily.SPRING_MODEL,
        )

    /**
     * A brush coat that looks like a mini emoji.
     *
     * @param clientTextureId the client texture ID of the emoji to appear in the coat.
     * @param tipScale the scale factor to apply to both X and Y dimensions of the mini emoji
     * @param tipRotation the rotation to apply to the mini emoji
     * @param tipParticleGapDistanceScale the scale factor to apply to the particle gap distance
     * @param positionOffsetRangeStart the start of the range for the position offset behavior
     * @param positionOffsetRangeEnd the end of the range for the position offset behavior
     * @param distanceTraveledRangeStart the start of the range for the distance traveled behavior
     * @param distanceTraveledRangeEnd the end of the range for the distance traveled behavior
     * @param luminosityRangeStart the start of the range for the luminosity behavior
     * @param luminosityRangeEnd the end of the range for the luminosity behavior
     */
    private fun miniEmojiCoat(
        clientTextureId: String,
        tipScale: Float,
        tipRotation: Float,
        tipParticleGapDistanceScale: Float,
        positionOffsetRangeStart: Float,
        positionOffsetRangeEnd: Float,
        distanceTraveledRangeStart: Float,
        distanceTraveledRangeEnd: Float,
        luminosityRangeStart: Float,
        luminosityRangeEnd: Float,
    ): BrushCoat =
        BrushCoat(
            tip =
                BrushTip(
                    scaleX = tipScale,
                    scaleY = tipScale,
                    cornerRounding = 0f,
                    rotation = tipRotation,
                    particleGapDistanceScale = tipParticleGapDistanceScale,
                    behaviors =
                        listOf(
                            BrushBehavior(
                                terminalNodes =
                                    listOf(
                                        BrushBehavior.TargetNode(
                                            target = Target.SIZE_MULTIPLIER,
                                            targetModifierRangeStart = 1.0f,
                                            targetModifierRangeEnd = 0.0f,
                                            input =
                                                BrushBehavior.SourceNode(
                                                    source = Source.TIME_SINCE_INPUT_IN_SECONDS,
                                                    sourceValueRangeStart = 0.0f,
                                                    sourceValueRangeEnd = 0.7f,
                                                    sourceOutOfRangeBehavior = OutOfRange.CLAMP,
                                                ),
                                        )
                                    )
                            ),
                            BrushBehavior(
                                terminalNodes =
                                    listOf(
                                        BrushBehavior.TargetNode(
                                            target = BrushBehavior.Target.HUE_OFFSET_IN_RADIANS,
                                            Angle.degreesToRadians(59f),
                                            Angle.degreesToRadians(60f),
                                            input = BrushBehavior.ConstantNode(value = 0f),
                                        ),
                                        BrushBehavior.TargetNode(
                                            target = BrushBehavior.Target.LUMINOSITY,
                                            luminosityRangeStart,
                                            luminosityRangeEnd,
                                            input = BrushBehavior.ConstantNode(value = 0f),
                                        ),
                                    )
                            ),
                            BrushBehavior(
                                terminalNodes =
                                    listOf(
                                        BrushBehavior.TargetNode(
                                            target =
                                                BrushBehavior.Target
                                                    .POSITION_OFFSET_Y_IN_MULTIPLES_OF_BRUSH_SIZE,
                                            positionOffsetRangeStart,
                                            positionOffsetRangeEnd,
                                            input =
                                                BrushBehavior.ResponseNode(
                                                    responseCurve =
                                                        EasingFunction.Predefined.LINEAR,
                                                    input =
                                                        BrushBehavior.SourceNode(
                                                            source =
                                                                BrushBehavior.Source
                                                                    .DISTANCE_TRAVELED_IN_MULTIPLES_OF_BRUSH_SIZE,
                                                            distanceTraveledRangeStart,
                                                            distanceTraveledRangeEnd,
                                                            BrushBehavior.OutOfRange.REPEAT,
                                                        ),
                                                ),
                                        )
                                    )
                            ),
                        ),
                ),
            paint =
                BrushPaint(
                    textureLayers =
                        listOf(
                            TextureLayer(
                                clientTextureId = clientTextureId,
                                sizeX = 1f,
                                sizeY = 1f,
                                opacity = 0.4f,
                                mapping = TextureMapping.WINDING,
                                blendMode = BlendMode.MODULATE,
                            )
                        )
                ),
        )

    /**
     * Version 1 of emoji highlighter--a highlighter with an emoji at the head of the stroke, under
     * the pointer tip.
     *
     * In order to use this brush, the [TextureBitmapStore] provided to your renderer must map the
     * [clientTextureId] to a bitmap; otherwise, no texture will be visible. The emoji bitmap should
     * be a square, though the image can have a transparent background for emoji shapes that aren't
     * square.
     *
     * The behavior of this [BrushFamily] will not meaningfully change in future releases. More
     * significant updates would be contained in a [BrushFamily] with a different name specifying a
     * later version number.
     *
     * @param clientTextureId The client texture ID of the emoji to appear at the end of the stroke.
     *   This ID should map to a square bitmap with a transparent background in the implementation
     *   of [androidx.ink.brush.TextureBitmapStore] passed to
     *   [androidx.ink.rendering.android.canvas.CanvasStrokeRenderer.create].
     * @param showMiniEmojiTrail Whether to show a trail of miniature emojis disappearing from the
     *   stroke as it is drawn. Note that this will only render properly starting with Android U,
     *   and before Android U it is recommended to set this to false.
     */
    @JvmOverloads
    public fun emojiHighlighterV1(
        clientTextureId: String,
        showMiniEmojiTrail: Boolean = false,
    ): BrushFamily {
        return BrushFamily(
            coats =
                // Highlighter coat.
                buildList() {
                    add(
                        BrushCoat(
                            tip =
                                BrushTip(
                                    scaleX = 1f,
                                    scaleY = 1f,
                                    cornerRounding = 1f,
                                    behaviors =
                                        listOf(
                                            BrushBehavior(
                                                terminalNodes =
                                                    listOf(
                                                        BrushBehavior.TargetNode(
                                                            target = Target.OPACITY_MULTIPLIER,
                                                            targetModifierRangeStart = 1.0f,
                                                            targetModifierRangeEnd = 0.3f,
                                                            input =
                                                                BrushBehavior.BinaryOpNode(
                                                                    operation =
                                                                        BrushBehavior.BinaryOp
                                                                            .PRODUCT,
                                                                    firstInput =
                                                                        BrushBehavior.SourceNode(
                                                                            source =
                                                                                Source
                                                                                    .PREDICTED_TIME_ELAPSED_IN_MILLIS,
                                                                            sourceValueRangeStart =
                                                                                0.0f,
                                                                            sourceValueRangeEnd =
                                                                                24.0f,
                                                                            sourceOutOfRangeBehavior =
                                                                                OutOfRange.CLAMP,
                                                                        ),
                                                                    secondInput =
                                                                        BrushBehavior.ResponseNode(
                                                                            responseCurve =
                                                                                EasingFunction
                                                                                    .Predefined
                                                                                    .EASE_IN_OUT,
                                                                            input =
                                                                                BrushBehavior
                                                                                    .SourceNode(
                                                                                        source =
                                                                                            Source
                                                                                                .PREDICTED_DISTANCE_TRAVELED_IN_MULTIPLES_OF_BRUSH_SIZE,
                                                                                        sourceValueRangeStart =
                                                                                            1.5f,
                                                                                        sourceValueRangeEnd =
                                                                                            2.0f,
                                                                                        sourceOutOfRangeBehavior =
                                                                                            OutOfRange
                                                                                                .CLAMP,
                                                                                    ),
                                                                        ),
                                                                ),
                                                        )
                                                    )
                                            ),
                                            BrushBehavior(
                                                terminalNodes =
                                                    listOf(
                                                        BrushBehavior.TargetNode(
                                                            target = Target.OPACITY_MULTIPLIER,
                                                            targetModifierRangeStart = 1.2f,
                                                            targetModifierRangeEnd = 1.0f,
                                                            input =
                                                                BrushBehavior.DampingNode(
                                                                    dampingSource =
                                                                        BrushBehavior.DampingSource
                                                                            .TIME_IN_SECONDS,
                                                                    dampingGap = 0.01f,
                                                                    input =
                                                                        BrushBehavior.SourceNode(
                                                                            source =
                                                                                Source
                                                                                    .DISTANCE_TRAVELED_IN_MULTIPLES_OF_BRUSH_SIZE,
                                                                            sourceValueRangeStart =
                                                                                0.0f,
                                                                            sourceValueRangeEnd =
                                                                                2.0f,
                                                                            sourceOutOfRangeBehavior =
                                                                                OutOfRange.CLAMP,
                                                                        ),
                                                                ),
                                                        )
                                                    )
                                            ),
                                            BrushBehavior(
                                                terminalNodes =
                                                    listOf(
                                                        BrushBehavior.TargetNode(
                                                            target = Target.OPACITY_MULTIPLIER,
                                                            targetModifierRangeStart = 1.2f,
                                                            targetModifierRangeEnd = 1.0f,
                                                            input =
                                                                BrushBehavior.DampingNode(
                                                                    dampingSource =
                                                                        BrushBehavior.DampingSource
                                                                            .TIME_IN_SECONDS,
                                                                    dampingGap = 0.01f,
                                                                    input =
                                                                        BrushBehavior.SourceNode(
                                                                            source =
                                                                                Source
                                                                                    .DISTANCE_REMAINING_IN_MULTIPLES_OF_BRUSH_SIZE,
                                                                            sourceValueRangeStart =
                                                                                0.4f,
                                                                            sourceValueRangeEnd =
                                                                                2.4f,
                                                                            sourceOutOfRangeBehavior =
                                                                                OutOfRange.CLAMP,
                                                                        ),
                                                                ),
                                                        )
                                                    )
                                            ),
                                            BrushBehavior(
                                                terminalNodes =
                                                    listOf(
                                                        BrushBehavior.TargetNode(
                                                            target = Target.SIZE_MULTIPLIER,
                                                            targetModifierRangeStart = 1.0f,
                                                            targetModifierRangeEnd = 0.04f,
                                                            input =
                                                                BrushBehavior.DampingNode(
                                                                    dampingSource =
                                                                        BrushBehavior.DampingSource
                                                                            .TIME_IN_SECONDS,
                                                                    dampingGap = 0.01f,
                                                                    input =
                                                                        BrushBehavior.SourceNode(
                                                                            source =
                                                                                Source
                                                                                    .DISTANCE_REMAINING_IN_MULTIPLES_OF_BRUSH_SIZE,
                                                                            sourceValueRangeStart =
                                                                                0.3f,
                                                                            sourceValueRangeEnd =
                                                                                0.0f,
                                                                            sourceOutOfRangeBehavior =
                                                                                OutOfRange.CLAMP,
                                                                        ),
                                                                ),
                                                        )
                                                    )
                                            ),
                                        ),
                                )
                        )
                    )
                    // Minimoji trail coats.
                    if (showMiniEmojiTrail) {
                        add(
                            miniEmojiCoat(
                                clientTextureId = clientTextureId,
                                tipScale = 0.4f,
                                tipRotation = 0f,
                                tipParticleGapDistanceScale = 1.0f,
                                luminosityRangeStart = 0.48f,
                                luminosityRangeEnd = 2.0f,
                                positionOffsetRangeStart = -0.35f,
                                positionOffsetRangeEnd = 0.35f,
                                distanceTraveledRangeStart = 0f,
                                distanceTraveledRangeEnd = 0.22f,
                            )
                        )
                        add(
                            miniEmojiCoat(
                                clientTextureId = clientTextureId,
                                tipScale = 0.3f,
                                tipRotation = -Angle.degreesToRadians(35f),
                                tipParticleGapDistanceScale = 1.3f,
                                luminosityRangeStart = 0.8f,
                                luminosityRangeEnd = 2.0f,
                                positionOffsetRangeStart = -0.4f,
                                positionOffsetRangeEnd = 0.32f,
                                distanceTraveledRangeStart = 0.1f,
                                distanceTraveledRangeEnd = 0.74f,
                            )
                        )
                        add(
                            miniEmojiCoat(
                                clientTextureId = clientTextureId,
                                tipScale = 0.45f,
                                tipRotation = Angle.degreesToRadians(45f),
                                tipParticleGapDistanceScale = 1.8f,
                                luminosityRangeStart = 0.8f,
                                luminosityRangeEnd = 2.0f,
                                positionOffsetRangeStart = -0.25f,
                                positionOffsetRangeEnd = 0.25f,
                                distanceTraveledRangeStart = 0.01f,
                                distanceTraveledRangeEnd = 0.74f,
                            )
                        )
                    }

                    // Emoji stamp coat.
                    add(
                        BrushCoat(
                            tip =
                                BrushTip(
                                    scaleX = EMOJI_STAMP_SCALE,
                                    scaleY = EMOJI_STAMP_SCALE,
                                    cornerRounding = 0f,
                                    behaviors =
                                        listOf(
                                            BrushBehavior(
                                                source =
                                                    Source
                                                        .DISTANCE_REMAINING_IN_MULTIPLES_OF_BRUSH_SIZE,
                                                target = Target.SIZE_MULTIPLIER,
                                                sourceValueRangeStart = 0.01f,
                                                sourceValueRangeEnd = 0f,
                                                targetModifierRangeStart = 0f,
                                                targetModifierRangeEnd = 1f,
                                                sourceOutOfRangeBehavior = OutOfRange.CLAMP,
                                            )
                                        ),
                                ),
                            paint =
                                BrushPaint(
                                    listOf(
                                        TextureLayer(
                                            clientTextureId = clientTextureId,
                                            sizeX = EMOJI_STAMP_SCALE,
                                            sizeY = EMOJI_STAMP_SCALE,
                                            offsetX = -0.5f,
                                            offsetY = -0.5f,
                                            sizeUnit = TextureSizeUnit.BRUSH_SIZE,
                                            origin = TextureOrigin.LAST_STROKE_INPUT,
                                            wrapX = TextureWrap.CLAMP,
                                            wrapY = TextureWrap.CLAMP,
                                            blendMode = BlendMode.SRC,
                                        )
                                    )
                                ),
                        )
                    )
                },
            inputModel = BrushFamily.SPRING_MODEL,
        )
    }

    /**
     * The latest version of emoji highlighter--a highlighter with an emoji at the head of the
     * stroke, under the pointer tip.
     *
     * In order to use this brush, the [TextureBitmapStore] provided to your renderer must map the
     * [clientTextureId] to a bitmap; otherwise, no texture will be visible. The emoji bitmap should
     * be a square, though the image can have a transparent background for emoji shapes that aren't
     * square.
     *
     * The behavior of this [BrushFamily] may change in future releases, as it always points to the
     * latest version of the emoji highlighter.
     *
     * @param clientTextureId The client texture ID of the emoji to appear at the end of the stroke.
     *   This ID should map to a square bitmap with a transparent background in the implementation
     *   of [androidx.ink.brush.TextureBitmapStore] passed to
     *   [androidx.ink.rendering.android.canvas.CanvasStrokeRenderer.create].
     * @param showMiniEmojiTrail Whether to show a trail of miniature emojis disappearing from the
     *   stroke as it is drawn. Note that this will only render properly starting with Android U,
     *   and before Android U it is recommended to set this to false.
     */
    @JvmOverloads
    public fun emojiHighlighterLatest(
        clientTextureId: String,
        showMiniEmojiTrail: Boolean = false,
    ): BrushFamily = emojiHighlighterV1(clientTextureId, showMiniEmojiTrail)
}
