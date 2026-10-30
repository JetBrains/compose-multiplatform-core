/*
 * Copyright 2023 The Android Open Source Project
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

import org.gradle.api.initialization.Settings

class SkikoSetup {
    /**
     * Declares the skiko entry in the version catalog of the given settings instance.
     *
     * @param settings The settings instance for the current root project
     */
    static void defineSkikoInVersionCatalog(Settings settings) {
        settings.dependencyResolutionManagement {
            versionCatalogs {
                libs {
                    def skikoOverride = System.getenv("SKIKO_VERSION")
                    if (skikoOverride != null) {
                        org.gradle.api.logging.Logging.getLogger(SkikoSetup.class).warn("Using custom version ${skikoOverride} of SKIKO due to " +
                                "SKIKO_VERSION being set.")
                        version('skiko', skikoOverride)
                    }
                }
            }
        }
    }
}

ext.skikoSetup = new SkikoSetup()