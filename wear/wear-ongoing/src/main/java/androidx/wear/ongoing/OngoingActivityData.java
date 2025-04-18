/*
 * Copyright 2020 The Android Open Source Project
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
package androidx.wear.ongoing;

import android.app.PendingIntent;
import android.graphics.drawable.Icon;

import androidx.annotation.RestrictTo;
import androidx.core.content.LocusIdCompat;
import androidx.versionedparcelable.ParcelField;
import androidx.versionedparcelable.VersionedParcelable;
import androidx.versionedparcelable.VersionedParcelize;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * This class is used internally by the library to represent the data of an OngoingActivity and
 * serialize/deserialize using VersionedParcelable.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY)
@VersionedParcelize
class OngoingActivityData implements VersionedParcelable {
    @ParcelField(value = 1, defaultValue = "null")
    @Nullable Icon mAnimatedIcon;

    @ParcelField(value = 2)
    @NonNull Icon mStaticIcon;

    @ParcelField(value = 3, defaultValue = "null")
    @Nullable OngoingActivityStatus mStatus;

    @ParcelField(value = 4)
    @NonNull PendingIntent mTouchIntent;

    @ParcelField(value = 5, defaultValue = "null")
    @Nullable String mLocusId;

    @ParcelField(value = 6, defaultValue = "-1")
    int mOngoingActivityId;

    @ParcelField(value = 7, defaultValue = "null")
    @Nullable String mCategory;

    @ParcelField(value = 8)
    long mTimestamp;

    @ParcelField(value = 9, defaultValue = "null")
    @Nullable String mTitle;

    @ParcelField(value = 10, defaultValue = "null")
    @Nullable String mContentDescription;

    // Required by VersionedParcelable
    OngoingActivityData() {
    }

    OngoingActivityData(
            @Nullable Icon animatedIcon,
            @NonNull Icon staticIcon,
            @Nullable OngoingActivityStatus status,
            @NonNull PendingIntent touchIntent,
            @Nullable String locusId,
            int ongoingActivityId,
            @Nullable String category,
            long timestamp,
            @Nullable String title,
            @Nullable String contentDescription
    ) {
        mAnimatedIcon = animatedIcon;
        mStaticIcon = staticIcon;
        mStatus = status;
        mTouchIntent = touchIntent;
        mLocusId = locusId;
        mOngoingActivityId = ongoingActivityId;
        mCategory = category;
        mTimestamp = timestamp;
        mTitle = title;
        mContentDescription = contentDescription;
    }

    @Nullable Icon getAnimatedIcon() {
        return mAnimatedIcon;
    }

    @NonNull Icon getStaticIcon() {
        return mStaticIcon;
    }

    @Nullable OngoingActivityStatus getStatus() {
        return mStatus;
    }

    @NonNull PendingIntent getTouchIntent() {
        return mTouchIntent;
    }

    @Nullable LocusIdCompat getLocusId() {
        return mLocusId == null ? null : new LocusIdCompat(mLocusId);
    }

    int getOngoingActivityId() {
        return mOngoingActivityId;
    }

    @Nullable String getCategory() {
        return mCategory;
    }

    long getTimestamp() {
        return mTimestamp;
    }

    @Nullable String getTitle() {
        return mTitle;
    }

    @Nullable String getContentDescription() {
        return mContentDescription;
    }

    // Status is mutable, by the library.
    void setStatus(@NonNull OngoingActivityStatus status) {
        mStatus = status;
    }
}

