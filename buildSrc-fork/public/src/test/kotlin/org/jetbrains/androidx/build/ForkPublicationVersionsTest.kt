/*
 * Copyright 2026 The Android Open Source Project
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

package org.jetbrains.androidx.build

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ForkPublicationVersionsTest {

    private val catalog = mapOf(
        "COMPOSE" to "1.12.0-beta01",
        "LIFECYCLE" to "2.11.0",
        "SAVEDSTATE" to "1.5.0-alpha01",
        "NAVIGATIONEVENT" to "1.1.1",
    )

    private fun resolve(
        library: String,
        override: String? = null,
        snapshot: Boolean = false,
    ) = ForkPublicationVersions.resolve(library, override, catalog, snapshot).toString()

    @Test
    fun catalogValueIsUsedWhenThereIsNoOverride() {
        assertEquals("1.12.0-beta01", resolve("COMPOSE"))
        assertEquals("2.11.0", resolve("LIFECYCLE"))
        assertEquals("1.5.0-alpha01", resolve("SAVEDSTATE"))
    }

    @Test
    fun catalogKeyIsRemappedWhereTheTwoRegistriesDisagree() {
        assertEquals("1.1.1", resolve("NAVIGATION_EVENT"))
    }

    @Test
    fun overrideWinsOverTheCatalog() {
        assertEquals("1.12.0-beta02", resolve("COMPOSE", override = "1.12.0-beta02"))
        assertEquals("2.11.1", resolve("LIFECYCLE", override = "2.11.1"))
    }

    @Test
    fun snapshotReplacesThePreReleaseRatherThanAppending() {
        assertEquals("1.12.0-SNAPSHOT", resolve("COMPOSE", snapshot = true))
        assertEquals("1.5.0-SNAPSHOT", resolve("SAVEDSTATE", snapshot = true))
    }

    @Test
    fun snapshotOfAStableVersionAddsThePreRelease() {
        assertEquals("2.11.0-SNAPSHOT", resolve("LIFECYCLE", snapshot = true))
    }

    @Test
    fun snapshotAppliesOnTopOfAnOverride() {
        assertEquals("2.11.1-SNAPSHOT", resolve("LIFECYCLE", override = "2.11.1", snapshot = true))
    }

    @Test
    fun unmappedLibraryFails() {
        assertFailsWith("NOT_A_LIBRARY") { resolve("NOT_A_LIBRARY") }
    }

    @Test
    fun missingCatalogEntryFails() {
        assertFailsWith("NAVIGATION") {
            ForkPublicationVersions.resolve("NAVIGATION", null, catalog, false)
        }
    }

    @Test
    fun malformedOverrideFails() {
        assertFailsWith("Can not parse version: 2.11.0.1") {
            resolve("LIFECYCLE", override = "2.11.0.1")
        }
    }

    @Test
    fun overrideOutsideTheHouseGrammarFails() {
        assertFailsWith("followed by a number only") {
            resolve("COMPOSE", override = "1.12.0-beta01.1")
        }
    }

    @Test
    fun overrideWithBuildMetadataFails() {
        assertFailsWith("metadata is not allowed") {
            resolve("LIFECYCLE", override = "2.11.0+fleet.1")
        }
    }

    @Test
    fun overrideBelowTheCatalogValueFails() {
        assertFailsWith("is below the branch's version") {
            resolve("COMPOSE", override = "1.12.0-alpha09")
        }
    }

    @Test
    fun overrideOnADifferentUpstreamLineFails() {
        assertFailsWith("outside the branch's 2.11 line") {
            resolve("LIFECYCLE", override = "2.12.0")
        }
    }

    private fun assertFailsWith(expectedInMessage: String, block: () -> Unit) {
        try {
            block()
            fail("Expected a failure mentioning \"$expectedInMessage\", but none was thrown")
        } catch (e: Exception) {
            assertTrue(
                "Expected a message containing \"$expectedInMessage\", got \"${e.message}\"",
                e.message?.contains(expectedInMessage) == true,
            )
        }
    }
}
