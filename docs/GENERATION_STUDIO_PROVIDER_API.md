# Edu Mini Generation Studio — Functional, Template and Provider Contract

**Document status:** corrected against the current Android implementation and the accepted design decisions  
**Scope:** Generation Studio, reusable prompt templates, source PDF attachment, response contracts, provider isolation, model fallback, draft persistence and future work.

This document distinguishes three states throughout:

- **Implemented:** verified in the current source tree.
- **Required design:** an accepted architectural rule that must govern future changes.
- **Not implemented / limitation:** a capability that must not be presented as complete.

Primary implementation references:

- `app/src/main/java/com/example/ui/screens/StudioScreen.kt`
- `app/src/main/java/com/example/ui/MainViewModel.kt`
- `app/src/main/java/com/example/data/repository/EduMiniRepository.kt`
- `app/src/main/java/com/example/data/ai/AiGenerationService.kt`
- `app/src/main/java/com/example/data/ai/ProviderRoutingPolicy.kt`
- `app/src/main/java/com/example/data/ai/GeminiModelRouter.kt`
- `app/src/main/java/com/example/data/ai/Indexer2PromptRegistry.kt`
- `app/src/main/java/com/example/data/ai/TemplateResponseAdapter.kt`
- `app/src/main/java/com/example/data/ai/TemplateOutputValidator.kt`
- `app/src/main/java/com/example/data/security/ProviderStorage.kt`
- `app/src/main/java/com/example/data/local/EduMiniDatabase.kt`
- `app/src/main/java/com/example/model/Models.kt`

---

## 1. Product responsibility and boundaries

Generation Studio is the lesson-aware authoring workspace. It receives an already identified lesson from the imported workspace and generates editable educational content against that lesson's source material.

The Studio is responsible for:

1. selecting the workspace lesson and generation task;
2. selecting and applying a reusable prompt template;
3. preparing the lesson source text and/or PDF attachment;
4. selecting one AI provider and one of that provider's configured profiles/models;
5. assembling the prompt and the template response contract;
6. submitting the request through the selected provider path;
7. validating and adapting the response;
8. saving an editable draft with provenance;
9. exposing task-specific follow-up actions, such as saving questions or building an interactive package.

The Studio is **not** responsible for re-indexing the source book, changing the workspace's canonical index, or creating a permanent lesson JSON snapshot.

### Source-of-truth boundaries

- Workspace `index.json` and `book_manifest.json` remain authoritative for book, unit, lesson, part and page identity.
- The selected lesson PDF remains the primary document source for generation when attached.
- Prompt templates define reusable generation instructions and output contracts.
- The provider response is untrusted until validated and adapted.
- A generated draft is application-owned content; it does not overwrite the imported workspace.
- A future persistent lesson text/JSON snapshot is a separate, deferred feature. It must not be silently introduced as a side effect of generation.

## 2. End-to-end Studio workflow

```mermaid
flowchart TD
    A[Open imported workspace] --> B[Select book and lesson]
    B --> C[Select task]
    C --> D[Select reusable prompt template]
    D --> E[Prepare lesson text and PDF source]
    E --> F[Select one provider and its profile/model]
    F --> G[Assemble prompt + response contract]
    G --> H[Provider-specific request]
    H --> I[Receive raw response]
    I --> J{JSON contract?}
    J -- Yes --> K[Validate schema and output]
    J -- No --> L[Keep text response]
    K --> M[Apply response field mapping]
    L --> N[Create editable draft]
    M --> N
    N --> O[Review and edit]
    O --> P[Save another draft version / approve]
    O --> Q[Task-specific action]
    Q --> R[Question bank or interactive package]
```

A request must have a selected lesson and at least one usable source: non-empty source text or an enabled, readable lesson PDF attachment. The UI must not submit an empty-source request.

The selected provider is captured for the request. Changing the default provider affects subsequent requests, not a request already in progress.

## 3. Lesson context and source attachment

### 3.1 Lesson context passed to the prompt

The assembled request includes the available lesson identity and context:

- grade;
- book title/key;
- unit title/key;
- lesson title/key;
- printed/source page range when available;
- task type;
- requested question count where relevant;
- question style and difficulty hints where relevant;
- user-provided source text;
- template instructions and additional instructions.

The workspace identifiers are context, not values to be guessed. A missing lesson identity or source reference must be surfaced as a validation problem rather than replaced with fabricated metadata.

### 3.2 PDF attachment behavior implemented

The attachment toggle and template attachment mode control whether the lesson PDF is attached.

| Selected provider | Current attachment behavior |
|---|---|
| Gemini | With mode `pdf_native`, the original PDF is sent as an `application/pdf` inline-data part in the same request content as the prompt. With mode `rendered_pages`, rendered JPEG page images are sent instead. |
| OpenAI-compatible | The lesson PDF is rendered into JPEG page images and sent as image parts with the prompt. A portable native-PDF contract is not assumed. |
| Either provider, attachment mode `none` | No PDF/image attachment is sent; usable source text is therefore required. |

For OpenAI-compatible profiles, the current renderer selects up to four pages distributed across the referenced PDF. It does not currently extract a precise printed-page interval from a full-book PDF.

For native Gemini PDF attachment:

- the local file must have a valid PDF signature;
- the current local size limit is 18 MiB;
- the complete referenced PDF is attached;
- if the lesson reference points to an entire book rather than a lesson-level PDF, the entire referenced book PDF is sent.

The current source-attachment mode is stored in the prompt-template record. The present Settings template editor does not expose a complete attachment-mode selector; the repository default is `pdf_native`. Do not imply that users can currently configure every stored mode through the UI.

### 3.3 Privacy boundary

Prompt text and selected lesson metadata are passed through the text privacy sanitizer. That does **not** redact personal information embedded in the PDF or rendered page images.

The UI notice must explain that attached PDF/image content is transmitted to the selected external provider. Users must be able to disable attachment and provide source text instead. Do not describe text sanitization as PDF/image redaction.

## 4. Prompt templates are the generation contract

### 4.1 Template contents

A reusable prompt template is more than a prompt string. The Room `prompt_templates` record stores:

| Field | Meaning |
|---|---|
| `id` | Stable template identity |
| `title` | User-facing template name |
| `task` | Generation task to which the template applies |
| `instructions` | Reusable task instructions |
| `response_schema_json` | Provider-facing output schema/structure |
| `response_mapping_json` | Mapping from provider field names to application canonical fields |
| `response_format` | `text` or `json` |
| `source_attachment_mode` | Source attachment policy, currently defaulting to `pdf_native` |
| `version` | Template version, incremented when an existing template is edited |

The schema and mapping belong to the template. They must not be duplicated as task-specific hardcoded response bodies inside `AiGenerationService`, `MainViewModel`, or parsers.

The selected template is resolved by its explicit ID first, with a task-compatible template as fallback. The request uses the selected template's instructions, schema, mapping and version.

### 4.2 Built-in and custom templates

- Built-in templates are inserted idempotently into Room from the repository's default template definitions.
- Custom templates are stored in Room and can be edited or deleted.
- Editing an existing template increments its version.
- The prompt blueprint registry provides task-level prompt metadata and asset paths.
- The prompt asset and the persisted template are complementary: the asset provides the base task blueprint; the Room template supplies the reusable selected instructions and response contract.

Current task identifiers:

| Task ID | Purpose |
|---|---|
| `explanation` | Lesson explanation |
| `summary` | Lesson summary |
| `objectives` | Learning objectives |
| `vocabulary` | Vocabulary/glossary |
| `worked_examples` | Worked examples |
| `lesson_review` | Lesson review |
| `difficulty_adapter` | Difficulty adaptation |
| `interactive` | Interactive lesson/activity |
| `questions` | Question bank |

The registry contains task blueprints for these task types. The repository currently seeds a smaller set of built-in prompt-template records; do not assume that every task already has a separate built-in Room template.

### 4.3 Required template behavior

A template must carry its complete response contract alongside its instructions. For structured output, the contract consists of:

1. response format;
2. JSON Schema;
3. response field mapping;
4. template version.

The AI response must be generated dynamically according to the selected template contract. Do not hardcode a fixed response paragraph, fixed question object, or task-specific JSON body in the provider service.

Changing the lesson, task, prompt instructions, schema, mapping, attachment mode, provider or model must be reflected in request provenance.

### 4.4 Template validation

**Implemented:**

- The save path checks that schema and mapping inputs parse as JSON objects.
- Before a JSON generation request, `TemplateOutputValidator` checks the schema structure.
- After the provider responds, the raw JSON is validated against the stored schema before response mapping and draft persistence.
- An invalid provider output is recorded as a validation failure and is not saved as a normal draft.
- The supported validator subset includes object, array, string, number, integer, boolean and null types, object properties/required fields, array items, enum and boolean `additionalProperties`.

**Limitations and required hardening:**

- The validator is a deliberately limited JSON Schema subset, not a complete JSON Schema implementation.
- The current template save path checks JSON-object syntax but does not run the full schema validator before persisting the template.
- The mapping is checked as a JSON object, but its semantic references are not comprehensively validated at template-save time.
- The Settings editor does not currently expose every stored contract field, including a complete response-format and attachment-mode editor.

Before expanding template editing, validate the complete schema and mapping contract before saving. Display field-specific errors and preserve the previous valid template when validation fails. Do not silently downgrade an invalid JSON template to text output.

## 5. Response mapping and canonical application payload

The provider-facing schema and application-facing data model are separate contracts.

Example mapping:

```json
{
  "rootArray": "items",
  "targetArray": "questions",
  "fields": {
    "prompt": "stem",
    "options": "choices",
    "correctAnswer": "answer",
    "rationale": "explanation"
  }
}
```

Meaning:

- `rootArray`: provider's source array name;
- `targetArray`: canonical root array name; defaults to `questions` when omitted;
- `fields`: canonical field name mapped to the provider's field name.

The adapter:

- parses the response when possible;
- adds the canonical root array when a source root array is mapped;
- recursively adds canonical field aliases when the provider's field exists;
- preserves original provider fields;
- does not delete source fields or mutate the stored template.

For an interactive contract, `targetArray` may be `steps`. This adapter is a compatibility layer, not an arbitrary Room schema migration engine. Changes to Room entities or application data semantics still require explicit application code and database migrations.

## 6. Provider isolation

### 6.1 Supported provider types

The current provider enum contains:

- `GEMINI`
- `OPENAI_COMPATIBLE` (user-facing label: OpenAI)

An OpenAI-compatible profile contains its own base URL, API key, model, enabled state, priority and fallback-model list. It must not inherit Gemini model names, keys, cooldown state or provider-specific response assumptions.

### 6.2 Required isolation rule

**A provider failure must never silently switch the request to a different provider.**

If Gemini is selected:

- only enabled Gemini profiles are eligible;
- Gemini model candidates come only from that Gemini profile's primary/fallback model list;
- Gemini-specific request format, PDF handling, error classification and cooldown logic apply;
- OpenAI is not a fallback destination.

If OpenAI-compatible is selected:

- only enabled OpenAI-compatible profiles are eligible;
- only that profile's OpenAI-compatible model list and base URL apply;
- OpenAI-compatible request construction and error handling apply;
- Gemini keys, model candidates and cooldowns are not consulted.

A user who wants to try the other provider must explicitly change the selected provider and start a new request. Provider switching is not an automatic retry policy.

### 6.3 Current code versus desired separation

The current implementation has provider-specific request functions (`callGemini` and `callOpenAiCompatible`) behind a shared `AiGenerationService.generate` dispatcher and a provider-scoped routing policy.

The required architectural direction is to keep the public generation paths explicitly separated as well, for example:

- `generateWithGemini(...)`
- `generateWithOpenAiCompatible(...)`

Each path must own its own profile validation, request assembly, model fallback, retry/error classification, connection test and provider telemetry. Shared code may be limited to provider-neutral preparation such as lesson context, prompt assembly, response validation, mapping and provenance.

This separation is a design requirement; the current shared dispatcher should not be described as two fully independent generation services already implemented.

## 7. Model fallback and overload handling

### 7.1 Configured candidate order

A provider profile stores:

- primary model;
- ordered fallback model IDs;
- `strictModel`.

When strict mode is enabled, only the primary model is attempted. Otherwise the primary model is followed by the configured fallback list, with blank and duplicate entries removed.

Model IDs are configuration, not permanent constants. Examples such as moving from a 3.8 model to 3.7, 3.6 or 3.5 describe a possible user-configured order only. The application must not assume that those model IDs exist, are enabled for a particular key/project, or are still offered by the provider.

The fallback list must remain provider-specific. A Gemini model ID must never be sent to OpenAI-compatible endpoints.

### 7.2 Current Gemini behavior

The current implementation:

1. orders eligible profiles belonging to the selected provider;
2. iterates the primary Gemini model and configured Gemini fallback models;
3. skips Gemini models that are in the process-local cooldown;
4. on HTTP 429, records the affected profile/model cooldown using the provider's `Retry-After` value when available, with a 60-second minimum/default;
5. tries the other configured models on that same Gemini profile;
6. after observing Gemini 429, stops the generation request rather than automatically rotating to another Google key/profile;
7. treats a confirmed quota-exhaustion response differently and stops additional model/profile attempts for that generation;
8. retries transient non-429 failures within a bounded per-candidate retry loop.

The Gemini model cooldown is currently process-local and is lost when the application process restarts. It is model-scoped (profile + model), not a global Gemini cooldown.

### 7.3 Current OpenAI-compatible behavior

OpenAI-compatible profiles use their own configured candidate model list and their own request function. They do not use Gemini cooldown state or Gemini model identifiers.

A provider switch is explicit. A failed OpenAI request must not dispatch Gemini automatically, and a failed Gemini request must not dispatch OpenAI automatically.

### 7.4 Retry policy requirements

For future hardening, keep the failure categories distinct:

| Failure | Required handling |
|---|---|
| Authentication/permission (401/403) | Mark the affected profile invalid/permission-failed; do not retry the same credentials repeatedly |
| Confirmed quota exhaustion | Stop requests that would bypass the provider's quota; show a clear quota message and retry guidance |
| Temporary rate limit / overload (429) | Respect `Retry-After`; apply cooldown to the affected model/profile according to provider semantics; try only configured models within the selected provider |
| Transient server failure (5xx) | Bounded retry with exponential backoff and jitter, then continue only within the selected provider's configured candidates |
| Network/timeout | Bounded retry; preserve the user's prompt and source selection |
| Invalid request/schema/model | Do not repeat an unchanged invalid request; report the precise cause |
| User cancellation | Cancel the active HTTP call and do not continue fallback attempts |

The current code already separates several of these cases, but its retry/backoff policy and Gemini cooldown persistence remain narrower than this target contract. Do not describe the target policy as fully implemented until tests prove each branch.

## 8. Provider profiles, secrets and request telemetry

Provider profiles are encrypted at rest using an Android Keystore-backed key and authenticated encryption. The profile contains the provider type, label, model, endpoint, API key, enabled state, priority, fallback model list, strict-model setting and runtime status.

Rules:

- Never include API keys in prompts, logs, draft provenance, backups or Firestore.
- Mask secrets in the UI.
- Test-connection must test only the selected provider profile and its configured model.
- Runtime status and last-used information may be stored locally.
- The application must not claim that encryption at rest makes an API key impossible to extract from a distributed APK. A public release should use a controlled backend for provider credentials and request dispatch.

### 8.1 Daily request counters

The current `dailyRequestLimit` and `dailyRequestsUsed` fields are not an authoritative local quota gate. `ProviderStorage` explicitly treats the counter as diagnostic telemetry and relies on the provider response for actual quota status. Do not promise that the app enforces a daily request cap until that behavior is deliberately implemented and tested.

Quota date normalization uses each profile's configured timezone. Provider runtime status and `Retry-After` timestamps are retained separately from the prompt-template data.

## 9. Output persistence, editing and task-specific actions

### 9.1 Draft persistence

A successful generation is saved as an application draft in the local Room database. Drafts are versioned per workspace/book/unit/lesson/task context and retain:

- provider label and actual model ID;
- template ID and template version;
- response schema hash;
- request fingerprint;
- generated content hash;
- source PDF hash;
- source page range and source text hash where available.

The generated text remains editable. Saving an edited version creates another draft version; it must not silently overwrite the previous draft. Draft approval is a separate user action.

Full local backup includes the template contract and draft provenance fields. Cloud draft synchronization may carry draft provenance, but provider secrets must never be synchronized.

### 9.2 Task-specific post-processing

- **Questions:** the generated output is saved as a draft and parsed into the question bank when the question task succeeds. Concept tags are retained or inferred by the existing question parsing path where applicable.
- **Interactive lesson:** the output is parsed into the interactive activity model. A valid activity can be built into a student-facing HTML/JS/JSON package. Checkpoint answers should be routed through the interactive question-attempt callback so learning analytics can record them.
- **Other tasks:** the generated result remains an editable lesson draft until the user chooses an available follow-up action.

An interactive package is a task-specific deliverable. It is not the deferred persistent lesson JSON snapshot and must not be conflated with one.

### 9.3 Deferred persistent lesson snapshot

A permanent lesson JSON/text snapshot remains deferred. When implemented later, it should be a separate versioned artifact containing at least:

- workspace/book/unit/lesson identity from the canonical workspace;
- source PDF SHA-256;
- page range;
- extraction method (PDF text layer/OCR/manual);
- extraction timestamp;
- snapshot schema version;
- extracted content hash.

It must be regenerable and auditable, must not rewrite `index.json` or `book_manifest.json`, and must not become an implicit side effect of a normal Studio generation request.

## 10. Provenance and reproducibility

The current request seed includes template ID/version, attachment mode, task, lesson identity, page range, response schema hash, mapping hash, response format, PDF hash, source text and effective instructions. The final request fingerprint additionally includes provider, actual model and assembled prompt.

The generated result and draft retain the relevant hashes and model/template identifiers. API keys are excluded.

**Known limitation:** the current template row is updated in place when edited; the version number increments, but immutable historical copies of every prior template body/schema/mapping are not maintained as separate template revisions. A draft's version and hashes help identify its generation contract, but they do not by themselves reconstruct the exact prior template contents. If exact replay/audit is required, add immutable template revision snapshots or store a complete request-contract snapshot with the draft.

## 11. Error handling and user experience

On failure:

- preserve the selected lesson, source text, prompt instructions, selected template and provider;
- preserve existing drafts;
- show a classified, actionable error instead of a generic "generation failed";
- show which provider and model failed, without exposing secrets;
- distinguish overload/rate limit from quota exhaustion, invalid credentials, invalid model, invalid request, malformed JSON and schema mismatch;
- allow the user to retry after correcting the cause or explicitly selecting another provider;
- never report success before validation and draft persistence complete.

Progress indicators represent real pipeline stages (source preparation, attachment preparation, request dispatch, response validation/adaptation and persistence). They must not imply token-by-token progress when the HTTP provider does not expose it.

## 12. Database and migration contract

The current Room schema stores the prompt-template response contract and generation provenance in addition to the original draft fields.

Migration `15 → 16` adds to `prompt_templates`:

- `response_schema_json`;
- `response_mapping_json`;
- `response_format`;
- `source_attachment_mode`;
- `version`.

It adds to `generation_drafts`:

- `template_id`;
- `template_version`;
- `response_schema_hash`;
- `request_fingerprint`;
- `content_hash`;
- `source_pdf_hash`.

The migration is additive and supplies defaults for existing rows. Release validation must test migration from every supported historical database version to the current version, not merely instantiate a fresh database. The current source includes a 15→16 migration; do not claim a tested 13→16 path unless the migration chain and tests prove it.

Template changes must not erase existing drafts or source workspace records.

## 13. Required tests and acceptance criteria

### Template and response contract
- Add/edit/delete custom templates and preserve their IDs.
- Editing increments the template version.
- Built-in templates are inserted idempotently.
- Invalid schema is rejected before the template is saved.
- Invalid mapping structure or unsupported field references produce actionable validation errors.
- JSON response is validated before adaptation and draft persistence.
- Invalid AI output is recorded as validation failure and is not persisted as a normal draft.
- Field mapping preserves original provider fields while adding canonical aliases.
- Different tasks can use different schemas/mappings without task-specific hardcoded response bodies.

### PDF and source
- Gemini receives the original PDF bytes as `application/pdf` with the prompt.
- OpenAI-compatible receives rendered JPEG page parts with the prompt.
- Attachment disabled means no PDF/image data is sent.
- Invalid, missing, oversized or unreadable PDFs fail before provider dispatch.
- PDF attachment privacy notice is visible and accurate.
- Source PDF hash is stable across repeated reads of the same bytes.

### Provider isolation and fallback
- Gemini failure never dispatches OpenAI-compatible.
- OpenAI-compatible failure never dispatches Gemini.
- Candidate model order follows the selected profile configuration.
- Strict model mode attempts only the primary model.
- Gemini 429 cooldown affects the relevant model/profile and does not silently rotate Google keys.
- Confirmed quota exhaustion stops prohibited retries.
- OpenAI-compatible model candidates are independent of Gemini models and cooldown state.
- Provider switching affects new requests only.
- Cancellation stops the active HTTP request.

### Persistence and learning integration
- Draft provenance is saved and included in full local backup/restore.
- Editing creates a new draft version.
- Question parsing retains or infers concept tags without overwriting explicit valid tags.
- Interactive checkpoints invoke the answer callback and record attempts against the correct lesson/concept.
- Room migration tests cover every supported historical version through the current version.

---

## 14. Explicit non-goals

The following must not be silently folded into the current Generation Studio contract:

- automatic cross-provider fallback;
- automatic permanent lesson JSON snapshot creation;
- rewriting canonical workspace indexes or lesson PDFs;
- treating a prompt example as a validated JSON Schema;
- assuming all OpenAI-compatible endpoints support identical PDF or strict JSON Schema features;
- claiming local daily quota enforcement when the provider is authoritative;
- claiming that a configured model preset is available for every API key/project;
- treating an interactive student package as the canonical lesson source.

