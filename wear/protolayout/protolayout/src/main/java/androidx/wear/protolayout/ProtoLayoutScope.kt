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

package androidx.wear.protolayout

import android.app.PendingIntent
import android.os.Bundle
import androidx.annotation.MainThread
import androidx.annotation.RestrictTo
import androidx.annotation.RestrictTo.Scope
import androidx.annotation.VisibleForTesting
import androidx.core.os.BundleCompat
import androidx.wear.protolayout.ResourceBuilders.ImageResource
import androidx.wear.protolayout.ResourceBuilders.Resources
import java.util.Objects

/**
 * A scope object responsible for handling internal details of ProtoLayout layouts and Tiles.
 *
 * Object of this class, in Tiles cases, can be obtained via
 * `androidx.wear.tiles.RequestBuilders#TileRequest.getScope`.
 *
 * Some example of usage:
 * * Used for registering Android resources automatically instead of manually via
 *   `androidx.wear.tiles.TileService.onTileResourcesRequest`.
 *
 * This class is not thread safe and should only be used from one thread, [MainThread].
 */
@MainThread
public class ProtoLayoutScope
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
public constructor() {
    /**
     * Maps String key to the [ImageResource] type describing it in ProtoLayout terms.
     *
     * String key is generated based on the hash of the resource, to have its uniqueness and
     * preserve the same value across diff updates.
     *
     * This can be:
     * * Android Image [ResourceBuilders.AndroidImageResourceByResId]
     * * Lottie animation [ResourceBuilders.AndroidLottieResourceByResId]
     * * Inline Image [ResourceBuilders.InlineImageResource]
     * * Android Animated Image [ResourceBuilders.AndroidAnimatedImageResourceByResId]
     * * Android Seekable Animated Image
     *   [ResourceBuilders.AndroidSeekableAnimatedImageResourceByResId]
     */
    @VisibleForTesting internal val resources: MutableMap<String, ImageResource> = mutableMapOf()

    /** Maps Clickable ID to the [PendingIntent]. */
    @VisibleForTesting internal val pendingIntents: Bundle = Bundle()

    /**
     * Registers the given Android resource that corresponds to the given String ProtoLayout
     * resources ID and maps it to the given [ImageResource] type.
     */
    @RestrictTo(Scope.LIBRARY)
    public fun registerResource(id: String, resource: ImageResource) {
        resources[id] = resource
    }

    /** Registers the given [PendingIntent] with given clickable ID. */
    @RestrictTo(Scope.LIBRARY)
    public fun registerPendingIntent(id: String, intent: PendingIntent) {
        val storedIntent: PendingIntent? =
            BundleCompat.getParcelable(pendingIntents, id, PendingIntent::class.java)
        if (storedIntent != null && storedIntent != intent) {
            throw IllegalArgumentException(
                "Duplicate use of clickable ID \"$id\" for PendingIntents."
            )
        } else if (storedIntent == null) {
            pendingIntents.putParcelable(id, intent)
        }
    }

    /**
     * Returns [Resources] object containing all previously registered resources, with {@link
     * Image#setImageResource} and {@link Image#Builder(ProtoLayoutScope).
     *
     * Resource is registered with the String ID that is a hash value of all resources.
     */
    public fun collectResources(): Resources =
        Resources.Builder()
            .setVersion(hashedResources())
            .apply {
                for ((id, res) in resources) {
                    addIdToImageMapping(/* id= */ id, /* image= */ res)
                }
            }
            .build()

    /**
     * Returns a [Bundle] with all previously registered [PendingIntent].
     *
     * Bundle contains (key, value) pairs of [android.os.Parcelable], where `key` is String ID
     * corresponding to the [ModifiersBuilders.Clickable] ID, and `value` is [PendingIntent].
     */
    // TODO: b/427954838 - Add example API in KDocs on where this is registered.
    public fun collectPendingIntents(): Bundle = pendingIntents.clone() as Bundle

    /** Clears mappings for resources and pending intents. */
    @RestrictTo(Scope.LIBRARY_GROUP_PREFIX)
    public fun clear() {
        resources.clear()
        pendingIntents.clear()
    }

    /**
     * Generates String version of hash codes for all [resources].
     *
     * Hash is calculated by first sorting the Map, so the order of added elements is not important.
     * Then, we use hash for <id, resource> pair, and hash all those elements together.
     */
    private fun hashedResources(): String =
        Objects.hash(resources.toSortedMap().map { (id, res) -> Pair(id, res).hashCode() }.toList())
            .toString()
}
