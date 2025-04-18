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
import android.view.Surface;

import androidx.annotation.IntRange;
import androidx.annotation.RequiresApi;
import androidx.camera.core.impl.utils.MainThreadAsyncHandler;

import org.jspecify.annotations.NonNull;

import java.util.concurrent.Executor;

@RequiresApi(23)
final class ImageWriterCompatApi23Impl {

    static @NonNull ImageWriter newInstance(@NonNull Surface surface,
            @IntRange(from = 1) int maxImages) {
        return ImageWriter.newInstance(surface, maxImages);
    }

    static @NonNull Image dequeueInputImage(@NonNull ImageWriter imageWriter) {
        return imageWriter.dequeueInputImage();
    }

    static void queueInputImage(@NonNull ImageWriter imageWriter, @NonNull Image image) {
        imageWriter.queueInputImage(image);
    }

    static void close(ImageWriter imageWriter) {
        imageWriter.close();
    }

    static void setOnImageReleasedListener(@NonNull ImageWriter imageWriter,
            ImageWriter.@NonNull OnImageReleasedListener releasedListener,
            @NonNull Executor executor) {
        imageWriter.setOnImageReleasedListener(
                writer -> executor.execute(() -> releasedListener.onImageReleased(writer)),
                MainThreadAsyncHandler.getInstance());
    }

    // Class should not be instantiated.
    private ImageWriterCompatApi23Impl() {
    }
}

