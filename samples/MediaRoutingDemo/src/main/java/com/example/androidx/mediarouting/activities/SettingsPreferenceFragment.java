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

package com.example.androidx.mediarouting.activities;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

import com.example.androidx.mediarouting.R;
import com.example.androidx.mediarouting.util.Utils;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/** The preference fragment to show the switch settings. */
public class SettingsPreferenceFragment extends PreferenceFragmentCompat
        implements SharedPreferences.OnSharedPreferenceChangeListener {
    private static final String TAG = "SettingsPreference";
    private static final String PREF_KEY_SIMPLE_ROUTE_PROVIDER = "enable_simple_route_provider";
    private static final boolean PREF_DEFAULT_VALUE_ENABLE_SIMPLE_ROUTE_PROVIDER = true;
    private static final String PREF_KEY_WRAPPER_ROUTE_PROVIDER = "enable_wrapper_route_provider";
    private static final boolean PREF_DEFAULT_VALUE_ENABLE_WRAPPER_ROUTE_PROVIDER = false;
    private Context mContext;

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        setPreferencesFromResource(R.xml.settings_preference, rootKey);
        mContext = getPreferenceManager().getContext();

        SharedPreferences sharedPreferences = getPreferenceScreen().getSharedPreferences();
        if (sharedPreferences != null) {
            sharedPreferences.registerOnSharedPreferenceChangeListener(this);
        }
    }

    /** Returns {@code true} if the simple route provider is enables. */
    public static boolean isSimpleRouteProviderEnabled(@NonNull Context context) {
        SharedPreferences sharedPreferences =
                PreferenceManager.getDefaultSharedPreferences(context);
        return sharedPreferences.getBoolean(
                PREF_KEY_SIMPLE_ROUTE_PROVIDER, PREF_DEFAULT_VALUE_ENABLE_SIMPLE_ROUTE_PROVIDER);
    }

    /** Returns {@code true} if the wrapper route provider is enables. */
    public static boolean isWrapperRouteProviderEnabled(@NonNull Context context) {
        SharedPreferences sharedPreferences =
                PreferenceManager.getDefaultSharedPreferences(context);
        return sharedPreferences.getBoolean(
                PREF_KEY_WRAPPER_ROUTE_PROVIDER, PREF_DEFAULT_VALUE_ENABLE_WRAPPER_ROUTE_PROVIDER);
    }

    @Override
    public void onSharedPreferenceChanged(
            @NonNull SharedPreferences sharedPreferences, @NonNull String key) {
        switch (key) {
            case PREF_KEY_SIMPLE_ROUTE_PROVIDER:
                updateSimpleRouteProviderConfiguration(sharedPreferences);
                break;
            case PREF_KEY_WRAPPER_ROUTE_PROVIDER:
                updateWrapperRouteProviderConfiguration(sharedPreferences);
                break;
            default:
                // Ignored.
        }
    }

    private void updateSimpleRouteProviderConfiguration(
            @NonNull SharedPreferences sharedPreferences) {
        boolean enabled =
                sharedPreferences.getBoolean(
                        PREF_KEY_SIMPLE_ROUTE_PROVIDER,
                        PREF_DEFAULT_VALUE_ENABLE_SIMPLE_ROUTE_PROVIDER);
        Utils.setSimpleRouteProviderServiceEnabled(mContext, enabled);
    }

    private void updateWrapperRouteProviderConfiguration(
            @NonNull SharedPreferences sharedPreferences) {
        boolean enabled =
                sharedPreferences.getBoolean(
                        PREF_KEY_WRAPPER_ROUTE_PROVIDER,
                        PREF_DEFAULT_VALUE_ENABLE_WRAPPER_ROUTE_PROVIDER);
        Utils.setWrapperRouteProviderServiceEnabled(mContext, enabled);
    }
}
