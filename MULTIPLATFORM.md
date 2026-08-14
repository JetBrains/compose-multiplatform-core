## General requirements

- Java 21 (should be specified in JAVA_HOME and in the IDE)
- Android SDK downloaded from Android Studio and specified in `ANDROID_SDK_ROOT`
- *(on Windows)* Git "symlinks" is enabled. Call `git config --global core.symlinks true` to set it globally. `core.symlinks` requires running git commands with admin priviligies, or with [Developer mode enabled](https://learn.microsoft.com/en-us/windows/advanced-settings/developer-mode)

## Developing in IDE

1. Download Android Studio from [the official site](https://developer.android.com/studio/archive) (it is mandatory to use the version, written [here](https://github.com/JetBrains/androidx/blob/jb-main/gradle/libs.versions.toml#L11)). As an alternative you can use IDEA, which is compatible with [this AGP version](https://github.com/JetBrains/androidx/blob/jb-main/gradle/libs.versions.toml#L5), or you can disable Android plugin in IDEA plugins, to develop non-Android targets.
2. Download Android SDK via [Android Studio](https://developer.android.com/studio/intro/update#sdk-manager) and specify it in `ANDROID_SDK_ROOT` environment variable.
4. Specify Gradle JVM to use JDK 17 in InteliJ IDEA Preferences (`Build, Execution, Deployment -> Build Tools -> Gradle`)

### Run tests

Run tests for Desktop:

```bash
./gradlew desktopTest
```

Run tests for Web:

```bash
./gradlew :mpp:testWeb
```

Run tests for iOS:

```bash
./gradlew :mpp:testIos'
```

Run iOS instrumented tests using CLI:

Note: To ensure the test runs on an iOS simulator with a detached hardware keyboard,
we must shut down all simulators and update the ConnectHardwareKeyboard flag.
```bash
xcrun simctl shutdown all

defaults write com.apple.iphonesimulator ConnectHardwareKeyboard -bool false

cd compose/ui/ui/src/uikitInstrumentedTest/launcher

xcodebuild test -scheme Launcher -project Launcher.xcodeproj -destination 'platform=iOS Simulator,name=iPhone 16'
```

Run configured iOS instrumented tests from IDE or using CLI:

1. Choose which tests to run in [Configuration.kt](https://github.com/JetBrains/compose-multiplatform-core/blob/jb-main/compose/ui/ui/src/uikitInstrumentedTest/kotlin/androidx/compose/ui/Configuration.kt) or leave `setupXCTestSuite(...)` empty to run the full instrumented suite.
2. Update the configuration values in `ios-instrumented-tests.sh` when you need a different simulator, OS version, number of iterations,...
3. Run the instrumented tests:
   - from the IDE run configuration `iOS Instrumented Tests`
   - or using CLI from the repository root:
```bash
./compose/ui/ui/src/uikitInstrumentedTest/launcher/ios-instrumented-tests.sh
```

### API checks

Compose Multiplatform stores all public API in *.api files. If any API is added/changed, `./gradlew jbApiCheck` will fail with an error that API is changed (it runs on CI). Example:

```
Execution failed for task ':compose:material3:material3:desktopApiCheck'.
> API check failed for project material3.
  --- D:\Work\compose-multiplatform-core\compose\material3\material3\api\desktop\material3.api
  +++ D:\Work\compose-multiplatform-core\out\androidx\compose\material3\material3\build\api\desktop\material3.api
  @@ -552,6 +552,11 @@
   public abstract interface annotation class androidx/compose/material3/ExperimentalMaterial3Api : java/lang/annotation/Annotation {
   }

  +public final class androidx/compose/material3/FF {
  +     public static final field $stable I
  +     public fun <init> ()V
  +}
  +
   public final class androidx/compose/material3/FabPosition {
        public static final field Companion Landroidx/compose/material3/FabPosition$Companion;
        public static final synthetic fun box-impl (I)Landroidx/compose/material3/FabPosition;

   You can run :material3:apiDump task to overwrite API declarations
```

To fix this error:

1. Run `./gradlew jbApiDump` or (for Linux/Window host) [CI task](https://teamcity.jetbrains.com/buildConfiguration/JetBrainsPublicProjects_Compose_CommitDumpApi) that creates a commit "Dump API" in a branch
2. See what has changed in *.api files.
3. If there are only additions - there is no binary incompatible change.
4. If there are some removals - most probably there is a binary incompatible change and it needs to be fixed before merging it to the main branch.

### Publishing

Compose Multiplatform core libraries can be published to local Maven with the following steps:

1. Publish libraries
   ```bash
   ./gradlew :mpp:publishComposeJbToMavenLocal -Pcompose.platforms=all
   ```

   `-Pcompose.platforms=all` can be replaced with comma-separated list of platforms, such as `web,desktop,android,macosx64,ios`.

   (Optional) Specify different versions
   ```
   ./gradlew ... -Pjetbrains.publication.version.COMPOSE=9999.1.0-alpha01 -Pjetbrains.publication.version.COMPOSE_MATERIAL3_ADAPTIVE=9999.1.0-alpha01
   ```
   The default value for a version is `9999.0.0-SNAPSHOT`

   (Optional) Specify different libraries to publish
   ```
   ./gradlew ... -Pjetbrains.publication.libraries=COMPOSE,COMPOSE_MATERIAL3_ADAPTIVE
   ```
   By default all libraries are published.

   See available libraries as keys in [JetBrainsPublication](https://github.com/JetBrains/compose-multiplatform-core/blob/jb-main/buildSrc/public/src/main/kotlin/org/jetbrains/androidx/build/JetBrainsPublication.kt#L32)

2. (Optional) Publish Gradle plugin from https://github.com/JetBrains/compose-multiplatform/tree/master/gradle-plugins using the published Compose:
   ```
   ./gradlew publishToMavenLocal -Pcompose.version="9999.0.0-SNAPSHOT" -Pdeploy.version="9999.0.0-SNAPSHOT"
   ```

3. (Optional) Publish Components from https://github.com/JetBrains/compose-multiplatform/tree/master/components:
   ```
   ./gradlew publishToMavenLocal -Pdeploy.version="9999.0.0-SNAPSHOT"
   ```

### Run samples

Run jvm desktop samples:

```bash
./gradlew :compose:mpp:demo:runDesktop
```

```bash
./gradlew :compose:desktop:desktop:desktop-samples:run1
```

```bash
./gradlew :compose:desktop:desktop:desktop-samples:run2
```

```bash
./gradlew :compose:desktop:desktop:desktop-samples:run3
```

```bash
./gradlew :compose:desktop:desktop:desktop-samples:runSwing
```

```bash
./gradlew :compose:desktop:desktop:desktop-samples:runWindowApi
```

```bash
./gradlew :compose:desktop:desktop:desktop-samples:runVsync
```

```bash
./gradlew :compose:desktop:desktop:desktop-samples:runLayout
```

Run wasm sample:

```bash
./gradlew :compose:mpp:demo:wasmJsBrowserDevelopmentRun
```

Run native macos X64 sample:

```bash
./gradlew :compose:mpp:demo:runDebugExecutableMacosX64
```

Run native macos Arm64 sample:

```bash
./gradlew :compose:mpp:demo:runDebugExecutableMacosArm64
```

### Run in KMP Wizard project

To use a locally built compose in [KMP with Compose wizard project](https://kmp.jetbrains.com) you need to perform some extra steps:

- Checkout <https://github.com/JetBrains/compose-multiplatform>.
- Open `gradle-plugins` in `compose-multiplatform`.
- Update `gradle.properties` by setting `compose.version` and `deploy.version` to the version you've published (`9999.0.0-alpha01` in example above).
- Run `./gradlew publishToMavenLocal`.
- Open `components` in `compose-multiplatform`.
- Update `gradle.properties` by setting `compose.useMavenLocal` to `true` and `compose.version` to the version you've published.
- Run `./gradlew publishToMavenLocal`.
- Open KMP wizard project.
- Update `settings.gradle.kts` by adding `mavenLocal()` to the end of both `repositories` blocks.
- Update `gradle/libs.versions.toml` by setting `compose-plugin` to the version you've published.
- Sync gradle.

Now the project will build with the locally published Compose.

### Run mpp/demo-swiftui sample on iOS with Xcode

Open the `iosApp.xcodeproj` with XCode and press the Run button.

### Run mpp/demo sample on iOS with Xcode

Run script:

```bash
./compose/mpp/demo/regenerate_xcode_project.sh
```

Wait while Xcode is opening, and press run button.

### Clean IDE and Gradle cache

- Close project

- ```bash
  ./cleanTempFiles.sh
  ```
### AOSP mode
The project can be opened in AOSP mode, which uses largely unmodified `buildSrc` and `build.gradle` files from the upstream `androidx-main` branch. This mode supports building and running standard upstream AndroidX targets, but does not support Compose Multiplatform targets (which require `buildSrc-fork`). AOSP mode is available only on the `integration` branch.

> **Note:** Opening the project in AOSP mode requires cloning the entire workspace setup using the Google `repo` tool.

Run this script to open the project in this mode:
```bash
./aospComposeProject.sh
```
