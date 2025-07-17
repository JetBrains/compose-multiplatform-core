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
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.javascriptengine.JavaScriptIsolate;
import androidx.javascriptengine.JavaScriptSandbox;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import org.jspecify.annotations.Nullable;

public class JavaScriptEngineEvaluationActivity extends AppCompatActivity {
    private Button mEvaluate;
    private Button mCreateIsolate;
    private Button mCloseSandbox;
    private Button mCloseIsolate;
    private EditText mQueuedEvalsNumber;
    private EditText mSequentialEvalsNumber;
    private EditText mText;
    private TextView mResultView;
    private TextView mTimeView;
    private TextView mErrorView;
    private TextView mCrashView;
    private JavaScriptSandbox mSandbox;
    private JavaScriptIsolate mCurrentIsolate;

    private static final String TAG = "JavaScriptEngineEvaluationActivity";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_javascriptengine_evaluation);
        setTitle(R.string.javascriptengine_evaluation_activity_title);

        mEvaluate = (Button) findViewById(R.id.evaluateButton);
        mCreateIsolate = (Button) findViewById(R.id.createIsolate);
        mCloseSandbox = (Button) findViewById(R.id.closeSandbox);
        mCloseIsolate = (Button) findViewById(R.id.closeIsolate);
        mQueuedEvalsNumber = (EditText) findViewById(R.id.queuedEvalsNumber);
        mSequentialEvalsNumber = (EditText) findViewById(R.id.sequentialEvalsNumber);
        mText = (EditText) findViewById(R.id.jscode);
        mResultView = (TextView) findViewById(R.id.result);
        mTimeView = (TextView) findViewById(R.id.time);
        mErrorView = (TextView) findViewById(R.id.error);
        mCrashView = (TextView) findViewById(R.id.crash);

        mCreateIsolate.setOnClickListener(view -> {
            // Don't leak the current isolate if one exists.
            closeIsolate();
            ListenableFuture<JavaScriptIsolate> unusedFuture = getOrCreateCurrentIsolate();
        });

        mEvaluate.setOnClickListener(view -> evaluate());

        mCloseIsolate.setOnClickListener(view -> closeIsolate());

        mCloseSandbox.setOnClickListener(view -> closeSandbox());
    }

    @Override
    protected void onDestroy() {
        closeSandbox();
        super.onDestroy();
    }

    private void evaluate() {
        mResultView.setText("");
        mErrorView.setText("");
        mTimeView.setText("");
        mCrashView.setText("");
        final String jsCode = mText.getText().toString();
        final int queuedEvals = Integer.parseInt(mQueuedEvalsNumber.getText().toString());
        final int sequentialEvals = Integer.parseInt(mSequentialEvalsNumber.getText()
                .toString());
        long startTime = System.nanoTime();
        final ListenableFuture<String> resultFuture;
        if (mCurrentIsolate == null) {
            resultFuture = Futures.transformAsync(
                    getOrCreateCurrentIsolate(), isolate ->
                            doEvaluationsAsync(isolate, jsCode, queuedEvals, sequentialEvals),
                    getMainExecutor());
        } else {
            resultFuture = doEvaluationsAsync(mCurrentIsolate,
                    jsCode, queuedEvals, sequentialEvals);
        }
        jsengineResultHandling(resultFuture, startTime);
    }

    private void closeIsolate() {
        if (mCurrentIsolate != null) {
            mCurrentIsolate.close();
            mCurrentIsolate = null;
        }
    }

    private void closeSandbox() {
        closeIsolate();
        if (mSandbox != null) {
            mSandbox.close();
            mSandbox = null;
        }
    }

    private ListenableFuture<JavaScriptIsolate> getOrCreateCurrentIsolate() {
        final ListenableFuture<JavaScriptSandbox> jsSandboxFuture;
        if (mSandbox == null) {
            jsSandboxFuture = JavaScriptSandbox.createConnectedInstanceAsync(this);
        } else {
            jsSandboxFuture = Futures.immediateFuture(mSandbox);
        }

        return Futures.transform(
                jsSandboxFuture, input -> {
                    mSandbox = input;
                    if (mCurrentIsolate == null) {
                        final JavaScriptIsolate isolate = input.createIsolate();
                        mCurrentIsolate = isolate;
                        isolate.addOnTerminatedCallback(info ->
                                mCrashView.setText(info.toString()));
                        return isolate;
                    } else {
                        return mCurrentIsolate;
                    }
                }, getMainExecutor());
    }

    private ListenableFuture<String> doEvaluationsAsync(
            final JavaScriptIsolate isolate, String jsCode, int queuedEvals, int sequentialEvals) {
        for (int i = 0; i < queuedEvals; i++) {
            ListenableFuture<String> queuedResultFuture = isolate.evaluateJavaScriptAsync(jsCode);
            Futures.addCallback(queuedResultFuture, new FutureCallback<String>() {
                @Override
                public void onSuccess(String result) {
                }

                @Override
                public void onFailure(Throwable t) {
                }
            }, getMainExecutor());
        }
        ListenableFuture<String> resultFuture = isolate.evaluateJavaScriptAsync(jsCode);
        for (int i = 0; i < sequentialEvals; i++) {
            resultFuture = Futures.transformAsync(resultFuture, prevResult ->
                    isolate.evaluateJavaScriptAsync(jsCode), getMainExecutor());
        }
        return resultFuture;
    }

    private void jsengineResultHandling(ListenableFuture<String> resultFuture, long startTime) {
        Futures.addCallback(resultFuture, new FutureCallback<String>() {
            @Override
            public void onSuccess(String result) {
                long endTime = System.nanoTime();
                long timeTaken = endTime - startTime;
                long timeTakenMillis = timeTaken / 1000_000;
                mResultView.setText(result);
                mTimeView.setText("Time Taken: " + timeTakenMillis + "ms");
            }

            @Override
            public void onFailure(Throwable t) {
                long endTime = System.nanoTime();
                long timeTaken = endTime - startTime;
                long timeTakenMillis = timeTaken / 1000_000;
                mErrorView.setText(t.toString());
                mTimeView.setText("Time Taken: " + timeTakenMillis + "ms");
            }
        }, getMainExecutor());
    }
}
