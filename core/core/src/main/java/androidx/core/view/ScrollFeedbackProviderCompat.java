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

package androidx.core.view;

import android.os.Build;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.ScrollFeedbackProvider;
import android.view.View;
import android.view.ViewConfiguration;

import androidx.annotation.RequiresApi;

import org.jspecify.annotations.NonNull;

/** Compat to access {@link ScrollFeedbackProvider} across different build versions. */
public class ScrollFeedbackProviderCompat {

    private final ScrollFeedbackProviderImpl mImpl;

    private ScrollFeedbackProviderCompat(@NonNull View view) {
        if (Build.VERSION.SDK_INT >= 35) {
            mImpl = new ScrollFeedbackProviderApi35Impl(view);
        } else {
            mImpl = new ScrollFeedbackProviderBaseImpl();
        }
    }

    /** Creates an instance of {@link ScrollFeedbackProviderCompat}. */
    public static @NonNull ScrollFeedbackProviderCompat createProvider(@NonNull View view) {
        return new ScrollFeedbackProviderCompat(view);
    }

    /**
     * Call this when the view has snapped to an item.
     *
     * @param inputDeviceId the ID of the {@link InputDevice} that generated the motion triggering
     *          the snap.
     * @param source the input source of the motion causing the snap.
     * @param axis the axis of {@code event} that caused the item to snap.
     */
    public void onSnapToItem(int inputDeviceId, int source, int axis) {
        mImpl.onSnapToItem(inputDeviceId, source, axis);
    }

    /**
     * Call this when the view has reached the scroll limit.
     *
     * <p>Note that a feedback may not be provided on every call to this method. This interface, for
     * instance, may provide feedback on every `N`th scroll limit event. For the interface to
     * properly provide feedback when needed, call this method for each scroll limit event that you
     * want to be accounted to scroll limit feedback.
     *
     * @param inputDeviceId the ID of the {@link InputDevice} that caused scrolling to hit limit.
     * @param source the input source of the motion that caused scrolling to hit the limit.
     * @param axis the axis of {@code event} that caused scrolling to hit the limit.
     * @param isStart {@code true} if scrolling hit limit at the start of the scrolling list, and
     *                {@code false} if the scrolling hit limit at the end of the scrolling list.
     *                <i>start</i> and <i>end</i> in this context are not geometrical references.
     *                Instead, they refer to the start and end of a scrolling experience. As such,
     *                "start" for some views may be at the bottom of a scrolling list, while it may
     *                be at the top of scrolling list for others.
     */
    public void onScrollLimit(int inputDeviceId, int source, int axis, boolean isStart) {
        mImpl.onScrollLimit(inputDeviceId, source, axis, isStart);
    }

    /**
     * Call this when the view has scrolled.
     *
     * <p>Different axes have different ways to map their raw axis values to pixels for scrolling.
     * When calling this method, use the scroll values in pixels by which the view was scrolled; do
     * not use the raw axis values. That is, use whatever value is passed to one of View's scrolling
     * methods (example: {@link View#scrollBy(int, int)}). For example, for vertical scrolling on
     * {@link MotionEvent#AXIS_SCROLL}, convert the raw axis value to the equivalent pixels by using
     * {@link ViewConfiguration#getScaledVerticalScrollFactor()}, and use that value for this method
     * call.
     *
     * <p>Note that a feedback may not be provided on every call to this method. This interface, for
     * instance, may provide feedback for every `x` pixels scrolled. For the interface to properly
     * track scroll progress and provide feedback when needed, call this method for each scroll
     * event that you want to be accounted to scroll feedback.
     *
     * @param inputDeviceId the ID of the {@link InputDevice} that caused scroll progress.
     * @param source the input source of the motion that caused scroll progress.
     * @param axis the axis of {@code event} that caused scroll progress.
     * @param deltaInPixels the amount of scroll progress, in pixels.
     */
    public void onScrollProgress(int inputDeviceId, int source, int axis, int deltaInPixels) {
        mImpl.onScrollProgress(inputDeviceId, source, axis, deltaInPixels);
    }

    @RequiresApi(35)
    private static class ScrollFeedbackProviderApi35Impl implements ScrollFeedbackProviderImpl {
        private final ScrollFeedbackProvider mProvider;

        ScrollFeedbackProviderApi35Impl(View view) {
            mProvider = ScrollFeedbackProvider.createProvider(view);
        }

        @Override
        public void onSnapToItem(int inputDeviceId, int source, int axis) {
            mProvider.onSnapToItem(inputDeviceId, source, axis);
        }

        @Override
        public void onScrollLimit(int inputDeviceId, int source, int axis, boolean isStart) {
            mProvider.onScrollLimit(inputDeviceId, source, axis, isStart);
        }

        @Override
        public void onScrollProgress(int inputDeviceId, int source, int axis, int deltaInPixels) {
            mProvider.onScrollProgress(inputDeviceId, source, axis, deltaInPixels);
        }
    }

    private static class ScrollFeedbackProviderBaseImpl implements ScrollFeedbackProviderImpl {
        @Override
        public void onSnapToItem(int inputDeviceId, int source, int axis) {}

        @Override
        public void onScrollLimit(int inputDeviceId, int source, int axis, boolean isStart) {}

        @Override
        public void onScrollProgress(int inputDeviceId, int source, int axis, int deltaInPixels) {}
    }

    /**
     * An interface parallel to {@link ScrollFeedbackProvider}, to allow different compat
     * implementations based on Build SDK version.
     */
    private interface ScrollFeedbackProviderImpl {
        void onSnapToItem(int inputDeviceId, int source, int axis);
        void onScrollLimit(int inputDeviceId, int source, int axis, boolean isStart);
        void onScrollProgress(int inputDeviceId, int source, int axis, int deltaInPixels);
    }
}
