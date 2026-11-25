# Room Database Audit and Learning Loop Fixes

Date: 2026-11-25
Branch: `main`

## Architecture constraints preserved

- Firebase Authentication and the existing Firestore integration remain active.
- No Firestore collection, document shape, or authentication contract was changed. The client now sends account-scoped progress only for the legacy `learner_default` profile; progress belonging to additional local learner profiles stays local until a profile-aware Firestore contract is designed. Draft sync and Firebase sign-in remain active.
- Room remains the local operational database.
- PDF files remain in the linked Workspace; Room stores references and metadata only.
- Local learner profile IDs remain distinct from Firebase Authentication UIDs.

## Changes completed

### 1. Database identity and migrations

- Version 13 to 14 enforces unique workspace/book and workspace/book/unit/lesson identities.
- Version 14 to 15 isolates local learner learning records.
- `MIGRATION_14_15` rebuilds `lesson_progress` with a composite key including `user_id`, rebuilds `study_days` with `(activity_date, user_id)` as its key, and adds `user_id` to `assessment_attempts`.
- Existing rows are preserved under `learner_default`. They are not assigned to another learner because the old records contain no evidence identifying their owner.
- Both migrations are registered. No destructive migration fallback was added.

### 2. Learner-scoped repository and UI

- Lesson completion and earned points are stored and queried by workspace, lesson identity, and local learner ID.
- Study-day activity and streak queries are scoped to the active local learner.
- Assessment attempt history and dashboard assessment metrics are scoped to the active local learner.
- Question attempts were already learner-scoped; the active learner continues to drive mastery and concept summaries.
- The ViewModel switches assessment history when the active learner changes and passes the active local learner ID when recording assessments.
- Student assessment, lesson quiz, practice, and interactive-checkpoint activity is recorded against the local learner. Content-authoring actions no longer inflate learner study streaks.

### 3. Targeted concept remediation

The dashboard identifies weak concepts from the active learner's question attempts and offers practice using existing questions. It now also offers two targeted generation actions for each weak concept:

- **شرح تقوية:** generates a focused remedial explanation using the affected questions, approved answers, rationales, and recorded incorrect responses as diagnostic evidence.
- **أسئلة تكيفية:** generates graduated questions with distractors informed by the recorded misconceptions.

Generated material is saved as a draft. Adaptive questions are added to the question bank with the target concept tag and remain unreviewed (`reviewed = false`), so generated content is not silently treated as teacher-approved assessment material. Generation requires recorded wrong-answer evidence; it does not invent a misconception when evidence is absent.

This is evidence-guided generation, not a trained predictive ML model. Mastery recommendations are based on observed attempt patterns; AI generation is explicit and uses the configured provider.

### 4. Stable Workspace catalog refresh

`syncDirectWorkspaceCatalog` now reconciles the incoming catalog against existing stable identities instead of deleting all books and lessons first:

- Existing book and lesson `rowId` values are retained when their identities remain present.
- Updated metadata replaces the existing row using its preserved `rowId`.
- Removed catalog rows are deleted, while progress, questions, drafts, and attempts are retained because no cascading foreign keys were introduced.
- Incoming duplicate identities are collapsed before insertion.

### 5. Learner-aware backup

- Full backups now serialize progress and assessment attempts with their local `user_id`.
- Study-day backups include date, action count, last update, and learner ID in `studyDayRecords`.
- The backup format is now `edu-mini-backup-1.2`; the legacy date-only `studyDays` field remains for compatibility.
- Import accepts old backups without learner fields (defaulting to `learner_default`) and restores the new learner-aware records without merging different learners.

### 6. Regression coverage

- `roomEnforcesStableBookAndLessonIdentityWithinWorkspace` verifies stable book and lesson uniqueness.
- `learnerProgressStudyDaysAndAssessmentAttemptsAreIsolated` verifies independent learner progress, study-day counts, and assessment results.
- `fullBackupPreservesLocalLearnerOwnership` verifies learner ownership survives backup serialization.
- `directWorkspaceCatalogSyncUpdatesIndexAndUnitRowsWithoutDuplicates` now also verifies that catalog refresh preserves stable book and lesson row IDs.

## Deliberately deferred

1. **Foreign keys and deletion policy:** curriculum refreshes must not accidentally cascade-delete drafts, questions, attempts, or progress. Define archival/retention behavior before adding foreign keys.
2. **Schema export:** `exportSchema = false` remains unchanged. Enable it only with KSP schema-location configuration, committed schema snapshots, and migration tests.
3. **Learner profile metadata in backup:** learning records retain their local learner IDs, but the local profile names/list are currently stored separately in SharedPreferences and are not included in the workspace backup. Recreate matching profile IDs or implement a separate profile backup before moving data to a new device.
4. **Book file registry:** PDF, extracted text, and related file references remain distributed across existing entities. A dedicated `BookFile` entity should follow the finalized PDF/OCR/splitting lifecycle, not be introduced speculatively.
5. **Cloud learner separation:** Firestore remains account-scoped and its document contract is unchanged. To avoid merging multiple local learners into one account-wide progress record, the current client syncs progress only for `learner_default`; additional local profiles remain local-only. Do not interpret account-level cloud progress as per-profile progress until a separately versioned cloud contract is designed.

## Verification status

The source and regression-test changes have been committed to `main`. This environment has not run Gradle, Room schema validation, the 14-to-15 migration on an existing device database, or Android assembly. A commit is not evidence that these checks passed.

Run the focused and full commands in `docs/LOCAL_ANDROID_TESTING.md`. Before release, back up a real version-14 database and verify upgrade to version 15 while retaining existing records under `learner_default`.
