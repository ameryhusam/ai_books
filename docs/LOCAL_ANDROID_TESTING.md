# Local Android verification

GitHub Actions is not required to verify the workspace binder changes. Run the Android Gradle tests locally with JDK 17 and an installed Android SDK.

## Run the focused workspace test

Linux / macOS:

```bash
./gradlew :app:testDebugUnitTest --tests "com.example.EduMiniCoreTest.directWorkspaceBinderInspectsMultiGradeAndBindsSingleGradeInPlaceWithoutCopying"
```

Windows:

```bat
gradlew.bat :app:testDebugUnitTest --tests "com.example.EduMiniCoreTest.directWorkspaceBinderInspectsMultiGradeAndBindsSingleGradeInPlaceWithoutCopying"
```

This test exercises:
- A science index at `G07/SCI/index.json` with no component.
- English Course and Workbook indexes at `G07/ENG/COURSE/index.json` and `G07/ENG/WORKBOOK/index.json`.
- Social Studies indexes at `G07/SOC/GEO/index.json`, `G07/SOC/HIST/index.json`, and `G07/SOC/TARBIA/index.json`.
- Index/manifest component identity, PDF references, grade discovery, and zero-copy binding.

## Run the full JVM unit-test suite

Linux / macOS:

```bash
./gradlew :app:testDebugUnitTest
```

Windows:

```bat
gradlew.bat :app:testDebugUnitTest
```

The HTML report is generated at:

`app/build/reports/tests/testDebugUnitTest/index.html`

## Verify Android compilation

```bash
./gradlew :app:assembleDebug
```

On Windows use `gradlew.bat :app:assembleDebug`.

## Environment requirements

- JDK 17 (the project compiles Java/Kotlin against version 17).
- Android SDK compatible with the project's compile SDK (36).
- Android SDK environment configured through Android Studio or `ANDROID_HOME` / `ANDROID_SDK_ROOT`.

A successful unit-test task verifies the JVM/Robolectric tests. A successful `assembleDebug` additionally verifies Android source compilation and packaging. Neither should be inferred from a GitHub commit or from reading source files alone.
