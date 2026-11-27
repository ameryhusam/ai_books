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
