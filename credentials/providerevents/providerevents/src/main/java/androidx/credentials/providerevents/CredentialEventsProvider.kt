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

package androidx.credentials.providerevents

import android.os.IBinder
import androidx.annotation.RestrictTo
import androidx.credentials.providerevents.service.CredentialProviderEventsService

@RestrictTo(RestrictTo.Scope.LIBRARY)
/**
 * A service interface that feature providers implement in order to provide a specific interface.
 * The feature providers that wish to propagate update events to credential providers must add a
 * class that implements this interface. They must then add the class name as an extra to the intent
 * that they use to bind to the credential provider's service - an instance of
 * [CredentialProviderEventsService]. The onBind method of this base service class reads the class
 * name from the intent extras and instantiates it.
 */
public interface CredentialEventsProvider {
    /**
     * Returns the Stub implementation that will be invoked by the feature provider after binding to
     * an instance of [CredentialProviderEventsService]. The stub implementation will in turn call
     * the service endpoints using the [service] instance passed into this method.
     */
    public fun getStubImplementation(service: CredentialProviderEventsService): IBinder? {
        return null
    }

    public companion object {
        public const val EVENTS_PROVIDER_KEY: String =
            "androidx.credentials.providerevents.service.EVENTS_SERVICE_PROVIDER_KEY"
    }
}
