#!/bin/bash
#
# Copyright 2026 The Android Open Source Project
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

set -e
cd "$(dirname "$0")"

echo "--------------------------------------------------------"
echo "This command will remove those files added to .gitignore"
git clean -ndX
read -p "Do you want to remove this files? (y/n) " yn

case $yn in
	y ) git clean -fdX;;
	n ) exit;;
	* ) exit 1;;
esac
