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

package androidx.xr.runtime

import android.app.Activity
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.xr.runtime.internal.ApkCheckAvailabilityErrorException
import androidx.xr.runtime.internal.ApkCheckAvailabilityInProgressException
import androidx.xr.runtime.internal.ApkNotInstalledException
import androidx.xr.runtime.internal.UnsupportedDeviceException
import androidx.xr.runtime.testing.FakeJxrPlatformAdapter
import androidx.xr.runtime.testing.FakeLifecycleManager
import androidx.xr.runtime.testing.FakeRuntimeFactory
import androidx.xr.runtime.testing.FakeStateExtender
import com.google.common.truth.Truth.assertThat
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

@RunWith(AndroidJUnit4::class)
class SessionTest {
    private lateinit var activity: Activity
    private lateinit var testDispatcher: TestDispatcher
    private lateinit var testScope: TestScope

    @get:Rule val activityScenarioRule = ActivityScenarioRule<Activity>(Activity::class.java)
    @get:Rule val secondActivityScenarioRule = ActivityScenarioRule<Activity>(Activity::class.java)

    @Before
    fun setUp() {
        activityScenarioRule.scenario.onActivity { this.activity = it }
        shadowOf(activity).grantPermissions(*FakeLifecycleManager.TestPermissions.toTypedArray())

        testDispatcher = StandardTestDispatcher()
        testScope = TestScope(testDispatcher)
    }

    @Test
    fun create_returnsSuccessResultWithNonNullSession() {
        val result = Session.create(activity) as SessionCreateSuccess

        assertThat(result.session).isNotNull()

        // Destroy the session to clean up the static activity map.
        result.session.destroy()
    }

    @Test
    fun create_setsLifecycleToInitialized() {
        val underTest = (Session.create(activity) as SessionCreateSuccess).session

        val lifecycleManager = underTest.runtime.lifecycleManager as FakeLifecycleManager
        assertThat(lifecycleManager.state).isEqualTo(FakeLifecycleManager.State.INITIALIZED)

        // Destroy the session to clean up the static activity map.
        underTest.destroy()
    }

    @Test
    fun create_initializesStateExtender() {
        val underTest = (Session.create(activity) as SessionCreateSuccess).session

        // The FakeStateExtender is being loaded in Session here because it is defined as a class in
        // the
        // "//third_party/arcore/androidx/java/androidx/xr/testing" dependency.
        val stateExtender = underTest.stateExtenders.first() as FakeStateExtender
        assertThat(stateExtender.isInitialized).isTrue()

        // Destroy the session to clean up the static activity map.
        underTest.destroy()
    }

    @Test
    fun create_permissionException_returnsPermissionsNotGrantedResult() {
        FakeRuntimeFactory.hasCreatePermission = false

        val result = Session.create(activity)
        // Reset the flag to true so other tests are not affected.
        FakeRuntimeFactory.hasCreatePermission = true

        assertThat(result).isInstanceOf(SessionCreatePermissionsNotGranted::class.java)
    }

    @Test
    fun create_arcoreNotInstalledException_returnsApkRequiredResult() {
        FakeRuntimeFactory.lifecycleCreateException = ApkNotInstalledException(ARCORE_PACKAGE_NAME)

        val result = Session.create(activity)

        assertThat(result).isInstanceOf(SessionCreateApkRequired::class.java)
        assertThat((result as SessionCreateApkRequired).requiredApk).isEqualTo(ARCORE_PACKAGE_NAME)
    }

    @Test
    fun create_arcoreUnsupportedDeviceException_returnsUnsupportedDeviceResult() {
        FakeRuntimeFactory.lifecycleCreateException = UnsupportedDeviceException()

        val result = Session.create(activity)

        assertThat(result).isInstanceOf(SessionCreateUnsupportedDevice::class.java)
    }

    @Test
    fun create_arcoreCheckAvailabilityInProgressException_returnsApkRequiredResult() {
        FakeRuntimeFactory.lifecycleCreateException =
            ApkCheckAvailabilityInProgressException(ARCORE_PACKAGE_NAME)

        val result = Session.create(activity)

        assertThat(result).isInstanceOf(SessionCreateApkRequired::class.java)
        assertThat((result as SessionCreateApkRequired).requiredApk).isEqualTo(ARCORE_PACKAGE_NAME)
    }

    @Test
    fun create_arcoreCheckAvailabilityErrorException_returnsApkRequiredResult() {
        FakeRuntimeFactory.lifecycleCreateException =
            ApkCheckAvailabilityErrorException(ARCORE_PACKAGE_NAME)

        val result = Session.create(activity)

        assertThat(result).isInstanceOf(SessionCreateApkRequired::class.java)
        assertThat((result as SessionCreateApkRequired).requiredApk).isEqualTo(ARCORE_PACKAGE_NAME)
    }

    @Test
    fun create_initializesPlatformAdapter() {
        val underTest = (Session.create(activity) as SessionCreateSuccess).session

        // The FakeJxrPlatformAdapter is being loaded in Session here because it is defined as a
        // class
        // in the "//third_party/arcore/androidx/java/androidx/xr/testing" dependency.
        val platformAdapter = underTest.platformAdapter as FakeJxrPlatformAdapter
        assertThat(platformAdapter).isNotNull()
        assertThat(platformAdapter.state.name).isEqualTo("CREATED")

        // Destroy the session to clean up the static activity map.
        underTest.destroy()
    }

    @Test
    fun configure_destroyed_throwsIllegalStateException() {
        val underTest = (Session.create(activity) as SessionCreateSuccess).session
        underTest.destroy()

        assertFailsWith<IllegalStateException> { underTest.configure(Config()) }
    }

    @Test
    fun configure_returnsSuccessAndChangesConfig() {
        val underTest = (Session.create(activity) as SessionCreateSuccess).session
        check(
            underTest.config ==
                Config(
                    Config.PlaneTrackingMode.HORIZONTAL_AND_VERTICAL,
                    Config.HandTrackingMode.BOTH,
                    Config.HeadTrackingMode.LAST_KNOWN,
                    Config.DepthEstimationMode.SMOOTH_AND_RAW,
                    Config.AnchorPersistenceMode.LOCAL,
                )
        )
        val config =
            Config(
                Config.PlaneTrackingMode.DISABLED,
                Config.HandTrackingMode.DISABLED,
                Config.HeadTrackingMode.DISABLED,
                Config.DepthEstimationMode.DISABLED,
                Config.AnchorPersistenceMode.DISABLED,
            )

        val result = underTest.configure(config)

        assertThat(result).isInstanceOf(SessionConfigureSuccess::class.java)
        assertThat(underTest.config).isEqualTo(config)

        // Destroy the session to clean up the static activity map.
        underTest.destroy()
    }

    @Test
    fun configure_permissionNotGranted_returnsPermissionNotGrantedResult() {
        val underTest = (Session.create(activity) as SessionCreateSuccess).session
        val lifecycleManager = underTest.runtime.lifecycleManager as FakeLifecycleManager
        val currentConfig = underTest.config
        check(currentConfig.depthEstimation == Config.DepthEstimationMode.SMOOTH_AND_RAW)
        lifecycleManager.hasMissingPermission = true

        val result =
            underTest.configure(
                underTest.config.copy(depthEstimation = Config.DepthEstimationMode.DISABLED)
            )

        assertThat(result).isInstanceOf(SessionConfigurePermissionsNotGranted::class.java)
        assertThat(underTest.config).isEqualTo(currentConfig)

        // Destroy the session to clean up the static activity map.
        underTest.destroy()
    }

    @Test
    fun configure_unsupportedMode_returnsConfigurationNotSupportedResult() {
        val underTest = (Session.create(activity) as SessionCreateSuccess).session
        val lifecycleManager = underTest.runtime.lifecycleManager as FakeLifecycleManager
        val currentConfig = underTest.config

        lifecycleManager.shouldSupportPlaneTracking = false
        val result =
            underTest.configure(
                Config(planeTracking = Config.PlaneTrackingMode.HORIZONTAL_AND_VERTICAL)
            )

        assertThat(result).isInstanceOf(SessionConfigureConfigurationNotSupported::class.java)
        assertThat(underTest.config).isEqualTo(currentConfig)

        // Destroy the session to clean up the static activity map.
        underTest.destroy()
    }

    // TODO(b/349855733): Add a test to verify configure() calls the corresponding LifecycleManager
    // method once FakeRuntime supports it.

    @Test
    fun resume_returnsSuccessAndSetsLifecycleToResumed() {
        val underTest = (Session.create(activity) as SessionCreateSuccess).session

        val result = underTest.resume()

        assertThat(result).isInstanceOf(SessionResumeSuccess::class.java)
        val lifecycleManager = underTest.runtime.lifecycleManager as FakeLifecycleManager
        assertThat(lifecycleManager.state).isEqualTo(FakeLifecycleManager.State.RESUMED)

        // Destroy the session to clean up the static activity map.
        underTest.destroy()
    }

    @Test
    fun resume_returnsSuccessAndSetsPlatformAdapterToResumed() {
        val underTest = (Session.create(activity) as SessionCreateSuccess).session
        val result = underTest.resume()

        assertThat(result).isInstanceOf(SessionResumeSuccess::class.java)
        assertThat((underTest.platformAdapter as FakeJxrPlatformAdapter).state.name)
            .isEqualTo("STARTED")

        // Destroy the session to clean up the static activity map.
        underTest.destroy()
    }

    @Test
    fun resume_destroyed_throwsIllegalStateException() {
        val underTest = (Session.create(activity) as SessionCreateSuccess).session
        underTest.destroy()

        assertFailsWith<IllegalStateException> { underTest.resume() }
    }

    // TODO(b/349859981): Add a test to verify update() calls the corresponding LifecycleManager
    // method once FakeRuntime supports it.
    @Test
    fun update_emitsUpdatedState() =
        runTest(testDispatcher) {
            val underTest =
                (Session.create(activity, testDispatcher) as SessionCreateSuccess).session
            val lifecycleManager = underTest.runtime.lifecycleManager as FakeLifecycleManager
            val timeSource = lifecycleManager.timeSource
            val expectedDuration = 100.milliseconds

            awaitNewCoreState(underTest, this)
            val beforeTimeMark = underTest.state.value.timeMark
            timeSource += expectedDuration
            // By default FakeLifecycleManager will only allow one call to update() to go through.
            // Since
            // we are calling update() twice, we need to allow one more call to go through.
            lifecycleManager.allowOneMoreCallToUpdate()
            awaitNewCoreState(underTest, this)
            val afterTimeMark = underTest.state.value.timeMark

            val actualDuration = afterTimeMark - beforeTimeMark
            assertThat(actualDuration).isEqualTo(expectedDuration)

            // Destroy the session to clean up the static activity map.
            underTest.destroy()
        }

    @Test
    fun update_extendsState() =
        runTest(testDispatcher) {
            val underTest =
                (Session.create(activity, testDispatcher) as SessionCreateSuccess).session
            val stateExtender = underTest.stateExtenders.first() as FakeStateExtender
            check(stateExtender.extended.isEmpty())

            awaitNewCoreState(underTest, this)

            assertThat(stateExtender.extended).isNotEmpty()

            // Destroy the session to clean up the static activity map.
            underTest.destroy()
        }

    @Test
    fun pause_setsLifecycleToPaused() {
        val underTest = (Session.create(activity) as SessionCreateSuccess).session
        underTest.resume()

        underTest.pause()

        val lifecycleManager = underTest.runtime.lifecycleManager as FakeLifecycleManager
        assertThat(lifecycleManager.state).isEqualTo(FakeLifecycleManager.State.PAUSED)

        // Destroy the session to clean up the static activity map.
        underTest.destroy()
    }

    @Test
    fun pause_setsPlatformAdapterToPaused() {
        val underTest = (Session.create(activity) as SessionCreateSuccess).session
        underTest.resume()

        underTest.pause()

        val platformAdapter = underTest.platformAdapter as FakeJxrPlatformAdapter
        assertThat(platformAdapter.state.name).isEqualTo("PAUSED")

        // Destroy the session to clean up the static activity map.
        underTest.destroy()
    }

    @Test
    fun pause_destroyed_throwsIllegalStateException() {
        val underTest = (Session.create(activity) as SessionCreateSuccess).session
        underTest.destroy()

        assertFailsWith<IllegalStateException> { underTest.pause() }
    }

    @Test
    fun destroy_initialized_setsLifecycleToStopped() {
        val underTest = (Session.create(activity) as SessionCreateSuccess).session

        underTest.destroy()

        val lifecycleManager = underTest.runtime.lifecycleManager as FakeLifecycleManager
        assertThat(lifecycleManager.state).isEqualTo(FakeLifecycleManager.State.STOPPED)
    }

    @Test
    fun destroy_resumed_setsLifecycleToStopped() {
        val underTest = (Session.create(activity) as SessionCreateSuccess).session
        underTest.resume()

        underTest.destroy()

        val lifecycleManager = underTest.runtime.lifecycleManager as FakeLifecycleManager
        assertThat(lifecycleManager.state).isEqualTo(FakeLifecycleManager.State.STOPPED)
    }

    @Test
    fun destroy_withMultiple_doesNotSetFinalActivity() {
        var secondActivity: Activity? = null
        secondActivityScenarioRule.scenario.onActivity { secondActivity = it }
        val underTest = (Session.create(activity) as SessionCreateSuccess).session
        val secondSession = (Session.create(secondActivity!!) as SessionCreateSuccess).session
        underTest.resume()

        // Destroy the session while the other session is still active.
        underTest.destroy()

        val lifecycleManager = underTest.runtime.lifecycleManager as FakeLifecycleManager
        // This should not be stopped because there is still an active activity but it will update
        // to PAUSED.
        assertThat(lifecycleManager.state).isEqualTo(FakeLifecycleManager.State.PAUSED)

        // Destroy the second session to clean up the static activity map.
        secondSession.destroy()
    }

    @Test
    fun destroy_lastDestroyed_setFinalActivityTrue() {
        var secondActivity: Activity? = null
        secondActivityScenarioRule.scenario.onActivity { secondActivity = it }
        val underTest = (Session.create(activity) as SessionCreateSuccess).session
        val secondSession = (Session.create(secondActivity!!) as SessionCreateSuccess).session
        secondSession.resume()
        secondSession.destroy()
        underTest.resume()

        // Destroy the session after the other session was destroyed.
        underTest.destroy()

        val lifecycleManager = underTest.runtime.lifecycleManager as FakeLifecycleManager
        assertThat(lifecycleManager.state).isEqualTo(FakeLifecycleManager.State.STOPPED)

        // Destroy the second session to clean up the static activity map.
        secondSession.destroy()
    }

    @Test
    fun destroy_setsPlatformAdapterToStopped() {
        val underTest = (Session.create(activity) as SessionCreateSuccess).session
        underTest.resume()

        underTest.destroy()

        val platformAdapter = underTest.platformAdapter as FakeJxrPlatformAdapter
        assertThat(platformAdapter.state.name).isEqualTo("STOPPED")
    }

    @Test
    fun destroy_cancelsCoroutineScope() {
        val underTest = (Session.create(activity) as SessionCreateSuccess).session
        // Creating a job that will not finish by the time destroy is called.
        val job = underTest.coroutineScope.launch { delay(12.hours) }

        underTest.destroy()

        // The job should be cancelled iff destroy was called and the coroutine scope was cancelled.
        assertThat(job.isCancelled).isTrue()
    }

    /** Resumes and pauses the session just enough to emit a new CoreState. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun awaitNewCoreState(session: Session, testScope: TestScope) {
        session.resume()
        testScope.advanceUntilIdle()
        session.pause()
    }

    private companion object {
        const private val ARCORE_PACKAGE_NAME = "com.google.ar.core"
    }
}
