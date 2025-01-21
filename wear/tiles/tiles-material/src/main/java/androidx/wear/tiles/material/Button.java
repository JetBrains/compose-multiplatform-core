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

import static androidx.annotation.Dimension.DP;

import android.content.Context;

import androidx.annotation.Dimension;
import androidx.annotation.IntDef;
import androidx.annotation.RestrictTo;
import androidx.annotation.RestrictTo.Scope;
import androidx.wear.protolayout.expression.Fingerprint;
import androidx.wear.protolayout.proto.LayoutElementProto;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.HashMap;
import java.util.Map;

/**
 * Tiles component {@link Button} that represents clickable button with the given content.
 *
 * <p>The Button is circular in shape. The recommended sizes are defined in {@link ButtonDefaults}.
 *
 * <p>The recommended set of {@link ButtonColors} styles can be obtained from {@link
 * ButtonDefaults}., e.g. {@link ButtonDefaults#PRIMARY_COLORS} to get a color scheme for a primary
 * {@link Button}.
 *
 * <p>When accessing the contents of a container for testing, note that this element can't be simply
 * casted back to the original type, i.e.:
 *
 * <pre>{@code
 * Button button = new Button...
 * Box box = new Box.Builder().addContent(button).build();
 *
 * Button myButton = (Button) box.getContents().get(0);
 * }</pre>
 *
 * will fail.
 *
 * <p>To be able to get {@link Button} object from any layout element, {@link #fromLayoutElement}
 * method should be used, i.e.:
 *
 * <pre>{@code
 * Button myButton = Button.fromLayoutElement(box.getContents().get(0));
 * }</pre>
 *
 * @deprecated Use the new class {@link androidx.wear.protolayout.material.Button} which provides
 *     the same API and functionality.
 */
@Deprecated
@SuppressWarnings("deprecation")
public class Button implements androidx.wear.tiles.LayoutElementBuilders.LayoutElement {
    /**
     * Tool tag for Metadata in androidx.wear.tiles.ModifiersBuilders.Modifiers, so we know that
     * androidx.wear.tiles.LayoutElementBuilders.Box is actually a Button with text.
     */
    static final String METADATA_TAG_TEXT = "TXTBTN";

    /**
     * Tool tag for Metadata in androidx.wear.tiles.ModifiersBuilders.Modifiers, so we know that
     * androidx.wear.tiles.LayoutElementBuilders.Box is actually a Button with icon.
     */
    static final String METADATA_TAG_ICON = "ICNBTN";

    /**
     * Tool tag for Metadata in androidx.wear.tiles.ModifiersBuilders.Modifiers, so we know that
     * androidx.wear.tiles.LayoutElementBuilders.Box is actually a Button with image.
     */
    static final String METADATA_TAG_IMAGE = "IMGBTN";

    /**
     * Tool tag for Metadata in androidx.wear.tiles.ModifiersBuilders.Modifiers, so we know that
     * androidx.wear.tiles.LayoutElementBuilders.Box is actually a Button with custom content.
     */
    static final String METADATA_TAG_CUSTOM_CONTENT = "CSTBTN";

    private final androidx.wear.tiles.LayoutElementBuilders.@NonNull Box mElement;

    Button(androidx.wear.tiles.LayoutElementBuilders.@NonNull Box element) {
        mElement = element;
    }

    /** Builder class for {@link Button}. */
    public static final class Builder
            implements androidx.wear.tiles.LayoutElementBuilders.LayoutElement.Builder {
        private static final int NOT_SET = -1;
        private static final int ICON = 0;
        private static final int TEXT = 1;
        private static final int IMAGE = 2;
        private static final int CUSTOM_CONTENT = 3;

        static final @NonNull Map<Integer, String> TYPE_TO_TAG = new HashMap<>();

        @RestrictTo(RestrictTo.Scope.LIBRARY)
        @Retention(RetentionPolicy.SOURCE)
        @IntDef({NOT_SET, ICON, TEXT, IMAGE, CUSTOM_CONTENT})
        @interface ButtonType {}

        private final @NonNull Context mContext;
        private androidx.wear.tiles.LayoutElementBuilders.@Nullable LayoutElement mCustomContent;
        private final androidx.wear.tiles.ModifiersBuilders.@NonNull Clickable mClickable;
        private @NonNull CharSequence mContentDescription = "";

        private androidx.wear.tiles.DimensionBuilders.@NonNull DpProp mSize =
                ButtonDefaults.DEFAULT_SIZE;

        private @Nullable String mText = null;
        private @Nullable Integer mTypographyName = null;
        private @Nullable String mIcon = null;
        private androidx.wear.tiles.DimensionBuilders.@Nullable DpProp mIconSize = null;
        private @Nullable String mImage = null;
        private @NonNull ButtonColors mButtonColors = ButtonDefaults.PRIMARY_COLORS;
        @ButtonType private int mType = NOT_SET;

        static {
            TYPE_TO_TAG.put(ICON, METADATA_TAG_ICON);
            TYPE_TO_TAG.put(TEXT, METADATA_TAG_TEXT);
            TYPE_TO_TAG.put(IMAGE, METADATA_TAG_IMAGE);
            TYPE_TO_TAG.put(CUSTOM_CONTENT, METADATA_TAG_CUSTOM_CONTENT);
        }

        /**
         * Creates a builder for the {@link Button} from the given content. Custom content should be
         * later set with one of the following ({@link #setIconContent}, {@link #setTextContent},
         * {@link #setImageContent}.
         *
         * @param context The application's context.
         * @param clickable Associated {@link androidx.wear.tiles.ModifiersBuilders.Clickable} for
         *     click events. When the Button is clicked it will fire the associated action.
         */
        public Builder(
                @NonNull Context context,
                androidx.wear.tiles.ModifiersBuilders.@NonNull Clickable clickable) {
            mClickable = clickable;
            mContext = context;
        }

        /**
         * Sets the content description for the {@link Button}. It is highly recommended to provide
         * this for button containing icon or image.
         */
        public @NonNull Builder setContentDescription(@NonNull CharSequence contentDescription) {
            this.mContentDescription = contentDescription;
            return this;
        }

        /**
         * Sets the size for the {@link Button}. Strongly recommended values are {@link
         * ButtonDefaults#DEFAULT_SIZE}, {@link ButtonDefaults#LARGE_SIZE} and {@link
         * ButtonDefaults#EXTRA_LARGE_SIZE}. If not set, {@link ButtonDefaults#DEFAULT_SIZE} will be
         * used.
         */
        public @NonNull Builder setSize(
                androidx.wear.tiles.DimensionBuilders.@NonNull DpProp size) {
            mSize = size;
            return this;
        }

        /**
         * Sets the size for the {@link Button}. Strongly recommended values are {@link
         * ButtonDefaults#DEFAULT_SIZE}, {@link ButtonDefaults#LARGE_SIZE} and {@link
         * ButtonDefaults#EXTRA_LARGE_SIZE}. If not set, {@link ButtonDefaults#DEFAULT_SIZE} will be
         * used.
         */
        public @NonNull Builder setSize(@Dimension(unit = DP) float size) {
            mSize = androidx.wear.tiles.DimensionBuilders.dp(size);
            return this;
        }

        /**
         * Sets the colors for the {@link Button}. If not set, {@link ButtonDefaults#PRIMARY_COLORS}
         * will be used.
         */
        public @NonNull Builder setButtonColors(@NonNull ButtonColors buttonColors) {
            mButtonColors = buttonColors;
            return this;
        }

        /**
         * Sets the custom content for this Button. Any previously added content will be overridden.
         */
        public @NonNull Builder setCustomContent(
                androidx.wear.tiles.LayoutElementBuilders.@NonNull LayoutElement content) {
            resetContent();
            this.mCustomContent = content;
            this.mType = CUSTOM_CONTENT;
            return this;
        }

        /**
         * Sets the content of this Button to be the given icon with the given size. Any previously
         * added content will be overridden. Provided icon will be tinted to the given content color
         * from {@link ButtonColors} and with the given size. This icon should be image with chosen
         * alpha channel and not an actual image.
         */
        public @NonNull Builder setIconContent(
                @NonNull String imageResourceId,
                androidx.wear.tiles.DimensionBuilders.@NonNull DpProp size) {
            resetContent();
            this.mIcon = imageResourceId;
            this.mType = ICON;
            this.mIconSize = size;
            return this;
        }

        /**
         * Sets the content of this Button to be the given icon with the default size that is half
         * of the set size of the button. Any previously added content will be overridden. Provided
         * icon will be tinted to the given content color from {@link ButtonColors}. This icon
         * should be image with chosen alpha channel and not an actual image.
         */
        public @NonNull Builder setIconContent(@NonNull String imageResourceId) {
            resetContent();
            this.mIcon = imageResourceId;
            this.mType = ICON;
            return this;
        }

        /**
         * Sets the content of this Button to be the given text with the default font for the set
         * size (for the {@link ButtonDefaults#DEFAULT_SIZE}, {@link ButtonDefaults#LARGE_SIZE} and
         * {@link ButtonDefaults#EXTRA_LARGE_SIZE} is {@link Typography#TYPOGRAPHY_TITLE2}, {@link
         * Typography#TYPOGRAPHY_TITLE1} and {@link Typography#TYPOGRAPHY_DISPLAY3} respectively).
         * Any previously added content will be overridden. Text should contain no more than 3
         * characters, otherwise it will overflow from the edges.
         */
        public @NonNull Builder setTextContent(@NonNull String text) {
            resetContent();
            this.mText = text;
            this.mType = TEXT;
            return this;
        }

        /**
         * Sets the content of this Button to be the given text with the given font. If you need
         * more font related customization, consider using {@link #setCustomContent} with {@link
         * Text} component. Any previously added content will be overridden. Text should contain no
         * more than 3 characters, otherwise it will overflow from the edges.
         */
        public @NonNull Builder setTextContent(
                @NonNull String text, @Typography.TypographyName int typographyName) {
            resetContent();
            this.mText = text;
            this.mTypographyName = typographyName;
            this.mType = TEXT;
            return this;
        }

        /**
         * Sets the content of this Button to be the given image, i.e. contacts photo. Any
         * previously added content will be overridden.
         */
        public @NonNull Builder setImageContent(@NonNull String imageResourceId) {
            resetContent();
            this.mImage = imageResourceId;
            this.mType = IMAGE;
            return this;
        }

        private void resetContent() {
            this.mText = null;
            this.mTypographyName = null;
            this.mIcon = null;
            this.mImage = null;
            this.mCustomContent = null;
            this.mIconSize = null;
        }

        /** Constructs and returns {@link Button} with the provided field and look. */
        @Override
        public @NonNull Button build() {
            androidx.wear.tiles.ModifiersBuilders.Modifiers.Builder modifiers =
                    new androidx.wear.tiles.ModifiersBuilders.Modifiers.Builder()
                            .setClickable(mClickable)
                            .setBackground(
                                    new androidx.wear.tiles.ModifiersBuilders.Background.Builder()
                                            .setColor(mButtonColors.getBackgroundColor())
                                            .setCorner(
                                                    new androidx.wear.tiles.ModifiersBuilders.Corner
                                                                    .Builder()
                                                            .setRadius(Helper.radiusOf(mSize))
                                                            .build())
                                            .build())
                            .setMetadata(
                                    new androidx.wear.tiles.ModifiersBuilders.ElementMetadata
                                                    .Builder()
                                            .setTagData(
                                                    Helper.getTagBytes(
                                                            Helper.checkNotNull(
                                                                    TYPE_TO_TAG.get(mType))))
                                            .build());
            if (mContentDescription.length() > 0) {
                modifiers.setSemantics(
                        new androidx.wear.tiles.ModifiersBuilders.Semantics.Builder()
                                .setContentDescription(mContentDescription.toString())
                                .build());
            }

            androidx.wear.tiles.LayoutElementBuilders.Box.Builder element =
                    new androidx.wear.tiles.LayoutElementBuilders.Box.Builder()
                            .setHeight(mSize)
                            .setWidth(mSize)
                            .setModifiers(modifiers.build());

            element.addContent(getCorrectContent());

            return new Button(element.build());
        }

        private androidx.wear.tiles.LayoutElementBuilders.@NonNull LayoutElement
                getCorrectContent() {
            androidx.wear.tiles.LayoutElementBuilders.LayoutElement.Builder content;
            switch (mType) {
                case ICON:
                    {
                        androidx.wear.tiles.DimensionBuilders.DpProp iconSize =
                                mIconSize != null
                                        ? mIconSize
                                        : ButtonDefaults.recommendedIconSize(mSize);
                        content =
                                new androidx.wear.tiles.LayoutElementBuilders.Image.Builder()
                                        .setResourceId(Helper.checkNotNull(mIcon))
                                        .setHeight(Helper.checkNotNull(iconSize))
                                        .setWidth(iconSize)
                                        .setContentScaleMode(
                                                androidx.wear.tiles.LayoutElementBuilders
                                                        .CONTENT_SCALE_MODE_FILL_BOUNDS)
                                        .setColorFilter(
                                                new androidx.wear.tiles.LayoutElementBuilders
                                                                .ColorFilter.Builder()
                                                        .setTint(mButtonColors.getContentColor())
                                                        .build());

                        return content.build();
                    }
                case TEXT:
                    {
                        @Typography.TypographyName
                        int typographyName =
                                mTypographyName != null
                                        ? mTypographyName
                                        : getDefaultTypographyForSize(mSize);
                        content =
                                new Text.Builder(mContext, Helper.checkNotNull(mText))
                                        .setMaxLines(1)
                                        .setTypography(typographyName)
                                        .setColor(mButtonColors.getContentColor());

                        return content.build();
                    }
                case IMAGE:
                    {
                        content =
                                new androidx.wear.tiles.LayoutElementBuilders.Image.Builder()
                                        .setResourceId(Helper.checkNotNull(mImage))
                                        .setHeight(mSize)
                                        .setWidth(mSize)
                                        .setContentScaleMode(
                                                androidx.wear.tiles.LayoutElementBuilders
                                                        .CONTENT_SCALE_MODE_FILL_BOUNDS);
                        return content.build();
                    }
                case CUSTOM_CONTENT:
                    return Helper.checkNotNull(mCustomContent);
                case NOT_SET:
                    // Shouldn't happen.
                default:
                    // Shouldn't happen.
                    throw new IllegalArgumentException("Wrong Button type");
            }
        }

        private static @Typography.TypographyName int getDefaultTypographyForSize(
                androidx.wear.tiles.DimensionBuilders.@NonNull DpProp size) {
            if (size.getValue() == ButtonDefaults.LARGE_SIZE.getValue()) {
                return Typography.TYPOGRAPHY_TITLE1;
            } else if (size.getValue() == ButtonDefaults.EXTRA_LARGE_SIZE.getValue()) {
                return Typography.TYPOGRAPHY_DISPLAY3;
            } else {
                return Typography.TYPOGRAPHY_TITLE2;
            }
        }
    }

    /**
     * Returns the custom content of this Button if it has been added. Otherwise, it returns null.
     */
    public androidx.wear.tiles.LayoutElementBuilders.@Nullable LayoutElement getCustomContent() {
        if (!getMetadataTag().equals(METADATA_TAG_CUSTOM_CONTENT)) {
            return null;
        }
        return getAnyContent();
    }

    /** Returns the icon content of this Button if it has been added. Otherwise, it returns null. */
    public @Nullable String getIconContent() {
        androidx.wear.tiles.LayoutElementBuilders.Image icon = getIconContentObject();
        return icon != null ? Helper.checkNotNull(icon.getResourceId()).getValue() : null;
    }

    /**
     * Returns the image content of this Button if it has been added. Otherwise, it returns null.
     */
    public @Nullable String getImageContent() {
        androidx.wear.tiles.LayoutElementBuilders.Image image = getImageContentObject();
        return image != null ? Helper.checkNotNull(image.getResourceId()).getValue() : null;
    }

    /** Returns the text content of this Button if it has been added. Otherwise, it returns null. */
    public @Nullable String getTextContent() {
        Text text = getTextContentObject();
        return text != null ? text.getText() : null;
    }

    private androidx.wear.tiles.LayoutElementBuilders.@NonNull LayoutElement getAnyContent() {
        return Helper.checkNotNull(mElement.getContents().get(0));
    }

    private androidx.wear.tiles.LayoutElementBuilders.@Nullable Image getIconContentObject() {
        if (!getMetadataTag().equals(METADATA_TAG_ICON)) {
            return null;
        }
        return (androidx.wear.tiles.LayoutElementBuilders.Image) getAnyContent();
    }

    private @Nullable Text getTextContentObject() {
        if (!getMetadataTag().equals(METADATA_TAG_TEXT)) {
            return null;
        }
        return Text.fromLayoutElement(getAnyContent());
    }

    private androidx.wear.tiles.LayoutElementBuilders.@Nullable Image getImageContentObject() {
        if (!getMetadataTag().equals(METADATA_TAG_IMAGE)) {
            return null;
        }
        return (androidx.wear.tiles.LayoutElementBuilders.Image) getAnyContent();
    }

    /** Returns click event action associated with this Button. */
    public androidx.wear.tiles.ModifiersBuilders.@NonNull Clickable getClickable() {
        return Helper.checkNotNull(Helper.checkNotNull(mElement.getModifiers()).getClickable());
    }

    /** Returns content description for this Button. */
    public @Nullable CharSequence getContentDescription() {
        androidx.wear.tiles.ModifiersBuilders.Semantics semantics =
                Helper.checkNotNull(mElement.getModifiers()).getSemantics();
        if (semantics == null) {
            return null;
        }
        return semantics.getContentDescription();
    }

    /** Returns size for this Button. */
    public androidx.wear.tiles.DimensionBuilders.@NonNull ContainerDimension getSize() {
        return Helper.checkNotNull(mElement.getWidth());
    }

    private androidx.wear.tiles.ColorBuilders.ColorProp getBackgroundColor() {
        return Helper.checkNotNull(
                Helper.checkNotNull(Helper.checkNotNull(mElement.getModifiers()).getBackground())
                        .getColor());
    }

    /** Returns button color of this Button. */
    public @NonNull ButtonColors getButtonColors() {
        androidx.wear.tiles.ColorBuilders.ColorProp backgroundColor = getBackgroundColor();
        androidx.wear.tiles.ColorBuilders.ColorProp contentColor = null;

        switch (getMetadataTag()) {
            case METADATA_TAG_TEXT:
                contentColor = Helper.checkNotNull(getTextContentObject()).getColor();
                break;
            case METADATA_TAG_ICON:
                contentColor =
                        Helper.checkNotNull(
                                        Helper.checkNotNull(getIconContentObject())
                                                .getColorFilter())
                                .getTint();
                break;
            case METADATA_TAG_IMAGE:
                contentColor =
                        Helper.checkNotNull(
                                        Helper.checkNotNull(getImageContentObject())
                                                .getColorFilter())
                                .getTint();
                break;
            case METADATA_TAG_CUSTOM_CONTENT:
                break;
        }

        if (contentColor == null) {
            contentColor = new androidx.wear.tiles.ColorBuilders.ColorProp.Builder().build();
        }

        return new ButtonColors(backgroundColor, contentColor);
    }

    /** Returns metadata tag set to this Button. */
    @NonNull String getMetadataTag() {
        return Helper.getMetadataTagName(
                Helper.checkNotNull(Helper.checkNotNull(mElement.getModifiers()).getMetadata()));
    }

    /**
     * Returns Button object from the given androidx.wear.tiles.LayoutElementBuilders.LayoutElement
     * (e.g. one retrieved from a container's content with {@code
     * container.getContents().get(index)}) if that element can be converted to Button. Otherwise,
     * it will return null.
     */
    public static @Nullable Button fromLayoutElement(
            androidx.wear.tiles.LayoutElementBuilders.@NonNull LayoutElement element) {
        if (element instanceof Button) {
            return (Button) element;
        }
        if (!(element instanceof androidx.wear.tiles.LayoutElementBuilders.Box)) {
            return null;
        }
        androidx.wear.tiles.LayoutElementBuilders.Box boxElement =
                (androidx.wear.tiles.LayoutElementBuilders.Box) element;
        if (!Helper.checkTag(boxElement.getModifiers(), Builder.TYPE_TO_TAG.values())) {
            return null;
        }
        // Now we are sure that this element is a Button.
        return new Button(boxElement);
    }

    @Override
    @RestrictTo(Scope.LIBRARY_GROUP)
    public LayoutElementProto.@NonNull LayoutElement toLayoutElementProto() {
        return Helper.checkNotNull(mElement.toLayoutElementProto());
    }

    @RestrictTo(Scope.LIBRARY_GROUP)
    @Override
    public @Nullable Fingerprint getFingerprint() {
        return mElement.getFingerprint();
    }
}
