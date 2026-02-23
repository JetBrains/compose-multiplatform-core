/*
 * Copyright 2021-2022 The Android Open Source Project
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

package androidx.wear.tiles;

import androidx.annotation.RestrictTo;
import androidx.annotation.RestrictTo.Scope;
import androidx.wear.protolayout.expression.Fingerprint;
import androidx.wear.protolayout.proto.StateProto;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Builders for state of a tile.
 *
 * @deprecated Use {@link androidx.wear.protolayout.StateBuilders} instead.
 */
@Deprecated
public final class StateBuilders {
    private StateBuilders() {}

    /** {@link State} information. */
    public static final class State {
        private final StateProto.State mImpl;
        private final @Nullable Fingerprint mFingerprint;

        State(StateProto.State impl, @Nullable Fingerprint fingerprint) {
            this.mImpl = impl;
            this.mFingerprint = fingerprint;
        }

        /** Gets the ID of the clickable that was last clicked. */
        public @NonNull String getLastClickableId() {
            return mImpl.getLastClickableId();
        }

        /** Get the fingerprint for this object, or null if unknown. */
        @RestrictTo(Scope.LIBRARY_GROUP)
        public @Nullable Fingerprint getFingerprint() {
            return mFingerprint;
        }

        @RestrictTo(Scope.LIBRARY_GROUP)
        public static @NonNull State fromProto(StateProto.@NonNull State proto) {
            return new State(proto, null);
        }

        @RestrictTo(Scope.LIBRARY_GROUP)
        public StateProto.@NonNull State toProto() {
            return mImpl;
        }

        /** Builder for {@link State} */
        public static final class Builder {
            private final StateProto.State.Builder mImpl = StateProto.State.newBuilder();
            private final Fingerprint mFingerprint = new Fingerprint(616326811);

            public Builder() {}

            /** Builds an instance from accumulated values. */
            public @NonNull State build() {
                return new State(mImpl.build(), mFingerprint);
            }
        }
    }
}
