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
package androidx.biometric.samples.java;

import static androidx.biometric.AuthenticationUtils.registerForAuthenticationResult;

import android.os.Bundle;
import android.util.Log;

import androidx.biometric.AuthenticationRequest;
import androidx.biometric.AuthenticationRequest.Biometric;
import androidx.biometric.AuthenticationResultLauncher;
import androidx.biometric.PromptContentItemBulletedText;
import androidx.fragment.app.FragmentActivity;

import kotlin.Unit;

import java.util.ArrayList;
import java.util.Arrays;

public class AuthenticationSampleActivity extends FragmentActivity {
    private static final String TAG = "AuthenticationSampleActivity";

    private final AuthenticationResultLauncher mAuthResultLauncher =
            registerForAuthenticationResult(
                    this,
                    () -> {
                        // Handle intermediate authentication failure, this is optional.
                        Log.i(TAG, "onAuthenticationFailed, try again");
                        return Unit.INSTANCE;
                    },
                    result -> {
                        if (result.isSuccess()) {
                            Log.i(TAG, "onAuthenticationSucceeded with type"
                                    + result.success().getAuthType());
                        } else if (result.isError()) {
                            // Handle authentication error, e.g. negative button click, user
                            // cancellation, etc
                            Log.i(
                                    TAG,
                                    "onAuthenticationError " + result.error().getErrorCode() + " "
                                            + result.error().getErrString()
                            );
                        }
                    }
            );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        String title = "Title";
        String subtitle = "Subtitle";
        AuthenticationRequest.BodyContent bodyContent =
                new AuthenticationRequest.BodyContent.VerticalList(
                        "Vertical list description",
                        new ArrayList<>(
                                Arrays.asList(new PromptContentItemBulletedText("test item1"),
                                        new PromptContentItemBulletedText("test item2")))

                );
        Biometric.Fallback fallback = new Biometric.Fallback.NegativeButton("Cancel button");
        Biometric.Strength minStrength = Biometric.Strength.Class2.INSTANCE;

        AuthenticationRequest authRequest =
                new Biometric.Builder(title, fallback)
                        .setMinStrength(minStrength)
                        .setSubtitle(subtitle)
                        .setContent(bodyContent)
                        .setSubtitle(subtitle)
                        .setIsConfirmationRequired(true)
                        .build();

        // This could also be a button click event or whenever it's needed as long as it's called
        // after Lifecycle has reached CREATED.
        mAuthResultLauncher.launch(authRequest);
    }
}

