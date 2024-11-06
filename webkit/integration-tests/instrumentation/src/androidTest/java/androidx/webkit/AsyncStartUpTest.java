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

package androidx.webkit;

import androidx.concurrent.futures.ResolvableFuture;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.MediumTest;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Field;
import java.util.concurrent.Executors;

/**
 * Tests for behaviours related to
 * {@link WebViewCompat#startUpWebView(WebViewStartUpConfig, WebViewCompat.WebViewStartUpCallback)}
 */
@MediumTest
@RunWith(AndroidJUnit4.class)
public class AsyncStartUpTest {
    /**
     * Tests that
     * {@link WebViewCompat#startUpWebView(WebViewStartUpConfig, WebViewCompat.WebViewStartUpCallback)}
     * has loaded WebView when `onSuccess` is triggered.
     */
    @Test
    @MediumTest
    @Ignore("b/376656739")
    public void testAsyncStartUp_onSuccessLoadsWebView() throws Throwable {
        WebViewStartUpConfig config = new WebViewStartUpConfig.Builder(
                Executors.newSingleThreadExecutor()).build();
        final ResolvableFuture<WebViewStartUpResult> startUpFinishedFuture =
                ResolvableFuture.create();
        Assert.assertFalse(webViewCurrentlyLoaded());

        WebViewCompat.startUpWebView(config,
                startUpFinishedFuture::set);

        // Wait until the callback has triggered.
        WebkitUtils.waitForFuture(startUpFinishedFuture);
        Assert.assertTrue(webViewCurrentlyLoaded());
    }

    /**
     * Tests that
     * {@link WebViewCompat#startUpWebView(WebViewStartUpConfig, WebViewCompat.WebViewStartUpCallback)}
     * can be called multiple times.
     */
    @Test
    @MediumTest
    @Ignore("b/376656739")
    public void testAsyncStartUp_canBeCalledMultipleTimes() throws Throwable {
        WebViewStartUpConfig config = new WebViewStartUpConfig.Builder(
                Executors.newSingleThreadExecutor()).build();
        final ResolvableFuture<WebViewStartUpResult> startUpFinishedFuture1 =
                ResolvableFuture.create();
        final ResolvableFuture<WebViewStartUpResult> startUpFinishedFuture2 =
                ResolvableFuture.create();
        Assert.assertFalse(webViewCurrentlyLoaded());

        WebViewCompat.startUpWebView(config,
                startUpFinishedFuture1::set);
        WebViewCompat.startUpWebView(config,
                startUpFinishedFuture2::set);

        // Wait until the callback has triggered.
        WebkitUtils.waitForFuture(startUpFinishedFuture1);
        WebkitUtils.waitForFuture(startUpFinishedFuture2);
        Assert.assertTrue(webViewCurrentlyLoaded());
    }

    /**
     * Checks if WebView is currently loaded in the current process.
     */
    private static boolean webViewCurrentlyLoaded() {
        // TODO(crbug.com/1355297): This is racy but it is the best we can do for now since we can't
        //  access the lock for sProviderInstance in WebView. Evaluate a framework path for
        //  ProcessGlobalConfig.
        try {
            Class<?> webViewFactoryClass = Class.forName("android.webkit.WebViewFactory");
            Field providerInstanceField =
                    webViewFactoryClass.getDeclaredField("sProviderInstance");
            providerInstanceField.setAccessible(true);
            return providerInstanceField.get(null) != null;
        } catch (Exception e) {
            // This means WebViewFactory was not found or sProviderInstance was not found within
            // the class. If that is true, WebView doesn't seem to be loaded.
            return false;
        }
    }
}
