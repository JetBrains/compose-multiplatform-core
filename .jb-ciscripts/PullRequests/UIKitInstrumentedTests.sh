./gradlew :compose:ui:ui:linkInstrumentedTestDebugFrameworkUikitSimArm64

cd compose/ui/ui/src/uikitInstrumentedTest/launcher

# Force-close all simulators
xcrun simctl shutdown all
killall Simulator

# Get list of all available devices (including unavailable)
devices=$(xcrun simctl list devices --json)

# Parse only iOS simulators using `jq`
# This assumes you have jq installed; you can also parse manually if not
if ! command -v jq &> /dev/null; then
  echo "Error: jq is not installed"
  exit 1
fi

## Configure simulators to disconnect hardware keyboard (and show on-screen keyboard).

# Export current preferences to PREF_PLIST
PREF_PLIST=~/iphonesimulator.plist
defaults export com.apple.iphonesimulator - > "$PREF_PLIST"

# Adding "ConnectHardwareKeyboard = false" for every simulator
echo "$devices" | jq -r '.devices | to_entries[] | select(.key | startswith("com.apple.CoreSimulator.SimRuntime.iOS")) | .value[] | "\(.udid)"' | while read -r UUID; do
    /usr/libexec/PlistBuddy -c "Set :DevicePreferences:$UUID:ConnectHardwareKeyboard false" "$PREF_PLIST" 2>/dev/null || \
    /usr/libexec/PlistBuddy -c "Add :DevicePreferences:$UUID:ConnectHardwareKeyboard bool false" "$PREF_PLIST"
done

# Import back the modified plist
defaults import com.apple.iphonesimulator "$PREF_PLIST"

echo "BOOT:"
xcrun simctl boot "iPhone 16"
echo "BOOT: Launch sim"
open -a Simulator
echo "BOOT: Launch sim via AppleScript"
osascript -e 'tell application "Simulator" to activate'

xcodebuild test -scheme Launcher-CI -project Launcher.xcodeproj -destination 'platform=iOS Simulator,name=iPhone 16'
