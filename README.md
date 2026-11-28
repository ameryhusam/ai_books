# Edu Mini (Android Kotlin + Jetpack Compose)

Offline-first Android educational content preparation app built natively with Kotlin, Jetpack Compose, and Material 3. It manages curriculum workspaces, lesson resources, question banks, AI-assisted learning content, and learner progress on Android devices.

## Features

- **Workspace Import & Export**: Imports prepared grade ZIP workspaces through Android Storage Access Framework, validates archive paths and size limits, discovers grade folders, and reads canonical `index.json` and `book_manifest.json` metadata.
- **Library & Lesson Navigation**: Browses imported grades, books, subjects, parts, units, lessons, page ranges, and available lesson PDFs.
- **PDF Source Handling**: Imports a selected PDF into the app's managed workspace, validates that it is readable and has pages, records source metadata and SHA-256, previews the source, and provides the PDF splitting engine for producing lesson-level PDFs from supplied page ranges.
- **Book Index Intake Contracts**: Defines book/unit/lesson draft data, printed-page and physical-PDF-page coordinates, offset conversion, validation of page ranges, and an explicit approval gate before a reviewed index can be treated as approved. The AI index-discovery prompt asset and intake contracts are present; the complete upload → AI discovery → review UI → approved-index persistence → PDF-splitting workflow is not yet connected end to end.
- **Generation Studio**: Selects lesson tasks (including concept extraction, source questions, interactive lessons, activities, assessment, review and remediation), prepares source material and prompts, and uses one shared AI execution service with an explicitly selected Gemini or OpenAI-compatible provider/model; the app selects and rotates that provider's API keys automatically. Provider-specific transport is internal to the service; task-specific parsing, validation, persistence, and presentation remain with the calling feature.
- **Question Bank**: Generates structured question content using an application-owned response contract derived from Kotlin serialization, validates the complete response before persistence, and maps accepted content to the existing Room entities.
- **AI Provider API Keys & Test**: Manages multiple Gemini API keys and multiple OpenAI-compatible API keys independently. Keys are stored encrypted with Android Keystore AES-GCM and receive generated aliases such as `GEMINI_API_KEY_1` and `OPENAI_API_KEY_1`. On confirmed quota exhaustion or invalid credentials, generation can move to another enabled key of the same provider. Model selection and model fallback candidates are resolved separately from the provider model catalogue; changing a model does not require adding or editing an API key. The connection test uses the provider's enabled default model and displays the complete text response.
- **Learning Progress & Cloud Sync**: Stores application learning data locally with Room and supports Firebase Authentication and per-user Firestore synchronization under the configured security rules.

## Generation Studio task isolation

Generation outputs are retained as task-specific drafts. Question-bank extraction is restricted to the `QUESTIONS` task, question rows are promoted to reviewed status through explicit draft approval, and learner quizzes exclude unreviewed rows and legacy interactive checkpoint rows. Interactive activities are parsed as structured JSON for approval and publishing; ordinary numbered lesson prose is not treated as an activity. Publishing is deferred until an explicit approval/publish action.

Draft version numbers are scoped by workspace, book, unit, lesson, and task. The Studio shows drafts for the selected task, clears the active editor context when the task or lesson changes, and exposes the saved lesson-text source actions. The learner view displays the lesson PDF when available; if no PDF exists, it opens a published interactive lesson package when one is available. Raw lesson source text is reserved for teacher/Studio preparation and is never displayed to learners. If neither PDF nor a published interactive package exists, the learner sees a preparation message. The complete `INTERACTIVE_LESSON` generation-to-publication workflow remains a tracked implementation item; it is distinct from ordinary interactive activities.
Interactive checkpoint rows (`q-int-*`) are recorded lazily when a learner answers a checkpoint, not pre-inserted when a package is generated. They are tagged with `source_type = INTERACTIVE_CHECKPOINT` and excluded from the official question-bank view and end-of-lesson quizzes, while their attempts remain available to concept-mastery analytics. The `q-int-*` ID prefix is retained as a compatibility fallback for legacy checkpoint rows. Room migration 18→19 adds `source_type`; existing rows remain `LEGACY_UNKNOWN` because their origin cannot be inferred safely.

## Generation Studio pipeline and prompt quality

The Studio now exposes three execution modes over one shared AI provider path: single-lesson content, bulk question-bank generation for a selected subject, and full subject-content generation. Each mode supports create, update and review operations. Bulk artifacts are saved as `DRAFT` and are not approved or published automatically.

New task types and prompt assets are wired for concept extraction, source-question extraction, explanatory interactive lessons, educational activities, assessments, content review, remediation resources and learner reports. The legacy `INTERACTIVE` task remains a practice activity and has a structured visual preview. Question-bank JSON is displayed as readable question content rather than an editable raw JSON document.

Important boundaries: concepts are not yet normalized into stable relational IDs; source questions, assessment instruments and remediation resources remain draft artifacts; a durable batch-job table and typed field-level JSON editors are still pending; the explanatory interactive-lesson renderer does not yet execute diagram/3D blocks or manage glTF assets. See [Generation Studio bulk pipeline and prompt audit](docs/GENERATION_STUDIO_BULK_PIPELINE_AND_PROMPT_AUDIT.md) for details.

## Book PDF indexing status

The PDF intake and splitting building blocks are not the same as a completed index-to-split user flow. The current implementation includes the intake draft/validation contract and PDF ingestion/splitting components, but the following integration work remains before claiming full automatic indexing:

1. Connect PDF upload to the book-index discovery request in the shared AI service.
2. Parse and validate the AI response into the book-index intake draft.
3. Present the discovered book metadata, parts, units, lessons, and page ranges for user review.
4. Apply the configured offset to printed page numbers exactly once when physical PDF page numbers are not already supplied.
5. Persist the user-approved canonical index and only then invoke the PDF splitter.
6. Verify the generated lesson PDFs and their links to the approved curriculum records.

The source PDF must remain intact; temporary working copies may be cleaned up only after successful processing. The printed page number in the book, the physical page number in the original PDF, and the local page number inside a split lesson PDF are distinct coordinate systems.

## Recent Generation Studio task reports

- [Post-merge technical audit](docs/GENERATION_STUDIO_MAIN_BRANCH_AUDIT_2026-11-28.md)
- [Task-by-task completion report](docs/GENERATION_STUDIO_TASK_REPORT_2026-11-28.md)

## Build & Test

- Build debug APK: `gradle assembleDebug`
- Run unit tests: `gradle :app:testDebugUnitTest`
- Run Firestore Security Rules unit tests (with Firebase Emulator): `node --test firestore.test.js`
