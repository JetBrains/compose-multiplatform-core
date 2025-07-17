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

package androidx.appsearch.builtintypes;

import static com.google.common.truth.Truth.assertThat;

import androidx.appsearch.app.SearchSpec;

import org.junit.Test;

public class BuiltInCorpusFiltersTest {
    @Test
    public void testMobileApplicationNamespace() {
        SearchSpec.Builder builder = new SearchSpec.Builder();
        SearchSpec searchSpec = BuiltInCorpusFilters.searchMobileApplicationCorpus(builder).build();
        assertThat(searchSpec.getFilterPackageNames()).containsExactly("android");
        assertThat(searchSpec.getFilterNamespaces()).containsExactly("apps");
    }

    @Test
    public void testPersonSchemaType() {
        SearchSpec.Builder builder = new SearchSpec.Builder();
        SearchSpec searchSpec = BuiltInCorpusFilters.searchPersonCorpus(builder).build();
        assertThat(searchSpec.getFilterPackageNames()).containsExactly("android");
        assertThat(searchSpec.getFilterSchemas()).containsExactly("builtin:Person");
    }
}
