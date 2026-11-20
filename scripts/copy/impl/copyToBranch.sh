#!/bin/bash

# This script is rarely needed to be used explicitly.
# Use parent scripts and see the parent Readme.md

set -e

if [ -z "$1" ]; then
echo "Specify the branch to merge into. For example:"
echo "  ./copyLifecycle.sh jb-main"
echo "  ./copyCompose.sh release/1.12"
exit 1
fi

DESTINATION_BRANCH=$1
shift
if [ -z "$1" ]; then
echo "Specify folders to copy. For example: ./copyToBranch.sh jb-main compose ':(exclude)compose/material3'"
exit 1
fi

DIR=$(dirname "$0")
ROOT_DIR="$(dirname "$0")/../../.."
ALL_FOLDERS=${@:1}
FIRST_FOLDER=$1
FIRST_FOLDER=${FIRST_FOLDER////-} # replace / by -
CURRENT_COMMIT=$(git rev-parse --short @)
BRANCH_TO_RESTORE_IN_THE_END=$(git branch --show-current)


DESTINATION_REF=$(git config "branch.$DESTINATION_BRANCH.remote")/$DESTINATION_BRANCH
TO_JB_MAIN_BRANCH=integration-copy/$FIRST_FOLDER/$CURRENT_COMMIT/to-$DESTINATION_BRANCH
git checkout --quiet $(git merge-base $CURRENT_COMMIT $DESTINATION_REF) -B $TO_JB_MAIN_BRANCH
(
    cd $ROOT_DIR;
    git checkout --quiet --no-overlay $CURRENT_COMMIT -- $ALL_FOLDERS;
    git commit --quiet -m "Copy $FIRST_FOLDER from $CURRENT_COMMIT"
)
echo "Created $TO_JB_MAIN_BRANCH"

git checkout --quiet integration
$DIR/mergeEmpty.sh $TO_JB_MAIN_BRANCH
echo "Merged $TO_JB_MAIN_BRANCH into integration"


git checkout --quiet $BRANCH_TO_RESTORE_IN_THE_END
