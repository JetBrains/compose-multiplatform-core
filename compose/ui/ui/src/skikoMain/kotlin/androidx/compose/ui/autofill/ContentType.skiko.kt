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

package androidx.compose.ui.autofill

import kotlin.jvm.JvmInline

// TODO https://youtrack.jetbrains.com/issue/CMP-7154/Adopt-Autofill-semantic-properties

actual sealed interface ContentType {
    actual companion object {
        actual val Username: ContentType = PlatformContentType(1L shl 0)
        actual val Password: ContentType = PlatformContentType(1L shl 1)
        actual val EmailAddress: ContentType = PlatformContentType(1L shl 2)
        actual val NewUsername: ContentType = PlatformContentType(1L shl 3)
        actual val NewPassword: ContentType = PlatformContentType(1L shl 4)
        actual val PostalAddress: ContentType = PlatformContentType(1L shl 5)
        actual val PostalCode: ContentType = PlatformContentType(1L shl 6)
        actual val CreditCardNumber: ContentType = PlatformContentType(1L shl 7)
        actual val CreditCardSecurityCode: ContentType = PlatformContentType(1L shl 8)
        actual val CreditCardExpirationDate: ContentType = PlatformContentType(1L shl 9)
        actual val CreditCardExpirationMonth: ContentType = PlatformContentType(1L shl 10)
        actual val CreditCardExpirationYear: ContentType = PlatformContentType(1L shl 11)
        actual val CreditCardExpirationDay: ContentType = PlatformContentType(1L shl 12)
        actual val AddressCountry: ContentType = PlatformContentType(1L shl 13)
        actual val AddressRegion: ContentType = PlatformContentType(1L shl 14)
        actual val AddressLocality: ContentType = PlatformContentType(1L shl 15)
        actual val AddressStreet: ContentType = PlatformContentType(1L shl 16)
        actual val AddressAuxiliaryDetails: ContentType = PlatformContentType(1L shl 17)
        actual val PostalCodeExtended: ContentType = PlatformContentType(1L shl 18)
        actual val PersonFullName: ContentType = PlatformContentType(1L shl 19)
        actual val PersonFirstName: ContentType = PlatformContentType(1L shl 20)
        actual val PersonLastName: ContentType = PlatformContentType(1L shl 21)
        actual val PersonMiddleName: ContentType = PlatformContentType(1L shl 22)
        actual val PersonMiddleInitial: ContentType = PlatformContentType(1L shl 23)
        actual val PersonNamePrefix: ContentType = PlatformContentType(1L shl 24)
        actual val PersonNameSuffix: ContentType = PlatformContentType(1L shl 25)
        actual val PhoneNumber: ContentType = PlatformContentType(1L shl 26)
        actual val PhoneNumberDevice: ContentType = PlatformContentType(1L shl 27)
        actual val PhoneCountryCode: ContentType = PlatformContentType(1L shl 28)
        actual val PhoneNumberNational: ContentType = PlatformContentType(1L shl 29)
        actual val Gender: ContentType = PlatformContentType(1L shl 30)
        actual val BirthDateFull: ContentType = PlatformContentType(1L shl 31)
        actual val BirthDateDay: ContentType = PlatformContentType(1L shl 32)
        actual val BirthDateMonth: ContentType = PlatformContentType(1L shl 33)
        actual val BirthDateYear: ContentType = PlatformContentType(1L shl 34)
        actual val SmsOtpCode: ContentType = PlatformContentType(1L shl 35)
    }

    actual operator fun plus(other: ContentType): ContentType
}

@JvmInline
private value class PlatformContentType(val type: Long) : ContentType {
    override fun plus(other: ContentType): ContentType {
        other as PlatformContentType
        return PlatformContentType(type or other.type)
    }
}
