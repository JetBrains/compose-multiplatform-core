# JavaScriptEngine Demo App

**See this page rendered in [Gitiles
markdown](https://android.googlesource.com/platform/frameworks/support/+/androidxx-main/javascriptengine/integration-tests/testapp/README.md).**

The JavaScriptEngine demo app serves as both a practical demonstration how to use
the latest AndroidX Webkit APIs and as a means to exercise those APIs for manual
testing.

## Building the demo app

```shell
cd frameworks/support/

# Optional: you can use Android Studio as your editor
./studiow

# Build the app
./gradlew :javascriptengine:integration-tests:testapp:assembleDebug

# Install the app
./gradlew :javascriptengine:integration-tests:testapp:installDebug

# Check for Lint warnings
./gradlew :javascriptengine:integration-tests:testapp:lintDebug

# Optional: launch the app via adb
adb shell am start -n com.example.androidx.javascriptengine/.MainActivity
```

