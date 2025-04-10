/*
 * Copyright 2022 The Android Open Source Project
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

package androidx.camera.camera2.internal.compat.params;

import android.hardware.camera2.params.DynamicRangeProfiles;

import androidx.annotation.RequiresApi;
import androidx.camera.core.DynamicRange;
import androidx.core.util.Preconditions;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

@RequiresApi(33)
class DynamicRangesCompatApi33Impl implements DynamicRangesCompat.DynamicRangeProfilesCompatImpl {
    private final DynamicRangeProfiles mDynamicRangeProfiles;

    DynamicRangesCompatApi33Impl(@NonNull Object dynamicRangeProfiles) {
        mDynamicRangeProfiles = (DynamicRangeProfiles) dynamicRangeProfiles;
    }

    @Override
    public @NonNull Set<DynamicRange> getDynamicRangeCaptureRequestConstraints(
            @NonNull DynamicRange dynamicRange) {
        Long dynamicRangeProfile = dynamicRangeToFirstSupportedProfile(dynamicRange);
        Preconditions.checkArgument(dynamicRangeProfile != null,
                "DynamicRange is not supported: " + dynamicRange);
        return profileSetToDynamicRangeSet(
                mDynamicRangeProfiles.getProfileCaptureRequestConstraints(dynamicRangeProfile));
    }

    @Override
    public @NonNull Set<DynamicRange> getSupportedDynamicRanges() {
        return profileSetToDynamicRangeSet(mDynamicRangeProfiles.getSupportedProfiles());
    }

    @Override
    public boolean isExtraLatencyPresent(@NonNull DynamicRange dynamicRange) {
        Long dynamicRangeProfile = dynamicRangeToFirstSupportedProfile(dynamicRange);
        Preconditions.checkArgument(dynamicRangeProfile != null,
                "DynamicRange is not supported: " + dynamicRange);
        return mDynamicRangeProfiles.isExtraLatencyPresent(dynamicRangeProfile);
    }

    @Override
    public @Nullable DynamicRangeProfiles unwrap() {
        return mDynamicRangeProfiles;
    }

    private static @NonNull DynamicRange profileToDynamicRange(long profile) {
        return Preconditions.checkNotNull(DynamicRangeConversions.profileToDynamicRange(profile),
                "Dynamic range profile cannot be converted to a DynamicRange object: " + profile);
    }

    private @Nullable Long dynamicRangeToFirstSupportedProfile(@NonNull DynamicRange dynamicRange) {
        return DynamicRangeConversions.dynamicRangeToFirstSupportedProfile(dynamicRange,
                mDynamicRangeProfiles);
    }

    private static @NonNull Set<DynamicRange> profileSetToDynamicRangeSet(
            @NonNull Set<Long> profileSet) {
        if (profileSet.isEmpty()) {
            return Collections.emptySet();
        }
        Set<DynamicRange> dynamicRangeSet = new HashSet<>(profileSet.size());
        for (long profile : profileSet) {
            dynamicRangeSet.add(profileToDynamicRange(profile));
        }
        return Collections.unmodifiableSet(dynamicRangeSet);
    }
}
