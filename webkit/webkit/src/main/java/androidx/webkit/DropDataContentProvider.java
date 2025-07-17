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

package androidx.webkit;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;

import androidx.webkit.internal.WebViewGlueCommunicator;

import org.chromium.support_lib_boundary.DropDataContentProviderBoundaryInterface;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.io.FileNotFoundException;

/**
 * WebView provides partial support for Android
 * <a href="https://developer.android.com/develop/ui/views/touch-and-input/drag-drop">
 * Drag and Drop</a> allowing images, text and links to be dragged out of a WebView.
 * <p>
 * The content provider is required to make the images drag work, to enable, you should add this
 * class to your manifest, for example:
 *
 * <pre class="prettyprint">
 *  &lt;provider
 *             android:authorities="&lt;your-package&gt;.DropDataProvider"
 *             android:name="androidx.webkit.DropDataContentProvider"
 *             android:exported="false"
 *             android:grantUriPermissions="true"/&gt;
 * </pre>
 *
 */
public final class DropDataContentProvider extends ContentProvider {
    DropDataContentProviderBoundaryInterface mImpl;

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public @Nullable ParcelFileDescriptor openFile(@NonNull Uri uri, @NonNull String mode)
            throws FileNotFoundException {
        return getDropImpl().openFile(this, uri);
    }

    @Override
    public @Nullable Cursor query(@NonNull Uri uri, String @Nullable [] projection,
            @Nullable String selection, String @Nullable [] selectionArgs,
            @Nullable String sortOrder) {
        return getDropImpl().query(uri, projection, selection, selectionArgs, sortOrder);
    }

    @Override
    public @Nullable String getType(@NonNull Uri uri) {
        return getDropImpl().getType(uri);
    }

    @Override
    public @Nullable Uri insert(@NonNull Uri uri, @Nullable ContentValues contentValues) {
        throw new UnsupportedOperationException("Insert method is not supported.");
    }

    @Override
    public int delete(@NonNull Uri uri, @Nullable String selection,
            String @Nullable [] selectionArgs) {
        throw new UnsupportedOperationException("delete method is not supported.");
    }

    @Override
    public int update(@NonNull Uri uri, @Nullable ContentValues contentValues, @Nullable String s,
            String @Nullable [] strings) {
        throw new UnsupportedOperationException("update method is not supported.");
    }

    @Override
    public @Nullable Bundle call(@NonNull String method, @Nullable String arg,
            @Nullable Bundle extras) {
        return getDropImpl().call(method, arg, extras);
    }

    private DropDataContentProviderBoundaryInterface getDropImpl() {
        if (mImpl == null) {
            mImpl = WebViewGlueCommunicator.getFactory().getDropDataProvider();
            mImpl.onCreate();
        }
        return mImpl;
    }
}
