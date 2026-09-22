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


tasks.register("publish") {
    group = "publishing"

    dependsOn(
        ":mpp:publishComposeJb",
        ":fleet:lifecycle:lifecycle-all-desktop:publish",
        ":fleet:compose:runtime:runtime-all-desktop:publish",
        ":fleet:compose:ui:ui-all-desktop:publish",
        ":fleet:navigationevent:navigationevent-all-desktop:publish",
    )
}

// Same name as the Gradle `publishToMavenLocal` lifecycle task, but not the same result as
// `./fleet/publishToMavenLocal.sh`: the script passes -Pjetbrains.publication.snapshot=true,
// which this task has no way to set for itself, so running it through `./gradlew` writes
// RELEASE-versioned artifacts while the script writes snapshots. Use the script; it's the
// intended entry point.
tasks.register("publishToMavenLocal") {
    group = "publishing"

    dependsOn(
        ":mpp:publishComposeJbToMavenLocal",
        ":fleet:lifecycle:lifecycle-all-desktop:publishToMavenLocal",
        ":fleet:compose:runtime:runtime-all-desktop:publishToMavenLocal",
        ":fleet:compose:ui:ui-all-desktop:publishToMavenLocal",
        ":fleet:navigationevent:navigationevent-all-desktop:publishToMavenLocal",
    )
}