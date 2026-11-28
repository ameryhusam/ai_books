# Generation Studio and Dashboard Audit — 2026-11-28

## Integration record

- Repository: `ameryhusam/ai_books`
- Source branch: `feat/studio-bulk-pipeline-prompt-audit`
- Target branch: `main`
- Integration PR: [#8](https://github.com/ameryhusam/ai_books/pull/8)
- Merge commit: `a1672ab24e73adea179deda5f1afcc2f824040fa`
- Result: merged successfully; `main` contains the complete source branch history.
- Post-merge comparison: `main` is 2 commits ahead of the source branch (the PR merge commit plus the pre-existing main-side merge topology); no file delta was reported after the merge.
- CI: no status checks were returned for the merge commit at the time of this audit. This is not a passing test result.

## Verified implementation

### Generation modes and operations

The current `MainViewModel.kt` implements:
- `SINGLE_LESSON`: one lesson and one selected artifact.
- `BULK_QUESTIONS`: question-bank generation across selected lessons; lesson text and concepts can be generated as internal prerequisites and are not persisted as user-facing artifacts in this mode.
- `BULK_FULL_CONTENT`: a planned sequence of lesson text, concepts, objectives, source questions, explanation, summary, vocabulary, worked examples, lesson review, difficulty adaptation, AI questions, interactive lesson, educational activities, assessment, and general remediation resources.
- `GENERATE`, `UPDATE`, and `REVIEW` operation branches are present. Review routes through `CONTENT_REVIEW`; generated outputs are persisted with `DraftStatus.DRAFT`.

### Prompt registration

The prompt registry contains task mappings for concepts, source questions, interactive lesson, educational activities, assessment, remediation, content review, and learner report. These are not merely orphaned asset files: the registry references their asset paths and task contracts.

### Studio interaction

- The `LaunchedEffect(generatedResult)` path already moves the workbench to output tab 1 when a new nonblank result arrives.
- Interactive station-count wording is already distinct from question-count wording.
- The current UI uses formatted-content editing for supported artifact types; the former raw JSON editor flag is not present in the inspected StudioScreen source.

### Approval and interactive publishing

- Single-generation and bulk-generation paths persist output as `DRAFT`.
- Interactive publication is performed by an explicit `buildAndPublishInteractiveBundle` action or by `approveDraft` for an interactive draft.
- `approveDraft` validates the interactive JSON before building/publishing and only marks the draft approved after the package is created successfully.
- The explicit build/publish action is a separate user-invoked command; it must remain visibly labelled as a publishing action, not be invoked as a side effect of generation.

## Incomplete or not yet proven

1. **Durable batch orchestration:** the current bulk loop has per-task skip/failure handling and partial persistence, but this audit did not find a durable batch/work-item entity that can reliably resume after process death with stable idempotency keys. In-memory progress is not equivalent to durable resumability.
2. **Update version guarantees:** draft persistence has version fields, but the full compare-and-swap behavior against the exact source draft version selected by the teacher needs dedicated tests and explicit stale-version handling.
3. **Review provenance:** bulk review outputs are saved under the `CONTENT_REVIEW` task. The originating artifact type should be stored as structured metadata, not only inferred from a title or prompt text.
4. **Visual editor coverage:** formatted editing is not yet evidence that every artifact type (especially interactive lesson, educational activity, assessment, source-extracted question, and remediation) has a safe domain editor and round-trip validation.
5. **Admin content-readiness metrics:** `AdminDashboardScreen` currently receives `WorkspaceSummary` values including file count, but no verified indexed-book/lesson/approved-content aggregates. Those metrics must come from repository/DAO queries; do not infer indexing readiness from file counts.
6. **Learner dashboard:** the current dashboard already shows progress, completion, question readiness, concept mastery, and recommendations. Any changes should avoid duplicating these and must exclude unapproved drafts from learner availability and completion metrics.
7. **Validation:** no Android unit-test, instrumentation-test, or APK-build result is claimed by this report. The merge commit had no returned CI status checks at audit time.

## Next implementation order

1. Add durable batch and work-item persistence, including idempotency, per-item status, retry, cancellation, and restart-safe resume.
2. Add explicit source-artifact provenance for review reports and stale-version protection for updates.
3. Complete typed editors and validators for each artifact family; preserve raw response only as diagnostic evidence, not as the teacher-facing editing workflow.
4. Add DAO-backed content-readiness aggregates for the admin dashboard; do not use `WorkspaceSummary.filesCount` as a proxy for indexed or approved lessons.
5. Add focused tests for draft-only generation, explicit interactive approval/publishing, review immutability, update version retention, bulk partial failure, and dashboard filtering.
6. Run focused Gradle tests, then the full test suite and `assembleDebug`; record actual workflow/build outcomes before claiming completion.
