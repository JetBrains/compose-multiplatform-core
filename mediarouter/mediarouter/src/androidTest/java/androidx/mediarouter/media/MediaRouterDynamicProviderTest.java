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

import static androidx.mediarouter.media.StubDynamicMediaRouteProviderService.ROUTE_ID_1;
import static androidx.mediarouter.media.StubDynamicMediaRouteProviderService.ROUTE_ID_2;
import static androidx.mediarouter.media.StubDynamicMediaRouteProviderService.ROUTE_ID_GROUP;
import static androidx.mediarouter.media.StubDynamicMediaRouteProviderService.ROUTE_NAME_1;
import static androidx.mediarouter.media.StubDynamicMediaRouteProviderService.ROUTE_NAME_2;
import static androidx.test.core.app.ApplicationProvider.getApplicationContext;
import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.os.Build;
import android.os.ConditionVariable;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.mediarouter.testing.MediaRouterTestHelper;
import androidx.test.filters.SdkSuppress;
import androidx.test.filters.SmallTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Test for {@link MediaRouter} functionality around routes from a provider that supports {@link
 * MediaRouteProviderDescriptor#supportsDynamicGroupRoute() dynamic group routes}.
 */
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.R) // Dynamic groups require API 30+.
@SmallTest
public final class MediaRouterDynamicProviderTest {

    private enum RouteConnectionState {
        STATE_UNKNOWN,
        STATE_CONNECTED,
        STATE_DISCONNECTED
    }

    private Context mContext;
    private MediaRouter mRouter;
    private MediaRouteSelector mSelector;
    private MediaRouterCallbackImpl mCallback;
    private MediaRouter.RouteInfo mRoute1;
    private MediaRouter.RouteInfo mRoute2;
    private RouteConnectionState mRouteConnectionState;
    private MediaRouter.RouteInfo mConnectedRoute;
    private MediaRouter.RouteInfo mRequestedRoute;
    private int mRouteDisconnectedReason;

    @Before
    public void setUp() {
        mRouteConnectionState = RouteConnectionState.STATE_UNKNOWN;
        mSelector =
                new MediaRouteSelector.Builder()
                        .addControlCategory(
                                StubDynamicMediaRouteProviderService.CATEGORY_DYNAMIC_PROVIDER_TEST)
                        .build();
        mCallback = new MediaRouterCallbackImpl();
        getInstrumentation()
                .runOnMainSync(
                        () -> {
                            mContext = getApplicationContext();
                            mRouter = MediaRouter.getInstance(mContext);
                            mRouter.addCallback(
                                    mSelector,
                                    mCallback,
                                    MediaRouter.CALLBACK_FLAG_PERFORM_ACTIVE_SCAN);
                        });
        Map<String, MediaRouter.RouteInfo> routeSnapshot =
                mCallback.waitForRoutes(ROUTE_ID_1, ROUTE_ID_1);
        mRoute1 = routeSnapshot.get(ROUTE_ID_1);
        Objects.requireNonNull(mRoute1);
        MediaRouteDescriptor mediaRouteDescriptor1 = mRoute1.getMediaRouteDescriptor();
        Objects.requireNonNull(mediaRouteDescriptor1);
        assertEquals(ROUTE_ID_1, mediaRouteDescriptor1.getId());
        assertEquals(ROUTE_NAME_1, mediaRouteDescriptor1.getName());

        mRoute2 = routeSnapshot.get(ROUTE_ID_2);
        Objects.requireNonNull(mRoute2);
        MediaRouteDescriptor mediaRouteDescriptor2 = mRoute2.getMediaRouteDescriptor();
        Objects.requireNonNull(mediaRouteDescriptor2);
        assertEquals(ROUTE_ID_2, mediaRouteDescriptor2.getId());
        assertEquals(ROUTE_NAME_2, mediaRouteDescriptor2.getName());
    }

    @After
    public void tearDown() {
        getInstrumentation().runOnMainSync(MediaRouterTestHelper::resetMediaRouter);
    }

    // Tests.

    @Test()
    public void selectDynamicRoute_doesNotMarkMemberAsSelected() {
        MediaRouter.RouteInfo newSelectedRoute = mCallback.selectAndWaitForOnSelected(mRoute1);

        assertEquals(ROUTE_ID_GROUP, newSelectedRoute.getDescriptorId());
        assertFalse(runBlockingOnMainThreadWithResult(mRoute1::isSelected));
        assertTrue(runBlockingOnMainThreadWithResult(newSelectedRoute::isSelected));
    }

    @Test()
    public void connectDynamicRoute_shouldNotifyRouteConnected() {
        assertEquals(RouteConnectionState.STATE_UNKNOWN, mRouteConnectionState);
        List<MediaRouter.RouteInfo> connectedRoutes =
                mCallback.connectAndWaitForOnConnected(mRoute2);

        assertNotNull(mConnectedRoute);
        assertEquals(ROUTE_ID_GROUP, mConnectedRoute.getDescriptorId());
        assertEquals(1, connectedRoutes.size());
        MediaRouter.RouteInfo connectedRoute = connectedRoutes.get(0);
        assertEquals(ROUTE_ID_GROUP, connectedRoute.getDescriptorId());
        assertTrue(runBlockingOnMainThreadWithResult(connectedRoute::isConnected));

        assertNotNull(mRequestedRoute);
        assertEquals(ROUTE_ID_2, mRequestedRoute.getDescriptorId());
        assertFalse(runBlockingOnMainThreadWithResult(mRequestedRoute::isConnected));
        assertFalse(runBlockingOnMainThreadWithResult(mRoute2::isConnected));
        assertEquals(RouteConnectionState.STATE_CONNECTED, mRouteConnectionState);
    }

    @Test()
    public void disconnectDynamicRoute_shouldNotifyRouteDisconnected() {
        assertEquals(RouteConnectionState.STATE_UNKNOWN, mRouteConnectionState);
        List<MediaRouter.RouteInfo> connectedRoutes =
                mCallback.connectAndWaitForOnConnected(mRoute2);
        assertEquals(RouteConnectionState.STATE_CONNECTED, mRouteConnectionState);
        assertEquals(1, connectedRoutes.size());

        connectedRoutes = mCallback.disconnectAndWaitForOnDisconnected(mRoute2);

        assertNull(mConnectedRoute);
        assertEquals(0, connectedRoutes.size());

        assertNotNull(mRequestedRoute);
        assertEquals(ROUTE_ID_2, mRequestedRoute.getDescriptorId());
        assertFalse(runBlockingOnMainThreadWithResult(mRequestedRoute::isConnected));
        assertFalse(runBlockingOnMainThreadWithResult(mRoute2::isConnected));
        assertEquals(RouteConnectionState.STATE_DISCONNECTED, mRouteConnectionState);
        assertEquals(MediaRouter.REASON_DISCONNECTED, mRouteDisconnectedReason);
    }

    @Test()
    public void connectDynamicRoute_failedToConnect_shouldNotifyRouteDisconnected() {
        assertEquals(RouteConnectionState.STATE_UNKNOWN, mRouteConnectionState);
        MediaRouter.RouteInfo selectedRoute = mCallback.selectAndWaitForOnSelected(mRoute1);
        assertEquals(ROUTE_ID_GROUP, selectedRoute.getDescriptorId());
        assertFalse(runBlockingOnMainThreadWithResult(mRoute1::isSelected));
        assertTrue(runBlockingOnMainThreadWithResult(selectedRoute::isSelected));

        List<MediaRouter.RouteInfo> connectedRoutes =
                mCallback.connectAndWaitForOnConnected(selectedRoute);

        assertNull(mConnectedRoute);
        assertEquals(0, connectedRoutes.size());

        assertNotNull(mRequestedRoute);
        assertEquals(ROUTE_ID_GROUP, mRequestedRoute.getDescriptorId());
        assertFalse(runBlockingOnMainThreadWithResult(mRequestedRoute::isConnected));
        assertFalse(runBlockingOnMainThreadWithResult(selectedRoute::isConnected));
        assertEquals(RouteConnectionState.STATE_DISCONNECTED, mRouteConnectionState);
    }

    // Internal methods.

    private Map<String, MediaRouter.RouteInfo> getCurrentRoutesAsMap() {
        Supplier<Map<String, MediaRouter.RouteInfo>> supplier =
                () -> {
                    Map<String, MediaRouter.RouteInfo> routeIds = new HashMap<>();
                    for (MediaRouter.RouteInfo route : mRouter.getRoutes()) {
                        routeIds.put(route.getDescriptorId(), route);
                    }
                    return routeIds;
                };
        return runBlockingOnMainThreadWithResult(supplier);
    }

    @SuppressWarnings("unchecked") // Allows us to pull a generic result out of runOnMainSync.
    private <Result> Result runBlockingOnMainThreadWithResult(Supplier<Result> supplier) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            return supplier.get();
        } else {
            Result[] resultHolder = (Result[]) new Object[1];
            getInstrumentation().runOnMainSync(() -> resultHolder[0] = supplier.get());
            return resultHolder[0];
        }
    }

    // Internal classes and interfaces.

    // Equivalent to java.util.function.Supplier, except it's available before API 24.
    private interface Supplier<Result> {

        Result get();
    }

    private class MediaRouterCallbackImpl extends MediaRouter.Callback {

        private final ConditionVariable mPendingRoutesConditionVariable = new ConditionVariable();
        private final Set<String> mRouteIdsPending = new HashSet<>();
        private final ConditionVariable mSelectedRouteChangeConditionVariable =
                new ConditionVariable(/* state= */ true);
        private final ConditionVariable mRouteConnectionConditionVariable =
                new ConditionVariable(/* state= */ true);

        private Map<String, MediaRouter.RouteInfo> waitForRoutes(String... routeIds) {
            Set<String> routesIdsSet = new HashSet<>(Arrays.asList(routeIds));
            getInstrumentation()
                    .runOnMainSync(
                            () -> {
                                Map<String, MediaRouter.RouteInfo> routes = getCurrentRoutesAsMap();
                                if (!routes.keySet().containsAll(routesIdsSet)) {
                                    mPendingRoutesConditionVariable.close();
                                    mRouteIdsPending.clear();
                                    mRouteIdsPending.addAll(routesIdsSet);
                                } else {
                                    mPendingRoutesConditionVariable.open();
                                }
                            });
            mPendingRoutesConditionVariable.block();
            return getCurrentRoutesAsMap();
        }

        public MediaRouter.RouteInfo selectAndWaitForOnSelected(
                MediaRouter.RouteInfo routeToSelect) {
            mSelectedRouteChangeConditionVariable.close();
            getInstrumentation().runOnMainSync(routeToSelect::select);
            mSelectedRouteChangeConditionVariable.block();
            return runBlockingOnMainThreadWithResult(() -> mRouter.getSelectedRoute());
        }

        public List<MediaRouter.RouteInfo> connectAndWaitForOnConnected(
                MediaRouter.RouteInfo routeToConnect) {
            mRouteConnectionConditionVariable.close();
            getInstrumentation().runOnMainSync(routeToConnect::connect);
            mRouteConnectionConditionVariable.block();
            return runBlockingOnMainThreadWithResult(() -> mRouter.getConnectedRoutes());
        }

        public List<MediaRouter.RouteInfo> disconnectAndWaitForOnDisconnected(
                MediaRouter.RouteInfo routeToDisconnect) {
            mRouteConnectionConditionVariable.close();
            getInstrumentation().runOnMainSync(routeToDisconnect::disconnect);
            mRouteConnectionConditionVariable.block();
            return runBlockingOnMainThreadWithResult(() -> mRouter.getConnectedRoutes());
        }

        @Override
        public void onRouteSelected(
                @NonNull MediaRouter router,
                @NonNull MediaRouter.RouteInfo selectedRoute,
                int reason,
                @NonNull MediaRouter.RouteInfo requestedRoute) {
            mSelectedRouteChangeConditionVariable.open();
        }

        @Override
        public void onRouteAdded(
                @NonNull MediaRouter router, @NonNull MediaRouter.RouteInfo route) {
            if (getCurrentRoutesAsMap().keySet().containsAll(mRouteIdsPending)) {
                mPendingRoutesConditionVariable.open();
            }
        }

        @Override
        public void onRouteConnected(
                @NonNull MediaRouter router,
                @NonNull MediaRouter.RouteInfo connectedRoute,
                @NonNull MediaRouter.RouteInfo requestedRoute) {
            mRouteConnectionState = RouteConnectionState.STATE_CONNECTED;
            mConnectedRoute = connectedRoute;
            mRequestedRoute = requestedRoute;
            mRouteConnectionConditionVariable.open();
        }

        @Override
        public void onRouteDisconnected(
                @NonNull MediaRouter router, @NonNull MediaRouter.RouteInfo route, int reason) {
            mRouteConnectionState = RouteConnectionState.STATE_DISCONNECTED;
            mConnectedRoute = null;
            mRequestedRoute = route;
            mRouteDisconnectedReason = reason;
            mRouteConnectionConditionVariable.open();
        }
    }
}
