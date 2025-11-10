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

package androidx.biometric.internal

import android.app.Application
import android.app.KeyguardManager
import android.content.Context
import android.hardware.biometrics.BiometricManager
import android.os.Build
import androidx.biometric.BiometricPrompt
import androidx.biometric.internal.data.FakeAuthenticationStateRepository
import androidx.biometric.internal.data.FakePromptConfigRepository
import androidx.biometric.internal.viewmodel.AuthenticationViewModel
import androidx.biometric.utils.AuthenticatorUtils
import androidx.biometric.utils.BiometricErrorData
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import java.util.concurrent.Executor
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowApplication
import org.robolectric.shadows.ShadowKeyguardManager

@RunWith(RobolectricTestRunner::class)
class AuthenticationHandlerFingerprintManagerTest {

    private lateinit var context: Application
    private lateinit var shadowApplication: ShadowApplication
    private lateinit var shadowKeyguardManager: ShadowKeyguardManager
    private lateinit var keyguardManager: KeyguardManager
    private lateinit var testLifecycleOwner: TestLifecycleOwner

    private lateinit var authenticationHandler: AuthenticationHandlerFingerprintManager

    private val promptRepository = FakePromptConfigRepository()
    private val authRepository = FakeAuthenticationStateRepository()
    private val viewModel: AuthenticationViewModel =
        AuthenticationViewModel(promptRepository, authRepository)
    private val confirmCredentialActivityLauncher: Runnable = mock()
    private val clientExecutor: Executor = mock()
    private val clientAuthenticationCallback: BiometricPrompt.AuthenticationCallback = mock()
    private val runnableCaptor: ArgumentCaptor<Runnable> =
        ArgumentCaptor.forClass(Runnable::class.java)

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        shadowApplication = shadowOf(context)

        keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        shadowKeyguardManager = shadowOf(keyguardManager)

        testLifecycleOwner = TestLifecycleOwner()
        testLifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_START)

        authenticationHandler =
            AuthenticationHandlerFingerprintManager(
                context,
                testLifecycleOwner,
                viewModel,
                confirmCredentialActivityLauncher,
                clientExecutor,
                clientAuthenticationCallback,
            )
    }

    @Test
    fun testCancelAuthentication_whenIgnoringCancel_doesNothing() {
        viewModel.isIgnoringCancel = true
        authenticationHandler.cancelAuthentication(CanceledFrom.USER)

        assertThat(shadowApplication.broadcastIntents).isEmpty()
    }

    @Test
    @Config(maxSdk = Build.VERSION_CODES.P)
    fun testOnAuthenticationError_lockoutWithDeviceCredential_showsKeyguard() {
        shadowKeyguardManager.setIsDeviceSecure(true)
        authenticationHandler.authenticate(
            getPromptInfo(
                BiometricManager.Authenticators.DEVICE_CREDENTIAL or
                    BiometricManager.Authenticators.BIOMETRIC_WEAK
            ),
            null,
        )

        viewModel.setAuthenticationError(
            BiometricErrorData(BiometricPrompt.ERROR_LOCKOUT, "Lockout")
        )

        verify(confirmCredentialActivityLauncher).run()
    }

    @Test
    fun testOnAuthenticationError_canceledFromClient_sendsErrorToClient() {
        authenticationHandler.authenticate(getPromptInfo(), null)
        viewModel.canceledFrom = CanceledFrom.CLIENT
        viewModel.setAuthenticationError(
            BiometricErrorData(BiometricPrompt.ERROR_CANCELED, "Canceled")
        )

        verify(clientExecutor).execute(runnableCaptor.capture())
        runnableCaptor.value.run()
        verify(clientAuthenticationCallback)
            .onAuthenticationError(eq(BiometricPrompt.ERROR_CANCELED), any())
    }

    @Test
    fun testOnAuthenticationError_canceledFromUser_doesNotSendErrorToClient() {
        authenticationHandler.authenticate(getPromptInfo(), null)
        viewModel.canceledFrom = CanceledFrom.USER
        viewModel.setAuthenticationError(
            BiometricErrorData(BiometricPrompt.ERROR_CANCELED, "Canceled")
        )

        verify(clientExecutor, never()).execute(runnableCaptor.capture())
    }

    @Test
    fun testOnAuthenticationError_otherError_sendsErrorToClient() {
        authenticationHandler.authenticate(getPromptInfo(), null)
        viewModel.setAuthenticationError(
            BiometricErrorData(BiometricPrompt.ERROR_HW_UNAVAILABLE, "HW unavailable")
        )

        verify(clientExecutor).execute(runnableCaptor.capture())
        runnableCaptor.value.run()
        verify(clientAuthenticationCallback)
            .onAuthenticationError(eq(BiometricPrompt.ERROR_HW_UNAVAILABLE), any())
    }

    private fun getPromptInfo(
        authenticators: Int = BiometricManager.Authenticators.BIOMETRIC_WEAK
    ): BiometricPrompt.PromptInfo {
        val builder = BiometricPrompt.PromptInfo.Builder().setTitle("test")
        if (!AuthenticatorUtils.isDeviceCredentialAllowed(authenticators)) {
            builder.setNegativeButtonText("test")
        }
        builder.setAllowedAuthenticators(authenticators)
        return builder.build()
    }
}
