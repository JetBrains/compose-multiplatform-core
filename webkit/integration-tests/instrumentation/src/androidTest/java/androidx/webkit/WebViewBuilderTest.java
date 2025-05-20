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

package androidx.webkit;

import android.webkit.WebView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;
import androidx.webkit.test.common.WebkitUtils;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class WebViewBuilderTest {
    @Before
    public void setUp() {
        WebkitUtils.checkFeature(WebViewFeature.WEBVIEW_BUILDER);
    }

    @Test
    public void testConstructsWebView() {
        Policy policy = new Policy.Builder().build();

        WebViewBuilder builder = new WebViewBuilder().setPolicy(policy);

        try (ActivityScenario<WebViewTestActivity> scenario =
                ActivityScenario.launch(WebViewTestActivity.class)) {
            scenario.onActivity(
                    activity -> {
                        try {
                            WebView webView = builder.build(activity);
                            Assert.assertNotNull(webView);
                            Assert.assertTrue(webView instanceof WebView);
                        } catch (WebViewBuilderException e) {
                            Assert.fail(e.toString());
                        }
                    });
        }
    }

    @Test
    public void testConstructsWebViewTwice() {
        Policy policy = new Policy.Builder().build();

        WebViewBuilder builder = new WebViewBuilder().setPolicy(policy);

        try (ActivityScenario<WebViewTestActivity> scenario =
                ActivityScenario.launch(WebViewTestActivity.class)) {
            scenario.onActivity(
                    activity -> {
                        try {
                            WebView webView = builder.build(activity);
                            WebView webView2 = builder.build(activity);
                            // These were two different WebView objects created.
                            Assert.assertTrue(webView != webView2);
                        } catch (WebViewBuilderException e) {
                            Assert.fail(e.toString());
                        }
                    });
        }
    }
}
