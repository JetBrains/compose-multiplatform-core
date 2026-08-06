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

# Resolve the repository from the script's own location so the recipe works from
# any working directory.
readonly REPO_ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"

# wasmjs is here because Fleet's wasm Bazel modules link against the klibs. A
# desktop-only publish leaves them behind at whatever revision last published
# them, and the staleness only shows up much later as an unresolved reference to
# a symbol that plainly exists in the source.
"$REPO_ROOT/gradlew" --project-dir "$REPO_ROOT" \
  -Pcompose.platforms=desktop,wasmjs \
  -Pjetbrains.publication.libraries=COMPOSE,LIFECYCLE,SAVEDSTATE \
  -Pjetbrains.publication.version.COMPOSE=1.9.0-0-fleet-SNAPSHOT \
  -Pjetbrains.publication.version.LIFECYCLE=2.9.2-0-fleet-SNAPSHOT \
  -Pjetbrains.publication.version.SAVEDSTATE=1.3.0-0-fleet-SNAPSHOT \
  :mpp:publishComposeJb \
  :fleet:lifecycle:lifecycle-all-desktop:publish \
  :fleet:compose:runtime:runtime-all-desktop:publish \
  :fleet:compose:ui:ui-all-desktop:publish \
  "$@"
