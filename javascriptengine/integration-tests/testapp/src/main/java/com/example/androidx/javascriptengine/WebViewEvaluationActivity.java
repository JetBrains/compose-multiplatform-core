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

package com.example.androidx.javascriptengine;

import android.os.Bundle;
import android.os.Process;
import android.webkit.ValueCallback;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import org.jspecify.annotations.Nullable;

public class WebViewEvaluationActivity extends AppCompatActivity {
    private Button mWebviewButton;
    private Button mCloseWebviewButton;
    private EditText mQueuedEvalsNumber;
    private EditText mSequentialEvalsNumber;
    private TextView mResultView;
    private TextView mTimeView;
    private EditText mText;
    private WebView mWebView;

    private static final String TAG = "WebViewEvaluationActivity";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_web_view_evaluation);
        setTitle(R.string.webview_evaluation_activity_title);

        mWebviewButton = (Button) findViewById(R.id.evaluateButton);
        mCloseWebviewButton = (Button) findViewById(R.id.closeWebView);
        mQueuedEvalsNumber = (EditText) findViewById(R.id.queuedEvalsNumber);
        mSequentialEvalsNumber = (EditText) findViewById(R.id.sequentialEvalsNumber);
        mResultView = (TextView) findViewById(R.id.result);
        mTimeView = (TextView) findViewById(R.id.time);
        mText = (EditText) findViewById(R.id.jscode);

        mWebviewButton.setOnClickListener(view -> evaluate());

        mCloseWebviewButton.setOnClickListener(view -> destroyWebView());
    }

    private void evaluate() {
        final String jsCode = mText.getText().toString();
        final int queuedEvals = Integer.parseInt(mQueuedEvalsNumber.getText().toString());
        final int sequentialEvals = Integer.parseInt(
                mSequentialEvalsNumber.getText().toString());
        mResultView.setText("");
        mTimeView.setText("");
        final long startTime = System.nanoTime();
        if (mWebView == null) {
            mWebView = new WebView(this);
            mWebView.getSettings().setJavaScriptEnabled(true);
        }
        for (int i = 0; i < queuedEvals; i++) {
            mWebView.evaluateJavascript(jsCode, (result) -> {});
        }
        {
            final class Callback implements ValueCallback<String> {
                private final WebView mWebView;
                private int mCountdown;

                Callback(WebView webView, int countdown) {
                    mWebView = webView;
                    mCountdown = countdown;
                }

                @Override
                public void onReceiveValue(String result) {
                    if (mCountdown == 0) {
                        long endTime = System.nanoTime();
                        long timeTaken = endTime - startTime;
                        long timeTakenMillis = timeTaken / 1000_000;
                        mResultView.setText(result);
                        mTimeView.setText("Time Taken: " + timeTakenMillis + "ms");
                    } else {
                        mCountdown--;
                        mWebView.evaluateJavascript(jsCode, this);
                    }
                }
            }
            mWebView.evaluateJavascript(jsCode, new Callback(mWebView, sequentialEvals));
        }
    }

    @Override
    protected void onDestroy() {
        destroyWebView();
        super.onDestroy();
        Process.killProcess(Process.myPid());
    }

    private void destroyWebView() {
        if (mWebView != null) {
            mWebView.destroy();
            mWebView = null;
        }
    }
}
