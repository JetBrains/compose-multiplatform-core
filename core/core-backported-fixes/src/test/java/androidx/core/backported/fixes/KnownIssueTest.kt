/*
 * Copyright 2025 The Android Open Source Project
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

package androidx.core.backported.fixes

import com.google.common.truth.Truth.assertThat
import kotlin.collections.map
import kotlin.reflect.full.companionObjectInstance
import kotlin.reflect.full.memberProperties
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/** Tests for [KnownIssue] */
@RunWith(JUnit4::class)
class KnownIssueTest {

    @Test
    fun values_IdsAreUnique() {
        val ids = KnownIssue.values().map { ki -> ki.id }
        assertThat(ids).containsNoDuplicates()
    }

    @Test
    fun values_AliasesAreUnique() {
        val aliases = KnownIssue.values().map { ki -> ki.alias }
        assertThat(aliases).containsNoDuplicates()
    }

    @Test
    fun values_IncludeAllConstants() {
        val coi = KnownIssue::class.companionObjectInstance
        assertThat(coi).isNotNull()
        val props = coi!!::class.memberProperties
        val kis: Iterable<KnownIssue> =
            props
                .filter { p -> p.name.startsWith("KI_") }
                .map { p -> p.getter.call(coi) as KnownIssue }
        assertThat(KnownIssue.values()).containsExactlyElementsIn(kis)
    }

    @Test
    fun constantsName_matchId() {
        val coi = KnownIssue::class.companionObjectInstance
        assertThat(coi).isNotNull()
        val props = coi!!::class.memberProperties
        val names = props.filter { p -> p.name.startsWith("KI_") }.map { p -> p.name }
        assertThat(names).containsExactlyElementsIn(KnownIssue.values().map { ki -> "KI_${ki.id}" })
    }
}
