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
  --tests "com.example.EduMiniCoreTest.learnerProgressStudyDaysAndAssessmentAttemptsAreIsolated" \
  --tests "com.example.EduMiniCoreTest.fullBackupPreservesLocalLearnerOwnership" \
  --tests "com.example.EduMiniCoreTest.learningProgressReportSeparatesInitialAccuracyDelayedRecallAndRecurringMistakes" \
  --tests "com.example.EduMiniCoreTest.practiceOnlyAttemptsDoNotInflateMainMasteryAccuracy" \
  --tests "com.example.EduMiniCoreTest.learningProgressReportMeasuresFollowUpAfterReinforcementOnlyOnSharedQuestions" \
  --tests "com.example.EduMiniCoreTest.lessonSequenceGateRequiresEveryEarlierLessonToBePassed" \
  --tests "com.example.EduMiniCoreTest.subjectMilestoneBadgesReflectPerBookCompletionThresholds" \
  --tests "com.example.EduMiniCoreTest.learnerPointsExcludeAuthoringOnlyBadgeBonuses" \
  --tests "com.example.EduMiniCoreTest.reinforcementSelectionExcludesUnreviewedGeneratedQuestions"
```

Windows:

```bat
gradlew.bat :app:testDebugUnitTest --tests "com.example.EduMiniCoreTest.directWorkspaceBinderInspectsMultiGradeAndBindsSingleGradeInPlaceWithoutCopying" --tests "com.example.EduMiniCoreTest.directWorkspaceBinderRejectsComponentKeyThatConflictsWithIndexPath" --tests "com.example.EduMiniCoreTest.directWorkspaceCatalogSyncUpdatesIndexAndUnitRowsWithoutDuplicates" --tests "com.example.EduMiniCoreTest.roomEnforcesStableBookAndLessonIdentityWithinWorkspace" --tests "com.example.EduMiniCoreTest.learnerProgressStudyDaysAndAssessmentAttemptsAreIsolated" --tests "com.example.EduMiniCoreTest.fullBackupPreservesLocalLearnerOwnership" --tests "com.example.EduMiniCoreTest.learningProgressReportSeparatesInitialAccuracyDelayedRecallAndRecurringMistakes" --tests "com.example.EduMiniCoreTest.practiceOnlyAttemptsDoNotInflateMainMasteryAccuracy" --tests "com.example.EduMiniCoreTest.learningProgressReportMeasuresFollowUpAfterReinforcementOnlyOnSharedQuestions" --tests "com.example.EduMiniCoreTest.lessonSequenceGateRequiresEveryEarlierLessonToBePassed" --tests "com.example.EduMiniCoreTest.subjectMilestoneBadgesReflectPerBookCompletionThresholds" --tests "com.example.EduMiniCoreTest.learnerPointsExcludeAuthoringOnlyBadgeBonuses" --tests "com.example.EduMiniCoreTest.reinforcementSelectionExcludesUnreviewedGeneratedQuestions"
```

The focused tests exercise:
- Delayed recall, recall-gap, recurring wrong answers, recurring concepts, weekly score aggregation, and post-reinforcement follow-up calculations.
- Sequential lesson access, subject-specific 25/50/75/100% badges, and exclusion of unreviewed generated questions from learner practice.
- Science indexes without a component, English COURSE/WORKBOOK components, and Social Studies GEO/HIST/TARBIA components.
- Index/manifest component identity, nested component-root binding, grade discovery, and zero-copy binding.
- Stable Room uniqueness and preservation of book/lesson row IDs during catalog refresh.
- Isolation of lesson completion, study-day activity, and assessment results between local learner profiles.
- Preservation of local learner IDs and study-day counts in full backup serialization.

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

Before release, back up app data on a test device that has an existing version-14 database. Install the new build without clearing app data, open the app, and verify:

1. The database opens without a Room schema-validation error.
2. Existing progress, study days, and assessment attempts appear under the default local learner profile (`learner_default`).
3. Two non-default learner profiles can complete the same lesson independently.
4. Assessment history and study streaks change when switching local learner profiles.
5. Full backup export/import preserves the learner IDs and separate study-day records.
6. Firebase sign-in and draft sync still work as before.
7. A non-default local learner's progress is not uploaded into the account-wide Firestore progress record; the sync screen explains that it remains local until a profile-aware cloud contract exists.

Do not use "Clear storage" for this upgrade test; that would bypass the migration.

## Environment requirements

- JDK 17.
- Android SDK compatible with compile SDK 36.
- Android SDK environment configured through Android Studio or `ANDROID_HOME` / `ANDROID_SDK_ROOT`.

A successful unit-test task verifies JVM/Robolectric tests. A successful `assembleDebug` additionally verifies Android source compilation and packaging. Neither should be inferred from a GitHub commit or from reading source files alone.


## Template/PDF/API quota regression checks

Run the focused tests first:

```bash
./gradlew :app:testDebugUnitTest --tests 'com.example.EduMiniCoreTest.templateResponseAdapterMapsProviderSpecificQuestionFields'
./gradlew :app:testDebugUnitTest --tests 'com.example.EduMiniCoreTest.extractLessonPdfBase64PreservesOriginalPdfDocument'
./gradlew :app:testDebugUnitTest --tests 'com.example.EduMiniCoreTest.providerQuotaReservationFreezesKeyUntilNextTimezoneDay'
```

Then run the complete unit suite and debug APK build:

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

The API quota test exercises concurrent reservations and a next-day timezone reset. The PDF test checks that the encoded payload still begins with the PDF signature after decoding. These tests are added but have not yet been executed in the remote editing environment.

