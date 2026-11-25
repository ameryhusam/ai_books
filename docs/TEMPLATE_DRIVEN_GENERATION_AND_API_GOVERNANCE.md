# Template-driven AI generation and API key governance

## Scope implemented on this branch

### Source attachment behavior

- Gemini requests receive the original lesson PDF as a native `inlineData` part with MIME type `application/pdf`, in the same user content as the assembled prompt.
- The PDF is read from the existing lesson PDF reference and is the generation source; generation no longer depends on a saved `.txt` sidecar. No permanent text/JSON copy of the lesson is created.
- The local file is checked for a PDF signature and an 18 MiB limit before Base64 encoding.
- OpenAI-compatible Chat Completions do not have one universal PDF attachment contract. For those profiles, the app renders lesson pages as JPEG image parts and sends those with the prompt. The provider-specific behavior is explicit rather than pretending every endpoint accepts PDF.
- The current lesson PDF reference is expected to identify the lesson-level PDF. If it points to an entire book, the complete PDF is sent to Gemini; page-range extraction is not yet implemented for native PDF uploads.

### Prompt templates and response contracts

The Room `prompt_templates` table now stores:

- prompt instructions;
- response schema JSON;
- response field mapping JSON;
- response format (`text` or `json`);
- source attachment mode;
- template version.

Built-in templates are inserted idempotently into Room at startup. Custom templates can be added and edited. Editing increments the template version. Template schema and mapping are also preserved in full local backups.

The generation service no longer selects a hard-coded question/interactive schema by task name. It receives the selected template contract. Gemini receives provider-compatible JSON schema through `generationConfig.responseSchema` when the stored contract is a valid schema object. Other JSON contracts are still included in the prompt. OpenAI-compatible endpoints receive JSON-object mode where requested; endpoint support for strict JSON Schema varies by provider.

### Response adapter

`TemplateResponseAdapter` separates provider output keys from the app's canonical data model. A template can map an alternative root collection and field names, for example:

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

The adapter preserves source fields while adding canonical fields, so the existing question parser and Room model remain stable when a provider changes field names. Interactive templates can use `targetArray: "steps"` for a different root collection.

This is a compatibility layer, not a general arbitrary database-schema migration engine. A material change in the internal Room schema still requires a deliberate adapter and Room migration.

### Progress display

The generation bar reports pipeline milestones (source read, PDF preparation, request submission, response adaptation, provenance and save), not a fabricated token-by-token percentage. While the provider request is in flight, the UI switches to an indeterminate bar because the current HTTP client does not expose reliable provider-side token progress.

### Content provenance

Generated drafts now persist:

- template ID and version;
- response-schema SHA-256;
- request fingerprint;
- generated-content SHA-256;
- original PDF SHA-256;
- provider and actual model ID.

The request fingerprint includes the template identity/version, task and lesson identity, page range, source PDF hash, source text, extra instructions, assembled prompt, provider and actual model. API keys are never included.

The new provenance fields are included in full backups. The cloud draft contract has not yet been extended with these provenance fields, so this provenance is currently authoritative in local Room and local backup.

### API key lifecycle

Each encrypted provider profile can have a daily request limit and quota timezone. Runtime state is persisted inside the encrypted Android Keystore-backed provider store.

Supported states:

- `ACTIVE`
- `DISABLED`
- `QUOTA_EXHAUSTED`
- `RATE_LIMITED`
- `INVALID_KEY`
- `ERROR`

A request slot is reserved atomically before the HTTP request. A key that reserves its final daily request is frozen immediately for subsequent calls. Quota resets at the start of the next date in that key's timezone. Manual reactivation resets the current key's daily counter; disabling a key does not delete it. Invalid-key state is not cleared automatically.

Keys are ordered by least-recently-used timestamp and then configured priority. Rate-limit cooldown uses the provider's `Retry-After` seconds when supplied, otherwise a 60-second fallback. HTTP 401/403 are treated as authentication/permission failures; HTTP 429 is separated into quota exhaustion or rate limiting using the provider error message.

Daily limits currently count HTTP requests, not tokens. Token-based quota accounting and cross-process/multi-device coordination are not implemented. The reservation lock protects concurrent requests within this app process and this ProviderStorage instance.

### Models

The default Gemini model was changed away from the 2.5 series. The model ID remains editable and fallback model IDs remain configurable. The visible 3.x entries are presets only: the provider must actually expose the exact model ID to the supplied API key. A preset is not a guarantee of provider availability.

## Important items still outstanding

1. Automatic weekly assessment scheduling (Saturday–Wednesday study days, Friday assessment) and editable schedule/timezone settings are not yet implemented. The current report still aggregates existing assessment attempts.
2. The remedial explanation workflow still lacks a learner assignment, explicit open/read/complete events, and linked follow-up assessment. Current practice-after-reinforcement analytics are not proof of explanation effectiveness.
3. Progress report needs to consume actual scheduled weekly assessment results and remedial assignment outcomes.
4. A WorkManager reminder/worker and learner-facing entry point for the Friday assessment are not yet present.
5. Local Android build, Room migration execution from a real version-15 database, and unit tests have not been run in this editing environment.

## Future design: persistent lesson JSON snapshot

Persistent lesson text/JSON is deferred, not excluded. The current generation path continues to attach the source PDF and prompt. A future implementation can add a versioned lesson snapshot artifact with extraction method, source PDF hash, page range, schema version, and extraction timestamp. It should remain optional and must not replace the workspace index as the source of truth for lesson identity.
