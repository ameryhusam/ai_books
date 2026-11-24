# Edu Mini Generation Studio — Provider/API contract

## Product priority
Generation Studio is the core feature, rebuilt for Edu Mini. Notebook-like flow: imported workspace/source context, selected lesson, task type, provider/model, prompt editor, generated draft, revision and save-to-lesson. Do not port indexer2's web UI.

## Indexer2 runtime behavior reviewed
- Runtime Gemini keys are sourced from `GEMINI_API_KEY` and numbered `GEMINI_API_KEY_N`, sorted numerically; enabled keys are tried in sequence.
- Model candidates are attempted as requested model, configured default, and configured model list, deduplicated. A strict model option can limit fallback.
- Retry count is bounded 0–5, timeout configurable, and failures advance through key/model candidates.
- Runtime settings return masked keys; raw keys are not exposed. UI supports add/enable/disable/delete key profiles.
- Edu Mini should reproduce the provider/model fallback strategy, not the `.env` management interface.

## Provider profiles
Initial providers: Gemini REST and OpenAI-compatible chat completions with configurable base URL. A profile has label, provider, model, endpoint (compatible API), API key, enabled state, and priority. Store profiles in Android SecureStore; never commit API keys. Keys are masked. Direct-from-device API is acceptable for a personal prototype only—keys can be extracted from APK. Public distribution requires a secure backend.

## Fallback behavior
Profiles are ordered by priority. Try enabled profiles sequentially; retry transient network/timeout/429/5xx failures with bounded retries then advance to next profile. Avoid repeated retries for auth/model/invalid-request errors. Let users disable, reorder or prefer profiles. Keep a configurable default model and candidate list; do not hard-code a provider model as permanent. Record provider/model/task/time, never secrets.

## Generation tasks
- `lesson_explanation`: explanation, examples, summary and comprehension checkpoints.
- `interactive_lesson`: lesson sections and safe activity JSON (not executable code).
- `question_bank`: question type, prompt, options, answer, rationale, difficulty and source reference.
Each request uses grade, subject/book, unit/lesson IDs, source page ranges and source text when available. Outputs are drafts, editable and reviewable before acceptance.

## Save-to-lesson contract
Prototype saves additively and versionably in `edu_mini_generated/<unitKey>/<lessonKey>/` with `lesson-explanation-draft.json`, `interactive-lesson-draft.json`, or `ai-question-drafts.json`. Future integration should map to `ai_content/` and canonical `questions/<unitKey>/<lessonKey>/ai_generated.json`. No silent overwrite and no auto-approval.

## Safety
Treat imported sources and AI output as untrusted. Do not send API keys to prompts or other providers. Validate structured outputs. Keep raw responses draft-only. Preserve prompts and prior results after failure. Record template version and source context hash.