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

package androidx.privacysandbox.sdkruntime.integration.testapp

import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.util.Log
import android.view.MenuItem
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import androidx.privacysandbox.sdkruntime.core.LoadSdkCompatException
import androidx.privacysandbox.sdkruntime.integration.testapp.fragments.AppOwnedInterfacesFragment
import androidx.privacysandbox.sdkruntime.integration.testapp.fragments.BaseFragment
import androidx.privacysandbox.sdkruntime.integration.testapp.fragments.ClientImportanceListenerFragment
import androidx.privacysandbox.sdkruntime.integration.testapp.fragments.GetClientPackageNameFragment
import androidx.privacysandbox.sdkruntime.integration.testapp.fragments.LoadedSdksFragment
import androidx.privacysandbox.sdkruntime.integration.testapp.fragments.SandboxDeathFragment
import androidx.privacysandbox.sdkruntime.integration.testapp.fragments.SdkActivityFragment
import androidx.privacysandbox.sdkruntime.integration.testapp.fragments.SdkContextFragment
import com.google.android.material.navigation.NavigationView
import kotlinx.coroutines.launch

class TestMainActivity : AppCompatActivity() {

    lateinit var api: TestAppApi
    private lateinit var logView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        api = TestAppApi(this)

        logView = findViewById(R.id.logView)
        logView.setMovementMethod(ScrollingMovementMethod())

        setupLoadTestSdkButton()
        setupUnloadTestSdkButton()
        setupLoadMediateeSdkButton()
        setupUnloadMediateeSdkButton()
        setupCUJList()

        switchContentFragment(LoadedSdksFragment())
    }

    fun addLogMessage(message: String) {
        Log.i(TAG, message)
        logView.append(message + System.lineSeparator())
    }

    private fun setupLoadTestSdkButton() {
        val loadSdkButton = findViewById<Button>(R.id.loadTestSdkButton)
        loadSdkButton.setOnClickListener {
            lifecycleScope.launch {
                try {
                    addLogMessage("Loading TestSDK...")
                    val testSdk = api.loadTestSdk()
                    addLogMessage("TestSDK Message: " + testSdk.doSomething("42"))
                    addLogMessage("Successfully loaded TestSDK")
                } catch (ex: LoadSdkCompatException) {
                    addLogMessage("Failed to load TestSDK: " + ex.message)
                }
            }
        }
    }

    private fun setupLoadMediateeSdkButton() {
        val loadSdkButton = findViewById<Button>(R.id.loadMediateeSdkButton)
        loadSdkButton.setOnClickListener {
            lifecycleScope.launch {
                try {
                    addLogMessage("Loading MediateeSDK...")
                    val testSdk = api.loadMediateeSdk()
                    addLogMessage("MediateeSDK Message: " + testSdk.doSomething("42"))
                    addLogMessage("Successfully loaded MediateeSDK")
                } catch (ex: LoadSdkCompatException) {
                    addLogMessage("Failed to load MediateeSDK: " + ex.message)
                }
            }
        }
    }

    private fun setupUnloadTestSdkButton() {
        val unloadSdkButton = findViewById<Button>(R.id.unloadTestSdkButton)
        unloadSdkButton.setOnClickListener {
            api.unloadTestSdk()
            addLogMessage("Unloaded TestSDK")
        }
    }

    private fun setupUnloadMediateeSdkButton() {
        val unloadSdkButton = findViewById<Button>(R.id.unloadMediateeSdkButton)
        unloadSdkButton.setOnClickListener {
            api.unloadMediateeSdk()
            addLogMessage("Unloaded MediateeSDK")
        }
    }

    private fun setupCUJList() {
        val drawerLayout = findViewById<DrawerLayout>(R.id.drawer)

        val cujListButton = findViewById<Button>(R.id.cujListButton)
        cujListButton.setOnClickListener {
            if (drawerLayout.isOpen) {
                drawerLayout.closeDrawers()
            } else {
                drawerLayout.open()
            }
        }

        val cujList = findViewById<NavigationView>(R.id.cujList)
        cujList.setNavigationItemSelectedListener {
            drawerLayout.closeDrawers()
            selectCuj(it)
            true
        }
    }

    private fun selectCuj(menuItem: MenuItem) {
        when (menuItem.itemId) {
            R.id.itemLoadedSdksCuj -> switchContentFragment(LoadedSdksFragment())
            R.id.itemAppSdksCuj -> switchContentFragment(AppOwnedInterfacesFragment())
            R.id.itemSandboxDeathCuj -> switchContentFragment(SandboxDeathFragment())
            R.id.itemClientPackageCuj -> switchContentFragment(GetClientPackageNameFragment())
            R.id.itemSdkContextCuj -> switchContentFragment(SdkContextFragment())
            R.id.itemClientImportanceListenerCuj ->
                switchContentFragment(ClientImportanceListenerFragment())
            R.id.itemSdkActivityCuj -> switchContentFragment(SdkActivityFragment())
            else -> addLogMessage("Invalid CUJ option")
        }
    }

    private fun switchContentFragment(fragment: BaseFragment) {
        supportFragmentManager
            .beginTransaction()
            .replace(R.id.cujFragmentContent, fragment)
            .commit()
    }

    companion object {
        private const val TAG = "TestMainActivity"
    }
}
