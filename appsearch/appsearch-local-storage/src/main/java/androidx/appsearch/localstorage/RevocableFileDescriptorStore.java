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

package androidx.appsearch.localstorage;

import android.os.ParcelFileDescriptor;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;
import androidx.appsearch.exceptions.AppSearchException;

import java.io.IOException;

/**
 * Interface for revocable file descriptors storage.
 *
 * <p>This store allows wrapping {@link ParcelFileDescriptor} instances into revocable file
 * descriptors, enabling the ability to close and revoke it's access to the file even if the
 * {@link ParcelFileDescriptor} has been sent to the client side.
 *
 * <p> Implementations of this interface can provide controlled access to resources by associating
 * each file descriptor with a package and allowing them to be individually revoked by package
 * or revoked all at once.
 *
 * @exportToFramework:hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public interface RevocableFileDescriptorStore {

    /**
     * Wraps the provided ParcelFileDescriptor into a revocable file descriptor.
     * This allows for controlled access to the file descriptor, making it revocable by the store.
     *
     * @param packageName The package name requesting the revocable file descriptor.
     * @param parcelFileDescriptor The original ParcelFileDescriptor to be wrapped.
     * @return A ParcelFileDescriptor that can be revoked by the store.
     */
    @NonNull
    ParcelFileDescriptor wrapToRevocableFileDescriptor(@NonNull String packageName,
            @NonNull ParcelFileDescriptor parcelFileDescriptor) throws IOException;

    /**
     * Revokes all revocable file descriptors previously issued by the store.
     * After calling this method, any access to these file descriptors will fail.
     *
     * @throws IOException If an I/O error occurs while revoking file descriptors.
     */
    void revokeAll() throws IOException;

    /**
     * Revokes all revocable file descriptors for a specified package.
     * Only file descriptors associated with the given package name will be revoked.
     *
     * @param packageName The package name whose file descriptors should be revoked.
     * @throws IOException If an I/O error occurs while revoking file descriptors.
     */
    void revokeForPackage(@NonNull String packageName) throws IOException;

    /**  Checks if the specified package has reached its blob storage limit. */
    void checkBlobStoreLimit(@NonNull String packageName) throws AppSearchException;
}
