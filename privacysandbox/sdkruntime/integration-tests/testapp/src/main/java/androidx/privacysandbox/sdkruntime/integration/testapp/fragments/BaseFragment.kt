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

package androidx.privacysandbox.sdkruntime.integration.testapp.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.IdRes
import androidx.annotation.LayoutRes
import androidx.fragment.app.Fragment
import androidx.privacysandbox.sdkruntime.integration.testapp.TestAppApi
import androidx.privacysandbox.sdkruntime.integration.testapp.TestMainActivity

/**
 * Base fragment to be used for controlling different manual flows.
 *
 * Create a new subclass of this for each independent flow you wish to test. There will only be one
 * active fragment in the app's main activity at any time.
 */
abstract class BaseFragment(@LayoutRes private val layoutId: Int) : Fragment() {

    private lateinit var inflatedView: View

    final override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        inflatedView = inflater.inflate(layoutId, container, false)
        onCreate()
        return inflatedView
    }

    abstract fun onCreate()

    fun <T : View?> findViewById(@IdRes id: Int): T {
        return inflatedView.findViewById<T>(id)
    }

    fun getTestAppApi(): TestAppApi {
        return testMainActivity().api
    }

    fun addLogMessage(message: String) {
        testMainActivity().addLogMessage(message)
    }

    private fun testMainActivity(): TestMainActivity = activity as TestMainActivity
}
