# Room Database Audit and Fixes

Date: 2026-11-25
Branch: `main`

## Architecture decision

- Keep Firebase Authentication and the existing Firestore integration active. Firebase was already in use; this work does not remove, bypass, or replace it.
- Keep Room as the local operational database.
- Keep source PDF files in the linked Workspace. The app stores references and metadata; it does not copy PDF bytes into Room or upload PDFs to Firebase as part of this change.
- No Firebase sync behavior or Firestore document contract was changed.

## Fixes applied

### 1. Corrected the DAO declaration corruption

In `EduMiniDatabase.kt`, the `insertDraft` declaration had been duplicated and concatenated on one line. It is now one valid DAO method declaration.

### 2. Enforced stable book identity

The `books` table now has a unique index on:

```text
(workspaceId, bookKey)
```

This prevents two rows representing the same book in the same workspace. Existing auto-generated `rowId` remains in place to avoid breaking callers that currently use it.

### 3. Enforced stable lesson identity

The `lessons` table now has a unique index on:

```text
(workspaceId, bookKey, unitKey, lessonKey)
```

This prevents duplicate rows for the same lesson identity while allowing the same lesson key to exist in another book or workspace.

### 4. Added a data-preserving Room migration

- Database version increased from 13 to 14.
- Migration `MIGRATION_13_14` removes duplicate book and lesson rows before creating the unique indexes.
- When legacy duplicates exist, the row with the smallest `rowId` is retained.
- Existing stable identifiers and related records are not rewritten by this migration.
- The migration is registered after `MIGRATION_12_13`.

### 5. Corrected the duplicate JUnit annotation

The `importerRejectsZipSlipPath` test had two consecutive `@Test` annotations. One was removed.

### 6. Added a regression test

`roomEnforcesStableBookAndLessonIdentityWithinWorkspace` verifies that inserting a second version of the same book or lesson replaces the previous row rather than creating duplicates.

The local testing guide now includes this focused test.

## Findings deliberately not changed in this pass

These findings require coordinated changes across the database, repository, UI, export/import and possibly Firebase contracts. They should not be patched by changing entities alone.

1. **Learner scoping of progress:** `lesson_progress` and `study_days` are currently not keyed by learner profile. Question attempts already include `user_id`, but lesson completion and study-day summaries can be shared between local learner profiles.
2. **Assessment attempt ownership:** `assessment_attempts` does not currently store the local learner profile ID. The UI and repository need to pass the active learner consistently.
3. **Foreign keys:** most curriculum/content relationships are represented by keys but are not enforced with Room foreign-key constraints. Adding constraints requires a deletion/retention policy for drafts, questions, attempts and progress when an index changes.
4. **Schema export:** `exportSchema` is still false. Enabling it should be paired with KSP schema-location configuration, committed schema snapshots, and migration tests. It was not toggled without those supporting files.
5. **Workspace sync strategy:** `syncDirectWorkspaceCatalog` remains transactional but currently replaces the workspace's books and lessons as a set. A differential upsert/removal strategy is still recommended to preserve stable local row identities and better handle content updates.
6. **Book file registry:** PDF and related file references are currently spread across book/lesson/index entities. A dedicated `BookFile` entity may be warranted when the PDF splitting/OCR pipeline requirements are finalized; no duplicate file table was introduced prematurely.

## Verification status

Source changes and regression test were committed to `main`. Gradle tests and Android assembly have **not** been executed in this environment. Do not treat the source commits as proof that compilation, migration execution, or tests passed.

Run the focused tests from `docs/LOCAL_ANDROID_TESTING.md`, then the complete `testDebugUnitTest` suite and `assembleDebug`. In particular, validate an existing version-13 database upgrade to version 14 on a device/emulator before release.
