# Room Database Audit and Learning Loop Fixes

Date: 2026-11-25
Branch: `main`

## Architecture constraints preserved

- Firebase Authentication and the existing Firestore integration remain active.
- No Firestore collection, document, authentication, or sync contract was changed.
- Room remains the local operational database.
- PDF files remain in the linked Workspace; Room stores references and metadata only.
- Local learner profile IDs remain distinct from Firebase Authentication UIDs.

## Changes completed

### 1. Database identity and migration

- Version increased from 13 to 14 to enforce unique workspace/book and workspace/book/unit/lesson identities.
- Version increased from 14 to 15 to isolate learner learning records.
- `MIGRATION_14_15` rebuilds `lesson_progress` with a composite key including `user_id`, rebuilds `study_days` with `(activity_date, user_id)` as its key, and adds `user_id` to `assessment_attempts`.
- Existing progress, study-day, and assessment-attempt rows are preserved under `learner_default`. They are not assigned to another learner because the old rows contain no evidence identifying their owner.
- The migration is registered after `MIGRATION_13_14`; no destructive migration fallback was added.

### 2. Learner-scoped repository and UI

- Lesson completion and earned points are now queried and stored by workspace, lesson identity, and local learner ID.
- Study-day activity and streak queries are scoped to the active local learner.
- Assessment attempt history and dashboard assessment metrics are scoped to the active local learner.
- Question attempts were already learner-scoped; the active learner continues to drive the mastery and concept summaries.
- The ViewModel now switches assessment history when the active learner changes and passes the active local learner ID when recording an assessment.
- Student assessment, lesson quiz, practice, and interactive-checkpoint activity is recorded against the local learner. Content-authoring actions (such as creating drafts or adding questions) no longer inflate a learner's study streak.

### 3. Targeted concept remediation

The dashboard already identifies weak concepts from the active learner's question attempts and offers practice using existing questions. It now also offers two targeted generation actions for each weak concept:

- **شرح تقوية:** generates a focused remedial explanation using the affected questions, approved answers, rationales, and the learner's recorded incorrect responses as diagnostic evidence.
- **أسئلة تكيفية:** generates a new graduated set of questions with distractors informed by the recorded misconceptions.

Generated material is saved as a draft. Adaptive questions are added to the question bank with the target concept tag and remain unreviewed (`reviewed = false`) so generated content is not silently treated as teacher-approved assessment material. The generation action requires recorded wrong-answer evidence; it does not invent a misconception when no evidence exists.

This is an evidence-guided generation workflow, not a trained predictive ML model. Recommendations are based on observed mastery/attempt patterns; AI generation is explicit and uses the configured provider.

### 4. Regression coverage

- `roomEnforcesStableBookAndLessonIdentityWithinWorkspace` verifies stable book and lesson uniqueness.
- `learnerProgressStudyDaysAndAssessmentAttemptsAreIsolated` verifies that two local learners can have different progress, study-day counts, and assessment results for the same workspace and lesson.

## Deliberately deferred

1. **Foreign keys and deletion policy:** curriculum refreshes must not accidentally cascade-delete drafts, questions, attempts, or progress. Define archival/retention behavior before adding foreign keys.
2. **Schema export:** `exportSchema = false` remains unchanged. Enable it only with KSP schema-location configuration, committed schema snapshots, and migration tests.
3. **Differential Workspace catalog sync:** `syncDirectWorkspaceCatalog` still performs a transactional catalog replacement. A differential upsert/removal implementation needs dedicated tests proving that stable local IDs and learner evidence survive index refreshes.
4. **Book file registry:** PDF, extracted text, and related file references remain distributed across existing entities. A dedicated `BookFile` entity should follow the finalized PDF/OCR/splitting lifecycle, not be introduced speculatively.
5. **Cloud learner separation:** Firestore behavior remains account-scoped exactly as before. Local learner profiles are isolated in Room, but the existing Firestore progress contract does not identify those local profiles. Do not interpret account-level cloud progress as per-profile progress until a separately versioned cloud contract is designed.

## Verification status

The source and regression-test changes have been committed to `main`. This environment has not run Gradle, Room schema validation, the 14-to-15 migration on an existing device database, or Android assembly. A commit is not evidence that these checks passed.

Run the focused and full commands in `docs/LOCAL_ANDROID_TESTING.md`. Before release, back up a real version-14 database and verify upgrade to version 15 while retaining existing learner_default records.
