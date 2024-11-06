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

package com.example.androidx.webkit;

import android.app.Activity;
import android.os.Bundle;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewStartUpConfig;

import java.util.concurrent.Executors;

/**
 * An {@link Activity} that calls
 * {@link androidx.webkit.WebViewCompat#startUpWebView(WebViewStartUpConfig, WebViewCompat.WebViewStartUpCallback)}
 * to startup WebView asynchronously and displays the summary of startup.
 */
public class AsyncStartUpActivity extends AppCompatActivity {
    private long mStartCaptureTime;

    @OptIn(markerClass = WebViewCompat.ExperimentalAsyncStartUp.class)
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        // Take care not to startup WebView (including layout inflation) before the explicit call
        // to `startUpWebView()`.

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_async_startup);
        setTitle(R.string.async_startup_activity_title);
        WebkitHelpers.appendWebViewVersionToTitle(this);

        WebViewStartUpConfig config = new WebViewStartUpConfig.Builder(
                Executors.newSingleThreadExecutor()).build();
        WebViewCompat.WebViewStartUpCallback callback = result -> {
            long duration =
                    System.currentTimeMillis()
                            - mStartCaptureTime;
            TextView tv = findViewById(R.id.async_startup_textview);
            tv.setText("WebView Async StartUp onSuccess() called in " + duration + " ms.");

            //  Render content in a WebView similar to real-life usage.
            WebView wv = new WebView(this);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.MATCH_PARENT
            );
            LinearLayout layout = (LinearLayout) findViewById(R.id.activity_async_startup);
            layout.addView(wv, params);
            wv.setWebViewClient(new WebViewClient());
            wv.loadUrl("www.example.com");
        };
        mStartCaptureTime = System.currentTimeMillis();
        WebViewCompat.startUpWebView(config, callback);
    }
}
