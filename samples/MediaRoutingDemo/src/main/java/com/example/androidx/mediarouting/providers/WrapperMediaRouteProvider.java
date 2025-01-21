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

package com.example.androidx.mediarouting.providers;

import android.content.Context;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.util.Log;

import androidx.mediarouter.media.MediaRouteDescriptor;
import androidx.mediarouter.media.MediaRouteDiscoveryRequest;
import androidx.mediarouter.media.MediaRouteProvider;
import androidx.mediarouter.media.MediaRouteProviderDescriptor;
import androidx.mediarouter.media.MediaRouteSelector;
import androidx.mediarouter.media.MediaRouter;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Wraps routes from a different provider and publishes the resulting wrapper routes. */
public class WrapperMediaRouteProvider extends MediaRouteProvider {
    private static final String TAG = "WrapperMrp";

    /**
     * A custom media control intent category for special requests that are supported by this
     * provider's routes.
     */
    public static final String CATEGORY_WRAPPER_ROUTE =
            "com.example.androidx.media.CATEGORY_WRAPPER_ROUTE";

    private static final IntentFilter WRAPPER_CONTROL_FILTER;
    private static final String ROUTE_ID_SEPARATOR = ":";
    private static final String KEY_ORIGINAL_ROUTE_ID = "key_original_route_id";
    private static final String WRAPPER_MARKER = "wrapper";
    private static final long MIN_INTERVAL_BETWEEN_ROUTE_PUBLICATIONS_MS =
            DateUtils.SECOND_IN_MILLIS / 10; // 0.1 second.

    private final MediaRouter mMediaRouter;
    private final OriginalMediaRouterCallback mOriginalMediaRouterCallback;

    private final Map<String, MediaRouteDescriptor> mOriginalDescriptorIdToWrapperRoutes =
            new HashMap<>();
    private final Handler mHandler;
    private Runnable mPublishRoutesRunnable;
    private long mLastPublishTimestampMs;

    static {
        WRAPPER_CONTROL_FILTER = new IntentFilter();
        WRAPPER_CONTROL_FILTER.addCategory(CATEGORY_WRAPPER_ROUTE);
    }

    /**
     * Creates a media route provider to provide wrapper routes.
     *
     * @param context the context
     */
    public WrapperMediaRouteProvider(@NonNull Context context) {
        super(context);
        mMediaRouter = MediaRouter.getInstance(context);
        mOriginalMediaRouterCallback = new OriginalMediaRouterCallback();
        mHandler = new Handler();
    }

    @Override
    public void onDiscoveryRequestChanged(@Nullable MediaRouteDiscoveryRequest request) {
        Log.i(TAG, "onDiscoveryRequestChanged with request: " + request);
        boolean shouldDiscoverDevices =
                request != null && request.getSelector().hasControlCategory(CATEGORY_WRAPPER_ROUTE);
        if (shouldDiscoverDevices) {
            int flags = request.isActiveScan() ? MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY : 0;
            mOriginalMediaRouterCallback.startScanning(flags);
        } else {
            mOriginalMediaRouterCallback.stopScanning();
        }
    }

    private void publishRoutes() {
        long timeSinceLastPublish = SystemClock.elapsedRealtime() - mLastPublishTimestampMs;
        if (timeSinceLastPublish < MIN_INTERVAL_BETWEEN_ROUTE_PUBLICATIONS_MS) {
            if (mPublishRoutesRunnable == null) {
                long delayToPublish =
                        MIN_INTERVAL_BETWEEN_ROUTE_PUBLICATIONS_MS - timeSinceLastPublish;
                mPublishRoutesRunnable = this::publishRoutesInternal;
                mHandler.postDelayed(mPublishRoutesRunnable, delayToPublish);
            }
            return;
        }
        publishRoutesInternal();
    }

    private void publishRoutesInternal() {
        mLastPublishTimestampMs = SystemClock.elapsedRealtime();
        mPublishRoutesRunnable = null;

        Collection<MediaRouteDescriptor> wrapperRoutes =
                mOriginalDescriptorIdToWrapperRoutes.values();
        MediaRouteProviderDescriptor.Builder builder =
                new MediaRouteProviderDescriptor.Builder()
                        .addRoutes(wrapperRoutes)
                        .setSupportsDynamicGroupRoute(true);
        setDescriptor(builder.build());
        logMediaRouteDescriptors(wrapperRoutes);
    }

    /** Logs a list of {@link MediaRouteDescriptor}. */
    private static void logMediaRouteDescriptors(Collection<MediaRouteDescriptor> routes) {
        Log.i(TAG, "Published " + routes.size() + " routes");
        int index = 0;
        for (MediaRouteDescriptor route : routes) {
            Log.d(TAG, "[" + index + "] " + routeLogString(route));
            index++;
        }
    }

    private static String routeLogString(MediaRouteDescriptor route) {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(
                String.format(
                        Locale.ROOT,
                        "Route: %s (%s) [%s] ",
                        route.getName(),
                        route.getId(),
                        route.getDescription()));
        String controlFiltersString = controlFiltersToString(route.getControlFilters());
        if (!TextUtils.isEmpty(controlFiltersString)) {
            stringBuilder.append(controlFiltersString);
        }
        return stringBuilder.toString();
    }

    private static String controlFiltersToString(List<IntentFilter> controlFilters) {
        StringBuilder stringBuilder = new StringBuilder();
        for (IntentFilter filter : controlFilters) {
            Iterator<String> categoryIterator = filter.categoriesIterator();
            while (categoryIterator.hasNext()) {
                String category = categoryIterator.next();
                stringBuilder.append("/").append(category);
            }
        }
        return stringBuilder.toString();
    }

    private static String getDescriptorId(String routeId) {
        return routeId.contains(ROUTE_ID_SEPARATOR)
                ? routeId.substring(routeId.lastIndexOf(ROUTE_ID_SEPARATOR) + 1)
                : routeId;
    }

    private static String getWrapperDescriptorId(String originalDescriptorId) {
        return originalDescriptorId + "-" + WRAPPER_MARKER;
    }

    private static String getWrapperRouteName(String originalRouteName) {
        return originalRouteName + " [" + WRAPPER_MARKER + "]";
    }

    private @Nullable MediaRouteDescriptor getWrapperRouteDescriptor(
            MediaRouter.@NonNull RouteInfo originalRoute) {
        MediaRouteDescriptor originalRouteDescriptor = originalRoute.getMediaRouteDescriptor();
        if (originalRouteDescriptor == null) {
            return null;
        }
        MediaRouteDescriptor.Builder builder =
                getWrapperRouteDescriptorBuilder(originalRouteDescriptor);
        Bundle extrasBundle = new Bundle();
        extrasBundle.putString(KEY_ORIGINAL_ROUTE_ID, originalRoute.getId());
        builder.setExtras(extrasBundle);
        return builder.build();
    }

    private MediaRouteDescriptor.Builder getWrapperRouteDescriptorBuilder(
            MediaRouteDescriptor originalRouteDescriptor) {
        String wrapperDescriptorId = getWrapperDescriptorId(originalRouteDescriptor.getId());
        String wrapperRouteName = getWrapperRouteName(originalRouteDescriptor.getName());
        return new MediaRouteDescriptor.Builder(originalRouteDescriptor)
                .setId(wrapperDescriptorId)
                .setName(wrapperRouteName)
                .clearControlFilters()
                .addControlFilter(WRAPPER_CONTROL_FILTER);
    }

    /** Tracks original route events. */
    private class OriginalMediaRouterCallback extends MediaRouter.Callback {

        private static final String CATEGORY_CAST = "com.google.android.gms.cast.CATEGORY_CAST";
        private final MediaRouteSelector mSelector;

        OriginalMediaRouterCallback() {
            mSelector = new MediaRouteSelector.Builder().addControlCategory(CATEGORY_CAST).build();
        }

        void startScanning(int flags) {
            mMediaRouter.addCallback(mSelector, this, flags);
            for (MediaRouter.RouteInfo route : mMediaRouter.getRoutes()) {
                if (route.matchesSelector(mSelector)) {
                    addOriginalRoute(route);
                }
            }
            publishRoutes();
        }

        void stopScanning() {
            mMediaRouter.addCallback(mSelector, this, /* flags= */ 0);
        }

        @Override
        public void onRouteAdded(
                @NonNull MediaRouter router, MediaRouter.@NonNull RouteInfo route) {
            addOriginalRoute(route);
            publishRoutes();
        }

        @Override
        public void onRouteChanged(
                @NonNull MediaRouter router, MediaRouter.@NonNull RouteInfo route) {
            addOriginalRoute(route);
            publishRoutes();
        }

        @Override
        public void onRouteRemoved(
                @NonNull MediaRouter router, MediaRouter.@NonNull RouteInfo route) {
            String originalDescriptorId = getDescriptorId(route.getId());
            mOriginalDescriptorIdToWrapperRoutes.remove(originalDescriptorId);
            publishRoutes();
        }

        void addOriginalRoute(MediaRouter.@NonNull RouteInfo originalRoute) {
            if (originalRoute.isSelected() || originalRoute.isConnected()) {
                // The wrapper route provider only wraps discovered routes and it wouldn't wrap
                // selected routes or connected routes.
                return;
            }
            String originalDescriptorId = getDescriptorId(originalRoute.getId());
            MediaRouteDescriptor wrapperDescriptor = getWrapperRouteDescriptor(originalRoute);
            mOriginalDescriptorIdToWrapperRoutes.put(originalDescriptorId, wrapperDescriptor);
        }
    }
}
