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

package androidx.xr.scenecore.impl.extensions;

import android.util.Log;

import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;

import com.android.extensions.xr.XrExtensions;

/** Provides the OEM implementation of {@link XrExtensions}. */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
public class XrExtensionsProvider {
    private XrExtensionsProvider() {}

    /**
     * Returns the OEM implementation of {@link XrExtensions} or returns null if no implementation
     * is found.
     */
    @Nullable
    public static XrExtensions getXrExtensions() {
        try {
            return XrExtensionsInstance.getInstance();
        } catch (NoClassDefFoundError e) {
            Log.w("XrExtensionsProvider", "No XrExtensions implementation found.", e);
            return null;
        }
    }

    private static class XrExtensionsInstance {
        private XrExtensionsInstance() {}

        private static class XrExtensionsHolder {
            public static final XrExtensions INSTANCE = new XrExtensions();
        }

        private static XrExtensions getInstance() {
            return XrExtensionsHolder.INSTANCE;
        }
    }
}
