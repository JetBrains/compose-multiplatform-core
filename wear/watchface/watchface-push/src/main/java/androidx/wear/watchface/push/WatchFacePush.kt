/*
 * Copyright 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package androidx.wear.watchface.push

import android.content.Context
import android.os.OutcomeReceiver
import android.os.ParcelFileDescriptor
import androidx.annotation.IntDef
import androidx.core.content.ContextCompat
import com.google.wear.Sdk
import com.google.wear.services.watchfaces.watchfacepush.WatchFacePushManager
import java.util.concurrent.Executor
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.suspendCancellableCoroutine

private const val PERMISSION_NAME = "com.google.wear.permission.SET_PUSHED_WATCH_FACE_AS_ACTIVE"

/**
 * The Watch Face Push API allows a Wear OS app to install a watch face on a watch programmatically.
 * The watch faces will then be available in the watch carousel similarly to watch faces that are
 * downloaded from Play Store. **It's important to note that all functions in this API operate only
 * on watch faces that have been added by the calling application.** Watch faces added by other apps
 * or pre-existing on the device cannot be managed using this API.
 *
 * Example usage:
 * <pre>
 * <code>lateinit var wf1: android.os.ParcelFileDescriptor
 *   val token1 = "1234" // Get it from the provided validation library.
 *   lateinit var wf2: android.os.ParcelFileDescriptor
 *   val token2 = "4567"
 *   val wfp = WatchFacePushManager(context)
 *   with(wfp) {
 *     val slot = addWatchFace(wf1, token1)
 *     setWatchFaceAsActive(slot.slotId)
 *     updateWatchFace(slot.slotId, wf2, token2)
 *     removeWatchFace(slot.slotId)
 *   }</code>
 * </pre>
 *
 * @param context The application context.
 */
public class WatchFacePushManager(private var context: Context) {
    private val receiverManager: WatchFacePushManager =
        Sdk.getWearManager(context, WatchFacePushManager::class.java)

    /**
     * Lists all watch faces that were added by the app invoking this method. Watch faces added by
     * other apps will not be included in the response.
     *
     * @return A [ListWatchFacesResponse] containing the list of installed watch face details and
     *   the number of available slots for this application.
     * @throws [ListWatchFacesException] if there is an error while retrieving the watch faces. This
     *   could happen if the Watch Face Push service on the watch cannot be accessed. See
     *   [ListWatchFacesException.errorCode] for details.
     */
    public suspend fun listWatchFaces(): ListWatchFacesResponse {
        val currentExecutor = executor()
        return suspendCancellableCoroutine { cont ->
            receiverManager.listWatchFaceSlots(
                currentExecutor,
                outcomeReceiver(
                    cont,
                    { result ->
                        ListWatchFacesResponse(
                            installedWatchFaceDetails =
                                (result?.installedWatchFaceSlots ?: emptyList()).map { w ->
                                    WatchFaceDetails(w)
                                },
                            remainingSlotCount = result?.availableSlotCount ?: 0
                        )
                    },
                    { e -> ListWatchFacesException(e) }
                )
            )
        }
    }

    /**
     * Removes an existing watch face that was previously added by this application. On success, the
     * watch face will no longer be available in the watch face carousel on the watch. Note that
     * this method can be used to remove the currently active watch face - in that case, the watch
     * will revert to one of the other existing watch faces. **Watch faces added by other apps or
     * pre-existing on the device cannot be removed using this method.**
     *
     * @param slotId The unique identifier of the watch face to be removed. This ID corresponds to
     *   the [WatchFaceDetails.slotId] of the watch face.
     * @throws [RemoveWatchFaceException] if there is an error while removing the watch face. This
     *   could happen if the provided [slotId] is invalid or if the Watch Face Push service on the
     *   watch cannot be accessed. See [RemoveWatchFaceException.errorCode] for details.
     * @see addWatchFace
     */
    public suspend fun removeWatchFace(slotId: String) {
        val currentExecutor = executor()
        return suspendCancellableCoroutine { cont ->
            receiverManager.removeWatchFace(
                slotId,
                currentExecutor,
                outcomeReceiver(cont, {}, { it -> RemoveWatchFaceException(it) })
            )
        }
    }

    /**
     * Adds a new watch face. On success, the given watch face will be available in the watch face
     * carousel on the watch. Note that calling this method will not change the currently active
     * watch face. See also [setWatchFaceAsActive].
     *
     * @param apkFd The [ParcelFileDescriptor] containing the watch face APK.
     * @param validationToken A token proving that the watch face has gone through the required
     *   validation checks.
     * @return The [WatchFaceDetails] representing the added watch face in its assigned slot.
     * @throws [AddWatchFaceException] if there is an error while adding the watch face. This could
     *   happen if the provided APK is malformed, the validation token is invalid, or if the Watch
     *   Face Push service on the watch cannot be accessed. See [AddWatchFaceException.errorCode]
     *   for the possible errors thrown by this method if the watch face cannot be added.
     */
    public suspend fun addWatchFace(
        apkFd: ParcelFileDescriptor,
        validationToken: String
    ): WatchFaceDetails {
        val currentExecutor = executor()
        return suspendCancellableCoroutine { cont ->
            receiverManager.addWatchFace(
                apkFd,
                validationToken,
                currentExecutor,
                outcomeReceiver(
                    cont,
                    { result -> WatchFaceDetails(result!!) },
                    { it -> AddWatchFaceException(it) }
                )
            )
        }
    }

    /**
     * Updates a watch face slot with a new watch face. **Watch faces added by other apps or already
     * existing on the device cannot be updated using this method.** The new watch face could be a
     * newer version of the existing watch face or a completely different watch face. If the slot is
     * updated with a watch face that has the same package name as the existing watch face, all the
     * associated user configuration settings of the watch face will be preserved. If the package
     * name is different, the user configuration settings will be reset to the default values.
     *
     * @param slotId The slot ID to update.
     * @param apkFd The [ParcelFileDescriptor] containing the new watch face APK.
     * @param validationToken A token proving that the watch face has gone through the required
     *   validation checks.
     * @return The [WatchFaceDetails] representing the updated watch face in the specified slot.
     * @throws [UpdateWatchFaceException] if there is an error while updating the watch face. This
     *   could happen if the provided APK is malformed, the validation token is invalid, or if the
     *   Watch Face Push service on the watch cannot be accessed. See
     *   [UpdateWatchFaceException.errorCode] for the possible errors thrown by this method if the
     *   watch face cannot be updated.
     */
    public suspend fun updateWatchFace(
        slotId: String,
        apkFd: ParcelFileDescriptor,
        validationToken: String
    ): WatchFaceDetails {
        val currentExecutor = executor()
        return suspendCancellableCoroutine { cont ->
            receiverManager.updateWatchFace(
                slotId,
                apkFd,
                validationToken,
                currentExecutor,
                outcomeReceiver(
                    cont,
                    { result -> WatchFaceDetails(result!!) },
                    { e -> UpdateWatchFaceException(e) }
                )
            )
        }
    }

    /**
     * Checks if a watch face with the given package name is active. **This method can only be used
     * to check the active status of watch faces installed by this application.**
     *
     * @param watchfacePackageName The package name of the watch face to check.
     * @return `true` if the watch face is active, `false` otherwise.
     * @throws [IsWatchFaceActiveException] if there is an error while checking if the watch face is
     *   active. This could happen if the provided [watchfacePackageName] is invalid or if the Watch
     *   Face Push service on the watch cannot be accessed. See
     *   [IsWatchFaceActiveException.errorCode] for details.
     */
    public suspend fun isWatchFaceActive(watchfacePackageName: String): Boolean {
        val currentExecutor = executor()
        return suspendCancellableCoroutine { cont: CancellableContinuation<Boolean> ->
            receiverManager.isWatchFaceActive(
                watchfacePackageName,
                currentExecutor,
                outcomeReceiver(cont, { t: Boolean -> t }, { e -> IsWatchFaceActiveException(e) })
            )
        }
    }

    /**
     * Sets a watch face with the given slot ID as the active watch face. **This method can only be
     * used to set watch faces installed by this application as active.**
     *
     * @param slotId The slot ID of the watch face to set as active.
     * @throws [SetWatchFaceAsActiveException] if there is an error while setting the watch face as
     *   active. This could happen if the provided [slotId] is invalid, the maximum number of
     *   attempts to set the watch face as active has been reached, the required permission is
     *   missing, or if the Watch Face Push service on the watch cannot be accessed. See
     *   [SetWatchFaceAsActiveException.errorCode] for details.
     */
    public suspend fun setWatchFaceAsActive(slotId: String) {
        val currentExecutor = executor()
        return suspendCancellableCoroutine { cont ->
            if (
                ContextCompat.checkSelfPermission(context, PERMISSION_NAME) ==
                    android.content.pm.PackageManager.PERMISSION_DENIED
            ) {
                throw SetWatchFaceAsActiveException(
                    SetWatchFaceAsActiveException.ERROR_MISSING_PERMISSION
                )
            }
            receiverManager.setWatchFaceAsActive(
                slotId,
                currentExecutor,
                outcomeReceiver(cont, {}, { e -> SetWatchFaceAsActiveException(e) })
            )
        }
    }

    private suspend fun executor(): Executor {
        return (currentCoroutineContext()[ContinuationInterceptor] as CoroutineDispatcher)
            .asExecutor()
    }

    /**
     * Helper method that provides an outcome receiver that converts an error into an exception.
     *
     * @param <T> The type of the value received from the remote service.
     * @param <R> The type of the value to be returned by the call.
     * @param transform A function that transforms the received value of type {@code T} from the
     *   remote service into the desired return type {@code R}.
     * @param transformException A function that transforms a remote service exception into an
     *   AndroidX-compatible exception.
     */
    private fun <I, O, E : Throwable, EO : Throwable> outcomeReceiver(
        cont: CancellableContinuation<O>,
        transform: (I) -> O,
        transformException: (E) -> EO
    ): OutcomeReceiver<I, E> {
        return object : OutcomeReceiver<I, E> {
            override fun onResult(result: I) {
                cont.resume(transform(result))
            }

            override fun onError(error: E) {
                super.onError(error)
                cont.resumeWithException(transformException(error))
            }
        }
    }

    /**
     * Represents the response from listing watch faces. See [WatchFacePushManager.listWatchFaces]
     *
     * @property installedWatchFaceDetails The list of installed watch face slots. **This list only
     *   contains watch faces that were added by the calling application.**
     * @property remainingSlotCount The remaining number of slots that can be used by this
     *   application to add more watch faces.
     */
    public class ListWatchFacesResponse(
        public val installedWatchFaceDetails: List<WatchFaceDetails>,
        public val remainingSlotCount: Int
    )

    /**
     * Details about a watch face that is installed through this API. Once installed, a watch face
     * gets assigned a "slot" that can be then updated with another watch face. Similarly, the watch
     * face in the slot can be deleted, freeing the slot. Each calling app has a limited number of
     * slots that can be utilized.
     *
     * @property slotId The unique slot ID assigned to this watch face. This ID is used to reference
     *   this watch face for subsequent operations like updating or removing.
     * @property versionCode The version code of the watch face defined in the watch face manifest
     *   file.
     * @property packageName The package name of the watch face defined in the watch face manifest
     *   file..
     */
    public class WatchFaceDetails(
        private val slot: com.google.wear.services.watchfaces.watchfacepush.WatchFaceSlot
    ) {
        public val slotId: String
            get() = slot.slotId

        public val versionCode: Long
            get() = slot.versionCode

        public val packageName: String
            get() = slot.packageName

        /**
         * Returns a function that, when invoked, returns the list of metadata values for the given
         * key.
         *
         * @param key The key for the metadata to retrieve.
         * @return A function returning a list of metadata values.
         */
        public fun getMetaData(key: String): () -> List<String> = { slot.getMetaDataValues(key) }
    }

    /** An exception that can be thrown by [addWatchFace] */
    public class AddWatchFaceException(
        private val rootCause: WatchFacePushManager.AddException,
    ) : Exception(rootCause) {

        internal companion object {
            /**
             * Unknown error while adding a watch face.
             *
             * This typically means that the Watch Face Push service on the watch could not be
             * accessed or that the watch may be in a bad state.
             */
            const val ERROR_UNKNOWN: Int = WatchFacePushManager.AddException.ADD_UNKNOWN_ERROR

            /**
             * Unexpected content in the APK.
             *
             * The APK must be a WFF watchface which only contains the watchface XML file and the
             * associated resources. The APK can't contain any executable code. Developers should
             * ensure that the APK conforms to the Watch Face Format.
             */
            const val ERROR_UNEXPECTED_CONTENT: Int =
                WatchFacePushManager.AddException.ADD_SECURITY_ERROR

            /**
             * The package name of the watch face is invalid.
             *
             * The package name of the watch face must start with the package name of the Watch Face
             * Push client, followed by the 'watchfacepush' keyword, ending with the unique watch
             * face name. Developers should verify that the package name follows this format.
             */
            const val ERROR_INVALID_PACKAGE_NAME: Int =
                WatchFacePushManager.AddException.ADD_INVALID_PACKAGE_NAME_ERROR

            /**
             * The provided watch face is not a valid Android APK.
             *
             * Developers should ensure that the provided file is a valid APK file.
             */
            const val ERROR_MALFORMED_WATCHFACE_APK: Int =
                WatchFacePushManager.AddException.ADD_INVALID_CONTENT_ERROR

            /**
             * The limit of watch faces that can be installed by this application has been reached.
             *
             * No more watch faces can be added. Developers should instruct the user to remove
             * existing watch faces added by this app before attempting to add new ones.
             */
            const val ERROR_SLOT_LIMIT_REACHED: Int =
                WatchFacePushManager.AddException.ADD_SLOT_LIMIT_REACHED_ERROR

            /**
             * The validation token provided does not match the watch face.
             *
             * Developers should see the Watch Face Push documentation to see how to generate a
             * validation token correctly.
             */
            const val ERROR_INVALID_VALIDATION_TOKEN: Int =
                WatchFacePushManager.AddException.ADD_INVALID_VALIDATION_TOKEN_ERROR

            /**
             * Defines the allowed integer values for [addWatchFace] error codes.
             *
             * Possible values are:
             * - [ERROR_UNKNOWN]
             * - [ERROR_UNEXPECTED_CONTENT]
             * - [ERROR_INVALID_PACKAGE_NAME]
             * - [ERROR_MALFORMED_WATCHFACE_APK]
             * - [ERROR_SLOT_LIMIT_REACHED]
             * - [ERROR_INVALID_VALIDATION_TOKEN]
             */
            @IntDef(
                ERROR_UNKNOWN,
                ERROR_UNEXPECTED_CONTENT,
                ERROR_INVALID_PACKAGE_NAME,
                ERROR_MALFORMED_WATCHFACE_APK,
                ERROR_SLOT_LIMIT_REACHED,
                ERROR_INVALID_VALIDATION_TOKEN
            )
            @Retention(AnnotationRetention.SOURCE)
            @Target(AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.TYPE)
            internal annotation class ErrorCode
        }

        /** The specific subtype of error occurred. See [ErrorCode] for the possible values. */
        public val errorCode: @ErrorCode Int
            get() = rootCause.errorCode

        override val message: String?
            get() =
                when (rootCause.errorCode) {
                    ERROR_UNKNOWN ->
                        "Unknown error while adding a watch face. Typically this means that the Watch Face Push service on the watch could not be accessed."
                    ERROR_UNEXPECTED_CONTENT ->
                        "Unexpected content in the APK. The APK must contain only the XML file and the associated resources that are part of the Watch Face Format. The APK can't contain any executable code"
                    ERROR_INVALID_PACKAGE_NAME ->
                        "The package name of the watch face must start with the package name of the Watch Face Push client, followed by the 'watchfacepush' keyword, ending with the unique watch face name."
                    ERROR_MALFORMED_WATCHFACE_APK ->
                        "The provided watch face is not a valid Android APK."
                    ERROR_SLOT_LIMIT_REACHED ->
                        "The limit of watch faces that can be installed by this application has been reached. No more watch faces can be added."
                    ERROR_INVALID_VALIDATION_TOKEN ->
                        "The validation token provided does not match the watch face. Please see the Watch Face Push documentation to see how to generate a validation token correctly."
                    else -> "Unknown error code"
                }
    }

    /** An exception that can be thrown by [updateWatchFace] */
    public class UpdateWatchFaceException(
        private val rootCause: WatchFacePushManager.UpdateException,
    ) : Exception(rootCause) {

        internal companion object {
            /**
             * Unknown error while updating a watch face. Typically this means that the Watch Face
             * Push service on the watch could not be accessed or that the watch may be in a bad
             * state.
             */
            const val ERROR_UNKNOWN: Int = WatchFacePushManager.UpdateException.UPDATE_UNKNOWN_ERROR

            /**
             * Unexpected content in the APK. The APK must be a WFF watchface which only contains
             * the watchface XML file and the associated resources. The APK can't contain any
             * executable code.
             */
            const val ERROR_UNEXPECTED_CONTENT: Int =
                WatchFacePushManager.UpdateException.UPDATE_SECURITY_ERROR

            /**
             * The package name of the watch face is invalid.
             *
             * The package name of the watch face must start with the package name of the Watch Face
             * Push client, followed by the 'watchfacepush' keyword, ending with the unique watch
             * face name.
             */
            const val ERROR_INVALID_PACKAGE_NAME: Int =
                WatchFacePushManager.UpdateException.UPDATE_INVALID_PACKAGE_NAME_ERROR

            /** The provided watch face is not a valid Android APK. */
            const val ERROR_MALFORMED_WATCHFACE_APK: Int =
                WatchFacePushManager.UpdateException.UPDATE_INVALID_CONTENT_ERROR

            /**
             * The slot ID provided is not valid. The watch face might have been removed previously,
             * or the ID is simply incorrect. Developers should make sure to retrieve slot IDs by
             * calling [listWatchFaces] or [addWatchFace].
             */
            const val ERROR_INVALID_SLOT_ID: Int =
                WatchFacePushManager.UpdateException.UPDATE_INVALID_SLOT_ID_ERROR

            /**
             * The validation token provided does not match the watch face. Please see the Watch
             * Face Push documentation to see how to generate a validation token correctly.
             */
            const val ERROR_INVALID_VALIDATION_TOKEN: Int =
                WatchFacePushManager.UpdateException.UPDATE_INVALID_VALIDATION_TOKEN_ERROR

            /**
             * Defines the allowed integer values for [updateWatchFace] error codes.
             *
             * Possible values are:
             * - [ERROR_UNKNOWN]
             * - [ERROR_UNEXPECTED_CONTENT]
             * - [ERROR_INVALID_PACKAGE_NAME]
             * - [ERROR_MALFORMED_WATCHFACE_APK]
             * - [ERROR_INVALID_SLOT_ID]
             * - [ERROR_INVALID_VALIDATION_TOKEN]
             */
            @IntDef(
                ERROR_UNKNOWN,
                ERROR_UNEXPECTED_CONTENT,
                ERROR_INVALID_PACKAGE_NAME,
                ERROR_MALFORMED_WATCHFACE_APK,
                ERROR_INVALID_SLOT_ID,
                ERROR_INVALID_VALIDATION_TOKEN
            )
            @Retention(AnnotationRetention.SOURCE)
            @Target(AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.TYPE)
            internal annotation class ErrorCode
        }

        /** The specific subtype of error occurred. See [ErrorCode] for the possible values. */
        public val errorCode: @ErrorCode Int
            get() = rootCause.errorCode

        override val message: String?
            get() =
                when (rootCause.errorCode) {
                    ERROR_UNKNOWN ->
                        "Unknown error while updating a watch face. Typically this means that the Watch Face Push service on the watch could not be accessed."
                    ERROR_UNEXPECTED_CONTENT ->
                        "Unexpected content in the APK. The APK must contain only the XML file and the associated resources that are part of the Watch Face Format. The APK can't contain any executable code"
                    ERROR_INVALID_PACKAGE_NAME ->
                        "The package name of the watch face must start with the package name of the Watch Face Push client, followed by the 'watchfacepush' keyword, ending with the unique watch face name."
                    ERROR_MALFORMED_WATCHFACE_APK ->
                        "The provided watch face is not a valid Android APK."
                    ERROR_INVALID_SLOT_ID ->
                        "The provided slot ID is not valid. The watch face might have been removed previously, or the ID is incorrect. Ensure you retrieve slot IDs by calling listWatchFaces or addWatchFace."
                    ERROR_INVALID_VALIDATION_TOKEN ->
                        "The validation token provided does not match the watch face. Please see the Watch Face Push documentation to see how to generate a validation token correctly."
                    else -> "Unknown error code"
                }
    }

    /** An exception that can be thrown by [removeWatchFace] */
    public class RemoveWatchFaceException(
        private val rootCause: WatchFacePushManager.RemoveException,
    ) : Exception(rootCause) {

        internal companion object {
            /**
             * Unknown error while removing a watch face. Typically this means that the Watch Face
             * Push service on the watch could not be accessed or that the watch may be in a bad
             * state.
             */
            const val ERROR_UNKNOWN: Int = WatchFacePushManager.RemoveException.REMOVE_UNKNOWN_ERROR

            /**
             * The slot ID provided is not valid. The watch face might have been removed previously,
             * or the ID is simply incorrect. Developers should make sure to retrieve slot IDs by
             * calling [listWatchFaces] or [addWatchFace].
             */
            const val ERROR_INVALID_SLOT_ID: Int =
                WatchFacePushManager.RemoveException.REMOVE_INVALID_SLOT_ID_ERROR

            /**
             * Defines the allowed integer values for [removeWatchFace] error codes.
             *
             * Possible values are:
             * - [ERROR_UNKNOWN]
             * - [ERROR_INVALID_SLOT_ID]
             */
            @IntDef(
                ERROR_UNKNOWN,
                ERROR_INVALID_SLOT_ID,
            )
            @Retention(AnnotationRetention.SOURCE)
            @Target(AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.TYPE)
            internal annotation class ErrorCode
        }

        /** The specific subtype of error occurred. See [ErrorCode] for the possible values. */
        public val errorCode: @ErrorCode Int
            get() = rootCause.errorCode

        override val message: String?
            get() =
                when (rootCause.errorCode) {
                    ERROR_UNKNOWN ->
                        "Unknown error while removing a watch face. Typically this means that the Watch Face Push service on the watch could not be accessed."
                    ERROR_INVALID_SLOT_ID ->
                        "The provided slot ID is not valid. The watch face might have been removed previously, or the ID is incorrect. Ensure you retrieve slot IDs by calling listWatchFaces or addWatchFace."
                    else -> "Unknown error code"
                }
    }

    /** An exception that can be thrown by [setWatchFaceAsActive] */
    public class SetWatchFaceAsActiveException
    private constructor(
        rootCause: WatchFacePushManager.SetActiveException?,
        @ErrorCode public val errorCode: Int,
    ) : Exception(rootCause) {

        public constructor(@ErrorCode errorCode: Int) : this(null, errorCode)

        public constructor(
            rootCause: WatchFacePushManager.SetActiveException?
        ) : this(rootCause, rootCause?.errorCode ?: ERROR_UNKNOWN)

        internal companion object {
            /**
             * Unknown error while setting a watch face as active. Typically this means that the
             * Watch Face Push service on the watch could not be accessed or that the watch may be
             * in a bad state.
             */
            const val ERROR_UNKNOWN: Int =
                WatchFacePushManager.SetActiveException.SET_ACTIVE_UNKNOWN_ERROR

            /**
             * The slot ID provided is not valid. The watch face might have been removed previously,
             * or the ID is simply incorrect. Make sure to retrieve slot IDs by calling
             * [listWatchFaces] or [addWatchFace].
             */
            const val ERROR_INVALID_SLOT_ID: Int =
                WatchFacePushManager.SetActiveException.SET_ACTIVE_INVALID_SLOT_ID_ERROR

            /** The maximum number of attempts to set the watch face as active has been reached. */
            const val ERROR_MAXIMUM_ATTEMPTS_REACHED: Int =
                WatchFacePushManager.SetActiveException.SET_ACTIVE_MAXIMUM_ATTEMPTS_REACHED_ERROR

            /** The required permission to set the watch face as active is missing. */
            // A number that does not conflict with the
            // WatchFacePushManager.SetActiveException namespace
            const val ERROR_MISSING_PERMISSION: Int = 1000

            /**
             * Defines the allowed integer values for [setWatchFaceAsActive] error codes.
             *
             * Possible values are:
             * - [ERROR_UNKNOWN]
             * - [ERROR_INVALID_SLOT_ID]
             * - [ERROR_MAXIMUM_ATTEMPTS_REACHED]
             * - [ERROR_MISSING_PERMISSION]
             */
            @IntDef(
                ERROR_UNKNOWN,
                ERROR_INVALID_SLOT_ID,
                ERROR_MAXIMUM_ATTEMPTS_REACHED,
                ERROR_MISSING_PERMISSION,
            )
            @Retention(AnnotationRetention.SOURCE)
            @Target(AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.TYPE)
            internal annotation class ErrorCode
        }

        /** The specific subtype of error occurred. See [ErrorCode] for the possible values. */
        override val message: String?
            get() =
                when (errorCode) {
                    ERROR_UNKNOWN ->
                        "Unknown error while setting a watch face as active. Typically this means that the Watch Face Push service on the watch could not be accessed."
                    ERROR_INVALID_SLOT_ID ->
                        "The provided slot ID is not valid. The watch face might have been removed previously, or the ID is incorrect. Ensure you retrieve slot IDs by calling listWatchFaces or addWatchFace."
                    ERROR_MAXIMUM_ATTEMPTS_REACHED ->
                        "The maximum number of attempts to set the watch face as active has been reached."
                    ERROR_MISSING_PERMISSION ->
                        "The required permission $PERMISSION_NAME to set the watch face as active is missing."
                    else -> "Unknown error code"
                }
    }

    /** An exception that can be thrown by [isWatchFaceActive] */
    public class IsWatchFaceActiveException(
        private val rootCause: WatchFacePushManager.IsActiveException,
    ) : Exception(rootCause) {

        internal companion object {
            /**
             * Unknown error while querying for watch face. Typically this means that the Watch Face
             * Push service on the watch could not be accessed or that the watch may be in a bad
             * state.
             */
            const val ERROR_UNKNOWN: Int =
                WatchFacePushManager.IsActiveException.IS_ACTIVE_UNKNOWN_ERROR

            /**
             * The package name provided is not valid. The watch face might have been removed
             * previously, or the package name is simply incorrect. Make sure to retrieve the
             * package name by calling [listWatchFaces] or [addWatchFace].
             */
            const val ERROR_INVALID_PACKAGE_NAME: Int =
                WatchFacePushManager.IsActiveException.IS_ACTIVE_FORBIDDEN_ERROR

            /**
             * Defines the allowed integer values for [isWatchFaceActive] error codes.
             *
             * Possible values are:
             * - [ERROR_UNKNOWN]
             * - [ERROR_INVALID_PACKAGE_NAME]
             */
            @IntDef(
                ERROR_UNKNOWN,
                ERROR_INVALID_PACKAGE_NAME,
            )
            @Retention(AnnotationRetention.SOURCE)
            @Target(AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.TYPE)
            internal annotation class ErrorCode
        }

        /** The specific subtype of error occurred. See [ErrorCode] for the possible values. */
        public val errorCode: @ErrorCode Int
            get() = rootCause.errorCode

        override val message: String?
            get() =
                when (rootCause.errorCode) {
                    ERROR_UNKNOWN ->
                        "Unknown error while querying for a watch face. Typically this means that the Watch Face Push service on the watch could not be accessed."
                    ERROR_INVALID_PACKAGE_NAME ->
                        "The provided package name is not valid. The watch face might have been removed previously, or the package name is incorrect. Ensure you retrieve package names by calling listWatchFaces or addWatchFace."
                    else -> "Unknown error code"
                }
    }

    /** An exception that can be thrown by [listWatchFaces] */
    public class ListWatchFacesException(
        private val rootCause: WatchFacePushManager.ListException,
    ) : Exception(rootCause) {

        internal companion object {
            /**
             * Unknown error while listing watch faces. Typically this means that the Watch Face
             * Push service on the watch could not be accessed or that the watch may be in a bad
             * state.
             */
            const val ERROR_UNKNOWN: Int = WatchFacePushManager.ListException.LIST_UNKNOWN_ERROR

            /**
             * Defines the allowed integer values for [isWatchFaceActive] error codes.
             *
             * Possible values are:
             * - [ERROR_UNKNOWN]
             */
            @IntDef(
                ERROR_UNKNOWN,
            )
            @Retention(AnnotationRetention.SOURCE)
            @Target(AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.TYPE)
            internal annotation class ErrorCode
        }

        /** The specific subtype of error occurred. See [ErrorCode] for the possible values. */
        public val errorCode: @ErrorCode Int
            get() = rootCause.errorCode

        override val message: String?
            get() =
                when (rootCause.errorCode) {
                    ERROR_UNKNOWN ->
                        "Unknown error while listing watch faces. Typically this means that the Watch Face Push service on the watch could not be accessed."
                    else -> "Unknown error code"
                }
    }
}
