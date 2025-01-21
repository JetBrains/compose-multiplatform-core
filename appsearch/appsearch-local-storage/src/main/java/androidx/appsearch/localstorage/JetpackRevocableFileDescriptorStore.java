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

// @exportToFramework:copyToPath(../../../cts/tests/appsearch/testutils/src/android/app/appsearch/testutil/external/JetpackRevocableFileDescriptorStore.java)
package androidx.appsearch.localstorage;

import android.os.ParcelFileDescriptor;

import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;
import androidx.appsearch.app.AppSearchResult;
import androidx.appsearch.exceptions.AppSearchException;
import androidx.collection.ArrayMap;
import androidx.core.util.Preconditions;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The local storage implementation of {@link RevocableFileDescriptorStore}.
 *
 * <p> The {@link ParcelFileDescriptor} sent to the client side from the local storage won't cross
 * the binder, we could revoke the {@link ParcelFileDescriptor} in the client side by directly close
 * the one in AppSearch side. This won't work in the framework, since even if we close the
 * {@link ParcelFileDescriptor} in the server side, the one in the client side could still work.
 *
 * <p>This class is thread safety.
 *
 * @exportToFramework:hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class JetpackRevocableFileDescriptorStore implements
        RevocableFileDescriptorStore {

    private final Object mLock  = new Object();
    private final AppSearchConfig mConfig;

    public JetpackRevocableFileDescriptorStore(@NonNull AppSearchConfig config) {
        mConfig = Preconditions.checkNotNull(config);
    }

    @GuardedBy("mLock")
    // <package, List<sent rfds> map to tracking all sent rfds.
    private final Map<String, List<JetpackRevocableFileDescriptor>>
            mSentAppSearchParcelFileDescriptorsLocked = new ArrayMap<>();

    @Override
    @NonNull
    public ParcelFileDescriptor wrapToRevocableFileDescriptor(@NonNull String packageName,
            @NonNull ParcelFileDescriptor parcelFileDescriptor) {
        JetpackRevocableFileDescriptor revocableFileDescriptor =
                new JetpackRevocableFileDescriptor(parcelFileDescriptor);
        setCloseListenerToFd(revocableFileDescriptor, packageName);
        addToSentAppSearchParcelFileDescriptorMap(revocableFileDescriptor, packageName);
        return revocableFileDescriptor;
    }

    @Override
    public void revokeAll() throws IOException {
        synchronized (mLock) {
            for (String packageName : mSentAppSearchParcelFileDescriptorsLocked.keySet()) {
                revokeForPackage(packageName);
            }
        }
    }

    @Override
    public void revokeForPackage(@NonNull String packageName) throws IOException {
        synchronized (mLock) {
            List<JetpackRevocableFileDescriptor> rfds =
                    mSentAppSearchParcelFileDescriptorsLocked.remove(packageName);
            if (rfds != null) {
                for (int i = rfds.size() - 1; i >= 0; i--) {
                    rfds.get(i).closeSuperDirectly();
                }
            }
        }
    }

    @Override
    public void checkBlobStoreLimit(@NonNull String packageName) throws AppSearchException {
        synchronized (mLock) {
            List<JetpackRevocableFileDescriptor> rfdsForPackage =
                    mSentAppSearchParcelFileDescriptorsLocked.get(packageName);
            if (rfdsForPackage == null) {
                return;
            }
            if (rfdsForPackage.size() >= mConfig.getMaxOpenBlobCount()) {
                throw new AppSearchException(AppSearchResult.RESULT_OUT_OF_SPACE,
                        "Package \"" + packageName + "\" exceeded limit of "
                                + mConfig.getMaxOpenBlobCount()
                                + " opened file descriptors. Some file descriptors "
                                + "must be closed to open additional ones.");
            }
        }
    }

    private void setCloseListenerToFd(
            @NonNull JetpackRevocableFileDescriptor revocableFileDescriptor,
            @NonNull String packageName) {
        revocableFileDescriptor.setCloseListener(e -> {
            synchronized (mLock) {
                List<JetpackRevocableFileDescriptor> fdsForPackage =
                        mSentAppSearchParcelFileDescriptorsLocked.get(packageName);
                if (fdsForPackage != null) {
                    fdsForPackage.remove(revocableFileDescriptor);
                    if (fdsForPackage.isEmpty()) {
                        mSentAppSearchParcelFileDescriptorsLocked.remove(packageName);
                    }
                }
            }
        });
    }

    private void addToSentAppSearchParcelFileDescriptorMap(
            @NonNull JetpackRevocableFileDescriptor revocableFileDescriptor,
            @NonNull String packageName) {
        synchronized (mLock) {
            List<JetpackRevocableFileDescriptor> rfdsForPackage =
                    mSentAppSearchParcelFileDescriptorsLocked.get(packageName);
            if (rfdsForPackage == null) {
                rfdsForPackage = new ArrayList<>();
                mSentAppSearchParcelFileDescriptorsLocked.put(packageName, rfdsForPackage);
            }
            rfdsForPackage.add(revocableFileDescriptor);
        }
    }

    /**
     * A custom {@link ParcelFileDescriptor} that provides an additional mechanism to register
     * a {@link ParcelFileDescriptor.OnCloseListener} which will be invoked when the file
     * descriptor is closed.
     *
     * <p> Since the {@link ParcelFileDescriptor} sent to the client side from the local storage
     * won't cross the binder, we could revoke the {@link ParcelFileDescriptor} in the client side
     * by directly close the one in AppSearch side. This class just adding close listener to the
     * inner {@link ParcelFileDescriptor}.
     */
    static class JetpackRevocableFileDescriptor extends ParcelFileDescriptor {
        private ParcelFileDescriptor.OnCloseListener mOnCloseListener;

        /**
         * Create a new ParcelFileDescriptor wrapped around another descriptor. By
         * default all method calls are delegated to the wrapped descriptor.
         */
        JetpackRevocableFileDescriptor(@NonNull ParcelFileDescriptor parcelFileDescriptor) {
            super(parcelFileDescriptor);
        }

        void setCloseListener(
                @NonNull ParcelFileDescriptor.OnCloseListener onCloseListener) {
            if (mOnCloseListener != null) {
                throw new IllegalStateException("The close listener has already been set.");
            }
            mOnCloseListener = Preconditions.checkNotNull(onCloseListener);
        }

        /**  Close the super {@link ParcelFileDescriptor} without invoke close listener.         */
        void closeSuperDirectly() throws IOException {
            super.close();
        }

        @Override
        public void close() throws IOException {
            try {
                super.close();
                if (mOnCloseListener != null) {
                    mOnCloseListener.onClose(null);
                }
            } catch (IOException e) {
                if (mOnCloseListener != null) {
                    mOnCloseListener.onClose(e);
                }
                throw e;
            }
        }
    }
}
