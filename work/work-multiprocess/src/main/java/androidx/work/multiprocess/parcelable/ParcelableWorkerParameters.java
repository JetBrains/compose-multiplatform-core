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

package androidx.work.multiprocess.parcelable;

import android.annotation.SuppressLint;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.RestrictTo;
import androidx.work.Configuration;
import androidx.work.Data;
import androidx.work.ForegroundUpdater;
import androidx.work.ProgressUpdater;
import androidx.work.WorkerParameters;
import androidx.work.impl.Processor;
import androidx.work.impl.WorkDatabase;
import androidx.work.impl.WorkManagerImpl;
import androidx.work.impl.utils.WorkForegroundUpdater;
import androidx.work.impl.utils.WorkProgressUpdater;
import androidx.work.impl.utils.taskexecutor.TaskExecutor;

import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * {@link androidx.work.WorkerParameters}, but parcelable.
 *
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
@SuppressLint("BanParcelableUsage")
public class ParcelableWorkerParameters implements Parcelable {
    private final @NonNull UUID mId;
    private final @NonNull Data mData;
    private final @NonNull Set<String> mTags;
    private final WorkerParameters.@NonNull RuntimeExtras mRuntimeExtras;
    private final int mRunAttemptCount;
    private final int mGeneration;

    public ParcelableWorkerParameters(@NonNull WorkerParameters parameters) {
        mId = parameters.getId();
        mData = parameters.getInputData();
        mTags = parameters.getTags();
        mRuntimeExtras = parameters.getRuntimeExtras();
        mRunAttemptCount = parameters.getRunAttemptCount();
        mGeneration = parameters.getGeneration();
    }

    public static final Creator<ParcelableWorkerParameters> CREATOR =
            new Creator<ParcelableWorkerParameters>() {
                @Override
                public @NonNull ParcelableWorkerParameters createFromParcel(Parcel in) {
                    return new ParcelableWorkerParameters(in);
                }

                @Override
                public ParcelableWorkerParameters[] newArray(int size) {
                    return new ParcelableWorkerParameters[size];
                }
            };

    public ParcelableWorkerParameters(@NonNull Parcel in) {
        // id
        String id = in.readString();
        mId = UUID.fromString(id);
        // inputData
        ParcelableData parcelableInputData = new ParcelableData(in);
        mData = parcelableInputData.getData();
        // tags
        mTags = new HashSet<>(in.createStringArrayList());
        // runtimeExtras
        ParcelableRuntimeExtras parcelableRuntimeExtras = new ParcelableRuntimeExtras(in);
        mRuntimeExtras = parcelableRuntimeExtras.getRuntimeExtras();
        // runAttemptCount
        mRunAttemptCount = in.readInt();
        // generation
        mGeneration = in.readInt();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel parcel, int flags) {
        // id
        parcel.writeString(mId.toString());
        // inputData
        ParcelableData parcelableInputData = new ParcelableData(mData);
        parcelableInputData.writeToParcel(parcel, flags);
        // tags
        List<String> tags = new ArrayList<>(mTags);
        parcel.writeStringList(tags);
        // runtimeExtras
        ParcelableRuntimeExtras parcelableRuntimeExtras =
                new ParcelableRuntimeExtras(mRuntimeExtras);
        parcelableRuntimeExtras.writeToParcel(parcel, flags);
        // runAttemptCount
        parcel.writeInt(mRunAttemptCount);
        // generation
        parcel.writeInt(mGeneration);
    }

    public @NonNull UUID getId() {
        return mId;
    }

    public @NonNull Data getData() {
        return mData;
    }

    public int getRunAttemptCount() {
        return mRunAttemptCount;
    }

    public @NonNull Set<String> getTags() {
        return mTags;
    }

    /**
     * Converts {@link ParcelableWorkerParameters} to an instance of {@link WorkerParameters}
     * lazily.
     */
    public @NonNull WorkerParameters toWorkerParameters(@NonNull WorkManagerImpl workManager) {
        Configuration configuration = workManager.getConfiguration();
        WorkDatabase workDatabase = workManager.getWorkDatabase();
        TaskExecutor taskExecutor = workManager.getWorkTaskExecutor();
        Processor processor = workManager.getProcessor();
        WorkProgressUpdater progressUpdater = new WorkProgressUpdater(workDatabase, taskExecutor);
        WorkForegroundUpdater foregroundUpdater = new WorkForegroundUpdater(
                workDatabase,
                processor,
                taskExecutor
        );
        return toWorkerParameters(
                configuration,
                taskExecutor,
                progressUpdater,
                foregroundUpdater
        );
    }

    /**
     * Converts {@link ParcelableWorkerParameters} to an instance of {@link WorkerParameters}
     * lazily.
     */
    public @NonNull WorkerParameters toWorkerParameters(
            @NonNull Configuration configuration,
            @NonNull TaskExecutor taskExecutor,
            @NonNull ProgressUpdater progressUpdater,
            @NonNull ForegroundUpdater foregroundUpdater) {
        return new WorkerParameters(
                mId,
                mData,
                mTags,
                mRuntimeExtras,
                mRunAttemptCount,
                mGeneration,
                configuration.getExecutor(),
                configuration.getWorkerCoroutineContext(),
                taskExecutor,
                configuration.getWorkerFactory(),
                progressUpdater,
                foregroundUpdater
        );
    }
}
