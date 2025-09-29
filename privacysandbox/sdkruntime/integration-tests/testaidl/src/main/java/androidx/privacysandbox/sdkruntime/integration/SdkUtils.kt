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

package androidx.privacysandbox.sdkruntime.integration

import android.os.IBinder
import androidx.privacysandbox.sdkruntime.integration.testaidl.IAppSdk
import androidx.privacysandbox.sdkruntime.integration.testaidl.IMediateeSdkApi
import androidx.privacysandbox.sdkruntime.integration.testaidl.ISdkApi

fun callDoSomething(sdkInterface: IBinder?, param: String): String? {
    val maybeTestSdk = toTestSdk(sdkInterface)
    if (maybeTestSdk != null) {
        return maybeTestSdk.doSomething(param)
    }

    val maybeMediateeSdk = toMediateeSdk(sdkInterface)
    if (maybeMediateeSdk != null) {
        return maybeMediateeSdk.doSomething(param)
    }

    return toAppOwnedSdk(sdkInterface)?.doSomething(param)
}

private fun toTestSdk(sdkInterface: IBinder?): ISdkApi? {
    return if (ISdkApi.DESCRIPTOR == sdkInterface?.interfaceDescriptor) {
        ISdkApi.Stub.asInterface(sdkInterface)
    } else {
        null
    }
}

private fun toMediateeSdk(sdkInterface: IBinder?): IMediateeSdkApi? {
    return if (IMediateeSdkApi.DESCRIPTOR == sdkInterface?.interfaceDescriptor) {
        IMediateeSdkApi.Stub.asInterface(sdkInterface)
    } else {
        null
    }
}

private fun toAppOwnedSdk(appInterface: IBinder?): IAppSdk? {
    return if (IAppSdk.DESCRIPTOR == appInterface?.interfaceDescriptor) {
        IAppSdk.Stub.asInterface(appInterface)
    } else {
        null
    }
}
