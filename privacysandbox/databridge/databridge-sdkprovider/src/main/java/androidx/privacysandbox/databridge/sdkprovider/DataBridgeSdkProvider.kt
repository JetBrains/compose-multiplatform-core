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

package androidx.privacysandbox.databridge.sdkprovider

import android.content.Context
import androidx.annotation.GuardedBy
import androidx.annotation.VisibleForTesting
import androidx.privacysandbox.databridge.core.Key
import androidx.privacysandbox.databridge.core.KeyUpdateCallback
import androidx.privacysandbox.databridge.core.KeyUpdateCallbackWithExecutor
import androidx.privacysandbox.databridge.core.aidl.IDataBridgeProxy
import androidx.privacysandbox.databridge.core.aidl.IGetValuesResultCallback
import androidx.privacysandbox.databridge.core.aidl.IKeyUpdateInternalCallback
import androidx.privacysandbox.databridge.core.aidl.IRemoveValuesResultCallback
import androidx.privacysandbox.databridge.core.aidl.ISetValuesResultCallback
import androidx.privacysandbox.databridge.core.aidl.ResultInternal
import androidx.privacysandbox.databridge.core.aidl.ValueInternal
import androidx.privacysandbox.sdkruntime.core.AppOwnedSdkSandboxInterfaceCompat
import androidx.privacysandbox.sdkruntime.provider.controller.SdkSandboxControllerCompat
import java.io.IOException
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor

/**
 * This class provides the SDK Runtime enabled SDKs APIs to access and modify the data which is
 * stored on the app process. The APIs in this class will perform IPC calls to the app process.
 */
public abstract class DataBridgeSdkProvider private constructor() {

    /**
     * Get value for specific keys. In case any of any of the keys are not set, null is returned as
     * part of Result object for those keys.
     *
     * @param keys: Set of keys for which the values is requested
     * @return A map containing the key associated with a [Result] instance with the corresponding
     *   value. This Result either contains the successful value or indicates failure with a
     *   java.io.IOException or ClassCastException
     */
    public abstract suspend fun getValues(keys: Set<Key>): Map<Key, Result<Any?>>

    /**
     * Set the value for the specific key. This operation is atomic.
     *
     * @param keyValueMap: A map of the key and associated values to be set
     * @throws IOException when an exception is encountered writing data to the disk
     * @throws ClassCastException when there is a mismatch in any the key type and the type of the
     *   value
     */
    public abstract suspend fun setValues(keyValueMap: Map<Key, Any?>)

    /**
     * Removes the key-value pairs of the given keys. This operation is atomic.
     *
     * @param keys: Key which needs to be removed
     * @throws IOException when an exception is encountered removing the data associated with the
     *   key in the disk
     */
    public abstract suspend fun removeValues(keys: Set<Key>)

    /**
     * Registers a callback that will be triggered whenever the value of any key in the provided set
     * is updated.
     *
     * @param keys: Set of keys for which updates are required
     * @param executor: The executor from which the callback is executed.
     * @param callback: The callback which will be called when there is update in any of the keys in
     *   the provided set
     */
    public abstract fun registerKeyUpdateCallback(
        keys: Set<Key>,
        executor: Executor,
        callback: KeyUpdateCallback,
    )

    /**
     * Unregisters the callback. This will ensure that the keys registered to the callback will not
     * receive any further updates
     *
     * @param callback: The callback which should be unregistered
     */
    public abstract fun unregisterKeyUpdateCallback(callback: KeyUpdateCallback)

    public companion object {
        private var instance: DataBridgeSdkProvider? = null
        private val lock = Any()

        /**
         * Get an instance of [DataBridgeSdkProvider]. This will help the SDK Runtime enabled SDKs
         * to access and modify the data which is stored on the app process.
         *
         * @param sdkContext: SDK context
         * @return DataBridgeClient instance
         * @throws java.lang.IllegalStateException If DataBridgeClient has not been initialized by
         *   the app process
         */
        @JvmStatic
        public fun getInstance(sdkContext: Context): DataBridgeSdkProvider {
            synchronized(lock) {
                if (instance == null) {
                    val sdkSandboxControllerCompat = SdkSandboxControllerCompat.from(sdkContext)
                    val appOwnedSdkSandboxInterface: AppOwnedSdkSandboxInterfaceCompat? =
                        sdkSandboxControllerCompat.getAppOwnedSdkSandboxInterfaces().firstOrNull {
                            it.getName() == "androidx.privacysandbox.databridge"
                        }

                    if (appOwnedSdkSandboxInterface == null) {
                        throw java.lang.IllegalStateException(
                            "DataBridgeClient must be initialized from the app before an instance of DataBridgeSdkProvider can be requested for."
                        )
                    }

                    instance =
                        DataBridgeSdkProviderImpl(
                            IDataBridgeProxy.Stub.asInterface(
                                appOwnedSdkSandboxInterface.getInterface()
                            )
                        )
                }
                return instance!!
            }
        }

        @JvmStatic
        @VisibleForTesting
        internal fun getInstance(dataBridgeProxy: IDataBridgeProxy): DataBridgeSdkProvider {
            synchronized(lock) {
                instance = DataBridgeSdkProviderImpl(dataBridgeProxy)
                return instance!!
            }
        }
    }

    private class DataBridgeSdkProviderImpl(val dataBridgeProxy: IDataBridgeProxy) :
        DataBridgeSdkProvider() {

        private val lock = Any()
        private val uuid = UUID.randomUUID().toString()

        @GuardedBy("lock")
        private val keyToKeyUpdateCallbackWithExecutorMap =
            mutableMapOf<Key, MutableList<KeyUpdateCallbackWithExecutor>>()

        private val keyUpdateInternalCallback =
            object : IKeyUpdateInternalCallback.Stub() {
                override fun onKeyUpdated(keyName: String, data: ValueInternal) {
                    val key =
                        when (data.type) {
                            "INT" -> Key.createIntKey(keyName)
                            "LONG" -> Key.createLongKey(keyName)
                            "FLOAT" -> Key.createFloatKey(keyName)
                            "DOUBLE" -> Key.createDoubleKey(keyName)
                            "BOOLEAN" -> Key.createBooleanKey(keyName)
                            "STRING" -> Key.createStringKey(keyName)
                            "STRING_SET" -> Key.createStringSetKey(keyName)
                            "BYTE_ARRAY" -> Key.createByteArrayKey(keyName)
                            else ->
                                throw IllegalStateException("$data.type is not a valid key type")
                        }
                    sendKeyUpdates(key, data.value)
                }
            }

        override suspend fun getValues(keys: Set<Key>): Map<Key, Result<Any?>> {
            val keyNameToKeyMap: Map<String, Key> = keys.associateBy { it.name }
            val (keyNames, keyTypes) = keys.map { it.name to it.type.toString() }.unzip()
            val callback =
                object : IGetValuesResultCallback.Stub() {
                    private val latch = CountDownLatch(1)
                    private var mResult: Map<Key, Result<Any?>> = emptyMap()

                    override fun getValuesResult(resultInternal: List<ResultInternal>) {
                        val result =
                            resultInternal.associate {
                                val key = keyNameToKeyMap[it.keyName]!!
                                if (it.valueInternal != null) {
                                    key to Result.success(it.valueInternal!!.value)
                                } else {
                                    key to
                                        getResultFailureFromThrowable(
                                            it.exceptionName!!,
                                            it.exceptionMessage!!,
                                        )
                                }
                            }
                        mResult = result
                        latch.countDown()
                    }

                    fun getResult(): Map<Key, Result<Any?>> {
                        latch.await()
                        return mResult
                    }
                }
            dataBridgeProxy.getValues(keyNames, keyTypes, callback)
            return callback.getResult()
        }

        override suspend fun setValues(keyValueMap: Map<Key, Any?>) {
            val callback =
                object : ISetValuesResultCallback.Stub() {
                    private val latch = CountDownLatch(1)
                    private var mExceptionName: String? = null
                    private var mExceptionMessage: String? = null

                    override fun setValuesResult(
                        exceptionName: String?,
                        exceptionMessage: String?,
                    ) {
                        mExceptionName = exceptionName
                        mExceptionMessage = exceptionMessage
                        latch.countDown()
                    }

                    fun throwExceptionIfPresent() {
                        latch.await()
                        if (mExceptionName != null) {
                            throw createAndReturnThrowable(mExceptionName!!, mExceptionMessage!!)
                        }
                    }
                }

            val valueInternal =
                keyValueMap.map {
                    ValueInternal(
                        it.key.type.toString(),
                        isValueNull = (it.value == null),
                        it.value,
                    )
                }

            dataBridgeProxy.setValues(keyValueMap.map { it.key.name }, valueInternal, callback)

            callback.throwExceptionIfPresent()
        }

        override suspend fun removeValues(keys: Set<Key>) {
            val callback =
                object : IRemoveValuesResultCallback.Stub() {
                    private val latch = CountDownLatch(1)
                    private var mExceptionName: String? = null
                    private var mExceptionMessage: String? = null

                    override fun removeValuesResult(
                        exceptionName: String?,
                        exceptionMessage: String?,
                    ) {
                        mExceptionName = exceptionName
                        mExceptionMessage = exceptionMessage
                        latch.countDown()
                    }

                    fun throwExceptionIfPresent() {
                        latch.await()
                        if (mExceptionName != null) {
                            throw createAndReturnThrowable(mExceptionName!!, mExceptionMessage!!)
                        }
                    }
                }

            val (keyNames, keyTypes) = keys.map { it.name to it.type.toString() }.unzip()
            dataBridgeProxy.removeValues(keyNames, keyTypes, callback)
            callback.throwExceptionIfPresent()
        }

        override fun registerKeyUpdateCallback(
            keys: Set<Key>,
            executor: Executor,
            callback: KeyUpdateCallback,
        ) {
            keys.forEach { key ->
                synchronized(lock) {
                    val keyUpdateCallbackWithExecutor =
                        KeyUpdateCallbackWithExecutor(callback, executor)
                    keyToKeyUpdateCallbackWithExecutorMap
                        .getOrPut(key) { mutableListOf() }
                        .add(keyUpdateCallbackWithExecutor)
                }
            }

            val (keyNames, keyTypes) = keys.map { it.name to it.type.toString() }.unzip()
            dataBridgeProxy.addKeysForUpdates(uuid, keyNames, keyTypes, keyUpdateInternalCallback)
        }

        override fun unregisterKeyUpdateCallback(callback: KeyUpdateCallback) {
            val keysToRemoveFromMap = mutableListOf<Key>()
            synchronized(lock) {
                keyToKeyUpdateCallbackWithExecutorMap.forEach {
                    (key, keyUpdateCallbackWithExecutorList) ->
                    keyUpdateCallbackWithExecutorList.removeAll { it.keyUpdateCallback == callback }

                    if (keyUpdateCallbackWithExecutorList.isEmpty()) {
                        keysToRemoveFromMap.add(key)
                    }
                }
                keysToRemoveFromMap.forEach { key ->
                    keyToKeyUpdateCallbackWithExecutorMap.remove(key)
                }

                val (keyNames, keyTypes) =
                    keysToRemoveFromMap.map { it.name to it.type.toString() }.unzip()

                // If keyToKeyUpdateCallbackWithExecutorMap is empty, it means there is no callback
                // registered Therefore, we can safely unregister from DataBridgeProxy to prevent
                // unnecessary IPC
                // calls.
                dataBridgeProxy.removeKeysFromUpdates(
                    uuid,
                    keyNames,
                    keyTypes,
                    keyToKeyUpdateCallbackWithExecutorMap.isEmpty(),
                )
            }
        }

        private fun getResultFailureFromThrowable(
            exceptionName: String,
            exceptionMessage: String,
        ): Result<Any?> {
            val throwable = createAndReturnThrowable(exceptionName, exceptionMessage)
            return Result.failure(throwable)
        }

        private fun createAndReturnThrowable(
            exceptionName: String,
            exceptionMessage: String,
        ): Throwable {
            if (exceptionName == java.lang.ClassCastException::class.java.canonicalName) {
                return ClassCastException(exceptionMessage)
            } else if (exceptionName == IOException::class.java.canonicalName) {
                return IOException(exceptionMessage)
            }
            return IllegalStateException(exceptionMessage)
        }

        private fun sendKeyUpdates(key: Key, value: Any?) {
            synchronized(lock) {
                if (!keyToKeyUpdateCallbackWithExecutorMap.containsKey(key)) {
                    return
                }

                keyToKeyUpdateCallbackWithExecutorMap[key]?.forEach { keyUpdateCallbackWithExecutor
                    ->
                    keyUpdateCallbackWithExecutor.executor.execute {
                        keyUpdateCallbackWithExecutor.keyUpdateCallback.onKeyUpdated(key, value)
                    }
                }
            }
        }
    }
}
