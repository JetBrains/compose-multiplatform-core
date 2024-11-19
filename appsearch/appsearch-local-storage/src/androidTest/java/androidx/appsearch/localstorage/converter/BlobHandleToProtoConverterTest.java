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

package androidx.appsearch.localstorage.converter;

import static androidx.appsearch.testutil.AppSearchTestUtils.calculateDigest;
import static androidx.appsearch.testutil.AppSearchTestUtils.generateRandomBytes;

import static com.google.common.truth.Truth.assertThat;

import androidx.appsearch.app.AppSearchBlobHandle;
import androidx.appsearch.localstorage.util.PrefixUtil;

import com.google.android.icing.proto.PropertyProto;
import com.google.android.icing.protobuf.ByteString;

import org.junit.Test;

public class BlobHandleToProtoConverterTest {

    @Test
    public void testToBlobHandleProto() throws Exception {
        byte[] data = generateRandomBytes(20 * 1024); // 20 KiB
        byte[] digest = calculateDigest(data);
        AppSearchBlobHandle handle = AppSearchBlobHandle.createWithSha256(
                digest, "package", "db1", "ns");

        PropertyProto.BlobHandleProto proto = BlobHandleToProtoConverter.toBlobHandleProto(handle);

        assertThat(proto.getDigest().toByteArray()).isEqualTo(digest);
        assertThat(proto.getNamespace()).isEqualTo(
                PrefixUtil.createPrefix("package", "db1") + "ns");
    }

    @Test
    public void testToBlobHandle() throws Exception {
        byte[] data = generateRandomBytes(20 * 1024); // 20 KiB
        byte[] digest = calculateDigest(data);

        PropertyProto.BlobHandleProto proto = PropertyProto.BlobHandleProto.newBuilder()
                .setNamespace(PrefixUtil.createPrefix("package", "db1") + "ns")
                .setDigest(ByteString.copyFrom(digest))
                .build();
        AppSearchBlobHandle handle = BlobHandleToProtoConverter.toAppSearchBlobHandle(proto);

        assertThat(handle.getPackageName()).isEqualTo("package");
        assertThat(handle.getDatabaseName()).isEqualTo("db1");
        assertThat(handle.getNamespace()).isEqualTo("ns");
        assertThat(handle.getSha256Digest()).isEqualTo(digest);
    }
}
