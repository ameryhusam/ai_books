# Generation Studio — unified generation pipeline and prompt audit

**Status:** design and prompt revision in progress. The prompt files in this change are authored assets; newly added task prompts are not runtime-wired until their task types, response contracts, validators and UI actions are implemented.

## 1. User-facing generation modes

The Studio exposes three modes while keeping one application execution path:

1. **Single content / single lesson** — preserve the current workflow. Select one lesson, one content type and one operation: generate, update or review.
2. **Bulk question bank** — select a subject/book and a set of lessons (or all lessons), then generate only the AI question-bank artifact for each lesson. Existing lesson text and approved/reviewed concepts are reused where available. Missing source text may be extracted as an internal prerequisite; concept extraction may be run as an internal dependency only when required for reliable tagging. The mode must not generate unrelated visible artifacts.
3. **Bulk full lesson content** — select a subject/book and lessons, then process reusable lesson-authoring artifacts in dependency order. Each lesson is an independent work item and each artifact is persisted as DRAFT.

A mode is a selection of work items, not a separate provider integration. Every item is normalized to a common request containing workspace/book/unit/lesson identity, artifact type, operation, source references, template/version, response contract, provider/model selection and idempotency fingerprint.

## 2. Shared execution architecture

`MainViewModel` handles UI state and user intent only. It delegates to a Generation Coordinator / use case. The coordinator creates or resumes a job, resolves the lesson set, expands the chosen mode into ordered lesson-artifact work items, schedules bounded concurrency, tracks cancellation/retry, and reports progress.

Each work item delegates to the existing repository task preparation and shared provider execution path:

`Generation Coordinator → feature task adapter → EduMiniRepository.executeAiTask() → AiGenerationService.execute(AiExecutionRequest)`

The task adapter owns source selection, prompt assembly, response contract, parsing, validation and persistence mapping. `AiGenerationService` remains task-neutral: it transports requests and returns provider output/telemetry. Do not add one API client, retry loop or provider route per bulk mode.

The coordinator must not call providers directly, parse provider JSON, or write Room entities itself.

## 3. Mode expansion and dependencies

### Bulk question bank

Per lesson:
- Resolve the current source text/PDF and page references.
- Reuse the latest usable concepts/goals if present.
- If source text is absent, extract it as a prerequisite; preserve the extracted artifact and mark it DRAFT.
- If concepts are absent and concept tags are required, extract candidate concepts as a prerequisite. Do not fabricate concept IDs; the application assigns stable IDs.
- Generate the configured question count/type distribution/difficulty.
- Validate schema, count, distribution, answer consistency, duplicate fingerprints, source grounding and concept references.
- Save the question artifact and its questions as DRAFT/review-required. Do not mark questions reviewed or expose them to learner quizzes until explicit approval.

Internal prerequisites are recorded as dependencies and surfaced in the job report, but the selected mode remains question-bank-only from the user's perspective.

### Bulk full lesson content

Per lesson, process reusable authoring artifacts in this order:
1. Lesson text extraction / reuse.
2. Concept extraction and learning objectives.
3. Verbatim source-question extraction.
4. AI question-bank generation.
5. Interactive explanatory lesson generation.
6. Educational activities: extract source activities and optionally create new activities as a distinct origin.
7. Assessment instrument / lesson assessment construction.
8. General remediation resources linked to concepts (not a learner-specific intervention).

Learner-specific assessment results, diagnosis-like interpretation and learner progress reports are not generated in this batch. They require real attempts and assessment data. The learner report and personalized remediation workflow runs after evidence exists. The full-content mode may prepare assessment instruments and reusable remediation resources, but must not invent learner performance.

## 4. Single-lesson operations

Every artifact type supports:
- `GENERATE`: create a first version when none exists.
- `UPDATE`: create a new version based on the current artifact and current source. Never overwrite the previous version.
- `REVIEW`: audit the current artifact against its source, task contract and curriculum context. Save a separate review report; do not silently edit the artifact.

Review findings need severity, location, evidence, rationale and a suggested correction. The user explicitly chooses whether to request an update. A review is not a regeneration request.

## 5. Persistence and lifecycle

- All bulk-generated artifacts are inserted in Room as `DRAFT`.
- A successful provider response is not equivalent to approval.
- A review result is a separate artifact and does not change the reviewed artifact's status.
- An update creates version N+1 and links to the prior version; the old version remains readable and restorable.
- Keep prompt/template ID and version, prompt hash, response-schema version/hash, source-text/PDF/index hashes, provider/model, timestamps and validation findings.
- Use deterministic idempotency keys from workspace + lesson + artifact type + operation + source hash + prompt version + schema version + relevant generation settings.
- Resume skips completed work items whose fingerprints still match. Failed items can be retried without re-running successful items.
- A failure in one lesson must not roll back successful drafts from other lessons.
- Use bounded concurrency and provider quota/rate-limit controls already owned by the shared execution service.
- Approval is explicit and separate from batch completion.

The current `DraftRecord` already carries status, version and several provenance fields. Before adding persistence tables, inspect whether the current Room draft model can be extended safely. The likely missing durable entities are a batch job and per-lesson work-item state; add a Room migration only if the current schema cannot represent them.

## 6. Prompt audit findings

The previous prompt assets were short instruction fragments. They did not consistently define source hierarchy, uncertainty handling, educational alignment, provenance, output boundaries or validation expectations.

Revised existing prompts:
- `lesson_text.prompt.md`: faithful extraction, no silent correction, explicit uncertainty, preserve tables/symbols/pages.
- `lesson_explanation.prompt.md`: source-grounded teaching, conceptual sequence, distinction between source facts and explanatory examples.
- `lesson_summary.prompt.md`: faithful condensation without deleting conditions or exceptions.
- `learning_objectives.prompt.md`: observable and measurable objectives aligned to content and grade.
- `vocabulary_builder.prompt.md`: terminology extraction, precise definitions and concept distinctions.
- `worked_examples.prompt.md`: mathematically/scientifically checked solutions and clear provenance for generated examples.
- `lesson_review.prompt.md`: retrieval-oriented review and evidence-bounded misconception notes.
- `difficulty_adapter.prompt.md`: invariant concept correctness across support levels.
- `question_bank.prompt.md`: item-writing quality, plausible distractors, exact counts/types and answer checks.
- `interactive_activity.prompt.md`: activity rather than explanatory lesson, schema-only JSON, no free JavaScript/HTML.

New prompt assets added for future task adapters:
- `concept_extraction.prompt.md`
- `source_question_extraction.prompt.md`
- `interactive_lesson.prompt.md`
- `educational_activities.prompt.md`
- `content_review.prompt.md`
- `remediation_content.prompt.md`
- `learner_report.prompt.md`

The new prompt assets are not yet selectable in the app. They must not be exposed as available features until corresponding TaskType/operation mappings, response schemas, strict parsers, domain validators, persistence and tests are in place.

## 7. Important contract gaps to resolve before wiring

1. Current question payload has a single `rationale` field and a question-level `conceptTag`. It cannot persist a distinct misconception hypothesis for each distractor. Do not prompt for per-distractor structured metadata until the question model and Room schema support it.
2. Current interactive contract describes steps/checkpoints/ordering/matching and is activity-oriented. A separate explanatory interactive-lesson schema is required for TEXT, KEY_POINT, DIAGRAM, PROCESS, COMPARISON, IMAGE, MODEL_3D, SIMULATION and CHECKPOINT blocks.
3. Source-extracted questions must preserve source wording and source page separately from AI-generated questions. The existing `QuestionSourceType` should be used; do not infer source type from prompt text.
4. Concepts need stable application-owned IDs and normalized links to questions/activities/objectives. A plain `conceptTag` string is not sufficient for a durable many-to-many curriculum model.
5. Prompt/schema versions must be explicit. Prompt text alone cannot enforce JSON structure; provider schema plus local parser and domain validation remain authoritative.
6. A generated content review must not be represented as approval. Approval remains an explicit teacher/admin action.

## 8. Current implementation status (Android branch)

The following application wiring has now been added on `feat/studio-bulk-pipeline-prompt-audit`:

- Added TaskType entries and prompt blueprints for concepts, source questions, explanatory interactive lessons, educational activities, assessments, remediation resources, content review and learner reports.
- Added built-in prompt templates for the new task types. Each template loads its Markdown asset through the existing `AiGenerationService.buildPrompt()` path; no new provider client or API route was introduced.
- Added the three Studio modes: one lesson, question-bank batch, and full subject batch. Subject selection groups all lesson records for the selected subject/component across its book parts.
- Added CREATE / UPDATE / REVIEW operation selection. Single UPDATE supplies the previous content as context and persists a new version. Single REVIEW creates a separate `CONTENT_REVIEW` draft.
- Added sequential batch orchestration through `MainViewModel` and the existing `EduMiniRepository.generateContent()` method. Every provider call still reaches `executeAiTask()`.
- Batch question mode may extract lesson text and concepts as internal prerequisites, but persists only the question-bank artifact (or its review report). It does not save prerequisite artifacts as extra visible drafts.
- Full-content mode includes lesson text, concepts, objectives, source questions, explanation, summary, vocabulary, worked examples, lesson review, difficulty adaptation, AI questions, interactive lesson, activities, assessment and remediation resources. Learner-specific reports are intentionally excluded.
- Every persisted batch artifact uses `DraftStatus.DRAFT`. Per-lesson failures do not roll back drafts from other lessons. Cancellation preserves completed drafts; rerunning CREATE skips tasks that already have drafts and retries missing tasks.
- REVIEW uses the dedicated review prompt and stores a separate review draft; UPDATE preserves prior draft versions through the existing versioned `persistDraft()` path.
- The Studio now shows mode, subject, and operation controls. The question-bank preview is rendered as readable question cards/Markdown, and the legacy interactive activity is rendered as structured step/checkpoint cards.
- Raw text editing has been removed for JSON-backed question, legacy interactive activity and lesson-text outputs. Markdown-backed content types can be edited in the formatted content editor.

### Remaining implementation boundaries

This wiring is functional but does not yet constitute the final normalized curriculum-content platform:

1. There is no durable batch/job table yet. Completed artifacts are durable drafts and can be skipped on a rerun, but there is no persistent batch dashboard, pause/resume cursor, or per-item retry state.
2. Extracted concepts remain draft artifacts; question links still use the existing `conceptTag` string. Stable concept IDs and normalized many-to-many question/concept relations require a separate Room migration.
3. Extracted source questions are saved as a source-question draft artifact. They are not yet imported into a dedicated source-question table or automatically added to the student question bank.
4. Assessment instruments and remediation resources are saved as drafts; this batch does not automatically create rows in the operational assessments table.
5. The explanatory interactive lesson prompt specifies diagrams, process visuals and optional 3D/simulation representations, but a renderer for those new block types and an actual glTF/3D asset pipeline are not implemented yet. The current preview is formatted Markdown. The legacy activity renderer is separate and functional for its existing schema.
6. Learner reports require real attempts. The current guard requires recorded question attempts for the active learner; assessment-attempt integration and richer statistical confidence thresholds remain future work.
7. Structured JSON outputs are no longer editable as raw text, but typed field-level editors for questions, lesson text and activity blocks are still needed to provide full in-place editing in their rendered form.

## 9. Acceptance criteria


- Existing single-task generation continues to work unchanged.
- Bulk question bank and bulk full content use the same repository/provider path as single generation.
- All batch results are saved as DRAFT and never become learner-visible solely because generation succeeded.
- A single lesson failure does not discard other lesson results.
- Resume/retry does not duplicate completed artifacts or questions.
- Updating content creates a new version and preserves the old one.
- Reviewing content creates a separate report and does not mutate the content.
- The full-content batch does not fabricate student-specific performance reports.
- Every prompt-backed task has a versioned response contract, strict parser and domain validation tests.
- Tests cover dependency ordering, cancellation, partial failure, resume, idempotency, draft persistence, version history and provider isolation.
