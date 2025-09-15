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

package androidx.xr.arcore.projected.testapp

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import androidx.xr.arcore.Earth
import androidx.xr.runtime.Config
import androidx.xr.runtime.Session
import androidx.xr.runtime.SessionConfigureConfigurationNotSupported
import androidx.xr.runtime.SessionConfigureGooglePlayServicesLocationLibraryNotLinked
import androidx.xr.runtime.SessionConfigureSuccess
import androidx.xr.runtime.SessionCreateApkRequired
import androidx.xr.runtime.SessionCreateSuccess
import androidx.xr.runtime.SessionCreateUnsupportedDevice
import androidx.xr.runtime.VpsAvailabilityResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Test app which tests projected perception API surface. */
class ProjectedTestAppActivity : ComponentActivity() {
    private lateinit var session: Session
    private lateinit var vpsAvailabilityResult: VpsAvailabilityResult
    private val TAG = "ProjectedTestAppActivity"
    val config: Config = Config()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "onCreate")
        tryCreateSession()
    }

    override fun onResume() {
        super.onResume()
        Log.i(TAG, "onResume")
        lifecycleScope.launch {
            delay(2000) // Sleep for 2 seconds
            checkVpsAvailability(1.0, 1.0)
        }
    }

    private fun checkVpsAvailability(latitude: Double, longitude: Double) {
        Log.i(TAG, "checkVpsAvailability")
        lifecycleScope.launch {
            vpsAvailabilityResult = Earth.checkVpsAvailability(session, latitude, longitude)
        }
    }

    public fun tryCreateSession() {
        Log.i(TAG, "tryCreateSession")
        when (val result = Session.create(this)) {
            is SessionCreateSuccess -> {
                session = result.session
                when (val configResult = session.configure(config)) {
                    is SessionConfigureConfigurationNotSupported -> {
                        Log.e(TAG, "Session configuration not supported.")
                        finish()
                    }
                    is SessionConfigureGooglePlayServicesLocationLibraryNotLinked -> {
                        Log.e(
                            TAG,
                            "Google Play Services Location Library is not linked, this should not happen.",
                        )
                    }
                    is SessionConfigureSuccess -> {
                        Log.i(TAG, "Session created successfully!!")
                    }
                    else -> {
                        Log.e(TAG, "Session creation error")
                    }
                }
            }
            is SessionCreateApkRequired -> {
                Log.e(TAG, "Can't create session due to apk missing")
            }
            is SessionCreateUnsupportedDevice -> {
                Log.e(TAG, "Can't create session, unsupported device")
                finish()
            }
        }
    }
}
