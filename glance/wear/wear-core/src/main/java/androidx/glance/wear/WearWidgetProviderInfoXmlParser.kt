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
import android.content.res.Resources
import android.content.res.XmlResourceParser
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException

internal object WearWidgetProviderInfoXmlParser {
    private const val TAG_PROVIDER = "wearwidget-provider"
    private const val TAG_CONTAINER = "container"
    private const val ATTR_LABEL = "label"
    private const val ATTR_DESCRIPTION = "description"
    private const val ATTR_ICON = "icon"
    private const val ATTR_PREFERRED_TYPE = "preferredType"
    private const val ATTR_GROUP = "group"
    private const val ATTR_IS_MULTI_INSTANCE_SUPPORTED = "isMultiInstanceSupported"
    private const val ATTR_CONFIG_INTENT_ACTION = "configIntentAction"
    private const val ATTR_MIN_SCHEMA_VERSION = "minSchemaVersion"
    private const val ATTR_MAX_SCHEMA_VERSION = "maxSchemaVersion"
    private const val ATTR_TYPE = "type"
    private const val ATTR_PREVIEW_IMAGE = "previewImage"

    private val NAMESPACE_DISABLED: String? = null

    @Throws(XmlPullParserException::class)
    internal fun XmlResourceParser.parseWearWidgetProviderInfo(
        resources: Resources,
        providerService: ComponentName,
        @ContainerInfo.ContainerType defaultPreferredContainerType: Int,
        defaultGroup: String,
    ): WearWidgetProviderInfo {
        while (this.next() != XmlPullParser.END_DOCUMENT) {
            if (this.eventType == XmlPullParser.START_TAG && this.name == TAG_PROVIDER) {
                return this.parseWearWidgetProviderTag(
                    resources,
                    providerService,
                    defaultPreferredContainerType,
                    defaultGroup,
                )
            }
        }
        throw XmlPullParserException("No <$TAG_PROVIDER> tag found in XML")
    }

    @Throws(XmlPullParserException::class)
    internal fun XmlResourceParser.parseWearWidgetProviderTag(
        resources: Resources,
        providerService: ComponentName,
        @ContainerInfo.ContainerType defaultPreferredContainerType: Int,
        defaultGroup: String,
    ): WearWidgetProviderInfo {
        // TODO(b/429979908): handle string resources for label and description. This should be done
        // similar to PackageItemInfo.loadLabel().
        // TODO(b/429979908): If not present, take label, description and icon from service tag.
        val label = getAttributeValue(NAMESPACE_DISABLED, ATTR_LABEL) ?: ""
        val description = getAttributeValue(NAMESPACE_DISABLED, ATTR_DESCRIPTION) ?: ""
        val icon = getAttributeResourceValue(NAMESPACE_DISABLED, ATTR_ICON, Resources.ID_NULL)
        val preferredContainerType =
            parseContainerTypeAttr(resources, ATTR_PREFERRED_TYPE, defaultPreferredContainerType)
        val group = getAttributeValue(NAMESPACE_DISABLED, ATTR_GROUP) ?: defaultGroup
        val isMultiInstanceSupported =
            getAttributeBooleanValue(NAMESPACE_DISABLED, ATTR_IS_MULTI_INSTANCE_SUPPORTED, false)
        val configIntentAction = getAttributeValue(NAMESPACE_DISABLED, ATTR_CONFIG_INTENT_ACTION)
        val minSchemaVersion =
            getAttributeValue(NAMESPACE_DISABLED, ATTR_MIN_SCHEMA_VERSION)?.let {
                parseSchemaVersion(it)
            }
        val maxSchemaVersion =
            getAttributeValue(NAMESPACE_DISABLED, ATTR_MAX_SCHEMA_VERSION)?.let {
                parseSchemaVersion(it)
            }
        val unrecognisedAttributes = mutableMapOf<String, String>()
        val knownAttributes =
            setOf(
                ATTR_LABEL,
                ATTR_DESCRIPTION,
                ATTR_ICON,
                ATTR_PREFERRED_TYPE,
                ATTR_GROUP,
                ATTR_IS_MULTI_INSTANCE_SUPPORTED,
                ATTR_CONFIG_INTENT_ACTION,
                ATTR_MIN_SCHEMA_VERSION,
                ATTR_MAX_SCHEMA_VERSION,
            )
        for (i in 0 until attributeCount) {
            val attrName = getAttributeName(i)
            if (attrName !in knownAttributes) {
                unrecognisedAttributes[attrName] = getAttributeValue(i)
            }
        }

        val containers = mutableListOf<ContainerInfo>()
        val providerDepth = depth
        while (next() != XmlPullParser.END_TAG || depth > providerDepth) {
            if (eventType != XmlPullParser.START_TAG) {
                continue
            }
            if (name == TAG_CONTAINER) {
                containers.add(parseContainerInfo(resources))
            }
        }

        return WearWidgetProviderInfo(
            providerService = providerService,
            label = label,
            description = description,
            icon = icon,
            containers = containers,
            preferredContainerType = preferredContainerType,
            group = group,
            isMultiInstanceSupported = isMultiInstanceSupported,
            configIntentAction = configIntentAction,
            minSchemaVersion = minSchemaVersion,
            maxSchemaVersion = maxSchemaVersion,
            unrecognisedAttributes = unrecognisedAttributes,
        )
    }

    @Throws(XmlPullParserException::class)
    private fun XmlResourceParser.parseContainerInfo(resources: Resources): ContainerInfo {
        val type = parseContainerTypeAttr(resources, ATTR_TYPE)
        val previewImage =
            getAttributeResourceValue(NAMESPACE_DISABLED, ATTR_PREVIEW_IMAGE, Resources.ID_NULL)
        // TODO: handle string resources for label and description
        val label = getAttributeValue(NAMESPACE_DISABLED, ATTR_LABEL)
        val description = getAttributeValue(NAMESPACE_DISABLED, ATTR_DESCRIPTION)
        return ContainerInfo(
            type = type,
            previewImage = previewImage,
            label = label,
            description = description,
        )
    }

    internal fun parseSchemaVersion(value: String): SchemaVersion {
        val parts = value.split('.')
        if (parts.size != 2) {
            throw IllegalArgumentException("Invalid schema version format: $value")
        }
        return try {
            val minor = parts[1].padEnd(3, '0').toInt()
            SchemaVersion(parts[0].toInt(), minor)
        } catch (e: NumberFormatException) {
            throw IllegalArgumentException("Invalid schema version format: $value", e)
        }
    }

    @Throws(XmlPullParserException::class)
    private fun XmlResourceParser.parseContainerTypeAttr(
        resources: Resources,
        attrName: String,
        @ContainerInfo.ContainerType defaultValue: Int? = null,
    ): Int {
        val attrResId = getAttributeResourceValue(NAMESPACE_DISABLED, attrName, Resources.ID_NULL)
        if (attrResId != Resources.ID_NULL) {
            try {
                return resources.getInteger(attrResId)
            } catch (e: Resources.NotFoundException) {
                throw XmlPullParserException("Invalid container type resource", this, e)
            }
        }
        return getAttributeValue(NAMESPACE_DISABLED, attrName)?.toIntOrNull()
            ?: defaultValue
            ?: throw XmlPullParserException("Failed to parse Container Type for $attrName")
    }
}
