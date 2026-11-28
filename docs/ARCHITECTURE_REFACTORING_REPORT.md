# Architecture Refactoring Report — Sprint 1

**Repository:** `ameryhusam/ai_books`  
**Base:** `main`  
**Working branch:** `refactor/extract-ui-state-models`  
**Scope:** First low-risk extraction from the oversized `MainViewModel.kt`.

## Completed

### 1. Extracted UI state declarations

Moved the following declarations from `MainViewModel.kt` into the new `MainUiState.kt` file in the same package:

- `AlertMessage`
- `PdfPreviewPayload`
- `ProviderFormState`
- `PdfSplitterUiState`
- `MainUiState`

The new file imports the existing model types and preserves the package name.

### 2. Preserved behavior and contracts

This change is intended to be structural only:

- No property names or types were changed.
- No default values were changed.
- No constructor parameters or public ViewModel methods were changed.
- No Room entities, migrations, persistence behavior, Firebase behavior, or prompt contracts were changed.
- No call-site migration is expected because the declarations remain in package `com.example.ui`.

### 3. Size impact

| File | Before | After |
|---|---:|---:|
| `MainViewModel.kt` | 137,039 characters | 133,216 characters |
| Extracted declarations | embedded | 3,822 characters in `MainUiState.kt` |

This is a first separation of UI contracts, not a complete decomposition of the ViewModel.

## Validation status

**Not yet validated by a local Android build.**

The GitHub file operations confirm that the extracted file and updated ViewModel were written to the working branch. They do not prove Kotlin compilation or runtime correctness.

Required checks before merge:

1. `./gradlew :app:compileDebugKotlin`
2. `./gradlew :app:testDebugUnitTest`
3. `./gradlew :app:assembleDebug`

Also inspect the PR diff for accidental declaration changes and verify that no duplicate declarations remain.

## Next refactoring slices

1. Extract immutable UI state models and related form state (this report).
2. Inventory MainViewModel methods by responsibility and call-site; classify state ownership.
3. Extract generation orchestration behind a dedicated use-case/coordinator while keeping ViewModel as UI adapter.
4. Extract workspace import/inspection orchestration only after mapping repository and importer dependencies.
5. Separate cloud sync state handling from local learning state.
6. Add focused tests at each seam before removing old delegation paths.

## Oversized-file inventory and incremental plan — 2026-11-27

The current source tree was inspected by file size and responsibility. Size is a prioritization signal only; it is not, by itself, a reason to split a file.

| File | Current size | Primary responsibility | Proposed extraction boundary |
|---|---:|---|---|
| `EduMiniCoreTest.kt` | 246.4 KiB | Cross-feature unit/integration tests and fixtures | Split tests by workspace, indexing, AI generation, assessment, learner progress; centralize only genuinely shared fixtures |
| `MainViewModel.kt` | 162.2 KiB | UI state orchestration across app features | Extract feature coordinators/use cases, beginning with workspace operations after their behavior stabilizes |
| `EduMiniRepository.kt` | 126.1 KiB | Persistence and operations across workspace, learning, assessment, generation and providers | Keep a compatibility facade; move one cohesive domain operation at a time behind focused collaborators |
| `StudioScreen.kt` | 119.6 KiB | Compose studio screen and its panels/dialogs | Extract stateless panels and dialogs first; retain state ownership and event contracts in the screen |
| `DashboardScreen.kt` | 95.9 KiB | Dashboard presentation | Extract independent cards/sections without changing query or navigation behavior |
| `EduMiniApp.kt` | 88.1 KiB | App root, role routing and global dialogs | Keep root routing; move global operation dialogs and role-specific content into focused composables |
| `QuestionBankScreen.kt` | 69.5 KiB | Question-bank workflows and presentation | Separate editor, filters and question cards where state boundaries are already clear |
| `EduMiniDatabase.kt` | 64.9 KiB | Room entities, DAO and migrations | Separate entities, DAO and migration definitions without changing schema or migration order |
| `DirectWorkspaceBinder.kt` | 63.8 KiB | SAF/local scanning, index validation and catalog binding | Separate tree scanning from index-to-catalog mapping; preserve the binder facade |
| `WorkspaceImporter.kt` | 41.8 KiB | ZIP extraction, validation, grade parsing and file installation | Separate archive safety/manifest verification from grade parsing and installation |

### Recommended sequence

1. **Stabilize workspace behavior first.** Complete review-mode import, multi-grade add/update, progress reporting and their tests before extracting these same code paths.
2. **Split test coverage.** Move tests into feature-specific files with no test-logic changes. This makes later refactors easier to validate and reduces the cost of diagnosing failures.
3. **Extract pure workspace helpers.** Separate archive path safety, manifest hash verification and index-to-entity mapping. Keep existing public importer/binder entry points as facades.
4. **Extract UI presentation.** Move stateless Studio and Dashboard sections into separate composables. Do not move state or business decisions during this pass.
5. **Extract repository domains.** Introduce one focused collaborator at a time while preserving `EduMiniRepository` methods as a compatibility facade; avoid a broad interface rewrite.
6. **Extract ViewModel orchestration.** Move workspace operations first, then generation and assessment workflows, only after each collaborator has tests and explicit state/event contracts.
7. **Split Room source files last.** Keep entity definitions, database version, migrations and exported schema unchanged; verify Room schema output after each extraction.

### Per-slice acceptance criteria

- One responsibility moved per commit; no simultaneous behavior change unless required by the feature being fixed.
- Existing public method signatures and persisted identifiers remain stable wherever possible.
- Add or move focused tests before deleting the old implementation path.
- Compare behavior and database rows before/after; do not introduce a Room migration solely to support file organization.
- Android build is run at a deliberate validation checkpoint (PR open/ready or manual dispatch), not on every commit.

No broad file decomposition is included in this workspace feature PR. The inventory establishes the order and boundaries for later, independently reviewable refactoring slices.

## Guardrails

- Do not split by arbitrary line count; split by responsibility and dependency direction.
- Do not create interfaces with only one trivial caller unless they establish a real boundary.
- Do not move Room or Firebase logic into UI.
- Do not change database schema as part of this refactor.
- Do not combine architectural extraction with feature changes.
- Merge only after CI passes or an equivalent reproducible Android build/test run is recorded.


## Learner content delivery boundary — 2026-11-28

The learner experience must distinguish an internal source artifact from published learning content.

### Source-text handling

- `LessonRef.sourceTextPath` and its extracted text are source material for authoring, quick review, and AI-assisted preparation.
- Raw source text is not a learner-facing reading surface and must not be rendered by `StudentStudyDialog`.
- The source-text reader belongs behind the repository/data boundary. Studio or teacher workflows may request it when preparing content; the learner presentation layer must not load it as a fallback reading format.
- A source-text file can be selected as the AI input without attaching or uploading the lesson PDF. Source selection must be explicit and recorded as provenance; do not silently attach both sources.

### Learner presentation policy

1. If a lesson PDF exists, the learner's primary source-content tab displays the PDF only. No raw-text toggle or combined PDF+text view is offered.
2. If no PDF exists and a published interactive lesson package exists, open the interactive lesson directly.
3. If neither PDF nor a published interactive package exists, show an actionable unavailable state. Never expose the raw source text as a fallback.
4. Approved explanation/summary and quiz features remain separate learning surfaces; they are not the raw source-text file.
5. Interactive packages remain inaccessible until teacher approval and explicit publication.

### Interactive lesson is not an interactive activity

- `INTERACTIVE` remains an activity/checkpoint package and must not be relabelled as a complete lesson.
- `INTERACTIVE_LESSON` is the intended complete, pedagogically structured interactive version of the lesson.
- The target workflow must create a complete lesson experience (explanation, examples, learning steps and formative interactions) and transform eligible reviewed textbook questions into interactive question/checkpoint components while retaining source provenance.
- Generation from a selected text source must work without requiring a PDF upload. PDF may remain an optional alternative input.
- Generated content remains a draft. Review, validation, approval and publication are explicit gates.

### Implementation status and remaining feature work

- Learner UI no longer loads or renders the raw source text. The learner flow starts on the PDF when present, otherwise on an existing published interactive package.
- When no PDF and no published interactive package exist, the learner sees a preparation message rather than a blank PDF or raw text.
- The complete `INTERACTIVE_LESSON` generation-to-published-package pipeline, including mapping reviewed book questions into interactive components, is not yet proven end-to-end and must be tracked as a separate feature slice. Do not claim the no-PDF fallback is complete until this pipeline and its publication/learner tests pass.

### Feature ownership boundaries

| Feature | Owns | Must not own |
|---|---|---|
| Studio | Source selection, quick review, AI task orchestration, draft editing, review and publish actions | Learner progress presentation |
| QuestionBank | Reviewed source questions, question provenance, assessment eligibility and grading | Interactive lesson layout or raw PDF rendering |
| Library | Workspace/book/lesson metadata and source artifact references | AI generation policy or learner UI decisions |
| Learner | Published PDF/interactive content presentation, quiz flow and progress events | Raw source-text display, source-file IO, draft approval |
| Admin | Access policy, operational oversight and reporting | Bypassing content approval/publication |

The existing `EduMiniRepository` remains a compatibility facade while these responsibilities are extracted incrementally. No Gradle module split is included in this phase.
