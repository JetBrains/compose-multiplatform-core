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

package androidx.camera.integration.featurecombo

import androidx.camera.camera2.Camera2Config
import androidx.camera.camera2.pipe.integration.CameraPipeConfig
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraXConfig
import androidx.camera.core.ExperimentalSessionConfig
import androidx.camera.core.SessionConfig
import androidx.camera.core.UseCase
import androidx.camera.core.featuregroup.GroupableFeature
import androidx.camera.integration.featurecombo.FeatureGroupTestBase.Companion.SupportedUseCase.*
import androidx.camera.testing.impl.CameraUtil
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.VideoCapture
import androidx.test.filters.LargeTest
import com.google.common.truth.Truth.assertWithMessage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@OptIn(ExperimentalSessionConfig::class)
@LargeTest
@RunWith(Parameterized::class)
class FeatureCombinationDeviceTest(
    private val testName: String,
    private val cameraSelector: CameraSelector,
    implName: String,
    cameraXConfig: CameraXConfig,
    private val useCasesToTest: List<FeatureGroupTestBase.Companion.SupportedUseCase>,
) : FeatureGroupTestBase(cameraSelector, implName, cameraXConfig) {
    @Test
    fun bindToLifecycle_allFeaturesPreferred_canBindSuccessfully(): Unit = runBlocking {
        bindAndVerifyFeatures(useCasesToTest.toUseCases(), allFeatures.toList())
    }

    @Test
    fun isFeatureGroupSupported_queryReturnsFalseWithUnselectedPreferredFeatures(): Unit =
        runBlocking {
            // Arrange: Bind with all features as preferred and store the selected ones.
            val useCases = useCasesToTest.toUseCases()
            val features = allFeatures.toList()
            val selectedFeatures = bindAndVerifyFeatures(useCases, features)

            // Act & assert: Ensure query returns false for each of the unselected features added
            //   to the selected ones.
            features.forEach { feature ->
                if (selectedFeatures.contains(feature)) return@forEach

                assertWithMessage(
                        "selectedFeatures = $selectedFeatures, newly added feature = $feature"
                    )
                    .that(
                        cameraProvider
                            .getCameraInfo(cameraSelector)
                            .isFeatureGroupSupported(
                                SessionConfig(
                                    useCases = useCases,
                                    requiredFeatureGroup = selectedFeatures + feature,
                                )
                            )
                    )
                    .isFalse()
            }
        }

    @Test
    fun bindToLifecycle_uhdRecordingAndAllFeaturesPreferred_canBindSuccessfully(): Unit =
        runBlocking {
            assumeTrue(useCasesToTest.contains(VIDEO_CAPTURE))

            val useCases = useCasesToTest.toUseCases().recordingQualityToUhd()

            bindAndVerifyFeatures(useCases, allFeatures.toList())
        }

    @Test
    fun isFeatureGroupSupported_queryReturnsFalseWithUnselectedPreferredFeatures_forUhd(): Unit =
        runBlocking {
            assumeTrue(useCasesToTest.contains(VIDEO_CAPTURE))

            // Arrange: Bind with all features preferred and store the selected ones + UHD recording
            val useCases = useCasesToTest.toUseCases().recordingQualityToUhd()
            val features = allFeatures.toList()
            val selectedFeatures = bindAndVerifyFeatures(useCases, features)

            // Act & assert: Ensure query returns false for each of the unselected features added
            //   to the selected ones.
            features.forEach { feature ->
                if (selectedFeatures.contains(feature)) return@forEach

                assertWithMessage(
                        "selectedFeatures = $selectedFeatures, newly added feature = $feature"
                    )
                    .that(
                        cameraProvider
                            .getCameraInfo(cameraSelector)
                            .isFeatureGroupSupported(
                                SessionConfig(
                                    useCases = useCases,
                                    requiredFeatureGroup = selectedFeatures + feature,
                                )
                            )
                    )
                    .isFalse()
            }
        }

    private suspend fun bindAndVerifyFeatures(
        useCases: List<UseCase>,
        preferredFeatures: List<GroupableFeature>,
    ): Set<GroupableFeature> {
        val selectedFeatures = CompletableDeferred<Set<GroupableFeature>>()

        val sessionConfig =
            SessionConfig(useCases = useCases, preferredFeatureGroup = preferredFeatures).apply {
                setFeatureSelectionListener { features -> selectedFeatures.complete(features) }
            }

        withContext(Dispatchers.Main) {
                // TODO: b/437820285 - Remove and make the tests simpler once UHD recording
                //  GroupableFeature is created.
                assumeTrue(
                    cameraProvider
                        .getCameraInfo(cameraSelector)
                        .isFeatureGroupSupported(sessionConfig)
                )

                cameraProvider.bindToLifecycle(fakeLifecycleOwner, cameraSelector, sessionConfig)
            }
            .apply { selectedFeatures.await().verifyFeatures(useCases, cameraInfo) }

        return selectedFeatures.await()
    }

    // TODO: b/437820285 - Remove and make the tests simpler once UHD recording GroupableFeature is
    //  created.
    private fun List<UseCase>.recordingQualityToUhd(): List<UseCase> = map {
        if (it is VideoCapture<*>) {
            VideoCapture.withOutput(
                Recorder.Builder()
                    .setQualitySelector(QualitySelector.fromOrderedList(listOf(Quality.UHD)))
                    .build()
            )
        } else {
            it
        }
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun data() =
            mutableListOf<Array<Any?>>().apply {
                CameraUtil.getAvailableCameraSelectors().forEach { selector ->
                    val lens = selector.lensFacing

                    useCaseCombinationsToTest.forEach { useCases ->
                        add(
                            arrayOf(
                                "config=${Camera2Config::class.simpleName} lensFacing={$lens}" +
                                    " useCases = {$useCases}",
                                selector,
                                Camera2Config::class.simpleName,
                                Camera2Config.defaultConfig(),
                                useCases,
                            )
                        )

                        add(
                            arrayOf(
                                "config=${CameraPipeConfig::class.simpleName} lensFacing={$lens}" +
                                    " useCases = {$useCases}",
                                selector,
                                CameraPipeConfig::class.simpleName,
                                CameraPipeConfig.defaultConfig(),
                                useCases,
                            )
                        )
                    }
                }
            }
    }
}
