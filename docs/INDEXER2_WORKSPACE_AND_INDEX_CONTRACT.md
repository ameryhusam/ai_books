# Edu Mini Workspace Import Contract — indexer2 compatibility

## Decision
Edu Mini does not recreate indexing, PDF slicing, or workspace preparation. It imports prepared ZIP files, validates them, extracts into app-private storage, and reads the index to build lesson navigation.

## Archive layout
ZIP must include a top-level `workspace/` directory and grade path such as `workspace/G04/...`. Grade keys are G01–G12. Do not flatten the archive or rewrite source files.

## Contracts inspected from indexer2
- `docs/CONTENT_WORKSPACE_AI.md`: workspace index and manifest files; workspace list/detail/files/artifact APIs; lesson generation saves under `ai_content/`; generated questions are reviewable drafts and selected questions are saved under `questions/<unitKey>/<lessonKey>/ai_generated.json`.
- `config/prompts/workspace_builder.md`: canonical workspace path `workspace/<gradeKey>/<subjectKey>[/<componentKey>]/`; one root `index.json` and `book_manifest.json`; unit and lesson folders under P01/P02; merged books can have derived P01/P02 indexes, but root index remains canonical; don't overwrite existing data; preserve unknown JSON fields.
- `docs/BOOK_IDENTITY_AND_INDEX_ALGORITHM.md`: source layout and part are distinct; index identity is canonical; P01/P02 are under grade/subject/component; BOTH is a merged source state and unit `partKey` remains meaningful.
- `docs/plans/adaptive-education/02-workspace-to-database-import-contract.md`: approved `index.json` provides `units[] -> lessons[]`; workspace is staging/reproducibility storage, not a replacement for canonical DB data.

## Import algorithm
1. Pick ZIP through Android picker.
2. Validate paths: reject absolute paths, parent traversal, malformed separators, duplicate paths, excessive path lengths, too many files and oversized archive.
3. Locate `workspace/`, require grade folder Gxx and preview.
4. Extract to app-private storage. Never execute scripts or content from the archive.
5. Prefer root `index.json`; optional part-specific `P01/index.json` and `P02/index.json` are views. Preserve unit/lesson keys, part assignment and page ranges.
6. Read `book_manifest.json`, fallback to legacy `manifest.json`; do not invent missing title/grade/subject metadata.
7. Show import receipt, warnings and unit/lesson counts.
8. Open selected workspace and start Generation Studio with lesson IDs, unit IDs and source references.

## Additive persistence
Import does not rewrite the archive. Generated artifacts use a separate additive namespace in the lesson area so existing content is not overwritten. Prototype path: `edu_mini_generated/<unitKey>/<lessonKey>/...`. Compatibility adapters should later map to indexer2's preferred `ai_content/` and `questions/<unitKey>/<lessonKey>/ai_generated.json` with safe merge, backup and user review.

## Compatibility caution
Current prototype recognizes common `units[].lessons[]` shapes only. Actual workspaces may vary in index placement, key names, page-range conventions and lesson folders. Before declaring full compatibility, test real ZIP fixtures for P01/P02/BOTH, merged books, and legacy manifests. Unsupported shapes must be shown as warnings, not guessed.