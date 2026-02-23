/*
 * Copyright 2019 The Android Open Source Project
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

package androidx.browser.customtabs;

import static androidx.browser.customtabs.CustomTabsIntent.EXTRA_NAVIGATION_BAR_COLOR;
import static androidx.browser.customtabs.CustomTabsIntent.EXTRA_NAVIGATION_BAR_DIVIDER_COLOR;
import static androidx.browser.customtabs.CustomTabsIntent.EXTRA_SECONDARY_TOOLBAR_COLOR;
import static androidx.browser.customtabs.CustomTabsIntent.EXTRA_TOOLBAR_COLOR;

import android.os.Bundle;

import androidx.annotation.ColorInt;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Contains visual parameters of a Custom Tab that may depend on the color scheme.
 * @see CustomTabsIntent.Builder#setColorSchemeParams(int, CustomTabColorSchemeParams)
 */
public final class CustomTabColorSchemeParams {

    /**
     * Toolbar color. See {@link CustomTabsIntent.Builder#setToolbarColor(int)}.
     */
    @ColorInt public final @Nullable Integer toolbarColor;

    /**
     * Secondary toolbar color. See {@link CustomTabsIntent.Builder#setSecondaryToolbarColor(int)}.
     */
    @ColorInt public final @Nullable Integer secondaryToolbarColor;

    /**
     * Navigation bar color. See {@link CustomTabsIntent.Builder#setNavigationBarColor(int)}.
     */
    @ColorInt public final @Nullable Integer navigationBarColor;

    /**
     * Navigation bar color. See {@link CustomTabsIntent.Builder#setNavigationBarDividerColor(int)}.
     */
    @ColorInt public final @Nullable Integer navigationBarDividerColor;

    @SuppressWarnings("WeakerAccess") /* synthetic access */
    CustomTabColorSchemeParams(
            @ColorInt @Nullable Integer toolbarColor,
            @ColorInt @Nullable Integer secondaryToolbarColor,
            @ColorInt @Nullable Integer navigationBarColor,
            @ColorInt @Nullable Integer navigationBarDividerColor) {
        this.toolbarColor = toolbarColor;
        this.secondaryToolbarColor = secondaryToolbarColor;
        this.navigationBarColor = navigationBarColor;
        this.navigationBarDividerColor = navigationBarDividerColor;
    }

    /**
     * Packs the parameters into a {@link Bundle}.
     * For backward compatibility and ease of use, the names of keys and the structure of the Bundle
     * are the same as that of Intent extras prior to introducing the themes.
     */
    @NonNull Bundle toBundle() {
        Bundle bundle = new Bundle();
        if (toolbarColor != null) {
            bundle.putInt(EXTRA_TOOLBAR_COLOR, toolbarColor);
        }
        if (secondaryToolbarColor != null) {
            bundle.putInt(EXTRA_SECONDARY_TOOLBAR_COLOR, secondaryToolbarColor);
        }
        if (navigationBarColor != null) {
            bundle.putInt(EXTRA_NAVIGATION_BAR_COLOR, navigationBarColor);
        }
        if (navigationBarDividerColor != null) {
            bundle.putInt(EXTRA_NAVIGATION_BAR_DIVIDER_COLOR, navigationBarDividerColor);
        }
        return bundle;
    }

    /**
     * Unpacks parameters from a {@link Bundle}. Sets all parameters to null if provided bundle is
     * null.
     */
    @SuppressWarnings("deprecation")
    static @NonNull CustomTabColorSchemeParams fromBundle(@Nullable Bundle bundle) {
        if (bundle == null) {
            bundle = new Bundle(0);
        }
        // Using bundle.get() instead of bundle.getInt() to default to null without calling
        // bundle.containsKey().
        return new CustomTabColorSchemeParams(
                (Integer) bundle.get(EXTRA_TOOLBAR_COLOR),
                (Integer) bundle.get(EXTRA_SECONDARY_TOOLBAR_COLOR),
                (Integer) bundle.get(EXTRA_NAVIGATION_BAR_COLOR),
                (Integer) bundle.get(EXTRA_NAVIGATION_BAR_DIVIDER_COLOR));
    }

    /**
     * Replaces the null fields with values from provided defaults.
     */
    @NonNull CustomTabColorSchemeParams withDefaults(@NonNull CustomTabColorSchemeParams defaults) {
        return new CustomTabColorSchemeParams(
                toolbarColor == null ? defaults.toolbarColor : toolbarColor,
                secondaryToolbarColor == null ? defaults.secondaryToolbarColor
                        : secondaryToolbarColor,
                navigationBarColor == null ? defaults.navigationBarColor : navigationBarColor,
                navigationBarDividerColor == null ? defaults.navigationBarDividerColor
                        : navigationBarDividerColor);
    }

    /**
     * Builder class for {@link CustomTabColorSchemeParams} objects.
     *
     * The browser's default colors will be used for any unset value.
     */
    public static final class Builder {
        @ColorInt private @Nullable Integer mToolbarColor;
        @ColorInt private @Nullable Integer mSecondaryToolbarColor;
        @ColorInt private @Nullable Integer mNavigationBarColor;
        @ColorInt private @Nullable Integer mNavigationBarDividerColor;

        /**
         * @see CustomTabsIntent.Builder#setToolbarColor(int)
         */
        public @NonNull Builder setToolbarColor(@ColorInt int color) {
            mToolbarColor = color | 0xff000000;
            return this;
        }

        /**
         * @see CustomTabsIntent.Builder#setSecondaryToolbarColor(int)
         */
        public @NonNull Builder setSecondaryToolbarColor(@ColorInt int color) {
            mSecondaryToolbarColor = color;
            return this;
        }

        /**
         * @see CustomTabsIntent.Builder#setNavigationBarColor(int)
         */
        public @NonNull Builder setNavigationBarColor(@ColorInt int color) {
            mNavigationBarColor = color | 0xff000000;
            return this;
        }

        /**
         * @see CustomTabsIntent.Builder#setNavigationBarDividerColor(int)
         */
        public @NonNull Builder setNavigationBarDividerColor(@ColorInt int color) {
            mNavigationBarDividerColor = color;
            return this;
        }

        /**
         * Combines all the options that have been into a {@link CustomTabColorSchemeParams}
         * object.
         */
        public @NonNull CustomTabColorSchemeParams build() {
            return new CustomTabColorSchemeParams(mToolbarColor, mSecondaryToolbarColor,
                    mNavigationBarColor, mNavigationBarDividerColor);
        }
    }
}
