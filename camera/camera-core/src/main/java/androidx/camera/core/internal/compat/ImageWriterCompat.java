/*
 * Copyright 2020 The Android Open Source Project
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

package androidx.camera.core.internal.compat;

import android.media.Image;
import android.media.ImageWriter;
import android.os.Build;
import android.os.Handler;
import android.view.Surface;

import androidx.annotation.IntRange;
import androidx.annotation.RequiresApi;

import org.jspecify.annotations.NonNull;

import java.util.concurrent.Executor;

/**
 * Helper for accessing features of {@link ImageWriter} in a backwards compatible fashion.
 */
@RequiresApi(23)
public final class ImageWriterCompat {

    /**
     * <p>
     * Create a new ImageWriter with given number of max Images and format.
     * </p>
     * <p>
     * The {@code maxImages} parameter determines the maximum number of
     * {@link android.media.Image} objects that can be be dequeued from the
     * {@code ImageWriter} simultaneously. Requesting more buffers will use up
     * more memory, so it is important to use only the minimum number necessary.
     * </p>
     * <p>
     * The format specifies the image format of this ImageWriter. The format
     * from the {@code surface} will be overridden with this format. For example,
     * if the surface is obtained from a {@link android.graphics.SurfaceTexture}, the default
     * format may be {@link android.graphics.PixelFormat#RGBA_8888}. If the application creates an
     * ImageWriter with this surface and {@link android.graphics.ImageFormat#PRIVATE}, this
     * ImageWriter will be able to operate with {@link android.graphics.ImageFormat#PRIVATE} Images.
     * </p>
     * <p>
     * Note that the consumer end-point may or may not be able to support Images with different
     * format, for such case, the application should only use this method if the consumer is able
     * to consume such images.
     * </p>
     * <p>
     * The input Image size depends on the Surface that is provided by
     * the downstream consumer end-point.
     * </p>
     *
     * @param surface The destination Surface this writer produces Image data
     *            into.
     * @param maxImages The maximum number of Images the user will want to
     *            access simultaneously for producing Image data. This should be
     *            as small as possible to limit memory use. Once maxImages
     *            Images are dequeued by the user, one of them has to be queued
     *            back before a new Image can be dequeued for access via
     *            {@link ImageWriter#dequeueInputImage()}.
     * @param format The format of this ImageWriter. It can be any valid format specified by
     *            {@link android.graphics.ImageFormat} or {@link android.graphics.PixelFormat}.
     *
     * @return a new ImageWriter instance.
     */
    public static @NonNull ImageWriter newInstance(@NonNull Surface surface,
            @IntRange(from = 1) int maxImages, int format) {
        if (Build.VERSION.SDK_INT >= 29) {
            return ImageWriterCompatApi29Impl.newInstance(surface, maxImages, format);
        } else if (Build.VERSION.SDK_INT >= 26) {
            return ImageWriterCompatApi26Impl.newInstance(surface, maxImages, format);
        }

        throw new RuntimeException(
                "Unable to call newInstance(Surface, int, int) on API " + Build.VERSION.SDK_INT
                        + ". Version 26 or higher required.");
    }

    /**
     * <p>
     * Create a new ImageWriter with given number of max Images and format.
     * </p>
     * <p>
     * The {@code maxImages} parameter determines the maximum number of
     * {@link android.media.Image} objects that can be be dequeued from the
     * {@code ImageWriter} simultaneously. Requesting more buffers will use up
     * more memory, so it is important to use only the minimum number necessary.
     * </p>
     *
     * @param surface The destination Surface this writer produces Image data
     *            into.
     * @param maxImages The maximum number of Images the user will want to
     *            access simultaneously for producing Image data. This should be
     *            as small as possible to limit memory use. Once maxImages
     *            Images are dequeued by the user, one of them has to be queued
     *            back before a new Image can be dequeued for access via
     *            {@link ImageWriter#dequeueInputImage()}.
     *
     * @return a new ImageWriter instance.
     */
    public static @NonNull ImageWriter newInstance(@NonNull Surface surface,
            @IntRange(from = 1) int maxImages) {
        if (Build.VERSION.SDK_INT >= 23) {
            return ImageWriterCompatApi23Impl.newInstance(surface, maxImages);
        }

        throw new RuntimeException(
                "Unable to call newInstance(Surface, int) on API " + Build.VERSION.SDK_INT
                        + ". Version 23 or higher required.");
    }

    /**
     * <p>
     * Dequeue an image from image writer.
     * </p>
     *
     * @param imageWriter image writer instance.
     * @return image from image writer
     */
    public static @NonNull Image dequeueInputImage(@NonNull ImageWriter imageWriter) {
        if (Build.VERSION.SDK_INT >= 23) {
            return ImageWriterCompatApi23Impl.dequeueInputImage(imageWriter);
        }

        throw new RuntimeException(
                "Unable to call dequeueInputImage() on API " + Build.VERSION.SDK_INT
                        + ". Version 23 or higher required.");
    }

    /**
     * <p>
     * Queue an image to image writer.
     * </p>
     *
     * @param imageWriter image writer instance.
     * @param image image to image writer.
     */
    public static void queueInputImage(@NonNull ImageWriter imageWriter, @NonNull Image image) {
        if (Build.VERSION.SDK_INT >= 23) {
            ImageWriterCompatApi23Impl.queueInputImage(imageWriter, image);
            return;
        }

        throw new RuntimeException(
                "Unable to call queueInputImage() on API " + Build.VERSION.SDK_INT
                        + ". Version 23 or higher required.");
    }

    /**
     * Sets an {@link ImageWriter.OnImageReleasedListener} to be notified when an {@link Image} is
     * released from the {@link ImageWriter}.
     *
     * <p>This method is a compatibility wrapper for
     * {@link ImageWriter#setOnImageReleasedListener(ImageWriter.OnImageReleasedListener, Handler)}.
     *
     * @param imageWriter The {@link ImageWriter} to set the listener on.
     * @param releasedListener The {@link ImageWriter.OnImageReleasedListener} to be notified when
     *                        an image is released.
     * @param executor The {@link Executor} on which the listener should be invoked.
     */
    public static void setOnImageReleasedListener(@NonNull ImageWriter imageWriter,
            ImageWriter.@NonNull OnImageReleasedListener releasedListener, @NonNull
            Executor executor) {
        ImageWriterCompatApi23Impl.setOnImageReleasedListener(imageWriter, releasedListener,
                executor);
    }

    /**
     * Close the existing ImageWriter instance.
     *
     * @param imageWriter ImageWriter instance.
     */
    public static void close(@NonNull ImageWriter imageWriter) {
        if (Build.VERSION.SDK_INT >= 23) {
            ImageWriterCompatApi23Impl.close(imageWriter);
            return;
        }

        throw new RuntimeException(
                "Unable to call close() on API " + Build.VERSION.SDK_INT
                        + ". Version 23 or higher required.");
    }

    // Class should not be instantiated.
    private ImageWriterCompat() {
    }
}
