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

package com.example.androidx.mediarouting.util;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.util.Log;

import com.example.androidx.mediarouting.services.SampleMediaRouteProviderService;
import com.example.androidx.mediarouting.services.WrapperMediaRouteProviderService;

import org.jspecify.annotations.NonNull;

public class Utils {
    private static final String TAG = "Utils";

    private Utils() {}

    /** Sets the simple media router provider service to be enabled or disabled. */
    public static void setSimpleRouteProviderServiceEnabled(
            @NonNull Context context, boolean enabled) {
        ComponentName componentName =
                new ComponentName(context, SampleMediaRouteProviderService.class);
        Log.i(TAG, "enable_simple_route_provider is changed to " + enabled);
        setMediaRouteProviderServiceEnabled(context, componentName, enabled);
    }

    /** Sets the wrapper media router provider service to be enabled or disabled. */
    public static void setWrapperRouteProviderServiceEnabled(
            @NonNull Context context, boolean enabled) {
        ComponentName componentName =
                new ComponentName(context, WrapperMediaRouteProviderService.class);
        Log.i(TAG, "enable_wrapper_route_provider is changed to " + enabled);
        setMediaRouteProviderServiceEnabled(context, componentName, enabled);
    }

    private static void setMediaRouteProviderServiceEnabled(
            @NonNull Context context, ComponentName componentName, boolean enabled) {
        PackageManager packageManager = context.getPackageManager();
        packageManager.setComponentEnabledSetting(
                componentName,
                enabled
                        ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                        : PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                /* flags= */ PackageManager.DONT_KILL_APP);
    }
}
