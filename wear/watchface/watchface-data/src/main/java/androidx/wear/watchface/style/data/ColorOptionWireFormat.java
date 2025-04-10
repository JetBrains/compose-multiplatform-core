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

package androidx.wear.watchface.style.data;

import android.os.Bundle;

import androidx.annotation.RestrictTo;
import androidx.versionedparcelable.ParcelField;
import androidx.versionedparcelable.VersionedParcelize;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Wire format for {@link androidx.wear.watchface.style.ColorUserStyleSetting.ColorOption}.
 *
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
@VersionedParcelize
public class ColorOptionWireFormat extends OptionWireFormat {
    /** Localized human readable name for the setting, used in the style selection UI. */
    @ParcelField(2)
    public @NonNull CharSequence mDisplayName = "";

    @ParcelField(3)
    public @NonNull CharSequence mScreenReaderName = "";

    @ParcelField(4)
    public @NonNull List<Integer> mColors;

    @ParcelField(5)
    public @Nullable Bundle mOnWatchFaceEditorBundle;

    // WARNING: This class is held in a list and can't change due to flaws in VersionedParcelable.

    ColorOptionWireFormat() {}

    public ColorOptionWireFormat(
            byte @NonNull [] id, @NonNull CharSequence displayName,
            @NonNull CharSequence screenReaderName, @NonNull List<Integer> colors,
            @Nullable Bundle onWatchFaceEditorBundle) {
        super(id);
        this.mDisplayName = displayName;
        this.mScreenReaderName = screenReaderName;
        this.mColors = colors;
        this.mOnWatchFaceEditorBundle = onWatchFaceEditorBundle;
    }
}
