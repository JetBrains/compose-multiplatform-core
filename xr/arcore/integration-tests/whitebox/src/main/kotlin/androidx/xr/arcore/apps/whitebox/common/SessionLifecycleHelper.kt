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

package androidx.xr.arcore.apps.whitebox.common

import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.xr.runtime.Config
import androidx.xr.runtime.Session
import androidx.xr.runtime.SessionConfigureConfigurationNotSupported
import androidx.xr.runtime.SessionConfigurePermissionsNotGranted
import androidx.xr.runtime.SessionConfigureSuccess
import androidx.xr.runtime.SessionCreatePermissionsNotGranted
import androidx.xr.runtime.SessionCreateSuccess
import androidx.xr.runtime.SessionResumePermissionsNotGranted
import androidx.xr.runtime.SessionResumeSuccess

/**
 * Observer class to manage the lifecycle of the JXR Runtime Session based on the lifecycle owner
 * (activity).
 */
class SessionLifecycleHelper(
    val activity: ComponentActivity,
    val config: Config = Config(),
    val onSessionAvailable: (Session) -> Unit = {},
) : DefaultLifecycleObserver {

    /** Accessed through the [onSessionAvailable] callback. */
    private lateinit var session: Session
    private lateinit var requestPermissionLauncher: ActivityResultLauncher<Array<String>>

    override fun onCreate(owner: LifecycleOwner) {
        registerRequestPermissionLauncher(activity)

        when (val result = Session.create(activity)) {
            is SessionCreateSuccess -> {
                session = result.session
                val configResult = session.configure(config)
                if (configResult is SessionConfigurePermissionsNotGranted) {
                    requestPermissionLauncher.launch(configResult.permissions.toTypedArray())
                } else if (configResult is SessionConfigureConfigurationNotSupported) {
                    showErrorMessage("Session configuration not supported.")
                    activity.finish()
                } else if (configResult is SessionConfigureSuccess) {
                    onSessionAvailable(session)
                }
            }
            is SessionCreatePermissionsNotGranted -> {
                requestPermissionLauncher.launch(result.permissions.toTypedArray())
            }
        }
    }

    override fun onResume(owner: LifecycleOwner) {
        if (!this::session.isInitialized) {
            return
        }
        when (val result = session.resume()) {
            is SessionResumeSuccess -> {}
            is SessionResumePermissionsNotGranted -> {
                requestPermissionLauncher.launch(result.permissions.toTypedArray())
            }
        }
    }

    override fun onPause(owner: LifecycleOwner) {
        if (!this::session.isInitialized) {
            return
        }
        session.pause()
    }

    override fun onDestroy(owner: LifecycleOwner) {
        if (!this::session.isInitialized) {
            return
        }
        session.destroy()
    }

    private fun registerRequestPermissionLauncher(activity: ComponentActivity) {
        requestPermissionLauncher =
            activity.registerForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) { permissions ->
                val allPermissionsGranted = permissions.all { it.value }
                if (!allPermissionsGranted) {
                    Toast.makeText(
                            activity,
                            "Required permissions were not granted, closing activity. ",
                            Toast.LENGTH_LONG,
                        )
                        .show()
                    activity.finish()
                } else {
                    activity.recreate()
                }
            }
    }

    companion object {
        private val TAG = this::class.simpleName
    }

    private fun <F> showErrorMessage(error: F) {
        Log.e(TAG, error.toString())
        Toast.makeText(activity, error.toString(), Toast.LENGTH_LONG).show()
    }
}
