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

package androidx.biometric

import androidx.biometric.BiometricPrompt.AuthenticationCallback
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.Lifecycle.Event.*
import androidx.lifecycle.LifecycleEventObserver

/**
 * A registry that stores [auth result callbacks][AuthenticationResultCallback] for
 * [registered calls][registerForAuthenticationResult].
 */
internal class AuthenticationResultRegistry(
    private var activity: FragmentActivity? = null,
    private var fragment: Fragment? = null
) {

    fun register(
        onAuthFailedCallback: () -> Unit = {},
        resultCallback: AuthenticationResultCallback
    ): AuthenticationResultLauncher {
        val callback =
            object : AuthenticationCallback() {
                override fun onAuthenticationFailed() {
                    onAuthFailedCallback()
                }

                override fun onAuthenticationSucceeded(
                    result: BiometricPrompt.AuthenticationResult
                ) {
                    resultCallback.onAuthResult(
                        AuthenticationResult.Success(result.cryptoObject, result.authenticationType)
                    )
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    resultCallback.onAuthResult(AuthenticationResult.Error(errorCode, errString))
                }
            }

        var biometricPrompt: BiometricPrompt? = null

        val lifecycleContainer =
            LifecycleContainer(if (activity != null) activity!!.lifecycle else fragment!!.lifecycle)
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                ON_START -> {
                    biometricPrompt =
                        if (activity != null) {
                            activity!!.createBiometricPrompt({ it.run() }, callback)
                        } else {
                            fragment!!.createBiometricPrompt({ it.run() }, callback)
                        }
                }
                ON_STOP -> {
                    // TODO(b/349213716): remove callback from BiometricViewModel
                }
                ON_DESTROY -> {
                    // TODO(b/349213716): remove callback from BiometricViewModel
                    lifecycleContainer.clearObservers()
                }
                else -> {}
            }
        }
        lifecycleContainer.addObserver(observer)

        return object : AuthenticationResultLauncher {
            override fun launch(input: AuthenticationRequest) {
                biometricPrompt?.let { onLaunch(input, it) }
            }

            override fun cancel() {
                biometricPrompt?.cancelAuthentication()
                lifecycleContainer.clearObservers()
            }
        }
    }

    fun onLaunch(input: AuthenticationRequest, biometricPrompt: BiometricPrompt) {
        if (input is AuthenticationRequest.Biometric) {
            biometricPrompt.authInternal(
                title = input.title,
                authFallback = input.authFallback,
                minBiometricStrength = input.minStrength,
                subtitle = input.subtitle,
                content = input.content,
                isConfirmationRequired = input.isConfirmationRequired,
                logoBitmap = input.logoBitmap,
                logoRes = input.logoRes,
                logoDescription = input.logoDescription
            )
        } else if (input is AuthenticationRequest.Credential) {
            biometricPrompt.authInternal(
                title = input.title,
                subtitle = input.subtitle,
                content = input.content,
                cryptoObjectForCredentialOnly = input.cryptoObject,
            )
        }
    }

    private class LifecycleContainer(val lifecycle: Lifecycle) {
        private val observers = mutableListOf<LifecycleEventObserver>()

        fun addObserver(observer: LifecycleEventObserver) {
            lifecycle.addObserver(observer)
            observers.add(observer)
        }

        fun clearObservers() {
            observers.forEach { observer -> lifecycle.removeObserver(observer) }
            observers.clear()
        }
    }
}
