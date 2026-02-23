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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.fragment.app.Fragment;

import com.example.android.supportv4.R;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Simple LinearLayout with ~50 TextViews.
 */
public class PreferenceScreenDetailsTemplateFragment extends Fragment {

    @Override
    public @NonNull View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        View view =  inflater.inflate(
                R.layout.fragment_preference_screen_details_template,
                container,
                false
        );

        LinearLayout linearLayout = view.findViewById(R.id.linear_layout);

        for (int index = 1; index <= 50; index++) {
            TextView textView = new TextView(getContext());
            textView.setText("TextView " + index);
            textView.setFocusable(true);
            textView.setFocusableInTouchMode(true); // For keyboard/touch based focus
            textView.setPadding(16, 16, 16, 16);

            linearLayout.addView(textView);
        }
        return view;
    }
}
