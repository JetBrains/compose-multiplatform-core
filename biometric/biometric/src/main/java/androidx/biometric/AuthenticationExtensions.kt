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
@file:JvmName("AuthenticationUtils")

package androidx.biometric

import android.annotation.SuppressLint
import android.graphics.Bitmap
import androidx.biometric.AuthenticationRequest.Biometric
import androidx.biometric.BiometricPrompt.AuthenticationCallback
import androidx.biometric.BiometricPrompt.CryptoObject
import androidx.biometric.BiometricPrompt.PromptInfo
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import java.util.concurrent.Executor

/**
 * Register a request to start an authentication for result.
 *
 * This *must* be called unconditionally, as part of initialization path, typically as a field
 * initializer of an Activity.
 *
 * Note that if multiple calls to this method are made within a single Fragment or Activity, only
 * the callback registered by the last invocation will be saved and receive authentication results.
 * This can result in unexpected behavior if you intend to manage multiple independent
 * authentication flows. It is strongly recommended to avoid multiple calls to this method in such
 * scenarios.
 *
 * @param onAuthFailedCallback the optional callback to be called on the main thread when
 *   authentication fails intermediately. This is not a terminal auth result, so it could happen
 *   multiple times.
 * @param resultCallback the callback to be called on the main thread when authentication result is
 *   available
 * @return the launcher that can be used to start the authentication.
 * @sample androidx.biometric.samples.activitySample
 */
@SuppressWarnings("ExecutorRegistration")
@JvmOverloads
public fun FragmentActivity.registerForAuthenticationResult(
    onAuthFailedCallback: () -> Unit = {},
    resultCallback: AuthenticationResultCallback
): AuthenticationResultLauncher {
    return AuthenticationResultRegistry(activity = this)
        .register(onAuthFailedCallback, resultCallback)
}

/**
 * Register a request to start an authentication for result.
 *
 * This *must* be called unconditionally, as part of initialization path, typically as a field
 * initializer of an Fragment.
 *
 * Note that if multiple calls to this method are made within a single Fragment or Activity, only
 * the callback registered by the last invocation will be saved and receive authentication results.
 * This can result in unexpected behavior if you intend to manage multiple independent
 * authentication flows. It is strongly recommended to avoid multiple calls to this method in such
 * scenarios.
 *
 * @param onAuthFailedCallback the optional callback to be called on the main thread when
 *   authentication fails intermediately. This is not a terminal auth result, and could happen
 *   multiple times.
 * @param resultCallback the callback to be called on the main thread when authentication result is
 *   available
 * @return the launcher that can be used to start the authentication.
 * @sample androidx.biometric.samples.fragmentSample
 */
@SuppressWarnings("ExecutorRegistration")
@JvmOverloads
public fun Fragment.registerForAuthenticationResult(
    onAuthFailedCallback: () -> Unit = {},
    resultCallback: AuthenticationResultCallback
): AuthenticationResultLauncher {
    return AuthenticationResultRegistry(fragment = this)
        .register(onAuthFailedCallback, resultCallback)
}

/** Creates a [BiometricPrompt] with given [AuthenticationCallback] */
internal fun FragmentActivity.createBiometricPrompt(
    executor: Executor?,
    callback: AuthenticationCallback,
): BiometricPrompt {
    return if (executor == null) {
        BiometricPrompt(this, callback)
    } else {
        BiometricPrompt(this, executor, callback)
    }
}

/** Creates a [BiometricPrompt] with given [AuthenticationCallback] */
internal fun Fragment.createBiometricPrompt(
    executor: Executor?,
    callback: AuthenticationCallback,
): BiometricPrompt {
    return if (executor == null) {
        BiometricPrompt(this, callback)
    } else {
        BiometricPrompt(this, executor, callback)
    }
}

/** Shows the authentication prompt to the user with biometric and/or device credential. */
@SuppressLint("MissingPermission")
internal fun BiometricPrompt.authInternal(
    title: String,
    authFallback: Biometric.Fallback? = null,
    minBiometricStrength: Biometric.Strength? = null,
    subtitle: String? = null,
    content: AuthenticationRequest.BodyContent? = null,
    isConfirmationRequired: Boolean = true,
    cryptoObjectForCredentialOnly: CryptoObject? = null,
    logoBitmap: Bitmap? = null,
    logoRes: Int = 0,
    logoDescription: String? = null,
) {
    val builder = PromptInfo.Builder()
    // Set authenticators and fallbacks
    var authType =
        minBiometricStrength?.toAuthenticationType()
            ?: BiometricManager.Authenticators.DEVICE_CREDENTIAL
    when (authFallback) {
        is Biometric.Fallback.DeviceCredential ->
            authType = authType or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        is Biometric.Fallback.NegativeButton ->
            builder.setNegativeButtonText(authFallback.negativeButtonText)
        else -> {}
    }

    // Set body content
    when (content) {
        is AuthenticationRequest.BodyContent.PlainText ->
            builder.setDescription(content.description)
        is AuthenticationRequest.BodyContent.VerticalList -> {
            val contentViewBuilder = PromptVerticalListContentView.Builder()
            content.items.forEach { contentViewBuilder.addListItem(it) }
            content.description?.let { contentViewBuilder.setDescription(content.description) }
            builder.setContentView(contentViewBuilder.build())
        }
        is AuthenticationRequest.BodyContent.ContentViewWithMoreOptionsButton -> {
            val contentViewBuilder = PromptContentViewWithMoreOptionsButton.Builder()
            content.description?.let { contentViewBuilder.setDescription(content.description) }
            builder.setContentView(contentViewBuilder.build())
        }
        else -> {}
    }

    // Set logo
    if (logoRes != 0) {
        builder.setLogoRes(logoRes)
    } else if (logoBitmap != null) {
        builder.setLogoBitmap(logoBitmap)
    }
    if (logoDescription != null) {
        builder.setLogoDescription(logoDescription)
    }

    // Set other configurations
    builder
        .setAllowedAuthenticators(authType)
        .setTitle(title)
        .setSubtitle(subtitle)
        .setConfirmationRequired(isConfirmationRequired)

    val promptInfo = builder.build()

    val cryptoObject =
        when (minBiometricStrength) {
            is Biometric.Strength.Class3 -> minBiometricStrength.cryptoObject
            null -> cryptoObjectForCredentialOnly
            else -> null
        }

    if (cryptoObject == null) {
        authenticate(promptInfo)
    } else {
        authenticate(promptInfo, cryptoObject)
    }
}
