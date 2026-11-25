# Local Android verification

GitHub Actions is not required to verify the workspace binder changes. Run the Android Gradle tests locally with JDK 17 and an installed Android SDK.

## Run the focused workspace test

Linux / macOS:

```bash
./gradlew :app:testDebugUnitTest \
  --tests "com.example.EduMiniCoreTest.directWorkspaceBinderInspectsMultiGradeAndBindsSingleGradeInPlaceWithoutCopying" \
  --tests "com.example.EduMiniCoreTest.directWorkspaceBinderRejectsComponentKeyThatConflictsWithIndexPath" \
  --tests "com.example.EduMiniCoreTest.directWorkspaceCatalogSyncUpdatesIndexAndUnitRowsWithoutDuplicates" \
  --tests "com.example.EduMiniCoreTest.roomEnforcesStableBookAndLessonIdentityWithinWorkspace"
```

Windows:

```bat
gradlew.bat :app:testDebugUnitTest --tests "com.example.EduMiniCoreTest.directWorkspaceBinderInspectsMultiGradeAndBindsSingleGradeInPlaceWithoutCopying" --tests "com.example.EduMiniCoreTest.directWorkspaceBinderRejectsComponentKeyThatConflictsWithIndexPath" --tests "com.example.EduMiniCoreTest.directWorkspaceCatalogSyncUpdatesIndexAndUnitRowsWithoutDuplicates" --tests "com.example.EduMiniCoreTest.roomEnforcesStableBookAndLessonIdentityWithinWorkspace"
```

These focused tests exercise:
- A science index at `G07/SCI/index.json` with no component.
- English Course and Workbook indexes at `G07/ENG/COURSE/index.json` and `G07/ENG/WORKBOOK/index.json`.
- Social Studies indexes at `G07/SOC/GEO/index.json`, `G07/SOC/HIST/index.json`, and `G07/SOC/TARBIA/index.json`.
- Index/manifest component identity, nested component-root binding, grade discovery, and zero-copy binding.
- Room uniqueness for `(workspaceId, bookKey)` and `(workspaceId, bookKey, unitKey, lessonKey)`, including replacement of updated records without duplicate rows.

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
