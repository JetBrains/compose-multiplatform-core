/*
 * Copyright 2021 The Android Open Source Project
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

import android.annotation.SuppressLint;

import androidx.annotation.RestrictTo;
import androidx.annotation.RestrictTo.Scope;
import androidx.wear.protolayout.expression.Fingerprint;
import androidx.wear.protolayout.proto.TypesProto;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Builders for extensible primitive types used by layout elements.
 *
 * @deprecated Use {@link androidx.wear.protolayout.TypeBuilders} instead.
 */
@Deprecated
public final class TypeBuilders {
    private TypeBuilders() {}

    /** An int32 type. */
    public static final class Int32Prop {
        private final TypesProto.Int32Prop mImpl;
        private final @Nullable Fingerprint mFingerprint;

        Int32Prop(TypesProto.Int32Prop impl, @Nullable Fingerprint fingerprint) {
            this.mImpl = impl;
            this.mFingerprint = fingerprint;
        }

        /** Gets the value. Intended for testing purposes only. */
        public int getValue() {
            return mImpl.getValue();
        }

        /** Get the fingerprint for this object, or null if unknown. */
        @RestrictTo(Scope.LIBRARY_GROUP)
        public @Nullable Fingerprint getFingerprint() {
            return mFingerprint;
        }

        static @NonNull Int32Prop fromProto(TypesProto.@NonNull Int32Prop proto) {
            return new Int32Prop(proto, null);
        }

        TypesProto.@NonNull Int32Prop toProto() {
            return mImpl;
        }

        /** Builder for {@link Int32Prop} */
        public static final class Builder {
            private final TypesProto.Int32Prop.Builder mImpl = TypesProto.Int32Prop.newBuilder();
            private final Fingerprint mFingerprint = new Fingerprint(-1809132005);

            public Builder() {}

            /** Sets the value. */
            public @NonNull Builder setValue(int value) {
                mImpl.setValue(value);
                mFingerprint.recordPropertyUpdate(1, value);
                return this;
            }

            /** Builds an instance from accumulated values. */
            public @NonNull Int32Prop build() {
                return new Int32Prop(mImpl.build(), mFingerprint);
            }
        }
    }

    /** A string type. */
    public static final class StringProp {
        private final TypesProto.StringProp mImpl;
        private final @Nullable Fingerprint mFingerprint;

        StringProp(TypesProto.StringProp impl, @Nullable Fingerprint fingerprint) {
            this.mImpl = impl;
            this.mFingerprint = fingerprint;
        }

        /** Gets the value. Intended for testing purposes only. */
        public @NonNull String getValue() {
            return mImpl.getValue();
        }

        /** Get the fingerprint for this object, or null if unknown. */
        @RestrictTo(Scope.LIBRARY_GROUP)
        public @Nullable Fingerprint getFingerprint() {
            return mFingerprint;
        }

        static @NonNull StringProp fromProto(TypesProto.@NonNull StringProp proto) {
            return new StringProp(proto, null);
        }

        TypesProto.@NonNull StringProp toProto() {
            return mImpl;
        }

        /** Builder for {@link StringProp} */
        public static final class Builder {
            private final TypesProto.StringProp.Builder mImpl = TypesProto.StringProp.newBuilder();
            private final Fingerprint mFingerprint = new Fingerprint(-319420356);

            public Builder() {}

            /** Sets the value. */
            public @NonNull Builder setValue(@NonNull String value) {
                mImpl.setValue(value);
                mFingerprint.recordPropertyUpdate(1, value.hashCode());
                return this;
            }

            /** Builds an instance from accumulated values. */
            public @NonNull StringProp build() {
                return new StringProp(mImpl.build(), mFingerprint);
            }
        }
    }

    /** A float type. */
    public static final class FloatProp {
        private final TypesProto.FloatProp mImpl;
        private final @Nullable Fingerprint mFingerprint;

        FloatProp(TypesProto.FloatProp impl, @Nullable Fingerprint fingerprint) {
            this.mImpl = impl;
            this.mFingerprint = fingerprint;
        }

        /** Gets the value. Intended for testing purposes only. */
        public float getValue() {
            return mImpl.getValue();
        }

        /** Get the fingerprint for this object, or null if unknown. */
        @RestrictTo(Scope.LIBRARY_GROUP)
        public @Nullable Fingerprint getFingerprint() {
            return mFingerprint;
        }

        static @NonNull FloatProp fromProto(TypesProto.@NonNull FloatProp proto) {
            return new FloatProp(proto, null);
        }

        TypesProto.@NonNull FloatProp toProto() {
            return mImpl;
        }

        /** Builder for {@link FloatProp} */
        public static final class Builder {
            private final TypesProto.FloatProp.Builder mImpl = TypesProto.FloatProp.newBuilder();
            private final Fingerprint mFingerprint = new Fingerprint(399943127);

            public Builder() {}

            /** Sets the value. */
            public @NonNull Builder setValue(float value) {
                mImpl.setValue(value);
                mFingerprint.recordPropertyUpdate(1, Float.floatToIntBits(value));
                return this;
            }

            /** Builds an instance from accumulated values. */
            public @NonNull FloatProp build() {
                return new FloatProp(mImpl.build(), mFingerprint);
            }
        }
    }

    /** A boolean type. */
    public static final class BoolProp {
        private final TypesProto.BoolProp mImpl;
        private final @Nullable Fingerprint mFingerprint;

        BoolProp(TypesProto.BoolProp impl, @Nullable Fingerprint fingerprint) {
            this.mImpl = impl;
            this.mFingerprint = fingerprint;
        }

        /** Gets the value. Intended for testing purposes only. */
        public boolean getValue() {
            return mImpl.getValue();
        }

        /** Get the fingerprint for this object, or null if unknown. */
        @RestrictTo(Scope.LIBRARY_GROUP)
        public @Nullable Fingerprint getFingerprint() {
            return mFingerprint;
        }

        static @NonNull BoolProp fromProto(TypesProto.@NonNull BoolProp proto) {
            return new BoolProp(proto, null);
        }

        TypesProto.@NonNull BoolProp toProto() {
            return mImpl;
        }

        /** Builder for {@link BoolProp} */
        public static final class Builder {
            private final TypesProto.BoolProp.Builder mImpl = TypesProto.BoolProp.newBuilder();
            private final Fingerprint mFingerprint = new Fingerprint(-278424864);

            public Builder() {}

            /** Sets the value. */
            @SuppressLint("MissingGetterMatchingBuilder")
            public @NonNull Builder setValue(boolean value) {
                mImpl.setValue(value);
                mFingerprint.recordPropertyUpdate(1, Boolean.hashCode(value));
                return this;
            }

            /** Builds an instance from accumulated values. */
            public @NonNull BoolProp build() {
                return new BoolProp(mImpl.build(), mFingerprint);
            }
        }
    }
}
