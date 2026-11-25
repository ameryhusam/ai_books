# Local Android verification

GitHub Actions is not required to verify the changes. Run the Android Gradle tests locally with JDK 17 and an installed Android SDK.

## Run focused regression tests

Linux / macOS:

```bash
./gradlew :app:testDebugUnitTest \
  --tests "com.example.EduMiniCoreTest.directWorkspaceBinderInspectsMultiGradeAndBindsSingleGradeInPlaceWithoutCopying" \
  --tests "com.example.EduMiniCoreTest.directWorkspaceBinderRejectsComponentKeyThatConflictsWithIndexPath" \
  --tests "com.example.EduMiniCoreTest.directWorkspaceCatalogSyncUpdatesIndexAndUnitRowsWithoutDuplicates" \
  --tests "com.example.EduMiniCoreTest.roomEnforcesStableBookAndLessonIdentityWithinWorkspace" \
  --tests "com.example.EduMiniCoreTest.learnerProgressStudyDaysAndAssessmentAttemptsAreIsolated"
```

Windows:

```bat
gradlew.bat :app:testDebugUnitTest --tests "com.example.EduMiniCoreTest.directWorkspaceBinderInspectsMultiGradeAndBindsSingleGradeInPlaceWithoutCopying" --tests "com.example.EduMiniCoreTest.directWorkspaceBinderRejectsComponentKeyThatConflictsWithIndexPath" --tests "com.example.EduMiniCoreTest.directWorkspaceCatalogSyncUpdatesIndexAndUnitRowsWithoutDuplicates" --tests "com.example.EduMiniCoreTest.roomEnforcesStableBookAndLessonIdentityWithinWorkspace" --tests "com.example.EduMiniCoreTest.learnerProgressStudyDaysAndAssessmentAttemptsAreIsolated"
```

The focused tests exercise:
- Science indexes without a component, English COURSE/WORKBOOK components, and Social Studies GEO/HIST/TARBIA components.
- Index/manifest component identity, nested component-root binding, grade discovery, and zero-copy binding.
- Stable Room uniqueness for workspace/book and workspace/book/unit/lesson identities.
- Isolation of lesson completion, study-day activity, and assessment results between local learner profiles.

## Run the full JVM unit-test suite

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

## Verify the Room 14-to-15 upgrade

Before release, back up the app data on a test device that has an existing version-14 database. Install the new build without clearing app data, open the app, and verify:

1. The database opens without a Room schema-validation error.
2. Existing progress, study days, and assessment attempts appear under the default local learner profile (`learner_default`).
3. Two non-default learner profiles can complete the same lesson independently.
4. Assessment history and study streaks change when switching local learner profiles.
5. Firebase sign-in and the existing Firestore sync still work as before.

Do not use "Clear storage" for this upgrade test; that would bypass the migration.

## Environment requirements

- JDK 17.
- Android SDK compatible with compile SDK 36.
- Android SDK environment configured through Android Studio or `ANDROID_HOME` / `ANDROID_SDK_ROOT`.

A successful unit-test task verifies JVM/Robolectric tests. A successful `assembleDebug` additionally verifies Android source compilation and packaging. Neither should be inferred from a GitHub commit or from reading source files alone.
