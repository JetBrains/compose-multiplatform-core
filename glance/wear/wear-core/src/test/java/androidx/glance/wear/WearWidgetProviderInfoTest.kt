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
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.XmlResourceParser
import androidx.glance.wear.WearWidgetProviderInfoXmlParser.parseWearWidgetProviderInfo
import androidx.glance.wear.core.test.R
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.xmlpull.v1.XmlPullParserException

@RunWith(RobolectricTestRunner::class)
class WearWidgetProviderInfoTest {
    private val context: Context = getApplicationContext()
    private val service = ComponentName(context, "test.class")

    @Test
    fun parseFromResource_parseProviderInfo() {
        val info =
            getXml(R.xml.wear_widget_provider_info_test)
                .parseWearWidgetProviderInfo(
                    context.resources,
                    service,
                    defaultPreferredContainerType = ContainerInfo.CONTAINER_TYPE_FULLSCREEN,
                    defaultGroup = "default.group",
                )
        assertThat(info.providerService).isEqualTo(service)
        assertThat(info.label).isEqualTo("test label")
        assertThat(info.description).isEqualTo("test description")
        assertThat(info.icon).isEqualTo(android.R.drawable.ic_delete)
        assertThat(info.preferredContainerType).isEqualTo(ContainerInfo.CONTAINER_TYPE_SMALL)
        assertThat(info.group).isEqualTo("test.group")
        assertThat(info.isMultiInstanceSupported).isTrue()
        assertThat(info.configIntentAction).isEqualTo("test.action")
        assertThat(info.minSchemaVersion?.major).isEqualTo(1)
        assertThat(info.minSchemaVersion?.minor).isEqualTo(200)
        assertThat(info.maxSchemaVersion?.major).isEqualTo(2)
        assertThat(info.maxSchemaVersion?.minor).isEqualTo(340)
        assertThat(info.containers).hasSize(2)
        assertThat(info.containers)
            .containsExactlyElementsIn(
                listOf(
                    ContainerInfo(
                        ContainerInfo.CONTAINER_TYPE_SMALL,
                        android.R.drawable.ic_dialog_alert,
                    ),
                    ContainerInfo(
                        ContainerInfo.CONTAINER_TYPE_LARGE,
                        android.R.drawable.ic_dialog_dialer,
                        label = "test label override for large",
                    ),
                )
            )
    }

    @Test
    fun parseFromService_validProviderInfo() {
        val serviceComponent =
            ComponentName(context, "androidx.glance.wear.test.TestGlanceWearWidgetService")
        val info = WearWidgetProviderInfo.parseFromService(context, serviceComponent)

        assertThat(info.providerService).isEqualTo(serviceComponent)
        assertThat(info.label).isEqualTo("test label")
        assertThat(info.description).isEqualTo("test description")
        assertThat(info.icon).isEqualTo(android.R.drawable.ic_delete)
        assertThat(info.preferredContainerType).isEqualTo(ContainerInfo.CONTAINER_TYPE_SMALL)
        assertThat(info.group).isEqualTo("test.group")
        assertThat(info.isMultiInstanceSupported).isTrue()
        assertThat(info.configIntentAction).isEqualTo("test.action")
        assertThat(info.minSchemaVersion?.major).isEqualTo(1)
        assertThat(info.minSchemaVersion?.minor).isEqualTo(200)
        assertThat(info.maxSchemaVersion?.major).isEqualTo(2)
        assertThat(info.maxSchemaVersion?.minor).isEqualTo(340)
        assertThat(info.containers).hasSize(2)
        assertThat(info.containers)
            .containsExactlyElementsIn(
                listOf(
                    ContainerInfo(
                        ContainerInfo.CONTAINER_TYPE_SMALL,
                        android.R.drawable.ic_dialog_alert,
                    ),
                    ContainerInfo(
                        ContainerInfo.CONTAINER_TYPE_LARGE,
                        android.R.drawable.ic_dialog_dialer,
                        label = "test label override for large",
                    ),
                )
            )
    }

    @Test(expected = PackageManager.NameNotFoundException::class)
    fun parseFromService_invalidMetaDataResource() {
        val serviceComponent =
            ComponentName(
                context,
                "androidx.glance.wear.test.TestGlanceWearWidgetServiceInvalidMetadata",
            )

        WearWidgetProviderInfo.parseFromService(context, serviceComponent)
    }

    @Test(expected = PackageManager.NameNotFoundException::class)
    fun parseFromService_noMetaData() {
        val serviceComponent =
            ComponentName(
                context,
                "androidx.glance.wear.test.TestGlanceWearWidgetServiceNoMetadata",
            )

        WearWidgetProviderInfo.parseFromService(context, serviceComponent)
    }

    @Test(expected = PackageManager.NameNotFoundException::class)
    fun parseFromService_invalidService() {
        val serviceComponent =
            ComponentName(context, "androidx.glance.wear.test.NonExistingService")

        WearWidgetProviderInfo.parseFromService(context, serviceComponent)
    }

    @Test
    fun parseFromResource_minimalProviderInfo() {
        val defaultPreferredContainerType = ContainerInfo.CONTAINER_TYPE_SMALL
        val defaultGroup = "some_group"
        val info =
            getXml(R.xml.wear_widget_provider_info_minimal)
                .parseWearWidgetProviderInfo(
                    context.resources,
                    service,
                    defaultPreferredContainerType,
                    defaultGroup,
                )

        assertThat(info.providerService).isEqualTo(service)
        assertThat(info.label).isEqualTo("")
        assertThat(info.description).isEqualTo("")
        assertThat(info.icon).isEqualTo(0)
        assertThat(info.preferredContainerType).isEqualTo(defaultPreferredContainerType)
        assertThat(info.group).isEqualTo(defaultGroup)
        assertThat(info.isMultiInstanceSupported).isFalse()
        assertThat(info.configIntentAction).isNull()
        assertThat(info.minSchemaVersion).isNull()
        assertThat(info.maxSchemaVersion).isNull()
        assertThat(info.containers).isEmpty()
    }

    @Test
    fun parseWearWidgetProviderInfo_unrecognizedAttributes() {
        val info =
            getXml(R.xml.wear_widget_provider_info_unrecognized)
                .parseWearWidgetProviderInfo(
                    context.resources,
                    service,
                    defaultPreferredContainerType = ContainerInfo.CONTAINER_TYPE_SMALL,
                    defaultGroup = "default.group",
                )

        assertThat(info.unrecognisedAttributes).hasSize(2)
        assertThat(info.unrecognisedAttributes).containsEntry("unrecognizedStr", "some string")
        assertThat(info.unrecognisedAttributes)
            .containsEntry("unrecognizedRef", "@${android.R.string.ok}")
    }

    @Test(expected = XmlPullParserException::class)
    fun parseWearWidgetProviderInfo_missingType() {
        getXml(R.xml.wear_widget_provider_info_missing_type)
            .parseWearWidgetProviderInfo(
                context.resources,
                service,
                defaultPreferredContainerType = ContainerInfo.CONTAINER_TYPE_SMALL,
                defaultGroup = "default.group",
            )
    }

    private fun getXml(resourceId: Int): XmlResourceParser = context.resources.getXml(resourceId)
}
