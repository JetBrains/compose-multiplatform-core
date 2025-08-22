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

import android.app.Activity
import android.app.Application
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import androidx.biometric.BiometricPrompt
import androidx.biometric.utils.BiometricErrorData
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.intent.Intents
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.MoreExecutors
import java.util.concurrent.Executor
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.junit.MockitoJUnit
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowKeyguardManager

@RunWith(RobolectricTestRunner::class)
class AuthenticationHandlerKeyguardManagerTest {
    @get:Rule val mocks = MockitoJUnit.rule()
    private lateinit var context: Application
    private val viewModel: BiometricViewModel = BiometricViewModel()
    private val mockLauncherRunnable: Runnable = mock()
    private val mockCallback: BiometricPrompt.AuthenticationCallback = mock()
    private val testExecutor: Executor = MoreExecutors.directExecutor()

    private val mockViewModelStoreOwner: ViewModelStoreOwner = mock()
    private val viewModelStore = ViewModelStore()
    private lateinit var shadowKeyguardManager: ShadowKeyguardManager
    private lateinit var keyguardManager: KeyguardManager

    private lateinit var testLifecycleOwner: TestLifecycleOwner
    private lateinit var handler: AuthenticationHandlerKeyguardManager

    private class TestLifecycleOwner : LifecycleOwner {
        private val lifecycleRegistry = LifecycleRegistry(this)

        fun handleLifecycleEvent(event: Lifecycle.Event) =
            lifecycleRegistry.handleLifecycleEvent(event)

        override val lifecycle: Lifecycle = lifecycleRegistry
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        testLifecycleOwner = TestLifecycleOwner()
        testLifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)

        // TODO(b/178855209): Add unit tests for AuthenticationHandlerKeyguardManager and
        // AuthenticationManager
        handler =
            AuthenticationHandlerKeyguardManager(
                context,
                testLifecycleOwner,
                viewModel,
                mockLauncherRunnable,
                testExecutor,
                mockCallback,
            )

        whenever(mockViewModelStoreOwner.viewModelStore).thenReturn(viewModelStore)

        keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        shadowKeyguardManager = shadowOf(keyguardManager)

        Intents.init()
    }

    @After
    fun tearDown() {
        Intents.release()
        viewModelStore.clear()
    }

    @Test
    fun launchConfirmCredentialActivity_withLauncher_launchesIntent() {
        shadowKeyguardManager.setIsDeviceSecure(true)

        val mockLauncher: ActivityResultLauncher<Intent> = mock()

        context.launchConfirmCredentialActivity(mockViewModelStoreOwner, mockLauncher)

        val intentCaptor = argumentCaptor<Intent>()
        verify(mockLauncher).launch(intentCaptor.capture())
        assertThat(intentCaptor.lastValue.action)
            .isEqualTo("android.app.action.CONFIRM_DEVICE_CREDENTIAL")
        assertThat(intentCaptor.lastValue.flags and Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
            .isNotEqualTo(0)
        assertThat(intentCaptor.lastValue.flags and Intent.FLAG_ACTIVITY_NEW_DOCUMENT)
            .isNotEqualTo(0)
    }

    @Test
    fun launchConfirmCredentialActivity_withLauncher_noIntent_doesNotLaunch() {
        // This makes getConfirmCredentialIntent return null
        shadowKeyguardManager.setIsDeviceSecure(false)
        val mockLauncher: ActivityResultLauncher<Intent> = mock()

        context.launchConfirmCredentialActivity(mockViewModelStoreOwner, mockLauncher)

        verify(mockLauncher, never()).launch(any())
    }

    @Test
    fun handleConfirmCredentialResult_resultOk_wasUsingKeyguardManager() {
        val captor = argumentCaptor<BiometricPrompt.AuthenticationResult>()
        val observer = mock<Observer<BiometricPrompt.AuthenticationResult>>()
        viewModel.authenticationResult.observeForever(observer)

        handleConfirmCredentialResult(context, viewModel, Activity.RESULT_OK)

        verify(observer).onChanged(captor.capture())
        assertThat(captor.lastValue.authenticationType)
            .isEqualTo(BiometricPrompt.AUTHENTICATION_RESULT_TYPE_DEVICE_CREDENTIAL)
        assertThat(captor.lastValue.cryptoObject).isNull()
    }

    @Test
    fun handleConfirmCredentialResult_resultCanceled() {
        val captor = argumentCaptor<BiometricErrorData>()
        val observer = mock<Observer<BiometricErrorData>>()
        viewModel.authenticationError.observeForever(observer)

        handleConfirmCredentialResult(context, viewModel, Activity.RESULT_CANCELED)

        verify(observer).onChanged(captor.capture())
        assertThat(captor.lastValue.errorCode).isEqualTo(BiometricPrompt.ERROR_USER_CANCELED)
    }
}
