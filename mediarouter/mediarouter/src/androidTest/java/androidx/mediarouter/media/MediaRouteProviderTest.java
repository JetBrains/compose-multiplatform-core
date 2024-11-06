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

package androidx.mediarouter.media;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.mediarouter.media.MediaRouteProvider.RouteControllerOptions;
import androidx.test.annotation.UiThreadTest;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

/** Test for {@link MediaRouteProvider}. */
@RunWith(AndroidJUnit4.class)
@UiThreadTest
public class MediaRouteProviderTest {
    private static final String ROUTE_ID = "route_id";

    private Context mContext;
    private MediaRouteProvider.RouteControllerOptions mRouteControllerOptions;

    @Before
    public void setUp() {
        mContext = ApplicationProvider.getApplicationContext();

        Bundle controlHints = new Bundle();
        controlHints.putBoolean("key", true);
        mRouteControllerOptions =
                new MediaRouteProvider.RouteControllerOptions.Builder()
                        .setControlHints(controlHints)
                        .build();
    }

    @Test
    @SmallTest
    public void onCreateDynamicGroupRouteControllerWithParameters_shouldProvideParameters() {
        MediaRouteProvider mediaRouteProvider = new TestMediaRouteProvider(mContext);
        TestDynamicGroupRouteController groupRouteController =
                (TestDynamicGroupRouteController)
                        mediaRouteProvider.onCreateDynamicGroupRouteController(
                                ROUTE_ID, mRouteControllerOptions);

        MediaRouteProvider.RouteControllerOptions routeControllerOptions =
                groupRouteController.getRouteControllerOptions();
        assertEquals(ROUTE_ID, groupRouteController.getInitialMemberRouteId());
        assertEquals(mRouteControllerOptions, routeControllerOptions);
        assertEquals(
                mRouteControllerOptions.getControlHints(),
                routeControllerOptions.getControlHints());
    }

    @Test
    @SmallTest
    public void onCreateDynamicGroupRouteController_shouldWorkWithoutParameters() {
        MediaRouteProvider mediaRouteProvider =
                new TestMediaRouteProviderWithoutParameters(mContext);
        TestDynamicGroupRouteController groupRouteController =
                (TestDynamicGroupRouteController)
                        mediaRouteProvider.onCreateDynamicGroupRouteController(
                                ROUTE_ID, mRouteControllerOptions);

        MediaRouteProvider.RouteControllerOptions routeControllerOptions =
                groupRouteController.getRouteControllerOptions();
        assertEquals(ROUTE_ID, groupRouteController.getInitialMemberRouteId());
        assertNull(routeControllerOptions);
        assertNotEquals(mRouteControllerOptions, routeControllerOptions);
    }

    private static class TestMediaRouteProvider extends MediaRouteProvider {

        private TestMediaRouteProvider(@NonNull Context context) {
            super(context);
        }

        @Override
        @Nullable
        public DynamicGroupRouteController onCreateDynamicGroupRouteController(
                @NonNull String initialMemberRouteId,
                @NonNull RouteControllerOptions routeControllerOptions) {
            return new TestDynamicGroupRouteController(
                    initialMemberRouteId, routeControllerOptions);
        }
    }

    private static class TestMediaRouteProviderWithoutParameters extends MediaRouteProvider {

        TestMediaRouteProviderWithoutParameters(Context context) {
            super(context);
        }

        @Override
        @Nullable
        public DynamicGroupRouteController onCreateDynamicGroupRouteController(
                @NonNull String initialMemberRouteId) {
            return new TestDynamicGroupRouteController(
                    initialMemberRouteId, /* routeControllerOptions= */ null);
        }
    }

    private static class TestDynamicGroupRouteController
            extends MediaRouteProvider.DynamicGroupRouteController {

        private final String mInitialMemberRouteId;
        @NonNull private final MediaRouteProvider.RouteControllerOptions mRouteControllerOptions;

        private TestDynamicGroupRouteController(
                String initialMemberRouteId,
                @NonNull MediaRouteProvider.RouteControllerOptions routeControllerOptions) {
            mInitialMemberRouteId = initialMemberRouteId;
            mRouteControllerOptions = routeControllerOptions;
        }

        @NonNull
        public String getInitialMemberRouteId() {
            return mInitialMemberRouteId;
        }

        @NonNull
        public RouteControllerOptions getRouteControllerOptions() {
            return mRouteControllerOptions;
        }

        @Override
        public void onAddMemberRoute(@NonNull String routeId) {}

        @Override
        public void onRemoveMemberRoute(@NonNull String routeId) {}

        @Override
        public void onUpdateMemberRoutes(@Nullable List<String> routeIds) {}
    }
}
