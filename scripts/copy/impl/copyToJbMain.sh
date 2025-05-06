#!/bin/bash

# This script is rarely needed to be used explicitly.
# Use parent scripts and see the parent Readme.md

set -e

if [ -z "$1" ]; then
echo "Specify folders to copy to jb-main. For example: ./copyToJbMain.sh compose ':(exclude)compose/material3'"
exit 1
fi

DIR=$(dirname "$0")
ROOT_DIR="$(dirname "$0")/../../.."
ALL_FOLDERS=${@:1}
FIRST_FOLDER=$1
FIRST_FOLDER=${FIRST_FOLDER////-} # replace / by -
CURRENT_COMMIT=$(git rev-parse --short @)
BRANCH_TO_RESTORE_IN_THE_END=$(git branch --show-current)


JB_MAIN_BRANCH=$(git config branch.jb-main.remote)/jb-main
TO_JB_MAIN_BRANCH=integration-copy/$FIRST_FOLDER/$CURRENT_COMMIT/to-jb-main
git checkout --quiet $(git merge-base $CURRENT_COMMIT $JB_MAIN_BRANCH) -B $TO_JB_MAIN_BRANCH
(
    cd $ROOT_DIR;
    git checkout --quiet --no-overlay $CURRENT_COMMIT -- $ALL_FOLDERS;
    git commit --quiet -m "Copy $FIRST_FOLDER from $CURRENT_COMMIT"
)
echo "Created $TO_JB_MAIN_BRANCH"

TO_INTEGRATION_BRANCH=integration-copy/$FIRST_FOLDER/$CURRENT_COMMIT/to-integration
git checkout --quiet $CURRENT_COMMIT -B $TO_INTEGRATION_BRANCH
$DIR/mergeEmpty.sh $TO_JB_MAIN_BRANCH
echo "Created $TO_INTEGRATION_BRANCH"


git checkout --quiet $BRANCH_TO_RESTORE_IN_THE_END