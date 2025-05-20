/*
 * Copyright 2025 The Android Open Source Project
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

package com.example.androidx.webkit;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.webkit.Policy;
import androidx.webkit.WebViewBuilder;
import androidx.webkit.WebViewBuilderException;
import androidx.webkit.WebViewFeature;

import org.jspecify.annotations.Nullable;

/** An {@link Activity} to exercise WebViewBulder related functionality. */
public class WebViewBuilderActivity extends AppCompatActivity {

    private static final String TAG = "WebViewBuilderActivity";

    @OptIn(
            markerClass = {
                WebViewBuilder.Experimental.class,
            })
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_webview_builder);
        setTitle(R.string.webview_builder_title);

        if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEBVIEW_BUILDER)) {
            WebkitHelpers.showMessageInActivity(this, R.string.webkit_api_not_available);
            return;
        }

        Policy policy = new Policy.Builder().build();
        WebViewBuilder builder = new WebViewBuilder().setPolicy(policy);
        WebView wv;
        try {
            wv = builder.build(this);
        } catch (WebViewBuilderException exception) {
            showError(exception);
            return;
        }

        WebSettings webSettings = wv.getSettings();
        webSettings.setJavaScriptEnabled(true);

        addView(wv);
        wv.loadUrl("https://www.google.com");
    }

    private void showError(Throwable exception) {
        Log.e(TAG, "Failed to build WebView", exception);

        TextView errorMessage = new TextView(this);
        errorMessage.setText(exception.toString());
        addView(errorMessage);
    }

    private void addView(View view) {
        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.MATCH_PARENT);
        LinearLayout layout = (LinearLayout) findViewById(R.id.activity_webview_builder);

        layout.addView(view, params);
    }
}
