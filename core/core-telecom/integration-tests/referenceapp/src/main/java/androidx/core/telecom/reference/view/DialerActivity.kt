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

package androidx.core.telecom.reference.view

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.core.content.ContextCompat
import androidx.core.telecom.reference.CallRepository
import androidx.core.telecom.reference.Constants.ACTION_ANSWER_AND_SHOW_UI
import androidx.core.telecom.reference.Constants.ACTION_ANSWER_CALL
import androidx.core.telecom.reference.Constants.DEEP_LINK_BASE_URI
import androidx.core.telecom.reference.Constants.EXTRA_CALL_ID
import androidx.core.telecom.reference.Constants.EXTRA_REMOTE_USER_NAME
import androidx.core.telecom.reference.Constants.EXTRA_SIMULATED_NUMBER
import androidx.core.telecom.reference.VoipApplication
import androidx.core.telecom.reference.service.TelecomVoipService
import androidx.core.telecom.reference.viewModel.DialerActivityViewModel
import androidx.core.telecom.reference.viewModel.DialerViewModel
import androidx.core.telecom.reference.viewModel.InCallViewModel
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navDeepLink

/**
 * The main activity for the Dialer application.
 *
 * This activity serves as the entry point and hosts the Jetpack Compose UI.
 */
@RequiresApi(Build.VERSION_CODES.S)
class DialerActivity : ComponentActivity() {
    private val activityViewModel: DialerActivityViewModel by viewModels()
    private val callRepository: CallRepository by lazy {
        (application as VoipApplication).callRepository
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d("DialerActivity", "onCreate")
        super.onCreate(savedInstanceState)
        // Set the content to be the DialerApp composable, wrapped in the app's theme.
        setContent {
            AppTheme { // Apply the defined Material theme
                DialerApp(context = applicationContext)
            }
        }
        // Handle new incoming call intents etc.
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        activityViewModel.processNewIntent(intent)
        Log.d("DialerActivity", "onNewIntent:")
        // Handle new incoming call intents etc.
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        val action = intent.action
        val callId = intent.getStringExtra(EXTRA_CALL_ID)
        val isAnswerAction = ACTION_ANSWER_AND_SHOW_UI == action
        val isViewCallAction =
            Intent.ACTION_VIEW == action &&
                intent.data?.toString()?.contains(NavRoutes.IN_CALL) == true

        Log.d(
            "DialerActivity",
            "Handling intent: action=$action, callId=$callId," +
                " isAnswerAction=$isAnswerAction, isViewCallAction=$isViewCallAction"
        )

        // --- Ensure service is connected whenever showing/interacting with a call ---
        if (isAnswerAction || isViewCallAction || callId != null) {
            Log.d("DialerActivity", "Intent relates to a call, ensuring service" + " connection.")
            callRepository.maybeConnectService(applicationContext)
        }

        // --- Handle ANSWER action ---
        if (isAnswerAction && callId != null) {
            Log.i(
                "DialerActivity",
                "[$callId] Received answer action from notification. Signaling service."
            )
            val number = intent.getStringExtra(EXTRA_SIMULATED_NUMBER)
            val name = intent.getStringExtra(EXTRA_REMOTE_USER_NAME)
            if (number != null && name != null) {
                val serviceIntent =
                    Intent(this, TelecomVoipService::class.java).apply {
                        this.action = ACTION_ANSWER_CALL
                        putExtra(EXTRA_CALL_ID, callId)
                        putExtra(EXTRA_SIMULATED_NUMBER, number)
                        putExtra(EXTRA_REMOTE_USER_NAME, name)
                    }
                Log.i("DialerActivity", "[$callId] Calling startForegroundService")
                ContextCompat.startForegroundService(this, serviceIntent)
            } else {
                Log.w(
                    "DialerActivity",
                    "[$callId] Received answer without a name or" + " number arg!"
                )
            }
        } else if (isViewCallAction || callId != null) {
            Log.d(
                "DialerActivity",
                "[$callId] Handling regular view intent (deep link or existing call)." +
                    " Navigation should handle screen display."
            )
        } else {
            Log.w("DialerActivity", "Received intent unrelated to a specific call" + " action.")
        }
    }
}

/**
 * The root composable function for the Dialer application UI.
 *
 * This function sets up the navigation controller ([androidx.navigation.compose.NavHost]), creates
 * custom ViewModel factories to provide dependencies ([Context], [CallRepository]) to the
 * ViewModels ([DialerViewModel], [InCallViewModel]), and defines the navigation graph using
 * [NavHost]. It orchestrates the display of different screens ([DialerScreen], [InCallScreen],
 * [SettingsScreen]) based on the current navigation route.
 *
 * @param context The application context, passed down to ViewModels that require it.
 */
@Composable
fun DialerApp(lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current, context: Context) {
    // Create and remember a NavController for managing navigation between screens.
    val navController = rememberNavController()
    val appContext = context.applicationContext
    val callRepository = (appContext as VoipApplication).callRepository
    val activityViewModel: DialerActivityViewModel = viewModel()

    // Remember a custom ViewModelProvider.Factory for DialerViewModel.
    // This allows injecting the Context and CallRepository into DialerViewModel.
    val dialerViewModelFactory = remember {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(DialerViewModel::class.java)) {
                    // Create DialerViewModel with dependencies
                    @Suppress("UNCHECKED_CAST")
                    return DialerViewModel(appContext, callRepository) as T
                }
                throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
            }
        }
    }

    // Remember a custom ViewModelProvider.Factory for InCallViewModel.
    // This allows injecting the CallRepository into InCallViewModel.
    val inCallViewModelFactory = remember {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(InCallViewModel::class.java)) {
                    // Create InCallViewModel with dependency
                    @Suppress("UNCHECKED_CAST") return InCallViewModel(callRepository) as T
                }
                throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
            }
        }
    }

    // Obtain instances of the ViewModels using their respective factories.
    // These ViewModels will be scoped to the NavHost's lifecycle.
    val dialerViewModel: DialerViewModel = viewModel(factory = dialerViewModelFactory)
    val inCallViewModel: InCallViewModel = viewModel(factory = inCallViewModelFactory)

    DisposableEffect(lifecycleOwner, appContext) {
        callRepository.maybeConnectService(appContext)
        // When the effect leaves the Composition, teardown
        onDispose { callRepository.maybeDisconnectService() }
    }

    // Effect to handle intents passed from the Activity
    LaunchedEffect(Unit) {
        activityViewModel.newIntentFlow.collect { intent ->
            Log.d("DialerApp", "Collected new intent from ViewModel, calling" + " handleDeepLink")
            val navigated = navController.handleDeepLink(intent)
            Log.d("DialerApp", "handleDeepLink result: $navigated")
        }
    }

    // Define the navigation graph for the application.
    NavHost(navController = navController, startDestination = NavRoutes.DIALER) {
        // Define the Dialer screen destination.
        composable(NavRoutes.DIALER) {
            DialerScreen(
                dialerViewModel = dialerViewModel,
                // Navigate to Settings when the settings action is triggered.
                onNavigateToSettings = { navController.navigate(NavRoutes.SETTINGS) },
                // Navigate to the In-Call screen when a call is started.
                onStartCall = { navController.navigate(NavRoutes.IN_CALL) }
            )
        }
        // Define the In-Call screen destination.
        composable(
            route = NavRoutes.IN_CALL,
            // *** Add Deep Link Information ***
            deepLinks =
                listOf(
                    navDeepLink {
                        uriPattern = "${DEEP_LINK_BASE_URI}/${NavRoutes.IN_CALL}"
                        action = ACTION_ANSWER_AND_SHOW_UI
                    },
                    navDeepLink {
                        uriPattern = "${DEEP_LINK_BASE_URI}/${NavRoutes.IN_CALL}"
                        action = Intent.ACTION_VIEW
                    }
                ),
        ) { _ ->
            InCallScreen(inCallViewModel)
        }
        composable(NavRoutes.SETTINGS) { SettingsScreen() }
    }
}

/**
 * Defines the navigation route constants used within the application.
 *
 * Using constants for routes helps prevent typos and makes navigation logic easier to manage.
 */
object NavRoutes {
    /** Route for the main Dialer screen. */
    const val DIALER = "dialer"
    /** Route for the Settings screen. */
    const val SETTINGS = "settings"
    /** Route for the In-Call screen displayed during an active call. */
    const val IN_CALL = "inCall"
}
