# Implementation roadmap: provider isolation and local workspace

Base: `main` at `8d1e0377afc1c964fb1259f36a5d2178eabb98ed`.
Working branch: `feat/provider-isolation-local-workspace`.

## Non-negotiable decisions

- Gemini and OpenAI are independent providers. Provider selection is explicit; failure in one provider never invokes the other.
- Gemini alone owns Gemini model fallback and Gemini key selection/rotation. Candidate models must be configured, available to the selected project/key, and compatible with the task contract.
- OpenAI remains independently selectable/testable and owns its own model, key, retry, and error policy.
- PDF splitting is paused and removed from active application flows. PDF reading, page rendering, OCR/text extraction, source attachment, and workspace indexing remain.
- Local workspace is the source for discovering grade/subject/book/lesson metadata. No persistent lesson JSON snapshot is introduced in this work.

## Phase 0 — Baseline and dependency inventory (P0)

- Confirm branch/base and inspect current provider, workspace, PDF, Room, backup, and test contracts.
- Record all call sites and tests before removing dependencies.
- Do not change Room schema or delete persisted data in this phase.

Exit criteria:
- Current compile/test baseline is recorded from an actual local Gradle run.
- A dependency map identifies provider routing and every PDF splitter call site.

## Phase 1 — Provider isolation (P0)

- Add/retain one explicit selected-provider setting using the existing settings persistence pattern.
- Route each generation request to exactly one provider implementation.
- Gemini: keep key/model candidate iteration inside Gemini only; classify 401/403, 429 quota vs rate-limit, 5xx, network and invalid-request failures; use bounded exponential backoff with jitter and Retry-After; enforce one quota reservation per actual HTTP dispatch.
- OpenAI: separate implementation and settings/test connection path; no Gemini key/model state is read by it.
- A provider switch affects new requests only; in-flight requests retain their selected provider.
- Preserve template prompt, response schema/mapping, attachment semantics and provenance.

Exit criteria:
- Tests prove Gemini failure never dispatches OpenAI and vice versa.
- Tests prove model/key fallback is provider-scoped and bounded.
- Test-connection action tests only the selected profile/provider.
- No secrets appear in logs or error messages.

## Phase 1 implementation status — source changes committed; validation pending

Implemented on `feat/provider-isolation-local-workspace`:

- Added a persisted default provider selection (`GEMINI` by default) separate from provider profiles.
- Added a Settings selector for the default provider.
- Captured the selected provider in each generation request. A provider change affects subsequent requests; an in-flight request keeps its captured selection.
- Filtered active provider profiles before generation. Gemini requests can only use Gemini profiles and OpenAI requests can only use OpenAI-compatible profiles.
- Restricted PDF attachment preparation to the selected provider's supported path: native PDF for Gemini; rendered page images for OpenAI-compatible endpoints.
- Added `ProviderRoutingPolicy` to centralize provider-scoped profile ordering and model fallback candidates.
- On Gemini HTTP 429, apply a model-scoped cooldown (profile + model) and immediately try the next configured Gemini model/key; sibling models on the same key remain eligible. Quota-exhausted keys are marked and skipped.
- Transient non-429 failures use bounded exponential backoff with jitter; every actual dispatch reserves its own request slot.
- Added tests for provider isolation, strict-model behavior, and persistence of the selected provider.
- Added the missing `MessageDigest` import in `AiGenerationService.kt`.

Validation limitations:
- Source changes and call-site wiring were inspected through the repository.
- A local clone/build attempt could not start because this execution environment could not resolve `github.com`; Java 21 is available, but Gradle is not installed in the environment. The feature-branch CI run (`45713201220`) failed in approximately four seconds with no recorded steps and no retrievable job log (`BlobNotFound`), so there is no evidence that a runner or Gradle started. This is an infrastructure/runner failure signal, not a confirmed Kotlin compilation failure.
- The CI workflow has been updated to run on pushes to `main` and `feat/**` branches, and on pull requests targeting `main`. No completed workflow result has been observed yet, so no Gradle result or APK is claimed.
- Before declaring Phase 1 complete, run `:app:compileDebugKotlin`, `:app:testDebugUnitTest`, and `:app:assembleDebug` in an Android/Gradle environment. Resolve any compiler/test failures before beginning Phase 2.

## Phase 2 — Pause PDF splitting (P0)

- Remove splitter entry points from active UI/navigation and repository workflows.
- Remove dependency injection and constructor parameters only after all call sites/tests are migrated.
- Keep PDF open/read, PDF page rendering, original-PDF attachment, OCR/text extraction, and source references.
- Retain historical implementation in Git history; do not delete shared PDF utilities.

Exit criteria:
- No active application call path invokes PDF splitting.
- Existing PDF reading/attachment tests still pass.
- No Room migration is needed solely for hiding/removing the feature.

## Phase 3 — Local workspace scanner and curriculum index (P1)

- Scan user-selected local workspace directories through Android Storage Access Framework; do not assume arbitrary filesystem paths.
- Discover existing grade/subject/book structures and canonical index/manifest files.
- Resolve metadata by priority: explicit metadata, folder structure, index/manifest, then content/OCR inference; ask for confirmation when ambiguous.
- Build/update the existing workspace index and reuse current entities/repositories where possible.
- Track file identity/metadata and changed files; avoid duplicate imports and destructive overwrite.
- Keep ZIP import compatibility unless explicitly retired in a separate decision.

Exit criteria:
- Existing local workspace can be scanned without PDF splitting.
- Grade/subject/book/lesson index is reproducible and validates file references.
- Re-scan is idempotent and preserves user data.

## Phase 4 — Regression, migration and release validation (P1)

- Run unit tests, Room migration tests from supported historical versions, backup round-trip tests, and provider contract tests.
- Run `:app:compileDebugKotlin`, `:app:testDebugUnitTest`, and `:app:assembleDebug`.
- Inspect APK output and document remaining limitations.

## Explicitly deferred

- Persistent lesson JSON snapshots.
- Automatic cross-provider fallback.
- New Room tables unless the existing schema cannot represent a demonstrated requirement.
- Reworking curriculum content extraction beyond what is necessary for local workspace indexing.
- New AI features unrelated to provider isolation and workspace discovery.
