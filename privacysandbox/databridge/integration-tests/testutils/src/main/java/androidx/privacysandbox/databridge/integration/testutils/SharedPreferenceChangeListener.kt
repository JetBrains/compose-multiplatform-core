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

package androidx.privacysandbox.databridge.integration.testutils

import android.content.SharedPreferences
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class SharedPreferenceChangeListener : SharedPreferences.OnSharedPreferenceChangeListener {
    private val latchMap = mutableMapOf<String, CountDownLatch>()
    private var keyToValueMap = mutableMapOf<String, Any?>()

    override fun onSharedPreferenceChanged(sharedPreference: SharedPreferences, key: String?) {
        if (key == null) {
            return
        }
        val value = sharedPreference.all[key]
        keyToValueMap[key] = value
        latchMap[key]?.countDown()
    }

    fun getValue(key: String): Any? {
        val res = latchMap[key]?.await(25, TimeUnit.SECONDS)
        res?.let {
            if (!it) {
                throw TimeoutException()
            }
        }
        return keyToValueMap[key]
    }

    fun initializeLatch(keys: Set<String>) {

        keys.forEach { key -> latchMap[key] = CountDownLatch(1) }
    }
}
