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

package androidx.tracing.driver

/** Makes it possible to associate debug annotations & categories to a [TraceEvent]. */
// False positive: https://youtrack.jetbrains.com/issue/KTIJ-22326
@Suppress("NOTHING_TO_INLINE", "OPTIONAL_DECLARATION_USAGE_IN_NON_COMMON_SOURCE")
public class TraceEventScope @PublishedApi internal constructor() {
    @PublishedApi internal lateinit var event: TraceEvent

    /** Adds an annotation where the type of the [value] is an [Boolean]. */
    public fun addMetadataEntry(name: String, value: Boolean) {
        val entry = nextMetadataEntry()
        entry.name = name
        entry.type = METADATA_TYPE_BOOLEAN
        entry.booleanValue = value
    }

    /** Adds an annotation where the type of the [value] is an [Long]. */
    public fun addMetadataEntry(name: String, value: Long) {
        val entry = nextMetadataEntry()
        entry.name = name
        entry.type = METADATA_TYPE_LONG
        entry.longValue = value
    }

    /** Adds an annotation where the type of the [value] is an [Double]. */
    public fun addMetadataEntry(name: String, value: Double) {
        val entry = nextMetadataEntry()
        entry.name = name
        entry.type = METADATA_TYPE_DOUBLE
        entry.doubleValue = value
    }

    /** Adds an annotation where the type of the [value] is an [String]. */
    public fun addMetadataEntry(name: String, value: String) {
        val entry = nextMetadataEntry()
        entry.name = name
        entry.type = METADATA_TYPE_STRING
        entry.stringValue = value
    }

    /**
     * Adds a category to the [TraceEvent].
     *
     * This is useful in the when an application is interested in a subset of [TraceEvent]s that
     * belong to well known categories. These are typically small identifiers useful for namespacing
     * [TraceEvent]s.
     */
    public fun addCategory(name: String) {
        event.lastCategoryIndex += 1
        if (event.lastCategoryIndex >= event.categories.size) {
            // Resize if necessary.
            event.categories += DEFAULT_STRING
        }
        event.categories[event.lastCategoryIndex] = name
    }

    private inline fun nextMetadataEntry(): MetadataEntry {
        event.lastMetadataEntryIndex += 1
        if (event.lastMetadataEntryIndex >= event.metadataEntries.size) {
            // Resize if necessary.
            event.metadataEntries += MetadataEntry()
        }
        return event.metadataEntries[event.lastMetadataEntryIndex]
    }
}
