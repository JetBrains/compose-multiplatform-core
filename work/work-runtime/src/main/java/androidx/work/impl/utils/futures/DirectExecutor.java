/*
 * Copyright 2018 The Android Open Source Project
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

package androidx.work.impl.utils.futures;

import androidx.annotation.RestrictTo;

import java.util.concurrent.Executor;

/**
 * Cloned from concurrent-futures package to avoid AndroidX namespace issues since there is no
 * supportlib 28.* equivalent of this class.
 *
 * An {@link Executor} that runs each task in the thread that invokes {@link Executor#execute
 * execute}.
 *
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
enum DirectExecutor implements Executor {
    INSTANCE;

    @Override
    public void execute(Runnable command) {
        command.run();
    }

    @Override
    public String toString() {
        return "DirectExecutor";
    }
}