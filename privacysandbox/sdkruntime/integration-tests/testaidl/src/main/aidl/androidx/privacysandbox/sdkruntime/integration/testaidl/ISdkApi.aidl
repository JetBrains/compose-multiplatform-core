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

package androidx.privacysandbox.sdkruntime.integration.testaidl;

import androidx.privacysandbox.sdkruntime.integration.testaidl.IClientImportanceListener;
import androidx.privacysandbox.sdkruntime.integration.testaidl.ILoadSdkCallback;
import androidx.privacysandbox.sdkruntime.integration.testaidl.ISdkActivityHandler;
import androidx.privacysandbox.sdkruntime.integration.testaidl.LoadedSdkInfo;

interface ISdkApi {
    String doSomething(String param);

    oneway void loadSdk(in String sdkName, in Bundle params, in ILoadSdkCallback callback);

    List<LoadedSdkInfo> getSandboxedSdks();
    List<LoadedSdkInfo> getAppOwnedSdks();

    List<String> callDoSomethingOnSandboxedSdks(String param);
    List<String> callDoSomethingOnAppOwnedSdks(String param);

    oneway void triggerSandboxDeath();

    String getClientPackageName();

    void writeToFile(String filename, String data);
    @nullable String readFromFile(String filename);

    void registerClientImportanceListener(in IClientImportanceListener listener);
    void unregisterClientImportanceListener(in IClientImportanceListener listener);

    IBinder registerSdkActivityHandler(in ISdkActivityHandler appSideActivityHandler);
}
