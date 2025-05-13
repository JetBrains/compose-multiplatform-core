./gradlew :compose:ui:ui:linkInstrumentedTestDebugFrameworkUikitSimArm64

cd compose/ui/ui/src/uikitInstrumentedTest/launcher

xcrun simctl shutdown all
killall Simulator
defaults write com.apple.iphonesimulator ConnectHardwareKeyboard -bool false

xcodebuild test -scheme Launcher-CI -project Launcher.xcodeproj -destination 'platform=iOS Simulator,name=iPhone 16'
