# Workspace ZIP Import — Actual indexer2 Layout Contract

## Input confirmed by the owner
ZIP archive is a prepared workspace tree, e.g. `workspace/G04/ARAB/`, `workspace/G04/ISL/`, `workspace/G04/MATH/`, `workspace/G04/SCI/`, `workspace/G04/SOC/`; another grade may contain a component such as `workspace/G07/ENG/COURSE/` or `workspace/G07/SOC/GEO/`. A grade folder may contain several subject books and components at once. Therefore the importer must NOT treat the first `index.json` found anywhere under the grade as the grade-wide index.

## Confirmed paths from provided fixture tree
- Book/workspace root: `workspace/<gradeKey>/<subjectKey>[/<componentKey>]/`
- Book manifest: `book_manifest.json` at the book root.
- Canonical index: `index.json` at the book root.
- Optional derived indexes: `P01/index.json`, `P02/index.json`; these are derived views, not replacements for the root canonical index.
- Lesson PDFs: `P01/units/<unitKey>/lesson_<nn>.pdf`, `P02/units/<unitKey>/lesson_<nn>.pdf`.
- Cover images may exist at the book root and under part directories.
- `validation_report.json` may exist at the book root.
- Existing `ai_content/`, `questions/`, and `manual_review/` are explicitly optional and may be ignored by Edu Mini for import/navigation. Edu Mini must not depend on them or delete them.

## ZIP import requirements
1. Keep the full extracted ZIP tree intact (no flattening, renaming or rewriting source workspace files).
2. Identify all book roots within grade folders by paired canonical `book_manifest.json` and root `index.json`, not by first matching filename.
3. Extract/validate archive safely: reject absolute paths, parent traversal, drive paths, backslashes, duplicate normalized paths, excessive file counts, and oversized archive/expanded bytes. Protect against zip bombs and only accept paths inside the expected `workspace/` root.
4. Display an import preview before committing extraction: grade(s), books/subjects/components, unit and lesson counts, file count, compressed and expanded sizes, warnings. If multiple grades exist, show them and allow selecting/adding a grade rather than silently selecting one.
5. If no canonical root index exists, mark book unsupported and report why. Do not fall back to P01/P02 indexes as canonical unless the user explicitly selects a compatibility mode.
6. Prefer root `index.json` for lesson metadata; read manifest for book title, subject, component, grade and source layout. Preserve external IDs and partKey. Parse index schema defensively, but don't guess fields or fabricate lesson rows.
7. Use the canonical index to create navigation. Associate a lesson PDF by canonical unit/lesson IDs and exact known filenames only. Show a warning when the file is missing; do not infer a different PDF.
8. PDF viewer should open the selected lesson PDF from its exact path and be a read-only view. On phones use a single-column view; tablets/iPad use split-pane (book/lesson list + PDF/Studio) when width permits.
9. Import should be transactional: extract to a temporary directory, validate the book roots/indexes and paths, then move/rename to a committed workspace directory. On failure remove only the temporary import; never erase existing workspaces.
10. Workspace import and generated content are separate. Generated content belongs to Edu Mini's local database and references workspace/book/unit/lesson IDs; canonical PDF/index files stay unchanged.

## Edu Mini local data model (recommended)
Use SQLite (Expo SQLite) for metadata, drafts, provider profile references, generation history, prompt templates and settings. API keys belong in SecureStore and must never be included in database exports or prompts. Avoid writing generated content into indexer2's source JSON by default.

Entities:
- ImportedWorkspace: id, gradeKey, rootPath, importedAt, zipHash, schemaVersion, status.
- Book: id, workspaceId, relativePath, subjectKey, componentKey nullable, title, manifestJson, canonicalIndexPath, sourceLayout, validationStatus.
- Unit: id (preserve canonical unitKey), bookId, title, partKey, orderIndex.
- Lesson: id (preserve lessonKey), unitId, title, partKey, orderIndex, pageStart/pageEnd, pdfRelativePath nullable, pdfStatus.
- GenerationDraft: id, bookId, unitId, lessonId, taskKind, title, providerProfileId (non-secret reference), model, sourceHash, promptVersion, contentJson/text, createdAt, updatedAt, status= DRAFT.
- GenerationRun: id, draftId nullable, provider/model/task, inputHash, timestamps, status, error summary (never keys), usage/cost when available.
- ProviderProfile: id, provider, label, model, baseUrl, enabled, priority. Secret is stored in SecureStore keyed by profile ID, not copied into SQLite.

## Studio workflow
- Select imported grade > book/subject/component > unit > lesson.
- Source pane: lesson PDF or extracted text/source snippet (PDF OCR/text extraction is optional and must report unsupported/scanned pages clearly).
- Notebook canvas: blocks/cells such as source, prompt, generated explanation, examples, interactive activity JSON, question set and revision notes.
- Task templates: lesson explanation, interactive lesson (safe structured JSON, not executable arbitrary scripts), question bank, summarize, improve/revise.
- Draft is saved locally before/after generation as appropriate. Keep prior versions; never overwrite silently.
- Generation context includes grade/book/subject/unit/lesson/page metadata and source content when available; don't send irrelevant files or secrets.
- Provider fallback is ordered and bounded; use user-configured profiles, timeout and retry policies; only retry transient failures. Show which provider/model succeeded and failures.
- Output validation is task-specific; questions require prompt/type/options/answer/explanation/difficulty/sourceRef fields (where applicable); interactive output uses safe declarative JSON schema, not HTML/JS execution.
- Generated results are editable and save to SQLite. Export into workspace is optional, explicit and additive.

## Critical defects identified in current prototype (source review only; runtime tests not yet performed)
- `importWorkspaceZip()` selects the first `index.json` anywhere in ZIP. This is incorrect for the provided grade workspace with multiple subject books. It should create a catalog of book roots and indexes.
- Current import does not present a validation preview or transactional commit of a staged import.
- Current source textbox is manual; the selected lesson PDF is not read/rendered or attached to generation. Studio is not yet lesson-source-aware despite its lesson selection.
- Current draft writes JSON files into the extracted workspace under `edu_mini_generated/`, rather than SQLite. The app should adopt local SQLite draft persistence and keep the imported workspace immutable by default.
- The current parser expects only a limited `units[].lessons[]` shape and has not been validated against real index fixtures.
- ZIP validation needs explicit archive/expanded byte caps and normalized-path collision checks. File count alone is not enough.
- Current imports silently store all extracted content but may use an arbitrary manifest/index for grade title/lessons; book selection is needed.
- The current app lacks full CRUD/reorder/delete of provider profiles, prompt templates, draft versions, and export/recovery.
- The existing ZIP path guard must be independently tested for ZIP-slip, duplicate normalized paths, and malicious size metadata before being trusted.

## Milestones for a credible prototype
A. Grade > Book catalog model and import preview.
B. Canonical root-index parsing with path-based lesson PDF lookup.
C. Offline SQLite schema and draft/version persistence.
D. PDF reading/viewing and source extraction where feasible.
E. Notebook-style Studio with editable cells and multiple task templates.
F. Provider profile management, fallback and secret isolation.
G. Safe JSON validation, draft review and optional export.
H. CI typecheck, tests, and Android build.

## Acceptance test fixture checklist
- Grade G04 with at least ARAB, ISL, MATH, SCI and SOC books.
- Grade G07 with ARAB, ENG/COURSE, ISL, MATH, SCI and SOC/GEO.
- P01-only, P02-only and both parts present.
- Variable lesson counts per unit.
- Units starting at unit_01, unit_05, unit_11 or unit_13; no assumption unit 1.
- Missing PDFs and optional cover/validation reports.
- Ignore optional legacy `ai_content/`, `questions/`, `manual_review/` files during catalog and navigation.
- A malformed index and a ZIP-slip entry should fail safely without partial import.
