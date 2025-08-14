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
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraXConfig
import androidx.camera.core.ExperimentalSessionConfig
import androidx.camera.core.SessionConfig
import androidx.camera.core.featuregroup.GroupableFeature
import androidx.camera.integration.featurecombo.FeatureGroupTestBase.Companion.SupportedUseCase
import androidx.camera.testing.impl.CameraUtil
import androidx.test.filters.LargeTest
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@OptIn(ExperimentalSessionConfig::class)
@LargeTest
@RunWith(Parameterized::class)
class FeatureGroupQueryBindAlignmentTest(
    private val testName: String,
    private val cameraSelector: CameraSelector,
    private val implName: String,
    private val cameraXConfig: CameraXConfig,
    private val featureGroup: Set<GroupableFeature>,
    private val useCasesToTest: List<SupportedUseCase>,
) : FeatureGroupTestBase(cameraSelector, implName, cameraXConfig) {
    @Test
    fun testIfBindAndQueryResultsMatch(): Unit = runBlocking {
        val useCases = useCasesToTest.toUseCases()
        val sessionConfig = SessionConfig(useCases = useCases, requiredFeatureGroup = featureGroup)

        val isSupported =
            cameraProvider.getCameraInfo(cameraSelector).isFeatureGroupSupported(sessionConfig)

        val camera = bindAndVerify(sessionConfig, isSupported)

        if (isSupported) {
            featureGroup.verifyFeatures(useCases, requireNotNull(camera?.cameraInfo))
        }
    }

    // TODO: b/419766630 - Add tests where FCQ provides extra support compared to non-FCQ bind flow,
    //  e.g. UHD recording + Preview Stabilization (which is probably not supported right now due to
    //  Preview Stabilization guaranteed table not supporting UHD PRIV). But we first need to wait
    //  for adding FCQ-queryable config combinations supported in Baklava, as Android 15 doesn't
    //  support UHD PRIV for FCQ.

    private suspend fun bindAndVerify(
        sessionConfig: SessionConfig,
        isExpectedToBeSupported: Boolean,
    ): Camera? {
        var caughtException: Exception? = null
        val camera =
            try {
                withContext(Dispatchers.Main) {
                    cameraProvider.bindToLifecycle(
                        fakeLifecycleOwner,
                        cameraSelector,
                        sessionConfig,
                    )
                }
            } catch (e: IllegalArgumentException) {
                caughtException = e
                null
            }

        // If binding is expected to be supported, there should be no exception
        assertThat(caughtException == null).isEqualTo(isExpectedToBeSupported)

        return camera
    }

    companion object {
        @OptIn(ExperimentalSessionConfig::class)
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun data() =
            mutableListOf<Array<Any?>>().apply {
                CameraUtil.getAvailableCameraSelectors().forEach { selector ->
                    val lens = selector.lensFacing

                    // Generates all non-empty subsets of the features to test all combinations
                    for (featureGroup in allFeatures.toPowerSet().filter { it.isNotEmpty() }) {
                        useCaseCombinationsToTest.forEach { useCases ->
                            add(
                                arrayOf(
                                    "config=${Camera2Config::class.simpleName} lensFacing={$lens}" +
                                        " featureGroup={$featureGroup} useCases = {$useCases}",
                                    selector,
                                    Camera2Config::class.simpleName,
                                    Camera2Config.defaultConfig(),
                                    featureGroup,
                                    useCases,
                                )
                            )

                            add(
                                arrayOf(
                                    "config=${CameraPipeConfig::class.simpleName} lensFacing={$lens}" +
                                        " featureGroup={$featureGroup} useCases = {$useCases}",
                                    selector,
                                    CameraPipeConfig::class.simpleName,
                                    CameraPipeConfig.defaultConfig(),
                                    featureGroup,
                                    useCases,
                                )
                            )
                        }
                    }
                }
            }

        /**
         * Returns the power set of the receiver set.
         *
         * The power set of a set S is the set of all subsets of S, including the empty set and S
         * itself.
         *
         * For example, the power set of `{A, B}` is `{{}, {A}, {B}, {A, B}}`.
         *
         * This function iteratively builds the power set. It starts with a set containing just the
         * empty set. Then, for each element in the original set, it creates new subsets by adding
         * the element to all existing subsets in the power set, and adds these new subsets to the
         * power set.
         */
        private fun <T> Set<T>.toPowerSet(): Set<Set<T>> {
            val sets = mutableSetOf<Set<T>>(emptySet())
            for (element in this) {
                sets.addAll(sets.map { it + element })
            }
            return sets
        }
    }
}
