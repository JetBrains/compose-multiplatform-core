#!/usr/bin/env bash
#
# Copyright 2025 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

../gradlew \
  -Pcompose.platforms=desktop \
  -Pjetbrains.publication.libraries=COMPOSE,LIFECYCLE,SAVEDSTATE \
  -Pjetbrains.publication.version.COMPOSE=1.9.0-0-fleet-SNAPSHOT \
  -Pjetbrains.publication.version.LIFECYCLE=2.9.2-0-fleet-SNAPSHOT \
  -Pjetbrains.publication.version.SAVEDSTATE=1.3.0-0-fleet-SNAPSHOT \
  :mpp:publishComposeJbToMavenLocal \
  :fleet:lifecycle:lifecycle-all-desktop:publishToMavenLocal \
  :fleet:compose:runtime:runtime-all-desktop:publishToMavenLocal \
  :fleet:compose:ui:ui-all-desktop:publishToMavenLocal \
  "$@"
