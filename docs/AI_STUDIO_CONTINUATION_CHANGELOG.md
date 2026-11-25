# AI Books — Change Log and Google AI Studio Continuation Handoff

**Repository:** `ameryhusam/ai_books`  
**Base:** `main` at `cba69adf932260f50b7e5cea54681465dd1eba18`  
**Feature branch:** `feat/template-driven-generation-and-api-governance`  
**Reviewed head before this report:** `270c9842d8b28b14217ce10d99535f8686becfc9`  
**PR:** [#1 — Template-driven PDF generation and API key quota governance](https://github.com/ameryhusam/ai_books/pull/1)  
**Review date:** 2026-11-26

## 1. Scope and evidence boundary

The GitHub comparison reports 106 commits ahead of `main`, 0 behind, and 21 changed files. The first commit after the comparison base is `dc9432f` — “Add template schema and generation provenance models”; the latest reviewed head is `270c984`.

**Important baseline clarification:** the comparison base is the merge-base with `main` (`cba69ad`, “docs(analytics): define weekly aggregation in local time zone”). The available Git evidence does not identify this commit as an “AI Studio” commit. Therefore this report accurately covers the 106 commits after the recorded merge-base. If “AI Studio commit” refers to a different SHA, the range must be recalculated from that SHA; this report does not silently relabel the merge-base.

This report records the changes visible in the PR diff, commit subjects, and inspected source files. It does not claim that the Android build, unit tests, provider calls, or Room migration have passed: the PR explicitly states they were not run in the authoring environment, and migration 15→16 has not yet been exercised against a real v15 database.

## 2. Executive summary of the 106 commits

The commits are a series of incremental implementation and correction commits, not 106 independent features. Their changes group into these areas:

1. **Template-driven generation contract:** move response shape/schema and field mapping from task-specific hard-coded prompt text into persisted, editable prompt templates; pass the selected template contract into generation.
2. **Provider response adaptation:** translate provider-specific JSON keys and root-array names into the app’s canonical question/activity representation before persistence.
3. **PDF-native Gemini input:** read and validate the original PDF, compute SHA-256, and attach it as `application/pdf` to the Gemini request alongside the assembled prompt. Keep rendered page images for OpenAI-compatible endpoints.
4. **Generation provenance:** record template identity/version, schema hash, request fingerprint, content hash, source PDF hash, and actual provider/model information; preserve provenance when drafts are edited, backed up, restored, and synchronized to cloud drafts.
5. **API key governance:** encrypted per-key daily request quotas, timezone-based reset, atomic reservation, key state classification, cooldown after rate limiting, manual disable/reactivation, and least-recently-used selection.
6. **Generation progress UI:** report actual pipeline stages and use indeterminate progress while waiting for a provider response rather than inventing token-level progress.
7. **Validation and regression coverage:** validate template JSON before saving; add focused tests for response mapping, PDF payload preservation, and quota reset/reservation.
8. **Documentation:** add provider/API, template governance, local testing, and learning-progress notes.

The commit subjects also record fixes to PDF streaming/hash consistency, duplicate prompt instructions, template edit cancellation, response schema normalization for Gemini, key replacement state reset, retry-after handling, cloud provenance restoration, and content-hash recalculation after editing.

## 3. Actual file-level changes

### 3.1 Prompt assets and prompt assembly

**`app/src/main/assets/prompts/indexer2/question_bank.prompt.md`**
- Removed the embedded hard-coded question JSON example and task-specific field list.
- Replaced it with an instruction to follow the response schema saved in the task template.

**`app/src/main/assets/prompts/indexer2/interactive_activity.prompt.md`**
- Removed the embedded interactive JSON contract.
- Replaced it with an instruction to follow the response schema saved in the task template.

**`app/src/main/java/com/example/data/ai/Indexer2PromptRegistry.kt`**
- Prompt construction accepts `responseSchemaJson` and `responseFormat`.
- The task instruction uses the task label rather than duplicating the former task-specific output-contract prose.
- Appends selected template instructions and the stored response schema when applicable.
- Includes question count, style/difficulty hints, source text, and PDF attachment context in the assembled prompt.
- The commit history records removal of task-specific hard-coded response contracts and resolution of the generation contract from persisted templates.

**Compatibility point:** the prompt asset is now the task guidance; the persisted template owns the output contract. Do not reintroduce a second hard-coded JSON schema in these asset files.

### 3.2 Provider generation and PDF transport

**`app/src/main/java/com/example/data/ai/AiGenerationService.kt`**
- Added `PdfDocumentPayload` with Base64 content, SHA-256, and byte count.
- `ProviderCallException` now carries HTTP code and retry-after epoch time.
- `buildPrompt(...)` accepts response schema and response format.
- Added `sha256LessonPdf(...)` and `extractLessonPdfBase64(...)`.
- PDF reading is bounded; the local maximum is 18 MiB; the file signature is checked for `%PDF-`; empty or oversized files are rejected.
- `generate(...)` accepts PDF payload, template/schema provenance, provider reservation and provider-state callbacks.
- Provider selection uses last-used time, priority, and creation time.
- JSON expectation is derived from the selected template’s response format/schema rather than only from task type.
- Gemini receives the original PDF as `application/pdf` inline data when supplied; otherwise the existing rendered-page fallback remains available.
- OpenAI-compatible endpoints continue to use rendered page images in this flow.
- Gemini structured-output schema is normalized before being sent.
- HTTP failures are classified into invalid-key, rate-limit, quota-exhausted, and generic error states.
- Added bounded stream reading, schema normalization, retry-after parsing, and SHA-256 helpers.

### 3.3 Provider response compatibility

**`app/src/main/java/com/example/data/ai/TemplateResponseAdapter.kt`** — new file
- `adapt(raw, mappingJson)` adapts provider JSON to the canonical internal response contract.
- Supports configured root-array and target-array names.
- Supports configured field aliases, including examples such as `stem → prompt`, `choices → options`, and `answer → correctAnswer`.
- Keeps plain text or unmapped content unchanged when it cannot safely adapt it.
- The adapter separates provider-facing response names from the app-facing model.

**`app/src/main/java/com/example/data/repository/EduMiniRepository.kt`**
- Integrates the template selection/contract with generation and persistence.
- Adds the persistence and retrieval path for template metadata and generation provenance.
- Wires provider quota reservation/state callbacks into generation.
- Handles the selected response mapping before saving generated question/activity content.
- The PR diff reports this as one of the largest changed files (+287/-62); inspect this file’s final implementation before refactoring it.

### 3.4 Room database and data models

**`app/src/main/java/com/example/data/local/EduMiniDatabase.kt`**
- Room database version changes from 15 to 16.
- Adds `MIGRATION_15_16`.
- Adds response contract fields to `prompt_templates`: `response_schema_json`, `response_mapping_json`, `response_format`, `source_attachment_mode`, and `version`.
- Adds generation provenance fields to `generation_drafts`: `template_id`, `template_version`, `response_schema_hash`, `request_fingerprint`, `content_hash`, and `source_pdf_hash`.
- Migration uses additive columns/table creation with defaults in the reviewed statements.

**`app/src/main/java/com/example/model/Models.kt`**
- Adds/updates local model properties required for editable prompt-template contracts, provider state/quota metadata, and generation provenance.
- Keep field meanings and defaults aligned with Room entities, converters, repositories, and backup serialization.

**`app/src/main/java/com/example/model/CloudModels.kt`**
- Adds optional provenance fields to the cloud draft model.
- Fields are additive/optional to preserve compatibility with existing cloud draft documents.

**`app/src/main/java/com/example/data/firebase/FirestoreSyncRepository.kt`**
- Adds provenance to cloud draft serialization/deserialization.
- Follow-up commits specifically correct provenance map placement, avoid duplicate keys, restore provenance into editor state, and import generation metadata when saving edited drafts.

### 3.5 API key storage and governance

**`app/src/main/java/com/example/data/security/ProviderStorage.kt`**
- Adds encrypted per-key quota and usage/state persistence.
- Adds timezone-based daily reset and atomic request reservation.
- Tracks last-used time for least-recently-used rotation.
- Tracks provider failure states and retry-after cooldown.
- Supports manual disable/reactivation and excludes unusable/exhausted keys from selection.
- Replacing a stored key resets the old key’s quota/authentication state.
- The PR describes Android Keystore and AES/GCM protection; verify behavior on an actual device and existing installations.

**`app/src/main/java/com/example/ui/screens/SettingsScreen.kt`**
- Adds UI for quota limit/timezone and provider key state.
- Exposes manual enable/reactivation controls and usage/status information.

**`app/src/main/java/com/example/ui/MainViewModel.kt`**
- Connects UI operations to template editing, provider quota/state, and generation progress.
- Exposes generation-stage updates and cleanup/cancellation behavior.
- Exposes template save/edit operations and the related validation feedback.

**`app/src/main/java/com/example/ui/EduMiniApp.kt`**
- Wires the new generation/template/progress and settings flows into the app UI.

**`app/src/main/res/values/strings.xml`**
- Updates the corresponding user-facing string resource(s).

### 3.6 Workspace export and backup

**`app/src/main/java/com/example/data/workspace/WorkspaceExporter.kt`**
- Extends full backup/export serialization to preserve prompt-template response contracts and generation provenance.
- Commit history records restoration of saved schemas and provenance from backups.

### 3.7 Tests and documentation

**`app/src/test/java/com/example/EduMiniCoreTest.kt`**
- Adds `providerQuotaReservationFreezesKeyUntilNextTimezoneDay`.
- Adds `templateResponseAdapterMapsProviderSpecificQuestionFields`.
- Adds `templateResponseAdapterLeavesPlainTextAndUnmappedJsonUntouched`.
- Adds `extractLessonPdfBase64PreservesOriginalPdfDocument`.
- Commit history also records concurrent quota reservation coverage and backup round-trip checks. These tests still require execution in the project’s local build environment.

**Documentation changed/added:**
- `docs/GENERATION_STUDIO_PROVIDER_API.md`
- `docs/LEARNING_PROGRESS_REPORT_AND_ASSESSMENT.md`
- `docs/LOCAL_ANDROID_TESTING.md`
- `docs/TEMPLATE_DRIVEN_GENERATION_AND_API_GOVERNANCE.md` (new)

## 4. Fixed compatibility contract — preserve these foundations

The following are the stable boundaries to preserve while continuing work with Google AI Studio/Gemini or another model:

1. **Canonical app models remain authoritative.** Provider output is adapted to the app’s existing question/activity models before persistence. Do not let each provider introduce a separate database model.
2. **Prompt and response contract remain separate.** Prompt instructions describe the task; `response_schema_json` defines the output structure; `response_mapping_json` maps provider field names to canonical app fields; `response_format` controls text/JSON mode.
3. **The selected persisted template is the source of the generation contract.** Do not add fallback hard-coded task schemas that silently override a blank or edited template contract.
4. **Keep the canonical task identity and existing task routing.** Do not rename or repurpose task types, question/activity persistence paths, or established lesson/workspace identifiers without a migration and regression plan.
5. **Gemini PDF transport:** when native PDF mode is selected and a valid PDF payload is provided, send the original PDF with MIME type `application/pdf` in the same generation request as the assembled prompt.
6. **OpenAI-compatible transport:** retain the existing rendered-page image path; do not assume every compatible endpoint accepts native PDF input in the same format.
7. **Schema normalization is provider-specific.** Gemini structured output needs provider-compatible schema normalization; do not mutate the canonical stored schema in place just to satisfy a provider.
8. **Provenance fields must remain stable and additive:** `template_id`, `template_version`, `response_schema_hash`, `request_fingerprint`, `content_hash`, `source_pdf_hash`, and actual provider/model metadata. Editing a draft may recompute content hash but must not erase the original request/source/template provenance.
9. **Existing Room data must remain readable.** Preserve Room entities, column defaults, migration chain, and backup compatibility. Never edit the database version/schema without a migration and migration test.
10. **Provider key secrets remain encrypted and excluded from backups/cloud sync.** Only non-secret status/provenance/configuration needed by the app may be serialized.
11. **Quota reservation must remain atomic.** Do not move quota checking into a non-atomic UI-only check; reserve before the HTTP call and handle failure status/cooldown consistently.
12. **Progress UI must represent real stages.** Use indeterminate progress while waiting for an HTTP response; do not manufacture percentage/token progress without provider data.
13. **Do not remove learner identity isolation, assessment history, question approval gates, or progress-report safeguards already present in the base.** These are pre-existing foundations in the comparison base and must not be weakened by the generation work.
14. **No student name or personal profile data is required by generation prompts.** Use stable internal IDs only where linkage is necessary; do not expose identifying information to model providers.

## 5. Work still open — continue without changing the foundations

### 5.1 Validation and release gate
- Run the project’s documented local Gradle unit tests and Android build.
- Exercise Room migration 15→16 against a real v15 database copy, including existing prompt templates and generation drafts.
- Test clean install and upgrade paths separately.
- Test full backup → restore with existing data and new template/provenance fields.
- Test old and newly added API keys, quota exhaustion, timezone reset, 429 rate limit, 401/403 invalid key, cooldown, key replacement, and concurrent reservation.
- Test Gemini native PDF requests and OpenAI-compatible rendered-page requests with real enabled provider models.
- Confirm provider model IDs against the actual account’s enabled model catalog; presets are editable defaults, not availability guarantees.

### 5.2 Template governance
- Inspect the current template editor and persistence path before adding any new template tables or screens.
- Confirm that invalid schema/mapping JSON is rejected with a visible validation message and does not erase the user’s editor input.
- Confirm that template edits increment versions and that old generation drafts retain the exact template version used.
- Add schema validation and canonical response validation as separate steps.
- Do not silently replace a missing/blank template schema with code defaults.

### 5.3 Educational tasks not completed by this PR
The PR explicitly leaves these out of scope:
- Friday weekly assessment scheduling and editable study/assessment calendar.
- Assigned remedial explanations with open/read/complete tracking.
- Linking remedial explanations to follow-up assessments.
- Wiring these outcomes into progress reports.
- WorkManager reminder/worker and learner-facing weekly assessment launch surface.

These should be separate follow-up work, after current generation changes pass validation.

### 5.4 Learning analytics enhancements
The following are future requirements, not completed features of this PR:
- Separate deterministic response recording from AI interpretation.
- Calculate accuracy/mastery/retention/transfer/prerequisite/concept-integration metrics from recorded attempts.
- Use minimum evidence thresholds and distinguish assisted from independent attempts.
- Trigger early warnings only from sufficient evidence, then assign a new remedial explanation and fresh questions for the same learning objective.
- Do not treat repeated attempts at the same question as independent proof of mastery.
- Keep AI as an explanation/recommendation layer, not the sole source of scores or dashboard metrics.

### 5.5 Future cross-model review
Not part of the current implementation. Later, an optional reviewer model may receive the original prompt, source, expected schema, and first model response to critique factual grounding, completeness, and schema compliance. Preserve both original outputs; do not treat the reviewer model as automatically correct. Keep human review for disputed or high-impact content.

## 6. Handoff instructions for Google AI Studio / Gemini work

Continue from the merged repository state and the contracts above. Before changing code:

1. Read this report and the current implementation of the files listed in section 3.
2. Inspect the current `main` branch, Room entities/migrations, canonical question/activity models, template editor, prompt builder, and provider request code. Do not rely on this report as a substitute for current source.
3. Classify every proposed change as: existing behavior, bug fix, additive feature, schema migration, or deferred requirement.
4. Propose a short file-by-file plan and identify affected canonical fields/contracts before editing.
5. Preserve the Gemini native-PDF path and the OpenAI-compatible rendered-image path.
6. Preserve template-owned response schema/mapping and canonical app models.
7. Do not hard-code response JSON in prompts or code as a fallback to template configuration.
8. Do not create duplicate tables or schema definitions before proving the required data is not already represented.
9. Add or update focused tests with each behavior change. Run the local test/build commands and report exact results; do not claim success without output.
10. Keep new educational analytics, remedial scheduling, and cross-model review in separate follow-up changes unless explicitly prioritized.
11. You may review, challenge, and suggest better designs. Explain the evidence and compatibility impact before making a breaking change.
12. Report changed files, functions/classes, migration impact, tests run, and remaining risks in every handoff.

## 7. Merge readiness status at the time of review

- Git comparison: `main...feature` = 106 commits ahead, 0 behind.
- PR state at review: open, draft, mergeable reported true.
- PR validation note: Android build and unit tests not run in the reported environment.
- Room 15→16 migration: added, not verified against a real v15 database.
- Therefore, Git history is mergeable, but functional validation is not established by the available evidence. Do not interpret GitHub’s `mergeable=true` as proof of runtime correctness.

## 8. Suggested next sequence

1. Complete local tests/build and migration/backup/provider validation for this change set.
2. Fix only verified regressions while preserving the compatibility contract above.
3. Merge the generation/template/API-governance work.
4. Continue with weekly assessment/remedial scheduling as a separate feature.
5. Implement deterministic response records and learning analytics before building AI-based recommendations.
6. Add concept integration/transfer assessment and evidence-based early warnings.
7. Add optional cross-model review only after the primary workflow is stable.

---

## 9. Main-branch continuation update — 2026-11-26

This section supersedes the earlier handoff's merge-readiness snapshot. It records the subsequent provider-isolation merge and the verified source corrections applied directly to `main`.

### 9.1 Merge record

- **Merged PR:** [#2 — Merge provider isolation and Gemini routing foundation](https://github.com/ameryhusam/ai_books/pull/2)
- **Source branch:** `feat/provider-isolation-local-workspace`
- **Source head:** `08e7fc833af92e305fb0f38c864a2e145a9a35da`
- **Merge commit on main:** `f6d2eea946653a8311de140bb6a227fdd1efc83e`
- **Merge result:** GitHub confirmed `merged=true`.
- **PR change set:** 26 commits, 12 files, 381 additions, 21 deletions.
- The merge brought explicit Gemini/OpenAI selection, provider-profile isolation, Gemini model-level cooldown/routing, bounded retries, provider-routing tests, and the implementation roadmap into `main`.

### 9.2 Build & Missing UI corrections applied directly to main

| Area | Finding from current source | Main-branch change / status |
|---|---|---|
| Room singleton | `EduMiniDatabase.getInstance(context)` had no declared return type although callers use its result as `EduMiniDatabase`. | **Fixed:** explicit return type `: EduMiniDatabase`. No database schema or migration change. |
| MessageDigest | `MessageDigest` import is present in the merged `AiGenerationService.kt` and in `WorkspaceImporter.kt`. | **Already present:** no redundant import edit was made. |
| WorkspaceImporter duplication | Two identical implementations of `verifyExportManifest` and `collectRelativeFiles` existed in the same source file. URI and stream import entry points also repeated the same first-grade commit logic. | **Fixed:** removed the second identical helper pair and extracted shared `importFirstGrade(preview)` logic. ZIP safety, manifest SHA-256 validation, and import behavior are retained. |
| MainViewModel flow initialization | `learningProgressReport` combined `workspaceQuestionAttempts` before that StateFlow was initialized in declaration order. | **Fixed:** moved the `workspaceQuestionAttempts` StateFlow declaration before `learningProgressReport`. |
| MainUiState provenance | Existing ViewModel code read/wrote active-draft provenance properties that were missing from `MainUiState`. | **Fixed:** added template ID/version, response-schema hash, request fingerprint, content hash, and source-PDF hash fields; wired values from generated output and local/cloud draft loading. Edited draft saves refresh the content hash from the newly persisted record. |
| Dashboard remediation callback | `DashboardScreen` already declared and invoked `onGenerateConceptRemediation`; `EduMiniApp` already passed `viewModel::generateConceptRemediation`. | **Already wired:** no duplicate callback or second route was added. |
| Generation progress | Nullable `state.generationProgressPercent` was read directly inside the Compose progress lambda. | **Fixed:** capture it in a local `generationProgressPercent` value before the null check and use the stable local value in the progress UI. |
| Reinforcement dialog | `EduMiniApp` imported and invoked `ConceptReinforcementQuizDialog`, but the referenced screen source file did not exist on `main`. | **Implemented:** added `ConceptReinforcementQuizDialog.kt` with per-question answer/check flow, answer feedback, session result, and one completion callback to record attempts. |

### 9.3 Files changed in this continuation

- `app/src/main/java/com/example/data/local/EduMiniDatabase.kt`
- `app/src/main/java/com/example/data/workspace/WorkspaceImporter.kt`
- `app/src/main/java/com/example/ui/MainViewModel.kt`
- `app/src/main/java/com/example/ui/EduMiniApp.kt`
- `app/src/main/java/com/example/ui/screens/ConceptReinforcementQuizDialog.kt` (new)
- `docs/AI_STUDIO_CONTINUATION_CHANGELOG.md` (this update)

### 9.4 Validation status and remaining acceptance gate

The source edits above are committed to `main`. **They are not yet build-verified.** GitHub Actions run [#294](https://github.com/ameryhusam/ai_books/actions/runs/45714248503) on commit `c28de986888bcb8ed7484598f77b927d8d046fde` completed with `failure`; its `android-check` job exposed an empty steps list and no log URL. A rerun (attempt 2) failed in the same way, and the job-log endpoint returned `BlobNotFound`. Subsequent push runs for commits `c0d1b905d9978a3c8844b68aa5e68ce5c40c5aab` and `85c17a1d6357b9118df408041a0505008718a28c` also failed with no exposed steps/logs; the latest source-validation run observed before this changelog revision is [run 45714427390](https://github.com/ameryhusam/ai_books/actions/runs/45714427390), which again exposed no job steps or log URL. These are not Kotlin compiler diagnostics and cannot be treated as proof that the source compiles or fails. The previous local clone attempt was blocked by DNS resolution. Do not report `:app:compileDebugKotlin`, `:app:testDebugUnitTest`, or `:app:assembleDebug` as passing until their actual output is available.

Required acceptance commands:

```bash
./gradlew :app:compileDebugKotlin
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

Additional targeted checks:
- Verify the new reinforcement dialog records attempts exactly once on successful session completion and records no incomplete session when dismissed.
- Verify `MainUiState` provenance survives local draft load, cloud draft load, generation, and edited-draft persistence.
- Verify the Room singleton change does not alter database version or migration registration.
- Verify ZIP manifest validation still rejects path traversal, missing files, byte-count mismatches, and SHA-256 mismatches after helper deduplication.

**Status:** implementation committed; compile, unit tests, and Android assembly remain pending until CI or a local Android/Gradle environment returns inspectable results.

---

## 10. Phase P0 — Build, reinforcement dialog, and Firestore provenance audit (2026-11-26)

### 10.1 Six Kotlin files named in the AI Studio report

The current `main` source was re-inspected. The following records distinguish a verified source correction from a pre-existing correct implementation; they do **not** claim a successful compiler run.

| File | Audit result |
|---|---|
| `data/local/EduMiniDatabase.kt` | The Room singleton now explicitly returns `EduMiniDatabase`. The database version and migration declarations were not changed in this pass. |
| `data/ai/AiGenerationService.kt` | `java.security.MessageDigest` is imported and used by the SHA-256 helpers. No second import or duplicate helper was added. Provider-specific Gemini/OpenAI routing remains separate. |
| `data/workspace/WorkspaceImporter.kt` | `MessageDigest` is imported; exactly one `verifyExportManifest`, one `collectRelativeFiles`, and one shared `importFirstGrade` implementation are present. |
| `ui/MainViewModel.kt` | `workspaceQuestionAttempts` is declared before `learningProgressReport`; the six active-draft provenance properties exist in `MainUiState` and are populated from generated, local, and cloud draft records. |
| `ui/EduMiniApp.kt` | The nullable generation progress value is captured locally before it is used by the Compose progress indicator. The concept-remediation callback is passed to `DashboardScreen`, and the reinforcement dialog call at the existing integration point supplies the same five arguments as its declaration. |
| `ui/screens/DashboardScreen.kt` | `onGenerateConceptRemediation: (String?, TaskType) -> Unit` exists and is invoked for explanation and question remediation actions. It was already wired; no duplicate callback was introduced. |

### 10.2 Reinforcement dialog

- `ConceptReinforcementQuizDialog.kt` is present and its signature matches the invocation in `EduMiniApp.kt`: title, question list, question statistics, attempt-recording callback, and dismiss callback.
- It uses the existing shared answer widgets and `QuestionBankEngine.gradeQuiz`.
- Added a completion guard so the final attempt-recording callback is submitted once per dialog session, even if the finish action is tapped repeatedly before recomposition.
- Dismissing before completion does not call the attempt-recording callback.

### 10.3 Firestore provenance contract — mismatch found and corrected

The Kotlin `CloudDraft` model and `FirestoreSyncRepository.upsertDraft` already serialize these six fields:

- `templateId`
- `templateVersion`
- `responseSchemaHash`
- `requestFingerprint`
- `contentHash`
- `sourcePdfHash`

The previous Firestore rules did not allow these fields in the draft `hasOnly` allowlist or in the content/provenance update allowlist. This meant valid Kotlin writes could be rejected with `PERMISSION_DENIED`. The blueprint also omitted the six properties.

Corrections applied:

- `firestore.rules`: added optional-field validation, create allowlist support, and update allowlist support. Template IDs are bounded to 160 characters; template versions are integers in the range 1–10000; hash values are empty or 64-character lowercase SHA-256 hex strings.
- `firebase-blueprint.json`: added the six optional properties and matching bounds/patterns. They remain optional in `required` so older cloud drafts remain readable and valid.
- `firestore.test.js`: retained the legacy draft case without provenance; added valid provenance create/update coverage and rejection cases for malformed SHA-256 and invalid template version.
- `FirestoreSyncRepository.kt`: validates and normalizes SHA-256 provenance before writing, rather than truncating a potentially malformed value to 128 characters and allowing the security rules to reject it later.

### 10.4 Quick audit of adjacent integration points

- Confirmed the Firestore model field names and types match the Kotlin serializer and blueprint.
- Confirmed local draft persistence computes a full SHA-256 content hash and generation computes schema/request/PDF hashes using the same lowercase hexadecimal representation expected by the rules.
- Confirmed the old cloud-draft shape remains accepted because provenance fields are optional.
- Confirmed the existing CI workflow runs Android tests/build only; it does not execute `firestore.test.js`. The repository currently has no root `package.json`, so the Firestore emulator test dependencies are not pinned in a reproducible Node test manifest. The new Firestore tests are therefore **added but not executed** in this pass.

### 10.5 Validation status

- Source-level audit and cross-file contract checks: completed.
- JSON parse of `firebase-blueprint.json`: passed.
- Static consistency checks for the six provenance names across blueprint, rules allowlists, and test cases: passed.
- Firestore emulator tests: pending; not executed.
- `:app:compileDebugKotlin`, `:app:testDebugUnitTest`, and `:app:assembleDebug`: pending; not executed successfully.

The latest Android CI run observed during this pass is [run 45715165859](https://github.com/ameryhusam/ai_books/actions/runs/45715165859) on commit `1d920a4faab662ba3765b320de2280759b40d6c2`. Its job reports failure with `steps=null` and `logs_url=null`, so it provides no Kotlin compiler diagnostic. This must not be represented as a passing or source-level failing build.

**Phase P0 status:** source corrections and Firestore contract alignment committed on `main`; build and emulator validation remain open acceptance gates.


---

## 11. Provider quota handling correction — 2026-11-26

### Scope and design
- Removed local dailyRequestLimit enforcement from ProviderStorage.reserveProviderKey. The legacy field remains serialized for backward compatibility, but it no longer prevents an outgoing request.
- dailyRequestsUsed is retained as diagnostic attempt telemetry only; it is not compared with a local cap.
- Provider-reported QUOTA_EXHAUSTED is retained as a diagnostic runtime status. It is not used as a local pre-request quota gate, and it is no longer automatically cleared merely because the device's local date changed.
- A successful provider request clears the diagnostic error state back to ACTIVE.
- Gemini error parsing now includes the API error status and structured details signals, rather than relying only on the human-readable message.
- Added classification for explicit QUOTA_EXCEEDED and RATE_LIMIT_EXCEEDED signals. A confirmed Gemini quota-exhaustion response stops the current generation immediately; it does not cycle through additional Google profiles/models as a quota-bypass mechanism.
- Retry-After now accepts both delta-seconds and RFC 1123 HTTP-date values. If the header is absent or invalid, the existing 60-second fallback remains.
- Gemini and OpenAI-compatible request paths remain separate. Model-level cooldown behavior for Gemini rate limiting is retained.
- SettingsScreen no longer exposes a local daily request cap or local quota-reset timezone; it explains provider-authoritative quota detection and shows the daily attempt count as diagnostic information only.

### Files changed
- app/src/main/java/com/example/data/security/ProviderStorage.kt
- app/src/main/java/com/example/data/ai/AiGenerationService.kt
- docs/AI_STUDIO_CONTINUATION_CHANGELOG.md

- app/src/main/java/com/example/ui/screens/SettingsScreen.kt

### Validation
- Source transformation checks confirmed the intended code sections were changed.
- No Gradle compiler, unit-test, or Android assembly command was executed in this pass. Build and runtime behavior remain unverified.
- The service still depends on the exact structured reason strings returned by Google; unknown HTTP 429 responses are conservatively classified as rate limiting, not quota exhaustion.
