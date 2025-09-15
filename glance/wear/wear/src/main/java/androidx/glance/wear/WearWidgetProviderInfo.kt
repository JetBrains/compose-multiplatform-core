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

package androidx.glance.wear

import android.content.ComponentName
import androidx.annotation.DrawableRes

/**
 * Describes the meta data for a Wear Widget provider.
 *
 * The fields in this class correspond to the fields in the `<wearwidget-provider>` xml tag.
 *
 * @property provider The component name of this widget provider.
 * @property label The label of this widget.
 * @property description The description of this widget.
 * @property icon The resource id of the icon for this widget.
 * @property containers The list of [ContainerInfo] supported for this widget provider.
 * @property group The name of the group this widget provider is associated with. Defaults to the
 *   fully qualified provider name.
 * @property isMultiInstanceSupported Whether this widget provider supports multiple instances.
 * @property configAction The configuration action for the widget provider.
 * @property minSchemaVersion The minimum schema version supported by this widget provider.
 * @property maxSchemaVersion The maximum schema version supported by this widget provider.
 *
 * TODO: populate default min schema version for remote compose widgets.
 * TODO: b/429979908 - Add xml example.
 */
internal class WearWidgetProviderInfo(
    public val provider: ComponentName,
    public val label: String,
    public val description: String,
    public @DrawableRes val icon: Int,
    public val containers: List<ContainerInfo>,
    public val group: String = provider.className,
    public val isMultiInstanceSupported: Boolean = false,
    public val configAction: String? = null,
    public val minSchemaVersion: SchemaVersion? = null,
    public val maxSchemaVersion: SchemaVersion? = null,
)

/**
 * Describes a container supported by a widget provider.
 *
 * A container is one representation of a widget, with a given size and shape.
 *
 * The fields in this class correspond to the fields in the `<container>` xml tag.
 *
 * @property type The type of this widget container.
 * @property previewImage The resource id of the preview image for this widget container.
 * @property label The override label for this widget container.
 * @property description The override description for this widget container.
 *
 * TODO: b/429979908 - Add xml example.
 */
internal class ContainerInfo(
    public val type: ContainerType,
    public @DrawableRes val previewImage: Int,
    public val label: String? = null,
    public val description: String? = null,
)

/** The container type of a widget. It defines the size and shape of the container. */
@JvmInline
internal value class ContainerType private constructor(private val value: Int) {
    public companion object {
        public val Large: ContainerType = ContainerType(1)
        public val Small: ContainerType = ContainerType(2)
    }
}

/**
 * The host schema version.
 *
 * @property major The major version of the schema.
 * @property minor The minor version of the schema.
 */
internal class SchemaVersion(public val major: Int, public val minor: Int)
