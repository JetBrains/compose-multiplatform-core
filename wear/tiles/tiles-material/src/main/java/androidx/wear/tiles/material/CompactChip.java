/*
 * Copyright 2021 The Android Open Source Project
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

package androidx.wear.tiles.material;

import android.content.Context;

import androidx.annotation.RestrictTo;
import androidx.annotation.RestrictTo.Scope;
import androidx.wear.protolayout.expression.Fingerprint;
import androidx.wear.protolayout.proto.LayoutElementProto;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Tiles component {@link CompactChip} that represents clickable object with the text.
 *
 * <p>The Chip is Stadium shape and has a max height designed to take no more than one line of text
 * of {@link Typography#TYPOGRAPHY_CAPTION1} style. Width of the chip is adjustable to the text
 * size.
 *
 * <p>The recommended set of {@link ChipColors} styles can be obtained from {@link ChipDefaults}.,
 * e.g. {@link ChipDefaults#COMPACT_PRIMARY_COLORS} to get a color scheme for a primary {@link
 * CompactChip}.
 *
 * <p>When accessing the contents of a container for testing, note that this element can't be simply
 * casted back to the original type, i.e.:
 *
 * <pre>{@code
 * CompactChip chip = new CompactChip...
 * Box box = new Box.Builder().addContent(chip).build();
 *
 * CompactChip myChip = (CompactChip) box.getContents().get(0);
 * }</pre>
 *
 * will fail.
 *
 * <p>To be able to get {@link CompactChip} object from any layout element, {@link
 * #fromLayoutElement} method should be used, i.e.:
 *
 * <pre>{@code
 * CompactChip myChip = CompactChip.fromLayoutElement(box.getContents().get(0));
 * }</pre>
 *
 * @deprecated Use the new class {@link androidx.wear.protolayout.material.CompactChip} which
 *     provides the same API and functionality.
 */
@Deprecated
@SuppressWarnings("deprecation")
public class CompactChip implements androidx.wear.tiles.LayoutElementBuilders.LayoutElement {
    /**
     * Tool tag for Metadata in androidx.wear.tiles.ModifiersBuilders.Modifiers, so we know that
     * androidx.wear.tiles.LayoutElementBuilders.Box is actually a CompactChip.
     */
    static final String METADATA_TAG = "CMPCHP";

    private final androidx.wear.tiles.LayoutElementBuilders.@NonNull Box mImpl;
    private final @NonNull Chip mElement;

    CompactChip(androidx.wear.tiles.LayoutElementBuilders.@NonNull Box element) {
        this.mImpl = element;
        // We know for sure that content of the androidx.wear.tiles.LayoutElementBuilders.Box is
        // Chip.
        this.mElement =
                new Chip(
                        (androidx.wear.tiles.LayoutElementBuilders.Box)
                                element.getContents().get(0));
    }

    /** Builder class for {@link androidx.wear.tiles.material.CompactChip}. */
    public static final class Builder
            implements androidx.wear.tiles.LayoutElementBuilders.LayoutElement.Builder {
        private final @NonNull Context mContext;
        private final @NonNull String mText;
        private final androidx.wear.tiles.ModifiersBuilders.@NonNull Clickable mClickable;

        private final androidx.wear.tiles.DeviceParametersBuilders.@NonNull DeviceParameters
                mDeviceParameters;

        private @NonNull ChipColors mChipColors = ChipDefaults.COMPACT_PRIMARY_COLORS;

        /**
         * Creates a builder for the {@link CompactChip} with associated action and the given text
         *
         * @param context The application's context.
         * @param text The text to be displayed in this compact chip.
         * @param clickable Associated {@link androidx.wear.tiles.ModifiersBuilders.Clickable} for
         *     click events. When the CompactChip is clicked it will fire the associated action.
         * @param deviceParameters The device parameters used for styling text.
         */
        public Builder(
                @NonNull Context context,
                @NonNull String text,
                androidx.wear.tiles.ModifiersBuilders.@NonNull Clickable clickable,
                androidx.wear.tiles.DeviceParametersBuilders.@NonNull DeviceParameters
                                deviceParameters) {
            this.mContext = context;
            this.mText = text;
            this.mClickable = clickable;
            this.mDeviceParameters = deviceParameters;
        }

        /**
         * Sets the colors for the {@link CompactChip}. If set, {@link
         * ChipColors#getBackgroundColor()} will be used for the background of the button and {@link
         * ChipColors#getContentColor()} for the text. If not set, {@link
         * ChipDefaults#COMPACT_PRIMARY_COLORS} will be used.
         */
        public @NonNull Builder setChipColors(@NonNull ChipColors chipColors) {
            mChipColors = chipColors;
            return this;
        }

        /** Constructs and returns {@link CompactChip} with the provided content and look. */
        @Override
        public @NonNull CompactChip build() {
            Chip.Builder chipBuilder =
                    new Chip.Builder(mContext, mClickable, mDeviceParameters)
                            .setMetadataTag(METADATA_TAG)
                            .setChipColors(mChipColors)
                            .setContentDescription(mText)
                            .setHorizontalAlignment(
                                    androidx.wear.tiles.LayoutElementBuilders
                                            .HORIZONTAL_ALIGN_CENTER)
                            .setWidth(androidx.wear.tiles.DimensionBuilders.wrap())
                            .setHeight(ChipDefaults.COMPACT_HEIGHT)
                            .setMaxLines(1)
                            .setHorizontalPadding(ChipDefaults.COMPACT_HORIZONTAL_PADDING)
                            .setPrimaryLabelContent(mText)
                            .setPrimaryLabelTypography(Typography.TYPOGRAPHY_CAPTION1)
                            .setIsPrimaryLabelScalable(false);

            androidx.wear.tiles.LayoutElementBuilders.Box tappableChip =
                    new androidx.wear.tiles.LayoutElementBuilders.Box.Builder()
                            .setModifiers(
                                    new androidx.wear.tiles.ModifiersBuilders.Modifiers.Builder()
                                            .setClickable(mClickable)
                                            .setMetadata(
                                                    new androidx.wear.tiles.ModifiersBuilders
                                                                    .ElementMetadata.Builder()
                                                            .setTagData(
                                                                    androidx.wear.tiles.material
                                                                            .Helper.getTagBytes(
                                                                            METADATA_TAG))
                                                            .build())
                                            .build())
                            .setWidth(androidx.wear.tiles.DimensionBuilders.wrap())
                            .setHeight(ChipDefaults.COMPACT_HEIGHT_TAPPABLE)
                            .setVerticalAlignment(
                                    androidx.wear.tiles.LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
                            .addContent(chipBuilder.build())
                            .build();

            return new CompactChip(tappableChip);
        }
    }

    /** Returns click event action associated with this Chip. */
    public androidx.wear.tiles.ModifiersBuilders.@NonNull Clickable getClickable() {
        return mElement.getClickable();
    }

    /** Returns chip color of this Chip. */
    public @NonNull ChipColors getChipColors() {
        return mElement.getChipColors();
    }

    /** Returns text content of this Chip. */
    public @NonNull String getText() {
        return androidx.wear.tiles.material.Helper.checkNotNull(mElement.getPrimaryLabelContent());
    }

    /** Returns metadata tag set to this CompactChip, which should be {@link #METADATA_TAG}. */
    @NonNull String getMetadataTag() {
        return mElement.getMetadataTag();
    }

    /**
     * Returns CompactChip object from the given
     * androidx.wear.tiles.LayoutElementBuilders.LayoutElement (e.g. one retrieved from a
     * container's content with {@code container.getContents().get(index)}) if that element can be
     * converted to CompactChip. Otherwise, it will return null.
     */
    public static @Nullable CompactChip fromLayoutElement(
            androidx.wear.tiles.LayoutElementBuilders.@NonNull LayoutElement element) {
        if (element instanceof CompactChip) {
            return (CompactChip) element;
        }
        if (!(element instanceof androidx.wear.tiles.LayoutElementBuilders.Box)) {
            return null;
        }
        androidx.wear.tiles.LayoutElementBuilders.Box boxElement =
                (androidx.wear.tiles.LayoutElementBuilders.Box) element;
        if (!androidx.wear.tiles.material.Helper.checkTag(
                boxElement.getModifiers(), METADATA_TAG)) {
            return null;
        }
        // Now to check that inner content of the androidx.wear.tiles.LayoutElementBuilders.Box is
        // CompactChip's Chip.
        androidx.wear.tiles.LayoutElementBuilders.LayoutElement innerElement =
                boxElement.getContents().get(0);
        if (!(innerElement instanceof androidx.wear.tiles.LayoutElementBuilders.Box)) {
            return null;
        }
        androidx.wear.tiles.LayoutElementBuilders.Box innerBoxElement =
                (androidx.wear.tiles.LayoutElementBuilders.Box) innerElement;
        if (!androidx.wear.tiles.material.Helper.checkTag(
                innerBoxElement.getModifiers(), METADATA_TAG)) {
            return null;
        }

        // Now we are sure that this element is a CompactChip.
        return new CompactChip(boxElement);
    }

    @RestrictTo(Scope.LIBRARY_GROUP)
    @Override
    public LayoutElementProto.@NonNull LayoutElement toLayoutElementProto() {
        return mImpl.toLayoutElementProto();
    }

    @RestrictTo(Scope.LIBRARY_GROUP)
    @Override
    public @Nullable Fingerprint getFingerprint() {
        return mImpl.getFingerprint();
    }
}
