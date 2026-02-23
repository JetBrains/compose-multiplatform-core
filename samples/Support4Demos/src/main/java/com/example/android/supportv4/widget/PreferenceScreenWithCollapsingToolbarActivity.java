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
package com.example.android.supportv4.widget;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceFragmentCompat;

import com.example.android.supportv4.R;
import com.google.android.material.appbar.CollapsingToolbarLayout;

import org.jspecify.annotations.Nullable;

/**
 * This activity demonstrates the use of scrolling with a PreferenceScreen in the v4 support
 * library along with a collapsing app bar (everything inside a CoordinatorLayout). See the
 * associated layout file for details.
 * Note: The PreferenceScreen fragment roughly demonstrates the Android Settings App (specifically
 * the app screen).
 */
public class PreferenceScreenWithCollapsingToolbarActivity extends AppCompatActivity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_preference_screen_collapsing_toolbar);

        CollapsingToolbarLayout collapsingToolbar = findViewById(R.id.collapsing_toolbar_layout);
        collapsingToolbar.setTitle(
                getResources().getString(R.string.preference_screen_collapsing_appbar_title)
        );
        collapsingToolbar.setContentScrimColor(getResources().getColor(R.color.color1));

        MyPreferenceFragment fragment = new MyPreferenceFragment();
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.main_content, fragment)
                .commit();
    }

    public static class MyPreferenceFragment extends PreferenceFragmentCompat {
        @Override
        public void onCreatePreferences(
                @Nullable Bundle savedInstanceState,
                @Nullable String rootKey
        ) {
            setPreferencesFromResource(R.xml.demo_pref_screen, rootKey);
        }
    }
}
