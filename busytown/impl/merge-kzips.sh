#!/bin/bash
#
# Copyright 2024 The Android Open Source Project
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
#
# Merge kzip files (source files for the indexing pipeline) for the given configuration, and place
# the resulting all.kzip into $DIST_DIR.
# Most code from:
# https://cs.android.com/android/platform/superproject/main/+/main:build/soong/build_kzip.bash;

set -e

# Absolute path of the directory where this script lives
SCRIPT_DIR="$(cd $(dirname $0) && pwd)"

PREBUILTS_DIR=$SCRIPT_DIR/../../../../prebuilts

cd "$SCRIPT_DIR/../.."
if [ "$OUT_DIR" == "" ]; then
  OUT_DIR="../../out"
fi
mkdir -p "$OUT_DIR"
export OUT_DIR="$(cd $OUT_DIR && pwd)"
if [ "$DIST_DIR" == "" ]; then
  DIST_DIR="$OUT_DIR/dist"
fi
mkdir -p "$DIST_DIR"
export DIST_DIR="$DIST_DIR"

REVISION=$(grep 'path="frameworks/support"' "$MANIFEST" | sed -n 's/.*revision="\([^"]*\).*/\1/p')

# Default KZIP_NAME to the revision value from the XML file
: ${KZIP_NAME:=$REVISION}

# Fallback to the latest Git commit hash if revision is not found
: ${KZIP_NAME:=$(git rev-parse HEAD)}

# Fallback to a UUID if both the revision and Git commit hash are not there
: ${KZIP_NAME:=$(uuidgen)}


rm -rf $DIST_DIR/*.kzip
declare -r allkzip="$KZIP_NAME.kzip"
echo "Merging Kzips..."

# Determine the directory based on OS
if [[ "$(uname)" == "Darwin" ]]; then
  BUILD_TOOLS_DIR="$PREBUILTS_DIR/build-tools/darwin-x86/bin"
else
  BUILD_TOOLS_DIR="$PREBUILTS_DIR/build-tools/linux-x86/bin"
fi

"$BUILD_TOOLS_DIR/merge_zips" "$DIST_DIR/$allkzip" @<(find "$OUT_DIR/androidx" -name '*.kzip')
