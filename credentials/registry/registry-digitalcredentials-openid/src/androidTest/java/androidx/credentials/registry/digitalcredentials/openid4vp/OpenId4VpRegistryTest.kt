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

package androidx.credentials.registry.digitalcredentials.openid4vp

import android.graphics.Bitmap
import androidx.credentials.registry.digitalcredentials.openid4vp.OpenId4VpDefaults.DEFAULT_MATCHER
import androidx.credentials.registry.digitalcredentials.sdjwt.SdJwtClaim
import androidx.credentials.registry.digitalcredentials.sdjwt.SdJwtEntry
import androidx.credentials.registry.provider.RegistryManager
import androidx.credentials.registry.provider.digitalcredentials.VerificationEntryDisplayProperties
import androidx.credentials.registry.provider.digitalcredentials.VerificationFieldDisplayProperties
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.google.common.truth.Truth.assertThat
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SmallTest
class OpenId4VpRegistryTest {

    private companion object {
        private val TEST_ICON_1 = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
        private val TEST_ICON_2 = Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_4444)
        private val TEST_ICON_3 = Bitmap.createBitmap(20, 10, Bitmap.Config.ARGB_4444)
    }

    @Test
    fun construction_emptyEntries() {
        val registry =
            OpenId4VpRegistry(
                credentialEntries = emptyList(),
                id = "id",
                intentAction =
                    "androidx.credentials.registry.digitalcredentials.openid4vp.IntentAction",
            )

        val (json, icons) = parseCredentialBytes(registry.credentials)
        assertThat(registry.id).isEqualTo("id")
        assertThat(registry.intentAction)
            .isEqualTo("androidx.credentials.registry.digitalcredentials.openid4vp.IntentAction")
        assertThat(json.getJSONObject("credentials").getJSONObject("dc+sd-jwt").length())
            .isEqualTo(0)
        assertThat(json.getJSONObject("credentials").getJSONObject("mso_mdoc").length())
            .isEqualTo(0)
        assertThat(icons).isEmpty()
        assertThat(registry.matcher).isEqualTo(DEFAULT_MATCHER)
    }

    @Test
    fun construction_singleSdJwt() {
        val claim1 =
            SdJwtClaim(
                path = listOf("claim1"),
                value = "value1",
                fieldDisplayPropertySet =
                    setOf(VerificationFieldDisplayProperties("Claim One", "Value One")),
            )
        val claim2 =
            SdJwtClaim(
                path = listOf("nested", "claim2"),
                value = null,
                fieldDisplayPropertySet = setOf(VerificationFieldDisplayProperties("Claim Two")),
            )
        val sdJwtEntryVerificationDisplay =
            VerificationEntryDisplayProperties("SD-JWT Credential", "For testing", TEST_ICON_1)
        val sdJwtEntry =
            SdJwtEntry(
                vct = "urn:eudi:pid:1",
                claims = listOf(claim1, claim2),
                entryDisplayPropertySet = setOf(sdJwtEntryVerificationDisplay),
                id = "sd-jwt-id",
            )

        val registry = OpenId4VpRegistry(credentialEntries = listOf(sdJwtEntry), id = "registry_id")

        assertThat(registry.id).isEqualTo("registry_id")
        assertThat(registry.matcher).isEqualTo(DEFAULT_MATCHER)
        assertThat(registry.intentAction).isEqualTo(RegistryManager.ACTION_GET_CREDENTIAL)
        val (json, icons) = parseCredentialBytes(registry.credentials)
        assertThat(json.has("credentials")).isTrue()
        val credentials = json.getJSONObject("credentials")
        assertThat(credentials.has("dc+sd-jwt")).isTrue()
        assertThat(credentials.has("mso_mdoc")).isTrue()
        assertThat(icons).hasSize(1)

        // Assert SD-JWT part
        val sdJwtCreds = credentials.getJSONObject("dc+sd-jwt")
        assertThat(sdJwtCreds.has(sdJwtEntry.vct)).isTrue()
        val sdJwtArray = sdJwtCreds.getJSONArray(sdJwtEntry.vct)
        assertThat(sdJwtArray.length()).isEqualTo(1)
        val sdJwtJson = sdJwtArray.getJSONObject(0)
        assertThat(sdJwtJson.getString("id")).isEqualTo(sdJwtEntry.id)

        val sdJwtDisplay = sdJwtJson.getJSONObject("display").getJSONObject("verification")
        assertThat(sdJwtDisplay.getString("title")).isEqualTo(sdJwtEntryVerificationDisplay.title)
        assertThat(sdJwtDisplay.getString("subtitle"))
            .isEqualTo(sdJwtEntryVerificationDisplay.subtitle)
        assertThat(sdJwtDisplay.getJSONObject("icon").getInt("length"))
            .isEqualTo(icons[sdJwtEntry.id]!!.size)

        val sdJwtPaths = sdJwtJson.getJSONObject("paths")
        val claim1Json = sdJwtPaths.getJSONObject("claim1")
        assertThat(claim1Json.getString("value")).isEqualTo(claim1.value)
        val claim1Display = claim1Json.getJSONObject("display").getJSONObject("verification")
        assertThat(claim1Display.getString("display"))
            .isEqualTo(
                (claim1.fieldDisplayPropertySet.first() as VerificationFieldDisplayProperties)
                    .displayName
            )
        assertThat(claim1Display.getString("display_value"))
            .isEqualTo(
                (claim1.fieldDisplayPropertySet.first() as VerificationFieldDisplayProperties)
                    .displayValue
            )

        val claim2Json = sdJwtPaths.getJSONObject("nested").getJSONObject("claim2")
        assertThat(claim2Json.has("value")).isFalse() // value is null
        val claim2Display = claim2Json.getJSONObject("display").getJSONObject("verification")
        assertThat(claim2Display.getString("display"))
            .isEqualTo(
                (claim2.fieldDisplayPropertySet.first() as VerificationFieldDisplayProperties)
                    .displayName
            )

        // Assert mdoc part
        val mdocCreds = credentials.getJSONObject("mso_mdoc")
        assertThat(mdocCreds.length()).isEqualTo(0)
    }

    @Test
    fun construction_multipleSdJwts() {
        val claim1 =
            SdJwtClaim(
                path = listOf("claim1"),
                value = "value1",
                fieldDisplayPropertySet =
                    setOf(VerificationFieldDisplayProperties("Claim One", "Value One")),
            )
        val claim2 =
            SdJwtClaim(
                path = listOf("nested", "claim2"),
                value = null,
                fieldDisplayPropertySet = setOf(VerificationFieldDisplayProperties("Claim Two")),
            )
        val sdJwtEntryVerificationDisplay1 =
            VerificationEntryDisplayProperties("SD-JWT Credential 1", null, TEST_ICON_1)
        val sdJwtEntry1 =
            SdJwtEntry(
                vct = "urn:eudi:pid:1",
                claims = listOf(claim1, claim2),
                entryDisplayPropertySet = setOf(sdJwtEntryVerificationDisplay1),
                id = "sd-jwt-id-1",
            )
        val claim3 =
            SdJwtClaim(
                path = listOf("claim3"),
                value = "value3",
                fieldDisplayPropertySet =
                    setOf(VerificationFieldDisplayProperties("Claim Three", null)),
            )
        val sdJwtEntryVerificationDisplay2 =
            VerificationEntryDisplayProperties("SD-JWT Credential 2", "subtitle 2", TEST_ICON_2)
        val sdJwtEntry2 =
            SdJwtEntry(
                vct = "urn:eudi:pid:1",
                claims = listOf(claim3),
                entryDisplayPropertySet = setOf(sdJwtEntryVerificationDisplay2),
                id = "sd-jwt-id-2",
            )
        val sdJwtEntryVerificationDisplay3 =
            VerificationEntryDisplayProperties("SD-JWT Credential 3", "subtitle 3", TEST_ICON_3)
        val sdJwtEntry3 =
            SdJwtEntry(
                vct = "urn:eudi:pid:2",
                claims = emptyList(),
                entryDisplayPropertySet = setOf(sdJwtEntryVerificationDisplay3),
                id = "sd-jwt-id-3",
            )

        val registry =
            OpenId4VpRegistry(
                credentialEntries = listOf(sdJwtEntry1, sdJwtEntry2, sdJwtEntry3),
                id = "registry_id",
                intentAction = "custom",
            )

        assertThat(registry.id).isEqualTo("registry_id")
        assertThat(registry.matcher).isEqualTo(DEFAULT_MATCHER)
        assertThat(registry.intentAction).isEqualTo("custom")
        val (json, icons) = parseCredentialBytes(registry.credentials)
        assertThat(json.has("credentials")).isTrue()
        val credentials = json.getJSONObject("credentials")
        assertThat(credentials.has("dc+sd-jwt")).isTrue()
        assertThat(credentials.has("mso_mdoc")).isTrue()
        assertThat(icons).hasSize(3)

        // Assert SD-JWT part
        val sdJwtCreds = credentials.getJSONObject("dc+sd-jwt")
        val pidV1Array = sdJwtCreds.getJSONArray("urn:eudi:pid:1")
        assertThat(pidV1Array.length()).isEqualTo(2)
        val pidV2Array = sdJwtCreds.getJSONArray("urn:eudi:pid:2")
        assertThat(pidV2Array.length()).isEqualTo(1)

        val sdJwtJson1 = pidV1Array.getJSONObject(0)
        assertThat(sdJwtJson1.getString("id")).isEqualTo(sdJwtEntry1.id)

        val sdJwtDisplay1 = sdJwtJson1.getJSONObject("display").getJSONObject("verification")
        assertThat(sdJwtDisplay1.getString("title")).isEqualTo(sdJwtEntryVerificationDisplay1.title)
        assertThat(sdJwtDisplay1.has("subtitle")).isFalse()
        assertThat(sdJwtDisplay1.getJSONObject("icon").getInt("length"))
            .isEqualTo(icons[sdJwtEntry1.id]!!.size)

        val claim1Json = sdJwtJson1.getJSONObject("paths").getJSONObject("claim1")
        assertThat(claim1Json.getString("value")).isEqualTo(claim1.value)
        val claim1Display = claim1Json.getJSONObject("display").getJSONObject("verification")
        assertThat(claim1Display.getString("display"))
            .isEqualTo(
                (claim1.fieldDisplayPropertySet.first() as VerificationFieldDisplayProperties)
                    .displayName
            )
        assertThat(claim1Display.getString("display_value"))
            .isEqualTo(
                (claim1.fieldDisplayPropertySet.first() as VerificationFieldDisplayProperties)
                    .displayValue
            )

        val claim2Json =
            sdJwtJson1.getJSONObject("paths").getJSONObject("nested").getJSONObject("claim2")
        assertThat(claim2Json.has("value")).isFalse() // value is null
        val claim2Display = claim2Json.getJSONObject("display").getJSONObject("verification")
        assertThat(claim2Display.getString("display"))
            .isEqualTo(
                (claim2.fieldDisplayPropertySet.first() as VerificationFieldDisplayProperties)
                    .displayName
            )

        val sdJwtJson2 = pidV1Array.getJSONObject(1)
        assertThat(sdJwtJson2.getString("id")).isEqualTo(sdJwtEntry2.id)

        val sdJwtDisplay2 = sdJwtJson2.getJSONObject("display").getJSONObject("verification")
        assertThat(sdJwtDisplay2.getString("title")).isEqualTo(sdJwtEntryVerificationDisplay2.title)
        assertThat(sdJwtDisplay2.getString("subtitle"))
            .isEqualTo(sdJwtEntryVerificationDisplay2.subtitle)
        assertThat(sdJwtDisplay2.getJSONObject("icon").getInt("length"))
            .isEqualTo(icons[sdJwtEntry2.id]!!.size)

        val claim3Json = sdJwtJson2.getJSONObject("paths").getJSONObject("claim3")
        assertThat(claim3Json.getString("value")).isEqualTo(claim3.value)
        val claim3Display = claim3Json.getJSONObject("display").getJSONObject("verification")
        assertThat(claim3Display.getString("display"))
            .isEqualTo(
                (claim3.fieldDisplayPropertySet.first() as VerificationFieldDisplayProperties)
                    .displayName
            )
        assertThat(claim3Display.has("display_value")).isFalse()

        val sdJwtJson3 = pidV2Array.getJSONObject(0)
        assertThat(sdJwtJson3.getString("id")).isEqualTo(sdJwtEntry3.id)

        val sdJwtDisplay3 = sdJwtJson3.getJSONObject("display").getJSONObject("verification")
        assertThat(sdJwtDisplay3.getString("title")).isEqualTo(sdJwtEntryVerificationDisplay3.title)
        assertThat(sdJwtDisplay3.getString("subtitle"))
            .isEqualTo(sdJwtEntryVerificationDisplay3.subtitle)
        assertThat(sdJwtDisplay3.getJSONObject("icon").getInt("length"))
            .isEqualTo(icons[sdJwtEntry3.id]!!.size)

        // Assert mdoc part
        val mdocCreds = credentials.getJSONObject("mso_mdoc")
        assertThat(mdocCreds.length()).isEqualTo(0)
    }

    /** Helper to parse the custom byte format into a JSON object and a map of icon bytes. */
    private fun parseCredentialBytes(bytes: ByteArray): Pair<JSONObject, Map<String, ByteArray>> {
        val inputStream = ByteArrayInputStream(bytes)

        // First 4 bytes are the offset to the JSON part
        val offsetBytes = ByteArray(4)
        inputStream.read(offsetBytes)
        val jsonOffset = ByteBuffer.wrap(offsetBytes).order(ByteOrder.LITTLE_ENDIAN).int

        // Read the JSON part
        val jsonBytes = bytes.sliceArray(jsonOffset until bytes.size)
        val json = JSONObject(String(jsonBytes, Charsets.UTF_8))

        // Read the icons
        val icons = mutableMapOf<String, ByteArray>()
        val creds = json.getJSONObject("credentials")
        val sdJwtCreds = creds.optJSONObject("dc+sd-jwt")
        val mdocCreds = creds.optJSONObject("mso_mdoc")

        val allCreds = mutableListOf<JSONObject>()
        sdJwtCreds?.keys()?.forEach { key ->
            sdJwtCreds.getJSONArray(key).let {
                for (i in 0 until it.length()) allCreds.add(it.getJSONObject(i))
            }
        }
        mdocCreds?.keys()?.forEach { key ->
            mdocCreds.getJSONArray(key).let {
                for (i in 0 until it.length()) allCreds.add(it.getJSONObject(i))
            }
        }

        for (cred in allCreds) {
            val id = cred.getString("id")
            val iconInfo =
                cred.optJSONObject("display")?.optJSONObject("verification")?.optJSONObject("icon")
            if (iconInfo != null) {
                val start = iconInfo.getInt("start")
                val length = iconInfo.getInt("length")
                val iconBytes = bytes.sliceArray(start until start + length)
                icons[id] = iconBytes
            }
        }

        return Pair(json, icons)
    }
}
