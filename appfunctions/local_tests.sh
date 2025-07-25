#!/bin/bash

# Exit immediately if a command exits with a non-zero status.
# We will temporarily disable this around the Gradle call.
set -e

log() {
  local current_datetime
  current_datetime=$(date +'%Y-%m-%d %H:%M:%S %Z (%z)')
  echo "[$current_datetime] $1"
}

# --- Configuration (Combined) ---
log "Setting up configuration..."
SCHEMA_TEST_DIR="appfunctions-schemas/src/androidTest"
INTEGRATION_TEST_DIR="integration-tests/multi-modules-testapp/app/src/androidTest"
INTEGRATION_BUILD_FILE="integration-tests/multi-modules-testapp/app/build.gradle"

FILES_TO_MODIFY_PATTERN="*.kt"
IGNORE_PATTERN="@Ignore"

# Details for uncommenting the KSP line
COMMENTED_KSP_LINE='//    kspAndroidTest(project(":appfunctions:appfunctions-compiler"))'
UNCOMMENTED_KSP_LINE='    kspAndroidTest(project(":appfunctions:appfunctions-compiler"))'

SCHEMA_GRADLE_TASK=":appfunctions:appfunctions-schema:connectedAndroidTest"
INTEGRATION_GRADLE_TASK=":appfunctions:integration-tests:multi-modules-testapp:app:connectedAndroidTest"

# Combine all paths that will be modified and need restoration
PATHS_TO_RESTORE=("$SCHEMA_TEST_DIR" "$INTEGRATION_TEST_DIR" "$INTEGRATION_BUILD_FILE")

# Variable to store the final exit code, default to 0 (success)
final_exit_code=0

# --- Pre-checks ---
log "Starting pre-checks..."

if [ ! -x "../gradlew" ]; then
  log "ERROR: Gradle wrapper './gradlew' not found or not executable in the current directory."
  log "Please ensure you are running this script from the correct project root directory."
  exit 1
fi

if ! git rev-parse --is-inside-work-tree > /dev/null 2>&1; then
    log "ERROR: Not inside a git repository. 'git restore' command cannot be used."
    exit 1
fi

if [ ! -d "$SCHEMA_TEST_DIR" ]; then
  log "ERROR: Directory '$SCHEMA_TEST_DIR' not found."
  exit 1
fi
if [ ! -d "$INTEGRATION_TEST_DIR" ]; then
  log "ERROR: Directory '$INTEGRATION_TEST_DIR' not found."
  exit 1
fi
if [ ! -f "$INTEGRATION_BUILD_FILE" ]; then
  log "ERROR: File '$INTEGRATION_BUILD_FILE' not found."
  exit 1
fi

log "Pre-checks passed."

# --- Confirmation Prompt ---
set +e
log "---------------------------------------------------------------------"
log "WARNING: This script will perform the following combined actions:"
log "1. Modify $FILES_TO_MODIFY_PATTERN files in '$SCHEMA_TEST_DIR': Remove lines with '$IGNORE_PATTERN'."
log "2. Modify $FILES_TO_MODIFY_PATTERN files in '$INTEGRATION_TEST_DIR': Remove lines with '$IGNORE_PATTERN'."
log "3. Modify '$INTEGRATION_BUILD_FILE': Uncomment KSP line ('$UNCOMMENTED_KSP_LINE')."
log "4. Run Gradle task: '$SCHEMA_GRADLE_TASK' and '$INTEGRATION_GRADLE_TASK'."
log "5. IMPORTANT: Afterwards, 'git restore' will be run on the following paths:"
for path_to_restore in "${PATHS_TO_RESTORE[@]}"; do
    log "   - $path_to_restore"
done
log "   This will DISCARD ALL UNSTAGED CHANGES (including manual edits)"
log "   within these specific paths."
log "   The restore step WILL RUN even if the Gradle task fails."
log "---------------------------------------------------------------------"

read -p "Do you want to continue? (yes/No): " user_confirm

# Re-enable exit on error initially
set -e

# Convert input to lowercase
user_confirm_lower=$(echo "$user_confirm" | tr '[:upper:]' '[:lower:]')

# Check the confirmation input
if [[ "$user_confirm_lower" != "yes" && "$user_confirm_lower" != "y" ]]; then
    log "Aborting script as requested by user."
    exit 1 # Use non-zero exit code for cancellation
fi

log "Confirmation received. Proceeding..."
# --- End Confirmation Prompt ---


# --- Step 1: Perform Modifications ---
log "Performing all modifications..."

# 1a. Schema Test Dir modifications
log "Modifying '$FILES_TO_MODIFY_PATTERN' files in '$SCHEMA_TEST_DIR'..."
find "$SCHEMA_TEST_DIR" -type f -name "$FILES_TO_MODIFY_PATTERN" -exec sed -i "/$IGNORE_PATTERN/d" {} +

# 1b. Integration Test Dir modifications
log "Modifying '$FILES_TO_MODIFY_PATTERN' files in '$INTEGRATION_TEST_DIR'..."
find "$INTEGRATION_TEST_DIR" -type f -name "$FILES_TO_MODIFY_PATTERN" -exec sed -i "/$IGNORE_PATTERN/d" {} +

# 1c. Integration Build File modification
log "Modifying '$INTEGRATION_BUILD_FILE': Uncommenting KSP line..."
# Use sed to replace the *exact* commented line with the uncommented version.
sed -i "s|$COMMENTED_KSP_LINE|$UNCOMMENTED_KSP_LINE|" "$INTEGRATION_BUILD_FILE"

log "Finished all modifications."


# --- Step 2a: Run Schema Gradle Task ---
log "Running Gradle task: '$SCHEMA_GRADLE_TASK'..."
set +e
../gradlew "$SCHEMA_GRADLE_TASK"
gradle_exit_code=$?
set -e

if [ $gradle_exit_code -eq 0 ]; then
  log "Gradle task finished successfully."
else
  log "ERROR: Gradle task failed with exit code $gradle_exit_code."
  final_exit_code=$gradle_exit_code # Store the error code for final script exit
  log "Proceeding with file restoration despite Gradle failure..."
fi

# --- Step 2b: Run Integration Gradle Task ---
log "Running Gradle task: '$INTEGRATION_GRADLE_TASK'..."
set +e
../gradlew "$INTEGRATION_GRADLE_TASK"
gradle_exit_code=$?
set -e

if [ $gradle_exit_code -eq 0 ]; then
  log "Gradle task finished successfully."
else
  log "ERROR: Gradle task failed with exit code $gradle_exit_code."
  final_exit_code=$gradle_exit_code # Store the error code for final script exit
  log "Proceeding with file restoration despite Gradle failure..."
fi


# --- Step 3: Restore Files ---
# This step will now always run because we handled potential Gradle failure above
# without exiting the script prematurely if Gradle failed.
log "Restoring all modified files/directories using 'git restore'..."
log "Paths to restore: ${PATHS_TO_RESTORE[*]}" # Log the array elements
# Use "${PATHS_TO_RESTORE[@]}" to handle paths with spaces correctly
git restore "${PATHS_TO_RESTORE[@]}"
log "Files/directories specified above have been restored to their original state."


# --- Completion ---
if [ $final_exit_code -eq 0 ]; then
    log "Script completed successfully."
else
    # Report failure, including the exit code from Gradle
    log "Script finished, but the Gradle task failed (exit code $final_exit_code)."
fi

# Exit with 0 if everything was successful, or with Gradle's non-zero exit code if it failed
exit $final_exit_code